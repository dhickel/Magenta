# Root-Relative Path Storage Review

## Scope

Review-only pass for Magenta-owned path persistence and read/download call sites in the root-relative workspace migration plan. Inspected output artifacts, plan runs, workflow runs, job runs, workspace links, workspace roots, chat file downloads, output downloads, and secondary read models that parse stored path strings.

Primary files reviewed:

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatFileService.java`

## Findings

High - Output downloads have an independent direct `Path.of(storedDbValue)` reader.

- `OutputArtifactService.loadContent` resolves `artifact.filePath()` with `Path.of(filePath).normalize().toRealPath()` before checking `dataRoot`.
- `OutputController.download` duplicates that logic instead of delegating to the service.
- If only `OutputArtifactService.loadContent` is updated, binary downloads will fail for new relative `run_output_artifacts.file_path` values because `Path.of("agents/...")` resolves against the process working directory.
- Required constraint: expose a service method such as `openArtifactFile`, `resolveArtifactPath`, or `resolveExistingArtifactFile` and make both text content and download use the same root-relative helper.

High - Plan run path columns are both persisted DB values and runtime filesystem inputs.

- `PlanService.startRun` currently stores `tempDir.toRealPath().toString()` and `realOutputDir.toString()` into `plan_runs.temp_workspace_path` and `plan_runs.output_directory`.
- `discoverLooseArtifactsForRun`, `materializeRunOutputs`, and `cleanupTempForRun` call `Path.of(run.outputDirectory())` or `Path.of(run.tempWorkspacePath())`.
- `OrchestrationTaskContext.withExecutionPaths` receives the host durable workspace path, output path, and temp path; shell/file tools later call `Path.of(path).toRealPath()` on those context values.
- Required constraint: persist relative values in the `PlanRun`, but keep a resolved absolute/current-root host path for live `OrchestrationTaskContext`. Do not pass stored relative DB values into tool context fields named `host...`.

High - Workflow run path columns flow back into active execution context.

- `WorkflowRunner.createRun` stores `workspacePath.toString()` and `outputPath.toString()`.
- `outputPathFor` returns `Path.of(run.outputDir())`; log/final output materialization uses this path.
- `executionContextFor` forwards `run.workspacePath()` and `run.outputDir()` directly into `OrchestrationTaskContext`, and `inferDurableWorkspacePath` parses `Path.of(run.outputDir())`.
- Required constraint: store relative values, but resolve them before `outputPathFor`, `executionContextFor`, `inferDurableWorkspacePath`, resume execution, and legacy `TaskNodeExecutor.execute(..., workspacePath)`.

High - Job run path columns feed job summaries, checkpoints, and job workspace tool aliases.

- `JobService.startRun` stores absolute `workspacePath` and `outputDir`.
- `JobService.executionSummary` returns these strings directly to the UI read model.
- `OrchestrationRunnerService` writes `jobWorkspacePath` and `jobOutputDir` into assignment checkpoints and calls `context.withJobWorkspacePath(jobRun.workspacePath())`.
- Required constraint: decide whether assignment checkpoint JSON should remain host-path runtime state or switch to relative display state. Regardless, `withJobWorkspacePath` must receive a resolved host path, not the relative stored `job_runs.workspace_path`.

Medium - Workspace PATH links validate paths but persist the caller's original target.

- `WorkspaceService.addLink` validates `link.target()` under `dataRoot`, then saves `link.target()` unchanged.
- For new root-relative semantics, PATH links under `dataRoot` should persist the normalized data-root-relative target. Non-PATH links should keep existing semantics.
- Existing absolute current-root links need compatibility display/read behavior. Stale old-root links should not be auto-repaired.

Medium - Secondary read models parse stored artifact/link strings directly.

- `AgentWorkspaceStatusService` calculates artifact bytes with `Path.of(a.filePath())`, which will undercount new relative artifacts.
- The same class infers linked project IDs from raw `link.target().contains("projects/")`; relative storage is compatible, but old absolute current-root values still work only by substring accident.
- These are not download blockers, but they should be included in phase tests so status widgets do not silently regress.

Medium - Display-only code must not require files to exist.

- Historical output/workspace files may be intentionally dropped in this migration.
- Read models such as job execution summaries and workspace link tables should use a helper display method or raw relative value. They should not call `toRealPath()` just to render a stale row.
- Filesystem operations should call `resolveExisting...`; display-only paths should call `display` or `resolveForDisplay` that does not fail on missing files.

Low - `PlanCompletionService.readArtifact` already handles relative paths against `AiConfig.dataRoot`, but should align with the helper for consistent stale-path messaging.

- It accepts relative paths and current-root absolute paths under `dataRoot`.
- It returns a text error instead of throwing, which is appropriate for validation context.
- It can remain behaviorally equivalent, but using the shared helper would keep stale old-root errors consistent.

Low - `workspaces.root_relative_path` is already relative for agent/project workspaces.

- `WorkspaceService.agentWorkspace` uses `agents/<agentId>/workspace`.
- `WorkspaceService.projectWorkspace` uses `projects/<projectId>/workspace`.
- `WorkspaceService.jobWorkspace` still uses `jobs/<jobId>`, while `WorkspaceDirectoryService.jobWorkspace` uses `jobs/<jobId>/workspace`; this is the compatibility oddity already called out in the plan and should not be expanded during this migration.

## Risk Assessment

The highest risk is mixing storage strings with runtime host paths. The new DB contract should be root-relative, but shell/file tools, symlink project links, temp cleanup, output materialization, and download streaming still need resolved filesystem paths under the current `dataRoot`.

The second major risk is incomplete replacement of direct `Path.of(storedDbValue)` readers. Relative DB strings will look syntactically valid but resolve against the process working directory unless every storage-column reader goes through the helper.

Compatibility should be intentionally narrow:

- Relative stored values resolve under current `dataRoot`.
- Existing absolute values resolve only if they are under current `dataRoot`.
- Absolute values from stale old roots fail at the operation that needs a path, with a clear stale/outside-root message.
- Missing files from dropped workspace/output/runtime trees do not delete records and do not break startup.

## Recommendations

- Add one root-relative path service and make it the only service used for persisted Magenta-owned path columns.
- Provide separate helper methods for storage, filesystem use, and display:
  - `store(Path)` returns slash-separated data-root-relative storage.
  - `resolve(String)` returns a confined current-root path without requiring existence.
  - `resolveExistingFile(String)` and `resolveExistingDirectory(String)` call `toRealPath()` and enforce existence/type.
  - `display(String)` returns a stable human-facing string without requiring the path to exist.
- Keep all `OrchestrationTaskContext.host...` fields as host filesystem paths. Resolve stored relative run paths before constructing or updating this context.
- Do not change repository schemas or record field names in this phase; change service-level semantics and tests.
- Prefer delegating output downloads to `OutputArtifactService` instead of duplicating artifact path resolution in controllers.
- Keep chat file behavior separate from DB path migration; chat files are filesystem-led under `dataRoot/chats/<conversationId>/files`.

## Path Column Matrix

| Column or path carrier | Current writer | Current readers | Target storage | Required change |
| --- | --- | --- | --- | --- |
| `run_output_artifacts.file_path` | `OutputArtifactService.saveArtifact` callers persist `Path.toString()` | `OutputArtifactService.loadContent`, `OutputController.download`, `AgentWorkspaceStatusService` | Data-root-relative slash path | Store with helper; resolve for content/download/status bytes; preserve current-root absolute compatibility. |
| `plan_runs.output_directory` | `PlanService.startRun` stores `realOutputDir.toString()` | `materializeRunOutputs`, `discoverLooseArtifactsForRun`, `resolveOutputAgentId`, `workspaceRuntimeContext`, UI display | Data-root-relative slash directory | Store relative; resolve before filesystem operations; use display helper for UI/prompt display. |
| `plan_runs.temp_workspace_path` | `PlanService.startRun` stores `tempDir.toRealPath().toString()` | `cleanupTempForRun`; live context allocation | Data-root-relative slash directory | Store relative; resolve before cleanup; keep live `OrchestrationTaskContext.hostRunPath` absolute/current-root. |
| `workflow_runs.workspace_path` | `WorkflowRunner.createRun` stores `workflowTemp(...).toString()` | `executionContextFor`, resume paths, legacy `TaskNodeExecutor` | Data-root-relative slash directory | Store relative; resolve before execution context and resume; stale old-root rows fail only on execution/resume. |
| `workflow_runs.output_dir` | `WorkflowRunner.createRun` stores `outputPath.toString()` | `outputPathFor`, `inferDurableWorkspacePath`, log/final output materialization, execution context | Data-root-relative slash directory | Store relative; resolve before materialization and context; infer durable workspace from resolved host path. |
| `job_runs.workspace_path` | `JobService.startRun` stores persistent job workspace absolute path when enabled | `JobService.executionSummary`, `OrchestrationRunnerService.installJobWorkspaceContext`, assignment checkpoint | Data-root-relative slash directory when present | Store relative; resolve before `withJobWorkspacePath`; use display helper in summaries/checkpoints unless checkpoint is explicitly runtime-host state. |
| `job_runs.output_dir` | `JobService.startRun` stores absolute output path | `JobService.executionSummary`, assignment checkpoint | Data-root-relative slash directory | Store relative; display without `toRealPath`; resolve if future job-output file operations need it. |
| `workspace_links.target` for `PATH` links | `WorkspaceService.addLink` validates then stores raw target | Workspace link table display, `AgentWorkspaceStatusService` project-id inference | Data-root-relative slash path when target is under `dataRoot` | Normalize/store relative for new PATH links; support old absolute current-root values for read/display; reject outside-root writes. |
| `workspaces.root_relative_path` | `WorkspaceService.createWorkspace` | workspace display, effective workspace labels, path confinement | Already relative | Keep as-is for agent/project; do not normalize warm stale job/legacy rows in this phase. |
| Chat files | No per-file DB column | `ChatFileService`, `ChatFileController` | Not applicable | No DB rewrite; continue resolving under `dataRoot/chats/<conversationId>/files`. |

## Required Call-Site Changes

- `OutputArtifactService`: inject helper; store relative in all `saveArtifact` call paths; resolve existing files in `loadContent`; expose a shared download/open path method.
- `OutputController`: remove direct `Path.of(artifact.filePath())`; delegate path resolution to `OutputArtifactService`.
- `AgentWorkspaceStatusService`: resolve artifact paths through the helper before size checks, or count metadata only.
- `PlanService.startRun`: persist relative `tempWorkspacePath` and `outputDirectory`; retain resolved host paths for `OrchestrationTaskContext`.
- `PlanService.materializeRunOutputs`, `discoverLooseArtifactsForRun`, `cleanupTempForRun`, `resolveOutputAgentId`: resolve stored paths through the helper before filesystem/path parsing.
- `WorkflowRunner.createRun`: persist relative `workspacePath` and `outputDir`; use resolved host paths for async context.
- `WorkflowRunner.outputPathFor`, `executionContextFor`, `inferDurableWorkspacePath`, resume paths, and legacy task-node executor path handoff: resolve stored values first.
- `JobService.startRun`: persist relative `workspacePath` and `outputDir`.
- `JobService.executionSummary`: render display values rather than assuming stored values are host paths.
- `OrchestrationRunnerService.installJobWorkspaceContext`: resolve `jobRun.workspacePath()` before `withJobWorkspacePath`.
- `WorkspaceService.addLink`: store normalized relative target for PATH links under `dataRoot`; keep non-PATH target behavior unchanged.
- `PlanCompletionService.readArtifact`: optional but recommended helper adoption for consistent relative/absolute/stale handling.

## Follow-ups

- Add tests that directly seed old absolute current-root values and stale old-root values for each path column.
- Add a regression test for output binary download with a relative `run_output_artifacts.file_path`.
- Add a regression test that a workflow/job/task execution context still contains host paths after DB storage becomes relative.
- Document that assignment checkpoint path-like JSON is compatibility/runtime state, not part of this phase's authoritative path-column rewrite, unless the implementation chooses to convert those checkpoint values too.
- Keep future repair/import/startup diagnostics separate from this migration phase, as the implementation plan already states.
