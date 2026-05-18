# Scope

Review-only pass for the completed operational UI contract refactor, focused on UI/HTMX/SimplyPages/browser readiness. Reviewed the requested guidance and phase artifacts:

- `.internal-dev/AGENTS.md`
- top-level `AGENTS.md`
- `.internal-dev/plans/operational-ui-contract-refactor/02-dashboard-information-architecture.md` through `07-validation-rollout.md`
- `.internal-dev/plans/operational-ui-contract-refactor/play_wright_tests.md`
- `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`
- `FrontendController`, `OrchestrationController`, orchestration static JS/CSS, and web/controller tests

No production code was edited.

# Findings

## BLOCKING-01 - Browser HTMX is still a noop stub, so HTMX-first pages cannot actually operate in-browser

The refactor is built around HTMX-driven page shells and partial endpoints, but the static asset currently served at the SimplyPages HTMX URL is a compatibility noop:

- `src/main/resources/static/webjars/htmx.org/dist/htmx.min.js:1` defines `window.htmx` with `version:"compat-noop"` and empty `process`/`onLoad` functions.
- `pom.xml:53-60` declares the real `htmx.org` WebJar and locator, but the checked-in static file still occupies the same URL path.
- The page shells rely on real HTMX behavior, for example `/dashboard` adds `hx-get="/dashboard/_stats"` and periodic `hx-trigger` refreshes at `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:211-214`.
- The phase 07 changelog claims the pages load and HTMX partial endpoints are valid, but explicitly records this exact risk: `.internal-dev/changelogs/2026-05-12-operational-ui-contract-refactor-phase-07-validation.md:38-42`.
- The separate "real HTMX WebJar" changelog says the noop route was removed and curl returned the real asset, but the static noop file still exists: `.internal-dev/changelogs/2026-05-12-real-htmx-webjar.md:6-13`.

Impact: All `hx-get`, `hx-post`, `hx-put`, `hx-delete`, `hx-trigger`, and `hx-swap` interactions can render correctly in source tests but fail to execute in an actual browser. This undercuts the main HTMX-first compliance claim.

## BLOCKING-02 - Agent detail tabs are visible controls but are not wired to HTMX or JavaScript

The Phase 06 Playwright target requires tab clicks to issue HTMX GETs to `/agents/_detail/{agentId}/{tab}`:

- Required behavior: `.internal-dev/plans/operational-ui-contract-refactor/play_wright_tests.md:841-856`.
- The detail layout renders `tabNav("dashboard", "queue", "inbox", "jobs", "workspace", "outputs", "history")`, then only the initial `#agent-tab-panel` has `hx-get="/agents/_detail/{agentId}/dashboard"` on load: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3261-3268`.
- `tabNav(...)` only emits `button` elements with `data-tab`, no `hx-get`, `hx-target`, or `hx-swap`: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3947-3956`.
- `agents.js` is intentionally a no-listener skeleton and states HTMX handles tab loading: `src/main/resources/static/js/orchestration/agents.js:1-8`.
- The test only checks the tab labels and the initial dashboard lazy-load container, not clickable tab wiring: `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:402-414`.

Impact: Queue, Inbox, Jobs, Workspace, Outputs, and History tabs appear clickable but do nothing. This is a visible page regression and a missed browser-readiness gate.

## HIGH-01 - Docker status is requested with HTMX from a JSON API and will render raw JSON in the agent dashboard

The agent dashboard loads Docker status into an HTML panel via HTMX:

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3335-3340` sets `hx-get="/api/runtime/docker/status"` with `hx-swap="innerHTML"`.
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java:13-28` is a `@RestController` endpoint returning `DockerStatusResponse` JSON.
- The Phase 06 browser target expects structured Docker fields and no raw JSON: `.internal-dev/plans/operational-ui-contract-refactor/play_wright_tests.md:825-839`.

Impact: once HTMX is real, the dashboard will swap a JSON object string into the panel instead of a styled status fragment. That violates the "no raw JSON" normal-UI contract and makes Docker health hard to scan.

## HIGH-02 - Job links target `/jobs/{jobId}`, but the page route was removed

The current web controller exposes `/jobs` plus HTMX fragments such as `/jobs/_editor/{jobId}`, but no GET page route for `/jobs/{jobId}`:

- Actual job UI routes include `/jobs`, `/jobs/_list`, `/jobs/_editor/{jobId}`, and related fragments: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2062-2150`.
- The only `/jobs/{jobId}` mapping in `OrchestrationController` is `@DeleteMapping("/jobs/{jobId}")`: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2223`.
- Dashboard Active Work rows link to `/jobs/{id}`: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:351-355`.
- Agent Jobs tab rows also link to `/jobs/{id}`: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3434-3440`.
- Handoff notes explicitly say the separate `/jobs/{jobId}` detail page endpoint was removed: `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md:282-287`.

Impact: visible dashboard and agent-page links can navigate to 404/405 instead of opening the job editor. This violates the route/page regression and "no 404 from visible controls" validation requirements.

## MEDIUM-01 - `/inbox` and `/outputs` remain JavaScript transport surfaces without HTMX partials

Phase 07 requires normal flows on `/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/agents`, `/inbox`, and `/outputs` to be HTMX-driven unless JS is explicitly justified: `.internal-dev/plans/operational-ui-contract-refactor/07-validation-rollout.md:48-51`.

Current implementation still uses JS as the primary transport for these pages:

- `/inbox` renders empty containers and loads `inbox.js`: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3036-3059`.
- `inbox.js` fetches `/api/agents`, `/api/users/inbox`, `/api/agents/{id}/inbox`, approval/respond endpoints, read/handled endpoints, and mutates `innerHTML`: `src/main/resources/static/js/orchestration/inbox.js:23-60`, `src/main/resources/static/js/orchestration/inbox.js:110-190`.
- `/outputs` exposes a `data-action="browse-outputs"` button and loads `outputs.js`: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3066-3086`.
- `outputs.js` fetches agents/jobs/projects/output results and renders cards with `innerHTML`: `src/main/resources/static/js/orchestration/outputs.js:10-18`, `src/main/resources/static/js/orchestration/outputs.js:21-80`, `src/main/resources/static/js/orchestration/outputs.js:86-114`.

Impact: these are not small client-only enhancements; they are full data-loading and action flows. This is a compliance gap against the accepted HTMX-first rollout contract.

## MEDIUM-02 - Browser validation evidence is not strong enough for the current risk surface

The phase 07 changelog claims all 10 operational pages load without console errors, all JS files comply, and `/chat` isolation is proven: `.internal-dev/changelogs/2026-05-12-operational-ui-contract-refactor-phase-07-validation.md:25-34`.

The inspected evidence does not cover the browser failures above:

- `play_wright_tests.md` is primarily an append-only target/checklist document, not a run log with route-by-route network/DOM proof: `.internal-dev/plans/operational-ui-contract-refactor/play_wright_tests.md:1-3`.
- Tests assert rendered source strings for HTMX containers and script isolation, but do not click controls or verify browser network behavior. Example: agent detail test checks labels and one initial `hx-get`, but not tab button HTMX wiring: `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:395-421`.
- The static HTMX noop, dead agent tabs, JSON Docker swap, and bad `/jobs/{id}` links are all browser-observable issues that source-only tests can miss.

Impact: the current validation record should not be treated as sufficient browser readiness evidence for rollout.

# Risk Assessment

Overall browser readiness risk is high. The implementation has a strong amount of server-rendered HTML and many HTMX endpoints, but browser execution is blocked or degraded by the HTMX asset issue and several visible control mismatches.

`/chat` isolation looks comparatively healthy from source inspection. `FrontendController` renders the chat root and chat client only at `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java:92-132`, while `FrontendControllerTest` verifies orchestration scripts are absent from `/chat` at `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java:57-65`.

Layout/text risk remains unproven rather than clearly failed. CSS has mobile breakpoints for major layouts, but dense tables such as `.dashboard-table` have no evident mobile overflow wrapper at `src/main/resources/static/css/orchestration.css:475-509`, so desktop/mobile screenshots are still needed before accepting the UI as browser-ready.

# Recommendations

1. Remove the checked-in noop HTMX static file or otherwise prove the real WebJar asset is the one served at `/webjars/htmx.org/dist/htmx.min.js`. Add a test or startup validation that checks the served asset content is not `compat-noop`.

2. Wire agent tab buttons directly with HTMX attributes in `tabNav` or a dedicated agent-tab renderer: each tab should have `hx-get="/agents/_detail/{agentId}/{tab}"`, `hx-target="#agent-tab-panel"`, and `hx-swap="innerHTML"`.

3. Replace the Docker status `hx-get` target with an HTML fragment endpoint, or render Docker status server-side in the agent dashboard. Keep `/api/runtime/docker/status` as JSON for API callers.

4. Repair job links. Either restore a GET `/jobs/{jobId}` route that preloads the job editor shell, or change visible links to open `/jobs` with an HTMX-loaded editor target that actually exists.

5. Convert `/inbox` and `/outputs` normal flows to HTMX fragments, or explicitly re-scope and document them as approved JS exceptions with a stronger justification than "would require backend endpoints." If kept as JS, browser validation should prove no dead endpoint or raw JSON regressions.

6. Re-run real browser validation after the fixes with evidence for desktop and mobile widths, click-through of all visible tabs/links/buttons, console output, network failures, and `/chat` isolation.

# Follow-ups

- Track the HTMX static asset contradiction between the real-WebJar changelog and the existing noop file.
- Add Playwright/MCP evidence for the Phase 07 checklist rather than only target definitions and source-string tests.
- Consider adding controller tests for visible route links, especially links emitted by dashboard and agent fragments.
- Consider adding a small test helper that fails if an HTMX panel points at a JSON API endpoint unless the target is explicitly designed to render JSON.
