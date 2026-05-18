# File Tools Are Scoped to Entire Data Root

## Summary

File tools are confined to `dataRoot`, not to the current agent workspace, project lease, or assignment.

## Scope

`AgentFileToolService` and file tool public contract.

## Reproduction

1. Run a tool-enabled agent.
2. Read/write paths under other agents', projects', runtime, or output directories inside `dataRoot`.

## Expected

File tool access should be scoped to the active assignment workspace and explicitly linked projects.

## Actual

Resolved before remediation as `aiConfig.dataRoot().toRealPath()`, allowing any path under that root. The implementation now resolves an active file scope from `OrchestrationTaskContextHolder` before IO and keeps the data-root fallback only when no orchestration context is active.

## Evidence

- Original review evidence: `AgentFileToolService` set its only root to `dataRoot`, path checks accepted any normalized path under that root, and `AgentFileTools` descriptions advertised data-root access.
- 2026-05-18 implementation: `AgentFileToolService` scopes active assignment file access to the run workspace, active output directory, and current project workspace aliases, while tests deny unrelated runtime, agent, and project paths.
- 2026-05-18 implementation: `AgentFileTools` descriptions no longer advertise whole data-root access.

## Impact

High: one tool-capable agent can access unrelated runtime data under `dataRoot`.

## Status

Implemented; validation pending.

## Next Action

Run validation for subplan 02 and record the final result in the public alpha remediation progress tracker.
