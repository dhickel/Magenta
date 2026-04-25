# Context

Agent configuration now stores `systemPrompt` as a prompt file path, such as `prompts/system.md`, instead of inline prompt text.

# Goal

Load each configured agent system prompt from disk during external AI config loading.

# In Scope

- Resolve relative prompt paths against the directory containing the AI config file.
- Replace loaded `AgentConfig.systemPrompt()` values with prompt file contents.
- Fail fast for blank, missing, or unreadable prompt paths.
- Update focused loader tests and local package guidance.

# Out of Scope

- Adding a new public config field.
- Changing chat service prompt behavior.
- Supporting inline prompt text compatibility.

# Implementation Steps

- Add prompt path resolution to `ExternalAiConfigLoader`.
- Preserve model/default/data root config while replacing agent records with resolved prompt text.
- Update JSON/YAML loader tests to use prompt files.
- Document path-only `systemPrompt` semantics in the user config package guide.

# Validation

- Run `mvn test`.

# Exit Criteria

- Config files using `systemPrompt: "prompts/system.md"` load the Markdown content before chat service consumes the config.
- Missing or blank prompt paths fail during config load.
