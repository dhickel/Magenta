# Assistant Dashboard Refactor Target Design

## Target Architecture

Create a general user dashboard primitive from the current Avatar dashboard implementation without broadening runtime semantics. The dashboard is an agent-agnostic abstraction for user widgets; it is not an agent shell, agent profile, Work Area owner, or execution context.

Recommended naming:

- User-visible default dashboard: `Assistant`.
- User-visible dashboard surface: `Home`.
- Operational old dashboard: `Manage`.
- Internal Java names should move toward `Dashboard` or `UserDashboard`, not new `Avatar` names. Reserve `Assistant` for the seeded default dashboard name, not for the primitive itself.

## Dashboard Persistence

Implement an additive model capable of multiple dashboards:

- Dashboard record: id, name, position/order, default/selected marker or default lookup, timestamps, settings JSON if useful. Do not bind dashboard records to agents.
- Dashboard rows: id, dashboard id, row position, collapsed/settings, timestamps.
- Dashboard widgets: id, dashboard id or row id, widget key, column position, column width, enabled/collapsed/settings, timestamps.
- Uniqueness should be per dashboard where needed, not global across all dashboards.

Use the existing application-owned dashboard persistence context unless implementation confirms a clearly better local convention. Do not move Work Area/runtime records into this store.

Seed behavior:

- Ensure `Assistant` exists.
- Seed Assistant with the current useful default dashboard widgets minus Work Areas.
- New user-created dashboards should start empty but editable.

## Routes And Controllers

Recommended primary routes:

- `GET /` renders dashboard home with selector and selected dashboard.
- `GET /dashboards/{dashboardId}` may render/select a specific dashboard if the worker chooses addressable dashboard URLs.
- `GET /dashboards/_selector` returns selector fragment.
- `GET /dashboards/_create` returns create-dashboard modal.
- `POST /dashboards` creates a dashboard and refreshes selector/dashboard fragments.
- `GET/POST/PUT/DELETE /dashboards/{dashboardId}/_layout/...` replaces new UI calls to `/avatar/_layout/...`.
- No `/avatar` redirect or deprecation route is required.

Operational route rename:

- `GET /manage` becomes the primary old operational dashboard route.
- Update old `/dashboard` callers/tests/docs to `/manage`; no redirect compatibility is required.
- Operational HTMX fragments should move to a Manage-owned route namespace unless implementation finds a narrow internal fragment name that is not user-facing and is cheaper to leave for this phase.

Agent Work Area relocation:

- Add a Work Areas/browser tab or dashboard section under agent detail.
- Use routes scoped under `/agents/_detail/{agentId}/work-areas/...` or a similarly explicit agent-detail route.
- Generalize `WorkAreaExplorerFragments` to accept a route prefix, target ids, and modal/list ids, or create a narrowly shared renderer wrapper. Do not duplicate the entire file browser.
- Guard access by Work Area owner/type so agent detail only exposes Work Areas owned by that agent unless a service contract explicitly supports broader user-owned Work Areas.

## UI Composition

Home should be the actual dashboard experience:

- Top selector row with dashboard names and trailing `+`.
- Main selected dashboard below, with Assistant selected by default.
- Assistant has embedded compact chat rail and configurable dashboard grid.
- Empty dashboards show an edit-friendly empty state and add-widget/add-row affordance; not a marketing empty state.
- Create modal is bounded and scroll-safe on mobile.

Remove from Assistant:

- Avatar banner/title.
- Inner Avatar tab strip.
- Work Areas widget and Work Areas tab.
- Queue/History/Outputs tab concepts that belong to agent detail or Manage.

Preserve:

- Chat rail feel.
- In-place dashboard layout editing.
- HTMX OOB swaps for layout responses.
- Compact widgets, modals, chips, and operational density.

## Documentation And Specification Impact

This refactor intentionally changes live contracts, so closeout must update:

- `.internal-dev/specifications/web.md`: Avatar entries become Assistant/user dashboard entries; top navigation and Manage route contract.
- `.internal-dev/specifications/simplypages.md`: general dashboard editing replaces Avatar-specific wording where appropriate.
- `.internal-dev/specifications/api.md`: Work Area fragment route movement and Manage/dashboard route ownership.
- End-user docs currently named for Avatar should be renamed/reframed or marked as historical only if docs policy permits.
- Technical docs for Avatar dashboard fragments and Work Area browser routes should be updated to current route names.
