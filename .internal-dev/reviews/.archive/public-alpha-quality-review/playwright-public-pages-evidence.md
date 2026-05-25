# Playwright Public Pages Evidence

## Agent

- Agent: validation-playwright-public-pages
- Agent id: `019e3723-63fd-72f0-9f75-bf96ce8ac6cb`
- Model / reasoning: GPT-5.5 Codex high
- Mode: browser-origin Playwright MCP, no repo edits

## Target

- URL: `http://localhost:18080`
- DB: `.internal-dev/test-fixtures/public-alpha-quality-review/playwright.db`

## Routes Covered

All returned HTTP 200 and rendered expected shell/content:

- `/`
- `/chat`
- `/dashboard`
- `/plans`
- `/workflows`
- `/jobs`
- `/projects`
- `/inbox`
- `/outputs`
- `/agents`
- `/agents/826bd773-38e0-4de1-8d34-0c9c3565ef25`
- `/settings`

## Probes Run

- Browser navigated each requested route.
- Browser-origin fetch status sweep for all routes and core APIs.
- Agent detail HTMX partials checked: dashboard, profile, queue, inbox, jobs, schedules, reactions, workspace, outputs, exec, history, submit. All returned 200.
- Mobile viewport probe at `390x780`.
- Safe persistence mutation through HTMX plan editor endpoints.
- DB read-back from isolated SQLite.

## Mutation and Persistence Evidence

Created and updated plan via browser origin:

- `POST /plans/_editor/_draft` -> 200
- `PUT /plans/_editor/ae9134d5-ddca-4cf8-bfff-a3820d94e060` -> 200
- `GET /api/plans/ae9134d5-ddca-4cf8-bfff-a3820d94e060` -> title persisted as `PW Public Alpha 2026-05-17T18-13-53-587Z`
- `/plans` UI displayed the same plan title.

SQLite evidence captured by the agent:

```text
ae9134d5-ddca-4cf8-bfff-a3820d94e060|PW Public Alpha 2026-05-17T18-13-53-587Z|Prove safe mutation persists|DRAFT|local-qwen|local-qwen
826bd773-38e0-4de1-8d34-0c9c3565ef25|magenta|ACTIVE|local-qwen
```

## Console and Network

- Unexpected 500s: none observed.
- Static asset failures: none observed.
- `/webjars/htmx.org/dist/htmx.min.js` loaded 200.
- One 400 was observed from an intentionally malformed first `POST /api/plans` probe using the wrong JSON shape; supported HTMX editor path succeeded immediately after.
- Raw console/network summary is retained in `playwright-console-network-log.md`.

## Findings

- High: mobile orchestration shell is effectively unusable at phone width. At `390x780`, `/agents/{agentId}` had `#content-area` width `70px`, `.content-wrapper` width `100px`, grid template `250px 100px`, and no horizontal overflow escape. The sidebar remains in layout instead of becoming overlay/content-only. File evidence points to Magenta enabling the collapsible sidebar in `OrchestrationController` while SimplyPages framework specificity makes `.main-container.has-sidebar` override the mobile one-column grid.

## Explicitly Ruled Out

- Requested public routes are not blank or 404ing.
- HTMX is present on dashboard shell pages.
- Agent detail route and checked tab partials return 200.
- No missing HTMX WebJar regression in this run.
- Persisted plan state is visible through UI, API, and SQLite.
