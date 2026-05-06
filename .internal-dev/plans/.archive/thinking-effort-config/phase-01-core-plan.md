# Context

Magenta2 currently has a single `boolean think` on `ModelConfig`. This is a blunt on/off toggle that only works for Ollama. For OpenAI-compatible models (including DeepSeek), the `think` boolean is completely ignored — `buildOpenAiModel()` never reads it and never sets `reasoningEffort` on the options.

DeepSeek and OpenAI both support a `reasoning_effort` parameter with level granularity. Ollama supports `think` with level granularity (`low`/`medium`/`high`). Spring AI JARs expose builder methods for all of these — we just don't use them.

Additionally, DeepSeek diverges from standard OpenAI in several ways that make the generic `OPENAI_COMPATIBLE` bucket problematic: it requires `reasoning_content` echo-back in multi-turn tool calls, accepts `"max"` as a reasoning effort value (OpenAI rejects it), and has tool_choice restrictions.

# Goal

Replace `boolean think` with `Integer thinkLevel` (0–4) on `ModelConfig`, split `DEEPSEEK` into its own `EndpointType`, and wire the level through `ChatModelRouter` to the appropriate provider-specific API parameter. Backward-compatible with existing boolean `think` configs.

# Provider Level Correlation

| Level | OpenAI `reasoning_effort` | DeepSeek `reasoning_effort` | Ollama `think` |
|-------|--------------------------|----------------------------|----------------|
| 0 (off) | omit param | omit param | disableThinking() |
| 1 (low) | `"low"` | `"high"` (aliased by DeepSeek) | thinkLow() / `"low"` |
| 2 (medium) | `"medium"` | `"high"` (aliased by DeepSeek) | thinkMedium() / `"medium"` |
| 3 (high) | `"high"` | `"high"` (default) | thinkHigh() / `"high"` |
| 4 (max) | **clamp to `"high"`** | `"max"` | **clamp to `"high"`** |

Key insight: `OpenAiChatOptions.reasoningEffort` is a `String`, not an enum. Jackson serializes whatever string we set directly into the JSON payload field `reasoning_effort`. No Spring AI JAR changes needed.

# In Scope

1. Add `DEEPSEEK` to `EndpointType`
2. Add `Integer thinkLevel` to `ModelConfig`, deprecate-old-config-path `boolean think` (accept both, `thinkLevel` wins)
3. Split `ChatModelRouter`: dedicated `buildDeepSeekModel()` method, or parameterize `buildOpenAiCompatibleModel()` with endpoint-type-aware mapping
4. Wire `thinkLevel` → provider-specific options in all three router code paths (build model default options, ollamaOptionsBuilder, toolCallingOptions)
5. Update `ai-config.example.json`
6. Update tests

# Out of Scope

- Anthropic thinking budget (no Spring AI Anthropic module exists yet)
- Per-request thinking level overrides (the level is set in model config — dynamic override is a separate feature)
- `reasoning_effort` for non-DeepSeek OpenAI-compatible models that don't support it (DeepSeek's `deepseek-reasoner` endpoint is the specific target; generic `OPENAI_COMPATIBLE` with `thinkLevel` set will just pass through the string — if the endpoint rejects it, that's a config error)

# Decisions Made

1. **Full `DEEPSEEK` enum value**, not passthrough on `OPENAI_COMPATIBLE`. Rationale: DeepSeek already diverges from OpenAI in three ways (reasoning_content echo, `"max"` reasoning effort, tool_choice restrictions). Future divergence is likely. Explicit is better than guessing.

2. **Integer 0–4 scale** rather than a string enum. Provider string values differ (`"max"` vs `"high"`). An integer lets the router own the mapping. Slightly less readable in config but unambiguous.

3. **No Spring AI JAR changes.** `OpenAiApi`, `OpenAiChatModel`, `OpenAiChatOptions` all work for DeepSeek as-is. The DeepSeek endpoint type reuses `OpenAiApi` and `OpenAiChatModel` internally — it's a config/routing distinction, not a different HTTP client.

4. **`thinkLevel` overrides `think`.** If both are present in config, `thinkLevel` wins. If only `think: true` is present, map to `thinkLevel: 3` (high/default). If only `think: false`, map to `thinkLevel: 0`. This keeps existing configs working.

# Files to Change

1. **`src/main/java/io/mindspice/magenta2/ai/config/user/EndpointType.java`** — add `DEEPSEEK`
2. **`src/main/java/io/mindspice/magenta2/ai/config/user/ModelConfig.java`** — add `@Nullable Integer thinkLevel`, keep `boolean think` for backward compat
3. **`src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java`** — add `buildDeepSeekModel()` and/or refactor `buildOpenAiModel()` to accept endpoint-type-aware mapping; wire `thinkLevel` in all options builder paths
4. **`config/ai-config.example.json`** — show `thinkLevel` usage for DeepSeek and Ollama examples
5. **`src/test/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouterTest.java`** — add tests for level mapping per endpoint type
6. **`src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java`** — may need no changes if Jackson handles the new field automatically (it should)

# Open Questions for the Agent Fleshing Out the Plan

1. **Backward compat:** Should we add a Jackson deserialization customizer that converts `"think": true` → `thinkLevel: 3`, or handle it in the router? Router-based is simpler but spreads the compat logic.

2. **`toolCallingOptions()` vs per-request:** Currently `toolCallingOptions()` for `OPENAI_COMPATIBLE` builds a bare `OpenAiChatOptions` with only model name. Should it also include the default `reasoningEffort` from model config? (Answer should be yes — thinking level should propagate to tool-calling loops.)

3. **Ollama `thinkLevel` → `ThinkOption` mapping:** Should `thinkLevel` on an Ollama model produce a `ThinkBoolean` (level 0 vs >0) or a `ThinkLevel` (1/2/3/4→high)? Using `ThinkLevel` for 1–3 and clamping 4 to `ThinkLevel.HIGH` gives finer control than boolean. But some Ollama models may only understand boolean `think`. The agent should check whether the Ollama API version in play supports `ThinkLevel`.

4. **Config example updates:** Should we show both `"think": true` (legacy) and `"thinkLevel": 4` (new) in the example, or only the new format?

5. **`reasoning_effort` on non-reasoning models:** If a DeepSeek config has `thinkLevel: 4` but the `remoteModelName` is `"deepseek-chat"` (not `"deepseek-reasoner"`), should we send `reasoning_effort` anyway, or warn/omit? The DeepSeek API likely ignores it on non-reasoning models, but worth verifying.

# Provider Divergence Reference

| Behavior | OpenAI | DeepSeek | Ollama |
|----------|--------|----------|--------|
| API base path | `/v1/chat/completions` | `/v1/chat/completions` | `/api/chat` |
| Auth | `Authorization: Bearer <key>` | `Authorization: Bearer <key>` | none |
| Reasoning effort key | `reasoning_effort` | `reasoning_effort` | `think` (in `options`) |
| Reasoning values | `low`, `medium`, `high` | `high`, `max` (low/med→high) | `low`, `medium`, `high` + boolean |
| Reasoning response key | `reasoning_content` | `reasoning_content` | `message.thinking` |
| Echo-back required | no | yes (multi-turn tool calls) | n/a |
| Tool choice restrictions | none | `deepseek-reasoner` rejects `tool_choice` | model-dependent |
| Spring AI model class | `OpenAiChatModel` | `OpenAiChatModel` | `OllamaChatModel` |
| Spring AI options class | `OpenAiChatOptions` | `OpenAiChatOptions` | `OllamaChatOptions` |

# Spring AI JAR Methods to Call (Already Exist, Currently Unused)

```java
// OpenAI/DeepSeek path
OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
    .reasoningEffort("max");  // String, Jackson-serialized to reasoning_effort

// Ollama path
OllamaChatOptions.Builder options = OllamaChatOptions.builder()
    .thinkLow();       // ThinkLevel("low")
    .thinkMedium();    // ThinkLevel("medium")
    .thinkHigh();      // ThinkLevel("high")
    .enableThinking(); // ThinkBoolean(true) — current behavior
    .disableThinking();// ThinkBoolean(false)
```
