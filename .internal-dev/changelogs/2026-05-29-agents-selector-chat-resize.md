# Date

2026-05-29

## Change Summary

Repaired the Agents operational UI shell and selector rows, and restored the Assistant dashboard chat resize shell hook.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`: added an Agents-specific shell, compact HTMX selector rows, selected-row preservation, and standalone detail layout inside the selector/detail browser.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`: restored the `data-avatar-shell` root hook used by `avatar-shell.js`.
- `src/main/java/io/mindspice/magenta2/api/web/AppNavigation.java`: bumped the orchestration CSS cache version for selector styling.
- `src/main/resources/static/css/orchestration.css`: tightened agent selector row styling and count chips.
- `src/main/resources/static/js/orchestration/agents.js`: kept JavaScript narrow to selected-row and tab active-state affordances.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`: covered Agents shell, selector rows, selected state, and workspace failure status mapping.
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`: covered the dashboard resize shell hook.
- `docs/end-user/agents.md`, `docs/end-user/avatar-dashboard.md`, `docs/technical/frontend-htmx.md`, `docs/technical/avatar-dashboard-fragments.md`: documented the repaired behavior.
- `.internal-dev/specifications/web.md`, `.internal-dev/knowledge/avatar-shell-resizable-rail-geometry.md`, `.internal-dev/knowledge/agent-selector-shell-and-htmx.md`: updated living contracts and reusable implementation notes.
- `artifacts/agents-selector-chat-resize/validation-summary.json`: recorded local validation evidence and remaining browser-validation handoff.

## Behavioral Impact

The Agents pages no longer render Manage side navigation or Manage-specific shell chrome. Agent selector rows now show only display name, one status chip, queue count, and inbox count, while lifecycle actions remain in the detail manage tab. The Assistant dashboard home again exposes the shell hook required for desktop chat rail width and panel height resizing.

## Specification Impact

Updated `web.md` with the Agents shell and selector contract. The Assistant dashboard contract remains unchanged, but knowledge now calls out the required resize hook.

## Risks

Focused controller tests pass, but browser geometry and visual quality still require the separate Playwright validation requested by the directive.

## Follow-up Items

- Run Playwright desktop/mobile checks for `/agents`, `/agents/{agentId}`, and `/` resize behavior.
