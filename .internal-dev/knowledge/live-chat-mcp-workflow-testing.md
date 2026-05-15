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
- Third-pass readiness fallback validation on 2026-05-08 against `http://localhost:18081` with `jdbc:sqlite:/tmp/magenta2-third-pass-browser.sqlite`

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
- A 2026-05-08 third-pass validation used an isolated Chromium DevTools Protocol fallback after Playwright MCP hit the `mcp-chrome-4e05678` profile lock. For SSE lifecycle tests, read incrementally from `response.body.getReader()` and return as soon as the target first event arrives. For task/workflow transport checks, abort intentionally after `started`, then inspect server logs for `onErrorDropped`, `ResponseBodyEmitter has already completed`, `broken pipe`, and `AsyncRequestNotUsableException`.
- If local model services are unavailable, run browser transport validation against a deterministic local OpenAI-compatible stub and point an isolated AI config at it. This keeps `/chat` and side-panel SSE browser contracts testable without depending on Ollama availability or model latency.

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

# Operational UI Browser Validation Checklist

This checklist covers every browser interaction that must be validated through Playwright MCP before the alpha sign-off (Phase 5). Each item should be verified with the browser open at `http://localhost:18080` using an isolated SQLite database.

## Smoke and basic page load

- Open `/dashboard` and verify page title, sidebar navigation, top banner, and dashboard stats strip.
- Open `/agents` and verify agent list table, filter input, Create/Reload buttons.
- Open `/agents/{agentId}` and verify agent detail page loads with tabs (dashboard, queue, inbox, jobs, schedules, reactions, workspace, outputs, history, chat), profile editor side panel, and submit work form.
- Open `/plans`, `/workflows`, `/jobs`, `/projects`, `/settings`, `/outputs`, `/inbox` and verify each page renders its shell, header, and primary HTMX containers.
- Capture console messages and network requests for every page load. Separate expected noise (400 responses from intentional negative tests, webjar warnings) from unexpected errors.

## HTMX click and swap checks

- On `/agents`, type in the agent filter and verify the list filters via HTMX partial without full page reload.
- On `/agents/{agentId}`, click each tab (dashboard, queue, inbox, jobs, schedules, reactions, workspace, outputs, history, chat) and verify content loads via `hx-get` into `#agent-tab-panel`.
- On `/agents/{agentId}`, click Wake/Sleep/Restart/Refresh/Enable/Disable buttons and verify the action completes and the list refreshes.
- On `/plans`, click Create and verify the editor loads via HTMX. Edit fields, save, and verify persistence.
- On `/workflows`, verify the node editor loads and node/route changes persist.
- On `/jobs`, verify job list loads, job editor opens, and job items can be added/removed.
- On `/projects`, verify project list, detail view, and agent membership management.

## Agent chat open/send/receive

- Open `/agents/{agentId}`, click the Chat tab. Verify the agent chat panel appears (fixed position, bottom-right).
- Verify the panel renders with header "Agent Chat", a toggle button, message area, and input form.
- Send a message through the chat panel and verify:
  - The message appears in the panel as a user message.
  - An SSE `start` event is received with `agentId` and `agentName`.
  - An SSE `done` event is received with `message` containing the assistant response.
  - The response is rendered in the panel.
  - No `chunk` events are assumed (agent chat SSE contract is `start`/`done`/`error`).
- Send a blank message and verify an error state is displayed.
- Close the panel (toggle button) and verify it collapses without removing chat history.
- Re-open the panel and verify it expands.
- Switch to another tab (e.g., dashboard) and back to chat. Verify the chat panel reinitializes.

## Workflow run SSE

- On `/workflows`, create or select a workflow, submit it for execution.
- Verify the run SSE stream emits `started` with a run ID.
- Verify the stream emits progress/status events.
- After the stream completes, verify the run appears in agent history with terminal status.

## Output view clicks

- On `/agents/{agentId}`, click the Outputs tab and verify output artifacts are listed.
- Click an output artifact and verify content loads (text view or download link).
- On `/outputs`, verify the global output listing shows artifacts with attribution (agent, job, project, run type).

## Plan editor persistence

- On `/plans`, create a new plan, edit title, goal, steps, and field definitions.
- Save the plan and reload the page. Verify all fields persist.
- Change work type profile, model selection, and verify they persist after save.

## Model dropdown save

- On `/agents/{agentId}`, edit the agent profile in the side panel.
- Change the default model dropdown and save.
- Reload the page and verify the model selection persisted.
- Verify the model dropdown shows available models from the AI config.

## Mobile sidebar toggle

- Resize the browser viewport to mobile width (under 900px).
- Verify the sidebar collapses or toggles correctly.
- Verify content is still navigable and readable at narrow widths.

## Console and network capture

- After running the full checklist, capture final console messages with `browser_console_messages`.
- Capture network requests with `browser_network_requests`.
- Verify no unexpected 500 errors or JavaScript exceptions (excluding known webjar noise).

# MCP Recovery Steps

If Playwright MCP disconnects or is blocked by a profile lock, use these recovery steps:

1. **Profile lock**: If MCP fails with a profile lock error (e.g., `mcp-chrome-4e05678` or similar under `~/.cache/ms-playwright/mcp-chrome-*`):
   - Kill any lingering Chromium processes: `pkill -f "chrome.*mcp-chrome"` or `pkill -f "chromium.*mcp-chrome"`.
   - Remove the locked profile directory: `rm -rf ~/.cache/ms-playwright/mcp-chrome-*`.
   - Restart the MCP session (restart Codex or the Playwright MCP server process).

2. **Disconnected session**: If MCP tools return timeout or connection errors mid-campaign:
   - Run a small follow-up probe that fetches a simple page (e.g., `GET /dashboard`) to determine if the MCP server or the Magenta app is down.
   - If the Magenta app is still running, restart only the MCP server.
   - If the app is down, restart it with the same isolated SQLite database to preserve state.

3. **MCP timeout during long-running tests**: If a long-running test (plan execution, workflow run) times out:
   - Do not assume failure. Immediately run a follow-up probe: load agent history or run status to inspect persisted state.
   - A timeout means the MCP call exceeded its limit, not that the server failed. The operation may have completed and persisted state after the timeout.

4. **Isolated Chromium DevTools Protocol fallback**: If MCP cannot be recovered after profile cleanup and restart, use the CDP fallback documented in the 2026-05-08 third-pass validation:
   - Launch Chromium with `--remote-debugging-port=9222 --user-data-dir=/tmp/magenta-cdp-profile`.
   - Connect via CDP WebSocket and run the same browser probes.
   - For SSE lifecycle tests, read incrementally from `response.body.getReader()` and return as soon as the target event arrives.
   - Document this as a fallback and record it in the validation evidence.

5. **Explicit blocker recording**: If MCP remains unavailable after all recovery steps, record the blocker with:
   - The exact error message or disconnection symptom.
   - The recovery steps attempted.
   - A statement that alpha validation is blocked unless the user explicitly approves a fallback validation path.

**Critical**: MCP failure blocks alpha validation unless the user explicitly approves a fallback. Curl-only validation can support diagnosis but cannot sign off user-facing browser flows (HTMX swaps, SSE in the browser, visual layout, console errors).

- A 2026-05-13 Phase 5 final validation pass confirmed the MCP browser on `http://localhost:18080` with zero console errors across dashboard, agents, agent detail, plans, outputs, and settings pages. The previous `mcp-chrome-*` profile lock issue did not recur. The app must be started on a port listed in the MCP server's `--allowed-origins` (currently 8080 and 18080). Port 18081 will fail with `ERR_BLOCKED_BY_CLIENT`.
- A 2026-05-13 Phase 5 operational UI overall check found that the settings page model dropdowns correctly use canonical aliases in the format `canonical-key (raw-name)` — for example `local-qwen (qwen3.6:35b)`. This matches the Phase 3 fix for model alias/raw-name confusion.
- A 2026-05-13 Phase 5 chat validation found that the `/api/chat/commands` endpoint only supports `/new` and `/plan` command roots. The `/switch`, `/clear`, `/exit-plan`, and `/exec-plan` commands documented in earlier validation are no longer present. Session switching is now handled through the browser-side HTMX client, not the commands API. Session mutation uses `PATCH /api/chat/{conversationId}/title`, `PATCH /api/chat/{conversationId}/favorite`, and `PATCH /api/chat/{conversationId}/archive` (not POST or PUT).
- A 2026-05-13 Phase 5 plan mode test confirmed that `/plan` enters PLANNING mode with `clarification_questions`, and answering via `POST /api/chat/{conversationId}/plan/answers` advances the draft. The plan service produces `DRAFT` -> `READY_FOR_APPROVAL` -> `APPROVED` state transitions.
- A 2026-05-13 Phase 5 output validation confirmed that `GET /outputs/_content/{artifactId}` and `GET /api/outputs/{artifactId}/download` work correctly for viewing and downloading output content (DEFECT-07-01 fixed). The outputs page lists artifacts with agent/plan/run attribution and provides View and Download buttons.

# Open Questions

- Should the project include a reusable Playwright MCP smoke-test snippet for SSE parsing and workflow assertions?
- Should the missing htmx webjar be fixed or removed from the page if it is no longer required?
- Should model configuration validate thinking-option compatibility before a live chat turn reaches Ollama?
- Are there other Spring AI tool argument shapes, beyond scalar-to-list, that should be coercion-tested with live models?
- Should plan execution stream timeouts and client disconnects always move saved plans to `NEEDS_REVIEW`?
- Should interrupt acceptance imply stronger cancellation semantics, or is best-effort interruption sufficient for the current chat workflow?
- Should the `ai_workflow_definitions` table name in `ai.chat.workflow.WorkflowRepository` be reconciled with the `workflow_definitions` table in the schema, or should the legacy chat workflow code be removed? (Discovered during Phase 5 validation — OrchestrationRunnerService imports the wrong WorkflowService.)
