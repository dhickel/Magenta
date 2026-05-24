# Date

2026-05-24

# Change Summary

Refactored `/avatar` into a compact tabbed operational shell that follows the agent-dashboard rhythm while preserving a persistent right-side Avatar chat rail. The pass removes the bulky top toolbar, limits layout editing to the dashboard tab, fixes row decorator layering above module chrome, adds desktop rail resizing with browser-local persistence, and introduces baseline top-level Avatar tabs for queue, history, profile, outputs, and Work Areas.

# Files

- `.internal-dev/bugs/avatar-dashboard-edit-empty-row-density/report.md`
- `.internal-dev/changelogs/2026-05-24-avatar-shell-baseline-refactor.md`
- `.internal-dev/focus/architecture-focus.md`
- `.internal-dev/focus/decisions.md`
- `.internal-dev/focus/unfinished-work.md`
- `.internal-dev/plans/avatar-shell-baseline-refactor/README.md`
- `.internal-dev/plans/avatar-shell-baseline-refactor/implementation-plan.md`
- `.internal-dev/plans/avatar-shell-baseline-refactor/orchestration.md`
- `.internal-dev/plans/avatar-shell-baseline-refactor/shared-notes.md`
- `.internal-dev/plans/avatar-shell-baseline-refactor/validation-red-team.md`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/avatar-dashboard-layout-persistence.md`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

# Behavioral Impact

- `/avatar` now renders as a tabbed shell with `Dashboard`, `Queue`, `History`, `Profile`, `Outputs`, and `Work Areas`.
- The right chat rail persists while tab content swaps in place, and the active tab now stays visually in sync during HTMX tab changes.
- Desktop users can drag the divider to resize the Avatar chat rail, and the chosen width persists across reloads in browser-local state.
- `Organizer` and manual `Refresh Widgets` are removed from the top shell.
- Only the `Dashboard` tab honors `edit=true`; non-dashboard tabs normalize back to read-only shell views.
- Row-level edit controls now render above widget edit chrome through the dedicated row decoration strip.

# Risks

- The baseline `History` tab is intentionally a recent-work fallback and does not yet expose the fuller Avatar user-surface/chat-history view.
- Avatar dashboard edit mode can still become visually tall when persisted layout data contains many empty rows; this is tracked as an open bug instead of being silently normalized away.

# Follow-up Items

- Design Avatar auto-refresh after removal of the manual shell refresh control.
- Expand the `History` tab beyond the recent-work fallback without creating a new persistence model.
- Reduce edit-mode density when users accumulate many empty dashboard rows.

# Validation

- `mvn -Dtest=AvatarDashboardControllerTest test`: passed.
- `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-avatar-shell-validation.sqlite'`: startup succeeded.
- Delegated Playwright validation on `http://localhost:18080` with `gpt-5.3-codex` verified:
  - desktop `/avatar` tab switching and persistent right rail
  - mobile stacked `/avatar`
  - dashboard edit-mode row decorator layering
  - desktop rail resize plus reload persistence after the final fix
