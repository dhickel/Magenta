# Date

2026-04-25

# Change Summary

External AI config loading now treats each agent `systemPrompt` value as a required prompt file path and resolves it to prompt text during load.

# Files

- `src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java`
- `src/test/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoaderTest.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AGENTS.md`

# Behavioral Impact

Relative prompt paths are resolved from the AI config file directory, so `config/ai-config.example.json` can reference `prompts/system.md`. Missing or blank prompt paths fail during config loading.

# Risks

Existing configs that still inline prompt text in `systemPrompt` must be migrated to prompt files.

# Follow-up Items

None.
