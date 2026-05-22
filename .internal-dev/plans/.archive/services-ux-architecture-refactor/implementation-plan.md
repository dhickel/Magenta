# Services/UX Architecture Refactor Implementation Plan

Date: 2026-05-21

## Objective

Align Magenta's services, APIs, and operational UI with the intended project/job/workspace architecture. After the refactor, users and operators should be able to submit work through agents with optional project context, see the effective workspace that will be used, inspect job assignment/run identity, and trace outputs back to project, agent, job, work unit, run, assignment, and workspace context.

The implementation must keep projects as shared workspace/visibility abstractions, agents as executors, tasks/plans/workflows as bounded work units, and jobs as hybrid orchestration/work-unit records. It must preserve `workspaceId` compatibility while making `projectId` explicit and non-interchangeable.

## Inputs And Assumptions

Confirmed inputs:

- Initial review artifacts under `.internal-dev/plans/services-ux-architecture-refactor/`.
- Current architecture docs:
  - `docs/technical/workspaces-tools-outputs.md`
  - `docs/technical/orchestration-runtime.md`
  - `docs/technical/services.md`
  - `docs/end-user/projects-and-workspaces.md`
  - `docs/end-user/jobs.md`
  - `.internal-dev/notes/current-architecture-focus.md`
- Current branch: `services-ux-architecture-refactor`.
- Existing direction: project-scoped execution uses project workspace; otherwise execution uses agent workspace.
- Existing direction: user-facing execution must route through assignments and leases, not direct service execution.

Assumptions implementation agents must verify before editing code:

- SQLite migrations are handled in repository bootstrap code rather than an external migration tool.
- `WorkAssignment` can be extended with nullable first-class context fields without breaking public clients that tolerate additive JSON fields.
- UI code should continue using SimplyPages/HTMX patterns in `OrchestrationController` and selector helpers, not raw ad hoc HTML where existing components cover the need.
- Output APIs remain public-alpha operational read models during this refactor. This plan improves filtering and display provenance; it does not introduce authorization or project membership enforcement for output reads.
- Optimistic editor revision checking is deferred unless implementation discovers existing revision helpers. This plan instead defines active-run mutation blocking for dangerous changes.

## Scope

In scope:

- First-class assignment context fields and read models:
  - `projectId`
  - compatibility `workspaceId`
  - effective workspace id/kind/display path
  - agent/job/job item/type/status context needed by UI
- Compatibility migration/backfill from existing assignment `input.projectId` into first-class assignment state.
- Controller/service boundary semantics for `projectId` versus `workspaceId`.
- Assignment-routed job execution and recurrence behavior.
- Job execution summary read model bridging job definition, assignment, job run, agent, project, effective workspace, persistent job workspace, output directory, child run ids, and output counts.
- Explicit active project/job mutation policy.
- Output provenance query/display improvements for existing stored attribution.
- Project, job, output, plan/workflow submit, and agent submit/history UI updates that use the new read models.
- Focused unit/controller/integration tests, Spring context smoke, and Playwright subagent validation.
- Required docs, `.internal-dev` changelog/knowledge notes, and final architecture review closeout.

Out of scope:

- Renaming all plan/task implementation types to task.
- Introducing project-level direct execution.
- Introducing a new permission model for output content/download visibility.
- Replacing coarse project write leases with subtree locks.
- Building a full scheduler redesign beyond preventing direct job recurrence bypass.
- Removing loose artifact discovery by default unless implementation discovers it can be done safely with existing tests and docs. New work must not rely on loose discovery.
- Broad frontend redesign unrelated to project/job/workspace/output context.

## Current-State Analysis

Backend/service state:

- `AssignmentRequest` accepts `projectId`, but `WorkAssignment` persists only compatibility `workspaceId` plus JSON input/checkpoint/output fields. `AssignmentService` merges project into input JSON, and runner code later recovers it from input or job fallback.
- `ProjectService` has membership APIs but still exposes legacy owner-oriented reads and labels. Projects are documented as shared workspaces, while UI still shows owner/initial-agent language in prominent places.
- Public job routes create `JOB_RUN` assignments, but `JobService.startRun` and recurrence helpers can still allocate job runs directly. `JobRun` stores job assignment id and path data, but there is no stable assignment/run summary contract.
- `WorkflowRunner` resolves effective workspace paths, but workflow run records and output contexts can miss effective workspace/project attribution.
- `OutputArtifactService` stores rich attribution, while API/UI filters and rows expose only a subset. Compatibility fallback can hide direct attribution gaps.
- Assignment queue leases and workspace write leases already exist. Project-scoped assignments acquire a writable project workspace lease in `OrchestrationRunnerService`.

UI/API state:

- Agent submit forms do not accept project/workspace context.
- Job submit/start surfaces hide effective context and cannot visibly override it in the operational UI.
- Persistent job workspace can be persisted by APIs/controller params but is not rendered as a UI control or run detail.
- Project UI lists members but lacks membership editing and has a broken project-job HTMX target.
- Assignment, run, and output tables hide project/workspace/job assignment/run context.
- Selector infrastructure can filter by context, but wrappers often call it with empty context.

Concurrency/testing state:

- Assignment leases and workspace lease tests exist.
- There is no test proving simultaneous project-scoped assignments serialize through project workspace leases.
- There is no deterministic active-run mutation policy for project membership/deletion or job item edits.
- Waiting assignment recovery after project lease conflict needs an explicit operation or requeue path.
- Playwright harness exists but does not cover the new project/job/output interaction flows yet.

## Target Design

### Abstraction Model

- Agent: executable actor and default workspace owner.
- Project: shared durable workspace, membership, and visibility context. Not executable.
- Task/plan: bounded work unit. Runs through assignment and uses effective workspace.
- Workflow: bounded composite work unit. Runs through assignment and uses effective workspace.
- Job: repeatable work-unit/orchestrator hybrid. A job definition describes work; a job assignment requests execution; a job run records execution; an optional job workspace is per assignment.

### Assignment Context Contract

Add first-class assignment context as nullable fields on persisted assignments and/or a read DTO backed by persisted fields:

- `projectId`: explicit project context. When present, project workspace is the effective durable workspace.
- `workspaceId`: compatibility metadata only. It must not be treated as a project id.
- `effectiveWorkspaceId`: resolved workspace id used for execution.
- `effectiveWorkspaceKind`: `PROJECT` when `projectId` is present, otherwise `AGENT`.
- `effectiveWorkspaceDisplayPath`: display/read-model field derived from `Workspace`/directory services.

Compatibility rules:

- Existing records are backfilled from `input.projectId` where possible.
- New assignment creation stores `projectId` first-class and keeps writing it into input JSON only for compatibility with older consumers.
- A request with only `workspaceId` remains accepted as compatibility metadata but does not become project-scoped execution.
- A request with `projectId` and an unrelated project-owned `workspaceId` should be rejected at controller/service validation. This avoids silently showing one workspace while executing in another.
- A request with `projectId` and an agent/legacy `workspaceId` may be accepted, but UI must label the workspace value as compatibility metadata and show project workspace as effective.

### Execution Boundary

All user-facing execution paths must create or resume a `WorkAssignment`.

- Public plan/task submit routes create assignments.
- Public workflow submit routes create assignments.
- Job submit/start routes create `JOB_RUN` assignments.
- Schedules and recurrences create assignments.
- Direct job run allocation remains internal to `OrchestrationRunnerService` after it owns an assignment lease, or is guarded by an explicit assignment context parameter.
- No project route executes work directly.

### Job Execution Read Model

Introduce a job execution summary contract, preferably in `JobService` first:

- job definition id/title/status
- job assignment id and assignment status
- agent id/name/status where available
- project id/name where available
- compatibility workspace id
- effective workspace id/kind/display path
- persistent workspace enabled flag
- persistent job workspace id/path/presence
- job run id/status
- output directory
- child task/workflow run ids when available
- output count and latest output timestamp
- timestamps for queued/started/completed/updated

Keep raw `JobRun` and `WorkAssignment` records for compatibility. UI should consume summaries where context is needed.

### Active Mutation Policy

Use a conservative alpha policy:

- Block project deletion while non-terminal assignments, active leases, active jobs, or active output-producing runs reference the project.
- Block project membership removal for an agent that has non-terminal assignments or active project workspace lease holders for that project.
- Allow project label-only edits such as name, description, repo URL, manager type, and default model while active work exists, but do not alter active assignment context.
- Block job deletion while non-terminal assignments or non-terminal job runs reference the job.
- Block job item add/update/delete while non-terminal job assignments/runs exist for that job.
- Allow job label-only edits such as title and summary while active work exists.
- Treat default model, project, owner/default agent, persistent workspace flag, recurrence, and item list as execution-affecting. Block these changes while non-terminal job assignments/runs exist.
- Return clear 409-style API errors and HTMX fragments that explain active work must finish, cancel, or be requeued first.

This avoids introducing definition snapshots in this suite. A later revision-based editor system can relax this policy.

### Leasing And Recovery

- Keep current coarse writable project workspace lease acquisition for project-scoped assignment execution.
- Preserve assignment queue lease ownership checks for runner writes.
- Add deterministic recovery for workspace-blocked `WAITING` assignments:
  - a service method to requeue one workspace-blocked assignment when the blocking lease has ended, and
  - a batch method/operator action to requeue eligible workspace-blocked assignments.
- UI must display `WAITING` workspace blocker details and avoid implying release is immediate.
- Leave subtree/read lease support as future-ready by centralizing lease intent in service helpers rather than sprinkling direct lease calls into controllers.

### Output Provenance

- Direct output artifact attribution is the primary contract.
- Output rows and details display project, agent, job, job assignment, job run, run type, workspace, plan/workflow/task run id, path, and created timestamp where present.
- Add query support for stored attribution fields that are currently internal:
  - `workspaceId`
  - `planId`
  - `jobAssignmentId`
  - `jobRunId`
  - `runType`
- Keep compatibility fallback explicit and tested. It must not be the only reason new project/job output filters work.
- Chat files remain separate from output artifacts.

### UI Design

- Use SimplyPages/HTMX patterns already present in the orchestration pages.
- Use selectors for agent/project/job/plan/workflow/model/workspace fields where existing selector infrastructure supports them.
- Extend selector helper APIs to pass context parameters, then use project/agent context on job selectors.
- Make effective context visible near submit buttons, queue rows, run rows, diagnostics, output rows, and output content panes.
- Do not expose new controls before service/API contracts can persist and display their state.

## Implementation Plan

### Phase 1: Service Contracts And Assignment Context

See `.internal-dev/plans/services-ux-architecture-refactor/phase-01-service-contracts.md`.

Primary steps:

1. Add assignment schema fields for `project_id`, `effective_workspace_id`, and `effective_workspace_kind`.
2. Extend `WorkAssignment`, repository row mapping, inserts, updates, and retained history reads.
3. Backfill `project_id` from `input_json.projectId`.
4. Resolve effective workspace at assignment creation and update it at execution start if missing.
5. Add `AssignmentSummary`/context read model for UI/API consumers.
6. Add repository/service queries by project, effective workspace, job, type, status, and active/non-terminal state.
7. Add validation that prevents `workspaceId` from masquerading as or conflicting with `projectId`.
8. Add workspace-blocked requeue service operations.
9. Add active project/job mutation policy service checks used by controllers.

Gotchas:

- Preserve old JSON input project fields while adding first-class columns.
- Do not make `workspaceId` select project execution.
- Do not acquire project write leases at assignment creation. Assignment creation may resolve workspace identity but execution lease acquisition remains in the runner.

### Phase 2: Job Execution Read Model And Assignment-Routed Execution

See `.internal-dev/plans/services-ux-architecture-refactor/phase-02-job-execution-read-model.md`.

Primary steps:

1. Introduce `JobExecutionSummary` records and service methods.
2. Make job submit/start/recurrence paths create assignments and never start job runs directly from user-facing or scheduler paths.
3. Guard direct `JobService.startRun` so it requires assignment context or is only called from `OrchestrationRunnerService`.
4. Link job runs to assignment/effective context and persistent workspace state.
5. Add cancellation compatibility so assignment-owned job runs cancel through assignment lifecycle or keep both states synchronized.
6. Add tests for per-assignment persistent workspaces under agent and project contexts.

Gotchas:

- Do not share one persistent job workspace across multiple assignments of the same job definition.
- During the gap between assignment creation and job run creation, summaries must still show assignment context and "run pending".
- Recurrence de-duplication must remain assignment-based through `ScheduleService`/schedule firing records.

### Phase 3: Output Provenance

See `.internal-dev/plans/services-ux-architecture-refactor/phase-03-output-provenance.md`.

Primary steps:

1. Ensure plan/task/workflow/job output materialization receives first-class assignment context and effective workspace id.
2. Persist or expose workflow run attribution needed for output/context views.
3. Expand output query APIs and repository query support for stored attribution fields.
4. Update output compatibility fallback tests so new direct attribution is not masked.
5. Add collision-safe output naming or deterministic rejection if concurrent workflow outputs can produce duplicate artifact filenames.
6. Keep loose artifact discovery as compatibility unless tests and docs are updated in the same phase.

Gotchas:

- `OrchestrationTaskContext.workspaceId` should refer to effective workspace identity for artifact attribution, while compatibility workspace id remains separate.
- Output paths must stay confined under the configured data root.
- Chat files must not be indexed or displayed as orchestration output artifacts.

### Phase 4: Project, Job, And Operator UX

See `.internal-dev/plans/services-ux-architecture-refactor/phase-04-project-job-ux.md`.

Primary steps:

1. Update agent submit forms to include project selector, compatibility workspace selector where still needed, and effective context summary.
2. Update plan/workflow submit fragments to display assignment project/effective workspace context after submit.
3. Update job editor with persistent workspace toggle and active-work mutation errors.
4. Update job submit/start forms to show and optionally override project/workspace compatibility context.
5. Update job run panels to use `JobExecutionSummary`.
6. Reframe project UI labels around shared workspace/membership; add membership controls only after active mutation checks are wired.
7. Fix project active job navigation/HTMX target.
8. Expand output rows/content panes to display provenance and add filters for new query fields.
9. Thread selector context through reusable selector components.

Gotchas:

- Use HTMX for CRUD, filtering, row actions, forms, and partial refreshes.
- Do not hide raw ids when they are needed for alpha traceability; pair them with labels where available.
- Verify mobile/desktop layouts for added columns and selector controls.

### Phase 5: Validation And Closeout

See `.internal-dev/plans/services-ux-architecture-refactor/phase-05-validation-closeout.md`.

Primary steps:

1. Run phase-specific Maven tests after each implementation phase.
2. Run full `mvn test` before final closeout.
3. Run bounded Spring context smoke.
4. Run Playwright validation in a subagent using `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
5. Capture screenshots of changed UI surfaces and review them for layout/provenance regressions.
6. Update end-user, technical, and API docs.
7. Update package guides if package responsibilities or public surfaces change.
8. Create `.internal-dev` changelog and reusable knowledge notes.
9. Run final xhigh architecture/code/UX review and remediate blockers.
10. Commit after each validated implementation phase and final closeout.

## Risk Assessment And Mitigation

| Risk | Severity | Mitigation |
| --- | --- | --- |
| Direct execution bypasses assignment and project leases. | Critical | Phase 1/2 must route all public execution through assignments and guard direct job start helpers. Add regression tests for public routes and recurrence. |
| `workspaceId` continues to be treated as project context. | Critical | Add validation, docs, and UI labels: `projectId` selects effective workspace; `workspaceId` is compatibility metadata. Add project-only, workspace-only, and mismatched-field tests. |
| Active project/job mutations invalidate running work. | Critical | Implement the conservative mutation policy before adding membership/job controls. Return 409/API and HTMX errors. |
| Workspace-blocked assignments remain stuck in `WAITING`. | High | Add explicit service/API/operator requeue path once blocking lease is gone. Test conflict then release then requeue. |
| Job summaries confuse definition id, assignment id, job run id, and child run ids. | High | Use one named `JobExecutionSummary` DTO and label fields consistently in API/UI/docs. |
| Output fallback hides missing direct attribution. | High | Add direct attribution tests and compatibility fallback tests separately. UI should show provenance from artifact fields, not fallback inference. |
| Concurrent output materialization overwrites files. | High | Add collision test for workflow parallel outputs; make filenames unique or reject duplicates with clear errors. |
| Selector context is dropped by reusable wrappers. | Medium | Extend selector helper contracts before UI usage; add controller tests that context appears in HTMX lookup URLs/requests. |
| Existing clients depend on raw assignment/job run payloads. | Medium | Prefer additive fields and new summary routes/fragments. Keep raw record routes where practical. |
| Playwright validation cannot run due local browser/MCP blockers. | Medium | Treat as UI sign-off blocker unless user explicitly approves fallback. Record blocker state in `.internal-dev`. |

## Validation Plan

Phase 1 required tests:

```bash
mvn test -Dtest=PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,ProjectServiceTest,OrchestrationRuntimeTest
mvn test -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest
```

Add tests for:

- assignment creation stores first-class `projectId`.
- assignment responses/summaries expose `projectId`, compatibility `workspaceId`, effective workspace id/kind/path.
- legacy input JSON project id is backfilled.
- `workspaceId`-only submission does not become project-scoped.
- mismatched project-owned `workspaceId` and `projectId` is rejected.
- simultaneous same-project assignments produce one executing path and one workspace-blocked `WAITING` path.
- released project lease can requeue a workspace-blocked assignment.
- active project/job mutations follow the chosen policy.

Phase 2 required tests:

```bash
mvn test -Dtest=JobServiceTest,OrchestrationRuntimeTest,PublicRunSubmissionControllerTest,AgentOrchestrationControllerTest
```

Add tests for:

- job submit/start returns assignment context and later summary links the job run.
- direct recurrence creates assignments and de-duplicates due firings.
- direct job run allocation cannot bypass assignment context.
- persistent job workspace path is per assignment under project and agent effective workspaces.
- assignment-owned job run cancellation is lifecycle-compatible.

Phase 3 required tests:

```bash
mvn test -Dtest=OutputArtifactServiceAttributionTest,WorkflowStreamSupportTest,TaskStreamSupportTest,PublicApiRouteBindingTest,OperationalUiContractControllerTest
```

Add tests for:

- output filters by `projectId`, `agentId`, `workspaceId`, `jobId`, `jobAssignmentId`, `jobRunId`, `planId`, `runId`, and `runType`.
- workflow outputs carry effective workspace/project attribution.
- fallback output queries are marked compatibility and do not mask direct attribution tests.
- parallel workflow output collision behavior is deterministic.

Phase 4 required tests:

```bash
mvn test -Dtest=OperationalUiContractControllerTest,PublicApiRouteBindingTest,PublicRunSubmissionControllerTest
```

Add controller tests for:

- agent submit form renders project/workspace selectors and submits them.
- plan/workflow/job submit result fragments show assignment id, project, effective workspace, and compatibility workspace when present.
- job editor renders and persists `persistentWorkspaceEnabled`.
- active mutation policy renders clear HTMX errors.
- project membership controls call the service policy.
- project active job links target an existing container or real route.
- output fragments display provenance and new filters.
- selector context is threaded through lookup requests.

Playwright validation:

- Must run in a subagent with model `gpt-5.3-codex` and reasoning effort `medium`.
- Must use `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
- Use isolated SQLite and an allowed port such as `18080`.
- Capture screenshots for changed `/projects`, `/jobs`, `/outputs`, plan submit, workflow submit, and agent submit/history surfaces.
- Validate desktop and mobile layouts for added selectors/provenance tables.
- Validate console/network diagnostics and backend observable state.

Suggested browser command setup:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-playwright.sqlite?foreign_keys=true --magenta.executor.chat-threads=4'
MAGENTA_PLAYWRIGHT_BASE_URL=http://localhost:18080 npx playwright test tests/playwright/public-alpha-harness.spec.js
```

Final regression:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-smoke.sqlite?foreign_keys=true'
git diff --check
```

Acceptance criteria:

- Public/user-facing execution routes create assignments, not direct runs.
- Assignment read models expose `projectId` and effective workspace identity without parsing input JSON.
- `projectId` and `workspaceId` are visibly and technically distinct.
- Job pages can show assignment id, job run id, effective workspace, persistent workspace state, output dir, and outputs.
- Output pages can filter and display direct artifact attribution.
- Project/job mutation controls enforce active-work policy.
- Playwright screenshots show usable, non-overlapping UI on desktop and mobile.

## Handoff Checklist

- Read the relevant package `AGENTS.md` before changing each package.
- Read SimplyPages docs/demos before frontend edits involving reusable controls, selectors, HTMX forms, or modal/fragment behavior.
- Implement only one phase at a time on `services-ux-architecture-refactor`.
- Before each phase, run `git status --short` and avoid unrelated dirty files.
- After each phase, run the phase tests, `git diff --check`, and commit only phase-related changes plus required `.internal-dev` updates.
- Do not expose UI controls whose service/API state is not implemented.
- Do not route project/job/task/workflow execution outside assignment/lease semantics.
- Do not treat `workspaceId` as `projectId`.
- Record out-of-scope bugs in `.internal-dev/bugs/` and ask before filing GitHub Issues.
- Complete docs/changelog/knowledge closeout and final review before merging or calling the suite complete.
