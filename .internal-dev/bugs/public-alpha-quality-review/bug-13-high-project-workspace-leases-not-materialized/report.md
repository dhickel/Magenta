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

## Impact

High: operators can see a held project lease while tasks cannot inspect or mutate the project workspace through advertised paths.

## Status

Open.

## Next Action

Implement and validate materialized project links or revise the runtime contract/UI to match actual access.
