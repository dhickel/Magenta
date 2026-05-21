# Date

2026-05-21

# Change Summary

Technical closeout for the root-relative workspace migration. This entry records the implementation details behind root-owned database placement, data-root-relative path storage, compatibility reads, and validation gates.

# Files

- Root/config: `MagentaRootConfiguration`, `MagentaRootProperties`, `AiUserConfigConfiguration`, `application.yml`, `config/ai-config.example.json`.
- Path helper: `RootRelativePathService`.
- Output artifacts: `OutputArtifactService`, `OutputController`, `AgentWorkspaceStatusService`.
- Run paths: `PlanService`, `WorkflowRunner`, `JobService`, `OrchestrationRunnerService`.
- Workspaces: `WorkspaceService`.
- Docs/tests: focused technical/end-user docs and test suites listed in the implementation plan.

# Behavioral Impact

Root defaults:
- `magenta.root.path` defaults to `${user.home}/.magenta`.
- Default SQLite URL is `jdbc:sqlite:${magenta.root.path}/magenta.sqlite?foreign_keys=true`.
- SQLite parent directories are created before datasource/SQL initialization for file-backed SQLite URLs.
- In-memory SQLite URLs and URI-memory forms are ignored by parent-directory setup.
- Missing AI `dataRoot` resolves to `<magenta.root.path>/root`.
- Relative AI `dataRoot` resolves under `magenta.root.path`.
- Absolute AI `dataRoot` remains supported.

Path storage:
- `RootRelativePathService.store(Path)` persists slash-separated values relative to `WorkspaceDirectoryService.dataRoot()`.
- `resolve(String)` accepts relative values and legacy absolute current-root values.
- Existing file/directory helpers call `toRealPath()` and enforce type/confinement.
- Display helpers do not require existence and do not create directories.
- Traversal and stale/outside-root absolute paths are rejected.

Persisted columns now write relative values for new rows:
- `run_output_artifacts.file_path`
- `plan_runs.output_directory`
- `plan_runs.temp_workspace_path`
- `workflow_runs.workspace_path`
- `workflow_runs.output_dir`
- `job_runs.workspace_path`
- `job_runs.output_dir`
- `workspace_links.target` for new `PATH` links

Runtime host paths:
- Tool/run contexts still receive resolved host filesystem paths.
- Plan, workflow, and job execution resolve stored relative values before materialization, cleanup, resume, and runtime context installation.
- Output content and downloads share `OutputArtifactService.resolveArtifactFile(...)`.

# Risks

- Any future code that calls `Path.of(storedDbValue)` on owned path columns can reintroduce process-relative path bugs.
- Old absolute values outside the current data root intentionally remain unrepaired.
- Unstructured JSON path values are not authoritative and were not rewritten.
- Workspace files are dropped by not being carried forward; they are not deleted from old roots.

# Follow-up Items

- Add a migration/import command that can scan a DB and root together, report stale path rows, copy chat files, and optionally rewrite owned columns.
- Add admin-facing diagnostics for stale path rows and missing files.
- Consider a future schema marker that distinguishes storage path kind when compatibility windows shrink.
- Keep future UI validation seeded with non-running fixture state so model-backed assignments do not start background LLM work during visual checks.
