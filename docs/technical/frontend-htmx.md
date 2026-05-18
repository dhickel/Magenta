# Frontend HTMX

Magenta's frontend is server-rendered and HTMX-first. SimplyPages components/modules should be the default way to build reusable UI. Raw HTML strings are a fallback for advanced cases only after checking the SimplyPages docs and demos.

Source anchors:

- Web controllers/fragments: [`api/web`](../../src/main/java/io/mindspice/magenta2/api/web)
- Main CSS: [`magenta.css`](../../src/main/resources/static/css/magenta.css)
- Orchestration CSS: [`orchestration.css`](../../src/main/resources/static/css/orchestration.css)
- Chat client JS: [`chat-client.js`](../../src/main/resources/static/js/chat-client.js)
- Alpha security JS: [`alpha-security.js`](../../src/main/resources/static/js/alpha-security.js)
- Orchestration JS islands: [`static/js/orchestration`](../../src/main/resources/static/js/orchestration)

## Page and Fragment Controllers

`FrontendController` owns the basic shell routes such as `/` and `/chat`.

`FrontendFragmentController` owns small chat fragments:

- `/api/fragments/chat/transcript`
- `/api/fragments/chat/sessions`
- `/api/fragments/chat/planning`

`OrchestrationController` owns the operational UI routes and HTMX fragments for dashboard, plans, workflows, jobs, projects, inbox, outputs, agents, and settings.

JSON API controllers provide the route families consumed by both HTMX fragments and JavaScript islands.

## HTMX Default Policy

Use HTMX for:

- CRUD form submissions.
- List/detail refreshes.
- Tab and panel swaps.
- Filtering and row actions.
- Modal body refreshes.
- Validation fragments.
- Submit-to-agent forms.
- Delete/confirm actions.

HTMX routes should return server-rendered HTML and preserve alpha security behavior. Failed unsafe requests from HTMX receive a small HTML auth error plus `HX-Trigger: magenta:security-error` from `AlphaSecurityConfiguration`.

## JavaScript Islands

Existing JavaScript is justified where persistent browser state, SSE, or client-side graph/editor interaction is simpler than pure HTMX.

- `chat-client.js`: chat SSE, active stream state, incremental token rendering, interruption, and chat-specific browser behavior.
- `alpha-security.js`: reads the CSRF cookie and applies the `X-XSRF-TOKEN` header to unsafe browser requests; handles alpha security events.
- `orchestration/api.js` and `dom.js`: small shared helpers for operational islands.
- `orchestration/dashboard.js`: dashboard refresh/poll style behavior.
- `orchestration/agent-chat.js`: agent side-panel SSE chat behavior.
- `orchestration/agents.js`: agent detail/editor behaviors that need local UI state.
- `orchestration/plans.js`: richer plan/workflow editor interactions where client-side state simplifies repeated node/field operations.
- `orchestration/projects.js`: project detail interactions that coordinate fragment updates.

Do not turn ordinary CRUD into a JavaScript transport surface. If an interaction can be a form plus HTMX target/swap cleanly, keep it HTMX.

## SimplyPages Reuse

When adding or changing UI:

- Prefer reusable components/modules over page-local markup.
- Use shared render structures and slot keys when the same structure renders request-specific data.
- Keep controllers thin; rendering helpers/components should assemble view state, while services own domain behavior.
- Follow existing orchestration page patterns before introducing new markup style.

If SimplyPages lacks a reasonable primitive, inspect `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs` and `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo`. If the library truly has a bug or missing primitive, document or fix the library rather than adding brittle page-level workarounds.

## Static Assets

CSS is split by surface:

- `magenta.css` for shared shell/chat styling.
- `orchestration.css` for operational UI styling.

Static JavaScript is loaded as browser assets from `src/main/resources/static/js`. Keep modules narrowly scoped and avoid hidden global behavior except where an existing island already establishes a small shared helper.

## Entity Selectors

Concurrent selector work lives under [`api/web/selector`](../../src/main/java/io/mindspice/magenta2/api/web/selector). Selector routes are read-only GET endpoints under `/selectors/{kind}` and should be treated as reusable UI infrastructure for choosing plans, tasks, workflows, jobs, agents, projects, workspaces, and other operational entities.

When selector behavior is reused across pages, prefer the shared selector components/configuration rather than duplicating lookup UI in each page.

## Validation Expectations

For UI changes, validate against a running app with focused Playwright checks on the changed target. For documentation-only work, safe validation is limited to link/file inventory, source-reference checks, and whitespace checks.

During UI review, explicitly check that JavaScript use is justified and that standard CRUD remains HTMX-first.
