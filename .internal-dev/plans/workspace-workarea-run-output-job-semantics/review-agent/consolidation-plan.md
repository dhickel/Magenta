# XHigh Layout Audit Consolidation

## Review Artifact

- Source: `review-agent/xhigh-layout-audit.md`
- Method: read-only xhigh Codex review of path/layout usage across production code, tests, docs, specs, knowledge, and package guides.

## Integrated Decisions

- The shared structural layout authority is named `WorkspacePathLayout` and lives in `io.mindspice.magenta2.ai.orchestration.workspaces`.
- `WorkspaceDirectoryService` remains responsible for data-root confinement, directory creation, and realpath checks; it consumes `WorkspacePathLayout` rather than inventing raw structural strings.
- `RootRelativePathService` remains persistence-focused and must not become the structural layout authority.
- Tool alias resolution should be consolidated behind a shared alias model/resolver used by file tools, shell tools, prompt text, and tests.
- Path-shape inference methods such as `PlanService.resolveOutputAgentId(...)` and `WorkflowRunner.inferDurableWorkspacePath(...)` should be replaced by explicit DB/run attribution before old path shapes are removed.

## Added/Adjusted Worker Requirements

- Phase 02 owns `WorkspacePathLayout`, low-level path tests, Work Area ID-backed disk semantics, and additive DB fields.
- Phase 03 owns run-local output staging, output promotion, job-owned workspace removal from new writes, and shared tool alias/prompt behavior.
- Phase 04 owns controller/status display path cleanup, including `OrchestrationController` and `AgentWorkspaceStatusService` style hints.
- Phase 05 owns search-based regression checks to prevent scattered literals outside `WorkspacePathLayout`, compatibility tests, and docs.

## Review Hotspots To Recheck During Validation

- `WorkspaceDirectoryService`
- `WorkspaceService`
- `OutputDirectoryService`
- `WorkAreaService`
- `PlanService.workspaceRuntimeContext(...)`
- `PlanService.resolveOutputAgentId(...)`
- `WorkflowRunner.inferDurableWorkspacePath(...)`
- `JobService`, `JobRun`, `JobRepository`, and `JobExecutionSummary`
- `AgentFileToolService` and `AgentShellToolService`
- `OrchestrationController.workspaceOutputHint(...)`
- `AgentWorkspaceStatusService`
- Tests and docs hard-coded to old paths

## Consolidated Search Commands

```bash
rg -n 'agents/|projects/|chats/|runtime/task-runs|runtime/workflow-runs|outputs/tasks|outputs/workflows|outputs/jobs|workspace/outputs|workspace/projects|workspace/scratch|jobs/' src/main/java src/test/java docs .internal-dev --glob '!**/.archive/**'
rg -n 'WorkspaceOwnerType\.JOB|jobWorkspace|jobOutput|persistentWorkspaceEnabled|hostJobWorkspacePath|workspacePath\(\)|outputDir\(\)' src/main/java src/test/java docs
rg -n 'resolveOutputAgentId|inferDurableWorkspacePath|agentOutput\(|taskTempPath\(|workflowTemp\(|jobAssignmentWorkspace\(' src/main/java
```

