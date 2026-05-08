# Date

2026-05-08

# Change Summary

Replaced placeholder task execution with chat/model-backed task execution. Task runs now enter durable `EXECUTE_TASK` context, complete only through `task_complete`, and fall back to `NEEDS_REVIEW` or `FAILED` when completion is not accepted. Workflow and orchestration task execution now route through the same task runner. Orchestration event reactions are transactional, and job items now persist retry and continue-on-failure policy.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/*`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/resources/schema.sql`

# Behavioral Impact

User-facing task, workflow, and orchestration task runs no longer fabricate output values. Direct task SSE now emits conversation id, run id, progress/tool activity, terminal status, output values, and errors from model-backed execution.

# Risks

Existing callers that depended on immediate synthetic task output must now have a usable model/tool path. Long-running direct task SSE currently blocks the request thread while the model-backed Flux runs.

# Follow-up Items

Add more end-to-end controller coverage for live SSE event ordering when a real `ToolCallingManager` emits multiple tool transcripts.
