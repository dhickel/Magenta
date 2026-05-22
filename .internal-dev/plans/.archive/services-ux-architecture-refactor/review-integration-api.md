# Integration/API Review: Services UX Architecture Refactor

## Scope

Read-oriented review of public API controllers, request/response records, route tests, API docs, selector services, and operational UI service handoffs for project/job/workspace/output integration.

Focused areas:

- Project membership and project selection contracts.
- Task/plan/workflow/job assignment submission payloads.
- Job definition, assignment, run, and persistent workspace identity.
- Output artifact filtering and display metadata.
- Route and contract test coverage for the above.

No production source, tests, docs, or shared orchestration notes were modified. This review artifact is the only file written.

## Findings

1. Assignment responses do not expose `projectId` as a first-class contract, so UI/API consumers must infer project context from `input`.

   `AssignmentRequest` accepts `projectId` separately from `workspaceId`, but `WorkAssignment` has no `projectId` field and persists only `workspaceId` plus JSON input. `AssignmentService` merges the request project into `input.projectId`, then saves `WorkAssignment` without a first-class project field. Public submission routes return `WorkAssignment`, so `/api/plans/{planId}/submit`, plan/task stream acknowledgements, workflow submission, job submission, and `/api/agents/{agentId}/assignments` cannot reliably display or filter assignment project context without parsing arbitrary input JSON.

   References:

   - `AssignmentRequest.projectId` exists: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java:8
   - `WorkAssignment` exposes `workspaceId` but not `projectId`: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/WorkAssignment.java:6
   - project context is only merged into `input`: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java:542
   - assignment creation drops first-class project context: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java:96
   - route test asserts DB `input_json` contains the project rather than response JSON containing `projectId`: src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java:141

2. Project selectors and assignment validation allow agent/project combinations that the UI cannot reason about from membership data.

   Project membership APIs return only membership rows with `agentId` and `role`; there is no enriched member read model with agent name/status, and selectors list all projects without agent-context filtering. Submit forms validate that selected project and agent records exist independently, but they do not validate membership or warn when assigning a non-member agent to a project-scoped run. This makes project assignment technically possible but ambiguous for a UI that is supposed to display "agents execute work with attached project context."

   References:

   - membership DTO lacks agent display/status metadata: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectAgentMembership.java:8
   - membership route returns raw memberships: src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:95
   - project selector lists all projects: src/main/java/io/mindspice/magenta2/api/web/selector/EntityLookupService.java:154
   - submit forms validate agent/project existence separately: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1658, src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2706, src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3726
   - project service has membership helpers that are not enforced in assignment creation: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:164

3. `projectId` vs `workspaceId` compatibility remains a route-level trap.

   API docs correctly say `workspaceId` is compatibility metadata and is not interpreted as a project id, while `projectId` selects the effective durable workspace. However, public run requests accept both fields, workspace selectors expose workspace records directly, and there is no controller or service guard against mismatched `projectId` and `workspaceId`. A caller can submit a project workspace through `workspaceId` only, or submit a project and unrelated workspace id together; in both cases the response only surfaces `workspaceId`, making the effective workspace hard to display or debug.

   References:

   - architecture docs: docs/technical/workspaces-tools-outputs.md:24
   - plan run request carries both fields: src/main/java/io/mindspice/magenta2/api/web/PlanController.java:364
   - workflow run request carries both fields: src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java:237
   - job run request carries both fields: src/main/java/io/mindspice/magenta2/api/web/JobController.java:247
   - selector exposes workspaces independently of project selection: src/main/java/io/mindspice/magenta2/api/web/selector/EntityLookupService.java:160

4. Job persistent workspace is API-backed but not fully reachable or visible through the operational UI contract.

   `JobDefinition` and `JobController` expose `persistentWorkspaceEnabled`, and `JobService.startRun` allocates per-assignment persistent workspace paths when the flag is enabled. The `/jobs` HTMX editor reads `persistentWorkspaceEnabled` from form params, but the rendered editor does not render any input/control for that field. Existing job run tables also omit job assignment id, effective workspace id, persistent workspace path, output dir, and the definition-level persistence flag, so users cannot confirm whether a submitted job will or did receive assignment-scoped persistent space.

   References:

   - job DTO includes persistence flag: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobDefinition.java:21
   - API update preserves persistence flag: src/main/java/io/mindspice/magenta2/api/web/JobController.java:85
   - UI create/update reads `persistentWorkspaceEnabled`: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3478, src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3509
   - no rendered input exists for that param; the editor jumps from project/status to manager/model: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3841
   - run allocation records assignment-scoped workspace/output paths: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:179
   - run table displays only run/status/created/action: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4004

5. Job assignment identity and job run identity are split without a bridging read contract.

   `POST /api/jobs/{jobId}/runs` and the `/jobs` UI submit/start paths create a `JOB_RUN` assignment and return/display `WorkAssignment`. The actual `JobRun` is created later with `jobAssignmentId`, but public job assignment responses do not include `jobRunId`, and job run reads do not include `agentId`, `projectId`, or assignment status. During the gap between assignment submission and job run creation, the UI can show only the assignment id/status, not the eventual persistent workspace/output identity.

   References:

   - API job run submit returns `WorkAssignment`: src/main/java/io/mindspice/magenta2/api/web/JobController.java:149
   - HTMX job submit returns assignment panel only: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3716
   - HTMX start run also creates an assignment, not a run: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4029
   - `JobRun` has `jobAssignmentId` but no agent/project/status bridge to assignment: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRun.java:26
   - diagnostics can infer linked run ids only after checkpoint/output fields are populated: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java:371

6. Output filtering is narrower than stored output attribution and UI display omits the context needed for discovery.

   `RunOutputArtifact` stores agent, job, job assignment, job run, project, workspace, run type, plan, and run attribution. `OutputArtifactQuery` supports `workspaceId` and `planId`, but `/api/outputs` and `/outputs/_list` accept only `agentId`, `jobId`, `projectId`, `runId`, `type`, and `limit`. There is no filter for `workspaceId`, `planId`, `jobAssignmentId`, `jobRunId`, or `runType`. The UI output table displays only output/type/run/plan/created/action, so project/job assignment/workspace context is hidden even when present in the artifact record.

   References:

   - artifact record has rich attribution: src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RunOutputArtifact.java:9
   - query record supports `workspaceId` and `planId`: src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactQuery.java:5
   - public output route does not expose those filters: src/main/java/io/mindspice/magenta2/api/web/OutputController.java:40
   - operational outputs fragment does not expose those filters: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4804
   - UI output rows omit project/job/workspace/assignment context: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4817
   - docs explicitly note that not all stored attribution is exposed as public query params: docs/technical/workspaces-tools-outputs.md:82

7. Project/job output fallback can blur direct attribution.

   Both API and HTMX output queries first run a direct attribution query, then, when direct results are empty and a job/agent/project filter exists, fall back to job definitions and `jobService.outputRunIds`. This helps old artifacts that lack attribution, but it also means project-filtered output discovery depends on current job definitions when artifacts are missing project metadata. It cannot recover standalone project-scoped task/workflow outputs that lack project attribution, and it cannot distinguish assignments of the same job definition.

   References:

   - API fallback behavior: src/main/java/io/mindspice/magenta2/api/web/OutputController.java:57
   - HTMX fallback behavior: src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4913
   - job output run ids combine job run ids and child work item run ids: src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:326

8. Route tests cover binding, but not the intended UI-safe contracts.

   Existing tests verify that routes bind, submission routes create assignments, some project/workspace fields reach the request or DB, and basic output routes are callable. They do not assert response payloads contain first-class `projectId`, effective workspace identity, job assignment/job run linkage, persistent workspace visibility, enriched project memberships, or output filters for stored attribution fields. The tests currently encode the weaker compatibility behavior by checking `input_json` for project id rather than asserting a public assignment contract.

   References:

   - public route binding checks route existence and DB storage: src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java:107
   - job/project/output route test only verifies callable routes and arrays: src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java:220
   - controller unit tests assert requests carry `projectId` but not returned payload shape: src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java:41
   - operational UI contract tests do not cover output attribution filters or job assignment linkage: src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java:81

## Risk Assessment

Current contracts are usable for alpha CRUD and assignment submission, but they are not safe enough for a UI refactor that wants clear project/job/workspace state.

Primary risks:

- Users can submit project-scoped work but the assignment list/history cannot display project context without ad hoc JSON parsing.
- `workspaceId` may be mistaken for `projectId`, causing project workspace expectations to silently fail.
- Job definition ids, assignment ids, job run ids, child task/workflow run ids, and output run ids are easy to confuse because no single read model ties them together.
- Persistent job workspace configuration can be saved through JSON API, but the UI lacks a visible control and run-level confirmation.
- Output discovery can miss or over-broaden results depending on whether artifact attribution was written directly or recovered through compatibility fallback.
- Existing tests are too shallow to protect the UI from contract regressions during the refactor.

## Recommendations

1. Add explicit assignment project/effective-workspace contracts first.

   Either add `projectId` to `WorkAssignment` and `work_assignments`, or introduce public assignment response DTOs that expose `projectId`, `workspaceId`, `effectiveWorkspaceId`, `effectiveWorkspaceKind`, and `effectiveWorkspaceDisplayPath`. Make all assignment-returning controllers use that contract. Add compatibility migration/backfill from `input.projectId`.

2. Clarify `projectId`/`workspaceId` semantics at controller boundaries.

   Keep accepting `workspaceId` for compatibility, but validate or warn on mismatched project/workspace selections. Prefer project selectors for new UI flows and hide raw workspace selection unless there is a real compatibility workflow. Add route tests for project-only, workspace-only, and mismatched submissions.

3. Add job assignment/run bridge read models.

   Add a job execution summary route or enrich `GET /api/jobs/{jobId}/runs` with `jobAssignmentId`, assignment status, agent id, project id, persistent workspace enabled, persistent workspace path/presence, output dir, and child run ids. Keep raw `JobRun` for internal compatibility if needed, but give UI one stable shape.

4. Make persistent job workspace visible and controllable.

   Add the missing UI control for `persistentWorkspaceEnabled`, show the saved value in the job editor, and show per-run/assignment persistent workspace state. Add API/UI tests that create/update a job with the flag and verify run summaries expose assignment-scoped workspace identity.

5. Expand output query/display contracts to match stored attribution.

   Add public query params for at least `workspaceId`, `planId`, `jobAssignmentId`, `jobRunId`, and `runType`, and show project/job/assignment/workspace context in `/outputs` rows and content panes. Keep compatibility fallback explicit in docs/tests so direct attribution remains the primary contract.

6. Add project membership-aware selectors or validation.

   Decide whether project membership is advisory or enforced for assignments. If advisory, enrich project/member APIs with labels and warnings. If enforced, validate agent membership when a project is selected and add tests for allowed/rejected combinations.

## Follow-ups

- Update `docs/technical/api-reference.md` and `docs/technical/workspaces-tools-outputs.md` after the contracts are chosen, especially around `workspaceId` compatibility and output attribution filters.
- Add route tests for enriched assignment payloads, job assignment/run linkage, persistent workspace visibility, output attribution filters, and project membership behavior.
- Add UI-focused controller tests for `/jobs`, `/projects`, `/outputs`, and agent assignment history rows once the new read models are in place.
- Preserve compatibility for existing clients by either continuing to return raw records on current routes and adding `.../summaries` routes, or versioning/enriching responses in a backward-compatible way.
