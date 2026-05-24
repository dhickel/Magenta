# Avatar Dashboard Fragment Routes

`/avatar` is a server-rendered SimplyPages shell with HTMX fragments for tab swaps, dashboard layout editing, widget detail flows, output preview, alerts, and Work Area file operations.

## Shell Routes

- `GET /avatar` renders the full Avatar shell.
- `GET /avatar?tab=<dashboard|queue|history|profile|outputs|work-areas>` deep-links the initial tab.
- `GET /avatar?tab=dashboard&edit=true` renders the dashboard tab in layout edit mode.
- `GET /avatar/_tab-panel?tab=...`
- `GET /avatar/_tab-panel/{tab}`

Tab fragment responses return two pieces:

- `#avatar-tab-panel` as the primary swap target;
- `#avatar-shell-tabs-wrap` as an out-of-band swap so active-tab styling and shell actions stay in sync without rerendering the right chat rail.

`edit=true` is normalized away for all non-dashboard tabs. The shell pushes the canonical tab URL back into browser history with `HX-Push-Url`.

## Dashboard Layout

- `GET /avatar/_widgets`
- `GET /avatar/_widgets?edit=true`
- `GET /avatar/_widgets/{widgetKey}`
- `GET /avatar/_widgets/{widgetKey}/detail`
- `GET /avatar/_edit`
- `POST /avatar/_layout/rows`
- `POST /avatar/_layout/rows/{rowId}/insert-after`
- `POST /avatar/_layout/rows/{rowId}/move?direction=up|down`
- `DELETE /avatar/_layout/rows/{rowId}`
- `GET /avatar/_layout/rows/{rowId}/catalog`
- `POST /avatar/_layout/rows/{rowId}/widgets`
- `POST /avatar/_layout/widgets/{widgetId}/move?direction=left|right|up|down`
- `GET /avatar/_layout/widgets/{widgetId}/width-picker`
- `PUT /avatar/_layout/widgets/{widgetId}/width`
- `POST /avatar/_layout/widgets/{widgetId}/width-cycle`
- `DELETE /avatar/_layout/widgets/{widgetId}`

Only the `Dashboard` tab is layout-editable. Layout mutations refresh `#avatar-widget-grid` with an out-of-band swap and clear the shared `#avatar-edit-container` when appropriate.

Edit chrome contract:

- widget controls stay in the widget corner;
- row controls render through `.avatar-row-decoration` above row content;
- empty rows render as `.avatar-empty-row-insert`;
- insert-row affordances render as compact separators between populated rows.

The old top-level `Organizer` and `Refresh Widgets` shell actions are intentionally removed from the shell contract.

## Widget And Modal Actions

- Planner tasks: `POST /avatar/_planner-tasks` and `POST /avatar/_planner-tasks/{taskId}/subtodos`
- Todos: `POST /avatar/_todos`, `POST /avatar/_todos/{todoId}/complete`, `DELETE /avatar/_todos/{todoId}`
- Daily tasks: `POST /avatar/_daily-tasks`, `POST /avatar/_daily-tasks/{taskId}/complete`
- Notes: `POST /avatar/_notes`
- Calendar: `POST /avatar/_calendar`, `DELETE /avatar/_calendar/{calendarId}`
- Outputs: `GET /avatar/_outputs/{artifactId}`
- Alerts: `POST /avatar/_alerts/{eventId}/dismiss`
- Work Areas: `GET /avatar/_work-areas/{workAreaId}/explorer`, `GET /avatar/_work-areas/{workAreaId}/preview`, `GET /avatar/_work-areas/{workAreaId}/edit`, `PUT /avatar/_work-areas/{workAreaId}/text`, `POST /avatar/_work-areas/{workAreaId}/directories`, `POST /avatar/_work-areas/{workAreaId}/text`, `POST /avatar/_work-areas/{workAreaId}/mark`, and `DELETE /avatar/_work-areas/{workAreaId}/files`

Planner, todo, calendar, and note flows still exist, but they are reached from dashboard widgets and detail surfaces rather than from a standalone shell toolbar action.

## Client Assets

- `/js/avatar-chat.js?v=3` owns the compact Avatar chat surface.
- `/js/avatar-layout-edit.js?v=1` owns in-place dashboard edit helpers.
- `/js/avatar-shell.js?v=1` owns desktop rail resizing and browser-local width persistence.

`avatar-shell.js` stores the desktop rail width in `localStorage` under `magenta.avatar.chatRailWidthPx`. The shell reads that value only at desktop breakpoints and ignores it on mobile stacked layouts.
