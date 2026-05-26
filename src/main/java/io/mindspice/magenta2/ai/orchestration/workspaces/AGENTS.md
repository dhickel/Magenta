## Workspace Package

This package owns filesystem workspace management including centralized layout helpers, effective durable workspace resolution, agent/project workspace roots, Work Area directories, run-local output staging, final output promotion destinations, artifact metadata, and workspace leases.

### Responsibilities
- Manage all workspace directories and Work Area metadata confined under the configured `dataRoot`.
- Resolve effective durable workspace centrally: project workspace when `projectId` is present, otherwise executing agent workspace.
- Provide typed directory paths through centralized structural constants/helpers, including data-root children, agent workspace roots, Home, Work Areas, run roots, run-local outputs, final output destinations, chat files, and project roots.
- Enforce exclusive writable leases on project workspaces and reconcile expired leases before they block reacquisition. Job workspace leasing is legacy compatibility only and must not be expanded.
- Materialize explicit run output artifacts into effective workspace output directories and persist metadata.
- Keep loose artifact discovery compatibility-gated and confined under the real data root and expected run output directory.
- Clean up run staging only through retention-aware cleanup; never delete Work Areas, project/agent workspaces, or promoted final outputs.

### Layout
- Data-root children: `workspace/`, `chats/`, `agents/`, and `projects/`.
- Agent execution root: `workspace/<agentWorkspaceId>/`.
- Default Home Work Area: `workspace/<agentWorkspaceId>/home/`.
- User Work Areas: `workspace/<agentWorkspaceId>/workareas/<workAreaId>/`, where `workAreaId` is the stable `work_areas.id` and display names stay DB-owned.
- Run staging root: `workspace/<agentWorkspaceId>/runs/<runId>/`.
- Model-facing run outputs: `workspace/<agentWorkspaceId>/runs/<runId>/outputs/`.
- Final output destinations are backend promotion targets: jobless task/workflow outputs promote to the agent workspace final `outputs/`; job-bound task/workflow/job outputs promote to the bound Work Area or project output destination.
- Job-owned workspace and `outputs/jobs` style paths are legacy compatibility only, not new-contract layout.

### Services
- `WorkspaceDirectoryService` — filesystem path management and directory creation.
- `WorkAreaService` — mark/list/unmark confined workspace subdirectories as user-selectable Work Areas.
- `WorkAreaRepository` — durable `work_areas` metadata and active assignment/output target guard checks.
- `WorkAreaExplorerService` — confined Work Area browse, preview, edit, download, create, rename, copy, move, delete, label, recent-action, and nested mark operations.
- `WorkspaceFileMetadataService` and related repositories — reusable file labels, file label assignments, and recent file action records.
- `EffectiveWorkspaceResolver` — effective durable workspace selection and layout path record creation.
- `WorkspaceLeaseService` — exclusive writable lease acquisition, extension, and release.
- `OutputDirectoryService` — typed output directory resolution for task, workflow, and job publications.
- `OutputArtifactService` — explicit output materialization/publication, copied temp publication, compatibility loose discovery, and artifact persistence.

### Change guidance
- Workspace paths constructed by this package must be confined under `dataRoot` with normalized path checks.
- Work Areas are DB-backed metadata with stable id directories. Do not mark the owner workspace root itself as a Work Area.
- The `home/` Work Area is system-owned and cannot be unmarked.
- Unmarking must reject Work Areas referenced by queued/running assignments or active output targets.
- Source paths supplied for persisted output materialization must also resolve through `toRealPath()` and stay under the real `dataRoot` before copy or artifact registration, so symlinks cannot escape the managed data tree.
- Copied temp/run publication must skip symlinks, including project workspace links, and must register copied files under `copied_temp/...` artifact names.
- Always use `Files.createDirectories` before returning a workspace path.
- Run staging is retained for at least one day and must remain available for active or resumable work.
- Runtime aliases are part of this architecture contract: `workspace/` for the selected Work Area or durable workspace, `root/` for the owner workspace root, `outputs/` for the current run-local output staging directory, and `run/` for current run staging. Legacy aliases such as scratch or job workspace are compatibility only when still accepted.
- Lease extension must verify holder ownership.
- Do not add UI, scheduling, or agent behavior here.

### Validation
- Path confinement tests for all directory methods.
- Work Area persistence, Home creation, duplicate reactivation, active-use guard, and symlink escape tests.
- Lease conflict, extension, and release tests.
- Output materialization tests for each PlanFieldType.
