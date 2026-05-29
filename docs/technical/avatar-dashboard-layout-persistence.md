# User Dashboard Layout Persistence

User dashboard layout persistence is stored in `avatar.sqlite` for the current alpha implementation. The user-facing dashboard surface is `/`, with the default dashboard named `Assistant`; additional dashboards are agent-agnostic user-widget containers. Dashboards do not own agents, Work Areas, or execution runtime state.

## Tables

- `user_dashboards`: dashboard records with stable ids, names, ordering/default metadata, and timestamps.
- `user_dashboard_rows`: per-dashboard row records with stable ids, row positions, collapsed state, settings JSON, and update timestamps.
- `user_dashboard_widgets`: row-scoped widget instances with stable ids, compatibility `widget_key`, registry-backed `widget_type`, optional instance label, column position, 12-column width, enabled/collapsed state, per-instance settings JSON, optional `single_instance_key`, and timestamps.
- Legacy `avatar_dashboard_*` tables may remain in the datasource but are not the current user-dashboard contract.

Widget uniqueness is no longer type-wide for every widget. Multi-instance registry types can appear more than once on a dashboard. Single-instance registry types set `single_instance_key = widget_type`, and SQLite enforces `unique(dashboard_id, single_instance_key)` while allowing multi-instance rows to keep that sentinel null.

## Service Contract

`AvatarService` currently exposes user-dashboard row/widget operations used by the HTMX editor routes:

- list dashboard rows with widgets;
- add and move rows;
- add widgets to a row;
- validate widget type, supported width, row capacity, and single-instance policy through the widget registry;
- save per-instance widget settings with deterministic defaults and validation;
- move widgets left, right, up, or down;
- resize widgets to any width from `1` through `12` that still fits the row;
- remove widgets.

The repository enforces row width totals at or below 12 columns, persists registry-derived settings defaults, enforces the single-instance sentinel constraint, and rejects width values outside `1..12`.

## Default Dashboard

The default dashboard is `Assistant`. It is seeded from the intended default widget composition and excludes Work Area browser widgets. Phase 02 default widgets are Today Planner, Calendar/Schedule, Tasks/Routines, Notes, System, Alerts, and Recent Work. New dashboards start empty and editable.

## Editor UI Contract

The in-place dashboard layout editor uses per-action HTMX requests and OOB grid refreshes rather than a single flat save form. Current user-dashboard routes are under `/dashboards/{dashboardId}/_layout/...`, including:

- `POST /dashboards/{dashboardId}/_layout/rows`
- `POST /dashboards/{dashboardId}/_layout/rows/{rowId}/move?direction=up|down`
- `GET /dashboards/{dashboardId}/_layout/rows/{rowId}/catalog`
- `POST /dashboards/{dashboardId}/_layout/rows/{rowId}/widgets`
- `POST /dashboards/{dashboardId}/_layout/widgets/{widgetId}/move?direction=left|right|up|down`
- `GET /dashboards/{dashboardId}/_layout/widgets/{widgetId}/width-picker`
- `PUT /dashboards/{dashboardId}/_layout/widgets/{widgetId}/width`
- `DELETE /dashboards/{dashboardId}/_layout/widgets/{widgetId}`

The editor uses SimplyPages row/column layout primitives for the 12-column placement model and keeps mutations scoped to the shared edit overlay/popover surface with the dashboard widget grid refreshed out of band.

## Widget Instance Routes

Widget summaries, detail surfaces, and settings surfaces use stable widget instance ids:

- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}`
- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/detail`
- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings`
- `PUT /dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings`

Compatibility routes under `/_dashboards/_widgets/{widgetKey}` still resolve the first matching instance or a default shell for older widget actions.

There is no current Assistant inner tab shell. Layout edit mode applies to the selected dashboard surface only. Queue, history, profile, outputs, and Work Areas are not Assistant dashboard tabs; Work Areas are exposed through agent detail.

Row-level edit controls now render as a dedicated `.avatar-row-decoration` strip above row content instead of as a lower-z-index floating control cluster. This keeps row movement and row-level actions above widget edit chrome and aligns row editing with the module decorator pattern.
