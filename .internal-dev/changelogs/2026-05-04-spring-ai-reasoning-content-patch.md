# Date
2026-05-04

# Change Summary
Patched Spring AI 1.1.4's `OpenAiChatModel` to preserve `reasoning_content` across multi-turn tool-calling conversations. DeepSeek's API requires `reasoning_content` from assistant messages to be passed back in subsequent requests when thinking mode is enabled. Spring AI 1.1.4 drops this field in two places, causing `400 - The reasoning_content in the thinking mode must be passed back to the API` during any conversation involving tool calls (planning, web search, shell exec, etc.).

Two-line fix in `OpenAiChatModel.java` on the `v1.1.4` tag:
1. Added `reasoningContent` to the non-streaming response metadata `Map.of` (the streaming path already included it)
2. Extracted `reasoningContent` from `AssistantMessage` metadata and passed it through to `ChatCompletionMessage` constructor instead of `null`

Built as `spring-ai-openai:1.1.4-magenta1` and installed to local Maven repo. Magenta2's `pom.xml` excludes the BOM-managed `spring-ai-openai` and declares the patched version directly.

Upstream tracking: Spring AI issues #5038, #5086, #5898; PRs #5595, #5908. None merged as of 1.1.4.

# Files
- `/tmp/spring-ai-patched/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java` — two changes at lines 217-223 and 627-628 of the original 1.1.4 source
- `pom.xml` — exclude `spring-ai-openai` from `spring-ai-starter-model-openai`, add direct dependency on `spring-ai-openai:1.1.4-magenta1`

# Behavioral Impact
- DeepSeek v4 (and any thinking-enabled OpenAI-compatible model) can now participate in multi-turn tool-calling conversations without the 400 `reasoning_content` error
- No effect on Ollama models or non-thinking OpenAI-compatible models — `reasoningContent` is null for those and the null propagates harmlessly
- The `ChatCompletionMessage` record already has the `reasoningContent` field annotated with `@JsonProperty("reasoning_content")`; Jackson serialization includes it when non-null

# Risks
- The patched jar is only in the local Maven repo (`~/.m2`). It must be rebuilt if the Maven cache is cleared or if the project is built on a different machine
- The 1.1.4-magenta1 version is not published anywhere; team members must build from the patched source
- Future Spring AI 1.1.x releases (1.1.5, 1.1.6) will not include this fix — the version pin prevents accidental upgrade

# Follow-up Items
- Upgrade to Spring AI 2.0 when released; the `spring-ai-deepseek` module in 2.0.x has a proper fix (PR #5595)
- Document the patched source location and rebuild steps
- Consider publishing the patched jar to a private Maven repo if multi-machine development is needed
