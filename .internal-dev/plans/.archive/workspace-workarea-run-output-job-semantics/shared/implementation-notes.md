# Implementation Notes

## Planning-Time Evidence

- Root repo guidance requires relevant specification reads, knowledge filename filtering, docs updates, closeout, branch creation for multi-phase plans, phase commits, tests, startup smoke, and focused Playwright validation for UI changes.
- Current stale path anchors observed:
  - `WorkspaceDirectoryService`: old physical layout and cleanup guard.
  - `WorkspaceService`: old persisted `rootRelativePath` values and job workspace method.
  - `WorkAreaService`: arbitrary relative Work Area directories.
  - `OutputDirectoryService`: final output directories during execution.
  - `JobService`: persistent job workspace and job output allocation.
  - `PlanService`: `runtime/task-runs`, immediate cleanup, prompt text, output materialization.
  - `WorkflowRunner`: `runtime/workflow-runs` and output fallback.
  - `AgentFileToolService` and `AgentShellToolService`: duplicated alias strings.
  - `docs/technical/workspaces-tools-outputs.md`, `docs/technical/services.md`, `docs/technical/orchestration-runtime.md`, `docs/technical/workflow-engine.md`, `docs/api/00-index.md`.

## Exact DB Field Decisions For Execution

- Add `run_display_name text` to `work_assignments`, `plan_runs`, and `workflow_runs`.
- Add request/record property `runDisplayName` for non-job task/workflow submission flows and validate it before queuing.
- Do not add a second Work Area display-name concept. Use existing `work_areas.display_name`.
- Use existing `work_areas.id` as the disk ID. Keep `area_relative_path` as the backend path field and write `workareas/<id>` for new rows.
- Treat `selected_work_area_id` and `output_work_area_id` as the assignment/output backend references.

## Search Terms Workers Must Use

Use these before editing and again before validation:

```text
runtime/task-runs
runtime/workflow-runs
outputs/jobs
outputs/tasks
outputs/workflows
jobs/<
jobWorkspace
persistentWorkspace
workspace/outputs
scratch
run/
workareas
area_relative_path
hostOutputPath
hostWorkspacePath
runDisplayName
run_display_name
```

## Branch And Commit Expectations

- Main thread should create a dedicated branch before phase execution starts.
- Commit each completed and validated phase on that branch.
- The final closeout commit must include implementation plus `.internal-dev` and `docs/` updates.
- Leave unrelated untracked files untouched unless a phase explicitly ingests them as validation evidence.

