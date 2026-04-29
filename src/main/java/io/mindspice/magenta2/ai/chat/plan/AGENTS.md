## Chat Plan Package

This package owns chat-scoped plan mode and saved execution plan state.

### Responsibilities
- Persist the current conversation planning mode and saved execution plan.
- Provide compact model-visible runtime instructions for plan and execution modes.
- Keep plan lifecycle operations explicit and command-driven.
- Keep plan state separate from transient chat history and generic todo tracking.

### Change guidance
- Do not add general orchestration, scheduling, or subagent coordination here.
- Keep plan schemas small and tied to the current chat workflow.
- Keep the database as the source of truth; model tool calls request state changes but do not own state.
- Prefer compact prompt state over replaying long planning transcripts.

### Validation
- Add repository tests for schema and persistence changes.
- Add service tests for mode transitions, prompt injection, and context trimming.
