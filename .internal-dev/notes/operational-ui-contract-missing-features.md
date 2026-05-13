# Operational UI Contract Missing-Functionality Audit

**Audit date:** 2026-05-12
**Scope:** Read-only audit of all rendered UI surfaces, API/controller routes, JS/CSS assets, and review artifacts.
**Author:** Subagent F

---

## Summary

- Total findings: 22
- Severity breakdown:
  - `alpha blocker`: 7 (4 already owned by other subagents, 3 new)
  - `alpha should-fix`: 8
  - `post-alpha deferred`: 4
  - `needs user decision`: 3

---

## alpha blocker findings

### F-BLOCKER-01: Plan editor field rows emit hx-put to routes that do not exist ~~ ALREADY OWNED

- **Surface:** Plans
- **Missing behavior:** `PlanFieldDefinition` rows in the plan editor render `hx-put="/plans/_editor/{planId}/inputs"` and `hx-put="/plans/_editor/{planId}/outputs"` on change. No `@PutMapping` exists for these routes -- only POST (add) and DELETE (remove). Inline edits to field name, type, required, array, and description silently fail with HTTP 405.
- **User impact:** Users see editable input/output field rows in the structured editor. Editing them and tabbing away triggers a request that fails. There is no visible error feedback because HTMX error handling is not configured for these 405 responses.
- **Evidence:**
  - `OrchestrationController.java:1218-1223` -- renders `hx-put="/plans/_editor/" + planId + "/" + kind + "s"` for each field row
  - `OrchestrationController.java:632-656` -- only `@PostMapping` and `@DeleteMapping` exist for `/plans/_editor/{planId}/{inputs|outputs}`
- **Why out of scope for this subagent:** This subagent is read-only; this is a controller route gap requiring new PUT endpoints.
- **Consolidated from:** beta-readiness-review BLOCKER-02.
- **Recommended owner/next action:** Subagent addressing plan persistence; add `@PutMapping("/plans/_editor/{planId}/inputs")` and equivalent for outputs, or remove hx-put from field rows until persistence path is complete.
- **Severity:** alpha blocker

### F-BLOCKER-02: Agent detail tabs render as inert buttons with no HTMX wiring ~~ ALREADY OWNED

- **Surface:** Agents / Agent detail
- **Missing behavior:** `tabNav("dashboard", "queue", "inbox", "jobs", "workspace", "outputs", "history")` emits `<button data-tab="X">` elements with no `hx-get`, `hx-target`, or `hx-swap`. Only the initial page load triggers `hx-get` for the dashboard tab. Clicking any other tab does nothing.
- **User impact:** Six of seven tabs on the agent detail page appear clickable but are dead. Users cannot navigate agent queue, inbox, jobs, workspace, outputs, or history from the tab bar.
- **Evidence:**
  - `OrchestrationController.java:3264-3268` -- initial load only for dashboard tab
  - `OrchestrationController.java:3947-3958` -- `tabNav` emits bare buttons with `data-tab`
  - `agents.js:1-9` -- skeleton acknowledges HTMX should handle tab loading
  - Tab GET endpoints exist at `OrchestrationController.java:3380-3514`
- **Why out of scope for this subagent:** Read-only; requires wiring `tabNav` to emit HTMX attributes or writing JS tab loader.
- **Consolidated from:** ui-htmx-review BLOCKING-02.
- **Recommended owner/next action:** Wire `tabNav` to emit `hx-get="/agents/_detail/{agentId}/{tab}"`, `hx-target="#agent-tab-panel"`, and `hx-swap="innerHTML"` for each tab button.
- **Severity:** alpha blocker

### F-BLOCKER-03: Docker status panel loads raw JSON into HTML via HTMX ~~ ALREADY OWNED

- **Surface:** Agent dashboard tab
- **Missing behavior:** `RuntimeController.dockerStatus()` returns `DockerStatusResponse` JSON. The agent dashboard swaps this JSON string into an HTML panel via `hx-swap="innerHTML"`.
- **User impact:** The agent dashboard will display raw JSON like `{"enabled":true,"available":false,...}` instead of a styled status fragment. This violates the "no raw JSON in normal UI" contract.
- **Evidence:**
  - `OrchestrationController.java:3336-3340` -- `hx-get="/api/runtime/docker/status"` with `hx-swap="innerHTML"`
  - `RuntimeController.java:28-47` -- returns `DockerStatusResponse` JSON from `@RestController`
- **Why out of scope for this subagent:** Read-only; requires a Docker status HTML fragment endpoint.
- **Consolidated from:** ui-htmx-review HIGH-01.
- **Recommended owner/next action:** Add `@GetMapping("/dashboard/_docker-status")` in OrchestrationController returning HTML, and change the hx-get target.
- **Severity:** alpha blocker

### F-BLOCKER-04: Job links target `/jobs/{jobId}` which is a delete-only route ~~ ALREADY OWNED

- **Surface:** Dashboard, Agent Jobs tab
- **Missing behavior:** Multiple rendered links point to `/jobs/{jobId}`. The only mapped route is `@DeleteMapping("/jobs/{jobId}")` which requires DELETE method. GET requests to `/jobs/{jobId}` return 405 Method Not Allowed.
- **User impact:** Clicking "Active Work" job links on the dashboard or any job link in the agent Jobs tab navigates to a 405 error page.
- **Evidence:**
  - `OrchestrationController.java:354` -- Dashboard active work links `/jobs/{id}`
  - `OrchestrationController.java:3439` -- Agent Jobs tab links `/jobs/{id}`
  - `OrchestrationController.java:2223` -- only `@DeleteMapping("/jobs/{jobId}")` exists
- **Why out of scope for this subagent:** Read-only; requires restoring a GET route or changing visible links to use HTMX fragment targets.
- **Consolidated from:** ui-htmx-review HIGH-02.
- **Recommended owner/next action:** Either add `@GetMapping("/jobs/{jobId}")` that renders the jobs shell with editor preloaded, or change all visible links to `/jobs` with HTMX preload of editor fragment.
- **Severity:** alpha blocker

### F-BLOCKER-05: HTMX static asset is a compat-noop stub, making all HTMX interactions inert in browser ~~ ALREADY OWNED

- **Surface:** All operational pages
- **Missing behavior:** `src/main/resources/static/webjars/htmx.org/dist/htmx.min.js` contains `window.htmx={version:"compat-noop",process:function(){},onLoad:function(){}}`. The real `htmx.org` WebJar 1.9.10 is declared in `pom.xml` but the static noop file shadows it. No `hx-get`, `hx-post`, `hx-put`, `hx-delete`, or `hx-trigger` fires in an actual browser.
- **User impact:** All HTMX-driven interactions (dashboard refreshes, plan/workflow/job/project editing, agent detail loading, tabs, save buttons, submit-to-agent forms) render as static HTML and do nothing.
- **Evidence:**
  - `static/webjars/htmx.org/dist/htmx.min.js` -- one line, 91 bytes, compat-noop
  - `pom.xml:53-60` -- real htmx WebJar configured
- **Why out of scope for this subagent:** Read-only; requires removing the static compat-noop file or otherwise proving the real WebJar asset is served.
- **Consolidated from:** ui-htmx-review BLOCKING-01.
- **Recommended owner/next action:** Delete `src/main/resources/static/webjars/htmx.org/dist/htmx.min.js` and verify the WebJar resource locator serves the real asset. Add a test asserting the served asset is not `compat-noop`.
- **Severity:** alpha blocker

### F-BLOCKER-06: Plan editor Save preserves database state for deliverables/inputs/outputs/steps/validation/assumptions, discarding user edits

- **Surface:** Plans
- **Missing behavior:** `PUT /plans/_editor/{planId}` (`updatePlanEditor`) constructs the updated `PlanDefinition` using `current.deliverables()`, `current.inputs()`, `current.outputs()`, `current.assumptions()`, `current.steps()`, and `current.validationCriteria()` -- the pre-edit database state. The form renders editable text fields for list items and field rows, but Save does not read any of the form-submitted values for these sections. Only scalar fields (title, summary, goal, notes, worktype, models) are read from `params`.
- **User impact:** A user types "Write a deployment script" as a deliverable, adds steps, and clicks Save. The save succeeds but the edited list text is silently discarded. The user sees their edits still visible (because the editor re-renders the same form with database state) until they navigate away and return.
- **Evidence:**
  - `OrchestrationController.java:586-609` -- `current.deliverables()`, `current.inputs()`, `current.outputs()`, `current.assumptions()`, `current.steps()`, `current.validationCriteria()` used directly
  - `OrchestrationController.java:1291-1314` -- `listSection` renders text inputs with no `name` attribute; even if they had names, Save does not read them
- **Why out of scope for this subagent:** Read-only; requires rewriting `updatePlanEditor` to read list/form data.
- **Consolidated from:** This expands on beta-readiness-review BLOCKER-02, which focused on route gaps. This finding shows even scalar Save has the same persistence gap for complex sections.
- **Recommended owner/next action:** Either (1) rewrite Save to read and persist all edited sections from form data, or (2) remove edit controls for sections until persistence is implemented.
- **Severity:** alpha blocker

### F-BLOCKER-07: JOB_RUN submit-to-agent from agent detail page bypasses the legacy bridge, causing validation failures for canonical jobs ~~ ALREADY OWNED by Subagent A

- **Surface:** Agent Submit Work panel
- **Missing behavior:** `/agents/_submit/{agentId}` creates a `JOB_RUN` assignment directly via `assignmentService.create()` without calling `ensureLegacyJob()`. The job detail submit path (`/jobs/_submit/{jobId}`) does call `ensureLegacyJob()`. For canonical `JobDefinition` rows, `AssignmentService.create` tries to validate `jobId` via legacy `OrchestrationJobService.get()`, which fails with a not-found error.
- **User impact:** Users can submit canonical jobs from the job detail page but not from the agent detail page. This is inconsistent and confusing.
- **Evidence:**
  - `OrchestrationController.java:3805-3832` -- agent submit: no `ensureLegacyJob()`
  - `OrchestrationController.java:2342-2366` -- job submit: calls `ensureLegacyJob(job)` first
  - `AssignmentService.java:44-45` -- validates jobId via legacy service
- **Why out of scope for this subagent:** Already assigned to Subagent A (canonical job runtime refactor). This finding is flagged for the orchestrator.
- **Consolidated from:** backend-contract-review Finding 1.
- **Recommended owner/next action:** Subagent A should ensure both submit paths use the same bridge or, preferably, retire the legacy validation.
- **Severity:** alpha blocker

---

## alpha should-fix findings

### F-SHOULDFIX-01: Plan/job/project editor model dropdowns are empty, showing only "Default" option

- **Surface:** Plans, Jobs, Projects
- **Missing behavior:** `modelSelect()` creates `<select name="X"><option value="">Default</option></select>`. No JS loads actual model names into these selects. The only JS model-population logic is in `dashboard.js initSettings` which targets the `/settings` page. Plan/job/project editors are HTMX fragments with no accompanying JS, so their model dropdowns remain empty.
- **User impact:** Users editing plans, jobs, or projects see "Default" as the only model option. They cannot select from available models. If they accidentally change the dropdown away from Default, they cannot return to it.
- **Evidence:**
  - `OrchestrationController.java:3939-3941` -- `modelSelect()` produces single-option select
  - `OrchestrationController.java:1139-1142` -- used in plan editor
  - `OrchestrationController.java:2502` -- used in job editor
  - `OrchestrationController.java:2919` -- used in project editor
  - `dashboard.js:60-66` -- only populates settings page model selects
- **Why out of scope for this subagent:** Requires either server-side model loading in `modelSelect()` or JS in each editor fragment.
- **Recommended owner/next action:** Inject `ChatService` (or a model catalog bean) and populate model selects server-side. Use `chatService.availableModels()` which is already used in `FrontendController.modelSelect()`.
- **Severity:** alpha should-fix

### F-SHOULDFIX-02: `/inbox` and `/outputs` pages remain primary-JS-transport surfaces contrary to HTMX-first contract

- **Surface:** Inbox, Outputs
- **Missing behavior:** Both pages render empty container HTML and delegate all data loading and interaction to JS (`inbox.js`, `outputs.js`). These are not small client-only enhancements -- they are full data-loading and action flows (agent selectors, inbox messages with approve/reject/mark-read/handled buttons, output browsing with multiple filters, results rendering). The HTMX-first contract requires these normal flows to use HTMX unless JS is explicitly justified.
- **User impact:** If the HTMX noop is resolved but inbox/outputs JS fails, both pages appear empty with no fallback. The JS approach also means there is no zero-JS or deep-link fallback. The inbox page uses `innerHTML` string templates vulnerable to XSS if any message data is not properly sanitized.
- **Evidence:**
  - `OrchestrationController.java:3036-3060` -- inbox renders empty containers
  - `OrchestrationController.java:3066-3088` -- outputs renders empty containers and a `data-action="browse-outputs"` button
  - `inbox.js:10-192` -- full JS data-loading and action flow
  - `outputs.js:10-115` -- full JS data-loading and rendering flow
  - `OrchestrationControllerTest.java` -- verifies page shell and JS presence, not HTMX wiring
- **Why out of scope for this subagent:** Read-only; requires a significant refactor or explicit scope exception.
- **Consolidated from:** ui-htmx-review MEDIUM-01.
- **Recommended owner/next action:** Either (1) convert to HTMX fragments with server-rendered lists and inline approve/reject/browse actions, or (2) formally document as an accepted JS exception with explicit justification and add an XSS audit of `inboxMessageHtml()` and `renderOutputs()`.
- **Severity:** alpha should-fix

### F-SHOULDFIX-03: Settings page model dropdowns populated by JS and Save requires JS fetch -- no HTMX fallback

- **Surface:** Settings
- **Missing behavior:** The settings form renders model selects with ONLY the current values. `dashboard.js initSettings()` fetches `/api/settings/runtime` and populates the selects, then wires a JS click listener for save. If JS is unavailable or fails, the model dropdowns show one option, and the Save button does nothing. The `data-action="save-settings"` button has no HTMX attributes.
- **User impact:** Settings page is non-functional without JavaScript. This is the ONLY operational page where Save is JS-only rather than HTMX.
- **Evidence:**
  - `OrchestrationController.java:3882-3920` -- settings form with `data-action="save-settings"` button (no HTMX)
  - `dashboard.js:55-94` -- JS populates selects and wires save
- **Why out of scope for this subagent:** Read-only; requires HTMX-based save endpoint.
- **Recommended owner/next action:** Add `PUT /settings` HTMX form handler, populate model selects server-side, or accept settings as a JS transport surface with explicit documentation.
- **Severity:** alpha should-fix

### F-SHOULDFIX-04: Dashboard "Recent Events" section permanently shows "No recent events"

- **Surface:** Dashboard
- **Missing behavior:** `dashboardHxSideSection("Recent Events", "side-events", null, null)` passes `null` for both the full-page link and the HTMX endpoint. The section is hardcoded to display "No recent events." There is no event aggregation endpoint.
- **User impact:** Dashboard has a permanent dead panel. Users cannot tell if there are recent events because the panel never loads data.
- **Evidence:**
  - `OrchestrationController.java:258-259` -- `null, null` for href and hxEndpoint
  - `OrchestrationController.java:287-289` -- `dashboardHxSideSection` renders hardcoded "No recent events" when `hxEndpoint` is null
- **Why out of scope for this subagent:** Requires building an event aggregation endpoint or removing the panel.
- **Recommended owner/next action:** Add `/dashboard/_side-events` endpoint that aggregates recent events across jobs, projects, workflows, and runs; wire the hx-get. Or remove the panel until an event feed is implemented.
- **Severity:** alpha should-fix

### F-SHOULDFIX-05: Inbox JS references field names that do not match either InboxMessage type

- **Surface:** Inbox
- **Missing behavior:** `inbox.js` references `msg.fromAgentId`, `msg.responded`, and `msg.handled` (as direct booleans). Neither `runtime.InboxMessage` nor `workflow.InboxMessage` has these exact fields.
  - `runtime.InboxMessage` has `fromId` (not `fromAgentId`), `read`/`handled` (booleans; no `responded`)
  - `workflow.InboxMessage` has `fromId` (not `fromAgentId`), `respondedAt`/`handledAt` (timestamps; no `responded`/`handled`)
  - `inbox.js:65` checks: `msg.responded || msg.respondedAt || msg.handled` -- this partially works (workflow messages have `respondedAt`, runtime messages have `handled`) but the logic is fragile and the "from" field is always empty
- **User impact:** The "From:" field on inbox message cards shows "system" because `msg.fromAgentId` is always undefined and falls back to `msg.fromId || "system"`. Message state detection (responded/handled) may behave differently for user inbox vs agent inbox messages.
- **Evidence:**
  - `inbox.js:65-87` -- `inboxMessageHtml()` references mismatched fields
  - `runtime.InboxMessage.java:8-19` -- record definition
  - `workflow.InboxMessage.java:25-39` -- record definition
- **Why out of scope for this subagent:** Requires JS fix or server-side field mapping.
- **Recommended owner/next action:** Fix inbox.js to use the correct field names for each InboxMessage type. Consider adding a unified InboxMessage DTO or dedicated HTML fragment endpoints.
- **Severity:** alpha should-fix

### F-SHOULDFIX-06: Agent workspace tab shows hardcoded API reference instead of workspace data

- **Surface:** Agent detail / Workspace tab
- **Missing behavior:** The workspace tab renders "Workspace details available via the API." with a `GET /api/agents/{agentId}/workspace` code reference. The API endpoint `/api/agents/{agentId}/workspace` exists and returns `Workspace` data, but the tab does not fetch or display it.
- **User impact:** The workspace tab is a nonfunctional placeholder. Users expecting to see workspace files, links, or status see only documentation text.
- **Evidence:**
  - `OrchestrationController.java:3450-3466` -- hardcoded text referencing API endpoint
  - `AgentProfileController.java:85-89` -- works, returns `Workspace` object
- **Why out of scope for this subagent:** Requires server-side workspace rendering or JS fetch + display.
- **Recommended owner/next action:** Either render workspace data server-side in the tab fragment (preferred, HTMX-first) or add JS to fetch and display workspace data.
- **Severity:** alpha should-fix

### F-SHOULDFIX-07: Agent outputs tab ignores the agent filter, showing all recent outputs

- **Surface:** Agent detail / Outputs tab
- **Missing behavior:** The outputs tab calls `outputArtifactService.query(null, null, null, 20)` -- no agent filter. The tab displays the 20 most recent outputs globally, not outputs associated with the agent. The code comment acknowledges this: "Agent-specific outputs are tracked through job/assignment/run relationships. Show the global recent outputs list; agent filtering will improve when RunOutputArtifact carries agent identity metadata."
- **User impact:** Agent outputs tab plausibly shows data but is misleading -- outputs from other agents are shown without indication.
- **Evidence:**
  - `OrchestrationController.java:3477` -- `query(null, null, null, 20)` -- no agent filter
  - `OrchestrationController.java:3474-3476` -- explicit code comment acknowledging the gap
- **Why out of scope for this subagent:** Requires output-to-agent tracking enhancement or filtered query.
- **Recommended owner/next action:** Either filter outputs via job ownership (jobs have `ownerAgentId`), or add agent identity to `RunOutputArtifact`.
- **Severity:** alpha should-fix

### F-SHOULDFIX-08: No schedule or event reaction UI in the operational dashboard

- **Surface:** Agents, Dashboard
- **Missing behavior:** `AgentOrchestrationController` exposes `/api/agents/{agentId}/schedules` and `/api/agents/{agentId}/event-reactions` with create/list endpoints. The operational UI (OrchestrationController) has NO routes, fragments, or tabs for schedules or event reactions. Both features are feature-flagged (`magenta.features.schedules-enabled`, `magenta.features.reactions-enabled`) and default to `false`, so their absence is not visible to users yet.
- **User impact:** When feature flags are enabled, users see API-only features with no UI. There is no plan for schedules or event-reaction UI in the operational interface.
- **Evidence:**
  - `AgentOrchestrationController.java:143-181` -- full schedule and reaction API
  - `OrchestrationController.java` -- zero references to schedules or reactions
  - `application.yml` -- feature flags default to false
- **Why out of scope for this subagent:** This is a scoping question -- the features exist in the API layer but have no UI plan.
- **Recommended owner/next action:** Either add agent detail tabs for schedules and reactions, or explicitly scope them as API-only features and document the split. Remove the feature-flag scaffolding if they are not planned for alpha.
- **Severity:** alpha should-fix

---

## post-alpha deferred findings

### F-DEFERRED-01: Plan editor list items render without `name` attributes, making form data inaccessible even if persistence were implemented

- **Surface:** Plans
- **Missing behavior:** `listSection()` renders `TextInput.create("")` with empty name for deliverable, step, validation, and assumption items. The text fields have `value` attributes populated but no form field names. Even if the Save endpoint tried to read these values from `@RequestParam`, they would not appear because HTML forms do not submit unnamed inputs.
- **User impact:** This is a secondary blocker behind F-BLOCKER-06 (Save doesn't read complex sections). Even if Save were fixed to read list data, the data would not arrive because inputs lack names.
- **Evidence:**
  - `OrchestrationController.java:1301-1303` -- `TextInput.create("")` with no name
- **Why out of scope for this subagent:** Read-only; requires field naming convention and form data parsing.
- **Recommended owner/next action:** Add unique `name` attributes (e.g., `name="deliverables[{i}]"`) when persistence path is implemented.
- **Severity:** post-alpha deferred (dependent on F-BLOCKER-06 resolution)

### F-DEFERRED-02: Dashboard stat aggregation computes workflow counts as hardcoded zero

- **Surface:** Dashboard API
- **Missing behavior:** `DashboardController.SystemStats` hardcodes `0, 0` for `runningWorkflows` and `pendingAssignments`. These fields are returned in the API response but always zero regardless of actual workflow/assignment activity.
- **User impact:** Dashboard summary API always reports zero running workflows and pending assignments. Downstream consumers relying on these fields get no data.
- **Evidence:**
  - `DashboardController.java:71-72` -- `0, 0` hardcoded
- **Why out of scope for this subagent:** Requires querying workflow run and assignment state, which depends on Subagent C's workflow validation work.
- **Recommended owner/next action:** Wire real workflow run and assignment counts after workflow validation and runtime are stabilized.
- **Severity:** post-alpha deferred

### F-DEFERRED-03: No `.dashboard-table` overflow/wrap handling for mobile widths

- **Surface:** All pages using `.dashboard-table`
- **Missing behavior:** The CSS has responsive breakpoints (max-width: 900px) for dashboard layout, status strip, chat band, and card grid, but `.dashboard-table` tables have no `overflow-x: auto` wrapper, no `white-space: nowrap` override, and no mobile-specific column hiding. Dense tables with 5+ columns (agents list, dashboard active work, agent queue, inbox, etc.) will overflow horizontally on narrow screens.
- **User impact:** On mobile devices or narrow browser windows, table content extends beyond the viewport without scroll indication. Users cannot see the rightmost columns.
- **Evidence:**
  - `orchestration.css:475-509` -- table styles, no overflow handling
  - `orchestration.css:554-569` -- `@media (max-width: 900px)` has no table rules
- **Why out of scope for this subagent:** CSS-only fix, low severity for alpha where desktop use is primary.
- **Recommended owner/next action:** Add `overflow-x: auto` to `.dashboard-table` or its container, and consider `@media` rules for column priority/hiding.
- **Severity:** post-alpha deferred

### F-DEFERRED-04: Stale `app.js` remains in static assets with incompatible routes

- **Surface:** Static assets
- **Missing behavior:** `src/main/resources/static/js/orchestration/app.js` contains old JS-first agent/job flows. It references `/api/tasks` (legacy endpoint), references old DOM patterns (`[data-orchestration-page='jobs']` vs current `[data-orchestration-page='jobs']` -- note: the tag IS "jobs" in current code), and posts stale save payloads to `/api/jobs`. The file is not referenced by any current page shell, but remains publicly accessible.
- **User impact:** If any external or cached reference loads this file, it would attempt to execute old flows against current API endpoints, potentially causing confusing errors or state corruption. More likely, it is noise in browser debug tools.
- **Evidence:**
  - `app.js:5` -- `ORCHESTRATION_ENDPOINTS` references old patterns
  - `app.js:251-252` -- stale `/api/jobs` save payload
  - No page shell references `app.js`
- **Why out of scope for this subagent:** Read-only; requires delete or quarantine decision.
- **Consolidated from:** beta-readiness-review MEDIUM.
- **Recommended owner/next action:** Delete `app.js` or move it to a quarantine directory. Add a test that verifies it is not referenced by any page shell.
- **Severity:** post-alpha deferred

---

## needs user decision findings

### F-DECISION-01: OrchestrationController is a ~4000-line monolith with 12 dependencies mixing rendering, domain mutations, and bridging

- **Surface:** Architecture / maintainability
- **Missing behavior:** The controller violates the web package guide's "controllers should stay thin and delegate behavior to services" rule. It contains domain mutation logic (`addField`, `removeField`, `addListItem`, `jobItemFromParams`), legacy bridge code (`ensureLegacyJob`), and cross-domain read aggregation (`agentDashboardTab`, `dashboardStats`). Many handlers catch broad `Exception` and return HTML 200 responses with error messages, bypassing `GlobalExceptionHandler`.
- **User impact:** This is a maintainability and correctness risk. Error responses from HTMX endpoints return 200 OK with error text, which means HTMX treats them as successful swaps and does not trigger error handling. Silent data-loss scenarios are easier to introduce.
- **Evidence:**
  - `OrchestrationController.java` -- 4003 lines, 12 injected dependencies
  - `OrchestrationController.java:625,688,689,844,919,918,1531,1572,1594,2379` -- broad `catch (Exception e)` returning HTML 200
  - Web package AGENTS.md: "Keep controllers thin and delegate behavior to services."
- **Why out of scope for this subagent:** This is an architecture decision about when/if to split the controller. This subagent cannot make that decision.
- **Consolidated from:** beta-readiness-review HIGH.
- **Recommended owner/next action:** User decides scope: split now before alpha, split after alpha, or accept as technical debt with explicit tracking bug.
- **Severity:** needs user decision

### F-DECISION-02: Two InboxMessage types with different shapes create a confusing dual inbox contract

- **Surface:** Inbox
- **Missing behavior:** Two `InboxMessage` records exist: `runtime.InboxMessage` (used by agent inbox API) and `workflow.InboxMessage` (used by user inbox API). They have different fields (`read`/`handled` vs `respondedAt`/`handledAt`, `metadata` Map vs `metadataJson` String). The UI (`inbox.js`) references an inconsistent union of both types' fields. This split creates a confusing contract for anyone consuming inbox APIs.
- **User impact:** Currently causes F-SHOULDFIX-05 (broken "From" field). Future API consumers will struggle with the split schema.
- **Evidence:**
  - `runtime.InboxMessage.java` -- 5 fields
  - `workflow.InboxMessage.java` -- 12 fields
  - `inbox.js:64-89` -- references a union of fields from both types
- **Why out of scope for this subagent:** Requires architectural decision about whether to unify or accept the dual format.
- **Recommended owner/next action:** User decides: unify on one InboxMessage type, or accept the split with documented API differences.
- **Severity:** needs user decision

### F-DECISION-03: Chat from the operational dashboard exposes a chat input that says "System-level chat coming in a future update"

- **Surface:** Dashboard
- **Missing behavior:** `dashboardChatBand()` renders a text input and disabled Send button labeled "System-level chat coming in a future update." The input is not wired to any endpoint. The button has `disabled` attribute.
- **User impact:** Users see a chat-like prompt on the dashboard but cannot use it. The placeholder text is honest but the disabled control creates a dead UI element on the main page.
- **Evidence:**
  - `OrchestrationController.java:196-204` -- disabled chat input on dashboard
- **Why out of scope for this subagent:** User needs to decide whether to ship the disabled placeholder, remove it, or implement dashboard chat.
- **Recommended owner/next action:** User decides: remove the disabled chat band, leave it with placeholder text, or implement dashboard chat.
- **Severity:** needs user decision

---

## Findings overlapping with Subagents A, C, E

| Finding | Overlapping Subagent | Overlap Type |
|---------|---------------------|--------------|
| F-BLOCKER-07 | Subagent A (canonical job runtime) | Same submit path needing `ensureLegacyJob()` bridge |
| F-DEFERRED-02 | Subagent C (workflow validation) | Workflow run counts need stabilized workflow runtime |

Subagents A and C should be aware of these findings. F-BLOCKER-07 is the same root cause Subagent A is addressing; F-DEFERRED-02 is a post-alpha improvement that depends on Subagent C's stabilization.

### F-DEFERRED-03: JobRun progress not wired into JOB_RUN assignment execution

- **Surface:** Jobs
- **Missing behavior:** The runner now executes canonical `JobWorkItem` items, but does not create a `JobRun` (via `jobService.startRun()`) or record per-item progress via `jobService.updateWorkItemRun()`. The `JobRun`/`JobWorkItemRun` model exists and is functional but is only used by direct API calls, not by the assignment runner.
- **User impact:** Job runs started via JOB_RUN assignment do not appear in the job's run list or event timeline. Users cannot see per-item progress from the UI.
- **Evidence:**
  - `OrchestrationRunnerService.runJob()` -- executes items but does not call `jobService.startRun()` or `jobService.updateWorkItemRun()`
  - `JobService.startRun()` -- creates `JobRun` with initial `JobWorkItemRun` entries
  - `JobService.updateWorkItemRun()` -- records per-item completion/failure
- **Why out of scope for this subagent:** The immediate task was to eliminate the legacy bridge and make execution canonical. Wiring run tracking is a follow-on feature.
- **Recommended owner/next action:** Post-alpha: update the runner to call `jobService.startRun(jobId)` before iterating items and `jobService.updateWorkItemRun()` after each item completes/fails.
- **Severity:** post-alpha deferred

### F-DEFERRED-04: Legacy AssignmentType values not represented in canonical JobWorkItemType

- **Surface:** Jobs
- **Missing behavior:** The canonical `JobWorkItemType` enum only supports `PLAN` and `WORKFLOW`. The legacy `OrchestrationJobItem` model supported `AGENT_MESSAGE`, `WAIT_FOR_MESSAGE`, `REPORT`, and `JOB_RUN` (nested). These capabilities are not available in the canonical job model.
- **User impact:** Users cannot create job items that send agent messages, wait for responses, or generate simple reports. These were meaningful features in the legacy system.
- **Evidence:**
  - `JobWorkItemType.java` -- only `PLAN`, `WORKFLOW`
  - `AssignmentType.java` -- includes `AGENT_MESSAGE`, `WAIT_FOR_MESSAGE`, `REPORT`
- **Why out of scope for this subagent:** The UI contract refactor focused on PLAN and WORKFLOW as the core job item types. Adding message/report items requires UI design and new persistence.
- **Recommended owner/next action:** Post-alpha: decide which legacy item types to reintroduce. At minimum, AGENT_MESSAGE is useful for notification-style job steps.
- **Severity:** post-alpha deferred

---

## Summary of Most Critical Findings (Alpha Blocker Priority)

1. **HTMX static noop** (F-BLOCKER-05) -- All HTMX interactions are inert in browser. This is the single highest-priority fix; everything else builds on HTMX being functional.

2. **Plan editor complex-section edits silently discarded** (F-BLOCKER-06) -- Users can edit fields, inputs, outputs, steps, deliverables, assumptions, and validation criteria, but Save reverts them to database state. This is a data-loss-class bug in the most heavily-used editing surface.

3. **Agent detail tabs dead** (F-BLOCKER-02) -- Six of seven tabs on the agent detail page are inert buttons. This is the most visible navigation regression.

4. **Job links target 405 route** (F-BLOCKER-04) -- Dashboard and agent page links produce HTTP errors. This is a highly visible regression from every entry point.

5. **Docker status loads raw JSON** (F-BLOCKER-03) -- Will be visible immediately once HTMX is functional.

6. **Plan field row edits hit nonexistent routes** (F-BLOCKER-01) -- Inline field editing triggers 405 errors. Combined with F-BLOCKER-06, the entire plan structured editing surface is nonfunctional.

7. **Agent submit JOB_RUN missing bridge** (F-BLOCKER-07) -- One of two submit paths fails for canonical jobs. Owned by Subagent A.

---

## Evidence files inspected

- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` (full, 4003 lines)
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/DashboardController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/JobController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/ProjectController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/AgentProfileController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/RuntimeSettingsController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/FrontendFragmentController.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/GlobalExceptionHandler.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/dashboard.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/agents.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/inbox.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/outputs.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/plans.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/workflows.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/projects.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/app.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/api.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/dom.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/js/orchestration/agent-chat.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/css/orchestration.css`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/static/webjars/htmx.org/dist/htmx.min.js`
- `/home/hickelpickle/Code/Java/magenta2/src/main/resources/schema.sql` (workspace_leases section)
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/InboxMessage.java`
- `/home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/InboxMessage.java`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-ui-htmx-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-beta-readiness-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-backend-contract-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-test-coverage-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-final-validation-plan.md`

---

## From Subagent E (Workspace Lease + Podman Runtime Hardening) — 2026-05-12

### E-NOTE-01: Live container execution timeout test requires a running Podman/Docker daemon

- **Surface:** Docker/Podman Runtime
- **Missing behavior:** Unit tests cover config, records, and contract behavior. The timeout cleanup path (stop + remove of stuck container within single timeout budget) compiles and is structurally correct but has not been validated against a live daemon because no Podman socket is available in the test environment.
- **User impact:** The double-timeout bug is fixed in code (single `waitContainerCmd` budget + stop/remove on timeout + 5s log drain grace). Live validation requires a host with Podman socket and `python:3.11-slim` image.
- **Evidence:** `DockerRuntimeClientTest.java` contains documented but disabled live-daemon tests. The refactored `execCommand` code is covered by code review and static analysis.
- **Why out of scope for this subagent:** Requires a running Podman daemon with the `python:3.11-slim` image pulled. The test environment has neither.
- **Recommended owner/next action:** Run `DockerRuntimeClient.execCommand` with `sleep 999` and a 5s timeout against a live Podman socket during Gate 5 (browser validation) or a dedicated integration gate. Verify the container is stopped and removed within the timeout + grace window (under 25s for a 5s timeout).
- **Severity:** alpha should-fix

### E-NOTE-02: Startup smoke blocked by missing external dependencies

- **Surface:** Startup smoke / Spring Boot context
- **Missing behavior:** `timeout 30s mvn spring-boot:run` does not reach `Started Magenta2Application` in the test environment because the Ollama endpoint at `http://192.168.1.112:11434` is unreachable.
- **User impact:** Cannot verify full application context startup in the current test environment. This is a pre-existing environment issue, not caused by workspace lease or Docker runtime changes.
- **Evidence:** application.yml references `spring.ai.ollama.base-url: http://192.168.1.112:11434`. Startup hangs waiting for Ollama connectivity.
- **Why out of scope for this subagent:** This is an infrastructure/Ollama dependency. The workspace lease and Docker runtime changes compile and pass focused tests independently.
- **Recommended owner/next action:** Run startup smoke on a host with Ollama accessible, or with `--spring.ai.ollama.base-url=` overridden to a reachable endpoint.
- **Severity:** alpha should-fix

### E-NOTE-03: Workspace lease unique index migration for existing duplicate data

- **Surface:** Workspace leases / Schema migration
- **Missing behavior:** If an existing database has duplicate active WRITE leases for the same workspace (from the pre-fix check-then-insert gap), the `CREATE UNIQUE INDEX IF NOT EXISTS` will fail because existing data violates the uniqueness constraint.
- **User impact:** Development databases are unlikely to have stale duplicates (concurrent access is rare in single-user alpha). If duplicates exist, SQLite will raise a clear error during schema initialization, and the user can reset their dev database.
- **Evidence:** The `ensureSchema()` method uses `CREATE UNIQUE INDEX IF NOT EXISTS`. No pre-cleaning migration step is included. The old non-unique index (`idx_workspace_leases_active`) remains in databases that were initialized before this change — it is harmless but redundant.
- **Why out of scope for this subagent:** Production migration is a deployment concern, not an alpha remediation task. The fix is correct for fresh databases.
- **Recommended owner/next action:** For production rollout, add a pre-migration step: release all but the most recent active WRITE lease per workspace before creating the unique index. For alpha, the clear error message is sufficient.
- **Severity:** post-alpha deferred

### E-NOTE-04: Pre-existing test compilation failures in other subagent scopes

- **Surface:** Test suite
- **Missing behavior:** `JobServiceTest.java`, `OrchestrationControllerTest.java`, and `OperationalUiContractControllerTest.java` fail to compile because `JobService`'s constructor was changed (Subagent A added `PlanService` and `WorkflowService` parameters) without updating these tests.
- **User impact:** `mvn test` does not pass because of compilation errors in other subagents' test files. Workspace lease and Docker tests (25 total) pass when compiled and run independently.
- **Evidence:** `mvn test-compile` shows errors only in `JobServiceTest`, `OperationalUiContractControllerTest`, and `OrchestrationControllerTest` — all referencing `JobService` constructor parameter mismatches.
- **Why out of scope for this subagent:** These are Subagent A's test files requiring constructor argument updates. My write scope is workspace leases and Docker runtime.
- **Recommended owner/next action:** Subagent A or the orchestrator should update these tests to pass the new `PlanService` and `WorkflowService` arguments to `JobService` constructor calls.
- **Severity:** alpha blocker (blocks full test suite run)

