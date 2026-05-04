# Date
2026-05-04

# Change Summary
Removed keyword-based error detection from `ToolLoopGuard.isToolError()` that caused false positives when web page content or search results contained words like "error" or "failed". The method now only checks for the structured `"timedOut":true` JSON signal. Actual tool failures (HTTP errors, exceptions) are surfaced before result processing, so keyword heuristics were redundant and harmful.

Added OpenAI-compatible API endpoint support. `ChatModelRouter` now builds `OpenAiChatModel` for models configured with `endpointType: "OPENAI_COMPATIBLE"`, using the model's `apiKey` and `remoteEndpoint` from the external config. Added `toolCallingOptions()` as a generic options factory so callers don't need endpoint-type awareness. Added DeepSeek v4 (`deepseek-v4-pro`) as a configured model.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — removed keyword substring matching (`exception`, `error`, `failed`, `does not match current file content`, `not found`, `permission denied`) from `isToolError()`; changed `toolOptions()`/`toolFinalOptions()` return types from `OllamaChatOptions` to `ToolCallingChatOptions` interface and switched to `chatModelRouter.toolCallingOptions()`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java` — added `toolCallingOptions()` dispatching by `EndpointType`; refactored `buildModel()` to switch on endpoint type with `buildOllamaModel()` and `buildOpenAiModel()`; `ollamaOptions()` now defensively rejects non-Ollama models
- `src/main/java/io/mindspice/magenta2/ai/config/user/ModelConfig.java` — added nullable `apiKey` field for OpenAI-compatible endpoints
- `src/main/java/io/mindspice/magenta2/ai/config/user/EndpointType.java` — unchanged (already had `OPENAI_COMPATIBLE`, now functional)
- `pom.xml` — added `spring-ai-starter-model-openai` dependency
- `src/main/resources/application.yml` — excluded all six OpenAI auto-configuration classes (chat, embedding, image, audio speech, audio transcription, moderation) since models are built manually
- `config/ai-config.example.json` — added `deepseek-v4` model entry (`deepseek-v4-pro` at `https://api.deepseek.com`, 128K context)
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` — updated `FakeToolCallingManager` to return `{"timedOut":true,...}` JSON; updated `toolLoopGuardStopsAfterFiveErrorsInEightResponses` to use timedOut responses; added `toolCallingOptions()` override to `SummaryRouter` and `FakeChatModelRouter`; updated all `ModelConfig` constructors with `apiKey` parameter
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouterTest.java` — replaced `rejectsUnsupportedEndpointTypeWhenBuildingModel` with `buildsOpenAiCompatibleModel`; updated all `ModelConfig` constructors
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java` — updated `ModelConfig` constructors with `apiKey` parameter
- `src/test/java/io/mindspice/magenta2/ai/agent/job/AgentJobServiceTest.java` — updated `ModelConfig` constructors with `apiKey` parameter

# Behavioral Impact
- `ToolLoopGuard` error detection is now narrower: only `"timedOut":true` in the response JSON body triggers an error tally. Real failures (non-2xx HTTP, DNS errors, unsupported content types, tool exceptions) throw before the result text reaches `isToolError()`, so no real errors are missed. The sliding-window abort (5 errors in 8 responses) remains unchanged.
- Models configured with `"endpointType": "OPENAI_COMPATIBLE"` now build successfully. The `OpenAiApi` is constructed with `baseUrl` from `remoteEndpoint`, `apiKey` from the config, `completionsPath="/v1/chat/completions"`, and `embeddingsPath="/v1/embeddings"`. Tool calling works via `ToolCallingChatOptions`.
- The `ollamaOptions()` and `ollamaOptionsBuilder()` methods now throw `IllegalStateException` if called for a non-Ollama model. Callers that need endpoint-agnostic options should use `toolCallingOptions()`.
- `toolOptions()` and `toolFinalOptions()` in `ChatService` now return `ToolCallingChatOptions` and use `DefaultToolCallingChatOptions` as the null-model fallback instead of `OllamaChatOptions.builder().build()`.
- Spring's OpenAI auto-configuration (chat, embedding, image, audio, moderation) is excluded because models are constructed manually via `ChatModelRouter`. This avoids startup failures from the missing `spring.ai.openai.api-key` property.

# Risks
- Models that previously caused tool-loop aborts due to keyword false-positives will no longer trigger spurious aborts. The abort mechanism is now less sensitive, limited to explicit timeout signals. If a tool produces errors that don't set `timedOut` and don't throw exceptions, the model must recognize and handle the failure itself.
- The `OpenAiChatModel` builder uses `ToolCallingManager` and `ObservationRegistry` from the `ChatModelRouter` constructor. If these are null (e.g., in tests using `FakeChatModelRouter`), the real `toolCallingOptions()` method will fail. Test subclasses override this method, so production impact is nil.
- DeepSeek's API key is stored in `ai-config.example.json`. This file should not be committed to version control if the key is real.

# Follow-up Items
- End-to-end test with DeepSeek v4: tool calls, streaming, and plan-mode execution.
- Consider adding a config-file-external credential store (env vars, vault) for API keys instead of storing them in the JSON config.
