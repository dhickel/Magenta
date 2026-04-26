# Date

2026-04-25

# Change Summary

Added configured model endpoint routing for chat execution and summarization.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/config/ChatBeanConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouterTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

# Behavioral Impact

- Chat execution now resolves selected configured model keys or remote model names through `ChatModelRouter`.
- Each configured Ollama model gets a ChatModel built with that model's `remoteEndpoint`.
- Context summarization now uses the summarization model's configured endpoint instead of the application-wide default Ollama endpoint.
- Unsupported endpoint types fail explicitly instead of silently using the wrong client.

# Risks

- `OPENAI_COMPATIBLE` remains represented in config but is not yet implemented for execution.
- Endpoint-specific clients are cached for the running process; config changes still require restart.

# Follow-up Items

- Add OpenAI-compatible execution when there is a concrete workflow needing it.
