# Phase 01: Playwright Harness And Docker Preflight

## Context

The previous failure mode was deferring Docker and replacing production-like validation with narrower tests. This phase establishes the validation harness and proves the browser can observe Docker readiness before any feature-specific testing starts.

## Goal

Start Magenta with Docker enabled, open it through Playwright, verify the operational UI loads, verify Docker runtime status is available from the UI, and prepare reusable Playwright helpers for the remaining agents.

## In Scope

- Running the app on an isolated SQLite database.
- Playwright browser setup against `http://localhost:18080`.
- Runtime/Docker status verification through UI surfaces.
- Console/network capture helpers.
- Shared fixture naming conventions.
- Hard stop on Docker or Playwright blockers.

## Out of Scope

- Feature validation beyond readiness probes.
- Curl-only checks as acceptance evidence.
- Production code changes.

## Implementation Steps

1. Start the app with Docker enabled and `python:3.11`.
2. Open `/`, `/dashboard` if present, `/agents`, `/tasks`, `/workflows`, `/jobs`, `/projects`, `/inbox`, `/outputs`, `/chat` through Playwright.
3. Verify the real HTMX asset loads and no `compat-noop` stub is active.
4. Verify Docker status is visible in the operational UI and distinguishes ready, disabled, unavailable, or image-missing states.
5. Create shared Playwright helper snippets for:
   - collecting console errors
   - collecting failed network requests
   - waiting for HTMX swaps
   - parsing browser-origin SSE
   - saving screenshots on failure
6. Record environment:
   - app port
   - database path
   - Docker host shown by app/UI
   - agent image
   - Playwright browser/runtime details

## Validation

Required Playwright checks:
- Browser can load every top-level operational page.
- No page shows a raw JSON fragment in place of HTML.
- Docker runtime status can be refreshed from the UI.
- At least one runtime status panel shows the configured image `python:3.11`.
- HTMX-triggered refresh controls fire network requests and update DOM.

## Exit Criteria

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/01-harness-and-docker-preflight-evidence.md` exists.
- If Docker or Playwright is blocked, the evidence file names the exact blocker and later phases do not run.
- If the harness passes, later agents can reuse the documented app URL, browser setup, and helper snippets.
