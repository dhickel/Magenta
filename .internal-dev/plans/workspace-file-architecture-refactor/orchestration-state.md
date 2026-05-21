# Orchestration State

Date: 2026-05-21

## Purpose

Persistent state for this long-running orchestration effort. Use this file to rehydrate context after compaction, interruption, or agent handoff.

## Branch

- Current branch: `workspace-file-architecture-refactor`

## Past Work

- Created `.internal-dev/notes/current-architecture-focus.md` to capture the intended workspace/file/orchestration architecture.
- Created `.internal-dev/plans/workspace-file-architecture-refactor/` as the durable plan directory.
- Created `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md` as the shared subagent notes file.
- Created setup commit: `9c05c24 docs: add workspace file architecture focus`.
- Ran five read-only review agents:
  - Review A: workspace/files/current-state divergence.
  - Review B: task-plan/workflow execution and output behavior.
  - Review C: job/project/orchestration assignment behavior.
  - Review D: loose artifact discovery risk assessment.
  - Review E: testing strategy and risk mitigation.
- Ran Planning Synthesis agent, which created the implementation plan, orchestration suite, review synthesis, and phase files.
- Created planning synthesis commit: `0738b4a plan: synthesize workspace file refactor phases`.
- Completed Phase 01 baseline characterization and committed it as `aee52fc test: characterize workspace file baseline`.
- Completed Phase 02 effective workspace resolver and run metadata implementation locally; no commit created by the phase implementation agent.
- Committed Phase 02 as `961a6c8 feat: add effective workspace resolver`.
- Completed Phase 03 task output routing, runtime alias, explicit publish, and loose-discovery confinement implementation locally; no commit created by the phase implementation agent.
- Completed Phase 03 validation through a dedicated validation agent.
- Committed Phase 03 as `4f59cb5 feat: route task outputs through effective workspace`.
- Completed Phase 04 workflow waiting/resume, async context propagation, and durable workflow output implementation locally; no commit created by the phase implementation agent.
- Completed Phase 04 validation through a dedicated validation agent.
- Committed Phase 04 as `d1cc9f3 feat: route workflow outputs through effective workspace`.
- Completed Phase 05 project owner-agent compatibility, explicit project submission context, and project UI remediation locally; no commit created by phase implementation/remediation agents.
- Completed Phase 05 backend/API validation and Playwright UI validation through dedicated validation agents.
- Committed Phase 05 as `f2aaa1d feat: make project context explicit for submissions`.

## Current Work

- Workspace/file architecture refactor.
- Current gate: Phase 06 commit.
- Phase 06 implementation and validation completed locally without a commit. Main orchestrator should commit, record the hash, then start Phase 07.

## Current Plan Artifacts

- Architecture note: `.internal-dev/notes/current-architecture-focus.md`
- Shared agent notes: `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`
- Review synthesis: `.internal-dev/plans/workspace-file-architecture-refactor/review-synthesis.md`
- Implementation plan: `.internal-dev/plans/workspace-file-architecture-refactor/implementation-plan.md`
- Orchestration suite: `.internal-dev/plans/workspace-file-architecture-refactor/orchestration-suite.md`
- Phase files: `.internal-dev/plans/workspace-file-architecture-refactor/phase-01-baseline-characterization.md` through `phase-07-integration-closeout.md`

## Required Gating Rules

- Every phase must run through a subagent subplan with its own context.
- Code-modifying phases are serial.
- Validation runs after every implementation phase.
- Remediation runs before proceeding when validation fails.
- Each validated phase ends with a commit.
- Final architecture/code review uses xhigh reasoning and must focus on fragile/complex refactor targets, assumptions, gotchas, robustness, path confinement, leases/races, output correctness, and architectural alignment.
- Testing/validation agents use `gpt-5.3-codex` with medium reasoning per repo guidance.

## Future Queued Work

After the workspace/file refactor is fully completed, run a second orchestration suite for services and UX alignment.

Queued review/refactor focus:

- Review services, frontend, and integration against the architecture after the file/workspace refactor lands.
- Verify how projects are displayed, assigned, and submitted through agents.
- Verify how jobs are displayed, assigned, configured, and routed.
- Verify project, job, task, and workflow UX exposes the intended architecture clearly.
- Verify services and UI support project-attached agent execution, job persistent workspace configuration, output visibility, assignment flows, and workspace/status panels.
- Run the same pattern: read-only review agents, risk/testing synthesis, advanced implementation plan, serial implementation phases, validation gates, xhigh final review, remediation, docs, changelog, and commits.

## Blockers

- None currently.

## Next Action

Run main Phase 06 validation review and commit if accepted.

## Phase 03 Local Validation

- `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest` -> PASS, 115 tests after two remediation passes for old assertions.
- `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 176 tests after adding an explicit Spring autowire constructor selection for `OutputArtifactService`.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started on port `34255`; exited `124` because `timeout` stopped the server.

## Phase 03 Validation Agent

- `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest` -> PASS, 115 tests.
- `mvn test -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,AgentFileToolServiceTest,AgentShellToolServiceTest,WorkspacePathSegmentValidationTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 176 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started on port `37317`; exited `124` because `timeout` stopped the server.
- Sanity check confirmed runtime aliases, loose-discovery gating/confinement, and `publishExistingFile(...)` explicit publishing are present.

## Phase 04 Local Validation

- `mvn test -Dtest=WorkflowRunnerTest` -> PASS, 13 tests.
- `mvn test -Dtest=OrchestrationRuntimeTest` -> PASS, 36 tests.
- First requested focused pass: `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest,OutputArtifactServiceAttributionTest` -> FAIL, 63 tests run, 1 failure in the new assignment waiting/resume regression. Root cause was duplicate async plus synchronous workflow execution in `WorkflowRunner.runSynchronously`; fixed by creating synchronous runs without auto-submitting them to the workflow executor.
- Reproducer after fix: `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest#workflowAssignmentWaitingStatusRemainsResumableAndReusesOriginalRun` -> PASS, 14 tests.
- Final requested focused pass: `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest,OutputArtifactServiceAttributionTest` -> PASS, 63 tests.
- Final broader regression pass: `mvn test -Dtest=PlanServiceTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 99 tests.
- `git diff --check` -> PASS.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started on port `34077`; exited `124` because `timeout` stopped the server.

## Phase 04 Ready State

- Implementation is ready for main validation and commit.
- No blockers.

## Phase 04 Validation Agent

- `git diff --check` for Phase 04 files -> PASS.
- `mvn test -Dtest=WorkflowRunnerTest,OrchestrationRuntimeTest,OutputArtifactServiceAttributionTest` -> PASS, 63 tests.
- `mvn test -Dtest=PlanServiceTest,WorkflowRunnerTest,OrchestrationRuntimeTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest` -> PASS, 99 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started on port `33571`; exited `124` because `timeout` stopped the server.
- Sanity check confirmed waiting assignment mapping, same-run workflow resume, async context propagation/restoration, durable workflow output placement, and terminal cleanup safety.

## Phase 05 UI Remediation

- Updated project UI semantics in `OrchestrationController` to describe projects as shared workspaces with membership/context rather than owner-agent wrappers.
- Kept project form submission backward-compatible by preserving `ownerAgentId` while relabeling it as `Initial Agent`.
- Successful project create/update responses now include editor feedback and an HTMX OOB `#project-list` refresh so ownerless creation is visible in the sidebar.
- Added focused `OrchestrationControllerTest` coverage for new copy, label compatibility, and ownerless create list refresh response.
- Validation passed:
  - `mvn test -Dtest=OrchestrationControllerTest,OperationalUiContractControllerTest` -> PASS, 97 tests.
  - `mvn test -Dtest=PublicApiRouteBindingTest,OrchestrationControllerTest,OperationalUiContractControllerTest,PublicRunSubmissionControllerTest` -> PASS, 114 tests.
- No blockers. Ready for dedicated Phase 05 Playwright re-validation.

## Phase 05 Validation Agent

- `git diff --check` for Phase 05 files -> PASS.
- `mvn test -Dtest=ProjectServiceTest,ProjectRepositoryTest,WorkspaceRepositorySchemaMigrationTest` -> PASS, 23 tests.
- `mvn test -Dtest=PublicApiRouteBindingTest,OrchestrationControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,PublicRunSubmissionControllerTest,TaskStreamSupportTest` -> PASS, 147 tests.
- `mvn test -Dtest=ProjectServiceTest,ProjectRepositoryTest,WorkspaceRepositorySchemaMigrationTest,PublicApiRouteBindingTest,OrchestrationControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,PublicRunSubmissionControllerTest,TaskStreamSupportTest,PlanServiceTest,WorkflowRunnerTest,OrchestrationRuntimeTest` -> PASS, 256 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started on port `34423`; exited `124` because `timeout` stopped the server.

## Phase 05 Playwright Validation

- Initial Playwright validation against `http://localhost:18080` found blockers: project UI still used owner-agent copy and ownerless creation did not visibly refresh the project list.
- Remediation updated project copy, relabeled the compatibility field as `Initial Agent`, and added HTMX OOB project-list refresh on create/update.
- Playwright re-validation against `http://localhost:18080` -> PASS:
  - Ownerless project creation succeeded with no initial agent selected.
  - New project appeared in the sidebar/list after submit.
  - Project details showed `Initial agent: -` and `Members: 0`.
  - `/plans` submit panel still showed Project and Workspace fields.
  - Screenshot captured as `phase05-projects-revalidation.png` for internal review; screenshots are not part of the phase commit.

## Phase 06 Local Implementation

- Added explicit additive `JobDefinition.persistentWorkspaceEnabled`, persisted as `job_definitions.persistent_workspace_enabled` with default false.
- Added `JobRun.jobAssignmentId` and `JobRun.workspaceId`, persisted in `job_runs`.
- Job persistent workspace allocation is now opt-in and assignment/run keyed under the effective durable workspace `jobs/<assignmentId-or-runId>/`.
- Job output directories now use effective durable workspace `outputs/jobs/<assignmentId-or-runId>/<jobRunId>/`.
- Assignment-backed job runs pass the work assignment id into `JobService.startRun(...)`, so repeated assignments of the same job definition do not share persistent job workspaces.
- Project-scoped jobs resolve to project durable workspace; agent-scoped jobs resolve to agent durable workspace.
- Resumed job assignments reuse checkpointed `jobRunId` instead of starting a second job run.
- Output artifact rows/context gained additive `job_assignment_id` and `job_run_id` attribution fields.
- Legacy `OrchestrationJobService` / `orchestration_jobs` was preserved as compatibility surface; no deletion or migration was attempted in Phase 06.

## Phase 06 Local Validation

- `mvn test -Dtest=JobServiceTest,JobRepositoryTest,WorkspacePathSegmentValidationTest,OutputArtifactServiceAttributionTest,WorkspaceRepositoryAttributionTest -DskipTests=false` -> PASS, 48 tests after constructor compatibility remediation.
- `mvn test -Dtest=JobServiceTest,JobRepositoryTest,OrchestrationRuntimeTest,WorkspacePathSegmentValidationTest,WorkspaceRepositorySchemaMigrationTest,OutputArtifactServiceAttributionTest` -> PASS, 87 tests.
- `mvn test -Dtest=PublicApiRouteBindingTest,OrchestrationControllerTest,OperationalUiContractControllerTest,PublicRunSubmissionControllerTest` -> PASS, 114 tests.
- `mvn test -Dtest=JobServiceTest,JobRepositoryTest,OrchestrationRuntimeTest,WorkspacePathSegmentValidationTest,WorkspaceRepositorySchemaMigrationTest,OutputArtifactServiceAttributionTest,PlanServiceTest,WorkflowRunnerTest,PublicApiRouteBindingTest` -> PASS, 144 tests.
- `git diff --check` -> PASS.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started on port `35907`; exited `124` because `timeout` stopped the server.

## Phase 06 Ready State

- Implementation is ready for main validation and commit.
- No blockers.

## Phase 06 Validation Agent

- `git diff --check` for Phase 06 files -> PASS.
- `mvn test -Dtest=JobServiceTest,JobRepositoryTest,OrchestrationRuntimeTest,WorkspacePathSegmentValidationTest,WorkspaceRepositorySchemaMigrationTest,OutputArtifactServiceAttributionTest` -> PASS, 87 tests.
- `mvn test -Dtest=PublicApiRouteBindingTest,OrchestrationControllerTest,OperationalUiContractControllerTest,PublicRunSubmissionControllerTest` -> PASS, 114 tests.
- `mvn test -Dtest=JobServiceTest,JobRepositoryTest,OrchestrationRuntimeTest,WorkspacePathSegmentValidationTest,WorkspaceRepositorySchemaMigrationTest,OutputArtifactServiceAttributionTest,PlanServiceTest,WorkflowRunnerTest,PublicApiRouteBindingTest` -> PASS, 144 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` -> application started on port `39931`; exited `124` because `timeout` stopped the server.
- Sanity check confirmed opt-in/default-false persistent job workspaces, assignment-isolated job workspace paths, effective project/agent job outputs, additive job assignment/run output attribution, and legacy `OrchestrationJobService` preservation.
