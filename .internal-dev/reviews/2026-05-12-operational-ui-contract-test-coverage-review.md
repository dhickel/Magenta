# Scope

Reviewed the completed operational UI contract refactor test surface against `.internal-dev/plans/operational-ui-contract-refactor/07-validation-rollout.md` and `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`.

Scope was limited to test coverage and validation framework. Production code was not edited. The current checkout is heavily dirty with many operational UI files untracked or modified, so this review treats the working tree as the target and does not assume committed baseline state.

Files inspected:

- `.internal-dev/AGENTS.md`
- `.internal-dev/plans/operational-ui-contract-refactor/README.md`
- `.internal-dev/plans/operational-ui-contract-refactor/07-validation-rollout.md`
- `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/main/java/io/mindspice/magenta2/api/web/DashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java`

Files changed:

- `src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-test-coverage-review.md`

# Findings

1. `OrchestrationControllerTest` is useful as a UI contract smoke suite, but most assertions are rendered-string checks rather than robust integration tests. It verifies route shells, HTMX attributes, absence of old JS markers, script versions, and minimal JS skeletons for dashboard/plans/workflows/projects/agents. This catches accidental reintroduction of old JS-driven UI and broken shell composition, but it does not execute Spring MVC routing, HTMX requests, form submission semantics, or browser behavior.

2. `OperationalUiContractControllerTest` is the stronger API/contract suite because it uses real SQLite-backed services. Before this review it covered canonical project create/update/workspace fields, missing project owner bad request handling, job draft/item/output endpoints, bad job item payload handling, and unknown-job output query behavior. I added `dashboardSummaryAggregatesOperationalContracts`, which wires real `ProjectService`, `JobService`, `AgentProfileService`, workflow `InboxService`, and `OutputArtifactService` over one in-memory SQLite connection, then asserts `/api/dashboard/summary` aggregates projects, active work, agents, waiting approvals, recent outputs, and running/queued stats.

3. Workflow runtime coverage is meaningful but incomplete for the phase 04 graph contract. `WorkflowRunnerTest` exercises definition CRUD, report nodes, approval waiting/resume shape, inbox persistence, binding resolution, status transitions, and delete-with-runs. It does not yet appear to cover the newer route model deeply enough: route persistence through REST controller POST/PUT/validate, cycle validation, MAP_OUTPUT type mismatch failures, PASS_THROUGH semantics, LOG route non-dependency behavior, or legacy binding import collision cases called out in handoff notes.

4. Job/project service tests are real service/repository tests and provide useful domain coverage. `JobServiceTest` covers empty draft jobs, item validation, run creation, workspace/output path allocation, progress, failed item propagation, recurrence, and cancellation. `ProjectServiceTest` covers owner requirement, owner membership, cross-project membership, owner-removal guard, network gating, and events. These are not meaningless narrow tests.

5. The plan's browser validation and HTMX compliance checklist are not automated in the current test suite. The current tests assert that HTMX attributes and JS skeletons exist, but they do not load pages in a browser, click controls, verify actual HTMX network calls, check console errors, validate mobile/desktop layout, or prove `/chat` remains functionally isolated beyond string absence checks in orchestration shells.

6. The test suite still has notable API gaps relative to phase 07: no focused contract test was found for `RuntimeController.dockerStatus()` enabled/disabled paths; no focused `PlanController` submit-to-agent endpoint test was found in this pass; no direct output filter coverage for agent/project/job run-id expansion beyond unknown job behavior; no Spring application-context or startup smoke was run as part of this scoped review.

# Risk Assessment

Risk is moderate. The current suite is not empty theater: there are real service/repository tests and several controller-level contract tests. However, the broadest operational UI coverage relies heavily on direct controller calls and HTML substring checks with stubs. That is good for catching accidental markup regressions and JS backsliding, but weak against wiring failures, request binding bugs, HTMX selector mistakes, missing static assets, browser-only errors, and API behavior drift.

The highest residual risk remains phase 07's live validation domain: browser behavior, HTMX request transport, `/chat` functional isolation, and end-to-end operational flows across dashboard, plans, workflows, jobs, projects, agents, inbox, and outputs.

# Recommendations

1. Keep `OrchestrationControllerTest` as a fast contract smoke suite, but do not treat it as final validation. Its assertions are mostly useful guardrails, not proof that the UI works in a browser.

2. Continue adding targeted service-backed controller tests in `OperationalUiContractControllerTest` for high-value API contracts. Good next candidates are `RuntimeController.dockerStatus()` disabled/enabled behavior, `OutputController` filtering by job/project/agent using real job run ids, and `PlanController` submit-to-agent request mapping.

3. Add Spring MVC or `@SpringBootTest` slices for the operational API routes once the working tree stabilizes. Direct controller calls miss request parameter binding, HTTP status translation, content negotiation, and route registration conflicts.

4. Preserve Playwright MCP validation as a required gate for this refactor. Automated Java tests should support phase 07, not replace the browser checklist.

# Follow-ups

Commands run:

- `mvn -q -Dtest=OperationalUiContractControllerTest test` initially failed because the new dashboard test fixture used `qwen3` without registering it in the test `AiConfig` model map.
- `mvn -q -Dtest=OperationalUiContractControllerTest test` passed after fixing the fixture. Surefire result: 6 tests, 0 failures, 0 errors, 0 skipped.

Tests added:

- `OperationalUiContractControllerTest.dashboardSummaryAggregatesOperationalContracts`

No full `mvn test`, startup smoke, or browser validation was run because this task was scoped to test coverage review and the focused test touched. Those remain required for final phase 07 acceptance.
