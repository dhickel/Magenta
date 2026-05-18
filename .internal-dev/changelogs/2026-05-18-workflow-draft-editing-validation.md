# Date

2026-05-18

# Change Summary

Implemented public-alpha remediation domain 04 subplan 01 for bug-03. Workflow draft saves now accept structurally valid but executable-incomplete graphs, while explicit validation, submit, and run paths keep strict executable graph validation.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

Users can incrementally build approval workflows and task-node graphs through the HTMX workflow editor without draft saves being blocked by missing future branches, route conditions, or runtime input routes. Approval/control route conditions can be added and edited in server-rendered forms.

The `/workflows` page no longer loads the current `workflows.js` graph composer because it replaced the server-rendered HTMX editor and used API transport for authoring. The static JS file remains for the later JS-island narrowing cleanup.

# Risks

Executable validation still reflects the existing validator behavior; the separate bug-04 work remains responsible for adding nonempty executable workflow enforcement.

# Validation

Focused workflow/controller tests passed with 90 tests, `git diff --check` passed, static scan confirmed draft save paths remain separate from validate/submit/run paths, and bounded startup reached `Started Magenta2Application` on port `40027`. Delegated browser validation remains the subplan gate before marking bug-03 passed.

First delegated validation failed because the live page still attached the graph composer. The fix pass removed that attachment and added page-level regression coverage that `/workflows` does not load `workflows.js` or render the graph composer.
