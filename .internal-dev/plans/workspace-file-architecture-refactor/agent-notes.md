# Workspace File Architecture Refactor Agent Notes

This is the running cross-agent notes file for the workspace/file architecture refactor.

All review, planning, implementation, validation, remediation, documentation, and closeout agents must read this file before starting and append concise notes before finishing.

## Global Assumptions

- Source architecture note: `.internal-dev/notes/current-architecture-focus.md`.
- Work executes through agents, optionally with attached project context.
- Projects are durable shared workspace/visibility abstractions, not executable work units.
- Effective durable workspace is project workspace when project-scoped, otherwise agent workspace.
- Tasks/plans and workflows use per-run temp/execution space and do not get stable persistent per-work-unit workspaces.
- Jobs are work units and may also own persistent per-assignment/per-instance job workspaces when configured.
- Only explicit outputs should be tracked as output artifacts.
- Chat files remain a separate conversation-scoped system.
- Code-editing phases must be serial and validation-gated.
- Each completed phase should end with a commit after validation.

## Active Agents

- None currently.

## Completed Work

- Created initial architecture focus note.
- Created dedicated branch: `workspace-file-architecture-refactor`.
- Created this cross-agent notes file.
- Created setup commit: `9c05c24 docs: add workspace file architecture focus`.
- Review C completed read-only job/project/orchestration divergence review.
- Review E completed read-only testing/risk planning review.
- Review A completed read-only workspace/files divergence review.
- Review D completed read-only loose artifact discovery risk assessment.
- Review B completed read-only task/plan and workflow execution divergence review.
- Created planning synthesis commit: `0738b4a plan: synthesize workspace file refactor phases`.
- Created Phase 01 commit: `aee52fc test: characterize workspace file baseline`.
- Phase 02 implementation completed locally; awaiting main orchestrator validation/commit.

## Validation Results

- Phase 01 validation passed:
  - `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest` -> PASS, 60 tests.
  - `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 108 tests.
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> PASS under timeout rule; app started on port `42157`, then timeout stopped it with exit code `124`.
- Phase 02 validation passed:
  - `mvn test -Dtest=WorkspacePathSegmentValidationTest,PlanServiceTest,PlanRepositoryTest,WorkspaceRepositorySchemaMigrationTest` -> PASS, 60 tests.
  - `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 109 tests.
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> PASS under timeout rule; app started on port `42423`, then timeout stopped it with exit code `124`.
- Phase 02 validation passed:
  - `mvn test -Dtest=WorkspacePathSegmentValidationTest,PlanServiceTest,PlanRepositoryTest,WorkspaceRepositorySchemaMigrationTest` -> PASS, 60 tests.
  - `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 109 tests.
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started successfully on ephemeral port `41141`; command exited `124` because `timeout` stopped the running server after startup.

## Remediation Notes

- None yet.

## Blockers

- None currently.

## Closeout Work

- Required before final completion: docs updates, `.internal-dev` changelog, deeper technical changelog/review artifact, relevant package guide updates if responsibilities change, validation record, and commits.

## Final Validation Status

- Not started.

## Handoff Notes

- Initial work should be read-only review and planning. Do not implement until divergence, risk, and testing review passes are complete.
- Review C findings: projects still require owner agents across API/schema/service/UI; assignment/run APIs lack first-class `projectId`; project execution only works indirectly through job definitions; job workspaces are unconditional per job definition instead of opt-in per assignment/instance; project-scoped outputs are still written under agent output directories; job run identity is separate from assignment identity and resume can create inconsistent new job runs; legacy `OrchestrationJobService`/`orchestration_jobs` remains alongside current `JobService`/`job_definitions`. Recommended remediation should introduce `projectId` explicitly, preserve old payload compatibility, remove project owner-agent semantics through migration, resolve effective durable workspace centrally, and make persistent job workspaces opt-in and assignment-keyed.
- Review E findings: key validation risks are project owner-model drift, project-scoped outputs still landing under agent outputs, workflow outputs materializing into workflow temp, job persistent workspace isolation by job definition instead of assignment/instance, loose artifact discovery conflicting with explicit-output-only policy, active temp retention/data-loss risk, and lease/context races. Existing coverage is strongest for path confinement, lease basics, output artifact attribution/security, file/shell active-context scoping, task temp cleanup, project link materialization, route binding, and Spring context smoke. Missing coverage should be added before/with implementation for effective durable workspace resolution, project-scoped output placement, workflow durable outputs, job per-assignment persistent workspaces, active/waiting temp retention, explicit-output-only behavior, chat-file separation from artifacts, and workflow async context propagation.
- Review A findings: task tools currently map `workspace/` to run temp; project outputs still write under agent outputs; direct task/workflow submissions cannot attach project context except through job-derived project lookup; workflow artifacts are materialized into workflow temp; job workspaces are definition-scoped and always persistent; projects still require owner agents. Recommended first implementation step is an effective workspace resolver plus alias contract update, with API compatibility around existing `workspaceId` before changing storage paths.
- Review D findings: direct loose artifact discovery exists only in `OutputArtifactService.discoverLooseArtifacts(...)`, called by `PlanService.completeRun(...)`; `TaskService`/task tools/job plan items inherit it through task completion. Tests rely on loose file discovery in `OutputArtifactServiceAttributionTest` and `PlanServiceTest`. Current behavior is a shallow direct-file scan of run output dir, extension-inferred, artifact name `discovered_*`, with no explicit publication contract. Risk: hard removal can hide deliverables written to `outputs/`; keeping as-is violates explicit-output architecture. Recommended staged mitigation: gate current behavior, add realpath/data-root confinement, introduce explicit output publishing, update prompts/tools/docs, then default discovery off after compatibility coverage.
- Review B findings: assignment-backed workflows persist workflow `WAITING`, but `OrchestrationRunnerService.runWorkflow` marks any non-completed workflow assignment as `FAILED`, so approval workflows are not assignment-resumable. Workflow runs use `runtime/workflow-runs/<runId>` as both temp and output directory; final outputs/log artifacts are materialized into temp instead of effective durable workspace outputs. Task/plan runs allocate outputs under `agents/<agent>/workspace/outputs` or `agents/system`, even for project-scoped work; project context only creates a temp symlink. Workflow task nodes execute on async executor threads and lose `OrchestrationTaskContextHolder` context because it is a plain `ThreadLocal`. `PlanRun.workspaceId` is not populated on start; artifact attribution is reconstructed later from thread-local context or output-path fallback. Recommended constraints: introduce a shared effective-workspace/output resolver, persist run workspace/output metadata at run creation, separate workflow temp from durable outputs, propagate orchestration context explicitly through workflow execution, and preserve `PlanRun` compatibility while adding clearer task/workflow work-unit metadata.
- Planning Synthesis completed 2026-05-21: created `review-synthesis.md`, `implementation-plan.md`, `orchestration-suite.md`, and phase files `phase-01` through `phase-07`. The synthesized plan keeps implementation code edits serial and validation-gated, requires phase commits, preserves chat-file separation, stages loose artifact discovery behind confinement/gating plus explicit publishing, preserves `workspaceId` compatibility while adding `projectId`, treats project owner-agent removal as its own migration/API phase, and makes workflow `WAITING` assignment handling plus workflow context propagation concrete defect fixes.
- Phase 01 validation completed 2026-05-21: all requested validation commands passed. Phase 01 is ready for commit.
- Phase 01 committed as `aee52fc test: characterize workspace file baseline`.
- Phase 02 validation completed 2026-05-21: all requested validation commands passed. Phase 02 is ready for commit.

## Phase 01 Implementation Notes - 2026-05-21

Changed files:

- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`
- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`

Coverage added or strengthened:

- Strengthened project-scoped chat execution characterization: current project context still allocates task output under `agents/<agent>/workspace/outputs`, while project workspace access is a temp-run symlink.
- Added chat-file separation coverage: conversation files under `dataRoot/chats/<conversation>/files` are not discovered as task output artifacts when completing a task run.
- Added loose artifact discovery characterization: current discovery is shallow, indexes only direct output files, infers `.log` as `text`, and treats unknown extensions as `file_path`.
- Added workflow output characterization: workflow `outputDir` currently equals `workspacePath`, and final output artifacts materialize under workflow temp.
- Added workflow waiting temp retention assertion: a `WAITING` approval workflow keeps its workflow temp directory present.
- Added workflow async context characterization: task-node execution on the async executor currently does not inherit caller `OrchestrationTaskContextHolder` state.

Commands and results:

- Baseline before edits: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 104 tests.
- Changed-test pass: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest` -> PASS, 60 tests.
- Baseline after edits: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 108 tests.
- Spring context smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started successfully on ephemeral port `45333`; command exited `124` because `timeout` stopped the running server after startup.

Deferred planned coverage:

- Do not commit a failing workflow assignment test in Phase 01. Add/flip coverage in Phase 04 to assert `OrchestrationRunnerService.runWorkflow` maps workflow `WAITING` to assignment `WAITING` instead of `FAILED`.
- Add target-behavior tests in Phase 02/03 for project-scoped task outputs under the effective project durable workspace and for persisted effective workspace/run output metadata once the resolver exists.
- Add target-behavior tests in Phase 03 for a disabled/gated loose-discovery policy once the policy/config surface exists.
- Update the workflow async-context characterization in Phase 04 to require propagated context, and update workflow output assertions to require durable output placement separate from workflow temp.

Blockers:

- None for Phase 01. Known behavior gaps remain intentionally characterized or deferred to their implementation phases.

Recommended next validation:

- Main validation can rerun the Phase 01 baseline command plus the bounded Spring context smoke above.
- Phase 02 should start from the current characterization tests and add resolver-level path confinement tests before moving task/plan output placement.

## Phase 02 Implementation Notes - 2026-05-21

Changed files:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/EffectiveWorkspace.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/EffectiveWorkspaceResolver.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathSegmentValidationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/orchestration-state.md`

Implemented behavior:

- Added a central `EffectiveWorkspaceResolver` that selects project durable workspace when `projectId` is present, otherwise agent durable workspace.
- Resolver creates/loads the durable `Workspace` record and ensures shared layout directories exist under the selected root: `work/`, `outputs/`, `runs/`, and `scratch/`.
- Added shared layout helpers and work-unit output helpers to `WorkspaceDirectoryService`: task outputs under `outputs/tasks/<taskId>/<runId>/`, workflow outputs under `outputs/workflows/<workflowId>/<runId>/`, and job assignment outputs under `outputs/jobs/<jobAssignmentId>/<runId>/`.
- Kept path confinement strict by validating ids as plain path segments and checking workspace roots remain under the real configured `dataRoot`.
- Integrated resolver into `PlanService.startRun` so new runs persist the effective durable workspace record id in `PlanRun.workspaceId` when the resolver is available.
- Preserved existing `workspaceId` compatibility by not interpreting context/request `workspaceId` as `projectId`.
- Preserved Phase 01 output behavior: task/plan run output directories still use the current agent/system output layout until Phase 03 moves task/plan path placement.
- No schema change was needed because `plan_runs.workspace_id`, `output_directory`, and `temp_workspace_path` already exist.

Coverage added:

- Resolver selection prefers project workspace over agent workspace when `projectId` is present.
- Resolver falls back to agent workspace when `projectId` is absent.
- Shared durable layout helpers create confined `work`, `outputs`, `runs`, and `scratch` directories.
- Work-unit output helpers create traceable task/workflow/job paths and reject invalid segments.
- `PlanService.startRun` persists the effective workspace id while keeping current output placement under `agents/<agent>/workspace/outputs/`.

Commands and results:

- `mvn test -Dtest=WorkspacePathSegmentValidationTest,PlanServiceTest,PlanRepositoryTest,WorkspaceRepositorySchemaMigrationTest` -> PASS, 60 tests.
- `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 109 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started successfully on ephemeral port `41141`; command exited `124` because `timeout` stopped the running server after startup.

Deferred planned work:

- Phase 03 should move task/plan output placement onto the resolver-selected durable output helper paths and update runtime aliases.
- Phase 03 should decide how explicit output publishing and gated loose discovery use the persisted effective workspace metadata.
- Phase 04 should apply the resolver to workflow durable output placement and fix workflow waiting/context propagation defects.
- Later project/API phases should add explicit public `projectId` submission paths while preserving existing `workspaceId` compatibility.

Blockers:

- None for Phase 02.

Ready state:

- Phase 02 is ready for main orchestrator validation. No commit was created by this implementation agent.
