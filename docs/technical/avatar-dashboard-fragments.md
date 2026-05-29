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

- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}` returns a summary fragment rooted at `#avatar-widget-{widgetInstanceId}`.
- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/detail` opens the detail modal.
- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings` opens the generic settings modal shell.
- `PUT /dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings` validates settings and returns OOB modal close plus summary refresh.
- Compatibility routes under `/_dashboards/_widgets/{widgetKey}` remain for older quick-action fragments and resolve the first matching widget instance.
- Todos: `POST /_dashboards/_todos`, `POST /_dashboards/_todos/{todoId}/complete`, `DELETE /_dashboards/_todos/{todoId}`
- Daily tasks: `POST /_dashboards/_daily-tasks`, `POST /_dashboards/_daily-tasks/{taskId}/complete`
- Notes: `POST /_dashboards/_notes`
- Instance notes: `POST /dashboards/{dashboardId}/widgets/{widgetInstanceId}/_notes`, `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/_notes/{noteId}`
- File notes: `GET|PUT /dashboards/{dashboardId}/widgets/{widgetInstanceId}/_file-note?source=project|work_area|agent&path=...`
- Project artifacts: `PUT /dashboards/{dashboardId}/widgets/{widgetInstanceId}/_project-artifacts/{artifactType}`
- Scoped outputs: `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/_outputs/{artifactId}`
- Agent Work Area mini-view: `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/_work-area-file?path=...`
- Calendar: `POST /_dashboards/_calendar`, `DELETE /_dashboards/_calendar/{calendarId}`
- Planner tasks: `POST /_dashboards/_planner-tasks`, `POST /_dashboards/_planner-tasks/{taskId}/subtodos`
- Habits/Trackers: `POST /_dashboards/_habits`, `POST /_dashboards/_habits/{habitId}/logs`, `POST /_dashboards/_habits/{habitId}/archive`
- Reminders/Alerts: `POST /_dashboards/_reminders`, `POST /_dashboards/_reminders/{reminderId}/complete`, `POST /_dashboards/_reminders/{reminderId}/snooze`, `POST /_dashboards/_reminders/{reminderId}/reschedule`, `POST /_dashboards/_reminders/{reminderId}/skip`, `POST /_dashboards/_reminders/{reminderId}/restart`
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

- `/css/avatar-dashboard.css?v=12` owns Assistant dashboard, compact chat rail, layout editor, planner/habit/reminder/context widgets, notes/project/agent operational widgets, and retained Work Area browser styling.
- `/js/avatar-chat.js?v=4` owns the compact dashboard chat surface.
- `/js/avatar-layout-edit.js?v=1` owns in-place dashboard edit helpers.
- `/js/avatar-workarea-editor.js?v=2` owns local Work Area editor behavior.
- `/js/avatar-shell.js?v=6` owns desktop chat corner resizing and local geometry persistence.

The dashboard root must render `data-avatar-shell="true"` around `.avatar-shell-grid`, `[data-avatar-chat="true"]`, and `[data-avatar-chat-corner-resizer="true"]`; `avatar-shell.js` uses that hook to bind horizontal rail width and vertical panel height resizing.

Dashboard selector links and dashboard edit toggles should target `#dashboard-home` with `hx-swap="outerHTML"` and `hx-push-url` so switching dashboards refreshes the dashboard component without reloading the full shell or top navigation.

## Notes And Project Context Widgets

Notes widget settings use `noteSourceMode=personal|agent|project|work_area|mixed` plus optional `agentId`, `projectId`, and `workAreaId` bindings. Personal notes remain in `avatar_notes`; file-backed notes are read and saved through `WorkAreaExplorerService` or project owner-root file service paths. Last-opened personal and file references are stored as widget settings metadata.

The Projects and Contacts/Materials widgets bind to `projectId`. Typed household project artifacts are fixed JSON files under the project workspace at `.magenta/project/`: `goals.json`, `materials.json`, `contacts.json`, `blockers.json`, `next-actions.json`, and `progress.json`. `ProjectArtifactService` creates defaults, validates the expected top-level JSON field for each artifact, and keeps output and note summaries read-only in the widget.

## Agent Operational Widgets

Agent Status/Queue binds to `agentId` and renders no-agent, missing-agent, and selected-agent states. The read model comes from agent profile, assignment, and agent inbox services.

Agent Outputs uses `sourceMode=agent|project|job|work_area|dashboard` plus the matching binding id. It queries `OutputArtifactService` with explicit filters. The instance-scoped preview route rejects artifacts outside the current widget scope; the compatibility output preview route remains available for older generic widgets.

Agent Files/Notes binds to a selected Work Area, optionally constrained by `agentId`. The dashboard route verifies the Work Area owner is an agent and matches the selected agent when present, then previews through `WorkAreaExplorerService`. Newly rendered mini-view controls use `/dashboards/{dashboardId}/widgets/{widgetInstanceId}/_work-area-file` and do not emit legacy `/avatar/_work-areas` links.

## Tracking, Alerts, And Context Widgets

Habits/Trackers stores Avatar-owned build/quit habits in `avatar_habits` and day-level correction logs in `avatar_habit_logs`. The widget renders compact non-punitive progress, trend, and optional streak chips. History correction uses the unique habit/date log row, so a user can log, skip, or restart the same date without creating duplicate punitive entries.

Reminders/Alerts uses `avatar_planner_reminders` as an in-dashboard inbox. It supports create, complete, snooze, skip, restart, and reschedule actions through HTMX fragments. Reminder rows may show linked source type/id, but they do not create assignments and do not send email, push, PWA, or other external notifications.

Dashboard Context is read-only. It summarizes the selected dashboard rows/widgets and visible registry tool descriptors. It intentionally says descriptors do not grant chat actions, preserving the current approved-tool boundary.
