# Date

2026-05-22

# Change Summary

Anonymous chat planning answer submission now handles continuation failures after the answer is saved. If the continuation model call fails, such as with invalid API credentials, a planning tool call fails because a dependency is unavailable, or the worker thread is interrupted after draft edits, `ChatService` records a controlled assistant notice and returns a recoverable plan state instead of propagating a servlet error after consuming the queued question.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `docs/technical/chat-planning-tasks.md`
- `docs/end-user/plans-and-tasks.md`

# Behavioral Impact

- Planning answers remain persisted even when the follow-up model or planning tool call fails.
- Invalid model credentials and unavailable planning tool dependencies no longer turn the answer route into an unhandled 500 after clearing the pending question.
- The user sees a controlled chat notice explaining that the answer was saved and model/tool configuration should be fixed before continuing.
- If the draft would otherwise be left with no pending question, Magenta queues a recovery clarification so the UI has a next action instead of stalling in `DRAFT`.
- Browser error helpers now display server `error` payload fields instead of falling back to generic HTTP status text.

# Risks

- The failed planning question is not re-queued because the answer was already recorded. After fixing model or tool configuration, the user should answer the recovery clarification or send another planning message.
- This handles AI/tool failures in the answer-continuation path; other chat/model routes may still surface provider failures through their existing route-specific handling.

# Follow-up Items

None.
