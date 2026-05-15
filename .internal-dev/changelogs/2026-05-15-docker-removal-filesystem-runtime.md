# 2026-05-15 - Docker Removal: Filesystem-Backed Agent Runtime

## Summary

Removed Docker/Podman as the agent runtime and replaced it with host filesystem-backed workspaces plus Bash execution. This is a breaking refactor — no dual-mode Docker/filesystem support remains.

## Files

### Deleted
- `ai/orchestration/docker/` — entire package (7 classes + AGENTS.md)
- `test/.../docker/DockerRuntimeClientTest.java`

### Modified
- `ai/orchestration/workspaces/WorkspaceDirectoryService.java` — new helpers: `agentWorkspace()`, `agentWorkspaceOutputs()`, `agentProjectLinks()`, `agentScratch()`, `migrateLegacyAgentDirs()`; deprecated `agentHome()` and `agentOutputRoot()`
- `ai/orchestration/workspaces/WorkspaceService.java` — rootRelativePath changed to `agents/<id>/workspace`
- `ai/orchestration/workspaces/OutputArtifactService.java` — removed Docker `/output/` path handling
- `ai/chat/tool/shell/AgentShellToolService.java` — removed Docker dependency; workspace-based host execution with aliases
- `ai/orchestration/runtime/OrchestrationTaskContext.java` — removed `containerOutputPath` (8 fields from 9)
- `ai/orchestration/runtime/OrchestrationRunnerService.java` — removed Docker runtime integration
- `ai/chat/plan/PlanService.java` — removed `containerOutputPath()`, `dockerRuntimeContext()` → `workspaceRuntimeContext()`
- `ai/orchestration/agents/AgentProfileService.java` — removed Docker lifecycle methods
- `api/web/OrchestrationController.java` — removed Docker UI, routes, tabs, actions; renamed "Container Exec" → "Shell Exec"
- `api/web/RuntimeController.java` — Docker endpoint replaced with `/api/runtime/status`
- `src/main/resources/application.yml` — removed `magenta.docker.*` block

### Added
- `ai/orchestration/workspaces/AgentWorkspaceStatus.java` — workspace health read model
- `ai/orchestration/workspaces/AgentWorkspaceStatusService.java` — workspace health service

### Updated Tests
- `AgentShellToolServiceTest.java` — rewritten for workspace-backed execution
- `PlanServiceTest.java` — updated for 8-arg OrchestrationTaskContext
- `OrchestrationRuntimeTest.java` — updated for new constructor signatures
- `OrchestrationControllerTest.java` — Docker assertions → workspace assertions
- `OutputArtifactServiceAttributionTest.java` — /output/ paths → filesystem paths

## Behavioral Impact

- Agent execution now runs via `ProcessBuilder`/Bash on the host inside `dataRoot/agents/<id>/workspace/`.
- Working directory aliases: blank → workspace, `outputs` → workspace/outputs, `scratch` → workspace/scratch.
- Agent outputs materialize under `workspace/outputs/<slug>-<runId>/`.
- Workspace health replaces container status in the operator UI.
- All Docker lifecycle controls (Wake/Sleep/Restart) and routes (`/_docker/*`) removed.
- `ShellExecResult.executionType` is `"bash"`; no `containerId`.

## Risks

- No OS-level isolation — execution is confined to `dataRoot` via path normalization checks.
- Startup validation deferred: unavailable Docker/Podman daemon blocked local Spring Boot startup test.

## Follow-up Items

- Run `WorkspaceDirectoryService.migrateLegacyAgentDirs(agentId)` if legacy `agents/<id>/home` or `agents/<id>/outputs` directories exist.
- Archive the plan suite in `.internal-dev/plans/filesystem-agent-runtime-refactor/.archive/`.
