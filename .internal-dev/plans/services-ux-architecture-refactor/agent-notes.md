# Services And UX Architecture Refactor Agent Notes

## Global Assumptions

- This suite starts after the workspace/file architecture refactor completed on `workspace-file-architecture-refactor` through `e6dfe87`.
- Target branch: `services-ux-architecture-refactor`.
- Projects are durable shared workspace/visibility abstractions, not ordinary work units.
- Agents remain the execution entry point. Work can be submitted through an agent alone or through an agent with an attached project.
- Jobs are orchestration/work-unit hybrids: repeatable, optionally persistent, assignable, and capable of launching their own task/workflow work.
- Tasks/plans and workflows are work units that should surface project/agent workspace selection and output visibility accurately.
- Chat files remain separate from output artifacts.

## Active Agents

- Advanced plan synthesis pending.

## Completed Work

- Created second-suite branch and initial orchestration notes.
- Setup commit: `7e63626 plan: start services ux architecture refactor`.
- Launched initial high-reasoning read-oriented review group.
- Backend services review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-backend-services.md`.
- Frontend/UX review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-frontend-ux.md`.
- Integration/API review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-integration-api.md`.
- Risk/testing review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-risk-testing.md`.
- Phase 01 committed: `0a92caa feat: add first-class assignment workspace context`.
- Phase 02 committed: `ca6c0c5 feat: add job execution summaries`.
- Phase 03 committed: `47877a9 feat: expose output provenance filters`.
- Advanced plan synthesis completed:
  - `.internal-dev/plans/services-ux-architecture-refactor/review-synthesis.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/implementation-plan.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-01-service-contracts.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-02-job-execution-read-model.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-03-output-provenance.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-04-project-job-ux.md`
  - `.internal-dev/plans/services-ux-architecture-refactor/phase-05-validation-closeout.md`

## Validation Results

- Phase 01 local validation:
  - `mvn test -Dtest=AssignmentContextServiceTest,ProjectServiceTest,JobServiceTest` passed.
  - `mvn test -Dtest=PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,ProjectServiceTest,OrchestrationRuntimeTest` passed.
  - `mvn test -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest` passed.
  - `git diff --check` passed.
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` started the Spring Boot context successfully on port `39211`; command exited `124` because the timeout stopped the running app after successful startup.

## Remediation Notes

- Planning synthesis chose conservative service-first sequencing:
  1. assignment/service contracts and mutation policy.
  2. job assignment/run bridge and assignment-routed job execution.
  3. output provenance and query/display contracts.
  4. project/job/operator UI.
  5. validation, docs, `.internal-dev`, and final review closeout.
- Key policy decisions captured in the plan:
  - `projectId` selects the effective durable workspace; `workspaceId` remains compatibility metadata.
  - all user-facing execution must enter through assignments and runner leases.
  - active project/job destructive or execution-affecting mutations are blocked while non-terminal work references that state.
  - output direct attribution is the primary contract; fallback remains compatibility only.

## Blockers

- None currently known.

## Closeout Work

- Required at end: docs updates, `.internal-dev` changelog, technical changelog if implementation changes are substantive, knowledge notes for reusable architecture rules, final validation, xhigh review, and scoped commits.

## Final Validation Status

- Pending.

## Handoff Notes

- The first wave should be read-only and should identify divergence between current backend services, frontend/UX, and the project/job/workspace architecture.
- Agents must append concise findings here before finishing.
- Next recommended implementation phase is Phase 01: service contracts and assignment context. Do not begin UI changes until Phase 01 service/API context and mutation policy are implemented and validated.
- Phase 01 implementation completed service-side assignment context:
  - `WorkAssignment` and `work_assignments` now carry first-class `projectId`, `effectiveWorkspaceId`, and `effectiveWorkspaceKind`, with legacy `input_json.projectId` backfill.
  - Assignment creation persists first-class project/effective workspace context, keeps `projectId` in input JSON, and does not acquire workspace write leases.
  - Runner execution repairs missing effective workspace context at lease start and reads first-class `projectId` before legacy input JSON.
  - Assignment summaries, active assignment queries, workspace-blocked requeue helpers, and project/job active mutation guards are available for later API/UI phases.
  - API controllers now map active mutation policy failures to 409 responses; no broader UI surface work was implemented.
- Phase 01 remediation updated `schema.sql` work assignment bootstrap to include nullable `project_id`, `effective_workspace_id`, and `effective_workspace_kind` columns in the same position/type shape as `OrchestrationRuntimeRepository.ensureSchema()`.
- Phase 01 remediation validation passed: `mvn test -Dtest=WorkspaceRepositorySchemaMigrationTest,AssignmentContextServiceTest` and scoped `git diff --check`.
- Phase 02 implementation completed job execution read-model and assignment-routed execution work:
  - Added `JobExecutionSummary` and job service/API summary reads by job, latest job execution, and assignment id.
  - Job run allocation now requires assignment context; public job submit/start paths remain assignment-submission paths.
  - Legacy job recurrence firing now enqueues `JOB_RUN` assignments and advances recurrence timestamps instead of allocating runs directly.
  - Assignment-owned run cancellation now also routes through the owning assignment lifecycle and keeps job run status synchronized.
  - Job run fragments now render assignment-aware summaries so pending assignments can appear before a run exists.
  - Focused tests cover direct-run guard behavior, pending/run summary bridge state, recurrence assignment routing, per-assignment persistent job workspaces, and assignment-owned cancellation.
- Phase 02 local validation passed:
  - `mvn test -Dtest=JobServiceTest,JobRepositoryTest,OrchestrationRuntimeTest,AssignmentContextServiceTest`
  - `mvn test -Dtest=JobControllerTest,PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest` (`JobControllerTest` is not present; Maven ran the existing listed controller tests.)
  - `mvn test -Dtest=WorkspaceRepositorySchemaMigrationTest,OutputArtifactServiceAttributionTest`
  - `mvn test -Dtest=OrchestrationControllerTest`
  - `git diff --check`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached successful startup on port `43617`; command exited `124` from timeout shutdown.
- Phase 03 implementation completed output provenance work:
  - Output artifact queries now support direct filters for `workspaceId`, `planId`, `jobAssignmentId`, `jobRunId`, and `runType` alongside existing agent/job/project/run/type filters.
  - Task and workflow output materialization now receives job assignment/run attribution through `OrchestrationTaskContext`; runner attribution uses first-class assignment `projectId` and effective workspace id where available.
  - Workflow runs now persist nullable agent/job/job-assignment/job-run/project/workspace/run-type attribution for output/context views.
  - Output API/detail and `/outputs` HTMX fragments expose the expanded provenance fields; route-level job fallback remains compatibility-only and is bypassed for direct attribution filters.
  - Materialized output filenames are collision-safe within a service instance via create-new writes/copies with stable numeric suffixes.
  - Chat files remain separate from output artifacts; no chat-file indexing behavior was changed.
- Phase 03 local validation passed:
  - `mvn test -Dtest=OutputArtifactServiceAttributionTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest`
  - `mvn test -Dtest=PublicApiRouteBindingTest,OutputControllerTest,OperationalUiContractControllerTest`
  - `mvn test -Dtest=OrchestrationRuntimeTest,WorkflowRunnerTest,JobServiceTest,AssignmentContextServiceTest`
  - `mvn test -Dtest=WorkflowRepositoryTest,PlanServiceTest,WorkflowStreamSupportTest,TaskStreamSupportTest` (`WorkflowStreamSupportTest` is not present; Maven ran the existing listed tests.)
  - `mvn test -Dtest=OutputArtifactServiceAttributionTest,OutputControllerTest`
  - `mvn test -Dtest=OrchestrationRuntimeTest`
  - `git diff --check`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached successful application startup on port `42229`; command exited `124` because timeout stopped the running app.
- Phase 04 implementation completed project/job/operator UX alignment:
  - Agent submit can carry explicit project context and compatibility workspace metadata, and submit results show assignment/project/effective workspace/compatibility workspace context.
  - Plan/workflow submit results show assignment context, and compatibility workspace selectors are labeled distinctly from effective project workspace selection.
  - Job editor exposes the persistent job workspace checkbox and saved status; job submit surfaces saved routing, project override, compatibility workspace metadata, and assignment context.
  - Job runs render `JobExecutionSummary` context: assignment/run ids, agent/project, effective workspace, compatibility workspace, persistent job workspace state/path, output directory, and output count.
  - Project UI copy now frames projects as shared workspace contexts, adds HTMX membership add/remove controls backed by service guards, and fixes project job links to real `/jobs/{jobId}` routes.
  - Assignment queue/history/diagnostics, agent outputs, job/project output side panels, and dashboard active work now expose more project/workspace/job assignment context.
  - Entity selector URLs preserve context params through options, selected-option, and validation requests.
- Phase 04 local validation passed:
  - `mvn test -Dtest=OrchestrationControllerTest`
  - `mvn test -Dtest=OperationalUiContractControllerTest,OrchestrationControllerTest,PublicRunSubmissionControllerTest,AgentOrchestrationControllerTest`
  - `mvn test -Dtest=ProjectServiceTest,JobServiceTest,AssignmentContextServiceTest,OutputControllerTest`
  - `mvn test -Dtest=PublicApiRouteBindingTest`
  - `git diff --check`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached successful application startup on port `37261`; command exited `124` because timeout stopped the running app.

## Review Wave Synthesis Inputs

- Backend/API reviews agree that assignment `projectId` and effective workspace context need first-class durable/read-model treatment instead of living only in assignment input JSON.
- Backend/API reviews agree jobs need a stable bridge read model that ties job definition, assignment id, job run id, agent, project, workspace, persistent workspace status, and outputs together.
- Frontend/API reviews agree project/job UI controls are missing or misleading: project-scoped agent submit, job project/workspace routing, persistent job workspace toggle/status, project membership controls, output provenance, and run-to-output navigation.
- Risk review requires assignment-routed execution and clear mutation policy before expanding project/job UI controls, so implementation must avoid direct execution paths that bypass workspace leasing.
- Playwright validation is required for changed `/projects`, `/jobs`, `/outputs`, and submit-flow surfaces.
