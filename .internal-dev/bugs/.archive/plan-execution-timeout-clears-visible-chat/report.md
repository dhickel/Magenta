# Summary

Saved-plan execution can leave the chat transcript showing only the hard-coded execution-start user message after validation failure and stream timeout.

# Scope

Observed on conversation `c56dac5e-9544-4dba-9fb5-6595bc8f5c2e` in the local `chat-memory.db`.

# Reproduction

1. Execute a saved plan through `/api/chat/{conversationId}/plan/execute/stream`.
2. Let the model perform tool work and call `plan_complete`.
3. Have validation fail, causing the model to continue remediation.
4. Allow the execution stream to exceed `magenta.plan.execution-stream-timeout-seconds`.
5. Reload `/api/chat/{conversationId}/history`.

# Expected

Saved-plan execution should not be failed solely because an active model/tool run crosses a fixed SSE wall-clock duration.

# Actual

The chat history can contain only one user message:

`Execute the saved plan now. Work through the plan directly and report the completed result.`

The plan state is `NORMAL` / `NEEDS_REVIEW`, and execution evidence records both validation feedback and timeout failure. The visible chat transcript no longer contains the tool-call sequence because the model turn did not durably complete.

# Evidence

- `ChatService.resolveSavedPlanExecution()` calls `planService.clearConversationForExecution(conversationId)` before starting execution.
- `PlanService.clearConversationForExecution()` persists an empty chat-memory list for the conversation.
- `ContextManagementAdvisor.preparePrompt()` then immediately persists the execution-start user message.
- Tool transcript messages are accumulated in memory during `ChatService.toolChat()` and persisted only at final turn completion via `saveAssistantMessages()`. This is intentional for chat memory: incomplete tool turns should not be replayed as durable model-visible context.
- Local `audit_event` rows for `c56dac5e-9544-4dba-9fb5-6595bc8f5c2e` show `plan_complete` validation failure at sequence 132, more `file_replace` remediation through sequence 150, and no later successful completion.
- Local `ai_chat_plans.execution_evidence_json` includes `Deviation: Plan execution stream timed out after 360 seconds`.
- Local `ai_chat_memory` for the conversation has a single `user` row containing the execution-start prompt.
- `ChatController` previously forced all plan execution stream timeout values to at least one second, and `application.yml` configured 360 seconds.

# Impact

Long-running saved-plan work can be interrupted while active, leaving the user with a failed/review-needed plan caused by transport lifetime rather than model or tool failure.

# Status

Fixed. Saved-plan execution SSE timeout is disabled by default, and non-positive configured values now produce no server-side SSE timeout. Positive configured values remain supported.

# Next Action

No further action for the narrow timeout fix. Keep audit as the operational record for incomplete tool attempts; do not persist intermediate tool output to chat memory unless a later product decision changes incomplete-turn semantics.
