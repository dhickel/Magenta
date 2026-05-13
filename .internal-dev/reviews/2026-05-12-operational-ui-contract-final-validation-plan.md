# Scope

This validation plan covers the completed operational UI contract refactor working tree under `.internal-dev/plans/operational-ui-contract-refactor/`.

Inputs:

- `.internal-dev/plans/operational-ui-contract-refactor/07-validation-rollout.md`
- `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-test-coverage-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-backend-contract-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-ui-htmx-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-beta-readiness-review.md`

This is the validation gate to run before archiving the original plan suite. The current review result is not archive-ready because multiple browser, assignment, and persistence blockers remain open.

# Findings

## Gate 0 - Blocker Remediation Required Before Final Validation

Do not archive `.internal-dev/plans/operational-ui-contract-refactor/` until these blockers are fixed and revalidated:

1. Real browser HTMX must be served at `/webjars/htmx.org/dist/htmx.min.js`; the compatibility noop asset must not be the runtime asset.
2. Agent detail tabs must be wired to real HTMX requests or narrowly justified JavaScript.
3. Docker status displayed inside HTMX panels must render HTML fragments, not raw JSON.
4. Visible job links must target valid routes.
5. `JOB_RUN` submit-to-agent must execute the same public `JobDefinition.items()` users edit.
6. Plan structured editors must persist edits or remove misleading edit controls until the persistence path is complete.
7. Workflow graph validation errors must be enforced before durable save.

## Gate 1 - Automated Test Coverage

Required command:

```bash
mvn test
```

Acceptance criteria:

- Full suite passes outside sandbox restrictions.
- Any environment-only failure is documented with the exact blocked local resource.
- `OperationalUiContractControllerTest` includes service-backed coverage for dashboard aggregation, canonical project contracts, job draft/item/output contracts, and output query behavior.
- Add missing tests before passing this gate:
  - `JOB_RUN` submit path using a canonical `JobDefinition` id and no preexisting legacy `OrchestrationJob`.
  - Plan HTMX field/list update methods with persisted `PlanDefinition` assertions.
  - Workflow save rejects cycles, missing required inputs, invalid route endpoint names, and type mismatches.
  - Job item validation rejects unknown `planId` and unknown `workflowId`.
  - Project creation/member updates reject unknown agent ids.
  - Runtime Docker status covers enabled, disabled, and unavailable daemon behavior.

## Gate 2 - Startup And Runtime Smoke

Required command:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Acceptance criteria:

- Spring context reaches `Started Magenta2Application`.
- Docker/Podman runtime is either verified as ready, or the failure is explicitly documented as a required local dependency.
- Startup recovery logs do not show unexpected assignment/job corruption.
- Exit code `124` from `timeout` is acceptable only after successful startup.

## Gate 3 - Static Route And HTMX Contract Audit

Run an automated audit against rendered operational pages before browser validation.

Acceptance criteria:

- Every rendered `hx-get`, `hx-post`, `hx-put`, and `hx-delete` route resolves to a controller route with the expected method.
- No HTMX panel targets a JSON-only API endpoint unless that panel intentionally renders JSON and the review accepts the exception.
- No visible link or button targets a removed route such as `/jobs/{jobId}` without a matching GET page.
- `/chat` includes only chat assets and does not load orchestration page scripts.
- Dead static orchestration code that still contains incompatible routes is deleted or quarantined from public serving.

## Gate 4 - Browser Validation

Use the Playwright MCP workflow from `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` when validating chat, SSE, agent/model routing, concurrent interaction, or live workflow behavior.

Required pages:

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

Desktop and mobile acceptance criteria:

- No console errors.
- No failed network calls from visible controls.
- Real HTMX requests fire for normal create/edit/delete/filter/tab/panel flows.
- Any JavaScript transport exception is documented as the path of least resistance and validated in-browser.
- Dashboard partials refresh and display live data.
- Plan field/list edits persist after reload.
- Workflow node/route edits validate and persist; invalid graphs cannot save.
- Job creation, item editing, submit-to-agent, and assignment execution use one coherent job model.
- Project create/edit/detail sections resolve owner, jobs, agents, workspace, and outputs.
- Agent detail tabs load dashboard, queue, inbox, jobs, workspace, outputs, and history without inert controls.
- Docker status renders as readable UI, not raw JSON.
- Inbox and outputs normal flows either use HTMX or have accepted, narrow JavaScript justification.
- `/chat` still works with the original chat client and remains isolated from operational UI scripts.
- No obvious text overlap or unusable dense tables at mobile width.

## Gate 5 - End-To-End Beta Workflows

Run these user-facing flows against a live app with seeded or newly created data:

1. Create an agent, create a project owned by that agent, and verify project dashboard/detail links resolve.
2. Create a plan/task template with inputs, outputs, steps, assumptions, and validation criteria; edit those sections; reload and verify persistence.
3. Submit the plan to an agent and verify a `TASK_RUN` assignment appears in the agent queue.
4. Create a workflow with at least two nodes and one `MAP_OUTPUT` route; validate, save, reload, and submit it to an agent.
5. Create a job with a plan item and workflow item; submit it to an agent; execute or inspect the assignment path to prove the edited job items are the runtime items.
6. Create or simulate an inbox approval and verify user and agent inbox actions update state.
7. Write or seed an output artifact and verify dashboard, output search, job, project, and agent output views can find it.
8. Open `/chat`, send a basic message or run the existing chat validation fixture, and verify operational UI changes did not break chat.

# Risk Assessment

Current risk is high for beta readiness. Automated tests pass when run outside sandbox restrictions, and the suite now includes stronger service-backed dashboard aggregation coverage. That is not enough to accept the refactor because multiple high-value browser and runtime paths are either untested or currently contradicted by source inspection.

The most important risk is that the UI can acknowledge user actions that do not map to the durable runtime model: public job items are not yet proven to be the same items the assignment runner executes, and plan structured edit controls are not yet proven to persist edited values.

# Recommendations

Fix blocker findings before archiving the plan suite. After fixes, rerun the full validation sequence in this order:

1. Focused tests for each fixed blocker.
2. `mvn test`.
3. Bounded startup smoke with Docker/Podman available.
4. Static route/HTMX audit.
5. Browser validation across all phase 07 pages at desktop and mobile widths.
6. End-to-end beta workflows.
7. Update `.internal-dev/reviews/` with final PASS/FAIL evidence.
8. Write changelog/knowledge updates for fixes.
9. Archive the original plan suite only after the final validation review is PASS.

# Follow-ups

- Use `.internal-dev/bugs/operational-ui-contract-beta-blockers/report.md` as the tracking bug for the blockers found in this validation pass.
- Convert the route/HTMX audit into a repeatable test helper once the first blocker remediation lands.
- Keep the four specialist reviews as supporting evidence; this file is the consolidated acceptance gate.
