# Upgrade To Spring AI 2.0 When Released

## Purpose

Track the need to upgrade from our patched Spring AI 1.1.4 to Spring AI 2.0 when it reaches a stable release. The 2.0 line includes a dedicated `spring-ai-deepseek` module with proper `reasoningContent` handling (PR #5595 targets main). Our current patch is a surgical backport to keep things working on 1.1.4.

## What We Patched

Spring AI 1.1.4's `OpenAiChatModel` drops `reasoning_content` in two places:
1. Non-streaming response handler — `reasoningContent` from `ChatCompletionMessage` was not included in the `AssistantMessage` metadata map
2. Request construction (`createRequest`) — `reasoningContent` was always `null` when serializing `AssistantMessage` back to `ChatCompletionMessage`

Our fix at tag `v1.1.4` (built as `spring-ai-openai:1.1.4-magenta1`):
- File: `models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java`
- Two targeted edits: add `reasoningContent` to metadata `Map.of`, extract it from metadata into `ChatCompletionMessage` constructor

## Why 2.0 Matters

- The `spring-ai-deepseek` module handles `reasoning_content` as a first-class concept (`DeepSeekAssistantMessage.getReasoningContent()`) rather than burying it in generic metadata
- PR #5595 has a proper fix targeting the `spring-ai-deepseek` module, covering more edge cases (streaming, tool call prefix marking)
- Once 2.0 is stable, we should drop our custom jar and switch to the official release

## Upgrade Path (When 2.0 Releases)

1. Remove the `<exclusions>` and explicit `spring-ai-openai:1.1.4-magenta1` dependency from `pom.xml`
2. Bump `spring-ai.version` to `2.0.0` (or whatever GA version)
3. Add `spring-ai-starter-model-deepseek` dependency
4. Update `ChatModelRouter` to use `DeepSeekChatModel` for DeepSeek models instead of routing through `OpenAiChatModel` with the OpenAI-compatible endpoint
5. Migrate any 1.1.x → 2.0 API changes (check Spring AI migration guide)
6. Remove the `/tmp/spring-ai-patched` directory and the local `1.1.4-magenta1` jar from `~/.m2`

## Rebuild Steps (If Needed Before 2.0)

```bash
cd /tmp
git clone https://github.com/spring-projects/spring-ai.git spring-ai-patched
cd spring-ai-patched
git checkout v1.1.4
git checkout -b magenta-reasoning-content-fix

# Apply fixes to models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java:
# Fix 1 ~line 223: add "reasoningContent" entry to metadata Map.of
# Fix 2 ~line 627: extract reasoningContent from AssistantMessage metadata into ChatCompletionMessage

# Compile single class
javac -cp "<existing-1.1.4-classpath>" \
  -d compiled \
  models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java

# Repackage jar
cp ~/.m2/repository/org/springframework/ai/spring-ai-openai/1.1.4/spring-ai-openai-1.1.4.jar \
   /tmp/spring-ai-openai-1.1.4-magenta1.jar
cd compiled && jar uf /tmp/spring-ai-openai-1.1.4-magenta1.jar org/

# Install
mvn install:install-file \
  -Dfile=/tmp/spring-ai-openai-1.1.4-magenta1.jar \
  -DgroupId=org.springframework.ai \
  -DartifactId=spring-ai-openai \
  -Dversion=1.1.4-magenta1 \
  -Dpackaging=jar \
  -DgeneratePom=true
```

## Upstream References

- Spring AI issue #5086: https://github.com/spring-projects/spring-ai/issues/5086
- Spring AI PR #5595: https://github.com/spring-projects/spring-ai/pull/5595
- Spring AI PR #5908: https://github.com/spring-projects/spring-ai/pull/5908
