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

The root is `aiConfig.dataRoot().toRealPath()` and any resolved path under that root is allowed.

## Evidence

- `AgentFileToolService.java:46` sets root to `dataRoot`.
- `AgentFileToolService.java:343` allows any normalized path under root.
- `AgentFileTools.java:19` tool descriptions advertise data-root access.

## Impact

High: one tool-capable agent can access unrelated runtime data under `dataRoot`.

## Status

Open.

## Next Action

Scope file tools through the active `OrchestrationTaskContext` and workspace lease/link model.
