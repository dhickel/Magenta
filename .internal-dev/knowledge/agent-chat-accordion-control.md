# Topic

Agent-detail chat disclosure ownership.

# Source References

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/orchestration/agent-chat.js`
- `.internal-dev/test-fixtures/orchestration-driver/live-validation.js`

# Key Takeaways

- On `/agents/{id}`, the `.agent-chat-accordion > summary` element is the only open/close control for chat.
- Do not render the side-panel JavaScript collapse button inside the accordion; that creates competing open states.
- Do not reintroduce `/agents/_detail/{id}/chat` dashboard actions unless the tab route is restored intentionally.
- When changing chat shell behavior, bump the module asset version so live browsers cannot keep stale JavaScript behind updated server markup.
- `/agents` must load `agent-chat.js` because the normal card-click flow HTMX-swaps `/agents/_detail/{id}` into the page without a full-page script refresh.
- The detail fragment must render the visible chat panel server-side; JavaScript enhances it, but should not be the only source of visible chat markup.

# Engine Relevance

- Agent-detail browser checks should assert the accordion opens from its summary label and that `#agent-chat-panel` gains visible height.
- Validate the card-click flow from `/agents`, not only direct navigation to `/agents/{id}`.
- Non-agent-page side-panel chat can still use the JavaScript collapse button where no native accordion wrapper exists.

# Open Questions

- None.
