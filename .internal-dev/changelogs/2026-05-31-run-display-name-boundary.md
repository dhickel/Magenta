## Date

2026-05-31

## Change Summary

Enforced the user-visible run display name boundary for non-job task and workflow assignment submissions, including the previously bypassing plan submit/stream and direct agent assignment routes.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentTemplateParser.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentContextServiceTest.java`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/services.md`
- `docs/api/00-index.md`
- `docs/technical/api-reference.md`
- `docs/technical/workspaces-tools-outputs.md`

## Behavioral Impact

New non-job `TASK_RUN` and `WORKFLOW_RUN` assignments require a nonblank `runDisplayName` before persistence. `JOB_RUN` assignment submission remains valid without a route-supplied run name when job context owns naming. Existing stored assignments with null names remain readable.

## Specification Impact

Updated API and service specifications to make the run display name requirement explicit across plan, task, workflow, and direct agent assignment boundaries.

## Risks

Internal or tool-driven task/workflow assignment call sites that still omit a run display name will now receive controlled validation errors from the shared assignment boundary.

## Follow-up Items

- Independent validation should confirm the focused route and assignment service tests pass from a clean worktree without the out-of-scope `workflow/v2` prototype.
