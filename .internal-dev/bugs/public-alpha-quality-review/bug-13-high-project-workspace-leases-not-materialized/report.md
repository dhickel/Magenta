# Project Workspace Leases Are Not Materialized Into Agent Workspace

## Summary

The runner acquires project workspace leases, but tools do not appear to get the promised `workspace/projects/{projectId}` filesystem view.

## Scope

Project workspace leases, runner context, shell alias resolution, and plan runtime instructions.

## Reproduction

1. Submit work tied to a project.
2. Observe a writable project lease is acquired.
3. Try to access the promised linked project workspace path through shell/file tools.

## Expected

Acquired project workspaces are materialized into the agent runtime workspace while the assignment holds the lease.

## Actual

Runner acquires the lease but sets task context without host paths/link materialization; shell alias resolution requires actual directories.

## Evidence

- `OrchestrationRunnerService.java:220` acquires writable project lease.
- `OrchestrationRunnerService.java:258` sets task context with null host paths and assignment workspace id.
- `AgentShellToolService.java:126` confines shell to agent workspace.
- `AgentShellToolService.java:156` expects actual project directory for alias.
- `PlanService.java:1873` promises `workspace/projects/` linked project workspaces.
- 2026-05-18 implementation: `PlanService.startRun` now materializes `projects/{projectId}` under the allocated assignment temp workspace when the orchestration context has an active project; shell/file assignment-context aliases verify that materialized link before resolving project paths; `OrchestrationRunnerService` removes the materialized link before releasing the project lease.
- 2026-05-18 validation: `OrchestrationRuntimeTest.projectLeaseMaterializesPromisedWorkspacePathForTaskTools` proves a project-backed task lease creates a real `runtime/task-runs/{runId}/projects/{projectId}` symlink, reads it through `file_read`, releases the lease, and leaves the canonical project workspace intact.

## Impact

High: operators can see a held project lease while tasks cannot inspect or mutate the project workspace through advertised paths.

## Status

Implemented; pending parent review/validation sign-off.

## Next Action

Parent review should confirm the focused evidence and decide when to mark bug-13 passed in the remediation tracker.
