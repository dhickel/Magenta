# Subplan 03: Inbox Table Ownership

## Goal

Decide and implement ownership for `inbox_messages` and `agent_inbox_messages`.

## Implementation Steps

1. Trace workflow/user inbox and runtime/agent inbox call sites.
2. Either document separate responsibilities in schema/repositories or unify tables with migration.
3. Preserve operator-visible history.
4. Add tests for both surfaces or migration path.

## Validation

Message history no longer splits unpredictably by surface.
