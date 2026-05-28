# Assistant Dashboard Refactor Specification Lock

## Classification

Medium, single-domain web/SimplyPages/UI refactor with one implementation phase.

This stays one phase because the work is tightly coupled across the same web surface: dashboard persistence/read models, shell routes, SimplyPages fragments, navigation, Work Area relocation, docs, and tests. This is greenfield alpha work, so there is no redirect, deprecation, or legacy compatibility burden.

## Implementation Model Lock

Implementation worker model: `gpt-5.5-high`.

If `gpt-5.5-high` cannot be selected for the implementation worker, stop and record `TOOLING_CONSTRAINT`; do not substitute another implementation model without user approval.

## Acceptance Criteria

- `/` renders the user dashboard home, not the old card portal.
- Dashboards are an agent-agnostic abstraction for user widgets; they are not agent shells, agent profiles, Work Area owners, or runtime execution containers.
- The dashboard selector row appears at the top of `/`, has `Assistant` selected by default, and ends with a clear `+` dashboard create control.
- Creating a dashboard opens a modal, accepts a name, creates an empty configurable dashboard, and selects or clearly exposes it after creation.
- `Assistant` preserves the useful current Avatar dashboard layout/editing feel and embedded chat rail.
- The old inner Avatar tab strip is removed: no Dashboard, Queue, History, Outputs, Work Areas, or similar Avatar shell tabs remain inside the Assistant dashboard.
- Work Areas/browser capability remains accessible from agent dashboard/detail tabs and is not exposed inside the Assistant dashboard.
- Top navigation order is exactly `Home`, `Chat`, `Agents`, `Manage`.
- The operational page formerly labeled `Dashboard` is renamed `Manage`.
- Manage side navigation has `System` above `Orchestration`; the old side-nav `Dashboard` item moves/renames to `System`, and the `Communication` section is removed if it would only contain `Inbox` and `Agents`.
- No user-facing `Avatar` label remains.
- Existing dense operational styling remains aligned with `/dashboard`, `/agents`, and current Avatar dashboard visual language: compact panels, thin blue-gray borders, small radii, clear chips, bounded modals, and HTMX-first interactions.

## Negative Criteria

- Do not rebuild the dashboard editor from scratch.
- Do not replace SimplyPages row/column/editing patterns with raw HTML strings or a JS-rendered dashboard app.
- Do not keep the Avatar shell as the main user experience under a different heading.
- Do not hide Work Areas by removing the browser capability; relocate it.
- Do not add auth, permissions, marketplace/widgets, runtime/workspace ownership changes, or a new agent execution model.
- Do not leave duplicate shell chrome or HTMX full-page nav swaps.
- Do not leave normal user-facing labels, buttons, titles, docs, or nav entries named `Avatar`.
- Do not add redirect/deprecation shims for the old Avatar UI; remove or replace the maintained route surface directly.

## Route Decisions

- `/` becomes the dashboard selector home and default Assistant dashboard surface.
- Remove `/avatar` as a maintained user-facing route; no redirect or deprecation support is required for greenfield alpha.
- Remove or replace old `/avatar/_...` fragment route usage; new rendered UI must not call those routes.
- Prefer new general dashboard fragment routes under `/dashboards/...` or `/_dashboards/...` for selector, create modal, layout edits, and dashboard fragments.
- The old operational `/dashboard` page becomes `Manage`; use `/manage` as the primary route and update tests/docs/routes rather than preserving redirect compatibility.

## Persistence Decision

Multiple dashboards require persisted dashboard records. Dashboards are user-owned widget layouts and should stay agent-agnostic. The current singleton Avatar layout tables are not sufficient because `avatar_dashboard_widgets` has a global `unique(widget_key)` and the row/widget records do not carry a dashboard id.

Use an additive persistence design:

- Add general dashboard tables to the existing application-owned dashboard persistence context, preferably the current `avatar.sqlite` datasource for this focused refactor unless implementation discovers a stronger local convention.
- Seed a default dashboard named `Assistant`.
- Seed `Assistant` from the current useful default dashboard composition, excluding Work Areas. Do not require a migration/copy path from old Avatar rows/widgets.
- Old Avatar organizer/profile/todo/note/calendar data is not part of the new dashboard contract.
- It is acceptable to replace/remove obsolete Avatar dashboard route/schema assumptions where needed because this is greenfield alpha; avoid unrelated broad data cleanup.

## Work Areas Decision

Work Areas remain runtime-owned Magenta state, not dashboard-owned state. Move browser access into the agent detail/dashboard tab interface, with routes scoped by agent id and guarded by Work Area owner checks. The new Assistant dashboard must not present Work Areas as dashboard widgets or tabs.

## Closeout Expectations

- Update `.internal-dev/specifications/web.md`, `.internal-dev/specifications/simplypages.md`, and `.internal-dev/specifications/api.md` for renamed routes/fragments and Work Area placement.
- Update `.internal-dev/specifications/decisions.md` if implementation chooses a durable persistence or route ownership tradeoff beyond this plan.
- Update relevant end-user and technical docs, especially Avatar dashboard docs renamed or replaced by user dashboard docs, Work Area docs, and navigation docs.
- Add a changelog under `.internal-dev/changelogs/`.
- Archive this plan only after implementation, validation, docs/spec closeout, and final approval.
