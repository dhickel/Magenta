# Date
2026-05-16

# Change Summary
Fixed agent queue transcript recovery and cancellation lifecycle behavior.

- Added durable `assignment_conversation_links` persistence and schema initialization.
- Recorded assignment-to-conversation links before model-backed task/workflow execution.
- Expanded transcript and diagnostics lookup to merge checkpoint/output IDs, durable links, and legacy plan-run/session metadata fallback IDs.
- Changed queued/non-running assignment cancellation to terminal `CANCELLED`.
- Changed running assignment cancellation to preserve lease ownership, request cancellation, interrupt local work, and let the runner finalize `CANCELLED`.
- Added stale `CANCEL_REQUESTED` recovery.
- Removed the queue `Watch` button, added cancel confirmation, and made Diagnostics refresh the transcript panel out of band.

# Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/schema.sql`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact
Operators can see assignment transcripts even when the primary checkpoint missed the conversation ID. Cancel now has a confirmation, running work receives a local interruption signal, and cancel-requested assignments no longer remain stuck indefinitely after lease expiry.

# Risks
Legacy transcript recovery depends on `plan_runs.plan_id` and `ai_chat_session_metadata.active_task_run_id` matching inside the assignment execution window. Very old rows with missing or heavily rewritten metadata may still lack a recoverable conversation link.

# Follow-up Items
None.
