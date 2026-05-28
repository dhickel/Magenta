# Assistant Dashboard Refactor Implementation Notes

## Scope Discipline

This is one implementation phase. Keep the worker focused on web/dashboard route/controller/component refactor, agent-agnostic dashboard persistence, Work Area UI relocation, tests, and docs/spec closeout.

Do not branch into runtime ownership, auth, marketplace widgets, or a broad data migration.

## Dirty Worktree Note

At planning time the worktree already had modified Avatar/docs/test files. The implementation worker must run `git status --short` before editing and must not revert user changes. If existing changes overlap with this plan, read them and work with them.

## Suggested Code Shape

- Prefer new general dashboard records/service/repository names instead of expanding `AvatarService` with more public Avatar-named dashboard APIs.
- Existing `AvatarDashboardComponents` can be renamed or split so reusable dashboard composition is not user-facing Avatar code.
- Keep `WorkAreaExplorerFragments` shared by parameterizing route/id prefixes instead of copying the whole browser.
- Keep controllers thin: controller maps request/response and delegates dashboard persistence/layout operations to a service.
- Do not add route compatibility/deprecation shims for `/avatar` or `/dashboard`; this is greenfield alpha, so update route ownership and tests/docs directly.
- Bump static asset query versions when CSS/JS files change.

## SimplyPages And HTMX Requirements

- Use SimplyPages components, `Row`, `Column`, and established fragment/OOB patterns.
- Use HTMX for dashboard create, selector refresh, layout row/widget mutations, tab/panel swaps, and Work Area browser CRUD.
- JavaScript is acceptable only for existing narrow behavior: chat streaming/client interaction, local shell geometry, editor dirty/preview behavior, or interactions where HTMX is materially awkward.
- Full-document top-nav links must keep HTMX navigation disabled.

## Data Compatibility Notes

- General dashboard tables should model dashboards as agent-agnostic user-widget containers.
- Seed Assistant from the intended default widget composition, excluding Work Areas; do not require old Avatar row/widget migration logic.
- Old Avatar organizer/todo/note/calendar tables are outside the new dashboard contract.
- Work Areas should not appear in the Assistant dashboard. Expose them through the corresponding agent detail route.

## Tests To Expect

- Update or replace `AvatarDashboardControllerTest` with general dashboard/home tests.
- Update `OrchestrationControllerTest` for Manage nav, Agents top nav, side-nav section changes, and agent Work Area browser placement.
- Add focused repository/service tests for dashboard seed/create/layout behavior if new persistence classes are introduced.
- Keep existing Work Area path-safety tests and route them through the new agent-detail Work Area routes.
