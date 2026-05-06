# Date
2026-05-04

# Change Summary
Normalized thinking/reasoning extraction into a single provider-agnostic method. Spring AI stores thinking in different locations depending on provider: Ollama puts it in `ChatGenerationMetadata["thinking"]`, while OpenAI/DeepSeek puts it in `AssistantMessage.properties["reasoningContent"]`. The extraction logic was duplicated across `ChatService.thinkingText()` and `ContextManagementAdvisor.assistantMessage()`, and both only checked the Ollama location — DeepSeek reasoning was silently dropped in all paths (rendering, persistence, audit, history).

Made `ChatService.thinkingText()` the single static extraction point that checks both locations. `ContextManagementAdvisor` now delegates to it instead of duplicating the logic. Added tests for all three cases: reasoningContent only, Ollama priority, and end-to-end rendering.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — `thinkingText()` made static, added `"reasoningContent"` fallback from `AssistantMessage.properties`; `stringValue()` made static
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java` — replaced inline extraction with `ChatService.thinkingText(generation)`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` — 3 new tests for reasoningContent extraction

# Behavioral Impact
- DeepSeek (and any OpenAI-compatible model with `reasoning_content`) thinking now displays in the frontend `<details>` toggle, persists in chat memory under `"magenta.thinking"`, and appears in audit logs
- All four paths converge: tool chat, plain chat (via advisor), streaming, and history retrieval
- Ollama thinking unchanged — `"thinking"` metadata takes priority when both keys are present
- `<think>` tag fallback unchanged — continues to work for models that embed thinking in visible text

# Risks
- The `"reasoningContent"` key name comes from Spring AI 1.1.4's `ChatCompletionMessage` record field. If Spring AI renames this in a future version, the fallback silently returns null (no crash, just no thinking display)
- Static methods on `ChatService` are package-private — safe from external coupling but worth noting for future package restructuring

# Follow-up Items
- Compaction summarization only captures `getText()`, losing thinking from older messages. Consider including thinking text in summaries for thinking-heavy models
- Non-streaming REST `MsgResponse` has no `thinkingHtml` field — thinking is only available via SSE streaming
