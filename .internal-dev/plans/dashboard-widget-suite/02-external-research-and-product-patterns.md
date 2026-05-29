---
schema_version: 1
document_type: external-research
status: planning
owner: advanced-planner
created: 2026-05-29
---

# External Research And Product Patterns

Sources were restricted to official/vendor/help sources where possible and are used for product pattern grounding, not as implementation dependencies.

## ADHD-Friendly Planning

- CHADD's to-do list guidance emphasizes actionable, specific tasks, breaking projects into smaller tasks, daily/weekly/monthly list horizons, and choosing a top three for the day. Source: https://chadd.org/adhd-weekly/the-art-of-the-to-do-list/
- Planning implication: Today Planner must prioritize a small daily focus set, show overflow/backlog separately, and support project task breakdown instead of dumping whole projects into the day.
- Planning implication: "restart day" and "move forward" actions are first-class, because a day planner for ADHD should help recover from missed plans rather than punish missed tasks.

## Task, Reminder, And Recurrence Products

- Todoist positions quick capture, recurring due dates, reminders, projects, priorities, labels, subtasks, Today, Upcoming, filters, list/calendar/board views, and progress history as core task behavior. Source: https://www.todoist.com/features
- Todoist reminders distinguish automatic, custom, recurring, and location reminders. Source: https://get.todoist.help/hc/en-us/articles/205348301-Introduction-to-reminders
- Microsoft To Do exposes due dates, reminders, and repeats as basic task behavior. Source: https://support.microsoft.com/en-us/office/add-due-dates-and-reminders-in-microsoft-to-do-064d9696-08d1-4433-bfdd-f661dc97491f
- Planning implication: Magenta should separate due date, scheduled block, reminder, and recurrence rule. Conflating these will block calendar/timeboxing and reminder UX.

## Daily Planning And Timeboxing

- Sunsama's timeboxing guide schedules tasks directly onto a calendar, supports internal vs external calendars, planned task duration, reminder settings, and calendar routing. Source: https://help.sunsama.com/docs/usage-guides/timeboxing/
- Sunsama usage guide navigation exposes daily planning, Today view, focus mode, planned/actual times, recurring tasks, backlog, and weekly objectives as distinct concepts. Source: https://help.sunsama.com/docs/usage-guides/
- Planning implication: Today Planner should support now/next/later plus optional time blocks; Calendar/Schedule should render task blocks as blocks, not only event rows.

## Habit And Progress Products

- Amazing Marvin's feature list calls out daily planning, weekly planning, time estimates, timers, focus mode, recurring tasks, habit tracking, anti-overwhelm, task breakdown, dopamine menu, and procrastination tools. Source: https://amazingmarvin.com/features/
- Amazing Marvin habits distinguish habits from recurring tasks, support build vs quit, periods, targets, optional time ranges, day view display, calendar display, ask-me quantities, history edits, stats, trends, streaks, and progress bars. Source: https://help.amazingmarvin.com/en/articles/4835241-habits
- Planning implication: Magenta habits/trackers should be non-punitive and should support period targets and history correction. Streaks may be displayed, but not as the primary success/failure model.

## Docs, Databases, And Calendar Links

- Notion recurring database templates show the value of creating recurring task/doc entries from a template and separating repeat configuration from the generated item. Source: https://www.notion.com/en-gb/help/guides/automate-work-repeating-database-templates
- Notion Calendar onboarding reinforces linked docs/calendar workflows and schedule context. Source: https://www.notion.com/en-gb/help/guides/getting-started-with-notion-calendar
- Planning implication: Household project content should use typed artifacts/templates under project/Work Area roots, not force all project data into runtime project records.

## Calendar Rendering Decision

- This suite should start with server-rendered HTMX calendar/agenda views because Magenta's UI stack and validation policy favor SimplyPages/HTMX. A third-party browser calendar library is allowed only if Phase 03 proves server-rendered day/week/month interactions cannot meet visual and interaction criteria without disproportionate custom JS.
- If a library is proposed, the worker must return to the main thread with license, accessibility, bundle, CSS isolation, HTMX integration, mobile behavior, and maintenance analysis before adoption.

## Product Pattern Locks

- Capture must be fast and low-friction.
- Daily planning must limit focus and separate backlog from today.
- Recurrence, reminder, and schedule blocks are separate concepts.
- Projects need next actions and materials/blockers, not only names.
- Notes need source modes: personal DB notes and file-backed Work Area/project/agent notes.
- Calendar must visually read as calendar/agenda.
- Progress and habits must support missed/skip/restart/history correction.
