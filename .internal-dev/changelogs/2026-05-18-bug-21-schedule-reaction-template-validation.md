# Date

2026-05-18

# Change Summary

Implemented bug-21 schedule and reaction assignment-template validation. Schedule and reaction saves now reject invalid assignment types and missing runtime-required saved-definition references before persistence.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentTemplateParser.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/EventReactionService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact

- Schedules default missing template assignment type to `JOB_RUN` and reject saves without a job id in the schedule, template, or template input.
- Event reactions default missing template assignment type to `REPORT`.
- Invalid `assignmentType`, `TASK_RUN` without `input.taskId`, and `WORKFLOW_RUN` without `input.workflowId` are rejected at save time for schedules and reactions.
- Schedule polling and event reaction handling use the same parser as save-time validation, so bad templates cannot be introduced through normal save routes and later fail repeatedly at runtime.
- HTMX schedule/reaction saves continue returning inline operator-visible errors, and REST schedule/reaction saves continue returning existing `400` behavior.

# Risks

- Existing persisted invalid schedule/reaction rows are not migrated or repaired by this change; the fix prevents new invalid saves through service/controller paths.

# Validation

Validated after fix commit `c3db8a4` with focused runtime/controller tests, static parser scan, `git diff --check`, bounded startup, and live browser-origin checks for schedule/reaction form defaults, inline HTMX validation errors, REST 400 validation errors, and valid schedule/reaction saves.
