## Chat Plan Package

This package owns chat-scoped plan mode and saved execution plan state.

### Responsibilities
- Persist the current conversation planning mode and saved execution plan.
- Persist lightweight acceptance criteria and execution evidence for saved plans.
- Provide the standalone plan-mode system prompt and compact execution-mode runtime instructions.
- Keep plan lifecycle operations explicit and command-driven.
- Keep plan state separate from transient chat history and generic todo tracking.

### Change guidance
- Do not add general orchestration, scheduling, or subagent coordination here.
- Keep plan schemas small and tied to the current chat workflow.
- Keep the database as the source of truth; model tool calls request state changes but do not own state.
- In PLAN mode, keep the model-visible prompt focused on planning-only behavior. Shell access is allowed for planning research, including local database/schema inspection.
- Prefer compact execution prompt state over replaying long planning transcripts.
- Treat execution as reviewable until evidence has been surfaced; do not equate a returned model turn with verified completion.

### Validation
- Add repository tests for schema and persistence changes.
- Add service tests for mode transitions, prompt injection, and context trimming.
