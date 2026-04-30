# Date

2026-04-29

# Change Summary

Added structured chat tool activity payloads, live SSE tool events, richer per-tool summaries, and collapsed expandable tool cards in the browser chat UI.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/model/`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java`
- `src/main/java/io/mindspice/magenta2/api/web/`
- `src/main/resources/static/js/chat-client.js`

# Behavioral Impact

Users see each completed tool call during streaming instead of waiting for the full tool loop to finish. History and non-stream responses now include additive structured tool activity details while preserving existing chat message fields.

# Risks

Tool activity payloads increase response size, bounded by display truncation and existing raw transcript retention limits.

# Follow-up Items

None.
