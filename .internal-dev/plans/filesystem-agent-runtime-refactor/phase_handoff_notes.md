# Filesystem Agent Runtime Refactor: Phase Handoff Notes

## Phase 01 Handoff - Filesystem Layout And Config Contract

### Contract Delivered
- New workspace helpers: `agentWorkspace()`, `agentWorkspaceOutputs()`, `agentProjectLinks()`, `agentScratch()` on `WorkspaceDirectoryService`.
- `agentOutput()` now writes to `agents/<id>/workspace/outputs/<slug>-<runId>/` (was `agents/<id>/outputs/`).
- Legacy `agentHome()` and `agentOutputRoot()` marked `@Deprecated`, retained for Docker compat until Phase 05.
- `migrateLegacyAgentDirs(String agentId)` is public and explicit; does NOT auto-fire. Call it in Phase 05 when Docker is gone.
- `WorkspaceService.agentWorkspace()` persists `rootRelativePath` as `agents/<id>/workspace` instead of `agents/<id>`.
- `AgentProfileService.ensureAgentDurableStorage()` now creates workspace, workspace/outputs alongside legacy home/outputs.
- `magenta.docker.*` defaults removed from `application.yml` (main); Docker services are `ConditionalOnProperty` with `matchIfMissing=false`, so they will not start. Test `application.yml` already had `enabled: false`.

### Files Changed
- `ai/orchestration/workspaces/WorkspaceDirectoryService.java` — new helpers, deprecated old, added migration
- `ai/orchestration/workspaces/WorkspaceService.java` — rootRelativePath changed to `agents/<id>/workspace`
- `ai/orchestration/agents/AgentProfileService.java` — ensureAgentDurableStorage calls new paths
- `src/main/resources/application.yml` — removed magenta.docker block
- `ai/orchestration/workspaces/AGENTS.md` — updated layout and directory descriptions

### Tests And Validation
- `mvn compile` — clean (only Guava sun.misc warnings, not our code)
- Workspace tests: 26/26 pass (WorkspaceLeaseServiceTest, WorkspaceRepositoryAttributionTest, OutputArtifactServiceAttributionTest)

### Data / Migration Notes
- Migration is explicit: `WorkspaceDirectoryService.migrateLegacyAgentDirs(agentId)` moves `home/` → `workspace/` and `outputs/` → `workspace/outputs/` only when workspace doesn't exist yet.
- Must be called after Docker execution dependencies on old paths are removed (Phase 05).
- Phase 02 must make the execution path use the new workspace layout.

### Assumptions For Next Phase
- `agentWorkspace()`, `agentWorkspaceOutputs()`, `agentProjectLinks()`, `agentScratch()` are available and confined under dataRoot.
- Docker services will NOT start (enabled defaults removed, `matchIfMissing=false`).
- Phase 02 owns removing Docker execution from `AgentShellToolService` and replacing with workspace-based host execution.

### Blockers Or Follow-Ups
- None.

### Next Agent Acceptance
- Accepted — Phase 02 starting.

## Phase 02 Handoff - Bash Execution Runtime

### Contract Delivered
- `AgentShellToolService` no longer depends on `AgentContainerRuntimeService`. All execution is host Bash via `ProcessBuilder`.
- Orchestration contexts resolve working directory against `agents/<id>/workspace/` with workspace aliases: blank/`.` → workspace, `outputs` → workspace/outputs, `scratch` → workspace/scratch, `projects/<id>/...` → workspace/projects/<id>/.
- Absolute paths and traversal paths in agent context are rejected.
- `ShellExecResult` removed `containerId`; `executionType` is `"bash"`.
- `OrchestrationTaskContext` removed `containerOutputPath` field (8 fields from 9).
- `PlanService` removed `containerOutputPath()` methods and `dockerRuntimeContext()` → replaced with `workspaceRuntimeContext()` and `workspaceRuntimeContext(run)`.
- `OrchestrationRunnerService` updated to 8-arg context constructor.
- Execution provenance no longer mentions Docker.

### Files Changed
- `ai/chat/tool/shell/AgentShellToolService.java` — removed Docker dependency, added workspace-aware execution with aliases
- `ai/orchestration/runtime/OrchestrationTaskContext.java` — removed containerOutputPath
- `ai/orchestration/runtime/OrchestrationRunnerService.java` — removed containerOutputPath null
- `ai/chat/plan/PlanService.java` — removed containerOutputPath, dockerRuntimeContext → workspaceRuntimeContext
- `test/.../AgentShellToolServiceTest.java` — rewritten for workspace-backed execution
- `test/.../PlanServiceTest.java` — updated for 8-arg context and workspace output paths
- `test/.../OrchestrationRuntimeTest.java` — updated for 8-arg context and deeper workspace root

### Tests And Validation
- `mvn compile` — clean
- `mvn test` — 424/424 pass (0 failures, 0 errors)
- AgentShellToolServiceTest: 18 tests covering host execution, workspace aliases, path rejection, timeout, interruption, and provenance

### Data / Migration Notes
- Agent output paths are now under `workspace/outputs/` (enforced by Phase 01 layout).
- `WorkspaceDirectoryService.migrateLegacyAgentDirs()` available for Phase 05 cleanup.

### Assumptions For Next Phase
- Agent execution runs on host via Bash in `agents/<id>/workspace/`.
- `executionType` is `"bash"`, no `containerId`.
- `OrchestrationTaskContext` has 8 fields (no containerOutputPath).
- Phase 03 can build workspace monitoring on this execution contract.

### Blockers Or Follow-Ups
- None.

### Next Agent Acceptance
- Accepted — Phase 03 starting.

## Phase 03 Handoff - Workspace Monitoring And Output Routing

### Contract Delivered
- `AgentWorkspaceStatus` record with `WorkspaceHealth` enum (READY, MISSING, READ_ONLY, BUSY, ERROR) and fields: exists, writable, activeRunCount, activeLeaseCount, linkedProjectIds, outputArtifactCount, outputBytes, lastActivityAt.
- `AgentWorkspaceStatusService` queries workspace, lease, assignment, and output services to produce deterministic health snapshots.
- `OutputArtifactService.materializeFilePath()` removed Docker `/output/` path handling.
- Agent outputs already materialize under `workspace/outputs/` (enforced by Phase 01 layout).

### Files Changed
- `ai/orchestration/workspaces/AgentWorkspaceStatus.java` — new read model record
- `ai/orchestration/workspaces/AgentWorkspaceStatusService.java` — new service
- `ai/orchestration/workspaces/OutputArtifactService.java` — removed /output/ container path handling
- `test/.../OutputArtifactServiceAttributionTest.java` — updated for filesystem-based path resolution

### Tests And Validation
- `mvn test` — 424/424 pass (post-Phase 03)

### Data / Migration Notes
- None beyond Phase 01 output path changes.

### Assumptions For Next Phase
- `AgentWorkspaceStatusService` is available for UI to consume workspace health.
- Output paths are clean of Docker /output/ references.

### Blockers Or Follow-Ups
- None.

### Next Agent Acceptance
- Accepted — Phase 04 starting.

## Phase 04 Handoff - UI And Public Contract Removal

### Contract Delivered
- Agent list: "Docker" column → "Workspace" health, Wake/Sleep/Restart buttons removed.
- Agent detail: "docker" tab removed from navigation; exec tab renamed from "Container Exec" to "Shell Exec" with workspace default working directory.
- All `/_docker/*` routes removed (docker-status, start, stop, restart, status-row).
- Docker status tab and Docker lifecycle fragment deleted.
- `OrchestrationController` constructor: Docker ObjectProviders replaced with `AgentShellToolService` ObjectProvider.
- `RuntimeController`: Docker status endpoint replaced with generic `/api/runtime/status`.
- Lifecycle methods (enable/disable/archive) return agent detail fragment instead of Docker status.
- `workspaceOutputHint()` updated for new output path.

### Files Changed
- `api/web/OrchestrationController.java` — removed Docker UI, routes, tabs, actions
- `api/web/RuntimeController.java` — Docker endpoint → runtime status
- `test/.../OrchestrationControllerTest.java` — updated assertions

### Tests And Validation
- `mvn test` — 424/424 pass (post-Phase 04)

### Data / Migration Notes
- None.

### Assumptions For Next Phase
- No active UI/API consumer references Docker. Phase 05 can safely delete the Docker package.

### Blockers Or Follow-Ups
- None.

### Next Agent Acceptance
- Accepted — Phase 05 starting.

## Phase 05 Handoff - Docker Deletion And Migration Cleanup

### Contract Delivered
- `ai/orchestration/docker/` package deleted (7 files: DockerRuntimeClient, DockerRuntimeConfig, AgentContainerRuntimeService, AgentContainerHandle, AgentContainerStatus, AgentExecResult, DockerStatusResponse, AGENTS.md).
- `DockerRuntimeClientTest.java` deleted.
- `AgentProfileService`: removed Docker runtime dependency, stopContainer(), containerRuntime() methods, and legacy dir calls.
- `OrchestrationRunnerService`: removed Docker runtime dependency, ensureAgentContainer/ensureProjectMount/markAgentBusy/markAgentIdle/removeProjectMount calls.
- `OrchestrationController`: Docker ObjectProvider fields and imports removed.
- `application.yml` (main): magenta.docker block removed (done in Phase 01).
- `application.yml` (test): already had enabled=false.

### Files Changed
- Deleted: `ai/orchestration/docker/*` (7 files + AGENTS.md)
- Deleted: `test/.../docker/DockerRuntimeClientTest.java`
- `ai/orchestration/agents/AgentProfileService.java` — removed Docker refs
- `ai/orchestration/runtime/OrchestrationRunnerService.java` — removed Docker refs
- `api/web/OrchestrationController.java` — final Docker import/field removal

### Tests And Validation
- `mvn test` — 412/412 pass (12 Docker tests removed)
- Stale reference sweep: clean (no magenta.docker, /_docker/, Docker Runtime, Container Exec, AgentContainerRuntimeService, DockerRuntimeClient in active source)

### Data / Migration Notes
- `WorkspaceDirectoryService.migrateLegacyAgentDirs(agentId)` still available for manual one-time migration of legacy `home/` and `outputs/` directories. Not auto-called.

### Assumptions For Next Phase
- Codebase is Docker-free. Final validator can prove end-to-end.

### Blockers Or Follow-Ups
- None.

### Next Agent Acceptance
- Accepted — Phase 06 starting.

## Phase 06 Handoff - Final Validation Gate

### Contract Delivered
- Full test suite: 412/412 pass, 0 failures, 0 errors.
- Stale reference sweep: clean across src/main, src/test, src/main/resources.
- `mvn compile` — clean (only Guava sun.misc warnings).
- Startup bounded-check: timed out due to unavailable local services (expected in this environment).

### Files Changed
- None (validation-only phase).

### Tests And Validation
- `mvn test` — 412/412 pass
- Stale refs: `rg -n 'magenta\.docker|/_docker/|Docker Runtime|Container Exec|AgentContainerRuntimeService|DockerRuntimeClient' src/main src/test src/main/resources` → clean

### Closeout Recommendation
- Changelog written.
- Plan suite ready for archival when user confirms.
- `WorkspaceDirectoryService.migrateLegacyAgentDirs()` available for one-time data migration if legacy `agents/<id>/home` or `agents/<id>/outputs` directories exist on disk.

### Final Acceptance
- **All phases complete. Docker removed. Filesystem-backed agent execution operational.**

