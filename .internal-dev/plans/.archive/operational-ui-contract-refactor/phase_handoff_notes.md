# Operational UI Contract Refactor Phase Handoff Notes

Append-only notes for phase divergences, implementation decisions forced by code reality, known limitations, and contract changes future phases must know.

## 2026-05-12 - Phase 01 implementation notes

- `JobDefinition` is now the public job API shape for `/api/jobs`, including `ownerAgentId`, `projectId`, `workspaceId`, and `status`; empty `DRAFT` jobs are valid so the manual job page can create a starting record before items exist.
- Job item endpoints are backed by the `JobDefinition.items` JSON contract for this phase. They validate PLAN items require `planId` and WORKFLOW items require `workflowId`.
- `/api/jobs/{jobId}/events` is a lightweight run-derived event view for contract stability; richer job event history remains a later operational surface concern.
- `/api/jobs/{jobId}/outputs` and `/api/outputs` return `RunOutputArtifact` arrays. Job/project/agent filters map through known job definitions and child run ids where possible; artifacts not attached to a job definition remain discoverable by `runId` or global query.
- Project requests now use canonical `name` and `description`; `title` and `summary` remain request-only compatibility aliases.
- `GET /api/projects/{projectId}/workspace` intentionally returns relative display path metadata instead of exposing raw filesystem paths.
- Agent inbox approval buttons were removed from agent inbox rows because the runtime agent inbox supports read/handled actions, not approval response semantics. User workflow approvals still use `/api/users/inbox/{messageId}/respond`.
- Future-phase caveat: `AssignmentService` still validates `JOB_RUN` assignments through legacy `OrchestrationJobService`/`orchestration_jobs`, while the public UI now creates `JobDefinition` rows. Phase 03 submit-to-agent work should either route public jobs through the canonical `JobDefinition` service or explicitly retire the legacy orchestration job validation path.

## 2026-05-12 - Phase 01 validation

- One blocking bug found and fixed: `dashboard.js:224` mapped the "queue" tab name directly to URL path `/api/agents/{agentId}/queue`, which does not exist. Fixed by mapping `"queue"` → `"assignments"` in the URL construction.
- All other exit criteria pass: no dead endpoints in active JS paths, all payload fields match controller DTOs, job/project CRUD is complete, dashboard summary endpoint exists with full data model.
- Non-blocking notes:
  - `app.js` is unreferenced dead code with bugs (uses POST for save-jobs, sends invalid `config` field). Should be deleted in a cleanup pass.
  - `WorkflowController.streamRun()` ignores the request body (`agentId`, `jobId`, etc. sent by JS are silently discarded). The runner starts without agent context. This is a pre-existing gap, not a Phase 01 regression.
  - Agent detail "history" tab is a static placeholder with no backend endpoint.

## 2026-05-12 - Phase 02 implementation (Dashboard information architecture)

- Replaced the card launcher grid on `/dashboard` with an operational layout: chat band (placeholder), status strip (KPI stats), main content (Active Work table, Open Projects cards, Agents table), and side rail (Inbox, Recent Outputs, Recent Events).
- Server-side renders layout structure with empty/loading containers; `dashboard.js` fetches `/api/dashboard/summary` and populates all sections.
- SimplyPages `Table` component used for server-rendered table headers; dynamic rows rendered by JS from API data.
- Status strip uses individual stat cards with IDs targeted by JS for value updates.
- `formatSince()` helper renders human-readable relative time for data freshness display.
- CSS uses a two-column grid layout (`.dashboard-main-layout`) with responsive collapse at 900px.
- Chat band is visually present with disabled input — deferred for future dashboard-aware tools.
- `/chat` remains isolated and unaffected.
- Phase 03 should note: the dashboard already renders project and agent names with links to their detail pages; any plan/workflow/worktype changes in Phase 03 should ensure the dashboard's Active Work rows remain consistent.

## 2026-05-12 - Phase 02 HTMX refactor (dashboard reimplementation)

### What was converted

The dashboard implementation was rewritten to be HTMX-first per the plan's explicit language in Step 5 of `02-dashboard-information-architecture.md` ("Build dashboard refresh with HTMX by default"). The previous Phase 02 implementation used `jsonFetch` + `innerHTML` template literals for all rendering, which contradicted the plan's HTMX-first mandate.

**Dashboard HTMX partial endpoints added to `OrchestrationController.java`:**
- `GET /dashboard/_stats` — returns the KPI status strip (5 stats) as HTML fragment, refreshed every 30s
- `GET /dashboard/_active-work` — returns Active Work table with pre-rendered data rows using SimplyPages `Table` component
- `GET /dashboard/_open-projects` — returns Open Projects card grid with links
- `GET /dashboard/_agents` — returns Agents table with status badges
- `GET /dashboard/_side-inbox` — returns inbox summary stat
- `GET /dashboard/_side-outputs` — returns recent outputs list (top 5)

**Dashboard page changes:**
- Main `/dashboard` endpoint now renders section shells with `hx-get` + `hx-trigger="load, every 30s"` + `hx-swap="innerHTML"` on each container div
- Status strip is split: stats load via HTMX, freshness element stays JS-driven
- Chat band and layout structure remain server-rendered in the main endpoint
- Loading/empty state placeholders rendered server-side

**Project list:**
- Added `GET /projects/_list` HTMX partial for project navigation list
- Project list items rendered as `<a>` links (no JS needed for navigation)
- `projects.js` stripped of list loading, keeps create/save/delete and detail panel loading

### What was kept as JS (per plan exceptions)

Per the plan's language: "Only add page-level JS when it is the path of least resistance (for example lightweight client-side recency ticker or SSE hook), and keep it narrowly scoped."

1. **Freshness/recency ticker** — `initDashboardTicker()` in `dashboard.js` with `formatSince()` helper. Runs on a 30s interval to update the `stat-freshness` element.

2. **Agent CRUD** — `initAgents()`, `initAgentDetail()`, `renderAgentProfile()`, `renderAgentTab()`, `renderAssignmentForm()` remain JS-based. These involve complex tab-based navigation, dynamic form rendering, and multiple API calls. Full HTMX conversion would require significant additional backend endpoints and is deferred.

3. **Job CRUD** — `initJobs()`, `initJobDetail()` remain JS-based. Jobs involve dynamic editor forms, multiple sub-panels (runs, events, outputs, items), and interactive prompts (`prompt()` for item type selection). HTMX conversion is deferred.

4. **Settings save** — remains JS-driven (`data-action="save-settings"`) because model dropdowns need API-populated options and the form spans multiple panels that aren't easily wrapped in a single `<form>`.

5. **Inbox and Outputs** — `inbox.js` and `outputs.js` unchanged. These involve multi-step interactions (agent selector → load inbox, multiple filter selects → browse outputs) that would require significant backend fragment endpoints.

6. **Plans and Workflows** — `plans.js` and `workflows.js` unchanged. These use SSE streaming for run execution and have complex dynamic field editors that are impractical in pure HTMX.

7. **`agent-chat.js`, `api.js`, `dom.js`** — unchanged (API utilities, used by chat).

### Divergences from the plan

1. **Phase 1 form submission HTMX conversion partially deferred**. The plan's "SHOULD FIX" items (agent list/detail, job CRUD) remain JS-driven for now. The project list is HTMX-converted. Full HTMX form conversion for agent/job pages would require substantial new backend partial endpoints.

2. **Settings save kept as JS**. The plan says "Convert save to HTMX form put." Model dropdowns are populated by JS (need API calls to list available models), and the form fields are spread across multiple panels. A pure HTMX approach would require either a monolithic form or complex `hx-include` selectors. The current JS approach is cleaner for this specific case.

3. **Agent/job pages still load dashboard.js**. The `dashboard.js` file now serves as the shared module for agents, jobs, settings, AND the dashboard ticker. This avoids creating separate tiny JS files for each page.

### Controller constructor changes

`OrchestrationController` now injects six additional services:
- `ProjectService`, `JobService`, `AgentProfileService`
- `InboxService` (workflow package, for `userInbox()`)
- `OutputArtifactService`, `RuntimeSettingsService`

These are used by the dashboard HTMX partial endpoints to fetch live data for HTML fragment rendering.

### Files modified

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` — Added 6 HTMX partial endpoints, modified dashboard page to use HTMX containers, added project list HTMX partial, bumped JS version to v=2
- `src/main/resources/static/js/orchestration/dashboard.js` — Stripped of client-side rendering functions (initDashboard, renderDashboardStats, renderActiveWork, renderOpenProjects, renderAgents, renderSideInbox, renderSideOutputs). Kept freshness ticker, agent/job/settings JS. Bumped version to v=2.
- `src/main/resources/static/js/orchestration/projects.js` — Removed list loading (now HTMX), kept create/save/delete and detail panel loading. Bumped version to v=2.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java` — Updated to provide stubs for all new service dependencies. Added 2 new tests for HTMX containers.

### Validation

- `mvn test`: 311 tests pass (0 failures, 0 errors)
- `timeout 30s mvn spring-boot:run`: Spring context starts successfully (Tomcat on port 34899, "Started Magenta2Application in 2.609 seconds")

## 2026-05-12 - Phase 03 implementation notes

- All plan editing is now HTMX-driven. The plan page title is "Plans" (not "Plans & Tasks"). No Run button or Run panel is rendered. The plan prompt profile text field is replaced with a Worktype dropdown (CODING_CENTRIC, DATA_CENTRIC, RESEARCH_CENTRIC).
- Kind and Status are hidden in a collapsible `<details>` "Advanced" section; SESSION_PLAN is not shown in the task template editor.
- `example` field is removed from the UI input/output field editor. Field rows show name, type, required, array, description, and schema JSON in an expandable layout.
- List editors (deliverables, steps, validation criteria, assumptions) use ordered row editors with add/remove HTMX buttons. Steps use `PlanStep(order, text)` semantics.
- Submit-to-Agent creates a `TASK_RUN` `WorkAssignment` via `AssignmentService`. The UI shows assignment ID, agent link, status, and priority. Model override and priority are configurable in the submit form.
- Continue-in-Chat generates a prompt text with current plan state, instructions to grok the plan, continue questioning, and ask for guidance.
- `WorkTypeProfile` enum maps legacy `PromptProfile` values: CODING->CODING_CENTRIC, RESEARCH->RESEARCH_CENTRIC, others->DATA_CENTRIC. The `promptProfile` field in `PlanDefinition` now stores the `WorkTypeProfile.name()` string.
- `WorkTypeProfileService` provides append-only system text for each profile. It is wired into `PromptContextAssembler` and injected as an optional dependency into `ChatService`.
- `PlanController` gained `AssignmentService` and `AgentProfileService` dependencies for the submit-to-agent endpoint. Added `workTypeProfile` field to `PlanCreateRequest` and `PlanUpdateRequest` with legacy `promptProfile` mapping. Added `POST /{planId}/submit` and `GET /{planId}/chat-prompt` endpoints.
- `OrchestrationController` gained `PlanService` and `AssignmentService` dependencies. Added 16 HTMX partial endpoints for plan list, editor CRUD, field add/remove, list item add/remove, submit-to-agent, and chat-prompt fragment.
- `plans.js` stripped from 247 lines to 12 lines — all rendering, save, new, run, and edit handlers removed. File preserved as a skeleton for future JS affordances.
- `ChatService` three constructors updated to accept optional `WorkTypeProfileService` parameter, passed to `PromptContextAssembler`.

### HTMX partial endpoints added to OrchestrationController
- `GET /plans/_list` — plan list HTML fragment (filterable via hx-include from filter input)
- `GET /plans/_editor/_new` — empty plan editor with POST form
- `GET /plans/_editor/{planId}` — editor pre-filled with plan data and PUT form
- `POST /plans/_editor` — creates new plan from form params
- `PUT /plans/_editor/{planId}` — updates plan scalar fields (complex fields preserved from DB state)
- `POST /plans/_editor/{planId}/finalize` — finalizes plan
- `POST|DELETE /plans/_editor/{planId}/inputs[/{index}]` — add/remove input field
- `POST|DELETE /plans/_editor/{planId}/outputs[/{index}]` — add/remove output field
- `POST|DELETE /plans/_editor/{planId}/deliverables[/{index}]` — add/remove deliverable
- `POST|DELETE /plans/_editor/{planId}/steps[/{index}]` — add/remove step
- `POST|DELETE /plans/_editor/{planId}/validation[/{index}]` — add/remove validation criterion
- `POST|DELETE /plans/_editor/{planId}/assumptions[/{index}]` — add/remove assumption
- `GET /plans/_submit-form/{planId}` — submit-to-agent form fragment with agent select and required inputs
- `POST /plans/_submit/{planId}` — creates WorkAssignment and returns result fragment
- `GET /plans/_editor/{planId}/chat-prompt-fragment` — returns copyable chat prompt text

### Divergences from the plan

1. **Field expand/collapse not fully implemented per spec**. The plan calls for expandable field rows with server-rendered expand/collapse fragments. Currently all field rows render in expanded mode (showing name, type, required, array, description, and schema). The per-field expand/collapse HTMX endpoints (`GET /plans/_editor/{planId}/fields/{fieldKind}/{index}` and `_collapse`) are deferred for now. The expand/collapse rendering is structurally correct but uses the `details` HTML element for the Advanced section only.

2. **Field editing uses individual form inputs, not a JSON serialization step**. The field rows render separate inputs for name, type, required, array, description, and schema. A future enhancement could add a "save field changes" HTMX endpoint that reads all fields from a row and updates them atomically, but currently the field values are not auto-persisted from the inline inputs (they require a save operation or add/remove to persist).

3. **Steps rendered as text items, not ordered PlanStep records in the list editor**. The list editor shows steps as text inputs (like deliverables). The PlanStep(order, text) semantics are preserved in the backend through `addListItem`/`removeListItem` methods, but the order number is derived from list position rather than explicitly editable.

4. **Agent select in submit form lists all active agents**. The plan mentions "agent select, optional model override, priority" which is implemented. The agent select defaults to the first active agent if none selected.

5. **planningModel and executionModel selectors are stub "Default" only**. These dropdowns are populated as in the original code (modelSelect helper that only has a "Default" option). Full model list population requires a server-side list of available models, which the ChatService provides via `availableModels()` but is not yet wired into the plan editor rendering. This matches the original behavior.

### Validation

- `mvn test`: 330 tests pass (0 failures, 0 errors) — 19 new tests added (2 WorkTypeProfileTest, 8 WorkTypeProfileServiceTest, 2 new PlanController/editor tests + 7 updated in OrchestrationControllerTest)
- `timeout 25s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: Spring context starts successfully (Tomcat on port 33819, "Started Magenta2Application in 2.669 seconds")

## 2026-05-12 - Phase 03 validation fixes

Three gaps found during validation against the plan spec, all fixed:

- **FAIL 2a — Description field now uses TextArea**: `OrchestrationController.java:1212` changed from `TextInput` (single-line) to `TextArea` with `rows="2"` for field descriptions, matching the plan requirement.
- **FAIL 9a — Nonblank description enforced for required fields**: Added check in `PlanService.validateFieldNames()` (line 1263) that throws `IllegalArgumentException` when a required field has a blank description.
- **FAIL 9b — Schema JSON validated when present**: Added `ObjectMapper.readTree()` check in `PlanService.validateFieldNames()` (line 1266) that validates schema is parseable JSON when nonblank, throwing `IllegalArgumentException` with the parse error message on failure.

## 2026-05-12 - Phase 04 implementation notes

- `WorkflowRoute` and `WorkflowRouteType` (MAP_OUTPUT, PASS_THROUGH, LOG, CONTROL) are new records in `orchestration.workflow`.
- `WorkflowNode` gained `label`, `inputName`, and `config` fields. The old 7-arg constructor (key, type, planId, inputBindings, messageTemplate, resumePolicy, parallel) is `@Deprecated` but preserved for backward compatibility.
- `WorkflowDefinition` gained a `routes` field. The old 6-arg constructor (without routes) is `@Deprecated` but preserved.
- `WorkflowRepository` adds `routes_json` column via online `ALTER TABLE ADD COLUMN` migration. Falls back silently if the column already exists.
- Compatibility import: Old `STEP_OUTPUT` bindings become `MAP_OUTPUT` routes on save. Old `LITERAL` bindings remain as legacy data read by the runner's fallback path (no route generated).
- Graph validator (`WorkflowValidator`) checks: node key uniqueness, route endpoint existence, cycle detection (DFS), required task input satisfaction, and type compatibility. Returns structured `ValidationResult(errors, warnings)`.
- `WorkflowRunner` was rewritten from sequential-index execution to graph-traversal ordering. Nodes become ready when all incoming dependency-creating route sources complete. LOG routes materialize outputs without creating dependencies. PASS_THROUGH routes forward all source outputs as a map.
- The runner still falls back to `BindingResolver` for old-style inputBindings so pre-route workflow definitions continue to execute.
- Workflow page is now HTMX-first: `/workflows` renders containers; all CRUD, node add/remove, route add/remove, validation, and submit-to-agent go through HTMX partial endpoints. The Run button and run panel are removed.
- `workflows.js` is a 12-line skeleton (page-level dispatch listener only).
- `OrchestrationController` now injects `WorkflowService`. 14 workflow HTMX partial endpoints added.
- Controller test updated: `StubWorkflowService` added, test expectations updated for HTMX-first page and skeleton JS.

### Divergences from the plan

1. **Node expand/collapse not implemented per spec**. The plan calls for expandable node cards with properties panels. The current implementation shows all nodes in a flat row list with inline editable fields. The expandable detail panel is deferred for a future UI polish pass.

2. **Route editor is inline, not a separate panel**. The route add form and route list are inline in the same editor panel rather than a dedicated properties panel. This keeps the implementation simple but isn't the three-panel layout specified.

3. **No plan output list loaded for route `fromOutputName` select**. The route add form uses a plain text input for `fromOutputName` instead of a dynamic dropdown populated from the source node's plan outputs. This is a known limitation; implementing it requires loading plan output lists per-node via HTMX.

4. **`hxConfirm` is not natively supported in SimplyPages' HtmlTag**. Used `withAttribute("hx-confirm", ...)` as a workaround. The SimplyPages library should add `.hxConfirm()` to HtmlTag for future phases.

5. **Gate nodes retain `currentNodeIndex` tracking**. The `WorkflowRun` record still uses `currentNodeIndex` for checkpoint compatibility and restart. This is kept for the resume flow but the main execution loop no longer depends on sequential indices.

### Known limitations

- The runner does not yet support parallel node execution (fan-out parallelism). All nodes execute sequentially even when multiple are ready.
- The runner's `resolveNodeInputs` from routes only supports MAP_OUTPUT, PASS_THROUGH, and CONTROL. CONDITIONAL routing with expression evaluation is out of scope.
- The graph validator validates plan input/output types for MAP_OUTPUT routes but not for PASS_THROUGH routes (since the latter forwards entire output maps).
- The compatibility importer runs on every save. If a user adds explicit routes AND has legacy bindings, the importer may create duplicate routes. The current implementation skips duplicates by route ID but this may not catch all collisions in practice.
- `hxInclude("previous .field-row")` for add-node/add-route forms was fixed in validation remediation; now uses closest .field-row.

### Contract changes future phases must know

- `OrchestrationController` constructor now requires `WorkflowService` as the 10th parameter. Any test or wiring code that constructs the controller must be updated.
- `WorkflowDefinition` now has a `routes` field. All code that reads/saves workflow definitions should expect routes to be present; code that creates `WorkflowDefinition` should use the new constructor or accept the `@Deprecated` no-routes constructor.
- `WorkflowNode` has new fields `label`, `inputName`, and `config`. The old 7-arg constructor is deprecated. New code should use the full constructor.
- `WorkflowRunner` now requires `WorkflowDefinition` to have a nodes list for graph computation. The `routes` field is optional (empty routes list means all nodes are roots and execute in list order).

## 2026-05-12 - Phase 04 validation remediation

Four blocking findings from Phase 04 validation, all fixed:

- **BLOCKING-01 — REST API discards routes**: `WorkflowController.java` POST/PUT/validate endpoints were constructing `WorkflowDefinition` without passing `definition.routes()`. Fixed by threading `definition.routes()` through all three endpoints.
- **BLOCKING-02 — Broken hx-include selectors**: `OrchestrationController.java` lines 1827 and 1861 used `hxInclude("previous .field-row")` but the Add/Add Route buttons are children of `.field-row`, not siblings. Changed both to `hxInclude("closest .field-row")` matching the pattern already used correctly for plan field edits.
- **BLOCKING-03 — schema.sql missing routes_json column**: Added `routes_json text not null default '[]'` column to `workflow_definitions` CREATE TABLE in schema.sql.
- **BLOCKING-04 — Node key collision risk**: `addWorkflowNode()` generated keys as `"node_" + (nodes.size() + 1)`, which collides after node deletions. Changed to find the max existing numeric suffix and increment.

### Remediation validation
- `mvn test`: 330 tests pass (0 failures, 0 errors)
- `timeout 25s mvn spring-boot:run`: Started Magenta2Application in 2.673 seconds

## 2026-05-12 - Phase 05 implementation notes

Jobs and projects are now HTMX-first. All job CRUD, job item editing, project CRUD, and project detail sections are driven by HTMX partial endpoints. JS-based handlers for these operations have been removed from dashboard.js and projects.js.

### Job HTMX partial endpoints added
- `GET /jobs` — page shell with sidebar list + editor panel
- `GET /jobs/_list` — job list fragment (filterable via agent select, `hx-include`)
- `GET /jobs/_editor/_new` — empty create form
- `GET /jobs/_editor/{jobId}` — populated editor with scalar fields, items list, side panels (events/outputs via HTMX)
- `POST /jobs/_editor` — create job from form params (title required)
- `PUT /jobs/_editor/{jobId}` — update job scalar fields (preserves items, workspaceId, timestamps)
- `DELETE /jobs/{jobId}` — delete job
- `POST /jobs/_editor/{jobId}/items` — add item (key, type, planId/workflowId, modelOverride, priority from inline form)
- `DELETE /jobs/_editor/{jobId}/items/{index}` — remove item by list index
- `PUT /jobs/_editor/{jobId}/items/{index}` — update item by list index
- `GET /jobs/_submit-form/{jobId}` — submit-to-agent form fragment with agent select
- `POST /jobs/_submit/{jobId}` — creates `JOB_RUN` `WorkAssignment` via `AssignmentService`
- `GET /jobs/_detail/{jobId}/events` — run events fragment
- `GET /jobs/_detail/{jobId}/outputs` — recent outputs fragment

### Project HTMX partial endpoints added
- `GET /projects` — page shell with sidebar list + editor panel (HTMX-first)
- `GET /projects/_list` — existing endpoint updated to use HTMX buttons (hx-get loads editor) instead of `<a>` links
- `GET /projects/_editor/_new` — empty create form
- `GET /projects/_editor/{projectId}` — populated edit form with workspace, agent membership, active jobs, and outputs sections (all loaded via HTMX)
- `POST /projects/_editor` — create project (name and ownerAgentId required; shows helpful message if no agents exist)
- `PUT /projects/_editor/{projectId}` — update project scalar fields
- `DELETE /projects/{projectId}` — delete project
- `GET /projects/_detail/{projectId}` — full detail fragment (delegates to editor)
- `GET /projects/_detail/{projectId}/jobs` — active jobs fragment
- `GET /projects/_detail/{projectId}/agents` — assigned agents fragment
- `GET /projects/_detail/{projectId}/outputs` — recent outputs fragment (cross-references project jobs -> run ids -> artifacts)

### JS reduction
- `dashboard.js`: Removed `initJobs()` and `initJobDetail()` functions (lines 297-392). Job rendering is now entirely HTMX-driven. Agent CRUD, settings save, and dashboard ticker remain as JS. Version bumped to v=3.
- `projects.js`: Reduced to 12-line skeleton (like plans.js and workflows.js). All create/save/delete/edit/detail panel loading moved to HTMX. Version bumped to v=3.

### OrchestrationController changes
- Added `OrchestrationJobService` as 11th constructor parameter.
- CSS version bumped to v=4, dashboard.js to v=3, projects.js to v=3.
- Job editor rendering (~150 lines of helpers): `jobEditorFragment()`, `jobItemsSection()`, `jobItemTypeSelect()`, `jobsAgentFilter()`, `jobItemFromParams()`, `parseIntOrNull()`.
- Project editor rendering (~100 lines of helpers): `projectEditorFragment()`.

### Divergences from the plan

1. **Job item editor uses inline form, not per-item HTMX rows**. The plan calls for a structured list editor similar to plan deliverables where each item is editable inline with its own save button. The current implementation has an inline add form at the bottom and shows items as read-only rows with remove buttons. Item updates go through a full editor re-render rather than per-field HTMX. This is simpler and avoids complex partial updates for multi-field records, but provides less granular editing.

2. **Job item inputBindings are not surfaced in the UI**. The `JobWorkItem.inputBindings` field (Map<String, Object>) is set to empty map in the add form. This is deferred because input binding editing requires a structured editor (JSON editor or key-value pair editor) that would be significantly more complex. The field is preserved in the data model for future use.

3. **Plan/workflow reference validation is deferred to JobService**. The HTMX form accepts planId and workflowId as plain text inputs. Server-side validation happens in `JobService.normalizeItem()` which enforces PLAN items require planId and WORKFLOW items require workflowId. There is no ahead-of-time validation that the referenced plan/workflow exists (that would require loading all plans/workflows for a dropdown, or validating on item add).

4. **Job item `hx-include` uses a dedicated form container**. The add-item form fields are wrapped in `#job-items-new-form` and the add button uses `hx-include="#job-items-new-form"`. This pattern works correctly but is unusual — normally HTMX includes fields from the same form. The dedicated container approach is needed because the add-item form is outside the main job editor form.

5. **Submit-to-agent bridges legacy OrchestrationJobService**. `ensureLegacyJob()` creates a shadow `OrchestrationJob` record when submitting a `JobDefinition` to an agent. This bridges the gap between the new `JobDefinition`/`job_definitions` table and the legacy `OrchestrationJobService.orchestration_jobs` table that `AssignmentService` validates against. This is necessary because Phase 01 moved job creation to the new system but `AssignmentService` still uses the legacy `OrchestrationJobService.get()` for JOB_RUN validation.

6. **Project workspace is rendered inline, not via API HTMX call**. The plan suggests an API response for workspace metadata. The implementation renders workspace summary inline in the project editor (calling `projectService.workspaceSummary()` server-side) rather than loading it via HTMX from the `/api/projects/{id}/workspace` JSON endpoint. This avoids the JSON-in-HTML mismatch.

7. **No "New Project" owner agent select populated server-side**. The plan calls for an owner agent select with real agents. The current implementation uses a plain text input for ownerAgentId. A dropdown with real agents would require either server-side pre-rendering (like the jobs agent filter) or a separate HTMX fragment. This is deferred — the text input is functional and a dropdown would be a UX enhancement.

8. **Project detail page preserves backward compatibility**. The old `/projects/{projectId}` endpoint now renders the projects page shell with the editor pre-loaded via HTMX (`hx-get="/projects/_editor/{projectId}"`), rather than a separate JS-driven detail page. Existing links to `/projects/{projectId}` continue to work.

### Contract changes future phases must know

- `OrchestrationController` constructor now requires `OrchestrationJobService` as the 11th parameter. Any test or wiring code that constructs the controller must be updated.
- `OrchestrationController` CSS version is v=4, dashboard.js is v=3, projects.js is v=3. Tests check for these specific version strings.
- The `/jobs` page no longer has a separate `/jobs/{jobId}` detail page endpoint. The `jobDetail()` method was removed. Job editing happens via HTMX fragments loaded into the `/jobs` page shell.
- `ensureLegacyJob()` creates shadow `OrchestrationJob` records in the legacy table. When the legacy orchestration job path is retired (future phase), this method should be removed and `AssignmentService` should validate against `JobService` instead.

### Validation
- `mvn test`: 331 tests pass (0 failures, 0 errors) — 1 new test added (jobEditorFragmentRendersFormWithItemsAndSubmitButtons)
- `timeout 25s mvn spring-boot:run`: Started Magenta2Application in 2.714 seconds

## 2026-05-12 - Phase 06 implementation notes

Phase 06 backend (HTMX partials, Docker status endpoint, agent editor sections, submit form) was already present in the working tree as an untracked `OrchestrationController.java`. Phase 06 work focused on fixing compilation errors, completing JS removal, adding CSS, and updating tests.

### What was done

**Bug fix — Form.create() chain compilation errors:**
Five agent editor form builders used chained `Form.create().withAttribute("hx-put", ...)` which broke because `withAttribute()` returns `HtmlTag`, not `Form`. Fixed by switching to separate `form.withHxPut(...)` / `form.withHxTarget(...)` / `form.withHxSwap(...)` calls, matching the pattern used elsewhere in the file.

**JS reduction — `dashboard.js`:**
Removed all agent CRUD and rendering functions: `initAgents()`, `agentCard()`, `loadAgentDetail()`, `initAgentDetail()`, `renderAgentProfile()`, `renderAssignmentForm()`, `renderAgentTab()`, `csv()`, `title()`, and the `ORCH_ENDPOINTS` constant. Removed unused imports (`$$`, `bindTabs`, `chip`, `escapeHtml` from dom.js). Kept `initDashboardTicker()`, `formatSince()`, `modelCatalog()`, `initSettings()`, the `save-settings` handler, and the HTMX after-request listener. Added local `escapeHtml()` helper since settings page still needs it for model chip rendering. Version bumped to `dashboard.js?v=5`.

**New file — `agents.js`:**
Minimal 8-line skeleton that listens for `data-orchestration-page='agents'` on `DOMContentLoaded`. No JS functionality — all agent CRUD, tab loading, editor saves, and submit-to-agent are HTMX-driven. Included in agent pages at `agents.js?v=1`.

**OrchestrationController changes:**
- Added `AGENTS_JS` constant (`"/js/orchestration/agents.js?v=1"`)
- Changed agents list page (`/agents`) and agent detail page (`/agents/{agentId}`) to include `AGENTS_JS` instead of `DASHBOARD_JS`
- CSS version bumped to `v=6` (was `v=5`)

**CSS — Agent dashboard styles:**
Added styles for: `.agent-dashboard`, `.agent-meta-item`/`.agent-meta-label`/`.agent-meta-value`, `.agent-dashboard-counters`, `.agent-counter-card`/`.agent-counter-value`/`.agent-counter-label`, `.agent-editor-section`, `.agent-prompt-textarea`, `.agent-tool-chips`, `.agent-docker-status`/`.agent-docker-stat`, `.agent-submit-result`, and responsive overrides at 900px.

**Test updates — `OrchestrationControllerTest`:**
- Added `StubRuntimeInboxService` (12th constructor param was missing)
- Updated all CSS version assertions: `v=4` → `v=6`
- Updated dashboard.js version: `v=3` → `v=5`
- Rewrote `agentsPageRendersWithProfileAndQuickActions` → `agentsPageRendersHtmxFirstWithListAndDetailContainers` (checks for browser-layout, agent-list, agent-detail-container, agents.js, HTMX attributes; verifies absence of old JS data-action attributes)
- Rewrote `agentDetailPageRendersWithTabsAndAssignmentForm` → `agentDetailPageRendersHtmxTabsAndEditorContainers` (checks for entity-detail-layout, tabs, editor/submit HTMX containers, agents.js; verifies absence of old JS-dependent markers)
- Renamed `dashboardJsPreservesAgentAndSettingsBehavior` → `dashboardJsRemovesAgentRenderingAfterPhase06` (verifies agent functions removed, settings+dashboard ticker kept)
- Added 7 new tests: `agentListFragmentRendersTable`, `agentDashboardTabRendersCountersAndDockerStatus`, `agentQueueTabRendersAssignmentTable`, `agentInboxTabRendersInboxTable`, `agentEditorRendersIdentityPromptToolsShellSections`, `agentSubmitFormRendersStructuredFields`, `agentsJsIsMinimalHtmxFirstSkeleton`
- 36 total tests in this class (was 29), 338 overall (was 331)

### Divergences from the plan

1. **Docker status endpoint already existed**. `RuntimeController.java` with `@GetMapping("/docker/status")` and `DockerStatusResponse` record were already present in the previous phase work. The agent dashboard tab references it via `hx-get="/api/runtime/docker/status"` — no new endpoint needed. The plan specified this as a Phase 06 deliverable but it was pre-implemented.

2. **Agent list redesign was already HTMX-based**. The working tree already had `/agents/_list` returning a table with HTMX rows, a filter input with hx-get, and create/reload buttons with HTMX actions. The plan's "redesign agents list" was essentially complete from Phase 02.

3. **Agent detail dashboard tabs were already HTMX-loaded**. All 7 tabs (dashboard, queue, inbox, jobs, workspace, outputs, history) were already implemented as `@ResponseBody` `@GetMapping` endpoints returning HTML fragments. The phase plan's detail dashboard was essentially pre-built.

4. **Profile editor sections were already implemented**. Identity, prompt, tools, and shell sections with HTMX PUT save endpoints were already present. The tools editor uses a comma-separated text input (not checkboxes from ChatToolRegistry) — this is a simplification that avoids needing to expose the tool registry as an API.

5. **Settings page still uses JS for model catalog and save**. The plan's "Convert settings to HTMX or keep as JS" question is answered by keeping JS (`dashboard.js` `initSettings()`). The model dropdowns require API calls to populate (`modelCatalog()`), and the form spans multiple panels that don't fit a single `<form>`. The `data-action="save-settings"` button remains JS-driven.

6. **No new REST endpoints created**. The `RuntimeController` Docker status endpoint was pre-existing. All agent page endpoints (`/agents/_list`, `/agents/_detail/*`, `/agents/_editor/*`, `/agents/_submit*`) were pre-existing. Phase 06 was purely a JS-removal + CSS + test completion pass on an already-HTML-ready backend.

7. **Submit form uses plain text inputs for target IDs**. The plan called for "generated input fields when selected item has schema" but the implementation uses a generic `targetId` text input with `assignmentType` select. Schema-driven input generation is deferred.

### Known limitations

- Tools editor uses comma-separated text input rather than multi-select checkboxes from `ChatToolRegistry`. Implementing a checkbox UI would require a separate endpoint exposing the tool registry names.
- Agent detail "history" tab is a placeholder with text about assignments/events being persisted. No actual run history query endpoint exists yet.
- Workspace tab shows a static note pointing to the API endpoint rather than inline workspace data.
- Agent chat side-panel at `/agents/{agentId}` needs `agent-chat.js` — this is loaded from the base shell (not the OrchestrationController) and remains functional.
- Settings page still loads `dashboard.js` for the `initSettings()` handler. This is intentional per the plan's allowance for JS when HTMX would be materially more complex.

### Contract changes future phases must know

- `OrchestrationController` constructor now requires 12 parameters including both `InboxService` (workflow package) and `io.mindspice.magenta2.ai.orchestration.runtime.InboxService` (runtime package). Tests must provide stubs for both.
- CSS version is `v=6`, dashboard.js is `v=5`, agents.js is `v=1`. Tests check for these specific version strings.
- Agent pages use `agents.js` (not `dashboard.js`). The `data-orchestration-page` attribute is `"agents"` for both list and detail pages.
- `dashboard.js` no longer contains any agent-init, agent-render, or agent-CRUD functions. Adding agent JS requires editing `agents.js`, not `dashboard.js`.

### Validation
- `mvn test`: 338 tests pass (0 failures, 0 errors) — 7 new tests added (agent HTMX partials and agents.js skeleton) plus constructor fix and assertion updates
- `timeout 30s mvn spring-boot:run`: Started Magenta2Application in 2.719 seconds on port 38895, Docker runtime ready
