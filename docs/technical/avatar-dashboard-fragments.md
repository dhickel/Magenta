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
- `#avatar-shell-tabs-wrap` as an out-of-band swap so active-tab styling and shell actions stay in sync without rerendering the left chat rail.

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
- Work Areas: `GET /avatar/_work-areas/{workAreaId}/explorer`, `GET /avatar/_work-areas/{workAreaId}/explorer/list`, `GET /avatar/_work-areas/{workAreaId}/inspect`, `GET /avatar/_work-areas/{workAreaId}/viewer`, `GET /avatar/_work-areas/{workAreaId}/viewer/text`, legacy `GET /avatar/_work-areas/{workAreaId}/preview`, `GET /avatar/_work-areas/{workAreaId}/edit`, `GET /avatar/_work-areas/{workAreaId}/modal/{action}`, `GET /avatar/_work-areas/{workAreaId}/modal/tag-editor`, `POST /avatar/_work-areas/{workAreaId}/modal/tag-editor/tags`, `POST /avatar/_work-areas/{workAreaId}/modal/tag-editor/assign`, `PUT /avatar/_work-areas/{workAreaId}/text`, `POST /avatar/_work-areas/{workAreaId}/directories`, `POST /avatar/_work-areas/{workAreaId}/text`, `POST /avatar/_work-areas/{workAreaId}/files/delete`, `POST /avatar/_work-areas/{workAreaId}/files/rename`, `POST /avatar/_work-areas/{workAreaId}/files/action/{copy|move}`, `POST /avatar/_work-areas/{workAreaId}/tags`, `POST|DELETE /avatar/_work-areas/{workAreaId}/files/tags`, `POST|DELETE /avatar/_work-areas/{workAreaId}/labels/note`, `POST /avatar/_work-areas/{workAreaId}/mark`, and compatibility `DELETE /avatar/_work-areas/{workAreaId}/files`.

Planner, todo, calendar, and note flows still exist, but they are reached from dashboard widgets and detail surfaces rather than from a standalone shell toolbar action.

## Work Area Explorer Fragment Contract

The Work Area explorer uses stable HTMX targets:

- `#avatar-workarea-explorer-shell`: full toolbar, path bar, details table, inspector, and modal container.
- `#avatar-workarea-list-region`: details table/list region.
- `#avatar-workarea-inspector`: selected file/directory metadata and operations.
- `#avatar-workarea-modal`: stable empty modal host.

Modal routes target `#avatar-workarea-modal` with `innerHTML` and return modal body content without a duplicate `id="avatar-workarea-modal"` wrapper. Mutation routes that affect current explorer state return out-of-band fragments for the modal host, list region, and inspector so table and metadata stay coherent after save, tag, rename, delete, copy, or move.

The visible explorer contract is a details/list layout with `Name`, `File Type`, `Size`, `Created`, `Last Modified`, `Tags`, and `Actions` columns plus a separate right inspector. Rows are selectable by full-row click while preserving button/link action clicks. Row actions stay compact: view when supported, rename, and delete. The inspector mirrors view/rename/delete and owns expanded copy/move controls plus the modal Tag Editor entry point.

Viewer modals expose explicit state hooks:

- Markdown rendered tab: `data-viewer-kind="markdown"` and `data-active-tab="rendered"`.
- Markdown raw tab: `data-viewer-kind="markdown"` and `data-active-tab="text"`.
- Plain text raw view: `data-viewer-kind="text"` and `data-active-tab="text"`.

Copy and move forms expose operation-specific hooks such as `form[data-file-action="copy"]`, `input[aria-label="Copy destination directory"]`, and `button[data-file-action-submit="copy"]`. Destination is required; blank copy/move destinations are rejected instead of defaulting silently.

## Client Assets

- `/js/avatar-chat.js?v=3` owns the compact Avatar chat surface.
- `/js/avatar-layout-edit.js?v=1` owns in-place dashboard edit helpers.
- `/js/avatar-shell.js?v=6` owns desktop chat corner resizing and browser-local width/height persistence.

`avatar-shell.js` stores the desktop rail width in `localStorage` under `magenta.avatar.chatRailWidthPx` and the desktop chat panel height under `magenta.avatar.chatPanelHeightPx`. The shell reads those values only at desktop breakpoints, clamps them before applying CSS variables, and ignores them on mobile stacked layouts.
Desktop resize math starts from the chat panel's rendered box and writes `--avatar-chat-rail-width` plus `--avatar-chat-panel-height` on `.avatar-shell`. The rail width variable controls the left grid column so dashboard width responds immediately when the bottom-right chat corner handle moves. The handle now uses a compact sideways double-arrow affordance at the panel corner and allows both horizontal and vertical drag adjustments.
