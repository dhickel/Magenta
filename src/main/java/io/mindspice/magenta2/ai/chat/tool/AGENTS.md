## Chat Tool Package

This package owns chat-scoped tool execution support.

### Responsibilities
- Resolve configured Spring AI tools for chat agents.
- Represent tool activity as Magenta-owned chat context messages.
- Keep tool output retention and truncation policy local to chat tooling.
- Own chat-approved file tools that operate inside the configured agent data root.
- Own chat-approved shell execution for explicitly allowed Linux commands inside the configured agent data root.
- Own lightweight chat planning tools that mutate Magenta-owned plan state through services.

### Change guidance
- Do not add separate durable tool-result storage unless a concrete workflow requires it.
- Avoid replay, approval, or orchestration behavior without an explicit user-facing tool use case.
- Keep model-visible tool context concise and easy to inspect.
- Keep file tool names and arguments plain, predictable, and friendly to smaller local models.
- Prefer `file_append` for accumulating notes, outlines, reports, or logs; use `file_write` only when writing the complete desired file content.
- Keep file path confinement centralized and reject traversal or symlink escapes before file IO.
- Keep shell command execution structured; do not accept raw shell command strings.
- Keep planning tools narrow; they should save or inspect plan state, not orchestrate execution.

### Validation
- Add focused tests for tool registry resolution, transcript rendering, and truncation policy changes.
- Add focused tests for file path confinement, chunked reading, search output, and anchored edits.
- Add focused tests for shell command allowlists, working-directory confinement, timeout handling, and output truncation.
