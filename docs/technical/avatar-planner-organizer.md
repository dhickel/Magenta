# Avatar Planner Organizer

Avatar planner data lives in `avatar.sqlite` and is intentionally separate from Magenta executable tasks, plans, assignments, and jobs.

## Data Model

- `avatar_planner_tasks` stores durable planner records with status, priority, optional start/due instants, timezone, recurrence JSON, and optional links to existing project, assignment, job, or output ids.
- `avatar_planner_subtodos` stores checklist items owned by a planner task.
- `avatar_planner_task_notes` links planner tasks to existing Avatar notes.
- `avatar_planner_calendar_projection` stores generated occurrences for calendar-style views.

Recurrence JSON is written explicitly as scalar fields so repository tests and lightweight tools do not depend on Jackson Java Time modules. Supported friendly modes are `NONE`, `DAILY`, `WEEKLY`, and `MONTHLY`; `CRON` is stored as an advanced value but is not automated in v1.

## UI Contract

Planner, todo, calendar, and note operations are reached from Avatar dashboard widgets and their detail flows. The old top-level shell `Organizer` action is intentionally not part of the current `/avatar` tabbed shell.

The legacy fragment route `GET /avatar/_organizer?tab=planner` still renders the shared organizer modal when called by widget/detail flows or compatibility clients. The modal uses HTMX tab swaps for:

- `planner`: create planner tasks, set recurrence, link existing work ids, and add subtodos.
- `todos`: view and mutate the existing Avatar todo list.
- `calendar`: view existing calendar items plus planner projections.
- `notes`: view and capture existing Avatar notes.

Mutations return the same modal target so the user stays in context. The planner recurrence inputs are rendered from one reusable compact component in `AvatarDashboardComponents`. Top-level `/avatar` shell navigation remains limited to `dashboard`, `queue`, `history`, `profile`, `outputs`, and `work-areas`; only the dashboard tab enters layout edit mode.

## Deferred Automation

Planner tasks can link existing Magenta work in v1. New scheduler behavior, contact-user automation, wait-for-input automation, reminder delivery, and task-to-assignment execution are future work and should not be inferred from the planner tables.
