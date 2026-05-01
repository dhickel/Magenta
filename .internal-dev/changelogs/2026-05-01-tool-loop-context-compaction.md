# Date

2026-05-01

# Change Summary

Added context-management checkpoints inside tool execution loops so Magenta compacts or aborts tool use before sending an oversized follow-up prompt. Compaction now preserves prior hidden summaries when producing a new summary, and the web stream can surface live context usage plus visible system compaction notices.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatStreamEvent.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`

# Behavioral Impact

Long tool-running turns now re-check context after tool results and before another model call. The UI updates token usage during streaming tool iterations and renders compaction notices as yellow system messages.

Follow-up fix: `ChatService` now records checkpoint usage directly before emitting tool messages, and the browser ignores stream events without `contextUsage` instead of resetting the meter to zero.

# Risks

Active in-flight tool compaction summarizes older tool-loop messages, so exact older raw tool outputs may be unavailable to the model during the same turn if the context budget is exhausted.

# Follow-up Items

- Consider adding browser-level tests for the new `context` and `system` SSE events.
