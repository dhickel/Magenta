---
status: active
created: 2026-05-25
owner: validation-redteam-agent
model: gpt-5.5
reasoning: high
source_intent: chat queued mid-turn messages
---

# Validation Checklist

## Review Scope

Validate the implementation against the plan lock, application contracts, architecture fit, persistence safety, UI behavior, and repo closeout workflow. This is one small mutating unit with browser/SSE behavior, so code-level validation and delegated Playwright validation are both required.

## Code And Architecture Checks

- Confirm durable queue logic lives in a chat service plus narrow repository, not in `ChatController` or `ActiveTurnRegistry`.
- Confirm controllers are thin and only validate/map request and response concerns.
- Confirm `ChatService.stream()` still preserves the per-conversation `Semaphore` overlap guard and existing error behavior.
- Confirm the interrupt endpoint still exists and is not used for ordinary mid-turn form submissions.
- Confirm queued messages are stored separately from `ai_chat_memory` and only enter chat history when sent through the normal stream path.
- Confirm `clearConversation()` deletes the pending queue.
- Confirm route and DTO naming is direct and documented if it differs from the spec lock.
- Confirm schema initialization works both from `schema.sql` and repository bootstrap on a clean SQLite DB.
- Confirm claim/ack/release cannot lose a message if stream start/fetch fails.
- Confirm stale claimed rows can recover after reload or client interruption.
- Confirm FIFO ordering is deterministic.

## UI And Browser Checks

- Confirm `#chat-queued-messages-panel` is between `#chat-planning-panel` and `#chat-form`.
- Confirm queued cards reuse `.planning-question-card` styling and show text, count/order, and status.
- Confirm page load/history switch fetches and renders pending queue.
- Confirm normal mid-turn submit enqueues visibly and clears input only after enqueue succeeds.
- Confirm commands submitted mid-turn are not queued and show explicit feedback.
- Confirm planning question answers are still sent through plan answer routes, not the queue.
- Confirm queued FIFO drain starts after active stream completion, clears cards after ack, and stops safely on failure.
- Confirm mobile and desktop composer screenshots have no clipped text, overlap, incoherent spacing, or hidden queue state.

## Test Expectations

Required automated coverage:

- Repository FIFO, persistence, claim, ack, release, stale claim recovery, delete by conversation.
- Service validation and no-memory-before-send behavior where practical.
- Controller enqueue/list/claim/ack/release payloads, UUID validation, blank message rejection, not-found/mismatch behavior where applicable.
- Conversation clear deletes pending queue.
- Existing same-conversation stream overlap guard remains covered.
- History does not include queued messages before they are sent.
- Browser/client behavior has either focused JS-level coverage if available or is covered by Playwright evidence.

## Required Commands

Run from repo root and inspect output/exit status:

```bash
git status --short
mvn test -Dtest=ChatPendingMessageRepositoryTest,ChatPendingMessageServiceTest,ChatControllerTest,ChatServiceTest
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
git diff --name-status
git diff --check
```

If exact test class names differ, run the equivalent focused tests and record the substituted command.

## Delegated Playwright Checklist

Use a separate Playwright/browser validation agent after code-level validation. It must use the live app with an isolated SQLite database and follow `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.

Checklist:

- Start a slow chat turn on `/chat`.
- Submit a normal second message while the first turn is running.
- Verify a queued card appears above the input with queued text, count/order, and status.
- Reload the page and verify the queued card persists.
- Let the active turn finish and verify the queued message is sent automatically, appears in history as a normal user turn, and the queued card clears after ack.
- Queue at least three messages mid-turn and verify FIFO send order.
- Submit a slash command mid-turn and verify it is not queued.
- Start/answer a planning question and verify answers are not routed through the queue.
- Capture desktop and mobile screenshots of the composer with queued cards.
- Report console/network errors and separate expected negative-test noise from regressions.

## Closeout Checks

- `.internal-dev/specifications/api.md`, `services.md`, `web.md`, and schema-related spec content were updated or explicitly justified.
- `docs/end-user/chat.md`, `docs/technical/api-reference.md`, `docs/technical/data-model.md`, and `docs/technical/frontend-htmx.md` were updated if their scope includes the changed behavior.
- `.internal-dev/changelogs/2026-05-25-chat-mid-turn-message-queue.md` exists and includes `Specification Impact`.
- Any new reusable gotcha is recorded in `.internal-dev/knowledge/`.
- No unrelated existing dirty files were modified or committed.

## Pass/Fail Rule

Do not mark validation passed until automated tests, bounded startup, code review, docs/spec closeout, and delegated Playwright evidence all reconcile with the criteria. If Playwright cannot run, mark validation blocked unless the user explicitly approves a fallback.

## Remediation Handoff Shape

If validation fails, return a concise handoff to the same implementation worker with:

- failing criterion;
- exact file/line or route/table when available;
- observed command/browser evidence;
- required correction;
- whether revalidation can resume from the failed criterion or must restart the full checklist.
