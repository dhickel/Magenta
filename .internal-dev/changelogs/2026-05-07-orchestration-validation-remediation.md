# Date

2026-05-07

# Change Summary

Fixed the blocking final-validation gaps for orchestration task/workflow context propagation, schedule due idempotency, and two additional wide dynamic validation defects found during Playwright MCP testing.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunContext.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunResult.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationDurableRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java`

# Behavioral Impact

Task and workflow stream requests without orchestration context keep the existing immediate synchronous behavior.

Task and workflow stream requests with orchestration context now create durable assignments, execute them through the orchestration runner, and return SSE payloads containing the assignment and run identifiers. Job-backed requests can inherit the job owner agent and workspace while preserving explicit model and priority fields.

Schedule polling now records each schedule due instant before assignment creation, so repeated processing of the same `schedule_id + due_at` creates at most one scheduled assignment.

Unknown agent approved-tool names now fail as request validation errors instead of leaking a 500 from the profile API.

Job runs that reach a `WAIT_FOR_MESSAGE` item now persist `WAITING` with the current step-boundary checkpoint instead of failing the assignment.

# Risks

Context-bearing task/workflow requests now require enough context to create a durable assignment. If no `agentId` is supplied and no `jobId` can provide an owner agent, the request fails instead of silently running as a legacy task/workflow.

Schedule idempotency uses repository-owned DDL, matching the current orchestration runtime convention.

`WAIT_FOR_MESSAGE` resume currently requeues the assignment at the same waiting item. That is enough for the current step-boundary wait behavior, but a future message-correlation workflow will need explicit logic to advance past the wait item after the awaited condition is satisfied.

# Follow-up Items

Archive the remediation plan after final validation is accepted by the user.
