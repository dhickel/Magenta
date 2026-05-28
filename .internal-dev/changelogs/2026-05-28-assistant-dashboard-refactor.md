## Date

2026-05-28

## Change Summary

Refactored the former Avatar dashboard surface into an Assistant dashboard home at `/`, with agent-agnostic dashboard persistence, dashboard creation, `/manage` navigation, and Work Area access moved to agent detail.

## Files

- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/UserDashboard.java`
- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/AppNavigation.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/api.md`
- `docs/end-user/avatar-dashboard.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/workspaces-tools-outputs.md`

## Behavioral Impact

`/` now opens the Assistant dashboard selector and selected dashboard content. `/manage` is the operational console route. Top navigation order is `Home`, `Chat`, `Agents`, `Manage`. Work Areas are no longer Assistant dashboard widgets and are exposed from agent detail.

## Specification Impact

Updated web, SimplyPages, and API specifications for Assistant dashboards, `/manage`, dashboard fragments, and agent-owned Work Area placement.

## Risks

Final validation includes delegated Playwright evidence reconciled under `artifacts/assistant-dashboard-refactor/validation-summary.json`. Some retained internal class and asset names still use `Avatar` as compatibility naming while visible labels and maintained routes moved to Assistant/dashboard terminology.

## Follow-up Items

- Consider a later internal rename of Avatar-named dashboard classes/assets if the compatibility naming becomes confusing.
