# Workspace File Architecture Refactor Implementation Plan

## Objective

Refactor Magenta's workspace and file architecture so every task, workflow, and job run resolves to exactly one effective durable workspace: project workspace when a project is attached, otherwise agent workspace. Separate durable workspace files, per-run temp files, and explicit output artifacts while preserving current API compatibility and chat-file separation.

The implementation must correct known execution defects around workflow `WAITING` assignment handling and async orchestration context propagation. It must also stage loose artifact discovery behind gated/constrained compatibility behavior rather than removing it abruptly.

## Inputs And Assumptions

Confirmed inputs:

- Intended architecture: `.internal-dev/notes/current-architecture-focus.md`.
- Cross-agent review findings: `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`.
- Internal workflow guide: `.internal-dev/AGENTS.md`.
- Existing plan setup: `.internal-dev/plans/workspace-file-architecture-refactor/phase-00-orchestration-setup.md`.

Confirmed constraints:

- Code-modifying implementation phases must be serial.
- Each implementation phase must run as a subagent subplan with its own context and write ownership.
- Each implementation phase must validate before commit.
- Each completed phase should end with a commit after validation.
- Planning/review/implementation agents should use high reasoning.
- Testing/validation agents should follow repo validation guidance and use `gpt-5.3-codex` with medium reasoning.
- Final architecture/code review should use xhigh reasoning.
- Chat files remain separate and conversation-scoped.
- Existing API payloads using `workspaceId` must remain compatible.
- New project context must be modeled as explicit `projectId`.
- Project owner-agent removal is a dedicated migration/API phase.

Assumptions to verify before source edits:

- The active branch remains `workspace-file-architecture-refactor`.
- The local data root can be reset or archived for clean validation when needed.
- SQLite schema migrations are expected to be additive/compatible in the short term.
- No external API clients require project creation to keep `ownerAgentId` as a required field after migration; if such clients exist, use a compatibility alias/deprecation field.
- Loose artifact discovery can be defaulted to compatibility-on for the first wave, then disabled by configuration in a later wave.

## Scope

In scope:

- Central effective workspace resolution for agent-scoped and project-scoped work.
- Shared workspace layout support: `work/`, `outputs/`, `runs/`, `scratch/`, and `jobs/`.
- Runtime aliases for file and shell tools: `workspace/`, `work/`, `outputs/`, `run/`, `scratch/`, optional `job/`.
- Persisting effective workspace and output metadata at run creation.
- Task/plan output placement under the effective workspace.
- Explicit output publishing path and gated/constrained loose artifact discovery.
- Workflow temp/output separation, waiting/resume correctness, and context propagation.
- Project API/schema migration away from required owner-agent semantics.
- Explicit `projectId` on task/workflow/job submission APIs while preserving `workspaceId`.
- Job persistent workspace opt-in and assignment/run-keyed workspace isolation.
- Tests, smoke validation, docs updates, `.internal-dev` closeout, phase commits, and final review.

Out of scope:

- Renaming every public "plan" concept to "task".
- Migrating ordinary chat files into project/agent output artifacts.
- Building broad scheduler, queue, subagent orchestration, or locking systems beyond needed lease compatibility.
- Removing loose artifact discovery entirely in the first implementation wave.
- Full legacy data migration from arbitrary old local data roots.
- Large UI redesign unrelated to changed semantics.

## Current-State Analysis

Architecture divergence:

- `WorkspaceDirectoryService` already has path helpers for agent workspaces, project workspaces, assignment project links, workflow temp, and output paths, but output helpers are still agent-centric for task/plan runs.
- `PlanService` creates and completes runs, calls `OutputArtifactService.discoverLooseArtifacts(...)`, and currently relies on output path or thread-local context for artifact attribution.
- `TaskService` wraps plan behavior and inherits output placement and artifact discovery behavior.
- `AgentFileToolService` and `AgentShellToolService` use `OrchestrationTaskContextHolder` and current path conventions for alias behavior.
- `WorkflowRunner` executes workflow nodes, materializes workflow outputs, starts child task runs, and persists `WAITING` workflow state.
- `OrchestrationRunnerService` bridges assignments to workflows/jobs and currently treats non-completed workflow results as failed assignments.
- `ProjectService`, `ProjectRepository`, `ProjectController`, and the operational UI still assume `ownerAgentId` is part of project creation and display.
- `JobService`, `JobRepository`, and `JobController` support `projectId` fields but persistent workspace policy is definition-scoped and not explicitly opt-in per assignment/run.
- Legacy `OrchestrationJobService` and `OrchestrationRuntimeRepository` still maintain `orchestration_jobs` and work assignment behavior alongside current `job_definitions`.

Known concrete defects:

- Workflow approvals can leave `WorkflowRun.status = WAITING` while the owning assignment becomes `FAILED`.
- Workflow task nodes lose thread-local orchestration context when dispatched to async executor threads.
- Workflow outputs are stored under workflow temp paths instead of effective durable workspace outputs.
- Project-scoped task outputs can land under agent output paths.
- Loose artifact discovery indexes incidental files without an explicit publish contract.

Compatibility risks:

- Controllers and stream support records already accept `workspaceId`; callers may depend on this field name.
- Tests assert existing agent output paths in several places.
- Project owner-agent fields appear in API records, UI labels, selectors, route tests, schema tests, and repository tests.
- Current output artifact tests rely on loose discovery behavior.

## Target Design

### Effective Workspace Resolver

Introduce a small shared service, tentatively `EffectiveWorkspaceResolver`, under the orchestration/workspaces package. It resolves:

```text
projectId present -> project durable workspace
projectId absent  -> agent durable workspace
```

The resolver should return a record similar to:

```java
record EffectiveWorkspace(
    String ownerType,          // PROJECT or AGENT
    String ownerId,            // projectId or agentId
    String agentId,
    String projectId,
    String workspaceId,
    Path root,
    Path workDir,
    Path outputsDir,
    Path runsDir,
    Path scratchDir,
    Optional<Path> jobWorkspaceDir
) {}
```

The exact names may follow local code style, but the returned value must be explicit enough that services do not reconstruct path semantics independently.

Responsibilities:

- Validate IDs and path segments using existing directory validation rules.
- Create shared layout directories lazily through `WorkspaceDirectoryService`.
- Preserve `workspaceId` as an existing compatibility identifier, not as a replacement for `projectId`.
- Include work-unit/run output path helpers to avoid collisions:
  - `outputs/tasks/<taskId>/<runId>/`
  - `outputs/workflows/<workflowId>/<runId>/`
  - `outputs/jobs/<jobAssignmentId>/<runId>/`

### Runtime Context And Aliases

Update orchestration context to carry the resolved effective workspace plus the run temp path. File and shell tools should expose:

- `workspace/` -> effective durable workspace root.
- `work/` -> effective durable workspace `work/`.
- `outputs/` -> effective durable workspace output root or current run output directory when command context requires a run-specific output target; document and test the chosen behavior.
- `run/` -> current run temp/execution directory.
- `scratch/` -> effective durable workspace `scratch/`.
- `job/` -> current persistent job workspace only when configured.

Implementation must keep existing tests passing or intentionally update them with compatibility assertions.

### Output Tracking

Explicit outputs are the target source of truth. Add or expose a publishing path that records an artifact only when a run declares or publishes an output. Loose discovery remains available only as a confined compatibility path:

- Gate with configuration or a service-level policy flag.
- Confine by realpath under the configured data root and expected run output directory.
- Preserve existing behavior for compatibility while adding tests that can run with discovery disabled.
- Avoid indexing chat files.

### Workflow Execution

Workflow runs must keep temp and durable outputs separate:

- Temp: `runtime/workflow-runs/<runId>` or equivalent run temp path.
- Durable output: effective workspace `outputs/workflows/<workflowId>/<runId>/`.

Workflow status mapping:

- `WAITING` workflow -> assignment `WAITING`.
- `COMPLETED` workflow -> assignment `COMPLETED`.
- `FAILED` workflow -> assignment `FAILED`.
- `CANCELLED` workflow, if represented, -> assignment `CANCELLED` or nearest existing status.
- Nonterminal `RUNNING` should not be marked failed solely because it is non-completed.

Async context propagation:

- Capture `OrchestrationTaskContext` before async dispatch.
- Set it inside the worker thread for the duration of node execution.
- Clear or restore prior context in `finally`.
- Add a regression test that fails with a plain unpropagated ThreadLocal.

### Projects

Projects become shared durable workspace/visibility abstractions without a required owner agent.

Migration/API target:

- Make project owner nullable or remove required usage in service validation.
- Preserve legacy `ownerAgentId` response field temporarily as nullable/deprecated if public records already expose it.
- Project creation accepts explicit membership/agent association separately from ownership.
- Existing project membership remains the way to associate agents with projects.
- UI labels and selectors should stop implying a project has one permanent owner.

### Jobs

Jobs remain executable work units and can optionally have persistent workspaces.

Target behavior:

- Job definitions can be associated with a project and/or default agent runtime path.
- Persistent job workspace is opt-in.
- Persistent job workspace identity is assignment/run scoped, for example `jobs/<jobAssignmentId>/`.
- Multiple assignments of the same job definition do not share persistent workspace unless an explicit future policy allows it.
- Job explicit outputs publish under the effective durable workspace and include job assignment/run metadata.

### Leases And Race Mitigation

Near-term behavior must remain lease-compatible:

- Project-attached writes acquire or verify a writable project workspace lease where existing lease primitives support it.
- Output directories include work-unit/run identity to avoid collisions.
- No cleanup path deletes project/agent durable workspace files, persistent job workspaces, or active/waiting temp directories.

## Implementation Plan

### Phase 01: Baseline Validation And Characterization

Goal:

- Capture current behavior and add targeted characterization/regression tests around known divergences before broad path changes.

Edit ownership:

- Test files only unless a tiny test fixture helper is needed.

Steps:

1. Run baseline test selection:
   - `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest`
2. Add failing or compatibility-aware tests for:
   - Project-scoped task run output placement.
   - Workflow `WAITING` assignment mapping.
   - Workflow async context propagation into task node execution.
   - Workflow final output durable placement separate from temp.
   - Active/waiting temp retention.
   - Loose discovery policy can be gated off.
   - Chat files are not output artifacts.
3. Keep tests narrow and deterministic. Where the implementation cannot pass yet, mark with local disabled/ignored rationale only if the repo normally permits that. Prefer writing tests in the same phase as the fix when disabled tests would create noise.
4. Commit only after the selected tests pass. If characterization tests intentionally expose current bugs, include the corresponding fix in the same phase or split into a no-commit notes artifact.

Validation gate:

- Targeted tests above.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` if no required secrets/services block startup.

### Phase 02: Effective Workspace Resolver And Run Metadata

Goal:

- Centralize workspace decisions and persist effective workspace/output metadata at run creation without changing every caller at once.

Edit ownership:

- `ai/orchestration/workspaces`
- `ai/chat/plan`
- schema tests and focused service tests

Steps:

1. Add `EffectiveWorkspaceResolver` and returned data carrier record.
2. Extend `WorkspaceDirectoryService` with shared layout helpers:
   - `agentWorkspaceRoot(agentId)`
   - `projectWorkspaceRoot(projectId)`
   - `workDir(...)`
   - `outputsDir(...)`
   - `runsDir(...)`
   - `scratchDir(...)`
   - work-unit output helpers.
3. Update `PlanRun` creation path to set effective `workspaceId`, `projectId` where supported, output directory, and run temp directory consistently.
4. Add schema migration/backfill only as needed and keep old fields readable.
5. Do not remove project links or loose discovery in this phase.

Validation gate:

- Workspace directory/path confinement tests.
- Plan repository/service tests.
- Schema migration tests.
- Spring context smoke.

### Phase 03: Task/Plan Runtime Paths, Aliases, And Output Publishing

Goal:

- Move task/plan runtime behavior onto the resolver and introduce explicit output publishing while retaining gated loose discovery.

Edit ownership:

- `PlanService`
- `TaskService`
- file/shell tool services
- output artifact service
- related tests

Steps:

1. Update task/plan run start to use resolver-selected durable output path.
2. Update `OrchestrationTaskContext` population so file/shell tools can resolve stable aliases.
3. Update `AgentFileToolService` and `AgentShellToolService` alias behavior to match target contract.
4. Add explicit output publishing API/service method. Prefer a service method first; expose a tool/controller only if required by current workflows.
5. Wrap `discoverLooseArtifacts` behind a compatibility policy and realpath confinement.
6. Ensure task temp cleanup never removes durable workspace files or active run temp.
7. Update prompts/docs in a later docs phase, but code should already support explicit publishing.

Validation gate:

- `PlanServiceTest`
- `TaskService` tests if present
- `AgentFileToolServiceTest`
- `AgentShellToolServiceTest`
- `OutputArtifactServiceAttributionTest`
- Path traversal/confinement tests.
- Spring context smoke.

### Phase 04: Workflow Execution Correctness And Durable Outputs

Goal:

- Fix workflow `WAITING` assignment status, propagate orchestration context across async workflow task nodes, and move workflow final outputs to durable output paths.

Edit ownership:

- `WorkflowRunner`
- `WorkflowService`
- `OrchestrationRunnerService`
- workflow repository/model tests
- runtime tests

Steps:

1. Update assignment status mapping so workflow `WAITING` becomes assignment `WAITING`, not `FAILED`.
2. Add context propagation around async task node execution:
   - capture current context before executor submission;
   - set it inside worker;
   - restore/clear in `finally`.
3. Add workflow output resolver usage:
   - keep temp under runtime run temp;
   - publish final outputs under effective workspace `outputs/workflows/<workflowId>/<runId>/`.
4. Ensure waiting workflow temp is retained until terminal completion/failure.
5. Ensure resume paths use the original run/workspace context and do not create inconsistent new run attribution.

Validation gate:

- `WorkflowRunnerTest`
- `OrchestrationRuntimeTest`
- targeted assignment/resume tests.
- output artifact attribution tests.
- Spring context smoke.

### Phase 05: Project API And Owner-Agent Migration

Goal:

- Remove required project owner-agent semantics while preserving compatible response/request shapes and introducing explicit `projectId` submission paths.

Edit ownership:

- `Project`
- `ProjectService`
- `ProjectRepository`
- `ProjectController`
- API request records in task/workflow/plan/job controllers and stream support
- operational UI project forms/labels
- route/schema/UI tests

Steps:

1. Make project owner nullable or compatibility-only at schema and repository boundaries.
2. Update project creation so owner agent is not required. If `ownerAgentId` is supplied by legacy callers, create a membership instead of treating it as permanent ownership.
3. Preserve existing response fields temporarily where public records expose `ownerAgentId`; populate nullable value and document compatibility.
4. Add explicit `projectId` to task, workflow, plan, assignment, and relevant stream request records where missing.
5. Compatibility rule:
   - If `projectId` is present, use it.
   - Else if old `workspaceId` references a project workspace and current code can resolve it safely, map it to project context.
   - Else preserve old `workspaceId` behavior.
6. Update UI labels from owner-focused language to membership/context language.
7. Do not make projects executable; work still runs through agents.

Validation gate:

- `ProjectServiceTest`
- `ProjectRepositoryTest`
- `WorkspaceRepositorySchemaMigrationTest`
- `PublicApiRouteBindingTest`
- `OrchestrationControllerTest`
- `OperationalUiContractControllerTest`
- Spring context smoke.
- Focused Playwright validation if UI forms or interactions changed, using the repo MCP workflow.

### Phase 06: Job Workspace Policy And Legacy Orchestration Reconciliation

Goal:

- Make job persistent workspace behavior explicit, project-aware, and assignment/run isolated while reconciling legacy orchestration job behavior.

Edit ownership:

- `JobService`
- `JobRepository`
- `AssignmentService`
- `OrchestrationRunService`
- `OrchestrationRuntimeRepository`
- `OrchestrationJobService` if still active
- job/controller tests

Steps:

1. Add explicit persistent workspace configuration to job definitions or job assignment/run metadata.
2. Allocate persistent job workspace only when configured.
3. Key persistent job workspaces by assignment/run identity, not by job definition alone.
4. Link job workspace metadata to effective durable workspace and output artifact metadata.
5. Ensure project-scoped jobs publish outputs under project workspace outputs.
6. Decide and implement one controlled path for legacy `OrchestrationJobService`:
   - compatibility-only with no new behavior; or
   - migration to current `JobService`; or
   - removal only if tests and API prove it is unused.
7. Add tests proving two assignments of one job definition do not share persistent workspace by accident.

Validation gate:

- Job service/repository tests.
- assignment/runtime tests.
- schema migration tests.
- output artifact attribution tests.
- Spring context smoke.

### Phase 07: Integration, Documentation, Final Review, And Closeout

Goal:

- Verify the refactor as a coherent architecture, update docs and package guides, complete `.internal-dev` workflow, run final xhigh review, remediate findings, and commit closeout.

Edit ownership:

- Docs and `.internal-dev` artifacts.
- Narrow remediation code edits only after review findings, serially.

Steps:

1. Update `docs/` for behavior/API/service/schema/config changes.
2. Update relevant package `AGENTS.md` files if package responsibilities changed.
3. Add `.internal-dev/changelogs/` entry.
4. Record any out-of-scope bugs under `.internal-dev/bugs/`; ask the user before filing GitHub Issues.
5. Capture reusable insights under `.internal-dev/knowledge/`.
6. Confirm deferred future ideas before recording under `.internal-dev/notes/`.
7. Run final validation suite.
8. Run xhigh final architecture/code review.
9. Run serial remediation loops until review blockers are fixed or explicitly accepted by the user.
10. Commit validated closeout artifacts.

Validation gate:

- Full targeted test suite from all phases.
- `mvn test` if runtime permits.
- Spring context smoke.
- Playwright validation for changed UI surfaces, if any.
- Final xhigh review.

## Validation Plan

Baseline and phase-local validation:

- Run phase-targeted tests immediately after each code-editing phase.
- Run a bounded Spring context startup after backend/application wiring phases:
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Run Playwright only for changed UI interactions and only through the repo-required MCP/subagent workflow.
- Record validation result, failures, remediation, and commit hash in `agent-notes.md`.

Required regression coverage:

- Effective workspace resolution chooses project workspace when `projectId` is attached.
- Agent-scoped runs still use agent workspace.
- Existing `workspaceId` payloads remain accepted.
- `projectId` payloads are preferred over `workspaceId` compatibility.
- Task/plan outputs for project-scoped work land under project outputs.
- Workflow outputs land under durable workflow output path, not temp.
- Workflow `WAITING` maps to assignment `WAITING`.
- Workflow resume continues from waiting state.
- Async workflow task nodes see orchestration context.
- Active/waiting temp directories are retained.
- Terminal cleanup does not delete durable workspace files.
- Persistent job workspaces are opt-in and assignment/run isolated.
- Loose discovery is confined and gateable.
- Explicit output publishing records artifacts with correct attribution.
- Chat files are not indexed as output artifacts by default.
- Output reads/downloads remain confined under the configured data root.
- Project creation no longer requires owner agent, but legacy owner fields remain compatible.

Suggested final command set:

```bash
mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,WorkspaceRepositoryAttributionTest,WorkspaceLeaseServiceTest,WorkspacePathSegmentValidationTest,ProjectServiceTest,ProjectRepositoryTest,PublicApiRouteBindingTest,OperationalUiContractControllerTest,OrchestrationControllerTest
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If any command cannot run because of required local services, secrets, or infrastructure, stop and consult the user before treating the phase as complete.

## Handoff Checklist

- Read `.internal-dev/notes/current-architecture-focus.md`, this plan, `orchestration-suite.md`, and `agent-notes.md`.
- Confirm current branch and worktree state before each phase.
- Do not revert or overwrite unrelated changes.
- Run only one code-editing subagent at a time.
- Give each implementation subagent a narrow write boundary and the relevant phase file.
- Run validation after each implementation phase.
- Remediate failed validation before starting the next implementation phase.
- Commit each completed phase after validation passes.
- Append concise notes to `agent-notes.md` after every phase, validation pass, remediation pass, and closeout step.
- Preserve chat file separation.
- Preserve `workspaceId` compatibility and add explicit `projectId`.
- Keep loose artifact discovery gated/constrained until explicit publishing has been adopted.
- Run final xhigh architecture/code review before closeout.
- Complete docs, package guide updates, `.internal-dev` changelog, knowledge, notes, bug capture, and commit workflow before final handoff.
