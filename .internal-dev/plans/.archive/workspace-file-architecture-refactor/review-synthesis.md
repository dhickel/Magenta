# Review Synthesis: Workspace File Architecture Refactor

## Scope

This synthesis summarizes the five completed read-only review lanes recorded in `agent-notes.md` and translates them into implementation constraints for the workspace/file architecture refactor. It is planning-only and does not change source behavior.

Inputs:

- `.internal-dev/notes/current-architecture-focus.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`
- Targeted source lookup for concrete file and test anchors

## Review Lane Summary

### Review A: Workspace And File Divergence

Review A found that task tools currently treat `workspace/` as run temp rather than the effective durable workspace. Project-scoped execution is represented mostly through an assignment project link rather than making the project workspace the durable target. Project outputs can still land under agent output directories, and direct task/workflow submission paths lack a first-class project context.

Implementation implication:

- Add a central effective workspace resolver before changing individual execution paths.
- Make aliases explicit: `workspace/`, `work/`, `outputs/`, `run/`, `scratch/`, and optional `job/`.
- Preserve `workspaceId` compatibility while adding clear `projectId` semantics.

Concrete anchors:

- `WorkspaceDirectoryService`
- `AgentFileToolService`
- `AgentShellToolService`
- `PlanService`
- `TaskService`
- `TaskController`
- `WorkflowController`
- `PlanController`

### Review B: Task/Plan And Workflow Execution

Review B found several execution defects and divergence points:

- Assignment-backed workflows can persist `WAITING`, but `OrchestrationRunnerService.runWorkflow` marks any non-completed workflow assignment as `FAILED`.
- Workflow runs use `runtime/workflow-runs/<runId>` as both temp and output directory.
- Task/plan output directories are agent-scoped even for project-scoped work.
- Workflow task nodes execute on async threads and lose `OrchestrationTaskContextHolder` because it is a plain `ThreadLocal`.
- `PlanRun.workspaceId` is not populated when runs start; artifact attribution is reconstructed later.

Implementation implication:

- Treat workflow `WAITING` handling and workflow context propagation as concrete defects, not optional cleanup.
- Persist effective workspace/output metadata at run creation.
- Separate workflow run temp from durable workflow outputs.
- Keep task/plan compatibility while making task/workflow work-unit metadata clearer.

Concrete anchors:

- `OrchestrationRunnerService`
- `WorkflowRunner`
- `WorkflowService`
- `WorkflowRun`
- `WorkflowRunStatus`
- `OrchestrationTaskContextHolder`
- `PlanRun`
- `PlanRepository`

### Review C: Job, Project, And Orchestration Divergence

Review C found that projects still require owner agents across API, schema, services, and UI. Assignment/run APIs do not consistently expose `projectId`. Project execution works indirectly through job definitions, and job workspaces are unconditional per job definition instead of opt-in per assignment or instance. Project-scoped outputs are still written under agent output paths. Legacy `OrchestrationJobService` and `orchestration_jobs` remain alongside current `JobService` and `job_definitions`.

Implementation implication:

- Project owner-agent removal must be an explicit migration/API phase.
- Introduce `projectId` without repurposing existing `workspaceId` payloads.
- Resolve effective durable workspace centrally for jobs, tasks, and workflows.
- Make persistent job workspaces opt-in and keyed by assignment/run identity, not job definition alone.
- Decide whether legacy orchestration job tables/services remain compatibility-only, are migrated, or are retired in a separate controlled step.

Concrete anchors:

- `Project`
- `ProjectService`
- `ProjectRepository`
- `ProjectController`
- `JobService`
- `JobRepository`
- `JobController`
- `AssignmentService`
- `OrchestrationRuntimeRepository`
- `OrchestrationJobService`
- `OrchestrationController`

### Review D: Loose Artifact Discovery

Review D found direct loose artifact discovery only in `OutputArtifactService.discoverLooseArtifacts(...)`, called by `PlanService.completeRun(...)`. Existing tests depend on this behavior. The current behavior is a shallow direct-file scan of the run output directory, extension-inferred, with `discovered_*` artifact names and no explicit publish contract.

Implementation implication:

- Do not hard-remove loose artifact discovery in the first implementation wave.
- Gate and confine it first, with realpath/data-root checks and explicit config.
- Add an explicit output publishing path before defaulting loose discovery off.
- Update prompts/tools/docs so generated deliverables are explicitly published.

Concrete anchors:

- `OutputArtifactService`
- `PlanService.completeRun`
- `OutputArtifactServiceAttributionTest`
- `PlanServiceTest`

### Review E: Testing And Risk Planning

Review E found strongest existing coverage around path confinement, lease basics, attribution/security, active context scoping, task temp cleanup, project link materialization, route binding, and Spring context smoke. Missing coverage is concentrated around effective workspace resolution, project output placement, workflow durable outputs, job workspace isolation, active/waiting temp retention, explicit-output-only policy, chat-file separation, and workflow async context propagation.

Implementation implication:

- Add characterization and regression tests before or with each behavior change.
- Validate Spring context startup after backend wiring phases.
- Keep validation gates phase-local and require commits only after validation passes.
- Run a final architecture/code review with extra attention to fragile refactor targets.

Concrete anchors:

- `WorkspaceLeaseServiceTest`
- `WorkspaceRepositoryAttributionTest`
- `WorkspaceRepositorySchemaMigrationTest`
- `WorkspacePathSegmentValidationTest`
- `PlanServiceTest`
- `WorkflowRunnerTest`
- `OrchestrationRuntimeTest`
- `PublicApiRouteBindingTest`
- `OperationalUiContractControllerTest`

## High-Risk Divergences

1. Effective workspace resolution is fragmented.
   Current code can route project-associated work through agent output paths or temp links. This risks incorrect artifact ownership, confusing file tool behavior, and future lease races.

2. `workspaceId` is overloaded.
   Existing API payloads and stream helpers use `workspaceId`, but the target architecture needs explicit `projectId`. Repurposing `workspaceId` would break callers and muddy compatibility.

3. Workflow state is incorrectly bridged to assignments.
   A workflow that waits for approval can be persisted as `WAITING` while its assignment is marked `FAILED`, making resume flows unreliable.

4. Async workflow task nodes lose orchestration context.
   Thread-local task context does not automatically cross async executor boundaries, so output attribution and alias resolution can silently fall back to incorrect defaults.

5. Workflow outputs are materialized in temp.
   The same directory currently acts as workflow execution temp and output storage. This conflicts with retention rules and explicit durable output tracking.

6. Job workspaces are definition-scoped.
   Multiple assignments or runs of one job definition can collide or share state unintentionally, and persistent job workspace behavior is not explicit.

7. Project owner-agent semantics conflict with target architecture.
   Projects should be shared workspace/visibility abstractions, but code and UI still make owner agent a required project property.

8. Loose artifact discovery conflicts with explicit-output-only architecture.
   Immediate removal is risky because existing task completion may rely on it. Keeping it indefinitely is also risky because incidental files become artifacts.

## Fragile Targets

- `WorkspaceDirectoryService`: central path construction and confinement. Any change must maintain data-root confinement and path segment validation.
- `OutputArtifactService`: artifact registration, discovery, reads/downloads, attribution, and security checks.
- `PlanService` and `TaskService`: run lifecycle, output directory selection, completion, temp cleanup, artifact publication, and compatibility with plan/task terminology.
- `WorkflowRunner`: node execution, waiting/resume behavior, child task execution, output materialization, and context propagation.
- `OrchestrationRunnerService`: assignment status transitions and job/workflow orchestration handoff.
- `ProjectService` and `ProjectRepository`: owner-agent migration, membership semantics, workspace release behavior, and schema compatibility.
- `JobService` and `JobRepository`: job definition compatibility, job run identity, assignment identity, workspace persistence policy, and project association.
- `OrchestrationController`: large UI surface with owner-agent labels, output path hints, and HTMX interactions that can regress silently.
- Public request records in API controllers and stream support classes: compatibility-sensitive because callers may already send `workspaceId`.

## Cross-Cutting Constraints

- Keep code-modifying phases serial.
- Each implementation phase must run as its own subagent subplan with narrow write ownership.
- Each implementation phase must run validation before commit.
- Each completed phase should end with a commit after validation passes.
- Preserve chat files as conversation-scoped and separate from project/agent output artifacts.
- Do not hard-remove loose artifact discovery in the first implementation wave.
- Introduce explicit `projectId`; do not repurpose `workspaceId`.
- Preserve API compatibility where existing payloads use `workspaceId`.
- Treat project owner-agent removal as a dedicated migration/API phase.
- Fix workflow `WAITING` assignment handling and workflow ThreadLocal context propagation as required defects.
- Project-attached writes must be compatible with future leasing and avoid output collisions by work unit and run identity.
- Final review must use xhigh reasoning and focus on fragile refactor targets, assumptions, gotchas, robustness, path confinement, leases/races, output correctness, and architecture alignment.

## Synthesis Recommendation

Implement the refactor in seven gated phases:

1. Baseline validation and characterization tests.
2. Effective workspace resolver, directory contract, and persisted run metadata.
3. Task/plan execution, aliases, explicit output publishing, and gated loose discovery.
4. Workflow waiting/resume defects, async context propagation, durable outputs, and retention.
5. Project API/schema migration to remove owner-agent requirements and add explicit project context.
6. Job workspace policy, project-scoped jobs, and legacy orchestration job reconciliation.
7. Integration docs, final validation, xhigh review, remediation, and closeout commits.

This order limits blast radius by first centralizing path decisions, then moving task/plan behavior, then workflow behavior, then project/job schema/API shifts. It intentionally delays hard behavior removals until compatibility paths and tests are in place.
