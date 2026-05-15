# 03: Workflow Gate Evidence

## Scope
Validate workflow creation, node/route editing, submission, and approval gate behavior (rejection blocks, approval continues).

## Workflow Creation

### Workflow: "Alpha Gate Test Workflow"
- ID: `d0a587cc-782e-46a7-a490-411ab19e7c34`
- Nodes: node_1 (task, Execute Plan) -> node_2 (user_approval, User Approves Result) -> node_3 (report, Final Report)
- Routes: route_1 (node_1 -> node_2), route_2 (node_2 -> node_3)
- Created successfully via `POST /workflows/_editor`

### BLOCKER: Database Schema Mismatch
Workflow submission failed with:
```
SQLITE_ERROR: no such table: ai_workflow_definitions
```

Root cause: `OrchestrationRunnerService` imports `io.mindspice.magenta2.ai.chat.workflow.WorkflowService` which queries table `ai_workflow_definitions`. The actual schema has table `workflow_definitions` (no `ai_` prefix). The correct orchestration WorkflowService at `io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService` uses the correct table name but is not wired in the runner service.

All three workflow submission attempts (IDs `1b9b325e`, `3ba837f2`, `b5e56c2c`) resulted in FAILED status with the same SQL error.

### Note on WorkflowRunnerTest
The unit tests pass (23/23 in WorkflowRunnerTest), but they use in-memory mock repositories that don't hit the real database. The table name mismatch only surfaces in integration/end-to-end testing.

## Approval Gate Testing
Could not be tested because workflow execution fails before reaching any gate node. The approval gate logic is tested and verified in `WorkflowRunnerTest`:
- `rejectedApprovalResumeMarksFailed` — rejected approval -> gate FAILED, run FAILED
- `approvedApprovalResumeCompletesLaterNodes` — approved -> both nodes COMPLETED
- `resumeBeforeResponseFails` — resume without response -> IllegalStateException

But live end-to-end validation of approval gate behavior could not be completed.

## Verdict
BLOCKED:
- Workflow submission fails due to `ai_workflow_definitions` table not existing in schema
- OrchestrationRunnerService uses wrong WorkflowService bean
- Live gate rejection/approval validation could not be completed
- Unit tests confirm gate logic is correct
