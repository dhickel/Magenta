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

- None.

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
- Created Phase 02 commit: `961a6c8 feat: add effective workspace resolver`.
- Created Phase 03 commit: `4f59cb5 feat: route task outputs through effective workspace`.
- Created Phase 04 commit: `d1cc9f3 feat: route workflow outputs through effective workspace`.

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
- Phase 03 validation passed:
  - `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest` -> PASS, 115 tests.
  - `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 176 tests.
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started successfully on ephemeral port `37317`; command exited `124` because `timeout` stopped the running server after startup.
- Phase 04 validation passed:
  - `git diff --check` for Phase 04 files -> PASS.
  - `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest,OutputArtifactServiceAttributionTest` -> PASS, 63 tests.
  - `mvn test -Dtest=PlanServiceTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 99 tests.
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started successfully on ephemeral port `33571`; command exited `124` because `timeout` stopped the running server after startup.

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
- Phase 02 committed as `961a6c8 feat: add effective workspace resolver`.
- Phase 03 validation agent completed 2026-05-21: all requested validation commands passed. Phase 03 is ready for commit.
- Phase 03 committed as `4f59cb5 feat: route task outputs through effective workspace`.
- Phase 04 validation agent completed 2026-05-21: all requested validation commands passed. Phase 04 is ready for commit.
- Phase 04 committed as `d1cc9f3 feat: route workflow outputs through effective workspace`.

## Phase 03 Implementation Notes - 2026-05-21

Changed files:

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`
- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/orchestration-state.md`

Implemented behavior:

- Moved task/plan output selection onto `EffectiveWorkspaceResolver` plus `WorkspaceDirectoryService.taskOutput(...)` when the resolver is available.
- Project-scoped task outputs now land under `projects/<projectId>/workspace/outputs/tasks/<taskId>/<runId>/`.
- Agent-scoped task outputs now land under `agents/<agentId>/workspace/outputs/tasks/<taskId>/<runId>/`.
- Preserved `PlanRun.tempWorkspacePath` under `runtime/task-runs/<runId>` and left existing terminal temp cleanup behavior intact.
- Extended `OrchestrationTaskContext` with explicit `hostDurableWorkspacePath` and `hostRunPath` while retaining the legacy `hostWorkspacePath` as the run/assignment temp path for cleanup/link compatibility.
- Alias contract chosen for active task contexts:
  - `workspace/` -> effective durable workspace root.
  - `work/` -> effective durable workspace `work/`.
  - `outputs/` -> current run output directory.
  - `run/` -> current run temp/execution directory.
  - `scratch/` -> effective durable workspace `scratch/`.
  - Existing `projects/<projectId>/...` compatibility remains through the materialized run-temp project link.
- Added `OutputArtifactService.publishExistingFile(...)` as an explicit method-level output publishing path for existing files.
- Gated loose artifact discovery behind a service-level compatibility policy, defaulting on for compatibility.
- Added realpath confinement for loose discovery so the output directory must resolve under `dataRoot`, and discovered files must resolve under both `dataRoot` and the run output directory. Symlink escapes are skipped.
- Preserved ordinary chat file behavior and data-root fallback behavior when no orchestration context is active.

Coverage added or updated:

- Updated Phase 01/02 task output placement assertions to require effective task output paths.
- Added file and shell tool alias tests documenting `workspace/`, `work/`, `outputs/`, `run/`, and `scratch/`.
- Added explicit output publishing coverage.
- Added disabled loose discovery coverage.
- Added loose discovery symlink escape coverage.
- Kept chat-file exclusion coverage passing.

Commands and results:

- First focused pass: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest` -> FAIL, 115 tests run, 2 old-assumption failures. Fixed missing resolver wiring in a project-scoped task test and updated old alias wording assertions.
- Second focused pass: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest` -> FAIL, 115 tests run, 1 remaining old alias wording assertion. Fixed assertion.
- Final focused pass: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest` -> PASS, 115 tests.
- First broader regression pass: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> FAIL, `PublicApiRouteBindingTest` Spring context failed because `OutputArtifactService` had multiple constructors and no selected autowire constructor. Added `@Autowired` to the compatibility-on constructor.
- Final broader regression pass: `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 176 tests.
- Spring context smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started successfully on ephemeral port `34255`; command exited `124` because `timeout` stopped the running server after startup.

Deferred planned work:

- Phase 04 should move workflow output placement onto effective durable workflow output paths and handle workflow waiting/context propagation fixes.
- Later phases should introduce a user-facing/tool-level explicit publish surface if needed; Phase 03 only added the method-level service API.
- Later phases can move loose discovery from default compatibility-on to default-off after migration/configuration coverage is in place.
- Package guide/docs/changelog updates and commit are left for the main orchestrator because this implementation agent was explicitly told not to edit docs outside the plan dir and not to commit.

Blockers:

- None.

Ready state:

- Phase 03 is ready for main validation. No commit was created by this implementation agent.

## Phase 04 Implementation Notes - 2026-05-21

Changed files:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/orchestration-state.md`

Implemented behavior:

- Workflow runs now keep temp execution state under `runtime/workflow-runs/<runId>` while durable workflow artifacts materialize under `outputs/workflows/<workflowId>/<runId>` in the resolver-selected effective workspace.
- Direct workflow runs without an active orchestration context use the `system` agent durable workspace; assignment-backed runs use the current assignment agent/project context.
- Workflow task-node futures explicitly receive the active `OrchestrationTaskContextHolder` context and restore/clear the worker thread context in `finally`.
- Workflow execution wraps the run with path-aware context so resume and task nodes use the original workflow run temp path and output path.
- Assignment-backed workflows map workflow `WAITING` to assignment `WAITING`, persist the workflow run id in checkpoint/output, and resume the original waiting workflow run instead of starting a second run.
- Workflow synchronous execution no longer also submits the same run to the async executor, removing a duplicate-run persistence race found during validation.
- Workflow artifact attribution now backfills artifacts materialized directly by the workflow run as well as child task-run artifacts.
- Waiting workflow temp directories remain present, and focused coverage confirms deleting workflow temp does not remove durable output artifacts.

Coverage added or updated:

- Updated workflow output placement assertions from temp-only to durable output paths.
- Updated async task-node context characterization to require propagated context and workflow run/output paths.
- Added assignment-backed workflow waiting/resume regression coverage that verifies the assignment remains `WAITING`, resumes to `COMPLETED`, and reuses the same workflow run id.
- Preserved waiting temp retention coverage and added durable-output survival coverage after temp cleanup.

Commands and results:

- `mvn test -Dtest=WorkflowRunnerTest` -> PASS, 13 tests.
- `mvn test -Dtest=OrchestrationRuntimeTest` -> PASS, 36 tests.
- First requested focused pass: `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest,OutputArtifactServiceAttributionTest` -> FAIL, 63 tests run, 1 failure in the new assignment waiting/resume regression. Root cause was existing `WorkflowRunner.runSynchronously` duplicate execution: `startRun` submitted async execution while synchronous execution also ran the same workflow, causing concurrent workflow node-run persistence and a failed assignment in combined test ordering.
- Reproducer after fix: `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest#workflowAssignmentWaitingStatusRemainsResumableAndReusesOriginalRun` -> PASS, 14 tests.
- Final requested focused pass: `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest,OutputArtifactServiceAttributionTest` -> PASS, 63 tests.
- Final broader regression pass: `mvn test -Dtest=PlanServiceTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 99 tests.
- `git diff --check` -> PASS.
- Spring context smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started successfully on ephemeral port `34077`; command exited `124` because `timeout` stopped the running server after startup.

Deferred planned work:

- No Phase 04 blockers remain.
- Later phases still own project owner-agent migration, job persistent workspace policy, public project/job UX, broader workflow UX/API cleanup, and docs/changelog outside this plan directory.

Blockers:

- None.

Ready state:

- Phase 04 is ready for main validation and commit. No commit was created by this implementation agent.

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
