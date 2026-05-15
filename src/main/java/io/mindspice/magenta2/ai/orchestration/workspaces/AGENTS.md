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
- Legacy `agents/<id>/home` and `agents/<id>/outputs` are deprecated and will be migrated into workspace/ during the Docker removal phases.

### Services
- `WorkspaceDirectoryService` — filesystem path management and directory creation.
- `WorkspaceLeaseService` — exclusive writable lease acquisition, extension, and release.
- `OutputArtifactService` — output materialization (file copy, .md, .json, .txt) and artifact persistence.

### Change guidance
- All paths must be confined under `dataRoot` with normalized path checks.
- Always use `Files.createDirectories` before returning a workspace path.
- Temp directories are deleted after terminal run completion; output directories persist.
- Lease extension must verify holder ownership.
- Do not add UI, scheduling, or agent behavior here.

### Validation
- Path confinement tests for all directory methods.
- Lease conflict, extension, and release tests.
- Output materialization tests for each PlanFieldType.
