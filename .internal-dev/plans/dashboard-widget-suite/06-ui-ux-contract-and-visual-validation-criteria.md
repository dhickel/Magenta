---
schema_version: 1
document_type: ui-ux-contract
status: planning
owner: advanced-planner
created: 2026-05-29
---

# UI/UX Contract And Visual Validation Criteria

## Visual System Lock

Reference surfaces:

- `/` Assistant dashboard normal and edit mode.
- `/manage` operational dashboard.
- `/agents` and `/agents/{agentId}`.
- Work Area file explorer/viewer fragments.
- SimplyPages editing demo at `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`.

Required style:

- Dense operational UI.
- Compact blue-gray bordered panels, thin borders, low shadow, small radii.
- Icon buttons for common commands with accessible labels/tooltips.
- Semantic chips for source/binding/status.
- Row/list/table surfaces for scan-heavy data.
- Bounded widget bodies with useful summaries and scroll-safe detail surfaces.
- No hero/landing/product-card styling.
- No nested cards inside cards for list rows.

## Summary Widget Contract

Each summary card must show:

- title and optional source/binding chip;
- 1-3 high-value metrics or statuses;
- bounded primary list/calendar/progress view;
- quick capture/action where appropriate;
- open detail affordance;
- settings affordance;
- clear empty/missing-binding/error state.

Summary cards must not be giant forms. Detailed CRUD belongs in detail surfaces.

## Detail Surface Contract

- Use modal or drawer with scroll-safe body.
- Use HTMX tabs/fragments for list/filter/edit panels.
- Preserve a single modal host; avoid duplicate ids.
- Mobile must stack controls and keep primary actions reachable.
- Forms must show validation errors as HTML fragments, not raw transport errors.

## Settings Contract

- Settings modal exposes selectors for agent/project/Work Area/source mode where relevant.
- Agent-bound widgets show selected agent name/status in summary and settings.
- Project/Work Area selectors reuse existing entity selector patterns where available.
- Invalid/deleted bindings render recoverable prompts and do not break the dashboard.
- Settings save returns OOB close + refreshed summary.

## Widget-Specific UX Criteria

- Today Planner: top priorities, now/next/later, time blocks, overdue/unscheduled, restart day, quick capture, daily review.
- Tasks/Routines: filters, recurrence, chores, subtasks, skip, snooze, restart, ranges, project links, backlog cleanup.
- Calendar/Schedule: day/week/month or day/week/agenda/month with real calendar grid/agenda visual structure; task timeboxing and reminder affordances.
- Notes: source selector, quick capture, search, tag filters, last-opened note/file, Markdown view/edit for file notes.
- Projects: goals, materials, contacts, blockers, next actions, outputs, progress; do not duplicate full `/projects`.
- Habits/Trackers: period progress, non-punitive missed/skip/restart, history correction.
- Reminders/Alerts: inbox, snooze, complete, reschedule, linked source.
- Agent Status/Queue: selected-agent chip, status, queue, inbox, active/waiting, empty/no-agent states.
- Agent Outputs: source mode explicit: dashboard-wide, selected agent, selected project, selected job, selected Work Area.
- Agent Files/Notes: Work Area/file browser language, confined service routes, selected source clear.

## Playwright Scenarios

Run with app live, desktop `1440x900` and mobile `390x844`:

- `/` normal Assistant dashboard renders summary widgets, chat rail, selector, one shell/nav, no duplicate roots.
- `/dashboards/assistant?edit=true` keeps in-place layout controls compact; add row/widget, width picker, move, remove, exit edit.
- Dashboard selector switching uses `/dashboards/{id}/_page`, targets `#dashboard-home`, pushes canonical URL, leaves one shell/nav.
- Create-dashboard modal opens and validates duplicate/blank names.
- Widget settings modal exercises no binding, selected agent, missing agent, selected project, selected Work Area, and save/cancel.
- Today Planner detail with seeded data shows top priorities, now/next/later, time blocks, overdue, restart action.
- Tasks/Routines detail shows recurrence, skip/snooze, subtasks, filters, and backlog cleanup.
- Calendar views show actual calendar/agenda structure and task blocks.
- Notes widget exercises personal mode and Work Area/file-note mode including Markdown viewer/editor.
- Projects widget shows a household project with goals/materials/contacts/blockers/next actions.
- Agent Status/Queue shows selected-agent and no-agent states.
- Agent Outputs shows selected-agent and dashboard-wide modes.
- Agent Files/Notes opens confined Work Area file/note mini-view.
- `/agents` and `/agents/{agentId}` are captured for style consistency.
- Work Area file browser/viewer reused surfaces are captured when notes/files widgets use them.

## Visual Failure Examples

Validation fails if screenshots show:

- stranded columns or excessive dead zones;
- oversized editor chrome;
- card-inside-card clutter;
- calendar rendered as a plain short list;
- clipped controls or overlapping text;
- horizontal overflow on mobile;
- modals that cannot scroll;
- hidden click targets without visible affordance;
- weak hierarchy where status/source/action labels compete equally;
- dashboard ownership confused with selected agent binding;
- HTMX swaps that duplicate shell/nav/root elements.
