# Date

2026-05-16

# Change Summary

- Loaded `agent-chat.js` on the `/agents` browser page so HTMX-swapped detail fragments can initialize chat.
- Moved visible chat markup into the server-rendered detail fragment so the accordion still reveals a usable panel before JavaScript enhancement.
- Updated validation to exercise the real list-card-to-detail flow instead of only direct `/agents/{id}` navigation.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/test-fixtures/orchestration-driver/live-validation.js`
- `.internal-dev/knowledge/agent-chat-accordion-control.md`

# Behavioral Impact

- Selecting an agent from `/agents` now receives visible chat markup immediately and then binds the live SSE behavior when the module is present.

# Risks

- None beyond the existing narrow JavaScript requirement for live SSE chat.

# Follow-up Items

- None.
