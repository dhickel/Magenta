# Docker Context Output Hardening

## Date
2026-05-14

## Change Summary
Hardened Docker-backed task execution context propagation and run output handling. Orchestration-backed task execution now carries agent/job/project/workspace context into chat task run creation, allocates agent-scoped output directories, exposes a run-specific container output path, and maps model-reported `/output/<run-dir>/<file>` paths back to host run artifacts.

## Files
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AgentContainerRuntimeService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- Focused tests under `src/test/java/io/mindspice/magenta2/...`

## Behavioral Impact
- Docker-backed task runs no longer default to `agents/system/outputs` when an orchestration agent context is active.
- Execution prompts now direct models to write deliverables into `/output/<run-output-dir>` instead of treating `/output` as the run directory.
- Output artifact materialization accepts run-scoped container paths, bare filenames, and relative paths while rejecting output path escapes.
- Container shell execution rejects host absolute working directories, passes per-call timeouts to Docker exec, and returns container id metadata.

## Risks
- A first Gemma-backed run reached the model but returned `NEEDS_REVIEW` because it did not call `task_complete`; the stricter Qwen-backed run completed successfully.
- The app stop endpoint still reported `IDLE` while the container was running in one live validation pass; tracked separately in `bugs/2026-05-14-docker-stop-status-mismatch/report.md`.

## Follow-up Items
- Follow up on Docker stop status consistency.
- Consider exposing the run output path as an environment variable for future model clarity.
