# Phase 04: Docker Alpha Completion

## Context

Docker/Podman support exists through `DockerRuntimeClient` and `DockerRuntimeConfig`. The current implementation creates one-off containers with mounted agent home, workspace, output directory, optional mounts, timeout cleanup, and SELinux relabel support. Deferred notes state that full model-backed Docker execution has not been proven end to end against live local services.

The product contract is stronger than the current implementation: an active agent should have its own Docker-backed computer while it is awake, waiting for work, or executing work. This phase replaces one-off-only execution with a production-ready persistent per-agent container lifecycle. One-off containers may remain as a fallback or test helper, but they are no longer the primary alpha runtime.

Relevant files:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClientTest.java`
- `.internal-dev/notes/2026-05-11-container-runtime-selection.md`
- `.internal-dev/notes/2026-05-11-orchestration-refactor-deferred.md`

## Goal

Make Docker/Podman execution alpha-complete and demonstrably working: clear configuration, clear disabled/unavailable states, persistent per-agent containers for active agents, bounded exec sessions for tasks/jobs/workflows, mounted directory write proof, timeout cleanup proof, app shutdown cleanup, and model-backed execution proof when local model services are available.

## In Scope

- Add a persistent agent-container runtime manager that owns start, lookup, health, exec, idle handling, and stop behavior.
- Make agent create/delete/enable/disable semantics allocate, mount, activate, deactivate, and archive workspace data predictably.
- Remove agent cloning from the alpha surface and service API unless another current production caller requires it.
- Add a live integration test profile or disabled-by-default live test class for Docker/Podman.
- Add a command-oriented validation document or test notes for local live runtime validation.
- Verify an active agent container can write to `/home/agent`, `/workspace`, and `/output`.
- Verify exec timeout cleanup terminates the stuck exec/process without leaving the managed container in an unusable state.
- Verify app shutdown stops/removes managed containers according to policy.
- Verify app status endpoints and UI fragments show disabled, unavailable, missing image, ready, and per-agent container states.
- Add Docker container lifecycle management to the agents management page and agent detail page.
- Verify model-backed plan/task execution runs inside the agent container, uses Docker context, and materializes outputs.
- Update docs to state what "full alpha Docker support" means.

## Out of Scope

- Docker Compose, Swarm, Kubernetes, or multi-node orchestration.
- Private registry auth.
- Shipping a custom agent image unless the live smoke proves `python:3.11-slim` is insufficient for required alpha workflows.
- Keeping containers alive across application restarts. Alpha may recreate containers on app startup and preserve state through mounted host directories.
- Copying one agent's home/workspace into another agent through clone behavior. Cloning is removed for now to avoid ambiguous Docker and workspace ownership semantics.

## Implementation Steps

1. Fix configuration/documentation mismatch.
   - `2026-05-11-container-runtime-selection.md` recommends `python:3.11`, while `DockerRuntimeConfig` defaults to `python:3.11-slim`.
   - Decide based on alpha needs:
     - If agents need runtime `pip install` with compiled packages, change default to `python:3.11`.
     - If alpha only needs basic Python/filesystem execution, keep slim and update the note.
   - The worker must not leave docs and config in conflict.

2. Define lifecycle ownership and update package guidance.
   - Update `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AGENTS.md`.
   - New ownership contract:
     - Docker/Podman daemon is external and must already be running.
     - Spring owns one Docker API client for the application lifetime.
     - A new runtime manager owns Magenta-created agent containers.
     - Each active agent gets at most one managed container.
     - Tasks/jobs/workflows run as bounded exec sessions inside the agent container.
     - Mounted host directories carry persistence; container-local filesystem is cache/scratch.
     - Application shutdown stops/removes managed containers unless `magenta.docker.keep-containers-on-shutdown=true`.
   - Keep the existing one-off `execCommand(...)` only as a helper or fallback, not as the main orchestration path.

3. Add a persistent container model.
   - Add records/classes in the docker package, for example:
     - `AgentContainerHandle(agentId, containerId, status, dockerHost, image, startedAt, lastUsedAt, mounts, labels)`
     - `AgentContainerStatus`
     - `AgentExecResult`
     - `AgentContainerRuntimeService`
   - Use Docker labels on every managed container:
     - `magenta.managed=true`
     - `magenta.agent.id=<agentId>`
     - `magenta.runtime.generation=<appInstanceId>`
   - Container name should be deterministic and sanitized, for example `magenta-agent-<shortAgentId>`.
   - On startup, reconcile any existing matching container:
     - If running and image/config labels match, adopt it.
     - If exited or config is stale, remove and recreate it when the agent becomes active.
   - Store runtime state in memory for alpha. Persisted container metadata is optional because Docker labels are the source of reconciliation.

4. Define active agent semantics.
   - An agent is active when it is assigned work, has queued/running assignments, has an active job/workflow/task run, or is explicitly marked awake by an operator action.
   - Starting a task/job/workflow for an agent must call `ensureAgentContainer(agentId)` before execution.
   - `AgentProfileStatus.ACTIVE` means the agent is allowed to be awakened and receive work. It does not have to mean the container is currently running.
   - Add an explicit Docker/runtime state separate from profile status: stopped, starting, running, idle, stopping, error, unavailable.
   - Turning an agent "on" from the management page should set/keep profile status `ACTIVE` and call `ensureAgentContainer(agentId)`.
   - Turning an agent "off" should stop/sleep the container and prevent new assignment execution if the profile is set to `DISABLED`.
   - When an agent becomes idle, keep the container running for `magenta.docker.agent-idle-ttl-seconds` before stopping it.
   - Default idle TTL should be conservative for alpha, for example 1800 seconds.
   - Provide explicit stop action through service/API/UI if feasible.

5. Define agent create/delete/workspace lifecycle.
   - On agent creation:
     - create the `AgentProfile`
     - create or ensure the durable agent workspace row through `WorkspaceService.agentWorkspace(agentId, name)`
     - create or ensure the durable host home directory through `WorkspaceDirectoryService.agentHome(agentId)`
     - create or ensure the agent output root if a helper exists, or ensure it on first output materialization
     - do not automatically start the container unless the create form includes "start now" or the default policy says created agents are awake
   - The persistent `/home` mount must be the host path returned by `WorkspaceDirectoryService.agentHome(agentId)` mounted to `/home/agent`.
   - The workspace mount should be stable and agent-owned by default:
     - host `data/agents/{agentId}` or the `WorkspaceService.agentWorkspace(...)` root mounted to `/workspace`
     - task/job-specific work should use subdirectories under that root or additional leased mounts when project/job workspace access is needed
   - Outputs should be mounted at `/output` and map to the agent output root or the current run output directory, depending on which output contract the implementation chooses.
   - On agent disable:
     - stop/sleep the running container
     - leave home, workspace, outputs, links, inbox, jobs, and history intact
     - reject or pause new work assignment execution with a clear disabled-agent message
   - On agent delete:
     - do not hard-delete by default
     - prompt the operator with choices:
       - disable only
       - archive workspace data and disable
       - hard delete profile and data, requiring explicit confirmation text
     - archive should move or mark the workspace under an archive path such as `data/agents/.archive/{agentId}-{timestamp}` and stop/remove the container first
     - hard delete must stop/remove the container first, release workspace leases, then delete profile and selected filesystem data
   - If archive/delete filesystem behavior is too large for one pass, implement disable-only plus a modal/confirmation stub and log the archive work as a blocking follow-up, not as silent deletion.

6. Remove cloning for alpha.
   - Remove or hide any clone buttons/routes if present.
   - Remove `AgentProfileService.clone(String id)` if no production caller remains.
   - If a test or hidden API uses clone, change it to create a new clean agent instead.
   - Do not copy home directories, workspace links, output artifacts, inbox messages, jobs, or Docker containers between agents.

7. Implement persistent container start/exec/stop.
   - Start command should keep the container alive, for example `sleep infinity` or a small shell loop.
   - Mounts at minimum:
     - agent home to `/home/agent`
     - current execution workspace to `/workspace` or a stable agent workspace root with task/job subdirectories
     - output root to `/output`
   - Prefer stable agent-level mounts:
     - `/home/agent` maps to `WorkspaceDirectoryService.agentHome(agentId)`
     - `/workspace` maps to the agent workspace root or current job/project workspace when lease rules allow it
     - `/output` maps to the agent output root
   - For job/project workspaces, acquire a workspace lease before mounting or before using that path in exec.
   - Exec calls should use Docker exec API against the running container, set working directory, capture stdout/stderr, and enforce timeout.
   - If an exec times out, terminate the process/session. If the container cannot be trusted after timeout, restart the agent container and mark the exec failed.

8. Wire runtime execution to persistent containers.
   - Replace production plan/task/job execution paths that only inject Docker prompt context with actual container-backed execution where shell/tool work is required.
   - `OrchestrationRunnerService` and plan execution paths should pass `agentId`, `jobId`, `projectId`, `workspaceId`, and output directory context into the runtime manager.
   - Do not silently fall back to host execution when Docker is enabled and required.
   - If Docker is disabled, render a clear disabled state and fail Docker-required assignments with actionable errors.

9. Add live Docker test coverage.
   - Keep normal `mvn test` independent of a local daemon.
   - Add tests enabled only by a system property such as `-Dmagenta.docker.live=true`.
   - Test cases:
     - daemon ping and image availability
     - `ensureAgentContainer` creates a running container
     - agent creation ensures durable workspace and home paths without starting a container unless requested
     - agent disable stops/sleeps the container and preserves home/workspace/output data
     - delete/archive flow asks for an explicit archive/delete choice before filesystem removal
     - repeated `ensureAgentContainer` for the same agent reuses the same running container
     - exec writes files to mounted home/workspace/output dirs
     - stdout/stderr capture
     - non-zero exit code capture
     - timed-out exec fails cleanly and leaves the agent container healthy or restarts it
     - stop removes/stops the managed container
     - shutdown cleanup stops/removes all managed containers unless keep policy is enabled

10. Add an application-level Docker smoke path.
   - Prefer a focused test or documented command that exercises the persistent agent-container path used by plan execution.
   - Avoid adding production-only debug endpoints unless necessary.

11. Prove model-backed execution.
   - Use existing plan/task execution flow.
   - Create a small plan/task that writes one text output and one JSON output.
   - Run it with Docker enabled and a local model service if available.
   - Verify output artifacts are persisted and files exist under the output directory.
   - If Ollama or the configured model is unavailable, document the exact blocker and leave the live test instructions executable.

12. Strengthen status rendering tests.
   - Test `RuntimeController.dockerStatus()`.
   - Test `OrchestrationController` Docker status fragment for:
     - disabled
     - daemon unavailable
     - ready
     - active agent container count
     - per-agent container running/stopped/error state
   - Use stubs/mocks; do not require Docker for controller tests.

13. Add operational controls.
   - The agents management page at `/agents` and `/agents/_list` should show each agent's Docker state in the list/table:
     - disabled
     - daemon unavailable
     - image missing
     - stopped
     - starting
     - running
     - unhealthy/error
     - idle timeout pending if tracked
   - Agent detail Docker status at `/agents/_detail/{agentId}/docker-status` should show container id, image, uptime, last used, mounted workspace/output paths, and status.
   - Add operator actions on the agents management page and agent detail page:
     - start/wake container
     - stop/sleep container
     - restart container
     - inspect/refresh status
     - enable agent
     - disable agent
     - delete/archive agent
   - Delete/archive must require a confirmation step. The first click should not remove workspace data.
   - These actions should be HTMX form posts returning fragments. The agents list action should refresh the affected row or table; the detail action should refresh the Docker status panel.
   - Add endpoints in `OrchestrationController`, for example:
     - `POST /agents/_docker/{agentId}/start`
     - `POST /agents/_docker/{agentId}/stop`
     - `POST /agents/_docker/{agentId}/restart`
     - `GET /agents/_docker/{agentId}/status-row`
     - `POST /agents/_lifecycle/{agentId}/enable`
     - `POST /agents/_lifecycle/{agentId}/disable`
     - `GET /agents/_lifecycle/{agentId}/delete-confirm`
     - `POST /agents/_lifecycle/{agentId}/archive-and-disable`
   - Do not put Docker lifecycle logic in the controller; route through the runtime manager.

## Validation

Required normal commands:

```bash
mvn -q -Dtest=DockerRuntimeClientTest,AgentContainerRuntimeServiceTest test
mvn -q -Dtest=OrchestrationControllerTest test
mvn -q test
timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Required live commands when Docker/Podman is available:

```bash
systemctl --user status podman.socket
docker info
docker image inspect python:3.11-slim || docker pull python:3.11-slim
mvn -q -Dmagenta.docker.live=true -Dtest=DockerRuntimeClientLiveTest,AgentContainerRuntimeServiceLiveTest test
```

If the default image is changed to `python:3.11`, replace `python:3.11-slim` in the live commands.

Model-backed validation:

```bash
timeout 60s mvn -q spring-boot:run -Dspring-boot.run.arguments="--server.port=0 --magenta.docker.enabled=true"
```

Then run the smallest available plan/task execution path that uses the configured model and produces outputs. Record:

- run id
- model key
- Docker host
- agent id
- container id
- output artifact ids
- output file paths
- whether temp workspace cleanup occurred

## Exit Criteria

- Persistent per-agent container lifecycle is implemented and proven by tests or documented live evidence.
- Disabled/unavailable runtime states are clear and non-crashing.
- Exec timeout cleanup is proven without leaving broken containers behind.
- App shutdown cleanup policy is proven.
- Model-backed Docker execution inside an agent container is either proven or blocked only by a named missing local dependency.
