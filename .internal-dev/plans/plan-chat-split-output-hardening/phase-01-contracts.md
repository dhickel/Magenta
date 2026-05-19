# Context
Magenta planning currently mixes anonymous chat plans with saved reusable plan/task definitions.

# Goal
Document and enforce the split between anonymous `/chat` planning and durable `/plans` saved plan chat.

# In Scope
- Package guide updates.
- Anonymous planning contract changes.
- Saved plan chat persistence and endpoint contract.

# Out of Scope
- Artifact-first deliverable replacement.
- Full workflow artifact-reference outputs.

# Implementation Steps
- Update package guides for chat plan and web API responsibilities.
- Add saved plan chat repository/service/endpoints.
- Keep anonymous plans from saving to task templates.

# Validation
- Compile and focused tests for planning, chat controller, plan repository, and runtime settings.

# Exit Criteria
- Anonymous and saved planning have separate contracts in code and docs.
