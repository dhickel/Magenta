# Subagent A: Canonical Job Runtime And Assignment Execution

## Context

The public job editing UI stores ordered items in `JobDefinition.items()`, but the HTMX job submit path creates a `JOB_RUN` assignment that bridges through legacy `OrchestrationJob`/`OrchestrationJobItem` rows. The runner executes `JOB_RUN` through `OrchestrationJobService`, loading the legacy model, not the public `JobDefinition`/`JobWorkItem` model. So submitted jobs may not execute the visible job items.

## Goal

Make `JOB_RUN` assignments validate and execute from the canonical `JobDefinition`/`JobWorkItem` data model, eliminating the legacy bridge.

## Current `JOB_RUN` Path (End to End)

### 1. UI Job Submit (`/jobs/_submit/{jobId}`)
- `OrchestrationController.submitJob()` (line 2344)
- Loads `JobDefinition` via `jobService.getDefinition(jobId)`
- Calls `ensureLegacyJob(job)` to create shadow `OrchestrationJob` row
- Creates `AssignmentRequest(agentId, jobId, null, JOB_RUN, ...)`
- Calls `assignmentService.create(request)`

### 2. Agent Dashboard Submit (`/agents/_submit/{agentId}`)
- `OrchestrationController.submitToAgent()` (line 3805)
- Does NOT call `ensureLegacyJob()`
- Creates `AssignmentRequest(agentId, jobId, null, JOB_RUN, ...)` with `jobId = targetId`
- Calls `assignmentService.create(request)`

### 3. AssignmentService.create()
- Validates `agentId` via `agentProfileService.get()`
- Validates `jobId` via `jobService.get()` -- BUT `jobService` is typed as `OrchestrationJobService`!
- Saves `WorkAssignment` with `jobId` field set

### 4. OrchestrationRunnerService.pollQueuedWork()
- Finds QUEUED/INTERRUPTED assignments
- Acquires lease
- Dispatches to `executeWithLease()`

### 5. OrchestrationRunnerService.runJob() (line 216)
- Loads `OrchestrationJob` via `jobService.get(jobId)` -- uses `OrchestrationJobService`
- Loads legacy `OrchestrationJobItem` list via `jobService.items(jobId)`
- Iterates items, dispatching by itemType (TASK_RUN, WORKFLOW_RUN, etc.)
- The legacy items may NOT match `JobDefinition.items()` ordering or content

### 6. OrchestrationRunService.createAssignment() (line 42)
- Uses `OrchestrationJobService.get(jobId)` to resolve agent/workspace from legacy job
- Must switch to `JobService.getDefinition(jobId)`

## Legacy Touchpoints to Remove or Bypass

| Touchpoint | File | Action |
|---|---|---|
| `ensureLegacyJob()` | `OrchestrationController.java` | Delete method |
| `ensureLegacyJob()` call | `OrchestrationController.submitJob()` | Remove call |
| `OrchestrationJobService` field | `OrchestrationController` | Remove field/constructor param |
| `OrchestrationJobService` import | `OrchestrationController` | Remove import |
| `OrchestrationJobService jobService` field | `AssignmentService` | Replace with `JobService` |
| `jobService.get(jobId)` in `validateInput` | `AssignmentService.create()` | Switch to `JobService.getDefinition()` |
| `resolveModel()` orbiting `OrchestrationJob`/`OrchestrationJobItem` | `AssignmentService` | Rewrite to use `JobDefinition`/`JobWorkItem` |
| `OrchestrationJobService jobService` field | `OrchestrationRunnerService` | Replace with `JobService` |
| `runJob()` loading `OrchestrationJob` | `OrchestrationRunnerService` | Rewrite to load `JobDefinition` |
| `runJob()` iterating `OrchestrationJobItem` | `OrchestrationRunnerService` | Rewrite to iterate `JobWorkItem` |
| `runJobItem()` dispatching by `OrchestrationJobItem.itemType()` | `OrchestrationRunnerService` | Rewrite to dispatch by `JobWorkItem.type()` |
| `OrchestrationJobService jobService` field | `OrchestrationRunService` | Replace with `JobService` |
| `jobService.get(jobId)` in `createAssignment()` | `OrchestrationRunService` | Switch to `JobService.getDefinition()` |

## Canonical Execution Algorithm

```
runJob(assignment):
  jobDef = jobService.getDefinition(jobId)
  items = jobDef.items()  // ordered list of JobWorkItem
  current = assignment
  for i from start to items.size():
    item = items[i]
    // check cancellation
    if current.status == CANCEL_REQUESTED: cancel(current)
    // dispatch by type
    result = switch item.type():
      PLAN     -> runPlanItem(current, item)
      WORKFLOW -> runWorkflowItem(current, item)
    // record progress
    jobService.updateWorkItemRun(runId, item.key(), status, childRunId, outputs, error)
    // checkpoint
    current = checkpointed(current, i+1, checkpoint, outputs, evidence)
  complete(current)
```

### PLAN Item Execution
```
runPlanItem(assignment, item):
  validate item.planId() != null
  taskRun = chatService.executeTaskBlocking(item.planId(), item.inputBindings(), conversationId, model)
  if taskRun.status != COMPLETED: throw with error
  return { planRunId: taskRun.id(), outputValues: taskRun.outputValues() }
```

### WORKFLOW Item Execution
```
runWorkflowItem(assignment, item):
  validate item.workflowId() != null
  wfRun = workflowService.runSynchronously(item.workflowId(), model)
  if wfRun.status != COMPLETED: throw with error
  return { workflowRunId: wfRun.id(), finalOutputs: wfRun.finalOutputs() }
```

### Per-Item Progress Recording
The runner will call `JobService.updateWorkItemRun()` to record each item's status as it completes/fails. The `JobRun` model already supports this via `JobWorkItemRun` records embedded in `JobRun.workItemRuns`.

## Failure Semantics

- Missing `planId` on PLAN item: fail the run with descriptive error
- Missing `workflowId` on WORKFLOW item: fail the run with descriptive error
- Plan resolution failure (`PlanService.getTask()` throws): fail the run with the error
- Workflow resolution failure (`WorkflowService.getDefinition()` throws): fail the run with the error
- Child run failure (task/workflow returns non-COMPLETED): fail the run with child error
- Continue-on-failure: not supported in current `JobWorkItem` model (no `continueOnFailure` field). First failure stops the job. This matches the existing behavior where `OrchestrationJobItem.continueOnFailure` defaults to `false`.

## Existence Validation at Item Save

`JobService.saveDefinition()` and `JobService.addItem()` will validate:
- PLAN item `planId` must resolve through `PlanService.getTask(id)` -- throws if not found
- WORKFLOW item `workflowId` must resolve through `WorkflowService.getDefinition(id)` -- throws if not found

This requires adding `PlanService` and `WorkflowService` dependencies to `JobService` (or alternatively, performing validation in the controller layer). Per the architecture guidelines (controllers thin, services own validation), validation belongs in `JobService`.

## Test Cases

### Unit/Integration Tests

1. **AssignmentService validates JOB_RUN via JobService**
   - Create a `JobDefinition`, submit `JOB_RUN` assignment with that jobId
   - Verify assignment is created without legacy `OrchestrationJob`

2. **AssignmentService rejects non-existent jobId**
   - Submit `JOB_RUN` with unknown jobId
   - Expect `IllegalArgumentException`

3. **Runner executes canonical job items in order**
   - Create job with PLAN item (plan-1, order=0) and WORKFLOW item (wf-1, order=1)
   - Submit JOB_RUN, run assignment
   - Verify items executed in order 0,1 using canonical work items

4. **Runner executes PLAN item through task path**
   - Create job with real PLAN item
   - Submit JOB_RUN, run assignment
   - Verify `chatService.executeTaskBlocking()` was called with correct planId

5. **Runner executes WORKFLOW item through workflow path**
   - Create job with real WORKFLOW item
   - Submit JOB_RUN, run assignment
   - Verify `workflowService.runSynchronously()` was called with correct workflowId

6. **Unknown planId rejected at save**
   - Try to save job definition with PLAN item referencing non-existent planId
   - Expect `IllegalArgumentException`

7. **Unknown workflowId rejected at save**
   - Try to save job definition with WORKFLOW item referencing non-existent workflowId
   - Expect `IllegalArgumentException`

8. **Agent dashboard submit and job page submit behave identically**
   - Submit same jobId through both paths
   - Verify resulting assignments have identical structure

9. **OrchestrationRunService uses JobService for context resolution**
   - Create `OrchestrationRunContext` with jobId
   - Verify `createAssignment()` resolves agent/workspace from `JobDefinition`

### Fixtures

Tests will need:
- In-memory SQLite with both `job_definitions` and `orchestration_jobs` tables
- Stub/mock `PlanService` that returns a `PlanDefinition` for known IDs and throws for unknown
- Stub/mock `WorkflowService` that returns a `WorkflowDefinition` for known IDs and throws for unknown
- Stub/mock `ChatService` for task execution
- `AgentProfileService` with a valid test agent

## Implementation Steps

1. Add `PlanService` and `WorkflowService` dependencies to `JobService`
2. Add item reference validation in `JobService.saveDefinition()` and `JobService.normalizeItem()`
3. Update `AssignmentService`: replace `OrchestrationJobService` with `JobService`
4. Rewrite `AssignmentService.resolveModel()` to use `JobDefinition`/`JobWorkItem`
5. Update `OrchestrationRunnerService`: replace `OrchestrationJobService` with `JobService`
6. Rewrite `OrchestrationRunnerService.runJob()` to use `JobDefinition.items()`
7. Rewrite `OrchestrationRunnerService.runJobItem()` to dispatch by `JobWorkItem.type()`
8. Update `OrchestrationRunService`: replace `OrchestrationJobService` with `JobService`
9. Remove `ensureLegacyJob()` from `OrchestrationController`
10. Remove `OrchestrationJobService` dependency from `OrchestrationController`
11. Update existing tests (stub changes, new constructor signatures)
12. Add new tests for validation and execution
13. Run full test suite
