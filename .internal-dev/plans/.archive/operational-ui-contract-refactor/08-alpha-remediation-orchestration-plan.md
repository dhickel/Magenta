# Operational UI Contract Alpha Remediation Orchestration Plan

## Context

The operational UI contract refactor is not ready to archive. The review set from 2026-05-12 found that the remaining risk is concentrated in runtime/browser behavior and contract mismatches, not in the headline test count.

This plan is written for an orchestrating implementation agent. The orchestrator should split work across subagents where write targets do not overlap, force each subagent to turn its assignment into a fully specified implementation plan before coding, integrate the branches carefully, and then run a central validation gate that proves all blockers are resolved together.

Source review documents:

- `.internal-dev/reviews/2026-05-12-operational-ui-contract-test-coverage-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-backend-contract-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-ui-htmx-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-beta-readiness-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-final-validation-plan.md`
- `.internal-dev/bugs/operational-ui-contract-beta-blockers/report.md`

Locked user decisions for this remediation:

- This is a breaking refactor. Drop legacy behavior instead of preserving compatibility paths that conflict with the new operational contracts.
- `JOB_RUN` must execute the public `JobDefinition` and `JobWorkItem` data users edit. Do not deepen the legacy `OrchestrationJob` bridge.
- Use HTMX by default for SimplyPages UI interactions, but use JavaScript when it is genuinely the path of least resistance. Do not shoehorn HTMX into state-heavy interactions where it creates a worse design.
- Any missing or intentionally deferred functionality discovered during implementation must be documented in `.internal-dev/notes/operational-ui-contract-missing-features.md`.
- The orchestrator must launch a dedicated subagent to review the whole UI contract surface for missing functionality across the project.
- Podman should work out of the box for local alpha validation. Docker-compatible runtime behavior must be explicit and validated.

## Goal

Bring the operational UI contract refactor to alpha readiness by fixing the blocker-level contract mismatches, proving the UI works in a real browser, and replacing source-string confidence with end-to-end operational evidence.

The completed remediation must ensure that users can create/edit plans, workflows, jobs, projects, agents, inbox items, and outputs through live UI flows without inert controls, dead routes, misleading edit affordances, or runtime paths that execute different data from what the user configured.

## In Scope

- Remove or bypass legacy job execution behavior that conflicts with the canonical `JobDefinition`/`JobWorkItem` model.
- Repair `JOB_RUN` assignment validation and execution so canonical job items are the runtime items.
- Repair plan structured editor persistence or remove controls that imply unsupported editing.
- Enforce workflow graph validation as a durable save gate and expose structured validation results.
- Repair HTMX browser delivery and visible UI wiring issues.
- Decide and document HTMX vs JavaScript surface by surface, with implementation matching that decision.
- Repair Docker/Podman runtime defaults and status rendering for alpha-local use.
- Add database-backed workspace write lease exclusivity.
- Delete or quarantine stale public orchestration JavaScript that contradicts the new contract.
- Add tests for every blocker and integrate them into the full validation gate.
- Create `.internal-dev/notes/operational-ui-contract-missing-features.md` for missing functionality discovered during the implementation or UI surface audit.
- Update `.internal-dev` changelog/knowledge/review artifacts after implementation and validation.

## Out of Scope

- Preserving backward compatibility with legacy orchestration job APIs or tables where they conflict with the new public job model.
- Broad visual redesign beyond what is needed to make existing operational flows correct and usable.
- Replacing `/chat` or mixing operational UI scripts into `/chat`.
- Full production deployment hardening beyond making Podman/Docker-compatible local alpha behavior explicit and validated.
- Broad `OrchestrationController` decomposition as an architecture cleanup goal. Extract narrow services/renderers only when needed to fix blocker behavior cleanly.

## Architecture Decisions

### Canonical Job Runtime

`JobDefinition` is the canonical job contract. `JOB_RUN` assignments must reference a `JobDefinition.id()` and execute its ordered `JobWorkItem` list. The runtime should not create a shadow `OrchestrationJob` to satisfy legacy validation.

Required direction:

- Update assignment creation/validation so `AssignmentType.JOB_RUN` validates through `JobService.getDefinition(jobId)`.
- Update `OrchestrationRunnerService` so `JOB_RUN` loads the canonical job definition and executes each `JobWorkItem` in order.
- Remove or stop calling `ensureLegacyJob(...)`.
- Retire legacy job compatibility methods/routes only when direct callers have been updated or confirmed unused.
- If a legacy table remains in `schema.sql` for historical data, it must not be the alpha runtime path for public UI-created jobs.

### HTMX vs JavaScript Policy

The default transport for SimplyPages CRUD, filtering, row actions, form submissions, tab/panel swaps, and partial refreshes is HTMX. JavaScript is acceptable when it is the simpler and safer path for a genuinely state-heavy interaction.

Surface decisions for this remediation:

- `/dashboard`: HTMX for summary/active work/recent output refreshes.
- `/plans`: HTMX for scalar fields, structured row add/update/delete, submit-to-agent, and fragment refreshes.
- `/workflows`: JavaScript may be used for graph/canvas/tree editing if the implementation needs client-side graph state, but save/validate endpoints must use stable structured APIs and browser validation must justify JS as the path of least resistance. Simple list/detail actions should remain HTMX.
- `/jobs`: HTMX for job definition CRUD, item editing, output filters, and submit-to-agent.
- `/projects`: HTMX for project CRUD, owner/member changes, network gating, workspace/output panels.
- `/agents/{agentId}`: HTMX for tabs and server-rendered tab content.
- `/inbox`: The implementing agent must choose based on code reality. Prefer HTMX for list filtering and approval/read/handled actions if server fragments are straightforward. Keep JavaScript only if the UX requires richer client-side state, and document the justification in implementation notes and final validation.
- `/outputs`: The implementing agent must choose based on code reality. Prefer HTMX for browse/filter/detail if server fragments are straightforward. Keep JavaScript only if client-side browsing is materially simpler, and document the justification in implementation notes and final validation.
- `/chat`: unchanged and isolated.

### Missing Functionality Tracking

If a subagent finds a mocked path, logical deficiency, route stub, no-op affordance, or missing feature that is outside its assigned fix but relevant to alpha readiness, it must append an entry to `.internal-dev/notes/operational-ui-contract-missing-features.md`.

Each entry must include:

- `Surface`
- `Missing behavior`
- `User impact`
- `Evidence`
- `Why out of scope for this subagent`
- `Recommended owner/next action`

The orchestrator owns final deduplication and severity labeling.

### Controller Decomposition Constraint

`OrchestrationController` is too large, but this remediation should not become a broad architecture rewrite. Subagents may extract focused collaborators only when doing so directly reduces blocker risk:

- job assignment/run command service;
- plan structured editor service;
- workflow validation/save service boundary;
- Docker status fragment renderer;
- route/HTMX audit test helper.

Do not split the entire controller by page unless a specific blocker cannot be fixed safely without that extraction.

## Orchestrator Operating Model

The orchestrator must use subagents intentionally, not as a substitute for integration ownership.

Before launching subagents:

1. Capture current `git status --short` and note existing dirty files.
2. Tell every subagent they are not alone in the codebase, must not revert others' edits, and must keep to their assigned write scope.
3. Give every subagent its individual plan section from this document.
4. Require every subagent to first produce its own implementation plan with technical specifications, targets, assumptions, risks, and validation before coding.
5. Require every subagent to report changed files, tests run, skipped tests, and any missing-feature notes it appended.

Parallelization rule:

- Subagents may run in parallel only when their write scopes are disjoint.
- If two work packages touch `OrchestrationController.java`, avoid parallel writes to that file. Either serialize those packages or have one subagent own controller edits while another only writes services/tests in a separate package.
- The orchestrator should integrate frequently and run focused tests after each merge of subagent results.

Recommended execution waves:

1. Wave A, parallel: job runtime, workflow validation, workspace lease, missing-functionality audit.
2. Wave B, parallel after Wave A integration: plan editor, UI/HTMX route fixes, Docker/Podman runtime.
3. Wave C, central orchestrator: stale JS cleanup, route audit test helper, full validation, `.internal-dev` closeout.

## Subagent Plan A - Canonical Job Runtime And Assignment Execution

### Ownership

Primary write targets:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- job-related records under `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/`
- job submit portions of `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`, if serialized by the orchestrator
- focused tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/` and `src/test/java/io/mindspice/magenta2/api/web/`

### Required Subagent Planning Output

Before coding, produce a concise technical plan covering:

- current `JOB_RUN` path from UI submit to assignment creation to runner execution;
- exact legacy touchpoints to remove or bypass;
- canonical execution algorithm for `JobDefinition.items()`;
- how PLAN and WORKFLOW job items are executed or dispatched;
- failure semantics for missing plan/workflow references;
- test cases and fixtures.

### Implementation Requirements

1. Make `AssignmentService` validate `JOB_RUN` against `JobService.getDefinition(jobId)`.
2. Remove `OrchestrationJobService` as the validation dependency for `JOB_RUN`.
3. Update the runner so `JOB_RUN` loads `JobDefinition` and iterates `JobWorkItem` values in persisted order.
4. Execute PLAN items through the same task/plan assignment or run path that user-submitted task templates use. Do not fake execution success.
5. Execute WORKFLOW items through the canonical workflow run path.
6. Record per-item progress/failure against the public job run/progress model if that exists; otherwise add the smallest service method needed to make runtime progress observable.
7. Remove `ensureLegacyJob(...)` calls from job submit and agent dashboard submit paths.
8. Delete `ensureLegacyJob(...)` if no longer used.
9. Add existence validation for job item references:
   - PLAN item `planId` must resolve through `PlanService`.
   - WORKFLOW item `workflowId` must resolve through `WorkflowService`.
10. Update tests that currently use fake plan/workflow ids to create real backing definitions or expect rejection.

### Validation

Required focused tests:

- Create an agent, create a canonical public job with a real PLAN item, submit to an agent, and prove the assignment uses the canonical job id without a legacy `OrchestrationJob`.
- Create a canonical job with PLAN and WORKFLOW items and prove the runner attempts the actual configured items in order.
- Unknown `planId` is rejected at job item save.
- Unknown `workflowId` is rejected at job item save.
- Agent dashboard submit path and job page submit path behave identically for `JOB_RUN`.

## Subagent Plan B - Plan Structured Editor Persistence

### Ownership

Primary write targets:

- plan editor sections of `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- plan service methods under `src/main/java/io/mindspice/magenta2/ai/chat/plan/`
- focused tests under `src/test/java/io/mindspice/magenta2/api/web/` and `src/test/java/io/mindspice/magenta2/ai/chat/plan/`

### Required Subagent Planning Output

Before coding, produce a concise technical plan covering:

- every rendered plan editor control and the route/method it is expected to call;
- persisted target fields for inputs, outputs, deliverables, assumptions, steps, and validation criteria;
- the exact add/update/delete route matrix;
- how blank rows are handled;
- whether any control should be removed because functionality is intentionally not available.

### Implementation Requirements

1. Audit rendered `hx-*` attributes in the plan editor and create matching controller routes or remove the misleading controls.
2. Add explicit update endpoints for structured rows that are editable:
   - inputs;
   - outputs;
   - deliverables;
   - assumptions;
   - steps;
   - validation criteria.
3. Ensure scalar Save does not discard pending complex-section edits. Prefer row-level persistence through HTMX updates so Save is not responsible for hidden row state.
4. Ensure added rows are not persisted as meaningless blank values unless the UI immediately presents a valid editable draft with a clear save path.
5. Return HTMX fragments that reflect the saved database state after every mutation.
6. Use typed domain errors or 400 responses for invalid row operations; do not return success-looking fragments for failed persistence.

### Validation

Required focused tests:

- Exact emitted plan editor `hx-put`, `hx-post`, and `hx-delete` routes resolve.
- Updating an input row persists after reloading the editor.
- Updating an output row persists after reloading the editor.
- Updating deliverable, assumption, step, and validation criterion rows persists after reloading.
- Removing a row removes it from persisted `PlanDefinition`.
- Blank row behavior is deterministic and documented by tests.

## Subagent Plan C - Workflow Validation As Durable Save Gate

### Ownership

Primary write targets:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- workflow fragment portions of `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`, if serialized by the orchestrator
- workflow tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/` and `src/test/java/io/mindspice/magenta2/api/web/`

### Required Subagent Planning Output

Before coding, produce a concise technical plan covering:

- current save-time checks vs `WorkflowValidator` checks;
- the stable validation response record for REST;
- error vs warning semantics;
- how HTMX fragments display validation errors;
- migration/compatibility behavior for existing invalid definitions in development data.

### Implementation Requirements

1. Make `WorkflowService.saveDefinition(...)` call the validator and reject blocking validation errors before durable save.
2. Preserve nonblocking warnings.
3. Expose a structured validation response from `POST /api/workflows/{workflowId}/validate`, with fields equivalent to:

```java
record WorkflowValidationResponse(boolean valid, List<String> errors, List<String> warnings) {}
```

4. Stop requiring clients to parse `"ERROR: "` strings.
5. Ensure validation covers:
   - cycles;
   - missing required task inputs;
   - invalid route source/destination endpoint names;
   - null route type where type is required;
   - MAP_OUTPUT type mismatch;
   - PASS_THROUGH semantics;
   - LOG route non-dependency behavior.
6. Fix node removal to be null-safe when routes have no source node.
7. Ensure HTMX workflow builder fragments show structured errors and do not save invalid graphs.

### Validation

Required focused tests:

- Invalid graph with cycle is rejected on save.
- Missing required task input is rejected on save.
- Invalid endpoint route is rejected on save.
- MAP_OUTPUT type mismatch is rejected on save.
- Warning-only graph can save and returns warnings from validation.
- `POST /api/workflows/{workflowId}/validate` returns structured errors/warnings.
- Removing a node with a route whose `fromNodeKey` is null does not throw.

## Subagent Plan D - UI/HTMX Browser Contract Repair

### Ownership

Primary write targets:

- UI rendering sections of `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`, serialized by the orchestrator if other subagents need the same file
- `src/main/resources/static/webjars/htmx.org/dist/htmx.min.js`
- page-specific static files under `src/main/resources/static/js/orchestration/`
- `src/main/resources/static/css/orchestration.css`
- controller/browser contract tests under `src/test/java/io/mindspice/magenta2/api/web/`

### Required Subagent Planning Output

Before coding, produce a concise technical plan covering:

- every visible route/control listed in the UI/HTMX review;
- the intended route target and HTTP method;
- HTMX vs JavaScript decision for `/inbox` and `/outputs`;
- how real HTMX delivery will be proven;
- mobile/desktop layout risks to verify.

### Implementation Requirements

1. Ensure `/webjars/htmx.org/dist/htmx.min.js` serves the real HTMX WebJar asset, not `compat-noop`.
2. Delete the checked-in noop static asset if it shadows the WebJar.
3. Add an automated assertion that the served HTMX asset does not contain `compat-noop`.
4. Wire agent detail tabs with real HTMX:
   - `hx-get="/agents/_detail/{agentId}/{tab}"`;
   - `hx-target="#agent-tab-panel"`;
   - `hx-swap="innerHTML"`;
   - accessible active-state behavior after swaps.
5. Replace Docker status panel JSON swap with an HTML fragment endpoint or server-side rendered fragment.
6. Repair visible job links:
   - either restore `GET /jobs/{jobId}` as a real page route; or
   - change links to target an existing `/jobs` shell/editor fragment flow.
7. Audit all visible links/buttons on `/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/agents`, `/agents/{agentId}`, `/inbox`, and `/outputs` for removed routes, inert buttons, JSON panel swaps, or no-op controls.
8. Decide `/inbox` and `/outputs` transport:
   - If HTMX is straightforward, convert normal list/action/filter flows to fragments.
   - If JavaScript remains the path of least resistance, keep it narrowly scoped and document the justification in the final validation review.
9. Delete or quarantine `src/main/resources/static/js/orchestration/app.js` if unreferenced and stale.
10. Confirm `/chat` does not load operational scripts.

### Validation

Required focused tests:

- Served HTMX asset is real and not `compat-noop`.
- Agent tab buttons contain valid `hx-get`, `hx-target`, and `hx-swap`.
- Docker status panel targets an HTML endpoint and renders HTML, not raw JSON.
- Dashboard and agent job links resolve to live GET routes.
- `/chat` includes only chat assets and no orchestration page scripts.
- If `/inbox` or `/outputs` remain JS-driven, test and document the accepted JS exception.

## Subagent Plan E - Workspace Lease And Podman Runtime Hardening

### Ownership

Primary write targets:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java`
- runtime configuration in `src/main/resources/application.yml`
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java`
- related tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/`, `src/test/java/io/mindspice/magenta2/ai/orchestration/docker/`, and `src/test/java/io/mindspice/magenta2/api/web/`

### Required Subagent Planning Output

Before coding, produce a concise technical plan covering:

- exact SQLite invariant needed for one active write lease per workspace;
- transaction/atomic insert strategy;
- expected behavior under concurrent acquisition;
- Podman default host/image/config expectations;
- timeout cleanup semantics.

### Implementation Requirements

1. Add a database-enforced active-write exclusivity invariant. A non-unique index is not sufficient.
2. Make writable lease acquisition atomic. Prefer a unique partial index plus insert-and-handle-conflict behavior.
3. Ensure concurrent attempts for the same workspace produce exactly one active write lease.
4. Keep read leases behavior unchanged unless current schema requires adjustment.
5. Make Podman/Docker-compatible local runtime settings explicit in config or documentation:
   - enabled state;
   - host/socket behavior;
   - default image;
   - timeout;
   - SELinux relabel behavior if relevant.
6. Ensure local alpha startup works out of the box with Podman when Podman socket access is available.
7. Ensure disabled/unavailable runtime status is represented as a status response and UI fragment, not an unexplained startup crash unless the configured policy is explicitly fail-fast.
8. Tighten command timeout cleanup so timed-out/stuck containers are stopped/removed within the configured budget.

### Validation

Required focused tests:

- Two concurrent write lease acquisitions for one workspace result in exactly one active write lease.
- Released write lease allows a later write lease.
- Docker/Podman status endpoint covers enabled-ready, disabled, and unavailable daemon states.
- Command timeout test proves long-running execution is stopped/removed and does not wait for two full timeout windows.
- Startup smoke works with Podman available, or reports the exact missing socket/image dependency.

## Subagent Plan F - Whole UI Contract Missing-Functionality Audit

### Ownership

Primary write targets:

- `.internal-dev/notes/operational-ui-contract-missing-features.md`
- optional read-only audit notes in the subagent final report

This subagent must not edit production code unless the orchestrator explicitly expands its scope.

### Required Subagent Planning Output

Before auditing, produce a checklist of surfaces and interaction classes to inspect:

- dashboard;
- plans;
- workflows;
- jobs;
- projects;
- agents;
- agent detail tabs;
- inbox;
- outputs;
- runtime/Docker status;
- `/chat` isolation;
- static assets;
- API routes backing visible controls.

### Audit Requirements

1. Inspect rendered UI code, API/controller routes, tests, and review artifacts.
2. Identify missing functionality, no-op controls, mocked behavior, fake production paths, route mismatches, or unimplemented contract promises.
3. Do not duplicate blocker fixes already owned by other subagents unless the finding is broader than the assigned fix.
4. Append findings to `.internal-dev/notes/operational-ui-contract-missing-features.md`.
5. Mark each finding as:
   - `alpha blocker`;
   - `alpha should-fix`;
   - `post-alpha deferred`;
   - `needs user decision`.
6. Include evidence paths and recommended next action.

### Validation

The orchestrator must review this note before final validation and either:

- assign any `alpha blocker` finding to a subagent before closing this remediation; or
- explicitly downgrade it with a short rationale in the final validation review.

## Orchestrator Integration Plan

1. Start with `git status --short`.
2. Launch Wave A subagents:
   - Subagent A: Canonical Job Runtime And Assignment Execution.
   - Subagent C: Workflow Validation As Durable Save Gate.
   - Subagent E: Workspace Lease And Podman Runtime Hardening.
   - Subagent F: Whole UI Contract Missing-Functionality Audit.
3. While Wave A runs, the orchestrator should inspect overlapping files and decide whether `OrchestrationController.java` edits must be serialized.
4. Integrate Wave A one subagent at a time.
5. After each integration:
   - inspect changed files;
   - run the subagent's focused tests;
   - resolve conflicts without reverting unrelated edits.
6. Launch Wave B subagents:
   - Subagent B: Plan Structured Editor Persistence.
   - Subagent D: UI/HTMX Browser Contract Repair.
7. Integrate Wave B one subagent at a time.
8. Review `.internal-dev/notes/operational-ui-contract-missing-features.md`.
9. Assign any remaining `alpha blocker` finding to a targeted follow-up subagent or fix it directly.
10. Add or update a route/HTMX audit test helper that verifies rendered `hx-*` attributes resolve to live routes and do not target JSON-only APIs.
11. Run the central validation gates.
12. Write final `.internal-dev` closeout artifacts only after validation passes.

## Central Validation Criteria

The orchestrator owns these gates. Do not accept individual subagent summaries as proof without direct inspection or command evidence.

### Gate 0 - Source And Contract Inspection

Acceptance criteria:

- No `JOB_RUN` production path depends on `OrchestrationJobService` or legacy shadow job rows.
- `ensureLegacyJob(...)` is removed or unreachable from public UI submit paths.
- Plan editor controls have matching routes and persistence tests.
- Workflow save rejects validator errors.
- Agent tabs have real HTMX behavior or a documented JS implementation.
- Docker status panel renders HTML in UI contexts.
- Visible job links target live routes.
- `compat-noop` HTMX is not served.
- Stale `app.js` is deleted, quarantined, or proven not public.
- `/chat` remains isolated from operational scripts.
- `.internal-dev/notes/operational-ui-contract-missing-features.md` exists and has no unresolved `alpha blocker` entries unless the orchestrator documents a user-approved deferral.

### Gate 1 - Focused Automated Tests

Run focused tests added or modified by subagents. Minimum expected suites:

```bash
mvn -q -Dtest=OperationalUiContractControllerTest test
mvn -q -Dtest=OrchestrationControllerTest test
mvn -q -Dtest=JobServiceTest test
mvn -q -Dtest=WorkflowRunnerTest test
mvn -q -Dtest=ProjectServiceTest test
```

If new focused test classes are added, run them explicitly.

Acceptance criteria:

- Every blocker has at least one focused failing-before/passing-after style test or a documented reason why browser validation is the only meaningful proof.
- No tests rely on fake plan/workflow/agent ids where the production contract now requires real references.

### Gate 2 - Full Automated Test Suite

Required command:

```bash
mvn test
```

Acceptance criteria:

- Full suite passes.
- If a local environment dependency blocks a test, document the exact dependency and rerun the relevant subset once available.

### Gate 3 - Startup Smoke With Podman

Required command:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Acceptance criteria:

- Spring reaches `Started Magenta2Application`.
- Exit code `124` is acceptable only after successful startup.
- Podman/Docker runtime status is explicit:
  - ready when socket/image are available;
  - disabled/unavailable with readable status when not configured;
  - no unexplained fail-fast crash for normal local alpha startup.

### Gate 4 - Static Route And HTMX Audit

Acceptance criteria:

- Every rendered `hx-get`, `hx-post`, `hx-put`, and `hx-delete` route on operational pages resolves to a controller route with the expected method.
- No HTMX panel targets a JSON-only API endpoint unless explicitly accepted and documented.
- No visible link targets a removed page route.
- JavaScript usage on `/workflows`, `/inbox`, or `/outputs` is explicitly justified where retained.
- Real HTMX asset is loaded.

### Gate 5 - Browser Validation

Before browser validation involving chat, SSE, agent/model routing, concurrent interaction, or live workflow behavior, read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.

Validate at desktop and mobile widths:

- `/dashboard`
- `/plans`
- `/workflows`
- `/jobs`
- `/projects`
- `/agents`
- `/agents/{agentId}`
- `/inbox`
- `/outputs`
- `/chat`

Acceptance criteria:

- No console errors.
- No failed network calls from visible controls.
- Normal operational CRUD/filter/tab/panel flows use HTMX unless a documented JS exception applies.
- Plan structured edits persist after reload.
- Invalid workflow graphs cannot save; valid workflows save and reload.
- Job creation, item editing, submit-to-agent, assignment creation, and runtime execution all use canonical job data.
- Project owner/member references resolve to real agents.
- Agent tabs load dashboard, queue, inbox, jobs, workspace, outputs, and history.
- Docker status renders as readable UI, not raw JSON.
- Inbox and outputs actions work according to the chosen HTMX/JS design.
- `/chat` still loads the original chat client and works.
- Mobile width has no obvious overlap or unusable dense tables.

### Gate 6 - End-To-End Alpha Workflows

Run these flows against a live app with seeded or newly created data:

1. Create an agent.
2. Create a project owned by that agent; verify project dashboard/detail links and member controls.
3. Create a plan with inputs, outputs, deliverables, assumptions, steps, and validation criteria; edit each structured section; reload and verify persistence.
4. Submit the plan to an agent; verify `TASK_RUN` assignment appears in the agent queue.
5. Create a workflow with at least two nodes and one `MAP_OUTPUT` route; validate, save, reload, and submit it to an agent.
6. Create a job with one plan item and one workflow item; submit it to an agent; prove the runtime reads the edited canonical job items.
7. Create or simulate an inbox approval and verify user and agent inbox state transitions.
8. Write or seed an output artifact and verify dashboard, output search, job, project, and agent output views can find it.
9. Open `/chat`, send a basic message or run the existing chat validation fixture, and verify operational UI changes did not break chat.

## Internal-Dev Closeout

After all validation gates pass:

1. Write `.internal-dev/changelogs/<date>-operational-ui-contract-alpha-remediation.md`.
2. Write or update knowledge docs for:
   - canonical job runtime contract;
   - workflow validation save gate;
   - HTMX vs JavaScript decisions for operational UI surfaces;
   - Podman runtime expectations.
3. Write a final validation review in `.internal-dev/reviews/`.
4. Update `.internal-dev/bugs/operational-ui-contract-beta-blockers/report.md` status.
5. Keep `.internal-dev/notes/operational-ui-contract-missing-features.md` as the durable missing-feature tracker.
6. Archive the original plan suite only after the final validation review is PASS and the user accepts the result.

## Exit Criteria

- All blocker findings in the 2026-05-12 review set are fixed or explicitly documented as user-approved deferrals.
- No public operational UI path depends on legacy job execution for `JOB_RUN`.
- Plan, workflow, job, project, agent, inbox, output, Docker, and dashboard flows have direct test and browser evidence.
- Podman-compatible local startup works out of the box when socket access is available.
- Missing functionality has been audited and tracked.
- The orchestrator has directly validated integrated behavior rather than trusting subagent summaries.
- `.internal-dev` changelog, knowledge, review, bug, and missing-feature notes are updated.
