# Phase 01 Worker Directive: Assistant Dashboard Refactor

## Objective

Using implementation model `gpt-5.5-high`, consolidate the current Avatar dashboard abstraction into a general user-configurable dashboard primitive. Dashboards are agent-agnostic user-widget containers. The default dashboard is named `Assistant`, lives on `/`, preserves useful Avatar dashboard layout/editing/chat behavior, removes Avatar-specific shell/navigation concepts, relocates Work Areas to agent detail, and updates navigation to `Home`, `Chat`, `Agents`, `Manage`.

## Editable Targets

Primary:

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AppNavigation.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/main/resources/static/js/avatar-chat.js`
- `src/main/resources/static/js/avatar-layout-edit.js`
- `src/main/resources/static/js/avatar-workarea-editor.js`
- Avatar/dashboard persistence classes under `src/main/java/io/mindspice/magenta2/avatar`
- `src/main/resources/avatar-schema.sql`

Tests:

- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- Add focused dashboard persistence/service tests if new service/repository classes are introduced.

Docs/specs:

- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/decisions.md` if final route/persistence tradeoffs differ from the plan
- `docs/end-user/avatar-dashboard.md` or renamed replacement
- `docs/end-user/projects-and-workspaces.md`
- `docs/technical/avatar-dashboard-fragments.md` or renamed replacement
- `docs/technical/workspaces-tools-outputs.md`
- `.internal-dev/changelogs/<date>-assistant-dashboard-refactor.md`

## Forbidden Scope

- Do not add auth, permissions, multi-user policy, marketplace widgets, new runtime/workspace ownership semantics, queues, model clients, or broad schema cleanup.
- Do not add redirect/deprecation support for old Avatar or Dashboard routes; this is greenfield alpha.
- Do not rebuild the dashboard editor from scratch.
- Do not preserve the Avatar shell by only renaming the banner.
- Do not move Work Area service ownership into dashboard persistence.
- Do not use raw HTML strings for new reusable dashboard composition unless matching existing unavoidable fragment style.

## Supporting Docs To Read Before Editing

- `.internal-dev/plans/assistant-dashboard-refactor/00-specification-lock.md`
- `.internal-dev/plans/assistant-dashboard-refactor/01-current-state-analysis.md`
- `.internal-dev/plans/assistant-dashboard-refactor/02-target-design.md`
- `.internal-dev/plans/assistant-dashboard-refactor/shared/implementation-notes.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `.internal-dev/knowledge/shell-navigation-htmx-vs-full-page.md`
- `.internal-dev/knowledge/workarea-operational-ui-consistency.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`

## Implementation Steps

1. Run `git status --short`; do not revert existing user changes.
2. Create or adapt general dashboard persistence:
   - Add dashboard records and per-dashboard rows/widgets.
   - Keep dashboard records agent-agnostic; do not bind dashboards to agents or Work Areas.
   - Seed `Assistant`.
   - Seed Assistant from the intended default widget composition, excluding Work Areas.
   - Ensure new dashboards can be created empty.
3. Refactor dashboard rendering:
   - Make `/` render the dashboard selector and selected dashboard.
   - Preserve compact chat rail and row/widget editing for Assistant.
   - Remove Avatar banner/title and inner tab strip from the main dashboard experience.
   - Remove Work Areas from Assistant dashboard widgets/tabs.
4. Add create-dashboard flow:
   - Selector trailing `+` opens bounded modal.
   - Submit creates dashboard by name with validation for blank/duplicate names.
   - Response refreshes selector/dashboard/modal via HTMX/OOB patterns.
5. Route cleanup:
   - Remove `/avatar` as a maintained user-facing route; no redirect/deprecation support is needed.
   - Move new UI calls away from `/avatar/_...`.
   - Make `/manage` the primary operational dashboard route and update old `/dashboard` callers/tests/docs.
6. Navigation:
   - Top nav order exactly `Home`, `Chat`, `Agents`, `Manage`.
   - Move `Agents` to top nav.
   - Rename/move side-nav `Dashboard` to `System` above `Orchestration`.
   - Remove `Communication` section if it would only contain obsolete Inbox/Agents grouping.
7. Work Area relocation:
   - Add agent-detail Work Area browser access.
   - Generalize `WorkAreaExplorerFragments` route/id prefixing or otherwise share the renderer without duplicating browser logic.
   - Scope/guard Work Areas by agent owner.
8. Update tests for routes, navigation, dashboard creation, no Avatar labels, Work Area relocation, and persistence seed behavior.
9. Update docs/specs/changelog and bump static asset query versions for changed CSS/JS.
10. Produce validation evidence at `artifacts/assistant-dashboard-refactor/validation-summary.json`.

## Experience Contract

The first screen is the usable dashboard home, not a landing page. It should show the selector row and selected Assistant dashboard content in the first viewport.

Layout:

- Selector row at top, compact and horizontally scannable on desktop.
- On mobile, selector wraps or stacks without horizontal overflow.
- Assistant dashboard below with chat rail and dashboard grid; no inner tab strip.
- Empty dashboards show a compact operational empty state with edit/add affordances.

Interaction:

- `+` opens create modal.
- Create form validates name and updates the selector/dashboard via HTMX.
- Dashboard edit controls remain in-place and compact.
- Work Area browser opens inside agent detail, not Assistant.

Visual failures:

- Hero/marketing portal.
- Big card grid replacing the dashboard.
- Duplicate top banner/nav.
- Hidden or tiny unlabelled dashboard create target.
- Any normal label saying `Avatar`.
- Work Areas still appearing as an Assistant tab/widget.

## Acceptance Criteria

Meet every criterion in `00-specification-lock.md`.

## Negative Checks

- Search rendered HTML/tests/docs for user-facing `Avatar`.
- Verify no new rendered UI calls `/avatar/_...`.
- Verify `/` is not the old portal card grid.
- Verify Work Area browser capability is not lost.
- Verify top nav order exactly.
- Verify there are no redirect/deprecation expectations for `/avatar` or `/dashboard`.
- Verify CSS/JS cache query versions changed when assets changed.

## Validation Commands

Run the commands listed in `shared/validation-matrix.md`. At minimum:

- `mvn test -Dtest=AvatarDashboardControllerTest,OrchestrationControllerTest`
- New focused dashboard persistence/service tests if added
- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- `node --check` for changed JavaScript files

Then hand off to validator and Playwright subagent per `shared/validation-matrix.md`.

## Stop Conditions

Stop and return to planning if:

- additive dashboard tables cannot support the refactor without destructive migration;
- Work Area ownership semantics require changing runtime/workspace contracts;
- `gpt-5.5-high` cannot be selected for implementation;
- browser validation cannot be delegated and no user-approved blocker exists;
- dashboard persistence cannot remain agent-agnostic.

## Do Not Close Unless

- `/` dashboard selector and Assistant are implemented and tested.
- Create-dashboard modal works and is tested.
- Inner Avatar tabs are gone.
- Work Areas are available from agent detail and tested.
- Navigation labels/order match the spec.
- Docs/spec/changelog closeout is complete.
- Spring startup smoke is run or blocker is user-approved.
- Playwright subagent evidence and visual critique are reconciled by validation.
