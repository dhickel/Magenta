# Topic
Spring AI thinking/reasoning metadata locations across providers

# Source References
- `org.springframework.ai.ollama.OllamaChatModel` (spring-ai-ollama 1.1.4) — stores thinking via `ChatGenerationMetadata.Builder.metadata("thinking", message.thinking())`
- `org.springframework.ai.openai.OpenAiChatModel` (spring-ai-openai 1.1.4-magenta1) — stores `reasoningContent` via `AssistantMessage.Builder.properties(Map.of("reasoningContent", message.reasoningContent(), ...))`
- `org.springframework.ai.chat.model.Generation` — `getMetadata()` returns `ChatGenerationMetadata`; `getOutput()` returns `AssistantMessage` with its own `getMetadata()` (properties map)
- `org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage` — has `reasoningContent()` accessor, Jackson-mapped from `reasoning_content` JSON field

# Key Takeaways
- There is no standard location for thinking/reasoning in Spring AI 1.1.4. Ollama and OpenAI providers put it in different metadata maps on the `Generation` object
- To extract thinking provider-agnostically, check both: `generation.getMetadata().get("thinking")` (Ollama) and `generation.getOutput().getMetadata().get("reasoningContent")` (OpenAI/DeepSeek)
- This is arguably a Spring AI bug — both should use the same metadata key and location. The inconsistency likely stems from different contributors building each provider integration
- Anthropic would add a third location if/when Spring AI adds an Anthropic module — the single-extraction-point pattern makes adding it a one-line change
- `ContextManagementAdvisor` needs to use the same extraction logic since it processes `Generation` objects in the ChatClient advisor chain

# Engine Relevance
- Magenta2 `ChatService.thinkingText()` is the canonical extraction point — add new provider locations there
- The `"magenta.thinking"` key in `AssistantMessage.properties` is Magenta's internal normalization — always store extracted thinking under this key for persistence
- Spring AI 2.0 may fix this with the `spring-ai-deepseek` module (PR #5595)

# Open Questions
- Will Spring AI 2.0 standardize thinking/reasoning metadata locations?
- Does Anthropic's API thinking (separate content block type) map to this same metadata pattern?
