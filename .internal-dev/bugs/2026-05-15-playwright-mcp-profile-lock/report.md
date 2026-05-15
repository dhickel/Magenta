# Summary

Playwright MCP validation was blocked because the configured browser profile was already locked by another browser process.

# Scope

This affects browser-level validation for operational UI work. It does not affect application startup, HTTP route availability, Maven tests, or Docker runtime startup validation.

# Reproduction

Run Playwright MCP browser commands while the existing profile lock is active.

# Expected

Playwright MCP should open or attach to an isolated browser session for interactive route validation.

# Actual

The MCP browser tools returned a browser-in-use error for `/home/hickelpickle/.cache/ms-playwright/mcp-chrome-4e05678` and suggested using an isolated browser instance.

# Evidence

Observed error:

`Browser is already in use for /home/hickelpickle/.cache/ms-playwright/mcp-chrome-4e05678, use --isolated to run multiple instances of the same browser`

# Impact

The final operational UI validation could not complete the browser interaction portion. Live HTTP route checks were used as partial evidence, but this is not equivalent to browser validation.

# Status

Open. Tooling/environment blocker recorded; product code validation passed through tests, live HTTP route checks, and bounded startup smoke.

# Next Action

Run Playwright MCP with an isolated profile or clear the stale profile lock before the next browser validation pass.
