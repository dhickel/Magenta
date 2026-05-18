# Subplan 03: Inbox Table Ownership

## Goal

Decide and implement ownership for `inbox_messages` and `agent_inbox_messages`.

## Implementation Steps

1. Trace workflow/user inbox and runtime/agent inbox call sites.
2. Either document separate responsibilities in schema/repositories or unify tables with migration.
3. Preserve operator-visible history.
4. Add tests for both surfaces or migration path.

## Implementation Decision

Keep the two inbox tables separate for alpha remediation. `inbox_messages` is owned by `ai.orchestration.workflow` for workflow/user approvals, workflow agent approval nodes, notifications, and run-output delivery. `agent_inbox_messages` is owned by `ai.orchestration.runtime` for direct-line runtime agent/operator inbox messages, read state, handled state, and inbox events.

Unifying the tables would require a broader data migration and model reconciliation because the workflow table stores response/approval fields while the runtime table stores read/handled flags and direct-line event semantics. The smaller safe remediation is to make both active tables canonical in clean schema and repository bootstrap, with focused tests proving the surfaces are intentionally distinct.

## Validation

Message history no longer splits unpredictably by surface.

Implemented focused coverage:

- Clean `schema.sql` includes both inbox table shapes and recipient indexes.
- `WorkflowRepository` and `OrchestrationRuntimeRepository` bootstrap does not change the clean-schema target shapes.
- Workflow/user and workflow-agent messages remain readable from `inbox_messages`.
- Runtime direct-line agent messages remain readable from `agent_inbox_messages`.
- Cross-surface lookups do not accidentally read the other table.
