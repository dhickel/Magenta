# Current State Analysis

## Verified Context

- `WorkspaceDirectoryService` still documents and creates the old layout: `agents/<id>/workspace`, durable `work/`, `outputs/`, `runs/`, `scratch/`, `runtime/task-runs/<runId>`, `runtime/workflow-runs/<runId>`, `jobs/<jobId>/workspace`, `jobs/<jobId>/outputs`, project `projects/<projectId>/workspace`, and assignment job workspaces.
- `WorkspaceService` persists agent roots as `agents/<agentId>/workspace`, job roots as `jobs/<jobId>`, project roots as `projects/<projectId>/workspace`, and has ad hoc path composition for assignment paths and archive/delete paths.
- `WorkAreaService` currently treats Work Areas as metadata around existing arbitrary relative directories under an owner root; `home/` is system-owned, but there is no target `workareas/<workAreaId>/` invariant.
- `WorkAreaRepository` already has `id`, `workspace_id`, `root_relative_path`, `area_relative_path`, and `display_name`. This is enough to use `id` as the stable disk segment while keeping display name DB-owned.
- `OutputDirectoryService` currently resolves task/workflow/job final output directories directly under selected Work Area/output route roots, including `outputs/tasks`, `outputs/workflows`, and `outputs/jobs` when not direct output.
- `JobDefinition`, `JobService`, `JobRun`, `JobRepository`, and `JobExecutionSummary` still model persistent job workspaces with `persistentWorkspaceEnabled`, `workspace_path`, and `persistentJobWorkspace*` summary fields.
- `PlanRun`, `WorkflowRun`, and queued `WorkAssignment` do not currently expose a user-visible run display-name field.
- `PlanService` allocates task execution under `runtime/task-runs/<runId>`, writes final output directories during start, prompts that `outputs/` is preserved permanently, and deletes temp immediately on terminal completion unless retention is configured.
- `WorkflowRunner` allocates workflow execution under `runtime/workflow-runs/<runId>` and output directories through `OutputDirectoryService`.
- File and shell tools already understand alias semantics for `workspace/`, `root/`, `outputs/`, `run/`, `work/`, `scratch/`, `job/`, and `projects/<projectId>/...`; those aliases now need to be narrowed to the target model and backed by centralized constants/helpers.
- Docs and package guidance still describe old output/job/runtime paths in `docs/technical/workspaces-tools-outputs.md`, `docs/technical/services.md`, `docs/technical/orchestration-runtime.md`, `docs/technical/workflow-engine.md`, `docs/api/00-index.md`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`, and `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`.

## Architecture Fit Gaps

- Job semantics conflict directly with the target contract. Jobs currently own persistent workspace options and job output directories; the new model says they bind to Work Areas/projects and do not own directories.
- Path/layout strings are scattered through services, tools, controllers, tests, docs, and prompts. Without a source-of-truth helper, the new model will drift quickly.
- Current immediate cleanup is incompatible with the one-day run-staging retention requirement.
- Existing output materialization happens into the final output directory at completion, but the run-local staging directory is also treated as durable/final. The backend needs an explicit promotion step from run staging to final destinations.
- Current Work Area model exposes arbitrary user-visible paths as the persistent Work Area identity. Target Work Areas should be stable ID directories with DB display names.
- Current API/UI still exposes compatibility `workspaceId` alongside project and Work Area fields. This needs careful wording and validation so `workspaceId` remains compatibility metadata, not a user-managed workspace-root selector.

## Schema/API/UX Contract Conflicts

- Non-job run display names require additive DB fields and payload changes. Proposed exact field names:
  - `work_assignments.run_display_name`
  - `plan_runs.run_display_name`
  - `workflow_runs.run_display_name`
  - request/record property `runDisplayName`
- Work Area backend references should stay ID-based:
  - use existing `work_areas.id` as the stable disk id;
  - keep `work_areas.display_name` as the user-owned label;
  - keep `work_areas.area_relative_path` for compatibility/backfill, but new rows should use `workareas/<id>`;
  - continue using `selected_work_area_id` and `output_work_area_id` as assignment/output refs.
- Job workspace fields should be deprecated/ignored or removed from new behavior:
  - `JobDefinition.persistentWorkspaceEnabled`
  - `JobRun.workspacePath`
  - `JobExecutionSummary.persistentJobWorkspace*`
  - `WorkspaceOwnerType.JOB` and `WorkspaceService.jobWorkspace` if no remaining current contract requires them.

## Migration And Data Risks

- This is not a live deployment. Schema-backed known records may be migrated or reset; ambiguous loose files may be deleted.
- Existing development directories at repo root include `agents/`, `data/`, and unrelated untracked `artifacts/playwright/`. Workers must not clean unrelated untracked review/playwright artifacts unless explicitly ingesting them as evidence.
- Additive schema migrations must be SQLite-safe and guarded by `pragma_table_info` checks. Destructive schema removal is not required; deprecate old columns in code/docs unless a reset task explicitly owns table rebuild.
- Any data reset must be based on schema-backed records: workspaces, work_areas, plan_runs, workflow_runs, job_runs, run_output_artifacts, work_assignments, projects, agents, and chats.

## Validation Blind Spots

- Tests currently cover old path semantics and may pass while prompts/tools still expose legacy aliases or docs still claim old paths.
- Full test-suite validation must wait until filesystem restructuring/dev reset completes, otherwise old directories can mask layout mistakes.
- Browser validation must inspect actual Work Area/project browsing and output views, not just API status codes.

