## Chat Service Package

This package owns chat use-case behavior.

### Responsibilities
- Resolve conversation ids, selected models, prompts, context compaction, memory, streaming behavior, and history conversion.
- Read default runtime agent model, prompt, tool, compaction, and context-buffer choices through orchestration services when they are available.
- Coordinate chat plan-mode entry, exit, and saved-plan execution through the plan service.
- Route configured chat models to their configured endpoint clients.
- Keep controller logic thin by centralizing chat behavior here.
- Use file configuration from `ai.config.user` for model endpoint definitions without duplicating config parsing rules.

### Change guidance
- Keep service methods focused on current chat workflows.
- Do not introduce general agent orchestration, scheduling, or tool execution here until a concrete chat use case requires it.
- Keep transport details and persistence details behind controller and repository boundaries.
- Keep this guide updated when chat behavior, service contracts, or orchestration responsibilities change.

### Validation
- Add focused unit tests for prompt resolution, model selection, context compaction, history conversion, streaming, and rendering behavior.
- Run affected controller tests when service response behavior changes.
