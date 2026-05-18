# Date

2026-05-18

# Change Summary

Domain 03 subplan 01 routes public plan/task/workflow run controls through saved-definition assignment submission instead of direct execution. Public submit defaults now use high priority `9`, chat no longer exposes `Execute now`, and old chat direct execution routes return a clear client error.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/js/magenta-tools.js`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
- `.internal-dev/knowledge/public-run-submission-contract.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

- Chat plan direct execution endpoints reject with `400 BAD_REQUEST` and tell callers to save and send the plan to an agent.
- Plan and task public run stream endpoints create queued `TASK_RUN` assignments and emit a submitted SSE acknowledgement.
- Workflow public run and run stream endpoints create queued `WORKFLOW_RUN` assignments instead of starting workflow runs directly.
- Supported request context fields are retained in assignment fields and input metadata.
- Public submit defaults normalize to priority `9`.
- The `/workflows` empty state now renders a deterministic `New Workflow` action and disabled `Submit to Agent` placeholder so browser validation can see the submit-to-agent surface before a workflow is selected.

# Risks

Some older clients that expected direct streaming task/workflow execution from public run endpoints now receive queued assignment semantics and must follow assignment/history surfaces for execution progress.

# Follow-up Items

- Parent validation should include focused browser checks for `/chat`, `/plans`, and `/workflows` submit controls if UI validation is in scope.
- Domain 03 subplan 04 still owns job `Start Run` conversion to `JOB_RUN` assignment submission.
