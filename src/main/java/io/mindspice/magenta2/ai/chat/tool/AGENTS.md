## Chat Tool Package

This package owns chat-scoped tool execution support.

### Responsibilities
- Resolve configured Spring AI tools for chat agents.
- Represent tool activity as Magenta-owned chat context messages.
- Keep tool output retention and truncation policy local to chat tooling.
- Own chat-approved file tools that operate inside the configured agent data root.

### Change guidance
- Do not add separate durable tool-result storage unless a concrete workflow requires it.
- Avoid replay, approval, or orchestration behavior without an explicit user-facing tool use case.
- Keep model-visible tool context concise and easy to inspect.
- Keep file tool names and arguments plain, predictable, and friendly to smaller local models.
- Keep file path confinement centralized and reject traversal or symlink escapes before file IO.

### Validation
- Add focused tests for tool registry resolution, transcript rendering, and truncation policy changes.
- Add focused tests for file path confinement, chunked reading, search output, and anchored edits.
