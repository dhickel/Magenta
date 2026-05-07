# Summary

Final orchestration validation found blocking gaps in task/workflow context propagation and schedule due idempotency.

# Scope

Applies to the orchestration driver final validation gate and the task/workflow/scheduler paths added for the orchestration implementation.

# Reproduction

1. Submit a task run request to `/api/tasks/{taskId}/runs/stream` with `agentId`, `jobId`, `workspaceId`, `modelOverride`, and `priority`.
2. Observe that `TaskController.streamRun` only passes `inputValues` into `taskService.startRun`.
3. Submit a workflow run request to `/api/workflows/{workflowId}/runs/stream` with the same orchestration context.
4. Observe that `WorkflowController.streamRun` ignores the request body and calls `workflowService.runSynchronously`.
5. Review `ScheduleService.pollDueSchedules` and note there is no durable due-time idempotency key or transaction across next-run advancement, event publication, and assignment creation.

# Expected

Task and workflow orchestration context is applied, persisted, or routed through durable assignment execution. Scheduled due processing creates at most one assignment per schedule due time and does not lose a due run if processing fails midway.

# Actual

Task/workflow request context fields are accepted but discarded. Schedule due processing is happy-path only and has no duplicate or crash-window protection.

# Evidence

- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java:119`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java:90`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java:61`
- `.internal-dev/reviews/2026-05-07-orchestration-spec-adherence-review.md`
- `.internal-dev/reviews/2026-05-07-orchestration-quality-reliability-review.md`

# Impact

Final validation cannot be accepted against the documented criteria until these gaps are fixed or the criteria are revised.

# Status

Fixed on 2026-05-07.

# Next Action

Re-run final orchestration acceptance after user review. The remediation is implemented and validated with `mvn test`, startup smoke, Playwright MCP, and the existing live browser validation script.

# Resolution

Implemented `.internal-dev/plans/orchestration-validation-remediation/phase-01-context-and-schedule-idempotency.md`.

- Context-bearing task and workflow stream requests now route through durable `TASK_RUN` and `WORKFLOW_RUN` assignments via `OrchestrationRunService`.
- Job-backed requests can infer the agent and workspace from the job while preserving explicit `agentId`, `workspaceId`, `modelOverride`, and `priority`.
- Schedule polling now records a durable firing row keyed by `schedule_id + due_at` before creating the scheduled assignment, preventing duplicate assignment creation for the same due instant.
- Focused tests cover context-bearing task/workflow runs and repeated polling of the same schedule due instant.

# Validation Evidence

- `mvn test`: passed, 167 tests, 0 failures, 0 errors.
- Startup smoke: passed; Spring Boot started on random port `42303` with `/tmp/magenta2-orchestration-remediation.sqlite` and shut down by the bounded timeout.
- Playwright MCP remediation probe: passed against `http://localhost:18080/chat`; verified context-bearing task/workflow runs persisted durable assignments with expected job, workspace, model, and priority fields.
- Live browser fallback suite: passed with `.internal-dev/test-fixtures/orchestration-driver/live-validation.js`.
- Final wide Playwright MCP pass: passed after the follow-on fixes recorded in `.internal-dev/bugs/orchestration-wide-dynamic-validation-gaps/report.md`.
- Post-wide-validation startup smoke: passed; Spring Boot started on random port `43077` with `/tmp/magenta2-orchestration-final-smoke.sqlite`.
