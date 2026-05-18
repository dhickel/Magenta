# Summary

The chat page requests a missing htmx webjar asset.

# Scope

Browser load of `/chat` and frontend console/network hygiene.

# Reproduction

1. Start the app.
2. Open `http://localhost:18080/chat` through Playwright MCP.
3. Read browser console messages or network requests.

# Expected

The chat page should load without missing static asset errors.

# Actual

The browser console reports:

`Failed to load resource: the server responded with a status of 404 () @ http://localhost:18080/webjars/htmx.org/dist/htmx.min.js`

# Evidence

Playwright MCP console and network captures during the 2026-05-06 live validation run repeatedly showed `GET /webjars/htmx.org/dist/htmx.min.js => 404`. Chat controls and API workflows still loaded, so the missing asset does not currently block the tested chat interactions.

# Impact

The console error makes browser validation noisier and can mask more meaningful frontend failures. If any current or future behavior relies on htmx, that behavior will fail at runtime.

# Status

Open.

# Next Action

Either add the expected htmx webjar dependency/static asset or remove the script reference if htmx is no longer used by the chat page.
