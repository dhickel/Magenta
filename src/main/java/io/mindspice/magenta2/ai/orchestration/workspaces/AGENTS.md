## Workspace Package

This package owns filesystem workspace management including agent workspace roots, job/project persistent workspaces, temp task/workflow directories, output directories, and workspace leases.

### Responsibilities
- Manage all workspace directories confined under the configured `dataRoot`.
- Provide typed directory paths: agentWorkspace, agentWorkspaceOutputs, agentProjectLinks, agentScratch, taskTemp, workflowTemp, jobWorkspace, projectWorkspace, agentOutput, jobOutput.
- Enforce exclusive writable leases on job/project workspaces and reconcile expired leases before they block reacquisition.
- Materialize run output artifacts into output directories and persist metadata.
- Clean up temp directories on terminal run states; never delete output directories.

### Layout
- Agent workspace root: `agents/<id>/workspace/`
- Agent outputs: `agents/<id>/workspace/outputs/<slug>-<runId>/`
- Agent project links: `agents/<id>/workspace/projects/<projectId>`
- Agent scratch: `agents/<id>/workspace/scratch/`
- Legacy `agents/<id>/home` and `agents/<id>/outputs` are deprecated and migrate into `workspace/` when warm data roots are opened.

### Services
- `WorkspaceDirectoryService` — filesystem path management and directory creation.
- `WorkspaceLeaseService` — exclusive writable lease acquisition, extension, and release.
- `OutputArtifactService` — output materialization (file copy, .md, .json, .txt) and artifact persistence.

### Change guidance
- Workspace paths constructed by this package must be confined under `dataRoot` with normalized path checks.
- Source paths supplied for persisted output materialization must also resolve through `toRealPath()` and stay under the real `dataRoot` before copy or artifact registration, so symlinks cannot escape the managed data tree.
- Always use `Files.createDirectories` before returning a workspace path.
- Temp directories are deleted after terminal run completion; output directories persist.
- Lease extension must verify holder ownership.
- Do not add UI, scheduling, or agent behavior here.

### Validation
- Path confinement tests for all directory methods.
- Lease conflict, extension, and release tests.
- Output materialization tests for each PlanFieldType.
