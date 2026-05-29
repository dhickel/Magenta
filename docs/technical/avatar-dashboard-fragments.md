# Assistant Dashboard Fragment Routes

`/` renders the dashboard home with the `Assistant` dashboard selected. Dashboard records are agent-agnostic widget containers stored in the dashboard persistence context.

## Shell Routes

- `GET /` renders the full dashboard home.
- `GET /dashboards/{dashboardId}` renders a selected dashboard.
- `GET /dashboards/{dashboardId}/_page` renders the `#dashboard-home` fragment for HTMX dashboard selector/edit swaps and pushes `/dashboards/{dashboardId}` or `/dashboards/{dashboardId}?edit=true`.
- `GET /dashboards/_create` renders the create-dashboard modal.
- `POST /dashboards` creates a new empty dashboard by name.
- `GET /dashboards/_modal/clear` clears the shared modal host.

## Layout Routes

- `POST /dashboards/{dashboardId}/_layout/rows`
- `POST /_dashboards/_layout/rows`
- `POST /_dashboards/_layout/rows/{rowId}/insert-after`
- `POST /_dashboards/_layout/rows/{rowId}/move?direction=up|down`
- `DELETE /_dashboards/_layout/rows/{rowId}`
- `GET /_dashboards/_layout/rows/{rowId}/catalog`
- `POST /_dashboards/_layout/rows/{rowId}/widgets`
- `POST /_dashboards/_layout/widgets/{widgetId}/move?direction=left|right|up|down`
- `GET /_dashboards/_layout/widgets/{widgetId}/width-picker`
- `PUT /_dashboards/_layout/widgets/{widgetId}/width`
- `POST /_dashboards/_layout/widgets/{widgetId}/width-cycle`
- `DELETE /_dashboards/_layout/widgets/{widgetId}`

Layout mutations refresh `#avatar-widget-grid` with out-of-band swaps and clear `#avatar-edit-container` when appropriate.

## Widget Routes

- Todos: `POST /_dashboards/_todos`, `POST /_dashboards/_todos/{todoId}/complete`, `DELETE /_dashboards/_todos/{todoId}`
- Daily tasks: `POST /_dashboards/_daily-tasks`, `POST /_dashboards/_daily-tasks/{taskId}/complete`
- Notes: `POST /_dashboards/_notes`
- Calendar: `POST /_dashboards/_calendar`, `DELETE /_dashboards/_calendar/{calendarId}`
- Planner tasks: `POST /_dashboards/_planner-tasks`, `POST /_dashboards/_planner-tasks/{taskId}/subtodos`
- Outputs: `GET /_dashboards/_outputs/{artifactId}`
- Alerts: `POST /_dashboards/_alerts/{eventId}/dismiss`

## Work Areas

Work Area browsing moved to agent detail:

- `GET /agents/_detail/{agentId}/work-areas`
- `GET /agents/_detail/{agentId}/work-areas/{workAreaId}/explorer`
- `GET /agents/_detail/{agentId}/work-areas/{workAreaId}/viewer`
- `GET /agents/_detail/{agentId}/work-areas/{workAreaId}/modal/{action}`
- `POST /agents/_detail/{agentId}/work-areas/{workAreaId}/directories`
- `POST|PUT /agents/_detail/{agentId}/work-areas/{workAreaId}/text`
- `POST /agents/_detail/{agentId}/work-areas/{workAreaId}/files/rename`
- `POST /agents/_detail/{agentId}/work-areas/{workAreaId}/files/delete`
- `POST /agents/_detail/{agentId}/work-areas/{workAreaId}/files/action/{copy|move}`
- `GET /agents/_detail/{agentId}/work-areas/{workAreaId}/files/directories`
- `GET /agents/_detail/{agentId}/work-areas/{workAreaId}/files/action/{copy|move}/picker`
- `GET|POST /agents/_detail/{agentId}/work-areas/{workAreaId}/modal/tag-editor...`
- `POST|DELETE /agents/_detail/{agentId}/work-areas/{workAreaId}/files/tags`

The route guard checks that the Work Area owner type is `AGENT` and the owner id matches the agent detail id. Newly rendered Work Area UI must call the agent-detail route family, not `/avatar/_work-areas`.

## Assets

- `/css/avatar-dashboard.css?v=7` owns Assistant dashboard, compact chat rail, layout editor, and retained Work Area browser styling.
- `/js/avatar-chat.js?v=4` owns the compact dashboard chat surface.
- `/js/avatar-layout-edit.js?v=1` owns in-place dashboard edit helpers.
- `/js/avatar-workarea-editor.js?v=2` owns local Work Area editor behavior.
- `/js/avatar-shell.js?v=6` owns desktop chat corner resizing and local geometry persistence.

The dashboard root must render `data-avatar-shell="true"` around `.avatar-shell-grid`, `[data-avatar-chat="true"]`, and `[data-avatar-chat-corner-resizer="true"]`; `avatar-shell.js` uses that hook to bind horizontal rail width and vertical panel height resizing.

Dashboard selector links and dashboard edit toggles should target `#dashboard-home` with `hx-swap="outerHTML"` and `hx-push-url` so switching dashboards refreshes the dashboard component without reloading the full shell or top navigation.
