# Date

2026-05-29

## Change Summary

Repaired the Agents operational UI shell and selector rows, restored the Assistant dashboard chat resize shell hook, and converted dashboard selector/edit navigation to HTMX fragment swaps.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`: added an Agents-specific shell, compact HTMX selector rows, selected-row preservation, and standalone detail layout inside the selector/detail browser.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`: added the `/dashboards/{dashboardId}/_page` fragment route for in-place dashboard switching.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`: restored the `data-avatar-shell` root hook used by `avatar-shell.js` and wired dashboard selector/edit links to `#dashboard-home` HTMX swaps.
- `src/main/java/io/mindspice/magenta2/api/web/AppNavigation.java`: bumped the orchestration CSS cache version for selector styling.
- `src/main/resources/static/css/orchestration.css`: tightened agent selector row styling and count chips, then added a framed Agents browser layout with a clear selector/detail divider.
- `src/main/resources/static/js/orchestration/agents.js`: kept JavaScript narrow to selected-row and tab active-state affordances.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`: covered Agents shell, selector rows, selected state, and workspace failure status mapping.
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`: covered the dashboard resize shell hook.
- `docs/end-user/agents.md`, `docs/end-user/avatar-dashboard.md`, `docs/technical/frontend-htmx.md`, `docs/technical/avatar-dashboard-fragments.md`: documented the repaired behavior.
- `.internal-dev/specifications/web.md`, `.internal-dev/knowledge/avatar-shell-resizable-rail-geometry.md`, `.internal-dev/knowledge/agent-selector-shell-and-htmx.md`, `.internal-dev/knowledge/dashboard-fragment-navigation.md`: updated living contracts and reusable implementation notes.
- `artifacts/agents-selector-chat-resize/validation-summary.json`: recorded local validation evidence and remaining browser-validation handoff.

## Behavioral Impact

The Agents pages no longer render Manage side navigation or Manage-specific shell chrome. Agent selector rows now show only display name, one status chip, queue count, and inbox count, while lifecycle actions remain in the detail manage tab. The selector/detail area now has clearer pane separation. The Assistant dashboard home again exposes the shell hook required for desktop chat rail width and panel height resizing, and dashboard selector/edit navigation swaps the dashboard component without reloading the full shell.

## Specification Impact

Updated `web.md` with the Agents shell/selector contract and the Assistant dashboard fragment-navigation contract. Knowledge now calls out the required resize hook and dashboard fragment swap pattern.

## Risks

No known functional risks after focused controller tests and Playwright validation. Browser validation used the MCP-allowed isolated port available in the local environment.

## Follow-up Items

- Consider converting dashboard creation to the same `#dashboard-home` fragment response instead of targeting `body` after create.
