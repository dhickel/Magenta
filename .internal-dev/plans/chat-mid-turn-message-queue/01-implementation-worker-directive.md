---
status: active
created: 2026-05-25
owner: implementation-worker-agent
model: gpt-5.5
reasoning: medium
source_intent: chat queued mid-turn messages
---

# Implementation Worker Directive

## Assignment

Implement the locked small feature in `00-specification-lock.md`: persistent visible queueing for normal browser `/chat` messages submitted while a turn is streaming. Complete code, tests, docs/spec updates, changelog, bounded startup, and handoff evidence in one pass.

## Required Context To Read First

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/schema.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/knowledge/chat-context-management-and-tools.md`
- `.internal-dev/knowledge/event-delegation-sse-dom-replacement.md`
- `.internal-dev/knowledge/chat-planning-composer-architecture.md`
- Closest package guides:
  - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/repository/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/model/AGENTS.md`

## Expected Editable Files

Likely production targets:

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatRequest.java`
- new model record file(s) under `src/main/java/io/mindspice/magenta2/ai/chat/model/`
- new repository under `src/main/java/io/mindspice/magenta2/ai/chat/repository/`
- new service under `src/main/java/io/mindspice/magenta2/ai/chat/service/`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/css/magenta.css`

Likely test targets:

- new repository/service tests under `src/test/java/io/mindspice/magenta2/ai/chat/repository/` and/or `src/test/java/io/mindspice/magenta2/ai/chat/service/`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- existing chat service tests if clear/delete/history behavior is already covered there

Expected docs/internal-dev targets:

- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/schema.md`
- `.internal-dev/changelogs/2026-05-25-chat-mid-turn-message-queue.md`
- `docs/end-user/chat.md`
- `docs/technical/api-reference.md`
- `docs/technical/data-model.md`
- `docs/technical/frontend-htmx.md`

Only edit package `AGENTS.md` files if the feature changes ownership/conventions beyond this specific implementation.

## Forbidden Scope

- No broad chat transport refactor.
- No SimplyPages framework changes.
- No saved plan chat queueing.
- No agent side-panel chat queueing.
- No removal of `ChatService.stream()` per-conversation `Semaphore`.
- No durable queue state in `ActiveTurnRegistry`.
- No controller-owned SQL/JDBC/persistence.
- No behavior that silently treats normal queued messages as tool-loop interrupts.

## Implementation Steps

1. Run `git status --short` and note unrelated existing changes. Preserve anything you did not create.
2. Re-check the current code anchors from the spec lock before editing: `ChatModuleRenderer`, `ChatController`, `ChatService.stream()`, `ActiveTurnRegistry`, and `chat-client.js`.
3. Add `ai_chat_pending_messages` to `schema.sql`.
4. Add a narrow repository, for example `ChatPendingMessageRepository`, that owns schema bootstrap and SQLite operations:
   - enqueue with deterministic per-conversation FIFO order;
   - list visible queue rows for a conversation;
   - atomically claim oldest pending row with token;
   - ack/delete only matching claimed row/token;
   - release matching claimed row/token;
   - recover stale claimed rows before list/claim or through a narrow helper;
   - delete by conversation id.
5. Add a service, for example `ChatPendingMessageService`, under `ai.chat.service`:
   - validate nonblank message and conversation id;
   - normalize optional model/planning model/surface;
   - expose enqueue/list/claim/ack/release/delete use cases;
   - keep persistence details hidden from controllers and `chat-client.js`.
6. Wire `ChatService.clearConversation()` to delete pending messages for the conversation alongside chat memory/session metadata/plan/context cleanup.
7. Add thin `ChatController` endpoints matching the spec lock route shape. Use Java records for request/response payloads. Validate UUID path values consistently with nearby routes.
8. Add `#chat-queued-messages-panel` in `ChatModuleRenderer.sessionChatModule()` between `#chat-planning-panel` and `#chat-form`.
9. Update `chat-client.js`:
   - replace/wrap the local `queuedMessages` array with server-backed enqueue/list/claim/ack/release behavior;
   - keep planning answers routed through `submitPlanningAnswer()`;
   - keep commands mid-turn out of the queue with explicit user feedback;
   - enqueue normal mid-turn messages instead of calling the interrupt endpoint by default;
   - load/render pending queue on init and session/history changes;
   - after a successful stream, claim and send FIFO queued rows through the normal stream path, ack only after success, then refresh cards;
   - release claimed rows when a queued send fails before ack.
10. Keep the explicit interrupt endpoint and active turn metadata intact for future/manual interrupt semantics, but do not use it for ordinary form submissions.
11. Style `#chat-queued-messages-panel` by reusing `.planning-question-card`; add only small queue-specific selectors if the base card needs a status/footer treatment.
12. Add focused tests:
   - repository FIFO and SQLite persistence;
   - claim/ack/release/stale-claim recovery;
   - delete by conversation clears pending queue;
   - queued messages are not in history until sent;
   - controller enqueue/list/claim/ack/release status and validation;
   - existing same-conversation stream overlap guard remains.
13. Update specs/docs/changelog required by `00-specification-lock.md`.
14. Run validation commands and fix failures before handoff.

## Experience Contract

- The queue panel sits directly above the input, below any active planning question panel.
- Queued cards visually match planning question cards closely enough to read as the same composer affordance.
- Each card must show the queued text, a compact count label such as `Queued 1/3`, and a status line such as `Will send after this turn finishes`.
- Multiple queued cards preserve order and do not resize the composer unpredictably.
- On desktop, cards should use available composer width without clipping long text.
- On mobile, labels and message text wrap cleanly; no card text overlaps the submit button or input.
- During drain, the currently sending queued card may show `Sending...`, but only if this can be done without complicating the queue contract. It is acceptable to remove the card after ack.
- The transient `#chat-error` may still show failures, but it is not the primary queue indicator.

## Validation Commands

Run focused tests first, then broader validation:

```bash
mvn test -Dtest=ChatPendingMessageRepositoryTest,ChatPendingMessageServiceTest,ChatControllerTest,ChatServiceTest
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
git diff --check
```

If exact new test class names differ, substitute the created class names and report the exact command used.

For browser validation, follow `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` and delegate the Playwright pass to the browser validation agent after code-level validation. Use an isolated SQLite DB and a bounded local model/stub strategy suitable for slow-turn testing.

## Acceptance Criteria

- All criteria in `00-specification-lock.md` pass.
- The implementation report lists every file changed and explains any changed route/table names from the suggested shape.
- Focused and full automated tests pass, or failures are unrelated and documented with evidence.
- Bounded Spring startup passes, or the blocking dependency is explicit.
- Playwright evidence covers slow turn queueing, reload persistence, FIFO drain, commands/planning negative checks, and desktop/mobile visual quality.
- Specs, docs, and changelog are updated.
- No unrelated dirty worktree changes are included in the final commit.

## Stop Conditions

Stop and return a blocked handoff if:

- implementation requires weakening same-conversation stream locking;
- queued sends cannot be retried safely after stream-start failure;
- the worker cannot produce a persistent queue without moving persistence into controller or `ActiveTurnRegistry`;
- browser validation cannot run and no user-approved fallback exists;
- tests reveal that queueing normal messages would break planning question answer semantics.

## Do Not Close Unless

- `ai_chat_pending_messages` exists in schema and repository bootstrap.
- Queue operations are service-owned and controller-thin.
- Browser mid-turn normal messages visibly enqueue and persist.
- Queue drain uses claim/ack and does not delete before successful stream completion.
- Conversation clear deletes pending queue.
- Same-conversation overlap guard remains covered.
- Specs/docs/changelog are complete.
- Required tests, startup, and delegated Playwright validation are reported.
