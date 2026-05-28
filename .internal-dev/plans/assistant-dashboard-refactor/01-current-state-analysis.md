# Assistant Dashboard Refactor Current-State Analysis

## Verified Files Read

- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `.internal-dev/knowledge/shell-navigation-htmx-vs-full-page.md`
- `.internal-dev/knowledge/dashboard-api-contract.md`
- `.internal-dev/knowledge/agent-detail-workspace-health-pattern.md`
- `.internal-dev/knowledge/workarea-operational-ui-consistency.md`
- `.internal-dev/knowledge/workspace-api-list-and-agent-tab-operational-pattern.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- Current web/controller/model targets listed in the task brief.

## Route And Navigation State

- `AppNavigation.primaryTopNav()` currently renders `Home`, `Dashboard`, `Chat`, in that order, with full-document HTMX navigation disabled.
- `AppNavigation.operationalSideNav()` currently has sections `Orchestration`, `Communication`, and `Tools`. `Dashboard` is inside `Orchestration`; `Agents` is inside `Communication`.
- `FrontendController.home()` currently renders a portal card grid linking to Chat, Avatar, Dashboard, Plans & Tasks, Workflows, Jobs, and Inbox.
- `AvatarDashboardController` owns `/avatar` and many `/avatar/_...` HTMX fragments for tabs, layout editing, organizer widgets, outputs, and Work Area browser/editor routes.
- `OrchestrationController` owns `/dashboard`, `/agents`, `/agents/{agentId}`, and agent detail tab fragments including dashboard, queue, inbox, jobs, schedules, reactions, workspace, outputs, exec, history, and submit.

## Avatar Dashboard State

- The useful dashboard UI is concentrated in `AvatarDashboardComponents`: shell grid, chat rail, widget grid, row/widget edit controls, detail modals, and Work Area widget/tab rendering.
- The current Avatar page includes an inner shell tab strip and tab-specific panels. This conflicts with the new requirement to make Assistant a dashboard surface rather than a mini-application shell.
- Useful pieces to preserve are the compact chat rail, row/column widget layout, in-place edit controls, OOB layout refreshes, compact modals, and operational visual density.
- Pieces to remove from the Assistant dashboard are the Avatar title/labels, inner tabs, Work Areas widget/tab, and routes that imply Dashboard/Queue/History/Outputs/Work Areas as Avatar-owned shell concepts.

## Persistence State

- `AvatarDataConfiguration` creates a separate `avatar.sqlite` datasource.
- `AvatarRepository.PROFILE_ID` is singleton `default`; `defaultProfile()` uses display name `Avatar`.
- Existing dashboard tables are singleton-oriented:
  - `avatar_dashboard_layout` stores legacy widget layout keyed by `widget_id`.
  - `avatar_dashboard_rows` has no dashboard id.
  - `avatar_dashboard_widgets` has no dashboard id and enforces `unique(widget_key)`.
- The existing schema can seed rows from the legacy widget layout, but it cannot safely represent multiple dashboards with reusable widget keys.

## Work Area State

- Avatar Work Area routes are guarded by `requireAvatarExplorerService(...)`, which only allows Work Areas owned by the reserved `avatar` agent.
- `WorkAreaExplorerFragments` hardcodes `/avatar/_work-areas/...` routes and `avatar-workarea-*` ids/classes.
- Agent detail already has a `workspace` tab and `outputs` tab, but the workspace tab is an operational metadata view, not the full Work Area browser/editor.
- Knowledge and package guidance treat Work Areas as runtime-owned metadata under Magenta workspace services, which fits agent-detail relocation.

## Schema And Route Risk

The only concrete risk that could have forced multi-phase work is persistence. Because the current tables are singleton-oriented and globally unique per widget key, a direct in-place multi-dashboard migration would be risky. The plan avoids that by requiring general dashboard records/tables for an agent-agnostic user-widget dashboard primitive and by seeding `Assistant` from the current useful default widget composition.

Route compatibility is intentionally not a requirement. This is greenfield alpha, so the implementation should update routes, tests, docs, and rendered links directly rather than preserving `/avatar` or `/dashboard` redirects/deprecation paths.

## Visual-System Lock

Reference surfaces:

- Current Avatar dashboard normal/edit modes.
- `/dashboard` operational console.
- `/agents` list/detail pages.
- SimplyPages editing demo for row/column editing behavior.

Required visual pattern:

- Dense operational tooling.
- Compact blue-gray bordered panels.
- Small-radius controls.
- Icon or compact `+` create affordance for dashboard creation.
- Row/list presentation for dashboard selectors and Work Area browser lists.
- Bounded scrollable modals.
- No nested cards for list rows.
- Fixed or bounded columns with wrapping/truncation instead of horizontal page growth.
- HTMX-first CRUD and fragment refreshes.

Failure examples:

- Hero-style dashboard homepage.
- Large marketing cards for dashboard choices.
- Reintroduced Avatar tab strip inside Assistant.
- Work Area browser still reachable only from Assistant.
- Full-page HTMX nav producing duplicated top banner/nav.
- Selector row or modal overflowing on mobile.
- Hidden `+` control or text-only click target with weak affordance.
