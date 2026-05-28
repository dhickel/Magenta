# User Dashboard Layout Persistence

User dashboard layout persistence is stored in `avatar.sqlite` for the current alpha implementation. The user-facing dashboard surface is `/`, with the default dashboard named `Assistant`; additional dashboards are agent-agnostic user-widget containers. Dashboards do not own agents, Work Areas, or execution runtime state.

## Tables

- `user_dashboards`: dashboard records with stable ids, names, ordering/default metadata, and timestamps.
- `user_dashboard_rows`: per-dashboard row records with stable ids, row positions, collapsed state, settings JSON, and update timestamps.
- `user_dashboard_widgets`: row-scoped widget placements with stable ids, first-party `widget_key`, column position, 12-column width, enabled/collapsed state, settings JSON, and update timestamps.
- Legacy `avatar_dashboard_*` tables may remain in the datasource but are not the current user-dashboard contract.

Widget uniqueness is scoped to the dashboard model as implemented by the user-dashboard tables, not globally across all dashboards.

## Service Contract

`AvatarService` currently exposes user-dashboard row/widget operations used by the HTMX editor routes:

- list dashboard rows with widgets;
- add and move rows;
- add widgets to a row;
- move widgets left, right, up, or down;
- resize widgets to any width from `1` through `12` that still fits the row;
- remove widgets.

The repository enforces row width totals at or below 12 columns, rejects duplicate widget keys where required by the dashboard contract, and rejects width values outside `1..12`.

## Default Dashboard

The default dashboard is `Assistant`. It is seeded from the intended default widget composition and excludes Work Area browser widgets. New dashboards start empty and editable.

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

There is no current Assistant inner tab shell. Layout edit mode applies to the selected dashboard surface only. Queue, history, profile, outputs, and Work Areas are not Assistant dashboard tabs; Work Areas are exposed through agent detail.

Row-level edit controls now render as a dedicated `.avatar-row-decoration` strip above row content instead of as a lower-z-index floating control cluster. This keeps row movement and row-level actions above widget edit chrome and aligns row editing with the module decorator pattern.
