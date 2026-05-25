---
status: active
created: 2026-05-25
owner: advanced-planning
classification: small
source_intent: user request on 2026-05-25
---

# Chat Mid-Turn Message Queue Specification Lock

## Locked Objective

Implement persistent, conversation-scoped queuing for normal user chat messages submitted while a `/chat` turn is already streaming. The user must see queued messages immediately in the composer area, the queue must survive reload/history switching, and queued messages must be sent FIFO after the active turn finishes.

This is a small single-pass implementation. One implementation worker should own backend storage, API endpoints, browser behavior, focused tests, docs/spec closeout, and validation handoff. Do not split into multiple phases.

## Verified Current State

- `ChatModuleRenderer.sessionChatModule()` renders `#chat-planning-panel` immediately above `#chat-form`; queued messages should render between those nodes.
- `chat-client.js` leaves the form enabled during streaming, changes the button text to `Send update`, and routes submissions through `sendInterruptOrQueue()` while `requestInFlight` is true.
- `sendInterruptOrQueue()` calls `/api/chat/turns/{turnId}/interrupt` when active turn metadata is present. `ACCEPTED` clears status and does not render or persist a queued card.
- `ActiveTurnRegistry` stores accepted interrupts in memory only, and `ChatService` drains them only at the tool checkpoint.
- `ChatService.stream()` uses a per-conversation `Semaphore` and returns `Another stream is already active...` for overlapping streams. Preserve this guard.
- Current local fallback `queuedMessages` is in-memory only and only surfaces `Message queued for the next turn.` in `#chat-error`.

## Acceptance Criteria

- Normal non-command messages submitted mid-turn are persisted in a conversation-scoped queue and shown immediately above the input using the existing planning question card visual language.
- Queued messages show message text, FIFO position/count, and a status such as `Will send after this turn finishes`.
- Queue state survives reload and session/history switching.
- After the active turn returns, the browser claims the next queued message, sends it through the normal `/api/chat/stream` path, acknowledges it only after successful completion, removes its card, then continues FIFO until the queue is empty.
- Queued messages are not written to `ai_chat_memory` until they are actually sent as model turns.
- If stream start or fetch fails after a claim, the queued message is not lost and can be retried.
- Clearing a conversation deletes its pending queue.
- Direct overlapping `/api/chat/stream` behavior and the per-conversation stream lock remain unchanged.
- The interrupt endpoint remains for explicit interrupt semantics/future use, but ordinary mid-turn chat submissions no longer use it by default.
- Commands submitted mid-turn are not queued unless the worker explicitly designs and tests a command-specific behavior; default behavior should keep the current "commands are available after the active turn finishes" user feedback.
- Planning question answers are never routed through the pending-message queue.

## Negative Criteria

- Do not put durable queue logic in `ActiveTurnRegistry`.
- Do not persist queued messages from the controller directly.
- Do not remove or weaken the same-conversation stream `Semaphore` in `ChatService.stream()`.
- Do not refactor the whole chat transport, SSE stack, SimplyPages module, or tool-loop interrupt system.
- Do not store queued messages in `ai_chat_memory` before they are sent.
- Do not route saved plan chat through this queue; this scope is the browser `/chat` conversation surface.
- Do not hide queue state only in `#chat-error`; queued messages need durable visible cards.

## Target API Contract

Add thin controller routes under `ChatController`, delegating to a chat service-owned queue use case:

```text
GET  /api/chat/{conversationId}/pending-messages
POST /api/chat/{conversationId}/pending-messages
POST /api/chat/{conversationId}/pending-messages/claim
POST /api/chat/{conversationId}/pending-messages/{messageId}/ack
POST /api/chat/{conversationId}/pending-messages/{messageId}/release
```

Expected DTOs should be Java records in `io.mindspice.magenta2.ai.chat.model`, for example:

```text
PendingChatMessage(id, conversationId, messageText, model, planningModel, surface, status, position, total, createdAt, updatedAt)
PendingMessageRequest(message, model, planningModel, surface)
ClaimedPendingChatMessage(message, claimToken)
PendingMessageAckRequest(claimToken)
```

Route expectations:

- `GET` returns visible queue rows ordered FIFO; include enough position/count data for the browser card labels.
- `POST` validates UUID conversation id and nonblank message, persists status `PENDING`, and returns the created row/list state.
- `claim` atomically moves the oldest `PENDING` row to `CLAIMED`, stores a claim token/claim timestamp, and returns no content or null-equivalent if none exists.
- `ack` deletes or marks sent only when the claim token matches the claimed row.
- `release` returns a matching claimed row to `PENDING`; stale claimed rows should be recoverable on subsequent list/claim so reloads do not strand a queue item forever.

## Target Storage Contract

Add `ai_chat_pending_messages` to `src/main/resources/schema.sql` and repository-owned schema bootstrap:

```sql
create table if not exists ai_chat_pending_messages (
    id text primary key,
    conversation_id text not null,
    message_order integer not null,
    message_text text not null,
    model text,
    planning_model text,
    surface text,
    status text not null,
    claim_token text,
    claimed_at text,
    created_at text not null,
    updated_at text not null
);
```

Add indexes for `(conversation_id, status, message_order)` and `(conversation_id, message_order)`. The repository may use `created_at` for display, but FIFO claim ordering must be deterministic through `message_order` or equivalent.

## Target UI Contract

- Add `#chat-queued-messages-panel` between `#chat-planning-panel` and `#chat-form` in `ChatModuleRenderer.sessionChatModule()`.
- Render queued rows using `.planning-question-card` as the base style, with small modifiers only if needed.
- Use document/parent-safe rendering patterns from `chat-client.js`; do not attach listeners to elements that are replaced during SSE churn.
- On page load and history switch, fetch pending queue for the active conversation and render cards.
- On mid-turn normal submit, enqueue server-side and render the returned queue state. The input should clear only after enqueue succeeds.
- After each successful streamed turn, drain server-backed queue by claim -> stream -> ack. If any send fails, release the claimed item when possible, keep/re-render the card, and stop draining.
- Mobile and desktop layouts must avoid clipped card text, overlapping composer controls, and stranded gaps.

## Required Closeout

Implementation must update:

- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/schema.md` if the schema examples need a new row or drift note
- relevant `docs/` files for API, data model, frontend behavior, and end-user chat behavior
- `.internal-dev/changelogs/2026-05-25-chat-mid-turn-message-queue.md`
- package `AGENTS.md` files only if responsibilities or conventions change

Reusable gotchas discovered during implementation should update a domain-named file under `.internal-dev/knowledge/`.

## Stop Rules

Stop and return to the main thread before implementation continues if:

- a safe claim/ack design cannot be implemented without changing the SSE stream contract broadly;
- queue persistence conflicts with existing schema initialization patterns;
- the worker would need to remove or weaken the per-conversation stream lock;
- the worker discovers that saved plan chat or agent side-panel chat must be changed to make `/chat` work;
- Playwright validation cannot run and the user has not approved a fallback;
- local model/runtime dependencies block real execution validation after bounded startup succeeds.
