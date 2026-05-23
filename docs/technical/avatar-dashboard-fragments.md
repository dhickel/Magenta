# Avatar Dashboard Fragment Routes

`/avatar` is a server-rendered SimplyPages page with HTMX fragments for widget refresh, layout editing, organizer mutations, output preview, and internal alert dismissal.

## Page And Layout

- `GET /avatar` renders the full shell with Avatar CSS, compact chat, widget grid, edit container, and output preview container.
- `GET /avatar/_widgets` returns `#avatar-widget-grid`.
- `GET /avatar/_widgets/{widgetKey}` returns one stable widget root.
- `GET /avatar/_edit` returns the row/widget layout edit modal.
- `POST /avatar/_layout/rows` adds a row.
- `POST /avatar/_layout/rows/{rowId}/move?direction=up|down` reorders rows.
- `GET /avatar/_layout/rows/{rowId}/catalog` opens the single add-widget modal view.
- `POST /avatar/_layout/rows/{rowId}/widgets` adds a known first-party widget with a 12-column width.
- `POST /avatar/_layout/widgets/{widgetId}/move?direction=left|right|up|down` moves widgets inside or across rows.
- `PUT /avatar/_layout/widgets/{widgetId}/width` resizes a widget to `3`, `4`, `6`, `8`, or `12` columns.
- `DELETE /avatar/_layout/widgets/{widgetId}` removes a widget instance.

Layout mutations return the edit modal as the primary target and refresh `#avatar-widget-grid` with an out-of-band swap. `PUT /avatar/_layout` remains as a deprecated compatibility endpoint that rerenders the row editor response without accepting the old flat form contract.

Stable widget root IDs:

- `avatar-widget-daily-tasks`
- `avatar-widget-todos`
- `avatar-widget-calendar`
- `avatar-widget-notes`
- `avatar-widget-files`
- `avatar-widget-outputs`
- `avatar-widget-system`
- `avatar-widget-alerts`
- `avatar-widget-recent-work`

## Widget Actions

- Todos: `POST /avatar/_todos`, `POST /avatar/_todos/{todoId}/complete`, `DELETE /avatar/_todos/{todoId}`.
- Daily tasks: `POST /avatar/_daily-tasks`, `POST /avatar/_daily-tasks/{taskId}/complete`.
- Notes: `POST /avatar/_notes`.
- Calendar: `POST /avatar/_calendar`, `DELETE /avatar/_calendar/{calendarId}`.
- Work Areas: `GET /avatar/_work-areas/{workAreaId}/explorer`, `GET /avatar/_work-areas/{workAreaId}/preview`, `GET /avatar/_work-areas/{workAreaId}/edit`, `PUT /avatar/_work-areas/{workAreaId}/text`, `POST /avatar/_work-areas/{workAreaId}/directories`, `POST /avatar/_work-areas/{workAreaId}/mark`, and `DELETE /avatar/_work-areas/{workAreaId}/files`.
- Outputs: `GET /avatar/_outputs/{artifactId}` uses `OutputArtifactService` for confined artifact content.
- Alerts: `POST /avatar/_alerts/{eventId}/dismiss` records an internal Avatar event dismissal and rerenders the alerts widget.

The Work Areas widget reads agent-owned Work Areas from `WorkAreaService` and opens the explorer in the shared modal container. Explorer fragments delegate filesystem behavior to `WorkAreaExplorerService`, so traversal, symlink, safe text editing, and protected delete rules stay in the workspace service layer. The alerts widget reads existing inbox messages and internal Avatar events only. Email ingestion is intentionally out of scope for this route set.
