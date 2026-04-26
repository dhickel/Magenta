Date
2026-04-25

Change Summary
Implemented chat-scoped Spring AI tool execution with context-only tool transcripts. Tool-enabled turns now use a blocking user-controlled loop, large tool outputs are retained briefly in context, and old large outputs are rewritten to summaries after four subsequent user turns.

Files
- src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java
- src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java
- src/main/java/io/mindspice/magenta2/ai/chat/tool/
- src/test/java/io/mindspice/magenta2/ai/chat/tool/

Behavioral Impact
Agents with configured approvedTools can execute registered Spring AI tools. Browser history renders tool metadata summaries, not raw tool output. Streaming chat falls back to a blocking response when tools are enabled.

Risks
Tool result summaries are structural rather than semantic, so exact raw output is intentionally lost after truncation for large results.

Follow-up Items
Add concrete read tools and end-to-end model tests once the first tool workflow is selected.
