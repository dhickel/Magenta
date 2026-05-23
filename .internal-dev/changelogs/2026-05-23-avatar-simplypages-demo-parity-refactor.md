# Date

2026-05-23

# Change Summary

Refactored Avatar dashboard edit mode to follow the SimplyPages HTMX editing demo pattern. The rendered dashboard is now the primary layout editor, with compact top-corner widget decorators, centered add-widget affordances, quiet insert-row separators, and modal/detail flows reserved for widget-specific iteration or catalog selection.

# Files

- `AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/plans/avatar-simplypages-demo-parity-refactor/README.md`
- `.codex-orchestration/avatar-simplypages-demo-parity/notes.md`

# Behavioral Impact

- `/avatar?edit=true` no longer renders the large row/widget decoration panels that made the dashboard look like a separate editor form.
- Widget edit controls now render as compact top-corner decorators with detail, refresh, move, width-cycle, and remove actions.
- Widget width can be cycled through the supported 12-column presets with `POST /avatar/_layout/widgets/{widgetId}/width-cycle`.
- Row controls render as small row-level micro controls while add-widget and insert-row controls follow the SimplyPages demo's in-place insertion affordances.
- `POST /avatar/_layout/rows/{rowId}/insert-after` inserts a row directly below the selected row and opens the add-widget catalog for the new row.
- `GET /avatar/_edit` no longer opens the legacy layout-list modal; layout editing is the live dashboard.
- Agent guidance and knowledge now require visual Playwright critique against the SimplyPages editing demo for Avatar layout work.

# Risks

- Existing local/live data may still contain empty layout rows or test todos from earlier validation. The refactor does not silently delete persisted rows.
- The width-cycle action trades explicit width selection for a compact top-corner control. If users need exact one-click width selection later, it should be added through a tasteful popover or module detail flow rather than by exposing a full select in every widget card.

# Follow-up Items

- A high-level design review is being run as a separate requested closeout step and may produce additional styling recommendations for Avatar and broader Magenta surfaces.
