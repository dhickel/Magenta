# Date

2026-05-18

# Change Summary

Completed the public alpha execution, history, and streams remediation domain. Public run surfaces now submit saved definitions to agents as high-priority assignments, saved-plan transcript rows are preserved, plan SSE names are semantic, job start controls create `JOB_RUN` assignments, and schedule/reaction assignment templates are validated before persistence.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/JobController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentTemplateParser.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/EventReactionService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java`
- Focused controller/service/repository tests covering the domain contracts.
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

Operators can still submit plans, tasks, workflows, and jobs from public surfaces, but those actions now route through durable assignments with observable priority, type, and queue state. Public direct chat plan execution is rejected with an explicit 400 response. Schedule and reaction saves fail fast with operator-visible validation when templates cannot produce valid assignments.

# Risks

`POST /api/jobs/{jobId}/runs` now returns a `WorkAssignment` rather than a `JobRun`, which is an intentional public alpha contract change. Live validation did not run a model-backed saved-plan execution; transcript preservation is covered by focused service tests.

# Follow-up Items

Proceed to domain `04-workflow-authoring-runtime-js` from the updated integration branch after merging this domain.

# Validation

Domain validation passed with 213 focused tests, full `mvn test` with 511 tests, `git diff --check`, bounded startup, and a live browser-origin sweep. Evidence paths: `/tmp/domain03-bounded-startup.log`, `/tmp/domain03-browser-evidence.json`, `/tmp/domain03-console-errors.json`, `/tmp/domain03-network.json`, `/tmp/domain03-live-app.log`.
