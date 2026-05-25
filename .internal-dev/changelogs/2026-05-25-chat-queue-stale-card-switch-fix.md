# Date

2026-05-25

# Change Summary

Fixed a browser `/chat` queue regression where a queued-message card could remain visible as `Sending...` after the claimed row had already drained and been acknowledged, especially around session switch or away-and-return timing.

# Files

- `src/main/resources/static/js/chat-client.js`: added active-conversation and request-generation guards for pending-message renders, and prevented queued-drain streams from stealing the visible chat session after the user switches away.
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`: added static client-contract assertions for guarded pending renders and queued-drain UI ownership.
- `.internal-dev/specifications/web.md`: tightened the browser chat queue contract for active-conversation scoping, stale pending-list responses, and background drain behavior after session switch.
- `.internal-dev/knowledge/chat-planning-composer-architecture.md`: recorded the stale-card race and the required guard pattern.
- `docs/technical/frontend-htmx.md`: documented that pending-card reloads ignore stale claimed responses after final ack.

# Behavioral Impact

Queued messages still use `/api/chat/{conversationId}/pending-messages`, claim/ack/release semantics, and normal `/api/chat/stream` FIFO drain. The visible queue panel now only reflects the currently active conversation and ignores older pending-list responses, so returning to a drained conversation should show no stale queued card when `GET pending-messages` is empty.

# Specification Impact

Updated the web specification to explicitly require active-conversation scoping for pending-card renders, latest-request protection against stale pending-list responses, and non-stealing background queued drains after session switch.

# Risks

The fix is client-state focused. Browser validation should still cover the slow-turn/session-switch path to prove no stale card remains after ack and no ordinary queued message uses the interrupt route.

# Follow-up Items

- None.
