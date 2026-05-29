# Assistant Dashboard Organizer Data

The planner/organizer records that originated with the legacy personal-dashboard prototype still live in `avatar.sqlite` and remain intentionally separate from Magenta executable tasks, plans, assignments, and jobs. The current user-facing dashboard surface is the Assistant dashboard on `/`; dashboards are agent-agnostic user-widget containers, not agent shells or execution owners.

## Data Model

- `avatar_planner_tasks` stores durable planner records with status, priority, optional start/due instants, timezone, recurrence JSON, and optional links to existing project, assignment, job, or output ids.
- `avatar_planner_subtodos` stores checklist items owned by a planner task.
- `avatar_planner_task_notes` links planner tasks to existing organizer notes.
- `avatar_planner_calendar_projection` stores generated occurrences for calendar-style views.
- `avatar_planner_day_maps` stores date-specific top priorities, now/next/later ids, restart metadata, and daily review notes.
- `avatar_planner_time_blocks` stores scheduled blocks independently from task due dates.
- `avatar_planner_reminders` stores in-dashboard reminder records with `OPEN`, `SNOOZED`, `COMPLETED`, or `SKIPPED` statuses. Reminder records are dashboard inbox items only; no external notification delivery is implied.
- `avatar_planner_occurrences` stores projected recurring-task occurrence state such as skip, snooze, and restart metadata without completing or corrupting the parent task.
- `avatar_habits` stores build/quit trackers with period, quantity/unit target, optional display days/time range, streak preference, and archive state.
- `avatar_habit_logs` stores day-level history corrections by unique habit/date, including logged, skipped, and restarted states.

Recurrence JSON is written explicitly as scalar fields so repository tests and lightweight tools do not depend on Jackson Java Time modules. Supported friendly modes are `NONE`, `DAILY`, `WEEKLY`, and `MONTHLY`; `CRON` is stored as an advanced value but is not automated in v1.

## UI Contract

Planner, todo, calendar, and note operations are reached from Assistant dashboard widgets and their detail flows. They are dashboard widget content, not top-level shell tabs.

Phase 02 planner widgets expose:

- Today Planner: top priorities, now/next/later, overdue, unscheduled, time blocks, quick capture, restart day, and daily review.
- Tasks/Routines: filters, recurrence metadata, subtasks, project links, status, and skip/snooze/restart occurrence controls.
- Calendar/Schedule: month grid plus agenda view that merges calendar events, time blocks, recurrence projections, and reminders while preserving their distinct source types. The dashboard detail surface exposes HTMX creation forms for time blocks and in-dashboard reminders.
- Habits/Trackers: build/quit tracker summary with non-punitive progress/trend/streak chips, day-level history correction, skip, restart, and archive controls.
- Reminders/Alerts: in-dashboard inbox with due/upcoming/snoozed summaries, linked source display, complete, snooze, skip, and reschedule controls.
- Dashboard Context: read-only dashboard/widget state and visible tool descriptor summary. It is deliberately separate from chat action tooling.

The shared organizer modal uses HTMX tab swaps for:

- `planner`: create planner tasks, set recurrence, link existing work ids, and add subtodos.
- `todos`: view and mutate the existing organizer todo list.
- `calendar`: view existing calendar items plus planner projections.
- `notes`: view and capture existing organizer notes.

Mutations return the same modal target so the user stays in context. The planner recurrence inputs are rendered from one reusable compact component in the dashboard component layer. The removed Avatar shell tabs (`dashboard`, `queue`, `history`, `profile`, `outputs`, and `work-areas`) are not part of the current Assistant dashboard contract. Work Areas are accessed from agent detail instead of Assistant dashboard tabs or widgets.

## Deferred Automation

Planner tasks can link existing Magenta work in v1. In-dashboard reminders are accepted for the widget suite, but external notification delivery, contact-user automation, wait-for-input automation, and task-to-assignment execution remain future work and should not be inferred from the planner tables.
