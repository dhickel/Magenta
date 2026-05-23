# Avatar Dashboard Fragment Routes

`/avatar` is a server-rendered SimplyPages page with HTMX fragments for widget refresh, layout editing, organizer modals, output preview, and internal alert dismissal.

## Page And Layout

- `GET /avatar` renders the full shell with Avatar CSS, compact chat, widget grid, edit container, and output preview container.
- `GET /avatar?edit=true` renders the same dashboard in in-place layout edit mode.
- `GET /avatar/_widgets` returns `#avatar-widget-grid`.
- `GET /avatar/_widgets?edit=true` returns `#avatar-widget-grid` with demo-style in-place edit decorations: top-corner widget controls, centered add-widget sections, and insert-row separators.
- `GET /avatar/_widgets/{widgetKey}` returns one stable widget root.
- `GET /avatar/_widgets/{widgetKey}/detail` opens a widget-specific detail modal in the shared edit container.
- `GET /avatar/_edit` only clears or leaves the shared edit container empty. The legacy row/widget layout edit modal is no longer the layout workflow.
- `POST /avatar/_layout/rows` adds a row.
- `POST /avatar/_layout/rows/{rowId}/insert-after` inserts a row directly below an existing row.
- `POST /avatar/_layout/rows/{rowId}/move?direction=up|down` reorders rows.
- `DELETE /avatar/_layout/rows/{rowId}` removes an empty row.
- `GET /avatar/_layout/rows/{rowId}/catalog` opens the single add-widget modal view.
- `POST /avatar/_layout/rows/{rowId}/widgets` adds a known first-party widget with a 12-column width.
- `POST /avatar/_layout/widgets/{widgetId}/move?direction=left|right|up|down` moves widgets inside or across rows.
- `PUT /avatar/_layout/widgets/{widgetId}/width` resizes a widget to `3`, `4`, `6`, `8`, or `12` columns.
- `POST /avatar/_layout/widgets/{widgetId}/width-cycle` advances the widget through the same width presets for compact top-corner editing.
- `DELETE /avatar/_layout/widgets/{widgetId}` removes a widget instance.

Layout mutations refresh `#avatar-widget-grid` with an out-of-band swap and clear the shared edit container when appropriate. `PUT /avatar/_layout` remains as a deprecated compatibility endpoint that rerenders the edit-mode grid response without accepting the old flat form contract.

In-place edit mode is the source-of-truth layout workflow. Move, resize, add-row, add-widget, remove-widget, and empty-row delete controls are rendered on the live dashboard surface. Modals remain appropriate for widget-specific detail work and add-widget catalog selection, but layout placement and 12-column sizing do not use a separate modal editor.

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

- Organizer modal: `GET /avatar/_organizer?tab=planner|todos|calendar|notes`.
- Planner tasks: `POST /avatar/_planner-tasks` and `POST /avatar/_planner-tasks/{taskId}/subtodos`.
- Todos: `POST /avatar/_todos`, `POST /avatar/_todos/{todoId}/complete`, `DELETE /avatar/_todos/{todoId}`.
- Daily tasks: `POST /avatar/_daily-tasks`, `POST /avatar/_daily-tasks/{taskId}/complete`.
- Notes: `POST /avatar/_notes`.
- Calendar: `POST /avatar/_calendar`, `DELETE /avatar/_calendar/{calendarId}`.
- Work Areas: `GET /avatar/_work-areas/{workAreaId}/explorer`, `GET /avatar/_work-areas/{workAreaId}/preview`, `GET /avatar/_work-areas/{workAreaId}/edit`, `PUT /avatar/_work-areas/{workAreaId}/text`, `POST /avatar/_work-areas/{workAreaId}/directories`, `POST /avatar/_work-areas/{workAreaId}/mark`, and `DELETE /avatar/_work-areas/{workAreaId}/files`.
- Outputs: `GET /avatar/_outputs/{artifactId}` uses `OutputArtifactService` for confined artifact content.
- Alerts: `POST /avatar/_alerts/{eventId}/dismiss` records an internal Avatar event dismissal and rerenders the alerts widget.

The Organizer toolbar action opens a single modal container with tabs for planner tasks, todos, calendar, and notes. Planner task records are distinct from Magenta executable task/work units; v1 supports durable planner records, subtodos, recurrence projection, and optional links to existing project/assignment/job/output ids, but does not schedule new automation.

The Work Areas widget reads agent-owned Work Areas from `WorkAreaService` and opens the explorer in the shared modal container. Explorer fragments delegate filesystem behavior to `WorkAreaExplorerService`, so traversal, symlink, safe text editing, and protected delete rules stay in the workspace service layer. The alerts widget reads existing inbox messages and internal Avatar events only. Email ingestion is intentionally out of scope for this route set.
