# Context
Anonymous chat planning is for ad hoc `/chat` sessions.

# Goal
Seed anonymous planning with backend questions and keep it non-durable as a saved task definition.

# In Scope
- Three queued opening questions.
- Prompt and tool restrictions that avoid typed inputs/outputs.
- Direct anonymous execution actions.

# Out of Scope
- Submit anonymous plans to agents.
- Save anonymous plans to `/plans`.

# Implementation Steps
- Queue goal, guidance, and deliverables questions from `PlanService.beginPlan`.
- Return queued state without model use from `ChatService.beginPlan`.
- Re-enable approved anonymous execution and clean execution.

# Validation
- Unit/controller coverage for queued questions and approval actions.

# Exit Criteria
- `/chat` anonymous plans remain session-local and executable only from the chat approval panel.
