# Services/UX Architecture Refactor Risk And Testing Review

## Scope

Read-oriented review of project/job/workspace/output service behavior, REST/SSE controllers, UI validation assets, and existing test coverage for the services/frontend/UX architecture refactor.

Target architecture assumptions:

- Agents execute work.
- Projects are shared durable workspace/visibility contexts.
- Jobs are assignable, repeatable orchestration/work-unit hybrids.
- Job persistent workspaces are opt-in and per assignment.
- Services and UI must remain compatible with project leasing/locking for multi-agent project use.

Files inspected include orchestration runtime services/repositories, workspace services/repositories, plan/workflow execution services, API controllers, Playwright validation docs, the focused Playwright harness, and related unit/controller tests.

## Findings

### Useful Existing Coverage

- Assignment execution already has database-level assignment leasing. `OrchestrationRuntimeRepository.acquireLease` transitions only `QUEUED`/`INTERRUPTED` rows with expired or absent leases to `RUNNING` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java:426`), and `saveAssignmentIfLeaseOwner` protects runner-owned writes (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java:177`).
- Project write leases are acquired before project-scoped assignment work runs, and conflicts push the assignment to `WAITING` with blocker details (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:220`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:236`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:245`).
- Workspace lease tests cover exclusive write conflict, concurrent acquisition, extension ownership, release ownership, expiry reconciliation, and graceful release (`src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseServiceTest.java:50`, `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseServiceTest.java:61`, `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseServiceTest.java:181`, `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseServiceTest.java:248`, `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseServiceTest.java:270`).
- Workspace/output architecture coverage is strong for path confinement, project-effective output roots, per-assignment persistent job workspaces, attribution, symlink handling, and loose artifact discovery (`src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java:85`, `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java:98`, `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java:115`, `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`).
- Workflow and assignment tests cover waiting workflow preservation, resume, project link materialization for tools, and isolated project-scoped job workspaces (`src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1047`, `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1155`, `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1262`).
- Public submission controller tests verify that plan/task/workflow/job public run routes submit agent assignments instead of running inline, and preserve `projectId`/`workspaceId` request fields (`src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java:41`, `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java:126`, `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java:157`, `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java:183`).
- Browser validation docs are mature for live chat/SSE and operational UI, including MCP-first requirements, isolated SQLite, console/network capture, SSE parsing, and MCP failure recovery (`.internal-dev/knowledge/live-chat-mcp-workflow-testing.md:1`).
- The checked-in Playwright harness covers page reachability, mobile agent shell, plan HTMX persistence, workflow HTMX save/validate/submit separation, and browser diagnostic capture (`tests/playwright/public-alpha-harness.spec.js:75`, `tests/playwright/public-alpha-harness.spec.js:93`, `tests/playwright/public-alpha-harness.spec.js:123`, `tests/playwright/public-alpha-harness.spec.js:176`).

### Gaps

- No test currently proves that two simultaneous project-scoped assignments on the same project result in one `RUNNING`/`COMPLETED` path and one `WAITING` project-lease conflict. Existing job workspace tests run assignments sequentially (`src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1318`).
- No service/controller test proves that project membership removal, project deletion, job definition edits, or job item edits are blocked, deferred, or made safe while a project/job assignment is actively leased. Current controllers mutate directly (`src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:58`, `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:72`, `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:100`, `src/main/java/io/mindspice/magenta2/api/web/JobController.java:85`, `src/main/java/io/mindspice/magenta2/api/web/JobController.java:116`, `src/main/java/io/mindspice/magenta2/api/web/JobController.java:136`).
- No optimistic concurrency/revision tests cover lost updates for project, job, workflow, or plan editors. Repository saves are last-write-wins updates keyed only by id (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRepository.java:72`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectRepository.java:54`).
- Output queries and content/download routes are global read APIs with filter-only visibility, not capability checks. That may be acceptable in public alpha, but the refactor must not accidentally imply project-scoped visibility enforcement that does not exist (`src/main/java/io/mindspice/magenta2/api/web/OutputController.java:40`, `src/main/java/io/mindspice/magenta2/api/web/OutputController.java:74`, `src/main/java/io/mindspice/magenta2/api/web/OutputController.java:106`).
- Output materialization uses deterministic filenames for non-file outputs and overwrites existing files in the same run output directory (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java:423`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java:436`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java:458`). This is fine for one writer per run, but unsafe if refactor introduces parallel writes into a shared run output directory.
- `WorkflowRunner` runs ready node batches concurrently (`src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java:333`) and materializes final/log outputs into one workflow output directory (`src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java:557`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java:626`). Current tests cover async context inheritance, not output filename collision or parallel artifact save ordering.
- `WorkspaceService.agentWorkspace/projectWorkspace/jobWorkspace` perform find-then-create followed by insert with a unique `(owner_type, owner_id)` index (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:43`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:55`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java:610`). Concurrent first access may throw instead of returning the already-created workspace.
- Scheduler recurrence firing can create repeated runs without advancing or atomically claiming recurrence state in the reviewed method (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:382`). This is a risk if recurring jobs become a primary assignment path in the refactor.
- Playwright harness does not yet cover project/job assignment flows, project lease display/release request, output filters/content clicks, or multi-agent/concurrent UI state changes. It is useful smoke/regression coverage but insufficient as a phase gate for this refactor.

## Risk Assessment

| Severity | Risk | Evidence | Mitigation |
| --- | --- | --- | --- |
| Critical | Concurrent project assignments can serialize only if every writer goes through `OrchestrationRunnerService` project lease acquisition; any direct task/workflow/job execution path or future UI shortcut can bypass project write exclusion. | Lease acquisition happens in runner only (`OrchestrationRunnerService.java:220`, `OrchestrationRunnerService.java:236`); task/workflow services allocate effective project workspaces directly when context is present (`PlanService.java:923`, `WorkflowRunner.java:714`). | Require all public/user-facing execution to enter via assignment. Add direct-service guard tests or keep direct execution test-only/internal. Gate phase with concurrent same-project assignment test. |
| Critical | Editing/deleting project membership, project records, job definitions, or job items while assignments are active can invalidate execution context mid-run. | Controllers call service mutations without active-lease or active-assignment checks (`ProjectController.java:58`, `ProjectController.java:72`, `ProjectController.java:100`, `JobController.java:85`, `JobController.java:116`, `JobController.java:136`). | Add explicit policy: block, snapshot, or version definitions/memberships at assignment start. Test active-run mutation conflicts before UX exposes controls. |
| High | Lost updates in UI editors can silently overwrite concurrent edits from another browser/agent. | Repository saves are id-based upserts without version preconditions (`JobRepository.java:72`, `ProjectRepository.java:54`); Playwright plan/workflow tests only cover single-user persistence (`public-alpha-harness.spec.js:123`, `public-alpha-harness.spec.js:176`). | Add `updatedAt`/revision precondition for editors or document last-write-wins. Gate with controller tests for stale update conflict and Playwright two-tab stale save scenario if UX exposes revisions. |
| High | Output visibility can be misleading or over-broad during project/job refactor. Global output APIs return by optional filters and artifact IDs can be loaded directly. | `OutputController.query/content/download` have no project membership/agent capability checks (`OutputController.java:40`, `OutputController.java:74`, `OutputController.java:106`). | Decide alpha visibility contract explicitly. If project visibility is intended, add service-level authorization/filter tests before changing UI copy. |
| High | Parallel workflow output materialization may overwrite same-named artifacts/files. | Workflow runs ready nodes concurrently (`WorkflowRunner.java:333`), while materialization writes deterministic names with overwrite semantics (`OutputArtifactService.java:423`, `OutputArtifactService.java:436`, `OutputArtifactService.java:458`). | Keep one writer per output key or add collision-safe filenames/unique artifact constraint. Add parallel workflow output collision test. |
| High | Project lease conflict leaves assignments `WAITING`, but there is no clear automatic wakeup when the lease is released. | Conflict branch saves `WAITING` with blocker details (`OrchestrationRunnerService.java:239`); `findRecoverableAssignments` only picks `QUEUED`/`INTERRUPTED` (`OrchestrationRuntimeRepository.java:387`). | Add explicit resume/retry UX and service operation for workspace-blocked assignments, or auto-requeue on lease release. Gate with conflict-then-release-then-resume test. |
| Medium | Concurrent first access to a workspace owner can hit unique index failures. | `WorkspaceService.projectWorkspace` uses `findByOwner(...).orElseGet(createWorkspace(...))` (`WorkspaceService.java:55`) with unique owner index (`WorkspaceRepository.java:610`). | Make create idempotent under unique conflict. Add concurrent `projectWorkspace` first-access test. |
| Medium | Job recurrence can duplicate runs if multiple scheduler loops see the same due recurrence. | `fireDueRecurrences` reads due recurrences and starts runs without claim/advance in the reviewed method (`JobService.java:382`). | Claim/advance recurrence atomically or mark due firing with unique id. Add concurrent recurrence firing test if scheduler phase changes. |
| Medium | Cancelling `JobRun` directly can diverge from assignment lifecycle. | `JobController.cancelRun` calls `jobService.cancelRun` (`JobController.java:206`) while assignment cancellation is separate (`AgentOrchestrationController.java:150`). | Prefer assignment cancellation for job assignment runs; add compatibility test that job-run cancel updates/blocks corresponding assignment or is disabled for assignment-owned runs. |
| Medium | Project graceful release is advisory only; active holders are not required to observe it before completion. | `requestWorkspaceRelease` marks `releaseRequested` (`ProjectController.java:138`, `WorkspaceLeaseService.java:161`); runner heartbeat only extends lease (`OrchestrationRunnerService.java:252`). | Treat release request as UI signal unless implementing cooperative cancellation/yield. Add status display test and avoid implying immediate unlock. |
| Low | The Playwright harness cannot sign off changed project/job/output interaction flows yet. | Harness scope excludes project/job assignment and output drilldown (`tests/playwright/README.md:27`). | Add focused scenarios per changed surface, run through MCP subagent, and capture screenshots. |

## Recommendations

### Phase-Gated Test Matrix

| Phase | Gate Tests |
| --- | --- |
| Service architecture foundation | `mvn test -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest,OutputArtifactServiceAttributionTest,JobServiceTest,ProjectServiceTest,OrchestrationRuntimeTest` plus new concurrent same-project assignment lease conflict test. |
| Project/workspace mutation policy | Add service/controller tests for active project lease summary, release request, membership removal during active assignment, project delete during active assignment, and workspace blocked assignment resume/requeue. |
| Job assignment/workspace refactor | Add tests for job definition snapshot vs live edit policy, active job item edit/delete conflict, per-assignment persistent workspace under project and agent contexts, direct job-run cancel compatibility, and recurring job duplicate prevention if recurrence is touched. |
| Output visibility/materialization | Add tests for project/job/global filters, artifact content/download by project/job/run, collision-safe materialization, parallel workflow final/log outputs, and explicit visibility/authorization contract. |
| API compatibility | Extend `PublicRunSubmissionControllerTest`, `OperationalUiContractControllerTest`, `WorkspaceControllerTest`, `TaskStreamSupportTest`, and `WorkflowStreamSupportTest` for any changed route/payload/status shape. Include old-field compatibility for `workspaceId` and new project/job fields. |
| UI/UX | Playwright MCP subagent validates `/projects`, `/jobs`, `/outputs`, plan submit, workflow submit, agent assignment tabs, project workspace lease display/release request, and output drilldown. Include desktop and mobile screenshots of changed surfaces. |
| Final regression | Full Maven test suite, Spring context smoke, focused Playwright harness, and MCP browser scenario pass against isolated SQLite. |

### New Targeted Tests

- `OrchestrationRunnerServiceProjectLeaseConcurrencyTest`: create two same-project assignments, use blocking chat/task stub, start both through runner/executor, assert exactly one acquires project lease and the other becomes `WAITING` with `workspaceBlocker`.
- `ProjectMutationDuringLeaseTest`: with active project lease, assert chosen policy for remove member, delete project, update project, and release request.
- `JobDefinitionActiveAssignmentPolicyTest`: while a job assignment is running, assert chosen policy for edit job metadata, add/update/delete item, delete job, and cancel job run.
- `WorkspaceServiceConcurrentCreationTest`: concurrent first calls to `projectWorkspace(projectId, ...)` return one workspace instead of unique-index failure.
- `WorkflowOutputCollisionTest`: parallel workflow nodes produce identical output names; assert collision-safe persisted files/artifacts or deterministic rejection.
- `OutputVisibilityContractTest`: query by project/job/agent/run and direct artifact content/download behavior matches documented visibility model.
- `PublicSubmissionCompatibilityTest`: submit task/workflow/job with and without `projectId`, `workspaceId`, `agentId`, `modelOverride`, and legacy fields.
- `TwoTabEditorStaleSaveTest` if optimistic concurrency is implemented: stale browser save should show conflict, not silently overwrite.

### Validation Commands

Baseline read/write service validation:

```bash
mvn test -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest,OutputArtifactServiceAttributionTest,JobServiceTest,ProjectServiceTest,OrchestrationRuntimeTest
mvn test -Dtest=PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,WorkspaceControllerTest,TaskStreamSupportTest,WorkflowStreamSupportTest,AgentOrchestrationControllerTest
```

Full unit/controller regression:

```bash
mvn test
```

Bounded Spring context smoke:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-smoke.sqlite?foreign_keys=true'
```

Focused browser harness against isolated SQLite:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-playwright.sqlite?foreign_keys=true --magenta.executor.chat-threads=4'
MAGENTA_PLAYWRIGHT_BASE_URL=http://localhost:18080 npx playwright test tests/playwright/public-alpha-harness.spec.js
```

MCP/Playwright requirements:

- Run Playwright validation in a subagent, not inline.
- Use the MCP-first process from `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
- Use a port allowed by the MCP config, currently `8080` or `18080`.
- Use a fresh isolated SQLite database for validation.
- Capture screenshots of changed UI surfaces.
- Capture console and network diagnostics.
- For SSE paths, assert named events and persisted backend state, not only final text.
- If MCP is blocked by profile lock or timeout, follow the documented recovery steps; MCP failure blocks UI sign-off unless the user explicitly approves a fallback.

## Follow-ups

- Decide and document active-run mutation policy before implementing UI controls that alter projects, memberships, job definitions, or job items.
- Decide whether output APIs remain public-alpha global read models or become project/agent-scoped visibility surfaces.
- Decide whether project lease release is only advisory or should trigger cooperative cancellation/yield/requeue behavior.
- Decide whether editor concurrency is last-write-wins or revision-checked. This should be explicit in API docs and UI behavior.
- If recurrence behavior is touched, add atomic due-claiming before considering scheduler changes safe for multi-agent use.
