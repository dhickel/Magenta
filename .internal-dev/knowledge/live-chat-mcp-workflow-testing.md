# Topic

Using the Playwright MCP server to test Magenta live chat workflows.

# Source References

- `~/.codex/config.toml`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java`
- Live MCP validation run on 2026-05-05 against `http://localhost:18080` with `jdbc:sqlite:/tmp/magenta2-mcp-browser-test.sqlite`

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

Open the live chat page with MCP first:

```js
await page.goto('http://localhost:18080/chat');
```

Assert the browser surface exists before endpoint work: page title `Magenta Chat`, `[data-chat-root="true"]`, `#chat-form`, `#chat-input`, `#chat-model-select`, `#chat-planning-model-select`, `#chat-history`, `#chat-planning-panel`, and active session text `New chat`.

Use `mcp__playwright__.browser_run_code_unsafe` to run browser-side workflow probes from the page. The browser-facing workflow is centered on `/chat`, but the live contract is the same `/api/chat` controller used by `chat-client.js`:

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

Observed MCP-run gotchas:

- The page currently logs a browser console error for missing `http://localhost:18080/webjars/htmx.org/dist/htmx.min.js`. The chat controls and API workflow still loaded during this run.
- `granite4.1:8b` rejected configured thinking options with `400 - "\"granite4.1:8b\" does not support thinking"`. Verify model-specific options through MCP before treating a configured model as usable.
- Starting `/plan` with `planningModel: local-qwen` returned model `deepseek-v4-pro` during this run. Treat planning model selection as state to verify from payloads and history.
- Interrupt calls can return `ACCEPTED` without visibly changing the final assistant text if the model completes the original turn before observing the interrupt. Validate the interrupt response and the final persisted history.
- Saved plan execution failed because the execution model supplied scalar text such as `None. Execution followed the plan exactly.` for a list-valued tool argument. The app correctly recorded `NEEDS_REVIEW` evidence, but the execution stream ended with `error`, not `done`.

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
9. Run `/plan`, answer the queued planning question, approve, and execute through `/plan/execute/stream`.
10. Capture console messages and network requests with MCP when the browser surface itself is under test.

# Open Questions

- Should the project include a reusable Playwright MCP smoke-test snippet for SSE parsing and workflow assertions?
- Should the missing htmx webjar be fixed or removed from the page if it is no longer required?
- Should model configuration validate thinking-option compatibility before a live chat turn reaches Ollama?
- Should plan execution tools coerce scalar strings into one-item lists or reject them in a model-retryable way?
- Should interrupt acceptance imply stronger cancellation semantics, or is best-effort interruption sufficient for the current chat workflow?
