# Avatar Dashboard

The Avatar dashboard is available at `/avatar`. It is a personal dashboard for quick assistant chat, organizer work, alerts, recent outputs, and recent operational context. It is separate from `/dashboard`, which remains the operational monitoring surface for plans, workflows, jobs, projects, agents, and outputs.

## What Is On The Page

- Compact Avatar chat is always visible on `/avatar`.
- Organizer widgets cover daily tasks, todos, calendar items, and notes.
- Output widgets show recent materialized outputs and safe previews through the existing output artifact APIs.
- System and recent-work widgets summarize existing agent, job, assignment, and output state.
- Alerts use existing inbox and internal Avatar event data.

## Editing Widgets

Use **Edit Layout** on `/avatar` to enable or disable widgets and choose compact, standard, or wide sizing. Layout changes are saved through HTMX and the dashboard refreshes in place.

Routine widget actions such as adding todos, completing daily tasks, saving notes, adding calendar items, refreshing widgets, previewing outputs, and dismissing Avatar event alerts are HTMX actions. The compact chat uses a small `/avatar`-specific SSE script and does not load the full `/chat` client.
