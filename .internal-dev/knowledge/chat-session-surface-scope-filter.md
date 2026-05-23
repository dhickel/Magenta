# Topic
Chat session list scoping by explicit UI surface and conversation mode.

# Source References
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/js/avatar-chat.js`

# Key Takeaways
- A shared chat backend needs an explicit UI-surface marker if only one browser surface should own the persisted session sidebar.
- Filtering the session list by conversation mode is still necessary so planning/task execution conversations stay out of the normal chat index.
- Session-origin metadata alone is not enough when multiple browser surfaces reuse the same `/api/chat` endpoints.

# Engine Relevance
Use explicit surface metadata plus mode filtering for future chat sidebar work so internal assistant usage does not leak into user-facing session lists.

# Open Questions
None from this pass.
