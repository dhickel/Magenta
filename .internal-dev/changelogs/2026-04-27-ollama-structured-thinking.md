## Date

2026-04-27

## Change Summary

Updated Ollama chat integration to use the verified `qwen3.6:35b` model alias and prefer Spring AI's structured Ollama thinking metadata over legacy `<think>...</think>` text parsing.

## Files

- `config/ai-config.example.json`
- `src/main/resources/application.yml`
- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/SQLiteChatMemoryRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/repository/SQLiteChatMemoryRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoaderTest.java`

## Behavioral Impact

Chat requests now enable Ollama native thinking and render the UI thinking panel from structured Spring AI generation metadata when present. Legacy `<think>...</think>` extraction remains only as a fallback for older stored/model output. Chat memory persists assistant metadata so structured thinking survives history reloads. Tool-call turns accumulate their structured thinking and attach it to the final assistant message.

## Risks

The SSE endpoint now emits completed assistant messages from the structured response path rather than token-by-token text when preserving structured thinking is required.

## Follow-up Items

- Add a live Ollama/Spring AI tool-call smoke test when a stable local test profile is available.
