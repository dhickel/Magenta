# Phase 01: Workflow Execution And Approval Gates

## Context

Phase 04 evidence found two alpha blockers in the orchestration workflow engine:

- `DEFECT-04-02`: task nodes complete instantly with empty outputs because `WorkflowRunner.executeTaskNode()` falls back to `PlanService.startRun()` when `taskNodeExecutor` is null.
- `DEFECT-04-01`: `WorkflowRunner.resumeRun()` unconditionally marks waiting approval nodes complete, so rejected approvals still allow workflow completion.

It also found `DEFECT-04-03`: duplicate routes are accepted without warning.

## Goal

Make workflow execution truthful. A workflow task node must execute the referenced plan through the real model-backed task execution path, approval rejection must block or fail the workflow, and duplicate routes must be rejected or clearly surfaced before save.

## In Scope

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/`
- Workflow controller/service tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/` and `src/test/java/io/mindspice/magenta2/api/web/`
- Minimal adjacent changes to wire a model-backed task executor if required.

## Out of Scope

- Docker shell-tool routing and `/output` path fixes belong to Phase 2.
- Output viewing UI belongs to Phase 3.
- Browser validation belongs to Phases 4 and 5, though this worker must leave testable endpoints.

## Implementation Steps

1. Read package guides:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md` if controller changes are needed.

2. Replace the task-node no-op fallback.
   - Target: `WorkflowRunner.executeTaskNode()`.
   - The fallback call to `planService.startRun()` is not acceptable because it creates a run record without model/Docker execution.
   - Preferred implementation: introduce a small `WorkflowTaskExecutor` service/interface in the workflow package, backed by `ChatService.executeTaskBlocking(...)`.
   - Keep existing test seams if useful, but production behavior must fail clearly when no model-backed executor is available.
   - Expected failure text: `Task node execution requires model-backed task execution`.

3. Preserve node output semantics.
   - Return the completed task run output values from `TaskRun.outputValues()`.
   - If the task run ends `FAILED` or `NEEDS_REVIEW`, mark the workflow node `FAILED` and the workflow run `FAILED`.
   - Do not convert failed task nodes into empty output maps.

4. Enforce approval response during resume.
   - Target: `WorkflowRunner.resumeRun()`.
   - Get the waiting node's `messageId` from `waitingNode.outputValues().get("messageId")`.
   - Add or use an `InboxService` method that can load the message by ID regardless of user/agent recipient.
   - If the message has no response yet, keep the workflow waiting and return a clear error for explicit resume calls.
   - If `inboxService.parseApprovalFromResponse(responseJson)` returns true, mark the gate complete and continue.
   - If it returns false, mark the gate `FAILED`, set workflow status `FAILED`, store an error such as `Approval rejected for gate <nodeKey>`, and cleanup temp workspace.

5. Mark handled approval messages after a successful approved resume.
   - Use `InboxService.markHandled(messageId)` after the gate is accepted.
   - Do not mark rejected messages handled until the terminal failed state is persisted.

6. Reject duplicate workflow routes.
   - Target validation: `WorkflowService`/`WorkflowDefinition` validation path, not only the HTML controller.
   - Duplicate identity should include `fromNodeKey`, `fromOutputName`, `toNodeKey`, `toInputName`, and `routeType`.
   - Existing saved duplicates may be rendered, but new saves/adds should produce validation error or warning. Prefer error for alpha correctness.

7. Add regression tests.
   - `WorkflowRunnerTest`: rejected approval followed by resume leaves the run failed and does not execute later nodes.
   - `WorkflowRunnerTest`: approved approval followed by resume completes later nodes.
   - `WorkflowRunnerTest`: task node without model-backed executor fails instead of completing with `{}`.
   - `WorkflowRunnerTest`: task node with a fake executor returns output values and routes them to downstream report/gate nodes.
   - Workflow validation/controller test: duplicate route save returns a validation error.

## Validation

Run:

```bash
mvn -q -Dtest=WorkflowRunnerTest test
mvn -q -Dtest=WorkflowControllerTest test
mvn -q -Dtest=OrchestrationControllerTest test
```

Then run full tests after all local phase changes are merged:

```bash
mvn test
```

Acceptance criteria:

- No workflow task node can complete successfully without a real task execution result.
- Rejected approval cannot proceed to downstream nodes.
- Approved approval resumes and completes as before.
- Duplicate route validation is deterministic.
- Existing workflow CRUD endpoints still return HTML/JSON without 500s.

## Exit Criteria

- `DEFECT-04-01` and `DEFECT-04-02` are fixed and their bug reports are updated with commands/evidence.
- `DEFECT-04-03` is fixed or represented as a validation warning with tests.
- No production path remains where `PlanService.startRun()` is used as a fake workflow task execution fallback.
