# Operational UI Contract Refactor Playwright Targets

Append-only live-browser validation plan. Phase validators add concrete setup, routes, actions, assertions, expected API/network behavior, and known expected noise.

## Phase 01 — Contract Repair & Data Model

### P01-T1: Project create sends canonical fields
- **Route**: `/projects` → click "New Project"
- **Action**: Fill name, description, select owner agent, submit
- **Assert**: POST `/api/projects` body contains `name` and `description` (not `title`/`summary`)
- **Network**: 200 OK, response includes `id`, `name`, `description`, `ownerAgentId`
- **Expected noise**: None

### P01-T2: Project detail loads workspace without 404
- **Route**: `/projects/{id}` for a project created in P01-T1
- **Action**: Click the project link, verify workspace tab loads
- **Assert**: GET `/api/projects/{projectId}/workspace` returns 200 with `workspaceId`, `ownerAgentId`, `rootKind`, `displayPath`
- **Network**: No 404 or 500 on workspace fetch
- **Expected noise**: None

### P01-T3: Job create/save/edit lifecycle
- **Route**: `/jobs` → "New Job" → job detail page
- **Action**: Create job, edit title/summary/owner, save, reload page
- **Assert**:
  - POST `/api/jobs` succeeds with `{ownerAgentId, title, summary, status: "DRAFT", items: []}`
  - PUT `/api/jobs/{jobId}` saves changes and reload shows updated values
  - GET `/api/jobs/{jobId}` returns job with all fields
- **Network**: All return 200
- **Expected noise**: None

### P01-T4: Job items CRUD
- **Route**: Job detail page for a DRAFT job
- **Action**: Add PLAN item (provide plan ID), add WORKFLOW item (provide workflow ID)
- **Assert**:
  - POST `/api/jobs/{jobId}/items` with PLAN type includes `planId`
  - POST `/api/jobs/{jobId}/items` with WORKFLOW type includes `workflowId`
  - GET `/api/jobs/{jobId}/items` returns ordered items
- **Network**: 200 OK on all
- **Expected noise**: None

### P01-T5: Job events and outputs return arrays
- **Route**: Job detail page
- **Action**: Load page, inspect network tab
- **Assert**:
  - GET `/api/jobs/{jobId}/events` returns JSON array (not null, not 404)
  - GET `/api/jobs/{jobId}/outputs` returns JSON array (not null, not single map)
- **Network**: 200 OK (empty arrays acceptable for new jobs)
- **Expected noise**: May be empty `[]` for new jobs

### P01-T6: Outputs page queries unified endpoint
- **Route**: `/outputs`
- **Action**: Load page, try filter by agent/job/project
- **Assert**: GET `/api/outputs?agentId=X&jobId=Y&...` returns 200 with array
- **Network**: 200 OK, no 404
- **Expected noise**: May return empty `[]`

### P01-T7: Inbox renders no dead agent approval buttons
- **Route**: `/inbox`
- **Action**: Load page, inspect agent inbox rows
- **Assert**:
  - Agent inbox rows show "read"/"handled" actions only
  - No approve/reject buttons on agent inbox messages
  - User inbox (workflow approvals) shows approve/reject which calls POST `/api/users/inbox/{messageId}/respond`
- **Network**: GET `/api/users/inbox` returns 200; GET `/api/agents/{agentId}/inbox` returns 200
- **Expected noise**: None

### P01-T8: Agent detail queue tab loads assignments
- **Route**: `/agents/{agentId}` → click "queue" tab
- **Action**: Click the queue tab
- **Assert**: GET `/api/agents/{agentId}/assignments` is called (NOT `/api/agents/{agentId}/queue`)
- **Network**: 200 OK, returns assignment array
- **Expected noise**: None

### P01-T9: Dashboard summary endpoint exists
- **Route**: Direct API call (not yet consumed by UI)
- **Action**: Fetch `GET /api/dashboard/summary`
- **Assert**: 200 OK, response body contains `openProjects`, `activeWork`, `agents`, `userInbox`, `recentOutputs`, `stats`, `generatedAt`
- **Network**: 200 OK
- **Expected noise**: UI does not call this yet (Phase 02 will wire it)

### P01-T10: Plan page no longer has dead Run button
- **Route**: `/plans` → open a plan
- **Action**: Inspect the plan editor UI
- **Assert**: No "Run" button or run panel present (direct run controls removed per plan)
- **Network**: N/A
- **Expected noise**: May still have SSE stream endpoint but UI should not expose it as a direct run button

### P01-T11: Workflow page no longer has dead Run button
- **Route**: `/workflows` → open a workflow
- **Action**: Inspect the workflow editor UI
- **Assert**: No "Run" button or run panel present
- **Network**: N/A
- **Expected noise**: Same caveat as plans

## Phase 02 — Dashboard Information Architecture

### P02-T1: Dashboard is operational, not card launcher
- **Route**: `/dashboard`
- **Action**: Load page, inspect DOM
- **Assert**:
  - `[data-orchestration-page="dashboard"]` exists
  - `.dashboard-operational` is the root body class (not `.dashboard-landing`)
  - `.dashboard-chat-band` exists with disabled input
  - `.dashboard-status-strip` has 6 stat cards (Running, Pending, Waiting Approval, Failed, Active Agents, freshness)
  - `.dashboard-main-layout` exists with `.dashboard-primary` and `.dashboard-side`
  - No `.dashboard-summary-grid` or `.summaryCard` elements (old card launcher)
- **Network**: 200 OK
- **Expected noise**: None

### P02-T2: Dashboard JS fetches summary and populates stats
- **Route**: `/dashboard`
- **Action**: Wait for JS to execute, inspect DOM after load
- **Assert**:
  - GET `/api/dashboard/summary` returns 200
  - `#stat-running`, `#stat-pending`, etc. contain numeric values (not "—")
  - `#stat-freshness` shows relative time (e.g. "0s ago" or "1m ago")
- **Network**: `/api/dashboard/summary` 200 OK
- **Expected noise**: Stats may be 0 for fresh database

### P02-T3: Active Work table populates with job rows
- **Route**: `/dashboard` (with at least one job in the database)
- **Action**: Create a job via `/api/jobs`, then load `/dashboard`
- **Assert**:
  - `#active-work-table tbody` contains at least one row
  - Row links to `/jobs/{jobId}`
  - Row shows type badge, title, owner, status chip
- **Network**: `/api/dashboard/summary` returns activeWork with the job
- **Expected noise**: May be empty for fresh database (acceptable)

### P02-T4: Open Projects list populates
- **Route**: `/dashboard` (with at least one project)
- **Action**: Create a project, load `/dashboard`
- **Assert**:
  - `#open-projects-list` contains project cards
  - Each card links to `/projects/{projectId}`
  - Card shows owner and relative update time
- **Network**: `/api/dashboard/summary` returns openProjects
- **Expected noise**: Empty state shows "No open projects"

### P02-T5: Agents table populates
- **Route**: `/dashboard` (with at least one agent)
- **Action**: Load page, check agents table
- **Assert**:
  - `#agents-table tbody` contains at least one row
  - Each row links to `/agents/{agentId}`
  - Status chip rendered with appropriate color class
- **Network**: `/api/dashboard/summary` returns agents
- **Expected noise**: Queue/Inbox columns show "—" (not populated in summary)

### P02-T6: Side rail shows inbox and outputs
- **Route**: `/dashboard`
- **Action**: Load page, inspect side rail
- **Assert**:
  - `#side-inbox` shows waiting approvals count
  - `#side-outputs` shows recent outputs or empty state
  - "View all" links go to `/inbox` and `/outputs`
- **Network**: Data from `/api/dashboard/summary`
- **Expected noise**: Empty states acceptable for fresh databases

### P02-T7: /chat remains isolated
- **Route**: `/chat`
- **Action**: Load `/chat`, inspect DOM
- **Assert**:
  - `[data-chat-root="true"]` exists
  - `#chat-form`, `#chat-input`, `#chat-history` exist
  - No dashboard elements (`.dashboard-operational`, `.dashboard-status-strip`) leaked into chat
- **Network**: `/api/chat/sessions` returns 200
- **Expected noise**: Missing htmx webjar console error (known, pre-existing)

### P02-T8: Dashboard responsive at mobile width
- **Route**: `/dashboard`
- **Action**: Resize browser to 600px width, inspect layout
- **Assert**:
  - `.dashboard-main-layout` collapses to single column
  - `.dashboard-status-strip` stacks vertically
  - No text overflow or horizontal scroll
- **Network**: N/A
- **Expected noise**: None

### P02-T9: Dashboard empty state is professional
- **Route**: `/dashboard` (fresh database, no jobs/projects/runs)
- **Action**: Load page
- **Assert**:
  - "No active work" shown in active work table
  - "No open projects" shown in projects section
  - Stats show "0" not "null" or "undefined"
  - No console errors
- **Network**: HTMX partial endpoints return 200 with empty/placeholder content
- **Expected noise**: None

## Phase 02 HTMX — Dashboard HTMX Partial Loading (post-remediation)

These targets replace/supplement the pre-remediation P02-T1 through P02-T9 targets,
which assumed JS fetch of `/api/dashboard/summary` and `innerHTML` rendering.
After the HTMX remediation, dashboard sections load via server-rendered HTMX partials.

### P02-H1: Dashboard page shell has HTMX load attributes
- **Route**: `/dashboard`
- **Action**: Inspect page source (before any JS/HTMX execution)
- **Assert**:
  - `#dashboard-stats-container` has `hx-get="/dashboard/_stats"` and `hx-trigger="load, every 30s"`
  - `#active-work-section` has `hx-get="/dashboard/_active-work"` and `hx-trigger="load, every 30s"`
  - `#open-projects-section` has `hx-get="/dashboard/_open-projects"` and `hx-trigger="load, every 30s"`
  - `#agents-section` has `hx-get="/dashboard/_agents"` and `hx-trigger="load, every 30s"`
  - `#side-inbox` has `hx-get="/dashboard/_side-inbox"` and `hx-trigger="load, every 30s"`
  - `#side-outputs` has `hx-get="/dashboard/_side-outputs"` and `hx-trigger="load, every 30s"`
  - All containers use `hx-swap="innerHTML"`
- **Network**: N/A (shell render, no fetch required)
- **Expected noise**: None

### P02-H2: HTMX partial endpoints return valid HTML fragments
- **Route**: Direct fetch of each HTMX partial endpoint
- **Action**: Fetch each endpoint and inspect response
- **Assert**:
  - `GET /dashboard/_stats` returns HTML containing 5 `.dashboard-stat` elements with IDs `stat-running`, `stat-pending`, `stat-waiting`, `stat-failed`, `stat-agents`
  - `GET /dashboard/_active-work` returns HTML containing either a `.dashboard-table` (with rows) or `.dashboard-empty` with "No active work"
  - `GET /dashboard/_open-projects` returns HTML containing either `.dashboard-card-grid` or `.dashboard-empty` with "No open projects"
  - `GET /dashboard/_agents` returns HTML containing either a `.dashboard-table` (with rows) or `.dashboard-empty` with "No agents"
  - `GET /dashboard/_side-inbox` returns HTML containing `.dashboard-side-stat` with a numeric value
  - `GET /dashboard/_side-outputs` returns HTML containing either `.dashboard-side-item` elements or `.dashboard-empty` with "No recent outputs"
  - All responses have `Content-Type: text/html` (or are rendered as HTML)
- **Network**: All return 200 OK
- **Expected noise**: Stats may be 0 for fresh database

### P02-H3: Dashboard sections populate without JS rendering functions
- **Route**: `/dashboard` (with at least one job in database)
- **Action**: Load page, wait for HTMX swaps to complete, inspect DOM
- **Assert**:
  - Active Work table shows at least one row with job data (loaded via HTMX, not JS innerHTML)
  - Active Work rows contain `<a href="/jobs/{jobId}">` links
  - Open Projects cards contain `<a href="/projects/{projectId}">` links
  - Agent rows contain `<a href="/agents/{agentId}">` links
  - No call to `/api/dashboard/summary` from dashboard page (JS ticker only)
- **Network**: HTMX GETs to `/dashboard/_stats`, `/dashboard/_active-work`, `/dashboard/_open-projects`, `/dashboard/_agents`, `/dashboard/_side-inbox`, `/dashboard/_side-outputs`
- **Expected noise**: None

### P02-H4: Freshness ticker is JS-driven but stats are HTMX-driven
- **Route**: `/dashboard`
- **Action**: Load page, wait 30+ seconds, inspect DOM
- **Assert**:
  - `#stat-freshness` element exists and is updated by JS ticker (not via HTMX)
  - `#dashboard-stats-container` is refreshed via HTMX (stats may update independently)
  - No `renderDashboardStats`, `renderActiveWork`, `renderOpenProjects`, `renderAgents`, `renderSideInbox`, `renderSideOutputs` functions in dashboard.js
  - `initDashboardTicker` function exists in dashboard.js
  - `formatSince` helper exists in dashboard.js
- **Network**: Periodic HTMX GETs to `/dashboard/_stats` (every 30s), `/dashboard/_active-work` (every 30s), etc.
- **Expected noise**: Ticker updates to stat-freshness may lag behind actual data freshness

### P02-H5: Project list on /projects loads via HTMX
- **Route**: `/projects`
- **Action**: Inspect page source, wait for HTMX load
- **Assert**:
  - `#project-list` has `hx-get="/projects/_list"` and `hx-trigger="load"`
  - `GET /projects/_list` returns HTML fragment containing `.tool-item` anchor links
  - Each project link has `href="/projects/{projectId}"`
  - List loads without JS (projects.js only handles create/save/delete actions)
- **Network**: `GET /projects/_list` returns 200 with HTML
- **Expected noise**: None

### P02-H6: /chat remains isolated after HTMX remediation
- **Route**: `/chat`
- **Action**: Load page, inspect DOM and network
- **Assert**:
  - `[data-chat-root="true"]` exists
  - No HTMX attributes targeting `/dashboard/_*` endpoints
  - No `.dashboard-operational`, `.dashboard-status-strip`, `.dashboard-main-layout` classes
  - `/js/orchestration/dashboard.js` is NOT loaded on the chat page
  - Chat still loads its own `/js/chat-client.js`
  - The `/webjars/htmx.org/dist/htmx.min.js` compat resource is served (noop stub)
- **Network**: `/api/chat/sessions` returns 200
- **Expected noise**: Missing htmx webjar console error (known, pre-existing)

## Phase 03 -- Plan Editor And Worktype Profiles

### P03-T1: Plan page title is "Plans", not "Plans & Tasks"
- **Route**: `/plans`
- **Action**: Load page, inspect `<h1>` element
- **Assert**:
  - Page `<h1>` contains "Plans"
  - Page does NOT contain "Plans & Tasks"
  - `[data-orchestration-page="plans"]` exists on root element
- **Network**: 200 OK

### P03-T2: No Run button or Run panel on plan page
- **Route**: `/plans` -> open an existing plan from the list
- **Action**: Open a plan, inspect the editor panel
- **Assert**:
  - No button with text "Run"
  - No `run-plan` class or id anywhere
  - No `plan-run-agent-id` or `plan-run-log` elements
  - "Submit to Agent" button exists
  - "Continue in Chat" button exists
- **Network**: N/A (DOM inspection)

### P03-T3: Plan list shows only TASK_TEMPLATE plans (no SESSION_PLAN)
- **Route**: `/plans`
- **Action**: Load page, inspect the plan list sidebar
- **Assert**:
  - All listed plans are TASK_TEMPLATE (no SESSION_PLAN kind visible)
  - If no TASK_TEMPLATE plans exist, sidebar shows empty state ("No plans.")
- **Network**: HTMX GET `/plans/_list` returns 200

### P03-T4: Worktype dropdown replaces Prompt Profile text field
- **Route**: `/plans` -> open a plan or create new
- **Action**: Inspect the plan editor form
- **Assert**:
  - `<select name="workTypeProfile">` exists with options: Coding-centric, Data-centric, Research-centric
  - The label reads "Worktype" (not "Prompt Profile")
  - No `<input>` or `<textarea>` named "promptProfile" or "plan-prompt-profile"
- **Network**: N/A (DOM inspection)

### P03-T5: Advanced section hides Kind and Status
- **Route**: `/plans` -> open an existing plan
- **Action**: Inspect the editor below the action buttons
- **Assert**:
  - `<details>` element with `<summary>Advanced</summary>` exists
  - Contains read-only display of Kind and Status (not editable inputs)
  - Kind and Status are NOT visible at top level of the form
- **Network**: N/A (DOM inspection)

### P03-T6: Field editor has correct inputs (no example field)
- **Route**: `/plans` -> open an existing plan -> add an input or output field
- **Action**: Inspect the input/output field row
- **Assert**:
  - Field row has: name input, type select, required checkbox, array checkbox, description input, schema textarea
  - Field row has remove button (x)
  - Field row does NOT contain an "example" input
  - `<select>` for type includes PlanFieldType options (string, number, boolean, object, array)
- **Network**: HTMX POST `/plans/_editor/{planId}/inputs` returns 200

### P03-T7: Description field is textarea (not single-line input)
- **Route**: `/plans` -> open an existing plan -> inspect field row
- **Action**: Inspect the DOM for the description field in an input row
- **Assert**:
  - Description field renders as `<textarea>` (not `<input type="text">`)
  - Has at least 2 rows
  - Named like `inputsDesc0` for the first input
- **Network**: N/A (DOM inspection)
- **Note**: Currently uses TextInput (single-line). Verify fix or confirm divergence.

### P03-T8: List editors use ordered row editors (not newline textareas)
- **Route**: `/plans` -> open an existing plan
- **Action**: Inspect each list section (Deliverables, Steps, Validation Criteria, Assumptions)
- **Assert**:
  - Each section has individual rows (not a single textarea)
  - Each row has a remove button (x)
  - Each section has an "Add" button below it
  - Steps display order number and use PlanStep(order, text) semantics
- **Network**: N/A (DOM inspection)

### P03-T9: HTMX-driven plan creation
- **Route**: `/plans`
- **Action**: Click "New Plan", fill title, set worktype, click Save
- **Assert**:
  - POST `/plans/_editor` is issued (not `/api/plans` via JS fetch)
  - Response is HTML fragment that replaces `#plan-editor-container`
  - Created plan appears in the sidebar list (via HTMX swap)
  - No JS `fetch()` call in network tab for plan creation
- **Network**: POST `/plans/_editor` returns 200 with editor HTML

### P03-T10: HTMX-driven field add/remove
- **Route**: `/plans` -> open an existing plan
- **Action**: Click "Add input field", then click x to remove it
- **Assert**:
  - POST `/plans/_editor/{planId}/inputs` returns HTML fragment for the inputs section
  - DELETE `/plans/_editor/{planId}/inputs/0` removes the field and returns updated section
  - No JS fetch calls in network tab for field operations
- **Network**: Both endpoints return 200 with HTML fragments

### P03-T11: HTMX-driven list item add/remove
- **Route**: `/plans` -> open an existing plan
- **Action**: Click "Add deliverable", add text, then click x to remove
- **Assert**:
  - POST `/plans/_editor/{planId}/deliverables` returns HTML for the deliverables section
  - DELETE `/plans/_editor/{planId}/deliverables/0` removes item and returns updated section
  - Same pattern works for steps, validation, assumptions
  - No JS fetch calls for list operations
- **Network**: All endpoints return 200 with HTML fragments

### P03-T12: Submit to agent creates WorkAssignment
- **Route**: `/plans` -> open an existing plan -> click "Submit to Agent"
- **Action**: Select agent, set priority, click Submit
- **Assert**:
  - Submit form loads via HTMX GET `/plans/_submit-form/{planId}`
  - Form has agent select, model override input, priority input, required inputs (if any)
  - Submit triggers POST `/plans/_submit/{planId}` (HTMX, not JS fetch)
  - Response shows assignment ID, agent link, status, priority
  - Result does NOT show raw run logs or streaming output
- **Network**: GET `/plans/_submit-form/{planId}` and POST `/plans/_submit/{planId}` return 200 with HTML

### P03-T13: Continue in Chat generates correct prompt
- **Route**: `/plans` -> open an existing plan -> click "Continue in Chat"
- **Action**: Inspect the generated prompt fragment
- **Assert**:
  - Fragment loads via HTMX GET `/plans/_editor/{planId}/chat-prompt-fragment`
  - Contains "Continue in Chat" heading
  - Contains current plan state (title, goal, summary, deliverables, inputs, outputs, steps, etc.)
  - Contains exact instructions: "Grok the existing plan before asking questions"
  - Contains: "Continue questioning the user if the plan lacks context"
  - Contains: "Summarize and ask for guidance if the plan appears complete"
  - Has "Copy & Open Chat" button
- **Network**: GET `/plans/_editor/{planId}/chat-prompt-fragment` returns 200

### P03-T14: plans.js is minimal skeleton
- **Route**: Inspect source or network tab for plans.js
- **Action**: Load `/plans`, view plans.js content
- **Assert**:
  - File is ~12 lines (not >200 lines)
  - Contains `data-orchestration-page='plans'` listener only
  - Does NOT contain: `renderPlanEdit`, `renderRunInputForm`, `readFields`, `fieldRow`, `deliverableRow`, `save-plan`, `run-plan`, `new-plan`, `jsonFetch`, `fetch(`
  - Does NOT contain references to `/api/plans` via JS
- **Network**: `/js/orchestration/plans.js?v=2` returns 200

### P03-T15: HTMX partial endpoints count
- **Route**: Direct access or network tab inspection
- **Action**: Verify all plan HTMX endpoints are accessible
- **Assert**:
  - `GET /plans/_list` returns 200 with HTML
  - `GET /plans/_editor/_new` returns 200 with empty editor form
  - `GET /plans/_editor/{planId}` returns 200 with populated editor
  - `POST /plans/_editor` returns 200 with created plan editor
  - `PUT /plans/_editor/{planId}` returns 200 with updated editor
  - `POST /plans/_editor/{planId}/finalize` returns 200
  - Inputs/outputs add/remove (4 endpoints) return 200
  - List section add/remove (8 endpoints) return 200
  - `GET /plans/_submit-form/{planId}` returns 200
  - `POST /plans/_submit/{planId}` returns 200
  - `GET /plans/_editor/{planId}/chat-prompt-fragment` returns 200
  - Total: 21 plan HTMX endpoints exist and work
- **Network**: All endpoints return Content-Type: text/html or render as HTML

### P03-T16: Plan API endpoints still functional (REST contract preserved)
- **Route**: Direct API calls
- **Action**: Test the `/api/plans` REST endpoints
- **Assert**:
  - `GET /api/plans` returns JSON array of plans
  - `POST /api/plans` with `workTypeProfile: "CODING_CENTRIC"` creates plan correctly
  - `POST /api/plans` with legacy `promptProfile: "CODING"` maps to CODING_CENTRIC
  - `GET /api/plans/{planId}/chat-prompt` returns `{prompt: "..."}` with correct instructions
  - `POST /api/plans/{planId}/submit` returns WorkAssignment JSON
- **Network**: All return 200 OK or 400 for validation errors

### P03-T17: Server validation: duplicate field names
- **Route**: `/api/plans` POST with duplicate input names
- **Action**: Send plan create with inputs: `[{name: "src", ...}, {name: "src", ...}]`
- **Assert**: Returns 400 Bad Request with error mentioning duplicate input name
- **Network**: 400

### P03-T18: Server validation: missing title
- **Route**: `/api/plans` POST with empty title
- **Action**: Send plan create with `title: ""`
- **Assert**: Returns 400 Bad Request with error mentioning title is required
- **Network**: 400

### P03-T19: Worktype profile preserved through save/load
- **Route**: `/api/plans`
- **Action**: Create plan with `workTypeProfile: "RESEARCH_CENTRIC"`, then GET that plan
- **Assert**:
  - Created plan shows `promptProfile: "RESEARCH_CENTRIC"` in response
  - Reloaded plan shows same value
- **Network**: POST and GET both return 200

### P03-T20: Schema JSON accepted and round-tripped
- **Route**: `/api/plans` or plan editor
- **Action**: Create plan with input field having `schema: "{\"type\": \"object\"}"`
- **Assert**: Schema value is preserved in GET response (exact string match)
- **Network**: 200 OK
- **Note**: Schema JSON is NOT validated server-side (known divergence)

## Phase 04 -- Workflow Builder Redesign

**Setup data needed for all P04 targets:**
1. At least two finalized TASK_TEMPLATE plans with inputs and outputs defined (e.g., Plan A: outputs `["result_text"]`, Plan B: inputs `["source_text"]`, outputs `["summary"]`)
2. At least one active agent to submit workflows to
3. A test workflow persisted via HTMX (created in P04-T1)

### P04-T1: Create workflow with two task nodes and one route via HTMX
- **Route**: `/workflows`
- **Action**:
  1. Click "New Workflow"
  2. Enter title "Fan-out Test", summary "Testing multi-route workflows"
  3. Click "Save" -- workflow is created, editor shows node/route sections
  4. In add-node form: select "task" type, pick Plan A from plan dropdown, click "Add"
  5. In add-node form: select "task" type, pick Plan B from plan dropdown, click "Add"
  6. In add-route form: select from "node_1", output name "result_text", route type "map_output", to "node_2", input name "source_text", click "Add Route"
- **Assert**:
  - POST `/workflows/_editor` creates the workflow (201/200)
  - POST `/workflows/_editor/{id}/nodes` adds each node (200, HTML fragment)
  - POST `/workflows/_editor/{id}/routes` adds the route (200, HTML fragment)
  - Nodes section shows 2 nodes with type badges and out-counts
  - Routes section shows 1 route with from/to description
  - No Run button visible
- **Network**: All HTMX endpoints return 200; no `/api/workflows` calls from JS
- **Expected noise**: None
- **VERIFIED (was BLOCKING, now fixed in remediation)**: The `hx-include="closest .field-row"` selector correctly includes form values from the containing `.field-row` div. Node/route creation should succeed. See re-validation report for BLOCKING-02.

### P04-T2: Add fan-out route (one source to two destinations)
- **Route**: `/workflows` after P04-T1
- **Action**:
  1. Add a third node (node_3) for Plan A again (as a second consumer)
  2. Add a second route: from "node_1", output "result_text", map_output, to "node_3", input "(same)"
- **Assert**:
  - Two routes visible in routes section, both from node_1
  - node_1 shows out-count of 2
  - Both node_2 and node_3 have incoming routes from the same source
- **Network**: HTMX POST endpoints return 200
- **Expected noise**: None

### P04-T3: Add log route
- **Route**: `/workflows` after P04-T2
- **Action**: Add route: from "node_2", output "summary", type "log", to "node_2" (self), input "(any)"
- **Assert**:
  - Log route appears in routes section with "log" type badge
  - Route count increments
- **Network**: HTMX POST `/workflows/_editor/{id}/routes` returns 200
- **Expected noise**: Log route to self is legal in the data model but validation may warn about it

### P04-T4: Validation errors appear inline
- **Route**: `/workflows` after loading a workflow
- **Action**:
  1. Click "Validate" button
  2. If no errors/warnings, create a node without planId or a route referencing nonexistent nodes
  3. Click "Validate" again
- **Assert**:
  - GET `/workflows/_editor/{id}/validate` returns HTML fragment
  - Error items have red background styling (`background:#fff1f1`)
  - Warning items appear with standard warning styling
  - If no issues, green "Valid: no errors found." message appears
- **Network**: HTMX GET returns 200 with HTML
- **Expected noise**: May show type compatibility warnings for plans without matching input/output types

### P04-T5: Route/node persistence through HTMX
- **Route**: `/workflows`
- **Action**:
  1. Load an existing workflow from the list by clicking its name
  2. Verify all previously saved nodes and routes are displayed
  3. Click "Save" to trigger PUT
- **Assert**:
  - GET `/workflows/_editor/{id}` returns populated editor
  - All nodes show with their type badges, plan chips, and out-counts
  - All routes show with from→to descriptions and type badges
  - PUT `/workflows/_editor/{id}` returns 200
- **Network**: HTMX GET and PUT return 200; no JS fetch calls
- **Expected noise**: None

### P04-T6: Submit-to-agent creates assignment
- **Route**: `/workflows` on a saved workflow editor
- **Action**:
  1. Click "Submit to Agent" button
  2. Verify submit form panel appears with agent select, model override, priority, workspace ID
  3. Select an agent, set priority to 50, click "Submit"
- **Assert**:
  - GET `/workflows/_submit-form/{id}` loads the submit panel (200, HTML)
  - POST `/workflows/_submit/{id}` submits the form (200, HTML)
  - Response shows assignment ID, agent link, status (QUEUED), priority (50)
  - Response shows workflow title
  - No raw run logs or streaming output visible
  - Result shows "View agent status" link
- **Network**: Both endpoints return 200 with HTML fragments
- **Expected noise**: None

### P04-T7: No direct run controls visible
- **Route**: `/workflows` and `/workflows` with an open editor
- **Action**: Inspect the DOM for any run-related elements
- **Assert**:
  - No button with text "Run"
  - No `run-workflow` class or id
  - No `workflow-run-agent-id` or `workflow-run-log` elements
  - No SSE streaming UI elements in the workflow page
  - "Submit to Agent" button is the only execution path
  - `workflows.js` does not contain any run-related function names
- **Network**: Page source contains no run-related data attributes or endpoints
- **Expected noise**: None

### P04-T8: Graph validator rejects cycles
- **Route**: `/workflows` -- test via HTMX or direct API
- **Action**:
  1. Create a workflow with two nodes (node_1 TASK, node_2 TASK)
  2. Add route node_1→node_2 (map_output)
  3. Add route node_2→node_1 (map_output) to create a cycle
  4. Click "Validate"
- **Assert**:
  - GET `/workflows/_editor/{id}/validate` returns HTML containing "ERROR: Workflow contains a cycle"
  - Error item is styled red
  - Other validations (type compatibility) may also appear
- **Network**: HTMX GET returns 200 with validation results
- **Expected noise**: May also show warnings about type compatibility

### P04-T9: Type compatibility validation
- **Route**: `/workflows` -- test via HTMX or direct API
- **Action**:
  1. Create/use a workflow with a TASK node that outputs STRING
  2. Add another TASK node that expects NUMBER input
  3. Route the STRING output to the NUMBER input as map_output
  4. Click "Validate"
- **Assert**:
  - GET `/workflows/_editor/{id}/validate` returns a WARNING about type mismatch
  - Warning mentions source type (string), destination type (number), and the node/output names involved
  - Warning is a non-blocking warning (not an ERROR)
- **Network**: HTMX GET returns 200 with validation results
- **Expected noise**: May require plans with explicitly typed fields to trigger type mismatch

### P04-T10: Workflow list renders via HTMX with node/route counts
- **Route**: `/workflows`
- **Action**:
  1. Load page, observe the sidebar list after HTMX swap
  2. Check filter behavior by typing in the filter input
- **Assert**:
  - GET `/workflows/_list` returns HTML fragment with `.tool-item` buttons
  - Each item shows title, node count, and route count (e.g., "3 nodes, 2 routes")
  - Clicking an item loads the editor via HTMX GET to `/workflows/_editor/{id}`
  - Filter input triggers HTMX GET with `workflowFilter` param after 300ms debounce
- **Network**: HTMX GET `/workflows/_list` returns 200; filter triggers additional GETs
- **Expected noise**: Empty state shows "No workflows."

### P04-T11: REST API endpoints still functional for workflows
- **Route**: `/api/workflows` (JSON API)
- **Action**:
  1. GET `/api/workflows` -- returns JSON array
  2. GET `/api/workflows/{workflowId}` -- returns single definition
  3. POST `/api/workflows` with `{title, summary, nodes, routes}` body
  4. PUT `/api/workflows/{workflowId}` to update
  5. POST `/api/workflows/{workflowId}/validate` to validate
- **Assert**:
  - GET returns 200 with workflow definitions
  - POST/PUT return 200
  - **VERIFIED (was BLOCKING, now fixed in remediation)**: POST `/api/workflows` and PUT `/api/workflows/{id}` now correctly pass `definition.routes()` into the `WorkflowDefinition` constructor. Routes sent in POST/PUT will be persisted. See re-validation report for BLOCKING-01.
  - `DELETE /api/workflows/{workflowId}` returns 200
  - `GET /api/workflow-runs/{runId}` returns 200 or 404
  - `POST /api/workflow-runs/{runId}/resume` returns 200
- **Network**: All endpoints return proper HTTP status codes
- **Expected noise**: Routes field will be empty in newly created/updated workflows via REST API


## Phase 04 Re-Validation (2026-05-12) -- All Blockers Resolved

All four blocking findings from the initial Phase 04 validation have been remediated and verified:

1. **BLOCKING-01 (WorkflowController routes)** -- FIXED and VERIFIED: `create()` (line 49-50), `update()` (line 62-65), and `validate()` (line 83-85) all pass `definition.routes()` to the `WorkflowDefinition` constructor. REST API now correctly persists routes.

2. **BLOCKING-02 (hx-include selectors)** -- FIXED and VERIFIED: `addNodeForm()` (line 1832) and `addRouteForm()` (line 1866) both use `hxInclude("closest .field-row")` matching the proven pattern used elsewhere. Node and route form submissions will correctly capture field values.

3. **BLOCKING-03 (schema.sql routes_json column)** -- FIXED and VERIFIED: `workflow_definitions` table includes `routes_json text not null default '[]'` (schema.sql line 160). Fresh deployments will have the column without needing runtime migration.

4. **BLOCKING-04 (node key collision)** -- FIXED and VERIFIED: `addWorkflowNode()` (lines 1540-1545) computes the max existing numeric suffix and increments, eliminating collisions after node deletions.

**Validation results:**
- `mvn test`: 330 tests, 0 failures, 0 errors
- `timeout 30s mvn spring-boot:run`: Started Magenta2Application in 2.742 seconds
- All four exit criteria met (see re-validation report)

**Existing P04 Playwright targets (P04-T1 through P04-T11) remain fully applicable.** The stale BLOCKING annotations on P04-T1 and P04-T11 have been updated to reflect remediation. No new browser targets are required for this re-validation.

## Phase 05 -- Jobs And Projects Operational Surfaces

**Setup data needed for all P05 targets:**
1. At least one active agent (created via `/agents`)
2. At least one finalized TASK_TEMPLATE plan
3. At least one workflow definition

### P05-T1: Create project via HTMX form
- **Route**: `/projects`
- **Action**:
  1. Load `/projects`, verify shell renders with sidebar and empty editor
  2. Click "New Project"
  3. Fill Name, Description, Owner Agent ID, Git Repo URL
  4. Select Worktype from dropdown
  5. Click "Save"
- **Assert**:
  - POST `/projects/_editor` is issued (HTMX, not JS fetch)
  - Response is HTML fragment that replaces `#project-editor-container`
  - Created project shows canonical `name` and `description` fields (not `title`/`summary`)
  - Created project appears in sidebar list (`.tool-item` button with name)
  - No JS `fetch()` call for project creation in network tab
- **Network**: POST `/projects/_editor` returns 200 with editor HTML
- **Expected noise**: Owner Agent ID is a text input (not a populated dropdown in current implementation)

### P05-T2: Edit project metadata
- **Route**: `/projects` with project created in P05-T1
- **Action**:
  1. Click the project in the sidebar list
  2. Change Name, Description, Git Repo URL
  3. Click "Save"
- **Assert**:
  - PUT `/projects/_editor/{projectId}` is issued (HTMX)
  - Response replaces `#project-editor-container` with updated values
  - Workspace section shows owner, kind, path, member count
  - Agents section loads via HTMX (`/projects/_detail/{projectId}/agents`)
  - Active Jobs section loads via HTMX (`/projects/_detail/{projectId}/jobs`)
  - Recent Outputs section loads via HTMX (`/projects/_detail/{projectId}/outputs`)
  - Delete button is visible
- **Network**: GET/PUT return 200; detail sections load via HTMX on page load
- **Expected noise**: Agents/jobs/outputs may be empty for fresh projects

### P05-T3: Create DRAFT job via HTMX
- **Route**: `/jobs`
- **Action**:
  1. Load `/jobs`, verify shell renders with sidebar, agent filter, and empty editor
  2. Click "New Job"
  3. Fill Title (required), Summary, Owner Agent ID, Project ID, Worktype
  4. Click "Save"
- **Assert**:
  - POST `/jobs/_editor` is issued (HTMX)
  - Response replaces `#job-editor-container` with populated editor
  - Job shows with Title, Status=DRAFT, items section (empty), and action buttons
  - "Save", "Submit to Agent", "Delete" buttons visible
  - No "Run" button present
  - Advanced section shows ID, Status, Workspace ID, Created timestamp
  - Outputs and Events panels load via HTMX (may be empty)
- **Network**: POST `/jobs/_editor` returns 200; events/outputs panels load via HTMX GET
- **Expected noise**: No items defined initially; events/outputs may be empty

### P05-T4: Add PLAN item to job
- **Route**: `/jobs` with job created in P05-T3
- **Action**:
  1. In the job editor, fill the inline add-item form fields
  2. Enter an item key, select type=PLAN, enter a planId
  3. Click "Add Item"
- **Assert**:
  - POST `/jobs/_editor/{jobId}/items` is issued with `hx-include="#job-items-new-form"`
  - Request body includes `key`, `itemType=PLAN`, `planId`
  - Response replaces the full job editor with updated items list
  - New item appears in the "Ordered Items" section with type=PLAN badge
  - Item shows sequence number, planId reference, model override, priority
  - Each item has a remove button (x) that issues DELETE item endpoint
- **Network**: POST `/jobs/_editor/{jobId}/items` returns 200
- **Expected noise**: If planId is invalid (nonexistent plan), server-side validation may reject

### P05-T5: Add WORKFLOW item to job
- **Route**: `/jobs` with job from P05-T4
- **Action**:
  1. In the inline add-item form, select type=WORKFLOW
  2. Enter an item key and a workflowId
  3. Click "Add Item"
- **Assert**:
  - POST `/jobs/_editor/{jobId}/items` is issued
  - Request body includes `itemType=WORKFLOW`, `workflowId`
  - New item appears with type=WORKFLOW badge and workflowId reference
  - Items list shows both PLAN and WORKFLOW items in sequence
- **Network**: POST returns 200
- **Expected noise**: If workflowId is invalid, server-side validation may reject

### P05-T6: Submit job to agent
- **Route**: `/jobs` with job from P05-T5
- **Action**:
  1. Click "Submit to Agent" button
  2. Verify submit form panel appears with agent select, model override, priority
  3. Select an agent, set priority, click "Submit"
- **Assert**:
  - GET `/jobs/_submit-form/{jobId}` loads the submit panel (200, HTML)
  - POST `/jobs/_submit/{jobId}` submits the form (200, HTML)
  - Response shows assignment ID, agent link, status, priority
  - Response does NOT show raw run logs or streaming output
  - No "Run" terminology in the submit flow
- **Network**: GET and POST return 200 with HTML fragments
- **Expected noise**: Ensure legacy OrchestrationJob bridge has no visible side effects

### P05-T7: Project detail loads jobs/agents/outputs
- **Route**: `/projects` with project from P05-T1 and job from P05-T3 associated
- **Action**:
  1. Load the project editor
  2. Wait for HTMX sections to load
  3. Inspect agents, jobs, and outputs sections
- **Assert**:
  - Workspace section renders inline with owner, kind, path, member count
  - Agents section loads via HTMX GET `/projects/_detail/{projectId}/agents`
  - Jobs section loads via HTMX GET `/projects/_detail/{projectId}/jobs`
  - Outputs section loads via HTMX GET `/projects/_detail/{projectId}/outputs`
  - Job items link to the job editor (hx-get to `/jobs/_editor/{jobId}`)
  - Empty states show professional messages (e.g., "No active jobs.")
- **Network**: All HTMX partial endpoints return 200
- **Expected noise**: Sections may show empty placeholder content

### P05-T8: No Run button on jobs page
- **Route**: `/jobs` with any job loaded in the editor
- **Action**: Inspect the DOM for any run-related elements
- **Assert**:
  - No button with text "Run"
  - No `run-job` class or id
  - No `data-action="run-job"` attribute
  - No SSE streaming UI elements in the job page
  - "Submit to Agent" is the only execution path
  - dashboard.js does not contain `initJobs` or `initJobDetail` functions
- **Network**: Page source contains no run-related data attributes or endpoints
- **Expected noise**: None

### P05-T9: Job editor preserves items after save
- **Route**: `/jobs` with job from P05-T6
- **Action**:
  1. Load the job in the editor (items should be visible)
  2. Edit the Title or Summary
  3. Click "Save"
  4. Verify items are still present after the save response
- **Assert**:
  - PUT `/jobs/_editor/{jobId}` is issued
  - Response contains the full editor with all items preserved
  - Items list shows the same items in the same order as before save
  - No items lost or reordered unexpectedly
- **Network**: PUT returns 200 with complete editor HTML
- **Expected noise**: `saveDefinition` re-reads the job from the service, ensuring fresh state

## Phase 06 -- Agent Dashboard Docker Runtime Visibility

**Setup data needed for all P06 targets:**
1. At least one active agent (created via `/agents`)
2. Docker daemon running (or disabled -- test both paths)
3. At least one plan, workflow, and job to submit to the agent

### P06-T1: Agents list loads as table via HTMX
- **Route**: `/agents`
- **Action**:
  1. Load `/agents`, verify shell renders with sidebar and empty detail panel
  2. Wait for HTMX to load the agent list
  3. Inspect the agent list table
- **Assert**:
  - `#agent-list` contains `#agents-list-table` with `dashboard-table` class
  - Table columns: Name, Status, Model, Queue, Inbox, Jobs
  - Each agent name is an `<a>` link with `hx-get="/agents/_detail/{agentId}"`
  - No `data-action` attributes on agent rows (no JS-driven CRUD)
  - Filter input triggers HTMX GET with delay on keyup
  - "Create Agent" button issues `hx-post="/agents/_create"`
- **Network**: HTMX GET `/agents/_list` returns 200 with HTML table
- **Expected noise**: Empty table for fresh database

### P06-T2: Agent detail dashboard shows Docker status
- **Route**: `/agents` -> click an agent name in the list
- **Action**:
  1. Click agent name in the left list
  2. Wait for HTMX to load the detail fragment
  3. Inspect the dashboard tab content
- **Assert**:
  - Agent name, status, model, ID, Direct Line status, created time displayed
  - Counter cards for Queue, Inbox, Jobs (clickable, load tab content)
  - `#agent-docker-status-{agentId}` div present with `hx-get="/api/runtime/docker/status"`
  - Docker status shows enabled/available/host/image/message fields
  - "Chat with Agent" link present pointing to `/chat?agent={agentId}`
  - No raw JSON blocks visible
- **Network**: GET `/agents/_detail/{agentId}` (HTML), GET `/api/runtime/docker/status` (JSON)
- **Expected noise**: Docker may show disabled if `magenta.docker.enabled=false`

### P06-T3: Agent tabs load content via HTMX
- **Route**: `/agents/{agentId}` detail page
- **Action**:
  1. Load agent detail page
  2. Click each tab: Queue, Inbox, Jobs, Workspace, Outputs, History
  3. Verify content loads for each
- **Assert**:
  - Queue: shows assignment table (or "No assignments" placeholder)
  - Inbox: shows message table with Type/From/Read/Handled/Created columns (or "No inbox messages")
  - Jobs: shows jobs table with Title/Status/Project/Updated columns (or "No jobs")
  - Workspace: shows agent metadata and API endpoint reference (placeholder)
  - Outputs: shows recent outputs table (or "No recent outputs")
  - History: shows placeholder text with link to queue
  - All tabs load via HTMX GET (no full page navigation)
  - No raw JSON in any tab content
- **Network**: Each tab click issues HTMX GET to `/agents/_detail/{agentId}/{tab}` returning 200 with HTML
- **Expected noise**: Empty placeholders for fresh agents without assignments/jobs/outputs

### P06-T4: Profile editor sections expand and save
- **Route**: `/agents/{agentId}` -> inspect the "Profile" side panel
- **Action**:
  1. Wait for HTMX to load the editor fragment
  2. Identity section: change name, status, model, direct line toggle
  3. Click "Save" on the Identity section
  4. Verify the section re-renders with updated values
- **Assert**:
  - Editor loads via HTMX GET `/agents/_editor/{agentId}`
  - Identity section has: name, status (ACTIVE/DISABLED), Default Model, Direct Line (Enabled/Disabled)
  - Save issues HTMX PUT `/agents/_editor/{agentId}/profile`
  - Response replaces the form with updated readout showing "Updated: X ago"
  - System Prompt section loads and saves independently
  - No JS fetch calls for profile editing
- **Network**: GET `/agents/_editor/{agentId}`, PUT `/agents/_editor/{agentId}/profile` return 200 with HTML
- **Expected noise**: Direct Line toggle uses select (not checkbox) per current implementation

### P06-T5: Tools editor uses text input (comma-separated)
- **Route**: `/agents/{agentId}` -> Profile side panel -> Approved Tools section
- **Action**:
  1. Scroll to "Approved Tools" section in the editor
  2. Edit the comma-separated tool list
  3. Click "Save"
  4. Verify tool count chip updates
- **Assert**:
  - Section labeled "Approved Tools"
  - Text input with placeholder "tool1, tool2, ..."
  - Helper text: "Comma-separated list. Use * for all available tools."
  - Tool count chip shows "N tools configured" or "No tools configured"
  - Save issues HTMX PUT `/agents/_editor/{agentId}/tools`
  - Tools saved as arrays in the data model (not raw CSV in DB)
- **Network**: PUT returns 200 with updated section HTML
- **Expected noise**: Uses comma-separated text input, not checkboxes from ChatToolRegistry (known divergence from plan -- documented in handoff notes)

### P06-T6: Shell allowlist uses text input (comma-separated)
- **Route**: `/agents/{agentId}` -> Profile side panel -> Shell Allowlist section
- **Action**:
  1. Scroll to "Shell Allowlist" section
  2. Edit the comma-separated command list
  3. Click "Save"
- **Assert**:
  - Section labeled "Shell Allowlist"
  - Text input with placeholder "ls, cat, grep, ..."
  - Helper text: "Comma-separated list. Use * for all commands. Bare executable names only."
  - Save issues HTMX PUT `/agents/_editor/{agentId}/shell`
  - Commands saved as arrays in the data model
- **Network**: PUT returns 200 with updated section HTML
- **Expected noise**: Uses comma-separated text input, not row editor (known divergence from plan -- documented in handoff notes)

### P06-T7: Submit work form has structured fields
- **Route**: `/agents/{agentId}` -> "Submit Work" side panel
- **Action**:
  1. Wait for HTMX to load the submit form
  2. Inspect form fields
  3. Select TASK_RUN, enter a plan ID, set priority to 5
  4. Click "Submit"
- **Assert**:
  - Form loads via HTMX GET `/agents/_submit-form/{agentId}`
  - Assignment Type: select with TASK_RUN, WORKFLOW_RUN, JOB_RUN options
  - Plan/Workflow/Job ID: text input with placeholder
  - Priority: number input (min=0, max=9)
  - Model Override: text input (optional)
  - Submit issues HTMX POST `/agents/_submit/{agentId}`
  - Result shows: assignment ID, type, status, priority, "View queue" link
  - No raw JSON textarea for assignment input
- **Network**: GET `/agents/_submit-form/{agentId}`, POST `/agents/_submit/{agentId}` return 200 with HTML
- **Expected noise**: Target ID is a free-text input (not a dropdown populated with available plans/workflows/jobs)

### P06-T8: No raw JSON in normal agent UI
- **Route**: `/agents` and `/agents/{agentId}`
- **Action**:
  1. Load agents list page
  2. Click into an agent detail page
  3. Inspect all tabs (Dashboard through History)
  4. Inspect Profile editor
  5. Inspect Submit Work form
- **Assert**:
  - No `<pre>` blocks containing JSON
  - No `<code>` blocks with raw JSON except workspace API reference
  - No `JSON.stringify` output in any rendered page
  - Agent identity displayed as structured form fields, not JSON dump
  - Tab content rendered as HTML tables, not JSON arrays
- **Network**: All responses are HTML fragments (Content-Type: text/html or HTML rendered inline)
- **Expected noise**: Workspace tab shows API endpoint reference in `<code>` (acceptable per plan: "Avoid raw JSON except in an advanced debug details panel")

### P06-T9: Agent side-panel chat remains functional
- **Route**: `/chat?agent={agentId}` (via "Chat with Agent" button)
- **Action**:
  1. Click "Chat with Agent" on agent detail page
  2. Verify chat page loads with agent context
  3. Send a message and verify response
- **Assert**:
  - `/chat` page loads with `?agent={agentId}` query parameter
  - `[data-chat-root="true"]` exists on chat page
  - Chat form and history elements present
  - SSE stream endpoint `/api/agents/{agentId}/chat/stream` is called
  - Agent context is passed to chat (page context knows which agent)
- **Network**: GET `/chat` returns 200; POST `/api/agents/{agentId}/chat/stream` returns SSE stream
- **Expected noise**: None

### P06-T10: Docker status endpoint graceful when Docker disabled
- **Route**: `/api/runtime/docker/status` (direct API call)
- **Action**:
  1. Fetch `GET /api/runtime/docker/status` directly
  2. Inspect JSON response
- **Assert**:
  - Response contains fields: `enabled`, `available`, `dockerHost`, `agentImage`, `message`, `checkedAt`
  - If Docker disabled: `enabled=false`, `available=false`, `message` explains why
  - If Docker enabled: `enabled=true`, pings daemon for `available`
  - `checkedAt` is a valid ISO-8601 timestamp
  - Response is valid JSON
- **Network**: 200 OK (never 500 for disabled Docker)
- **Expected noise**: Docker may be disabled in test environment
