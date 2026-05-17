# Agent IDs Can Escape Agent Subtree Inside Data Root

## Summary

Agent IDs are accepted as raw path segments and can contain traversal-like values that remain under `dataRoot` but escape the `agents/` subtree.

## Scope

Agent profile creation and workspace deletion paths.

## Reproduction

1. Create an agent/profile with an id containing path traversal such as `../projects/example`.
2. Trigger workspace creation or agent workspace deletion for that id.

## Expected

Agent ids must be normalized identifiers, not filesystem path fragments.

## Actual

Validation only checks nonblank id strings. Workspace paths concatenate `agents/` + `agentId`, and confinement only checks the final path remains under `dataRoot`.

## Evidence

- `AgentProfileService.java:90` accepts caller-supplied ids.
- `AgentProfileService.java:231` only validates nonblank.
- `WorkspaceService.java:42` stores `agents/` + `agentId` + `/workspace`.
- `WorkspaceService.java:119` deletes `confined("agents/" + agentId)`.
- `WorkspaceService.java:182` confirms only `dataRoot` confinement.

## Impact

Critical: a malformed id can target other durable runtime subtrees inside `dataRoot`, including possible deletion through lifecycle cleanup.

## Status

Open.

## Next Action

Require strict id segment validation for agent ids and any other ids used in filesystem paths; add tests for `..`, slash, backslash, absolute path, and encoded traversal values.
