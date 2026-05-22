# Avatar Dashboard Fragment Routes

`/avatar` is a server-rendered SimplyPages page with HTMX fragments for widget refresh, layout editing, organizer mutations, output preview, and internal alert dismissal.

## Page And Layout

- `GET /avatar` renders the full shell with Avatar CSS, compact chat, widget grid, edit container, and output preview container.
- `GET /avatar/_widgets` returns `#avatar-widget-grid`.
- `GET /avatar/_widgets/{widgetKey}` returns one stable widget root.
- `GET /avatar/_edit` returns the layout edit modal.
- `PUT /avatar/_layout` validates known widget keys and sizes, persists `AvatarDashboardWidget` rows through `AvatarService`, and returns out-of-band swaps for the edit container and widget grid.

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
- Outputs: `GET /avatar/_outputs/{artifactId}` uses `OutputArtifactService` for confined artifact content.
- Alerts: `POST /avatar/_alerts/{eventId}/dismiss` records an internal Avatar event dismissal and rerenders the alerts widget.

The alerts widget reads existing inbox messages and internal Avatar events only. Email ingestion is intentionally out of scope for this route set.
