# Avatar Dashboard Layout Persistence

`/avatar` dashboard layout persistence is owned by the Avatar package and stored in `avatar.sqlite`. The browser UI now renders the row/widget model for persisted layouts and keeps the legacy `AvatarDashboardWidget` contract only as a compatibility fallback for older rows.

## Tables

- `avatar_dashboard_layout`: legacy compatibility table keyed by `widget_id`.
- `avatar_dashboard_rows`: row records with stable ids, row positions, collapsed state, settings JSON, and update timestamps.
- `avatar_dashboard_widgets`: row-scoped widget placements with stable ids, first-party `widget_key`, column position, 12-column width, enabled/collapsed state, settings JSON, and update timestamps.

`avatar_dashboard_widgets.widget_key` is unique for v1 so first-party widgets have a single instance.

## Service Contract

`AvatarService` exposes row/widget operations used by the HTMX editor routes:

- list dashboard rows with widgets;
- add and move rows;
- add widgets to a row;
- move widgets left, right, up, or down;
- resize widgets to any width from `1` through `12` that still fits the row;
- remove widgets.

The repository enforces row width totals at or below 12 columns, rejects duplicate widget keys, and rejects width values outside `1..12`.

## Compatibility

Existing calls to `saveDashboardWidget(...)` and `dashboardLayout()` remain available for compatibility. When legacy layout rows exist, the repository seeds row/widget records by mapping:

- `wide` to 6 columns;
- `standard` to 4 columns;
- `compact` to 3 columns.

Legacy widgets wrap to a new row when the next placement would exceed 12 columns.

## Editor UI Contract

The in-place `/avatar` layout editor uses per-action HTMX requests and OOB grid refreshes rather than a single flat save form:

- `POST /avatar/_layout/rows`
- `POST /avatar/_layout/rows/{rowId}/move?direction=up|down`
- `GET /avatar/_layout/rows/{rowId}/catalog`
- `POST /avatar/_layout/rows/{rowId}/widgets`
- `POST /avatar/_layout/widgets/{widgetId}/move?direction=left|right|up|down`
- `GET /avatar/_layout/widgets/{widgetId}/width-picker`
- `PUT /avatar/_layout/widgets/{widgetId}/width`
- `DELETE /avatar/_layout/widgets/{widgetId}`

The editor uses SimplyPages row/column layout primitives for the 12-column placement model and keeps mutations scoped to the shared `#avatar-edit-container` overlay/popover surface with `#avatar-widget-grid` refreshed out of band.
