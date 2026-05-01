# Date

2026-05-01

# Change Summary

Added stored-context maintenance so Magenta compacts or trims persisted chat memory before returning context usage to the browser. Tool-heavy turns now run a post-save maintenance pass before reporting final usage, preventing the context meter from displaying an over-budget stored state after successful execution.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`

# Behavioral Impact

UI-facing context usage is now based on maintained persisted context rather than raw post-save history. If end-of-turn maintenance compacts memory, the stream emits a visible system compaction notice and the final `done` event reports the post-compaction usage.

# Risks

History and switch endpoints may now trigger compaction when loading an already oversized conversation. This keeps the meter trustworthy but means simply opening a conversation can rewrite stored memory if it is over budget.

# Follow-up Items

- Add browser-level assertions for streamed end-of-turn compaction notices if the UI test surface expands.
