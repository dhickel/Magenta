# Agent Delete/Archive Targets Missing Stale Docker Element

## Summary

Agent detail lifecycle controls target `#agent-docker-status-{agentId}`, but no matching element is rendered.

## Scope

Agent detail dashboard lifecycle controls.

## Reproduction

1. Open `/agents/{agentId}`.
2. Use `Delete / Archive`.
3. HTMX attempts to swap into a missing target.

## Expected

Lifecycle confirmation and results swap into an existing agent detail panel target.

## Actual

Actions target stale Docker ids and no rendered element exists.

## Evidence

- `OrchestrationController.java:4900` targets `#agent-docker-status-{agentId}`.
- `OrchestrationController.java:6138` and `OrchestrationController.java:6147` repeat the target.
- `orchestration.css:1499` contains stale Docker classes.
- Static and test-harness reviews found no rendered matching element id.

## Impact

Medium: lifecycle UI responses can disappear or fail to update the visible page.

## Status

Open.

## Next Action

Rename/repoint targets to existing filesystem-runtime panel elements and add browser HTMX swap coverage.
