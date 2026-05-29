# Topic

Agent selector shell and HTMX detail swap pattern.

## Source References

- `.internal-dev/plans/agents-selector-chat-resize/worker-directive.md`
- `.internal-dev/specifications/web.md`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/orchestration/agents.js`
- `src/main/resources/static/css/orchestration.css`
- `docs/end-user/agents.md`

## Key Takeaways

- `/agents` and `/agents/{agentId}` use an Agents-specific shell. Do not route them through the Manage side-nav shell or reintroduce Manage banner text on these pages.
- Agent selector rows are navigation rows, not lifecycle-control rows. Keep Refresh, Disable, Delete, archive, and hard-delete affordances in the detail/manage area.
- The Agents selector/detail browser needs an explicit visual frame and divider. Without a border around the browser layout plus a left-pane/right-pane separator, the compact selector reads as loose content rather than a master/detail operational surface.
- Each selector row should expose one status chip: `Active` for `ACTIVE`, `Inactive` for `DISABLED`, and `Error` for workspace health failures such as `ERROR`, `MISSING`, or `READ_ONLY`.
- Selection is still HTMX-first: row links swap `/agents/_detail/{agentId}` into `#agent-detail-container` and push `/agents/{agentId}`. JavaScript only mirrors selected row state and the hidden `selectedAgentId` input so filter/list refreshes preserve selection.

## Engine Relevance

Use this before changing agent list/detail browser behavior, Agents shell rendering, or selector row status semantics.

## Open Questions

- Whether future keyboard navigation should be added to the selector as a narrow enhancement after browser validation and user testing.
