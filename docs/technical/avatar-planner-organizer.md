# Assistant Dashboard Organizer Data

The planner/organizer records that originated with the legacy personal-dashboard prototype still live in `avatar.sqlite` and remain intentionally separate from Magenta executable tasks, plans, assignments, and jobs. The current user-facing dashboard surface is the Assistant dashboard on `/`; dashboards are agent-agnostic user-widget containers, not agent shells or execution owners.

## Data Model

- `avatar_planner_tasks` stores durable planner records with status, priority, optional start/due instants, timezone, recurrence JSON, and optional links to existing project, assignment, job, or output ids.
- `avatar_planner_subtodos` stores checklist items owned by a planner task.
- `avatar_planner_task_notes` links planner tasks to existing organizer notes.
- `avatar_planner_calendar_projection` stores generated occurrences for calendar-style views.

Recurrence JSON is written explicitly as scalar fields so repository tests and lightweight tools do not depend on Jackson Java Time modules. Supported friendly modes are `NONE`, `DAILY`, `WEEKLY`, and `MONTHLY`; `CRON` is stored as an advanced value but is not automated in v1.

## UI Contract

Planner, todo, calendar, and note operations are reached from Assistant dashboard widgets and their detail flows. They are dashboard widget content, not top-level shell tabs.

The shared organizer modal uses HTMX tab swaps for:

- `planner`: create planner tasks, set recurrence, link existing work ids, and add subtodos.
- `todos`: view and mutate the existing organizer todo list.
- `calendar`: view existing calendar items plus planner projections.
- `notes`: view and capture existing organizer notes.

Mutations return the same modal target so the user stays in context. The planner recurrence inputs are rendered from one reusable compact component in the dashboard component layer. The removed Avatar shell tabs (`dashboard`, `queue`, `history`, `profile`, `outputs`, and `work-areas`) are not part of the current Assistant dashboard contract. Work Areas are accessed from agent detail instead of Assistant dashboard tabs or widgets.

## Deferred Automation

Planner tasks can link existing Magenta work in v1. New scheduler behavior, contact-user automation, wait-for-input automation, reminder delivery, and task-to-assignment execution are future work and should not be inferred from the planner tables.
