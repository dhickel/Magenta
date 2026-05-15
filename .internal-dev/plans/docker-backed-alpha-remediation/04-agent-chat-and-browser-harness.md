# Phase 04: Agent Chat And Browser Harness

## Context

Phase 06 found that `/api/agents/{agentId}/chat/stream` works, and `agent-chat.js` exists, but no orchestration page imports or initializes it. Phase 01 and the final review also recorded that Playwright MCP disconnected mid-campaign, leaving browser-click, live SSE, visual layout, and console/network checks incomplete.

## Goal

Expose the existing agent chat capability from the operational UI and formalize the Playwright MCP harness required for alpha validation. This phase is implementation plus validation-tooling prep; the final pass remains Phase 5.

## In Scope

- Agent list/detail shell markup in `OrchestrationController.java`.
- Static orchestration JS/CSS only where needed to initialize the existing chat module.
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` updates if the MCP workflow changes.
- Reusable validation snippets or docs under `.internal-dev/knowledge/` or `.internal-dev/reviews/docker-backed-alpha-e2e-validation/`.

## Out of Scope

- Changing the main `/chat` product flow.
- Rewriting the agent chat API contract unless tests prove it cannot support the UI.
- Using curl-only validation as final browser signoff.

## Implementation Steps

1. Read package guides:
   - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
   - `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`

2. Decide the smallest UI exposure for agent chat.
   - Preferred alpha path: add a "Chat" tab or side-panel toggle on `/agents/{agentId}` that initializes `agent-chat.js` with the current agent ID.
   - Keep the existing tab model. Do not touch `/chat`.
   - If using JavaScript, document why: live SSE chat panel initialization and stream handling are better handled in focused JS than HTMX.

3. Wire script loading deliberately.
   - Ensure the relevant operational page loads `/js/orchestration/agent-chat.js`.
   - Add a stable host element such as `data-agent-chat-panel` or `data-agent-chat-agent-id`.
   - Initialize only once per page load or HTMX swap.
   - Avoid global side effects on plans/workflows/jobs pages unless the panel is intentionally global.

4. Align API response handling.
   - Current agent chat SSE emits `start` and `done`, unlike main chat's `start/context/chunk/done`.
   - The UI must handle the actual agent chat contract without assuming chunk events.
   - Add visible error state for failed stream or missing agent.

5. Add browser harness documentation/snippets.
   - Add a focused browser checklist for:
     - page load and console/network capture,
     - HTMX click/swap checks,
     - agent chat open/send/receive,
     - workflow run SSE,
     - output view clicks,
     - plan editor persistence,
     - model dropdown save,
     - mobile sidebar toggle.
   - Include MCP recovery steps for profile locks and disconnected sessions.
   - Make clear that MCP failure blocks alpha validation unless the user explicitly approves a fallback.

6. Add tests.
   - Controller test asserting agent detail includes the chat host/tab/toggle.
   - Static markup test asserting agent chat script is present only where intended.
   - If practical, a lightweight JS/browser test can be added; otherwise Phase 5 must cover the real browser behavior.

## Validation

Run:

```bash
mvn -q -Dtest=OrchestrationControllerTest test
mvn -q -Dtest=AgentOrchestrationControllerTest test
```

Manual/browser prep validation:

- Start app on `http://localhost:18080` with isolated SQLite.
- Open `/agents/{agentId}` with Playwright MCP.
- Confirm chat host/toggle/tab is visible and no console errors occur on load.
- Send a message through the agent chat panel.
- Confirm browser receives `start` and `done`, renders the response, and keeps the page usable.

## Exit Criteria

- Agent chat is reachable from the operational UI.
- JavaScript use is limited and justified.
- Browser validation instructions cover every previously blocked interaction.
- If Playwright MCP still fails, the blocker is recorded and Phase 5 cannot sign off alpha.
