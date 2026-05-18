# Dashboard

The `/dashboard` page is the operator overview for Magenta's orchestration state.

## What It Shows

The dashboard includes:

- **System Chat** link to `/chat`.
- A status strip with running jobs, pending jobs, message count, failed jobs, and active agents.
- **Active Work** for non-terminal jobs.
- **Open Projects** with project cards.
- **Agents** with status and model rows.
- Side panels for inbox messages, recent outputs, and recent events.

Most dashboard sections refresh through HTMX on load and periodically afterward.

## Status Counts

- **Running** counts jobs with `RUNNING` status.
- **Pending** combines queued and draft jobs.
- **Messages** counts user inbox messages.
- **Failed** counts jobs with `FAILED` status.
- **Active Agents** counts agents that are not disabled.

These are operational summaries, not a full audit log. For exact details, open the related page.

## Active Work

Active work shows jobs that are not completed or cancelled. Use the job title link to move into job details. Owner and project fields may display IDs in this summary table because it is a compact dashboard view; edit and submit forms increasingly use selectors where a user needs to choose a value.

## Open Projects

Open projects show project name, owner, and last update. Select a project to inspect workspace state, linked agents, jobs, and outputs.

## Agents

The agents table shows agent name, status, default model, and placeholder queue/inbox columns. Open an agent page for the authoritative queue, inbox, workspace, outputs, exec, and history tabs.

## Side Panels

- **Inbox** links to `/inbox` and shows the number of user messages.
- **Recent Outputs** links to `/outputs` and shows recent output names and run or plan context.
- **Recent Events** links to `/agents` and summarizes recent job, agent, and inbox events.

## When To Use Dashboard Vs Detail Pages

Use the dashboard for triage:

- Is anything running?
- Are messages waiting?
- Did recent output appear?
- Which agents are active?

Use detail pages for action:

- `/agents` for queue controls, diagnostics, force interrupt, shell exec, history, and lifecycle controls.
- `/jobs` for job items, recurrence, runs, outputs, and cancellation.
- `/projects` for workspace release and project context.
- `/outputs` for artifact filtering and downloads.
- `/inbox` for approvals and read/handled state.

## Common Errors

- **Loading... never changes**: refresh the page. If it persists, the backing fragment request may have failed.
- **No active work**: no jobs are currently draft, queued, running, or failed.
- **No recent outputs**: no artifacts match the dashboard's recent output query.
- **Counts disagree with a detail page**: detail pages are more authoritative; the dashboard is a periodic summary.

## Alpha Limits

The dashboard is a lightweight operations view. Some cells still show raw IDs for compact reference, and queue/inbox totals in the agent table may lag behind detail tabs. Use the detail tabs before pausing, canceling, deleting, or diagnosing work.
