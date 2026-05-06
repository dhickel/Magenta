# Date

2026-05-06

# Change Summary

Removed legacy task deliverables from reusable task definitions, task drafts, task persistence, task tools, task approval validation, API/editor payloads, and new database schema. Typed task outputs are now the single runtime/output contract for task execution and workflow binding.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskDraft.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/task/TaskTools.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/schema.sql`
- `src/test/java/io/mindspice/magenta2/ai/chat/task/TaskServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `.internal-dev/knowledge/task-workflow-schema-and-run-snapshots.md`

# Behavioral Impact

Task creation and draft approval now require title, goal, at least one named output, at least one step, and at least one validation criterion. Task deliverables are no longer accepted as an editable task section or rendered in the task editor/API response. Existing databases with old task deliverables columns remain readable because repository queries ignore those columns, and old task run snapshots containing a legacy `deliverables` property still deserialize.

# Risks

Clients that still submit or expect task-level `deliverables` must move to typed `outputs`. Existing plan-mode deliverables were intentionally left unchanged.

# Follow-up Items

None.
