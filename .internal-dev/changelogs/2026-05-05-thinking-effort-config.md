# Date
2026-05-05

# Change Summary
Replaced `boolean think` with `Integer thinkLevel` (0–4) on `ModelConfig`, added `DEEPSEEK` endpoint type, and wired level→provider-specific API parameters in `ChatModelRouter`. OpenAI-compatible and DeepSeek models now get `reasoningEffort` set on their options; Ollama models get `ThinkLevel` granularity instead of boolean on/off.

# Files
- `src/main/java/io/mindspice/magenta2/ai/config/user/EndpointType.java` — added `DEEPSEEK`
- `src/main/java/io/mindspice/magenta2/ai/config/user/ModelConfig.java` — replaced `boolean think` with `@Nullable Integer thinkLevel`, added `@JsonIgnoreProperties(ignoreUnknown = true)`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java` — added `buildDeepSeekModel()`, `effectiveThinkLevel()`, `applyOllamaThink()`, `applyOpenAiReasoningEffort()` helpers; wired thinkLevel through default options, ollamaOptionsBuilder, and toolCallingOptions
- `config/ai-config.example.json` — updated to `thinkLevel` format; deepseek-v4 now uses `DEEPSEEK` endpoint type
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouterTest.java` — 8 new tests for DeepSeek build, level mapping per endpoint type, clamping, and null defaults
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` — updated ModelConfig constructor calls
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java` — updated ModelConfig constructor calls

# Behavioral Impact
- DeepSeek models now correctly receive `reasoning_effort: "max"` on API requests (level 4), or `"high"` (levels 1–3)
- OpenAI-compatible models receive `reasoning_effort: "low"/"medium"/"high"` (levels 1–4, with 4 clamped to "high")
- Ollama models now use ThinkLevel (low/medium/high) instead of ThinkBoolean, giving finer control over thinking depth
- Old configs with `"think": true` are silently ignored via `@JsonIgnoreProperties` — thinking defaults to 0 (disabled)
- All 136 tests pass

# Risks
- Existing configs using `"think": true` must be manually migrated to `"thinkLevel": 3` to preserve thinking behavior
- `ExternalAiConfigLoader` does not validate that the `config/ai-config.json` has been migrated — old configs silently lose thinking
- DeepSeek `reasoning_content` echo-back for multi-turn tool calls is not explicitly handled (Spring AI handles via message history)

# Follow-up Items
- Consider `ExternalAiConfigLoader` warning when summary/planning model is a reasoning-oriented endpoint
- Test DeepSeek tool-call loop end-to-end to verify reasoning_content echo-back works correctly
- Add per-request thinking level override capability for dynamic adjustment
