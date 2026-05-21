# Services And UX Architecture Refactor Orchestration State

Date: 2026-05-21

## Current Status

- Workspace/file architecture refactor complete:
  - `3f447ae chore: close workspace file architecture refactor`
  - `e6dfe87 chore: record workspace file closeout commit`
- Second orchestration branch created: `services-ux-architecture-refactor`.
- Setup commit: `7e63626 plan: start services ux architecture refactor`.
- Initial read-only review wave completed. Review artifacts:
  - `.internal-dev/plans/services-ux-architecture-refactor/review-backend-services.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/review-frontend-ux.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/review-integration-api.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/review-risk-testing.md`
- Advanced plan synthesis completed. Generated artifacts:
  - `.internal-dev/plans/services-ux-architecture-refactor/review-synthesis.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/implementation-plan.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-01-service-contracts.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-02-job-execution-read-model.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-03-output-provenance.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-04-project-job-ux.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-05-validation-closeout.md`
- Phase 01 implementation completed locally:
  - first-class assignment project/effective workspace columns, mapping, backfill, creation persistence, and execution-start repair.
  - assignment summary/read contract, active assignment queries, workspace-blocked requeue operations, and active project/job mutation guards.
  - minimal API conflict mapping for active mutation policy failures.
- Phase 02 implementation completed locally:
  - stable `JobExecutionSummary` read model links job definition, assignment, run, agent/project ids and names where available, compatibility workspace id, effective workspace context, persistent job workspace state, output directory/count/latest timestamp, child runs, and lifecycle timestamps.
  - public/API and scheduler-style job execution paths now create assignments; direct job run allocation is guarded behind assignment context.
  - legacy job recurrence firing now creates `JOB_RUN` assignments and advances next fire timestamps rather than creating job runs directly.
  - assignment-owned cancellation now keeps assignment and job run status synchronized.
  - job run HTMX fragment consumes summaries so assignment-pending/run-pending states can be displayed.

## Objective

Review and align Magenta's backend services, frontend surfaces, and integration flows with the documented architecture for agents, projects, jobs, tasks/plans, workflows, workspaces, and outputs.

## Architecture Baseline

- Orchestrator abstractions:
  - Agents own primary workspaces and execute work.
  - Projects provide a shared, durable, project-scoped workspace and visibility surface under agents.
- Work-unit abstractions:
  - Tasks/plans and workflows are bounded work units submitted to an agent or agent-plus-project context.
  - Jobs are work-unit/orchestrator hybrids that can be assigned, repeat, optionally keep a persistent per-assignment workspace, and launch tasks/workflows toward a goal.
- UX expectations:
  - Users can see how projects attach to agents and how submitted work lands in project or agent workspace context.
  - Users can see and configure job assignment/routing behavior, including persistent workspace status where supported.
  - Output views should make project, agent, job, task/workflow, run, and workspace context discoverable without conflating chat files with output artifacts.

## Initial Review Questions

- Which services still encode project ownership, workspace selection, or job routing in ways that diverge from the architecture?
- Which public API routes or request records are missing project/job assignment fields needed by the UI?
- Which UI surfaces are misleading, missing, or inconsistent for project assignment, job assignment, workspace selection, output visibility, and run status?
- Are there race/concurrency risks around multi-agent project use, job assignment workspaces, or UI actions that should be left open for leasing/locking?
- Which tests and Playwright checks are required before implementation can be considered validated?

## Planned Gated Flow

1. Read-only backend services review.
2. Read-only frontend/UX review.
3. Read-only integration/API review.
4. Risk assessment and testing plan review.
5. Advanced implementation plan synthesis.
6. Serial implementation phases with validation after each phase.
7. xhigh final architecture/code/UX review.
8. Remediation loops if the review or validation fails.
9. Documentation, `.internal-dev`, changelog, and final commit closeout.

## Synthesized Plan Summary

Implementation order is service/API first, UI last:

1. Phase 01: service contracts and assignment context.
2. Phase 02: job execution read model and assignment-routed execution.
3. Phase 03: output provenance.
4. Phase 04: project, job, and operator UX.
5. Phase 05: validation and closeout.

Key decisions:

- `projectId` is first-class assignment state and selects the effective durable workspace.
- `workspaceId` is retained only as compatibility metadata and is not interchangeable with `projectId`.
- user-facing task, workflow, and job execution must route through `WorkAssignment` and runner lease semantics.
- jobs remain hybrid abstractions: definition plus assignment plus run plus optional per-assignment persistent workspace.
- active project/job destructive or execution-affecting mutations are blocked while non-terminal work references that state.
- direct output artifact attribution is the primary contract; route-level fallback remains compatibility behavior only.
- Playwright validation is mandatory for changed UI surfaces and must run in a subagent.

Next recommended phase: start Phase 01 only. Do not start UI work until Phase 01 contracts, validation, mutation policy, and workspace-blocked requeue behavior are implemented.

## Commit Policy

- Commit setup/planning artifacts before implementation.
- Commit after each validated implementation phase.
- Keep unrelated dirty files out of all commits.

## Unrelated Dirty Files To Avoid

- `AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `.internal-dev/notes/idea_drop.md`
- `.internal-dev/notes/scratch.md`
- `.codex-orchestration/*`
- screenshots and `test-results/`

## Agent Roster

- Backend services review: `019e497a-ef84-7900-92fa-0c6c0858a5db` (`Hubble`).
- Frontend/UX review: `019e497b-1e66-7e90-95d3-709e47c457f5` (`Lagrange`).
- Integration/API review: `019e497b-456f-7541-9ef8-1c5195cfbc61` (`Feynman`).
- Risk/testing review: `019e497b-771e-7442-9598-61d64c65eef2` (`Kierkegaard`).

## Validation Log

- Review wave was read-oriented; no source validation was expected. Review artifacts and notes passed `git diff --check`.
- Phase 01 implementation and remediation validation passed:
  - `mvn test -Dtest=AssignmentContextServiceTest,ProjectServiceTest,JobServiceTest`
  - `mvn test -Dtest=PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,ProjectServiceTest,OrchestrationRuntimeTest`
  - `mvn test -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest`
  - `mvn test -Dtest=WorkspaceRepositorySchemaMigrationTest,AssignmentContextServiceTest`
  - `git diff --check`
  - Spring startup smoke on ephemeral port.
- Phase 01 commit: `0a92caa feat: add first-class assignment workspace context`.
- Phase 02 implementation and validation passed:
  - `mvn test -Dtest=JobServiceTest,JobRepositoryTest,OrchestrationRuntimeTest,AssignmentContextServiceTest`
  - `mvn test -Dtest=PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,OrchestrationControllerTest`
  - `mvn test -Dtest=WorkspaceRepositorySchemaMigrationTest,OutputArtifactServiceAttributionTest`
  - `git diff --check`
  - Spring startup smoke on ephemeral port.
  - Focused browser validation of `/jobs` and the job editor runs panel, with screenshots captured under `test-results/` and intentionally not committed.
- Phase 02 commit: `ca6c0c5 feat: add job execution summaries`.
- Phase 01 local validation passed:
  - `mvn test -Dtest=AssignmentContextServiceTest,ProjectServiceTest,JobServiceTest`
  - `mvn test -Dtest=PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,ProjectServiceTest,OrchestrationRuntimeTest`
  - `mvn test -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest`
  - `git diff --check`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached successful application startup before timeout shutdown.
- Phase 01 remediation aligned `schema.sql` work assignment bootstrap with runtime assignment context columns: nullable `project_id`, `effective_workspace_id`, and `effective_workspace_kind`.
- Phase 01 remediation validation passed: `mvn test -Dtest=WorkspaceRepositorySchemaMigrationTest,AssignmentContextServiceTest` and scoped `git diff --check`.
- Phase 02 local validation passed:
  - `mvn test -Dtest=JobServiceTest,JobRepositoryTest,OrchestrationRuntimeTest,AssignmentContextServiceTest`
  - `mvn test -Dtest=JobControllerTest,PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest` (`JobControllerTest` is not present; Maven ran the existing listed controller tests.)
  - `mvn test -Dtest=WorkspaceRepositorySchemaMigrationTest,OutputArtifactServiceAttributionTest`
  - `mvn test -Dtest=OrchestrationControllerTest`
  - `git diff --check`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached successful application startup on port `43617`; command exited `124` because timeout stopped the running app.
