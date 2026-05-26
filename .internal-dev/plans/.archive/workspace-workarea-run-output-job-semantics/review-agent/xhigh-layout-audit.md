## 1. Audit Scope And Method

Read-only audit of workspace/path usage in production code, tests, docs, `.internal-dev` specs/knowledge, and package guides. No product code was edited and no tests were run.

Primary focus areas were `WorkspaceDirectoryService`, workspace/work-area services, output allocation/promotion paths, job/runtime execution paths, file/shell tool aliases, prompt text, controllers/status DTOs, persistence fields, and test hard-codings.

## 2. Existing Path/Layout Hotspots

`WorkspaceDirectoryService` is the current de facto path authority, but it still embeds obsolete structural strings directly: `agents/<id>/workspace`, `agents/<id>/home`, `agents/<id>/outputs`, `runtime/task-runs`, `runtime/workflow-runs`, `outputs/tasks`, `outputs/workflows`, `outputs/jobs`, `jobs/<jobId>/workspace`, `jobs/<jobId>/outputs`, `projects/<projectId>/workspace`, and `chats/<conversationId>/files`.

`WorkspaceService` duplicates root construction instead of delegating fully: `createWorkspace(...)`, `assignmentPath(...)`, archive/delete paths, and private confinement logic build `agents/...`, `jobs/...`, and `projects/...` paths manually.

`OutputDirectoryService` is the correct allocation boundary, but it encodes old output categories and run-type strings: `TASK_RUN`, `WORKFLOW_RUN`, `JOB_RUN`, plus `taskOutput`, `workflowOutput`, and `jobAssignmentOutput`.

`WorkAreaService` is DB-backed, which is good, but current disk semantics are not the target model. It stores `areaRelativePath` from selected directories, uses `home` as a constant, and lets filesystem path/display concerns bleed together. Target behavior needs stable disk IDs under `workareas/<workAreaId>` and DB-owned display names.

`AgentShellToolService` and `AgentFileToolService` duplicate alias resolution for `workspace`, `root`, `outputs`, `run`, `job`, `work`, `scratch`, and `projects/<projectId>`. `AgentFileTools` and related tool docs also contain prompt-only path assumptions.

`PlanService.workspaceRuntimeContext(...)` is a major prompt-only contract hotspot. It describes `workspace/`, `work/`, `outputs/`, `run/`, and `scratch/`, warns about direct writes to `workspace/outputs`, and computes run output context separately. This should be generated from the same layout/alias model used by tools.

`WorkflowRunner.inferDurableWorkspacePath(...)` and `PlanService.resolveOutputAgentId(...)` parse old path shapes to infer ownership. These are brittle and should be replaced by explicit DB/run attribution before the layout changes fully land.

`JobService`, `JobRun`, and `JobRepository` still model jobs as owning or carrying workspace directories. This conflicts directly with the new rule: jobs never own workspace directories.

Controller/status display paths are duplicated too. `OrchestrationController` has `outputPathHint` strings for `agents/.../workspace/outputs`, `jobs/.../outputs`, and `projects/.../workspace`; `AgentWorkspaceStatusService` and `AgentWorkspaceStatus` default display paths to `agents/<id>/workspace`.

Tests and docs are heavily coupled to the old layout: `PlanServiceTest`, `OrchestrationRuntimeTest`, `AgentShellToolServiceTest`, `AgentFileToolServiceTest`, `WorkspacePathSegmentValidationTest`, `OutputArtifactServiceAttributionTest`, chat file tests, and docs under `docs/technical` / `docs/end-user`.

## 3. Recommended Source Of Truth Design

Create one application-owned structural layout authority in the workspace package, for example:

`io.mindspice.magenta2.ai.orchestration.workspaces.WorkspacePathLayout`

This should not be config-backed. It should define static structural segments and dynamic helper methods:

Static segments:
`workspace`, `chats`, `agents`, `projects`, `home`, `workareas`, `outputs`, `runs`, `files`.

Target helpers:
`agentWorkspaceRoot(agentWorkspaceId)` -> `workspace/<agentWorkspaceId>`  
`agentHome(agentWorkspaceId)` -> `workspace/<agentWorkspaceId>/home`  
`workArea(agentWorkspaceId, workAreaDiskId)` -> `workspace/<agentWorkspaceId>/workareas/<workAreaId>`  
`finalOutputs(agentWorkspaceId)` -> `workspace/<agentWorkspaceId>/outputs`  
`runRoot(agentWorkspaceId, runId)` -> `workspace/<agentWorkspaceId>/runs/<runId>`  
`runOutputs(agentWorkspaceId, runId)` -> `workspace/<agentWorkspaceId>/runs/<runId>/outputs`  
`chatFiles(conversationId)` -> `chats/<conversationId>/files`  
`projectRoot(projectId)` and `agentMetadata(agentId)` if those roots remain structural.

Use `WorkspaceDirectoryService` for filesystem creation, confinement, and `dataRoot` resolution. It should consume `WorkspacePathLayout`; it should not remain the place where raw layout strings are invented.

Keep `RootRelativePathService` persistence-focused. It should continue to store/resolve data-root-relative paths, not become the structural layout authority.

Add one shared alias resolver/model, for example `WorkspaceAliasResolver` or `WorkspaceToolPathResolver`, used by both file and shell tools. It should own aliases such as `workspace/`, `root/`, `outputs/`, `run/`, and any retained compatibility aliases.

Prompt text should be rendered from the same layout/alias source. `PlanService.workspaceRuntimeContext(...)`, tool descriptions, and docs should not hand-author structural path contracts independently.

## 4. Migration/Compatibility Risks

Existing persisted rows may contain old root-relative or absolute paths in `workspaces.root_relative_path`, `work_areas.root_relative_path`, `work_areas.area_relative_path`, `plan_runs.output_directory`, `plan_runs.temp_workspace_path`, `workflow_runs.workspace_path`, `workflow_runs.output_dir`, `job_runs.workspace_path`, `job_runs.output_dir`, `run_output_artifacts.file_path`, and `workspace_links.target`.

Old path readers should remain tolerant through `RootRelativePathService`, but new writes should use only the new layout.

Do not preserve job workspace creation as a normal code path. Keep old `WorkspaceOwnerType.JOB`, `jobWorkspace`, `jobOutput`, and `JobRun.workspacePath` behavior only as compatibility/read-display/migration surface if needed.

Path-shape inference is a risk. Replace `PlanService.resolveOutputAgentId(...)` and `WorkflowRunner.inferDurableWorkspacePath(...)` with explicit owner/run/work-area metadata before removing old path shapes.

Work Area migration needs care: display names must not determine disk paths. Existing marked directories may need compatibility handling, while new Work Areas should use stable disk IDs.

Docs and package guides are currently stale, especially `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md` and `docs/technical/workspaces-tools-outputs.md`.

## 5. Worker-Planning Implications

Plan this as a staged refactor, not a search-and-replace.

First worker unit: add `WorkspacePathLayout` and migrate `WorkspaceDirectoryService` plus low-level path tests. This establishes the source of truth without changing all semantics at once.

Second worker unit: migrate Work Area semantics to stable disk IDs and `workspace/<agentWorkspaceId>/home` / `workareas/<workAreaId>` paths.

Third worker unit: migrate output staging so active execution uses `runs/<runId>/outputs`, then promotion moves declared outputs to final destinations. `OutputDirectoryService`, `PlanService`, `WorkflowRunner`, and `JobService` are the main targets.

Fourth worker unit: remove job-owned workspace semantics from new writes. Jobs should reference agent/work-area/run context, not own directories.

Fifth worker unit: consolidate tool alias resolution and prompt rendering. `AgentFileToolService`, `AgentShellToolService`, `AgentFileTools`, and `PlanService.workspaceRuntimeContext(...)` should use the same alias/layout model.

Sixth worker unit: update controllers, status DTOs, tests, docs, package guides, and `.internal-dev` knowledge/specs.

## 6. Validation Coverage Required

Add focused unit coverage for `WorkspacePathLayout`: expected target paths, invalid segment rejection, traversal rejection, and string/path rendering.

Add `WorkspaceDirectoryService` tests proving new root layout and confinement under `dataRoot`.

Add `WorkAreaService` tests for home creation, stable disk IDs, display-name rename without disk movement, and no job-owned Work Area roots.

Add `OutputDirectoryService`, `PlanService`, `WorkflowRunner`, and `JobService` tests proving active outputs resolve to `workspace/<agentWorkspaceId>/runs/<runId>/outputs`.

Add promotion tests proving declared outputs move/copy to final destinations and remain attributable without parsing old path strings.

Add file/shell tool tests proving aliases resolve through the shared resolver and traversal/symlink escapes remain blocked.

Add persistence compatibility tests proving old root-relative/absolute paths still read, while new writes use the new layout.

Add search-based regression checks or ArchUnit-style checks to prevent scattered literals outside `WorkspacePathLayout`, compatibility tests, and docs.

Run Spring context startup after implementation. Run Playwright only if UI/work-area pages or interactive output surfaces change.

## 7. Exact Search Terms Workers Should Use

```bash
rg -n 'agents/|projects/|chats/|runtime/task-runs|runtime/workflow-runs|outputs/tasks|outputs/workflows|outputs/jobs|workspace/outputs|workspace/projects|workspace/scratch|jobs/' src/main/java src/test/java docs .internal-dev --glob '!**/.archive/**'
```

```bash
rg -n '"workspace"|"root"|"outputs"|"run"|"work"|"scratch"|"job"|projects/' src/main/java/io/mindspice/magenta2/ai/chat/tool src/main/java/io/mindspice/magenta2/ai/chat/plan src/main/java/io/mindspice/magenta2/ai/orchestration
```

```bash
rg -n 'Path\.of\(|Paths\.get\(|dataRoot\.resolve|\.resolve\("' src/main/java/io/mindspice/magenta2/ai/orchestration src/main/java/io/mindspice/magenta2/ai/chat src/test/java
```

```bash
rg -n 'workspaceRuntimeContext|description =|system prompt|output directory|file_path|outputs/' src/main/java docs .internal-dev
```

```bash
rg -n 'workspace_path|output_dir|output_directory|temp_workspace_path|root_relative_path|area_relative_path|file_path' src/main/java src/main/resources docs
```

```bash
rg -n 'WorkspaceOwnerType\.JOB|jobWorkspace|jobOutput|persistentWorkspaceEnabled|hostJobWorkspacePath|workspacePath\(\)|outputDir\(\)' src/main/java src/test/java docs
```

```bash
rg -n 'resolveOutputAgentId|inferDurableWorkspacePath|agentOutput\(|taskTempPath\(|workflowTemp\(|jobAssignmentWorkspace\(' src/main/java
```

