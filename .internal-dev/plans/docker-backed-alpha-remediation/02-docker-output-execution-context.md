# Phase 02: Docker Output Execution Context

## Context

Phase 03 and Phase 07 proved that Docker containers start and expose `/home/agent`, `/workspace`, and `/output`, but task execution still wrote files to the host data root. The important current-state finding is that `AgentShellToolService` runs host processes under `aiConfig.dataRoot()`. A model-visible prompt telling the agent to use `/output` is insufficient while the shell tool itself is not container-scoped.

This phase owns `DEFECT-03-03`, `DEFECT-07-02`, `DEFECT-07-03`, `DEFECT-07-04`, and `DEFECT-07-05`.

## Goal

Make assignment-backed task execution actually Docker-backed: when an agent runs a task, shell/tool execution happens inside that agent's managed container, output directories are agent/job/workspace aware, required artifacts land under `/output`, and artifact registration matches the persisted run context.

## In Scope

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- Focused tests for task execution context, shell routing, workspace cleanup, and artifact registration.

## Out of Scope

- Output content viewing endpoints belong to Phase 3.
- Workflow gate semantics belong to Phase 1.
- Browser validation belongs to Phases 4 and 5.

## Implementation Steps

1. Read package guides:
   - `src/main/java/io/mindspice/magenta2/ai/chat/plan/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`

2. Extend execution context rather than adding globals.
   - Reuse `OrchestrationRunContext` where possible.
   - Extend `PlanToolContext` or add a parallel task-execution context so tool calls can know `agentId`, `agentName`, `jobId`, `projectId`, `workspaceId`, `runType`, host workspace path, and host output path.
   - The context must be set only around task execution and cleared in `finally`.

3. Allocate output directories with the executing agent.
   - Target: `PlanService.startRun(...)`.
   - Add an overload that accepts an output/artifact context.
   - For orchestration assignments, allocate `workspaceDirectoryService.agentOutput(agentId, slug, runId)` or a job output directory when job context owns the run.
   - Keep system output only for chat-only/manual task execution with no agent context.

4. Route shell execution through the agent container when task context has an agent.
   - Target: `AgentShellToolService.exec(...)`.
   - If task context contains `agentId`, use `AgentContainerRuntimeService.execInAgent(agentId, agentName, command, workingDirectory)`.
   - Container working directory must default to `/workspace`.
   - Container output path must remain `/output`.
   - Host fallback is allowed only when there is no orchestration task context.
   - Unknown Docker/unavailable runtime must fail the task, not silently run on the host.

5. Keep host path confinement for non-Docker use.
   - The existing data-root host shell behavior can remain for planning research and non-agent contexts.
   - Make the returned `workingDirectory` clearly identify whether execution was `docker` or `host` to help validation.

6. Register outputs from the correct location.
   - Target: `OutputArtifactService.materializeFilePath(...)` and `PlanService.materializeRunOutputs(...)`.
   - For `file_path` outputs from a Docker-backed run, accept `/output/<file>` as a container path and resolve it to the run's host output directory.
   - Do not accept arbitrary absolute host paths outside data root.
   - If a required output is declared as `file_path` and the model reports only `hello.txt`, resolve it relative to the run output directory.
   - Preserve a clear stored filename. Prefer the actual file name plus output name prefix only when needed for collisions.

7. Detect loose artifacts.
   - At terminal task completion, scan the run output directory for files not already registered and register them as discovered artifacts.
   - Also scan the data root top level for files created during the run only if there is a reliable timestamp/run marker; otherwise report a warning in execution evidence rather than guessing ownership.
   - Do not recursively register unrelated historical files.

8. Fully clean task temp directories.
   - Verify `WorkspaceDirectoryService.deleteTempDir(...)` deletes the parent task-run directory.
   - Fix callers that recreate the temp dir during cleanup by calling `workspaceDirectoryService.taskTemp(run.id())` just to compute the path. Add a path builder that does not create directories, or store the temp path on the run and delete that exact path.

9. Resolve duplicate workspace tables.
   - Inspect current migrations/repository ownership for `workspaces` and `workspace_roots`.
   - Pick `workspaces` as authoritative unless current code proves otherwise.
   - Stop creating/reading the unused table and add a migration-safe cleanup/comment/test so future agents do not write to both.

10. Add tests.
   - Shell service test: with execution context, command is routed to fake container runtime and host process is not spawned.
   - Plan service test: agent context allocates output under `agents/{agentId}/outputs`.
   - Output artifact test: `/output/foo.txt` resolves to the run output directory and escapes are rejected.
   - Workspace cleanup test: terminal run cleanup removes `runtime/task-runs/{runId}` parent.
   - Runtime assignment test: `OrchestrationRunnerService` passes agent/job/workspace context into model-backed execution.

## Validation

Run focused tests:

```bash
mvn -q -Dtest=AgentShellToolServiceTest test
mvn -q -Dtest=PlanServiceTest test
mvn -q -Dtest=OutputArtifactServiceAttributionTest test
mvn -q -Dtest=WorkspaceRepositoryAttributionTest test
mvn -q -Dtest=OrchestrationRuntimeTest test
```

Run Docker/Podman smoke with the configured test image:

```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-remediation-smoke.sqlite --magenta.docker.enabled=true --magenta.docker.agent-image=python:3.11'
```

Manual live proof required before signoff:

- Start the app on a fixed port with an isolated SQLite DB and Podman socket.
- Create or reuse an active agent.
- Submit a plan that writes `/output/hello.txt` and `/output/result.json`.
- Confirm files exist under `~/.magenta/root/agents/{agentId}/outputs/...`.
- Confirm no new loose files were written to the data root.
- Confirm `/api/outputs` returns artifact rows with matching `agentId`, `runId`, `planId`, `runType`, and confined file paths.

## Exit Criteria

- `DEFECT-03-03`, `DEFECT-07-02`, `DEFECT-07-03`, `DEFECT-07-04`, and `DEFECT-07-05` are fixed or have user-approved blocker notes.
- Docker-backed task execution cannot fall back to host shell execution when an agent context exists.
- Output registration and on-disk locations match the executing agent/job context.
