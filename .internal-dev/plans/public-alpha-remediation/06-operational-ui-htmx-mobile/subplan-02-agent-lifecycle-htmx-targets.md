# Subplan 02: Agent Lifecycle HTMX Targets

## Goal

Make Delete/Archive lifecycle controls swap into existing visible targets.

## Implementation Steps

1. Locate controls targeting `#agent-docker-status-{agentId}`.
2. Repoint to existing filesystem-runtime panel elements or render the intended target.
3. Remove stale target assumptions from tests.
4. Add browser/HTMX swap coverage.

## Validation

Lifecycle response updates visible UI.
