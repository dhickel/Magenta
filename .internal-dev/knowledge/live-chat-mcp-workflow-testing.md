# Topic

Using the Playwright MCP server to test Magenta live chat workflows.

# Source References

- `~/.codex/config.toml`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java`
- Live MCP validation run on 2026-05-05 against `http://localhost:18080` with `jdbc:sqlite:/tmp/magenta2-mcp-browser-test.sqlite`
- Live MCP validation refresh on 2026-05-06 against `http://localhost:18080` with `jdbc:sqlite:/tmp/magenta2-mcp-fix-validation.sqlite`

# Key Takeaways

The Playwright MCP server works with the bundled Playwright Chromium binary when configured with `--executable-path`:

```toml
[mcp_servers.playwright]
command = "npx"
args = ["-y", "@playwright/mcp@latest", "--headless", "--executable-path=/home/hickelpickle/.cache/ms-playwright/chromium-1217/chrome-linux64/chrome", "--viewport-size=1280x720", "--allowed-origins=http://localhost:8080;http://localhost:18080"]
```

Restart the Codex/MCP session after changing this config. The old `--browser=chrome` setting expects system Chrome and can fail with a missing `/opt/google/chrome/chrome`.

Run live workflow tests against an isolated SQLite database so chat history, sessions, and plan state are real without mutating the normal `chat-memory.db`:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-mcp-browser-test.sqlite --magenta.executor.chat-threads=4'
```

Prefer a fresh database per validation pass. Delete or rotate the `/tmp` database name when the test needs clean session counts or when a previous MCP timeout may have left execution state behind.

Open the live chat page with MCP first:

```js
await page.goto('http://localhost:18080/chat');
```

Assert the browser surface exists before endpoint work: page title `Magenta Chat`, `[data-chat-root="true"]`, `#chat-form`, `#chat-input`, `#chat-model-select`, `#chat-planning-model-select`, `#chat-history`, `#chat-planning-panel`, and active session text `New chat`.

Use `mcp__playwright__.browser_run_code_unsafe` to run browser-side workflow probes from the page. Keep each probe bounded. The MCP tool can time out at about 120 seconds, and a large all-in-one suite can leave a browser-side stream request in an ambiguous state. Split long validation into focused probes: smoke, concurrency, session mutations, interrupt, planning, model-specific checks, and console/network capture.

The browser-facing workflow is centered on `/chat`, but the live contract is the same `/api/chat` controller used by `chat-client.js`:

- `POST /api/chat/stream` for normal SSE chat turns.
- `POST /api/chat/commands` for `/new`, `/switch <uuid>`, `/clear`, `/plan`, `/exit-plan`, and `/exec-plan`.
- `GET /api/chat/sessions` and `GET /api/chat/{conversationId}/history` for session switching and state assertions.
- `POST /api/chat/turns/{turnId}/interrupt` for mid-stream user interruption.
- `POST /api/chat/{conversationId}/plan/answers`, `PATCH /plan/approve`, `PATCH /plan/continue`, and `POST /plan/execute/stream` for the planning lifecycle.

For SSE tests, parse named events in the browser page and assert event names and state, not just final text. A healthy simple stream on `local-qwen` produced `start -> context -> chunk -> done`, with `conversationId`, `model`, `turnId`, `interruptToken`, `contextUsage`, and `planState` in event payloads. Confirm persistence by loading `/api/chat/{conversationId}/history` after the stream completes.

Cross-chat concurrency works when separate browser-side stream requests target separate conversations. A live MCP run completed one `local-qwen` conversation and one `local-gemma-e4b` conversation concurrently with distinct conversation IDs and independent `done` events.

Only one active stream is allowed per chat. In the MCP run, two browser-side `POST /api/chat/stream` requests targeting the same conversation produced one normal completion and one `start -> error` sequence with `Another stream is already active for conversation <uuid>`.

Switching chats is validated with `/switch <uuid>` plus `/history`. In the MCP run, `/switch` returned the target conversation, stored model, normal plan state, and the same history count as direct history load. `/new` returned `conversationId: null`, message `New chat`, empty history, and normal plan state.

Plan mode is a multi-turn state machine. `/plan` entered `PLAN` / `DRAFT` and queued a planning question. Answering through `/plan/answers` advanced the draft to `READY_FOR_APPROVAL`; `PATCH /plan/approve` moved it to `APPROVED`. Execution should be tested through `/plan/execute/stream`, because that is the browser execution path.

After any plan execution stream, always reload `/api/chat/{conversationId}/history` and assert persisted plan state. Do not trust only the last SSE event or the browser tool's return value. In the 2026-05-06 validation refresh, one execution completed after the MCP tool timed out, and the persisted history was the source of truth showing `mode=NORMAL`, `status=COMPLETED`. Another execution remained stuck in `mode=EXECUTE_PLAN`, `status=EXECUTING` while containing failure evidence, which was only obvious after a history reload.

Add mutation and negative-path checks after the core stream checks:

- Rename, favorite, archive, and unarchive a known conversation, then inspect the returned `ChatSession`.
- Use `/new` and verify it returns `conversationId: null` and empty history.
- Send invalid commands such as `/switch not-a-uuid` and `/does-not-exist`; expect HTTP 400 and watch how the browser surfaces the error.
- Capture console messages and network requests with MCP so expected 400s are separated from unexpected frontend errors.

Observed MCP-run gotchas:

- The page currently logs a browser console error for missing `http://localhost:18080/webjars/htmx.org/dist/htmx.min.js`. The chat controls and API workflow still loaded during this run.
- `granite4.1:8b` previously rejected configured thinking options with `400 - "\"granite4.1:8b\" does not support thinking"`. After setting its thinking level to off, it no longer returned the thinking-option error in the 2026-05-06 refresh, but it still behaved unreliably on exact-output prompts and invoked a tool before answering. Verify both transport success and task-following quality for model-specific checks.
- Starting `/plan` with `planningModel: local-qwen` returned model `deepseek-v4-pro` during this run. Treat planning model selection as state to verify from payloads and history.
- Interrupt calls can return `ACCEPTED` without visibly changing the final assistant text if the model completes the original turn before observing the interrupt. Validate the interrupt response and the final persisted history.
- Saved plan execution previously failed because the execution model supplied scalar text such as `None. Execution followed the plan exactly.` for a list-valued tool argument. The 2026-05-06 refresh validated that scalar-to-list coercion fixed this original failure: the saved plan completed and recorded evidence instead of failing deserialization.
- A new plan execution issue was observed after an MCP timeout: a plan remained `EXECUTE_PLAN` / `EXECUTING` while already containing failure evidence from a mode-gated tool call. Log and inspect persisted state after MCP timeouts before deciding whether execution succeeded, failed, or is stuck.
- Invalid command tests intentionally create browser console 400 errors. Record them as expected test noise, not frontend failures, unless the UI fails to display a useful error to the user.
- A 2026-05-07 orchestration UI validation found that Spring's webjar resource handler can intercept `/webjars/htmx.org/dist/htmx.min.js` before `static/webjars/...` resources. If SimplyPages still emits that versionless HTMX URL, validate in the browser that it returns HTTP 200; a controller compatibility route fixed the 404 for this phase.
- A 2026-05-07 final orchestration validation attempt found the MCP browser could be blocked by an existing profile lock at `~/.cache/ms-playwright/mcp-chrome-*`. Treat that as an MCP infrastructure blocker, then either restart the MCP session with an isolated profile or run an explicitly documented fallback browser probe against the same live app.
- A 2026-05-07 wide orchestration validation pass found that collapsed agent side-panel chat hosts are attached but not visible on orchestration pages. Assert `[data-agent-chat-panel]` attachment for page coverage, then separately test visibility/toggle behavior when the panel interaction itself is in scope.
- Agent detail orchestration tabs render through one dynamic `#agent-tab-panel`; do not expect separate persistent DOM nodes for inbox, queue, schedules, reactions, workspace, or history panels.

# Engine Relevance

Playwright MCP is the preferred way to test live chat behavior because it verifies the browser can load the page and then exercises the same origin, fetch behavior, SSE stream handling, and endpoint contracts that the frontend depends on.

Use short exact-output prompts for baseline model checks. Use two different configured models for cross-chat concurrency. Use the same conversation ID for the active-stream lock test. Use a harmless chat-only plan for planning validation so execution issues are not mixed with file, shell, or web-search side effects.

Recommended MCP test shape:

1. Open `/chat` with `browser_tabs` or `browser_run_code_unsafe`.
2. Assert the expected DOM controls exist.
3. From `page.evaluate`, create a small SSE parser for browser `fetch` responses.
4. Run a `local-qwen` smoke stream and verify `start -> context -> chunk -> done`.
5. Load `/history` for the returned conversation ID and verify persisted messages and plan state.
6. Run two simultaneous stream requests for two separate conversations and verify both finish.
7. Run two overlapping stream requests for one conversation and verify the second gets the active-stream error.
8. Validate `/switch <uuid>`, `/history`, and `/new`.
9. Validate session mutation endpoints: rename, favorite, archive, unarchive.
10. Validate negative commands and capture their expected 400 responses.
11. Run `/plan`, answer the queued planning question, approve, and execute through `/plan/execute/stream`.
12. After plan execution, reload `/history` and assert the persisted terminal state (`COMPLETED`, `NEEDS_REVIEW`, or a logged stuck-state bug).
13. Run at least one model-specific probe for a non-default configured model.
14. Capture console messages and network requests with MCP when the browser surface itself is under test.

Keep long-running plan execution tests separate from fast smoke and mutation tests. If a Playwright MCP call times out, immediately run a small follow-up probe that lists sessions and histories. A timeout does not prove the server stopped; it may still complete and persist state after the MCP call returns an error.

# Open Questions

- Should the project include a reusable Playwright MCP smoke-test snippet for SSE parsing and workflow assertions?
- Should the missing htmx webjar be fixed or removed from the page if it is no longer required?
- Should model configuration validate thinking-option compatibility before a live chat turn reaches Ollama?
- Are there other Spring AI tool argument shapes, beyond scalar-to-list, that should be coercion-tested with live models?
- Should plan execution stream timeouts and client disconnects always move saved plans to `NEEDS_REVIEW`?
- Should interrupt acceptance imply stronger cancellation semantics, or is best-effort interruption sufficient for the current chat workflow?
