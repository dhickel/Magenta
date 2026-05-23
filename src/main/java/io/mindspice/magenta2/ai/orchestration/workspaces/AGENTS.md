## Workspace Package

This package owns filesystem workspace management including effective durable workspace resolution, agent/project workspace roots, optional job persistent workspaces, temp task/workflow directories, output directories, artifact metadata, and workspace leases.

### Responsibilities
- Manage all workspace directories and Work Area metadata confined under the configured `dataRoot`.
- Resolve effective durable workspace centrally: project workspace when `projectId` is present, otherwise executing agent workspace.
- Provide typed directory paths: agent/project workspace roots, `work/`, `outputs/`, `runs/`, `scratch/`, task temp, workflow temp, opt-in job workspace, task output, workflow output, and job output.
- Enforce exclusive writable leases on job/project workspaces and reconcile expired leases before they block reacquisition.
- Materialize explicit run output artifacts into effective workspace output directories and persist metadata.
- Keep loose artifact discovery compatibility-gated and confined under the real data root and expected run output directory.
- Clean up temp directories on terminal run states; never delete output directories or persistent job workspaces.

### Layout
- Agent workspace root: `agents/<id>/workspace/`
- Project workspace root: `projects/<projectId>/workspace/`
- Default Home Work Area: `<owner-workspace>/home/`
- Effective workspace directories: `work/`, `outputs/`, `runs/`, `scratch/`, `jobs/`
- Task outputs: `outputs/tasks/<taskId>/<runId>/`
- Workflow outputs: `outputs/workflows/<workflowId>/<runId>/`
- Job outputs: `outputs/jobs/<assignmentId>/<jobRunId>/`
- Persistent job workspace: `jobs/<assignmentId>/` when enabled
- Agent project links: `agents/<id>/workspace/projects/<projectId>`
- Agent scratch: `agents/<id>/workspace/scratch/`
- Legacy `agents/<id>/home` and `agents/<id>/outputs` are deprecated and migrate into `workspace/` when warm data roots are opened.

### Services
- `WorkspaceDirectoryService` — filesystem path management and directory creation.
- `WorkAreaService` — mark/list/unmark confined workspace subdirectories as user-selectable Work Areas.
- `WorkAreaRepository` — durable `work_areas` metadata and active assignment/output target guard checks.
- `EffectiveWorkspaceResolver` — effective durable workspace selection and layout path record creation.
- `WorkspaceLeaseService` — exclusive writable lease acquisition, extension, and release.
- `OutputDirectoryService` — typed output directory resolution for task, workflow, and job publications.
- `OutputArtifactService` — explicit output materialization/publication, copied temp publication, compatibility loose discovery, and artifact persistence.

### Change guidance
- Workspace paths constructed by this package must be confined under `dataRoot` with normalized path checks.
- Work Areas are metadata around existing confined directories. Do not mark the owner workspace root itself as a Work Area.
- The `home/` Work Area is system-owned and cannot be unmarked.
- Unmarking must reject Work Areas referenced by queued/running assignments or active output targets.
- Source paths supplied for persisted output materialization must also resolve through `toRealPath()` and stay under the real `dataRoot` before copy or artifact registration, so symlinks cannot escape the managed data tree.
- Copied temp/run publication must skip symlinks, including project workspace links, and must register copied files under `copied_temp/...` artifact names.
- Always use `Files.createDirectories` before returning a workspace path.
- Temp directories are deleted after terminal run completion; output directories persist.
- Waiting workflow temp directories must remain available for resume.
- Runtime aliases are part of this architecture contract: `workspace/`, `work/`, `outputs/`, `run/`, `scratch/`, and `job/` when an active job assignment/run has an opt-in persistent job workspace.
- Lease extension must verify holder ownership.
- Do not add UI, scheduling, or agent behavior here.

### Validation
- Path confinement tests for all directory methods.
- Work Area persistence, Home creation, duplicate reactivation, active-use guard, and symlink escape tests.
- Lease conflict, extension, and release tests.
- Output materialization tests for each PlanFieldType.
