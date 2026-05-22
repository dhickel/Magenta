## Date

2026-05-22

## Change Summary

Renamed the external AI config summary field to `summaryModel` while keeping legacy `summeryModel` configs readable as a fallback. Context compaction now resolves through the configured compaction model, falling back to the effective summary model when compaction is not set. Conversation title jobs now enqueue with the effective summary model instead of the chat turn's selected model.

Added a `deepseek-flash-v4-zero` DeepSeek model entry with `thinkLevel: 0` to the example AI config and made it the default `summaryModel`.

## Files

- `config/ai-config.example.json`
- `docs/technical/configuration-operations.md`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiUserConfigConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileSeeder.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/agent/job/AgentJobService.java`
- Focused tests under `src/test/java`

## Behavioral Impact

New configs should use `summaryModel`. Existing configs that still define only `summeryModel` continue to load.

Generated conversation titles are no longer coupled to the user-selected chat model. They use the effective summary model from runtime settings or file config.

## Risks

Existing persisted title jobs keep their stored `selected_model` and will run with that stored value. Newly enqueued title jobs use the summary model.

## Follow-up Items

None.
