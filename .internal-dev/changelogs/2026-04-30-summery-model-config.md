# Summery Model Config

## Date

2026-04-30

## Change Summary

Replaced summarization-agent configuration with a top-level `summeryModel` model key. Internal context compaction and conversation title jobs now use Magenta-owned prompts with the configured model instead of a summarization agent prompt/tools.

## Files

- `config/ai-config.example.json`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/agent/job/AgentJobService.java`
- `src/test/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoaderTest.java`
- `src/test/java/io/mindspice/magenta2/ai/agent/job/AgentJobServiceTest.java`

## Behavioral Impact

External AI config must define `summeryModel` as a key in `models`. The example config points it to `local-qwen`. Summary/title model calls do not use an agent system prompt or tools.

## Risks

Existing local configs that still define only `summarizationAgent` need to be updated to `summeryModel`.

## Follow-up Items

- None.
