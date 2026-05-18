# Project Workspace Materialized Links

## Topic

Materializing leased project workspaces into assignment temp workspaces for tool access.

## Source References

- `WorkspaceDirectoryService.materializeAssignmentProjectLink`
- `PlanService.startRun`
- `OrchestrationRunnerService.executeWithLease`
- `AgentFileToolService.resolveProjectScope`
- `AgentShellToolService.resolveProjectWorkingDirectory`

## Key Takeaways

- The canonical project workspace remains `projects/<projectId>/workspace` under the configured data root.
- During a leased task run, the promised tool path is an assignment-local symlink at `runtime/task-runs/<runId>/projects/<projectId>`.
- Shell and file tools still expose the friendly alias `projects/<projectId>/...`, but active assignment contexts now verify the materialized link exists and targets the current project workspace before resolving through it.
- Cleanup has two layers: the runner removes the project link before lease release, and terminal run cleanup deletes the temp workspace tree.

## Engine Relevance

This keeps project membership and lease acquisition in the runner while making the acquired workspace visible through the same path that task prompts and tool aliases advertise.

## Open Questions

- If Magenta must support host filesystems without symlink support, the materialization strategy needs an explicit alternate design rather than silently falling back to direct canonical project paths.
