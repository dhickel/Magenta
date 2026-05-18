# Topic

Workflow inbox and runtime direct-line agent inbox persistence ownership.

# Source References

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`

# Key Takeaways

- `inbox_messages` is workflow-owned. It stores workflow/user approvals, workflow agent approval nodes, notifications, response payloads, and run-output delivery.
- `agent_inbox_messages` is runtime-owned. It stores direct-line agent/operator messages, read state, handled state, and runtime inbox event history.
- The tables are intentionally separate because they have different message models and lifecycle state.
- Clean schema and repository warm bootstrap should define the same table/index targets so runtime ownership is visible without relying on hidden repository table creation.

# Engine Relevance

When adding inbox behavior, choose the table by surface and lifecycle:

- Workflow approval, workflow message, or run-output delivery: use `ai.orchestration.workflow.InboxService` and `inbox_messages`.
- Direct-line agent/operator message, read/unread state, or handled state: use `ai.orchestration.runtime.InboxService` and `agent_inbox_messages`.

# Open Questions

- A future combined operator timeline may need a read model across both tables, but that should be designed explicitly rather than by collapsing the persistence models opportunistically.
