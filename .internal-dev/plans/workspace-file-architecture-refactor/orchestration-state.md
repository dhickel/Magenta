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

## Current Work

- Workspace/file architecture refactor.
- Current gate: Phase 04 commit.
- Phase 04 implementation and validation completed locally without a commit. Main orchestrator should commit, record the hash, then start Phase 05.

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

Commit Phase 04, record the hash, then launch Phase 05 project API and owner-agent migration subplan.

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
