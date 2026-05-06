Scope
- Reviewed the current chat web/API surface for options to automate interactive agent sessions through the same flows a user exercises in the browser.
- Focused on `ChatController`, `FrontendController`, `chat-client.js`, `ChatService`, and existing controller/frontend tests.

Findings
- The HTTP API already exposes the core operations needed for automated chat sessions: streaming chat, commands, conversation history, session metadata, plan answers/actions, streamed plan execution, and active-turn interrupts.
- The browser client owns substantial workflow behavior above raw HTTP: SSE parsing, optimistic pending messages, active turn token tracking, queued messages, command routing, plan-panel rendering/actions, session actions, and post-turn history/session reloads.
- `curl` can drive the server-side chat contract but cannot prove the JavaScript-heavy end-user flow unless paired with a real browser automation layer.
- The current repo has no Node, Playwright, Selenium, HtmlUnit, or jsdom setup, so browser-level interaction is not available from the repo as-is.
- Streaming and synchronous chat paths are intentionally separate today; `.internal-dev/notes/2026-05-04-sync-stream-serialization-gap.md` documents a related concurrency gap worth remembering for any automated driver that mixes endpoints.

Risk Assessment
- A raw HTTP driver is low exposure and useful for agent debugging, but can drift from `chat-client.js` unless it deliberately mirrors the client state machine.
- Browser E2E coverage is the best match for end-user behavior, but adds test infrastructure and can become brittle without stable selectors and deterministic model/tool stubs.
- Production debug endpoints would be convenient for remote sessions but increase exposed surface unless strictly disabled by default, profile-gated, loopback-bound, and separately authenticated.

Recommendations
- Start with an external session driver that talks to existing endpoints and parses SSE. This gives immediate interactive debugging with no application API expansion.
- Add browser E2E tests with Playwright for the JavaScript-heavy flows that the HTTP driver cannot verify: form submission, streaming rendering, queued interrupt UX, plan panel actions, and session list mutations.
- Add deterministic test-profile model/tool fixtures so automated tests can exercise tool calls, delays, interrupts, failures, and plan flows without depending on a live LLM.
- Prefer a local MCP facade outside Magenta if Codex should interact through high-level tools like `send_message`, `interrupt_turn`, and `approve_plan`; register that MCP server in the Codex environment rather than adding Magenta production endpoints.
- Only add an in-app debug endpoint if remote production-like debugging becomes necessary, and keep it disabled by default with explicit profile/property/security controls.

Follow-ups
- Decide whether to build the first driver as a small external CLI script or a registered local MCP server.
- Decide whether browser E2E should be Java-based, such as Selenium, or Node-based, such as Playwright. Playwright is the better fit for SSE/fetch-heavy UI flows.
- Consider extracting reusable SSE/client-state logic from `chat-client.js` if duplication between browser and driver starts to hurt.
