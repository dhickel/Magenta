# Assistant Dashboards

The home route `/` is the user dashboard surface. It opens with the `Assistant` dashboard selected, a compact dashboard selector row, and a trailing `+` control for creating another dashboard.

Dashboards are configurable widget containers. They are not agents, Work Areas, or execution contexts.

Selecting another dashboard updates the dashboard area in place and keeps the page shell, top navigation, and chat rail context intact while the browser URL changes to the selected dashboard.

The left chat rail can be resized on desktop with the bottom-right corner handle. Horizontal resizing changes the chat rail width while dashboard widgets fill the remaining space; vertical resizing changes the chat panel height.

## Editing

Use the compact edit control on a dashboard to enter layout edit mode. Rows use the existing 12-column layout controls:

- Add rows from the empty-dashboard state or row insert affordances.
- Add widgets from the row widget picker.
- Move rows and widgets in place.
- Resize widgets with the width picker.
- Remove widgets and empty rows.
- Open a widget's detail or settings controls from the compact widget corner buttons.

New dashboards are created empty. The default `Assistant` dashboard starts with chat plus Today Planner, Calendar/Schedule, Tasks/Routines, notes, Habits/Trackers, Reminders/Alerts, system, Dashboard Context, and recent work.

## Planning Widgets

The Today Planner widget shows top priorities, now/next/later buckets, overdue and unscheduled work, time blocks, quick capture, restart day, and daily review notes.

The Tasks/Routines widget shows planner tasks, status/range/recurrence filters in the detail view, recurrence metadata, subtasks, project links, reminder counts, and non-punitive skip, snooze, and restart actions for projected recurring occurrences.

The Calendar/Schedule widget renders a real month grid plus agenda entries. Calendar events, task due dates, scheduled time blocks, reminder records, and recurrence projections are separate concepts in the UI and service model. The detail view includes in-dashboard forms for creating time blocks and reminder records.

Reminders in this suite are dashboard records only. They can be viewed, linked, snoozed, rescheduled, completed, or dismissed inside Magenta, but they do not send email, push, PWA, or other external notifications.

The Habits/Trackers widget supports build and quit trackers, daily/weekly/monthly targets, quantity/unit targets, optional display days and time ranges, archive state, history correction, skip, and restart. Progress chips are intentionally non-punitive: missed items can be skipped or restarted without marking a failure.

The Reminders/Alerts widget is the dashboard inbox for reminder records. It separates due, upcoming, and snoozed reminders, shows linked source ids when present, and provides complete, snooze, skip, and reschedule controls. External delivery remains deferred.

The Dashboard Context widget is read-only. It summarizes the selected dashboard/widget state and visible tool descriptor names, but it does not grant chat tools or imply new assistant actions.

Some widget types can appear more than once on the same dashboard. Single-instance widgets remain disabled in the picker after they are already present.

## Notes And Projects

The Notes widget can show personal notes, agent files, project files, Work Area files, or a mixed source view depending on the widget instance settings. Personal notes are quick-captured into Avatar notes and support search, tags, and last-opened note memory. File-backed notes stay in the selected agent/project/Work Area file store and open through confined file-note viewer/editor fragments.

The Projects widget summarizes a selected project without treating every project as a code repository. It shows whether the project has a git URL, then summarizes typed project artifacts for goals, materials, contacts, blockers, next actions, progress, notes, and recent outputs. Household artifacts are stored under the project workspace at `.magenta/project/*.json`.

The Contacts/Materials widget is a narrower project-bound view of the same typed project artifact files. Use it when a dashboard needs a compact household/project supplies and people panel without the full project summary.

## Work Areas

Work Areas are no longer dashboard widgets. Open an agent detail page from `Agents`, then use that agent's `Work Areas` tab to browse and edit the Work Areas owned by that agent.

The Agent Files/Notes widget is a compact exception for selected Work Area visibility. It shows a selected Work Area source chip, a bounded file row list, tagged/Markdown notes, and confined file previews. It does not expose internal run roots or replace the full agent detail Work Area browser.

## Agent Operations

The Agent Status/Queue widget binds to one selected agent. It shows the selected source, agent status/model, queue counts, running/waiting counts, recent queue rows, and agent inbox messages. If no agent is selected or the selected agent is missing, the widget shows a recoverable settings prompt.

The Agent Outputs widget always shows its source mode. It can be scoped to the selected agent, project, job, or Work Area. Dashboard-wide output browsing is available only when the widget source mode is explicitly set to dashboard-wide.

## Manage

The old operational dashboard is now `Manage` at `/manage`. The top navigation order is `Home`, `Chat`, `Agents`, `Manage`.
