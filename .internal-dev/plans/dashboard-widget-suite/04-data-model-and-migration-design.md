---
schema_version: 1
document_type: data-model-design
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Data Model And Migration Design

## Dashboard Instance Migration

Target `avatar.sqlite` table evolution:

- Add `widget_type text not null default widget_key` to `user_dashboard_widgets`.
- Preserve `widget_key` during compatibility as legacy alias if needed; new code should read/write `widget_type`.
- Add `instance_label text`, `created_at text`, and optional `single_instance_key text`.
- Replace global `unique(dashboard_id, widget_key)` with either:
  - no unique constraint on `(dashboard_id, widget_type)` plus service-level registry validation; and
  - partial uniqueness for single-instance widgets using `single_instance_key`, where `single_instance_key = widget_type` only for `SINGLE_PER_DASHBOARD`/`SINGLE_SYSTEM`, null for multi-instance widgets.
- Add indexes for `(dashboard_id, widget_type)`, `(dashboard_id, row_id, column_position)`, and common settings-derived query needs only after proven necessary.

SQLite migration must be additive-safe:

1. Create `user_dashboard_widgets_new` with target columns/constraints.
2. Copy existing rows with `widget_type = widget_key`, `single_instance_key = widget_key` for existing static widgets, `created_at = updated_at`.
3. Drop/rename old table in a transaction where the local migration pattern supports it.
4. Recreate indexes and foreign keys.
5. Validate row count, widget ids, row ids, dashboard ids, settings JSON, and column ordering.

If the project lacks formal migration tooling, the worker must follow current `avatar-schema.sql` compatibility style and add tests that initialize old schema fixtures before applying bootstrap.

## Planner Core Model

Unify behavior at service level first, not necessarily by immediately dropping tables.

Target records:

- `planner_items`: task/routine/chores with title, notes, status, priority, energy/effort optional, tags, due date/range, scheduled start/end, timezone, recurrence rule, project links, source, created/updated/completed.
- `planner_subtasks`: ordered child tasks.
- `planner_occurrences`: projected or materialized occurrences with status, skip/snooze/restart metadata, planned/actual times.
- `planner_time_blocks`: day-map blocks separate from due dates.
- `planner_reminders`: in-dashboard reminders with due/remind-at, status, snooze/reschedule, source item link.
- `planner_day_maps`: date-specific now/next/later/top priorities/order/review state.

Implementation may adapt existing `avatar_planner_tasks`, `avatar_daily_tasks`, `avatar_todos`, and `avatar_calendar_items` behind a service facade if a full table consolidation is too risky for one phase. However, workers must not perpetuate multiple shallow lists at the API/UI contract level.

## Calendar/Schedule Model

- Calendar events are separate from planner tasks and reminders.
- Task due dates, scheduled time blocks, reminder records, and recurrence projections remain separate.
- Day/week/month/agenda views consume a read model that merges events, time blocks, planner occurrences, and reminders with typed source metadata.

## Notes Model

- Personal notes remain in `avatar_notes`.
- File-backed notes are Work Area/project/agent files with note labels/tags through existing Work Area/file services.
- `avatar_notes.source_ref_json` can store source metadata for personal captures, but must not pretend file-backed notes are DB-owned.
- Notes widget settings store source mode and last-opened note/file reference.

## Project/Household Artifact Model

- Runtime `Project` remains identity/membership/workspace anchor.
- Household/project content is stored as typed files under project/Work Area roots, for example:
  - `.magenta/project/goals.json`
  - `.magenta/project/materials.json`
  - `.magenta/project/contacts.json`
  - `.magenta/project/blockers.json`
  - `.magenta/project/next-actions.json`
  - Markdown notes tagged through file label services.
- Service adapters own JSON schema validation, path confinement, default file creation, and optional indexes.
- No cross-database foreign keys. DB records may store string ids and validate through services at read/write time.

## Habits/Trackers Model

- Habits have type `BUILD` or `QUIT`, period `DAILY|WEEKLY|MONTHLY|CUSTOM`, target quantity/unit, optional display days/time range, calendar display flag, status, and archived flag.
- Habit logs support backfill/history correction.
- Summary uses progress/trend chips; streaks are optional and non-primary.

## Reminder Boundary

In scope:

- in-dashboard reminder records;
- reminder status `OPEN|SNOOZED|DONE|DISMISSED|CANCELED`;
- linked source type/id;
- snooze/reschedule/complete actions;
- alert inbox widget.

Out of scope unless user approves Gate A:

- email/push/PWA delivery;
- background user contact automation;
- automatic assignment creation from missed tasks;
- wait-for-input automation.
