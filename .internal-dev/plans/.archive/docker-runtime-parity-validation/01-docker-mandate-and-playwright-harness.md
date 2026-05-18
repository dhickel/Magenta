# Phase 01: Docker Mandate And Playwright Harness

## Context

This campaign only has value if Docker is a hard gate and the browser harness is healthy before deeper testing starts.

## Goal

Start Magenta in a clean Docker-enabled environment, verify Docker readiness from both backend and UI, establish reusable Playwright helpers, and record the exact environment used by later phases.

## In Scope

- Isolated SQLite database and bounded app startup.
- Docker/Podman daemon readiness and configured image availability.
- Playwright MCP or approved browser harness availability.
- HTMX shell/asset verification.
- Shared evidence helpers for console, network, screenshots, SSE, and persisted-state checks.

## Out of Scope

- Product behavior beyond readiness checks.
- Host-only fallback validation.

## Implementation Steps

1. Start the app with Docker enabled on an isolated database and an allowed browser origin.
2. Open `/dashboard`, `/agents`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/outputs`, `/inbox`, and `/chat` with Playwright.
3. Verify the real HTMX asset loads and the pages are usable HTML, not raw JSON or placeholder fragments.
4. Verify Docker readiness through the UI and the runtime endpoint:
   - enabled flag
   - daemon reachability
   - Docker host
   - configured image
   - image availability
   - actionable error text when not ready
5. Capture app command line, database path, Docker host, image, browser configuration, and git revision used for the run.
6. Save reusable helper snippets for:
   - console error capture
   - failed request capture
   - HTMX swap waiting
   - browser-origin SSE parsing
   - screenshot-on-failure
   - persisted-state rechecks after navigation/reload

## Validation

Required evidence:
- All top-level operational pages load in the browser.
- Docker readiness is visible to an operator without using the shell.
- Browser controls can trigger at least one HTMX refresh.
- Any Docker or Playwright blocker stops the campaign.

## Exit Criteria

- `.internal-dev/reviews/docker-runtime-parity-validation/01-harness-evidence.md` exists.
- The file contains the exact app start command and environment facts later phases must reuse.
- If Docker or Playwright is blocked, later phases do not proceed.
