# Avatar Dashboard

The Avatar dashboard is available at `/avatar`. It is a personal dashboard for quick assistant chat, organizer work, alerts, recent outputs, and recent operational context. It is separate from `/dashboard`, which remains the operational monitoring surface for plans, workflows, jobs, projects, agents, and outputs.

## What Is On The Page

- Compact Avatar chat is always visible on `/avatar`.
- Organizer widgets cover daily tasks, todos, calendar items, notes, and planner tasks.
- Work Areas show agent-owned Work Areas and open the file explorer modal.
- Output widgets show recent materialized outputs and safe previews through the existing output artifact APIs.
- System and recent-work widgets summarize existing agent, job, assignment, and output state.
- Alerts use existing inbox and internal Avatar event data.

## Editing Widgets

Use **Edit Layout In Place** on `/avatar` to change rows and widgets on the displayed dashboard. The layout uses 12-column rows. In edit mode, compact controls appear on the live dashboard surface: widget controls sit in the top corner of each widget, add-widget controls appear between row content, and insert-row controls appear as row separators. Placement, movement, and sizing happen where the widget is actually shown.

You can add rows, add one instance of each first-party widget, move widgets across rows, resize widgets from a compact width picker, remove widgets, and delete empty rows. Empty rows collapse into a compact add-widget affordance instead of a full blank dashboard band. Each action saves immediately through HTMX and refreshes the dashboard grid in place.

Clicking the width control opens a small picker beside that control. The picker offers common preset widths and a custom `n/12` input for any width that still fits the current row. It closes if you click away, press Escape, or apply a new width.

Adding a widget opens a focused picker modal. The picker lists available first-party widgets, disables widgets already present on the dashboard, and lets you choose the 12-column width before adding the widget to the row.

Each widget also has a small detail action. Detail views are for working with that widget's content or module-specific controls; dashboard placement and 12-column sizing remain in-place layout actions.

## Organizer

Use **Organizer** to open the tabbed organizer modal:

- **Planner** creates durable personal planner tasks with priority, start/due dates, recurrence, links to existing Magenta work, and subtodos.
- **Todos** shows the existing Avatar todo list.
- **Calendar** shows Avatar calendar items and planner projections.
- **Notes** shows Avatar notes.

Planner tasks are personal organizer records. They are not executable Magenta plans/tasks, and v1 does not schedule reminders, contact the user, or start assignments from planner recurrence.

## Work Areas And Files

The **Work Areas** widget opens a confined file explorer for agent-owned Work Areas. The explorer supports browsing directories, previewing and downloading files, safe text edits, creating directories, creating text files, deleting with confirmation, and marking nested directories as Work Areas.

New assignment work defaults to the selected Home Work Area. During execution, `workspace/` points at the selected Work Area and `root/` points at the broader owned root. Outputs default to the selected Work Area `outputs/` folder unless the submit form redirects them to another Work Area or to an existing confined owner-root directory.

## Submit Pickers

Operational submit forms now include **Work Area / Outputs** controls. Use the Work Area pickers to choose the selected execution Work Area or an output Work Area redirect. Direct output redirects still require an existing owner-root-relative directory path.

Plan chats do not show Work Area controls.

Routine widget actions such as adding todos, completing daily tasks, saving notes, adding calendar items, refreshing widgets, previewing outputs, and dismissing Avatar event alerts are HTMX actions. Long todo and daily-task lists are visually constrained so they do not take over the dashboard. The compact chat uses a small `/avatar`-specific SSE script with Avatar status/session chips and does not load the full `/chat` client.
