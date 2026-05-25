# Date

2026-05-25

# Change Summary

Implemented a persistent browser `/chat` pending-message queue for normal messages submitted while a turn is streaming. Mid-turn ordinary messages now enqueue server-side, render as composer cards, survive reload/session switching, and drain FIFO through the normal stream route after the active turn completes.

# Files

- `src/main/resources/schema.sql`: added `ai_chat_pending_messages` and queue indexes.
- `src/main/java/io/mindspice/magenta2/ai/chat/model/*Pending*`: added pending-message request/response records.
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatPendingMessageRepository.java`: added FIFO persistence, claim, ack, release, stale recovery, and delete support.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatPendingMessageService.java`: added service-owned queue validation/use cases.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`: clears pending queue on conversation clear.
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`: added thin pending-message endpoints.
- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`: added the queued-message panel between planning and composer form.
- `src/main/resources/static/js/chat-client.js`: switched ordinary mid-turn messages from interrupt fallback to pending-message enqueue and claim/ack/release drain, including active conversation URL sync and reload retry when the server stream lock is still active.
- `src/main/resources/static/css/magenta.css`: styled queue cards using the planning question card language.
- `src/test/java/io/mindspice/magenta2/ai/chat/repository/ChatPendingMessageRepositoryTest.java`, `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatPendingMessageServiceTest.java`, `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`, `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`: added focused queue coverage.
- `.internal-dev/specifications/api.md`, `.internal-dev/specifications/services.md`, `.internal-dev/specifications/web.md`, `.internal-dev/specifications/schema.md`: recorded the API/service/web/schema contracts.
- `docs/end-user/chat.md`, `docs/technical/api-reference.md`, `docs/technical/data-model.md`, `docs/technical/frontend-htmx.md`: documented behavior, routes, schema, and frontend ownership.

# Behavioral Impact

Normal non-command messages submitted during an active browser `/chat` stream are no longer sent to `/api/chat/turns/{turnId}/interrupt`. They are stored in `ai_chat_pending_messages`, shown above the input with queued order/status, and sent through `/api/chat/stream` after the current response finishes. The chat URL now tracks the active `conversationId` so a plain browser reload restores the selected chat. If the browser reloads while the original stream is still active, the client keeps the queued cards visible and retries after releasing any claim blocked by the existing stream lock. Slash commands remain unavailable during active turns, and planning answers continue through planning answer routes.

# Specification Impact

Updated API, service, web, and schema specifications to define the pending-message queue route, ownership, UI, and storage contracts.

# Risks

Browser Playwright validation is still required to prove slow-turn queueing, reload persistence, FIFO drain, command/planning negative paths, and desktop/mobile visual quality against the live app.

# Follow-up Items

- Delegate the Playwright checklist from `.internal-dev/plans/chat-mid-turn-message-queue/02-validation-checklist.md`.
