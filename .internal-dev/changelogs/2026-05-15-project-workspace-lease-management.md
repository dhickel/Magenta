# Date
2026-05-15

# Change Summary
Implemented project-scoped managed workspace leases: project creation now persists `PROJECT` workspaces, runtime assignments acquire exclusive writable project leases, contention moves assignments into retryable `WAITING`, project containers mount leased roots at `/projects/{projectId}`, and graceful release state is exposed through project workspace APIs/UI.

# Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/*`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AgentContainerRuntimeService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- focused workspace/runtime/controller tests and schema/package-guide updates

# Behavioral Impact
Project workspaces are now durable managed roots with one active writable assignment holder at a time. Expired leases are reconciled before reacquisition, active leases can be marked for graceful release, waiting assignments are eligible for later retry, and managed agent containers use `/home/agent` as private state while project binds are added/removed by recreation between turns.

# Risks
The end-to-end wait/drain/retry browser scenario still depends on a live long-running assignment flow; this pass validated the browser-facing project workspace surface and API shape, while unit/integration coverage guards the core lease transitions.

# Follow-up Items
- Extend browser coverage with a deterministic long-running assignment fixture if the product needs repeatable wait/drain/retry acceptance tests.
