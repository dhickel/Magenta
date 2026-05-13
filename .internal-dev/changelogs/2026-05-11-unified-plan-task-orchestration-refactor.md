# Changelog: Unified Plan/Task Orchestration Refactor

## Date
2026-05-11

## Change Summary
Completed a 5-phase refactor unifying the plan and task model, introducing Docker-backed execution via Podman, implementing workflows/gates/delegation, adding jobs/projects/agent networks, and building a full-screen SimplyPages orchestration dashboard. This is the single largest architectural change since project inception.

Key changes:
- **Phase 01**: Unified PlanDefinition/PlanRun model replacing separate ChatPlanRepository and TaskRepository. Single PlanService owns all definition and run lifecycle.
- **Phase 02**: Docker runtime via Podman (`DOCKER_HOST=unix:///run/user/1000/podman/podman.sock`). WorkspaceDirectoryService, WorkspaceLeaseService, OutputArtifactService for containerized execution.
- **Phase 03**: WorkflowDefinition/WorkflowRun with gates (USER_APPROVAL, AGENT_APPROVAL), delegation messaging, inbox service, WorkflowController.
- **Phase 04**: JobDefinition/JobRun with ordered work items, Project/ProjectAgentMembership agent networks, prompt profiles, settings precedence.
- **Phase 05**: OrchestrationController with SimplyPages dashboard at `/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/inbox`, `/outputs`, `/agents`, `/settings`.

## Files
New files (major):
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRun.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanKind.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRunStatus.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/JobController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java`
- `src/main/resources/static/css/orchestration.css`
- `src/main/resources/static/js/orchestration/dashboard.js`
- `src/main/resources/static/js/orchestration/plans.js`
- `src/main/resources/static/js/orchestration/workflows.js`
- `src/main/resources/static/js/orchestration/projects.js`
- `src/main/resources/static/js/orchestration/inbox.js`
- `src/main/resources/static/js/orchestration/outputs.js`

Deleted files:
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ChatPlanRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ExecutionPlan.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/ChatPlanRepositoryTest.java`

## Behavioral Impact
- Plan/task definitions are now unified in a single `plan_definitions` table with `plan_kind` distinguishing SESSION_PLAN from TASK_TEMPLATE.
- Docker execution is mandatory at startup -- the app aborts if Docker daemon is unreachable or the agent image is missing.
- The orchestration dashboard is served via `/dashboard` with SimplyPages sidebar navigation.
- `/chat` remains isolated from orchestration pages via separate controllers (FrontendController vs OrchestrationController).
- The HTMX webjar compatibility route at `/webjars/htmx.org/dist/htmx.min.js` continues to return a no-op stub.

## Risks
- SELinux enforcing mode requires `:Z` relabeling on Docker bind mounts. The `DockerRuntimeClient.bindParse()` does not include this flag, so containerized execution will fail on SELinux-enforcing hosts. See bug report `docker-bind-mounts-selinux-z-flag`.
- The orchestration dashboard JavaScript modules have no runtime test coverage -- only static DOM assertions were validated.
- Full model-backed Docker execution could not be tested end-to-end as Ollama was not running during validation.

## Follow-up Items
- Add SELinux `:Z` flag support to DockerRuntimeClient bind mounts.
- Write integration tests for Docker container execution.
- Write Playwright-based integration tests for the orchestration UI workflows (create plan, save, run; create workflow, add node, validate).
- Consider collapsing the two controller classes (FrontendController + OrchestrationController) into a router that delegates to page builders.
