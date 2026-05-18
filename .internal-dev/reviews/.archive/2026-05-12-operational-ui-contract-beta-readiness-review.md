# Scope

Review-only beta-readiness pass over the completed operational UI contract refactor in the current dirty worktree. I read the local `.internal-dev` process guide, operational UI plan suite README, original findings, final validation plan, phase handoff notes, and directly relevant changelogs/knowledge/bugs. I inspected production code and focused tests for maintainability, controller/service consistency, transaction/concurrency/idempotency, error handling, Docker/runtime safety, dead code, migration robustness, and beta blockers.

No production code was edited. I did not run the test suite; this review is based on code and artifact inspection.

# Findings

## BLOCKER - Job submit-to-agent creates assignments that do not execute public job items

Evidence:

- Public job editing stores ordered items in `JobDefinition.items()` through `JobService.addItem()` / `saveDefinition()` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:91`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:96`).
- The HTMX job submit path creates a `JOB_RUN` assignment against the public job id, but first calls `ensureLegacyJob(job)` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2353`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2356`).
- `ensureLegacyJob()` only creates a shadow `OrchestrationJob`; it does not copy `JobDefinition.items()` into legacy `OrchestrationJobItem` rows (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2389`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2393`).
- The runner still executes `JOB_RUN` through `OrchestrationJobService`, loading `OrchestrationJob` and `OrchestrationJobItem` rows, not the public `JobDefinition`/`JobWorkItem` model (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:216`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:219`).
- If a public job has no owner, the bridge uses `"unknown"` as owner (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2395`), while `OrchestrationJobService.save()` requires a real owner agent via `agentProfileService.get(job.ownerAgentId())` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationJobService.java:39`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationJobService.java:45`).

Impact:

The visible beta workflow "create job -> add items -> submit to agent" can either fail for ownerless jobs or produce a queued assignment whose runner sees zero legacy items and completes without executing the public job contents. This is a beta blocker because the UI reports "Assignment Created" while the durable runtime reads a different job model.

## BLOCKER - Plan structured editors render non-functional edit controls

Evidence:

- Existing plan scalar save preserves all complex sections from the current database state (`deliverables`, `inputs`, `outputs`, `assumptions`, `steps`, `validationCriteria`) rather than reading edited row values from the form (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:593`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:598`).
- Field rows render `hx-put="/plans/_editor/{planId}/{inputs|outputs}s"` on change (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1218`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1222`).
- There is no matching `@PutMapping` for `/plans/_editor/{planId}/inputs` or `/plans/_editor/{planId}/outputs`; the only plan field routes are POST add and DELETE remove (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:632`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:638`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:646`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:652`).
- Existing deliverable/step/validation/assumption rows render text inputs with no `name`, no save action, and no update route (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1299`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1301`).
- Add-list actions append blank strings or blank `PlanStep` rows, then immediately save them (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:784`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:799`).

Impact:

The plan editor looks structured, but users cannot reliably edit persisted inputs, outputs, deliverables, steps, assumptions, or validation criteria from the rendered controls. Field changes can issue 405s, and main Save discards complex row edits. This undermines the core plan/task-template beta surface.

## HIGH - Workspace write leases are not concurrency-safe

Evidence:

- The package guide says workspace leases enforce exclusive writable access (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md:8`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md:21`).
- `WorkspaceLeaseService.acquireWritable()` performs check-then-insert in separate repository calls with no service-level transaction (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseService.java:46`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseService.java:56`).
- The schema creates a non-unique partial index on `(workspace_id, mode)` where `released_at is null` (`src/main/resources/schema.sql:417`, `src/main/resources/schema.sql:419`).
- `WorkspaceRepository.findActiveWritableLease()` selects one unreleased write lease, but the database does not prevent two concurrent inserts from both succeeding (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java:190`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java:195`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java:215`).

Impact:

Concurrent job/project execution can obtain multiple write leases on the same workspace under load. That creates corruption risk exactly where beta users will expect job/project workspaces to be durable and isolated.

## HIGH - `OrchestrationController` has become a 4,003-line service/controller monolith

Evidence:

- `OrchestrationController.java` is 4,003 lines and injects 12 dependencies, including plan, workflow, job, project, agent, inbox, output, settings, and legacy job services (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:88`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:121`).
- The web package guide says controllers should stay thin and delegate behavior to services (`src/main/java/io/mindspice/magenta2/api/web/AGENTS.md:11`, `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md:18`).
- The controller now owns domain mutations and record-copy behavior for plan fields/list sections (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:658`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:779`), job bridging (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2389`), and dashboard/agent read aggregation (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3183`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3352`).
- Several UI actions catch broad `Exception` and return HTML error fragments without an HTTP error status (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:625`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:688`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2378`), bypassing the centralized 400/409 behavior in `GlobalExceptionHandler` (`src/main/java/io/mindspice/magenta2/api/web/GlobalExceptionHandler.java:92`, `src/main/java/io/mindspice/magenta2/api/web/GlobalExceptionHandler.java:100`).

Impact:

The controller is now the main integration point for too many domains and hides failures as successful HTML responses. That makes contract regressions easy to introduce and hard to validate, especially with HTMX where HTTP status is important for failure handling.

## MEDIUM - Docker runtime is fail-fast by default and timeout cleanup is fragile

Evidence:

- `DockerRuntimeClient` is enabled when `magenta.docker.enabled` is missing (`matchIfMissing = true`) (`src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:40`).
- Startup fails if the daemon or configured image is unavailable (`src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:69`, `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:79`, `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:82`, `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:90`).
- The default config path in `application.yml` does not declare `magenta.docker.enabled`, host, image, or timeout values, so local/dev/beta behavior depends on environment defaults (`src/main/resources/application.yml:8`).
- Command execution waits once for log streaming and then again for container status using the full timeout both times (`src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:223`, `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:228`), and only force-removes the container when an exception occurs (`src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:254`, `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:257`).

Impact:

Fail-fast Docker is appropriate for hosts where container execution is mandatory, but beta startup will be brittle unless environment expectations are explicit. Long-running or stuck commands may also consume up to roughly two configured wait windows before cleanup.

## MEDIUM - Dead orchestration `app.js` still ships stale incompatible behavior

Evidence:

- Phase handoff notes already identify `app.js` as unreferenced dead code with bugs (`.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md:21`).
- Current shells reference page-specific scripts, not `app.js` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:80`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:86`).
- `app.js` still contains old JS-first agent/job flows, direct run behavior, and stale endpoint assumptions (`src/main/resources/static/js/orchestration/app.js:13`, `src/main/resources/static/js/orchestration/app.js:15`, `src/main/resources/static/js/orchestration/app.js:247`).
- It posts stale job-save payloads back to `/api/jobs` from a job detail context instead of the canonical HTMX route (`src/main/resources/static/js/orchestration/app.js:251`, `src/main/resources/static/js/orchestration/app.js:252`).

Impact:

Even if unreferenced by current pages, stale public static code invites accidental reintroduction and makes browser/debug evidence noisy. It also contradicts the HTMX-first refactor target.

## MEDIUM - Validation coverage is render-heavy and misses the highest-risk flows

Evidence:

- `OrchestrationControllerTest` constructs the controller with in-memory stubs, not Spring MVC routing or real service wiring (`src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:45`, `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:58`).
- Plan editor tests assert rendered fragments and absence of old run controls, but do not exercise the emitted `hx-put` field routes or verify persisted complex-section edits (`src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:133`, `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:139`).
- Job editor tests assert the submit button exists and no direct run button is rendered, but do not submit a public `JobDefinition` through `AssignmentService` and then run it through `OrchestrationRunnerService` (`src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:249`, `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:261`).
- `JobServiceTest` covers public `JobService.startRun()` and work-item progress (`src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java:70`, `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java:98`), but the durable assignment runner uses the separate legacy `OrchestrationJobService` path for `JOB_RUN` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:218`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:219`).

Impact:

The tests are useful for shell regressions, but they do not prove the core beta workflows execute end-to-end. This explains how the job execution split and plan edit route gap can survive a passing test count.

# Risk Assessment

The refactor materially improved contract coverage and moved many surfaces toward HTMX-first rendering, but the beta risk is still high until the public job model and durable runner use the same persistence path. The current job submit path can acknowledge work that does not execute the visible job items.

The plan editor is the second beta blocker. It has the right visual direction, but complex section edits are not yet a reliable persistence workflow.

Maintainability risk is high because the operational UI now depends on a 4,003-line controller that mixes rendering, orchestration joins, validation, record copying, and compatibility bridging. More phases will become slower and riskier unless that surface is split along page/use-case boundaries.

Runtime safety is moderate-to-high. Workspace leases need a database-backed exclusivity guarantee before concurrent project/job execution is trusted. Docker default behavior is explicit fail-fast, but beta deployment docs/config should make that operational contract obvious.

# Recommendations

1. Fix `JOB_RUN` execution before beta: either make `AssignmentService`/`OrchestrationRunnerService` validate and execute `JobDefinition`/`JobWorkItem` directly, or fully mirror public job items into legacy `OrchestrationJobItem` rows during the bridge. Prefer retiring the legacy bridge rather than deepening it.

2. Repair plan editor persistence: add explicit update endpoints for field rows and list items, or remove inline edit affordances until Save reads and persists those values correctly. Add MVC tests that issue the exact HTMX methods and verify saved `PlanDefinition` sections.

3. Add a database-enforced workspace write lease invariant. A service check is not enough; use a transaction plus a unique active-write constraint or an atomic insert/update pattern that prevents two active write leases for the same workspace.

4. Split `OrchestrationController` into smaller controllers or fragment renderers by surface: dashboard, plans, workflows, jobs/projects, agents, inbox/outputs. Keep cross-domain aggregation in services/read-model assemblers so HTTP handlers stay thin.

5. Convert broad `catch (Exception)` HTMX handlers to typed errors with appropriate status codes. Use error fragments intentionally, but do not make unexpected domain failures indistinguishable from successful partial refreshes.

6. Delete or quarantine `src/main/resources/static/js/orchestration/app.js` after confirming no shell still references it. The file is stale enough that keeping it public is a liability.

7. Make Docker beta configuration explicit: document/ship `magenta.docker.enabled`, host, image, timeout, and SELinux relabel defaults. Add a command timeout test that proves long-running containers are stopped/removed when execution exceeds the configured budget.

# Follow-ups

- Add a focused beta gate test: create an agent, create a public job with at least one plan item, submit it to an agent, acquire/execute the assignment, and assert the referenced plan item ran or failed for a real reason.
- Add an HTMX route audit test for all rendered `hx-get`, `hx-post`, `hx-put`, and `hx-delete` attributes on `/plans`, `/workflows`, `/jobs`, `/projects`, `/agents`, `/inbox`, and `/outputs`.
- Add a concurrent workspace lease test that starts two acquisitions for the same workspace and proves exactly one write lease is active.
- Add a browser validation pass after the blocker fixes, with special attention to plan row editing, job submit-to-agent, Docker status fragments, and HTMX error behavior.
