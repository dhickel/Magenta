# Context
Saved plan chat belongs under `/plans`, not `/api/chat` sessions.

# Goal
Create plan-scoped saved chat storage that updates `TASK_TEMPLATE` drafts with typed inputs and outputs.

# In Scope
- `plan_chat_messages` persistence.
- `/api/plans/*planning-chat*` endpoints.
- `/plans` editor integration for creating/opening saved plan chat.

# Out of Scope
- Live manual-edit reconciliation by the model.

# Implementation Steps
- Add repository and service.
- Seed four backend questions.
- Render a small HTMX chat panel in the plan editor.

# Validation
- Repository and controller tests.

# Exit Criteria
- New Plan Chat creates a saved draft and plan-scoped chat without using `/api/chat`.
