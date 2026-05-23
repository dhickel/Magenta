# Avatar Dashboard Layout Persistence

`/avatar` dashboard layout persistence is owned by the Avatar package and stored in `avatar.sqlite`. The current browser UI still reads the compatibility `AvatarDashboardWidget` layout contract, but the persistence layer now also supports the row/widget model required by the next editable dashboard refactor.

## Tables

- `avatar_dashboard_layout`: legacy compatibility table keyed by `widget_id`.
- `avatar_dashboard_rows`: row records with stable ids, row positions, collapsed state, settings JSON, and update timestamps.
- `avatar_dashboard_widgets`: row-scoped widget placements with stable ids, first-party `widget_key`, column position, 12-column width, enabled/collapsed state, settings JSON, and update timestamps.

`avatar_dashboard_widgets.widget_key` is unique for v1 so first-party widgets have a single instance.

## Service Contract

`AvatarService` exposes row/widget operations for later HTMX editor routes:

- list dashboard rows with widgets;
- add and move rows;
- add widgets to a row;
- move widgets left, right, up, or down;
- resize widgets to supported widths `3`, `4`, `6`, `8`, or `12`;
- remove widgets.

The repository enforces row width totals at or below 12 columns and rejects duplicate widget keys.

## Compatibility

Existing calls to `saveDashboardWidget(...)` and `dashboardLayout()` remain available for the current `/avatar` UI. When legacy layout rows exist, the repository seeds row/widget records by mapping:

- `wide` to 6 columns;
- `standard` to 4 columns;
- `compact` to 3 columns.

Legacy widgets wrap to a new row when the next placement would exceed 12 columns.
