# Subplan 05: Agent Detail Quality

## Goal

Replace static placeholder event log and expose richer workspace health where available.

## Implementation Steps

1. Trace available event/audit data for agent detail.
2. Replace placeholder events with real recent events or remove the section.
3. Use `AgentWorkspaceStatusService` data in workspace health panel.
4. Add focused rendering tests.

## Validation

Agent detail shows real data or no misleading placeholder data.
