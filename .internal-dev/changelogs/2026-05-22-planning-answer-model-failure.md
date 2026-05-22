# Date

2026-05-22

# Change Summary

Anonymous chat planning answer submission now handles continuation failures and stale answer submissions after an answer is saved. If the continuation model call fails, such as with invalid API credentials, a planning tool call fails because a dependency is unavailable, or the worker thread is interrupted after draft edits, `ChatService` records a controlled assistant notice and returns a recoverable plan state instead of propagating a servlet error after consuming the queued question.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/InteractionQuestionTools.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/InteractionQuestionToolsTest.java`
- `docs/technical/chat-planning-tasks.md`
- `docs/end-user/plans-and-tasks.md`

# Behavioral Impact

- Planning answers remain persisted even when the follow-up model or planning tool call fails.
- `/plan <topic or instruction>` now starts anonymous plan mode and stores the inline text as the first opening goal answer instead of rejecting the command with `400 plan does not accept arguments`.
- Invalid model credentials and unavailable planning tool dependencies no longer turn the answer route into an unhandled 500 after clearing the pending question.
- The user sees a controlled chat notice explaining that the answer was saved and model/tool configuration should be fixed before continuing.
- If the draft would otherwise be left with no pending question, Magenta queues a recovery clarification so the UI has a next action instead of stalling in `DRAFT`.
- Duplicate or stale planning answer submissions now refresh the current planning prompt instead of returning `400 No active planning question exists for this conversation` or `400 Stale planning answer`.
- Anonymous plan completion validation now resolves relative artifact paths against the chat file directory before falling back to `dataRoot`, so files created by chat-scoped file tools can be read by the validator.
- Spring AI tool argument conversion failures are converted into model-visible tool diagnostics and retried inside the tool loop instead of aborting the planning continuation.
- The shared `ask_user_questions` planning/task tool now accepts object-shaped question entries and extracts their `question`, `text`, `prompt`, or `label` field before queuing prompts.
- Transient provider response extraction failures that wrap an `IOException` are retried through the existing conversation snapshot/restore path instead of immediately marking execution failed.
- Anonymous plan execution stream disconnects are recorded as transport diagnostics instead of plan execution failures, so a closed browser stream does not by itself move the plan to `NEEDS_REVIEW`.
- Browser error helpers now display server `error` payload fields instead of falling back to generic HTTP status text.

# Risks

- The failed planning question is not re-queued because the answer was already recorded. After fixing model or tool configuration, the user should answer the recovery clarification or send another planning message.
- Inline `/plan` text is treated as the answer to the first goal prompt. If the user intended a command argument rather than goal context, they should start with plain `/plan` and answer the prompts separately.
- This handles AI/tool failures in the answer-continuation path; other chat/model routes may still surface provider failures through their existing route-specific handling.
- A browser disconnect no longer marks execution failed, but the execution model still must complete normally and pass validator-gated completion for the plan to become `COMPLETED`.
- `ask_user_questions` still enforces the existing one-to-five queued question limit after normalization; extra metadata on model-provided question objects is ignored.
- Provider-side repeated response-stream closure can still fail after retry exhaustion, but a single wrapped body-close no longer bypasses transient retry handling.

# Follow-up Items

None.
