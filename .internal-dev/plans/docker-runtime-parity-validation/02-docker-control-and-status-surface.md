# Phase 02: Docker Control And Status Surface

## Context

Operators need truthful control over managed agent containers. The prior stop-status defect proves endpoint success is not enough.

## Goal

Validate that the UI exposes complete, truthful, and actionable Docker lifecycle information for agents and that every lifecycle control reconciles with actual daemon state.

## In Scope

- Agent list Docker column.
- Agent detail Docker panel/tab.
- Start, stop, restart, refresh, enable, disable, archive/delete-adjacent lifecycle behavior.
- Global runtime status display.
- Cross-check against real container state from Docker/Podman.

## Out of Scope

- Deep execution behavior once a container is running.

## Implementation Steps

1. Create a dedicated validation agent from the UI.
2. Inventory every Docker field surfaced in list and detail views.
3. Compare surfaced UI data with backend status fields and actual daemon data:
   - runtime enabled/disabled
   - daemon reachable/unreachable
   - image available/missing
   - container id/name
   - lifecycle state
   - last seen / last updated timestamp if available
   - error message / reason
4. Use the UI to start, stop, restart, refresh, disable, and re-enable the agent.
5. After each action, reconcile three sources:
   - browser DOM
   - browser-origin status endpoint/fragment
   - Docker/Podman daemon state
6. Re-run the known stop-status scenario and verify whether the UI remains truthful if stop requires escalation or fails.
7. Verify disabled agents present a clear non-runnable state and do not appear deceptively healthy.
8. Record missing operator fields or ambiguous wording as parity issues, not cosmetic notes.

## Validation

Required checks:
- UI state after every lifecycle action matches real container state.
- Action failures produce visible, actionable errors.
- Refresh is a real status refresh, not a cosmetic fragment reload.
- A stale or false `IDLE`/`RUNNING` display is an alpha blocker.

## Exit Criteria

- `.internal-dev/reviews/docker-runtime-parity-validation/02-docker-control-evidence.md` exists.
- The known stop-status defect is either proven fixed or remains logged with fresh evidence.
