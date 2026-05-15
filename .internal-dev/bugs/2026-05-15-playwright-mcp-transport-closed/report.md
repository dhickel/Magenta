# Summary
Playwright MCP browser validation was blocked because the MCP browser transport closed after cleaning stale profile lock processes.

# Scope
Affects local browser-based validation workflows that rely on `mcp__playwright__` tool calls during this refactor validation pass.

# Reproduction
1. Start app successfully (`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8091`).
2. Attempt Playwright MCP navigation (`browser_navigate`).
3. Tool reports profile-in-use lock for `mcp-chrome-4e05678`.
4. Kill stale profile/Playwright processes.
5. Retry `browser_navigate`.
6. Tool fails with `Transport closed`.

# Expected
Playwright MCP should open a browser session and allow `/workflows` end-to-end validation.

# Actual
Browser tool calls fail with transport errors, preventing Playwright validation execution.

# Evidence
- `Error: Browser is already in use for ... mcp-chrome-4e05678`
- `tool call failed for playwright/browser_navigate ... Caused by: Transport closed`

# Impact
Blocks the Playwright release-gate scenario for this change set despite successful automated tests and startup smoke.

# Status
Open

# Next Action
Restore Playwright MCP server/session health (or restart connector process cleanly), then rerun the workflow v2 graph composer browser scenario.
