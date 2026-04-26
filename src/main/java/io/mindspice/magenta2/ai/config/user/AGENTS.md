## User AI Configuration Package

This package owns user-editable AI, model, endpoint, and agent configuration.

### Responsibilities
- Load external JSON/YAML AI configuration.
- Represent configured models, agents, summarization agent selection, context buffer policy, endpoint types, prompts, approved tool names, and per-agent shell command allowlists.
- Keep config records simple and close to the external file shape.
- Treat external agent `systemPrompt` values as required prompt file paths, resolved relative to the config file directory.

### Change guidance
- Do not add config fields until runtime behavior actually consumes them.
- Keep defaults and validation easy to explain.
- Avoid coupling config loading to chat-specific behavior.
- Return loaded `AgentConfig.systemPrompt()` values as prompt text after resolving the external path.
- Keep this guide updated when the config file shape, validation rules, supported endpoint types, or wildcard semantics change.

### Validation
- Update loader tests for new or changed config fields.
- Include JSON and YAML coverage when external config compatibility changes.
