# Date

2026-05-16

# Change Summary

- Repaired the agent-detail chat disclosure so the styled accordion summary is the single open/close control.
- Removed the dead dashboard `Open Agent Chat` action that still targeted the deleted chat tab route.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/orchestration/agent-chat.js`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/test-fixtures/orchestration-driver/live-validation.js`

# Behavioral Impact

- Agent detail chat now opens and closes from the top styled label without a duplicate nested collapse button.
- The dashboard no longer exposes a redundant chat button that pointed at a non-existent fragment route.

# Risks

- None observed after focused browser validation on `/agents/{id}` confirmed the accordion opens and closes from the summary label.

# Follow-up Items

- None.
