# Date

2026-05-12

# Change Summary

Implemented Phase 01 of the operational UI contract refactor. Project, job, output, dashboard summary, and inbox UI/API contracts now use callable endpoints and canonical public field names.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/DashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/java/io/mindspice/magenta2/api/web/JobController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/main/resources/schema.sql`
- `src/main/resources/static/js/orchestration/dashboard.js`
- `src/main/resources/static/js/orchestration/inbox.js`
- `src/main/resources/static/js/orchestration/outputs.js`
- `src/main/resources/static/js/orchestration/projects.js`
- `src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java`
- `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`

# Behavioral Impact

- `/dashboard` now has a page contract marker and `/api/dashboard/summary` returns typed summary data for Phase 02.
- `/projects` creates projects with `name`, `description`, and selected `ownerAgentId`; project detail loads workspace metadata through `/api/projects/{projectId}/workspace`.
- `/api/jobs` supports owner/project/status filters, empty draft creation, metadata updates, item CRUD routes, run-derived events, and array output responses.
- `/outputs` now queries `/api/outputs` directly instead of probing unrelated plan/job endpoints.
- Agent inbox rows no longer render dead approval controls; they render read/handled actions while user approvals keep the existing response flow.

# Risks

- Public jobs are now backed by `JobDefinition`, but agent `JOB_RUN` assignment validation still references the legacy `OrchestrationJobService` path. This is recorded in phase handoff notes for submit-to-agent phases.
- Job event responses are lightweight run summaries, not a full durable event stream.

# Follow-up Items

- Later submit-to-agent work should align `AssignmentService` job validation with canonical public job definitions.
- Later job/project operational surfaces can replace lightweight event/output views with richer activity timelines.
