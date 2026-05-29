# Date

2026-05-29

# Change Summary

Implemented Phase 02 personal planning widgets for the dashboard widget suite: Today Planner, Tasks/Routines, and Calendar/Schedule. Scoped repairs on 2026-05-29 corrected occurrence status overlays, Tasks/Routines filters, Today Planner review notes, overdue/unscheduled planner visibility, subtodo display, quick-capture confirmation, and the Calendar/Schedule reminder creation affordance.

# Files

- `src/main/java/io/mindspice/magenta2/avatar/**`: added planner read-model records and service/repository behavior for day maps, time blocks, in-dashboard reminders, occurrence status, filtered Tasks/Routines views, and merged Today/Tasks/Calendar views.
- `src/main/resources/avatar-schema.sql`: added additive planner day map, time block, reminder, and occurrence status tables.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`: added HTMX planner/calendar/reminder routes, filtered Tasks/Routines detail parameters, wired planner read models into dashboard data, and bumped the Avatar dashboard CSS asset version for the repaired styling.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`: added Today Planner, Tasks/Routines, and Calendar/Schedule summary/detail renderers with a real calendar grid/agenda structure, compact review notes form, overdue/unscheduled sections, subtodo rows, HTMX task filter controls, and HTMX time-block/reminder forms.
- `src/main/resources/static/css/avatar-dashboard.css`: added compact subtodo list styling inside Tasks/Routines rows.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/**`: added compact service-backed planner/calendar/reminder tools and response records.
- `src/test/java/...`: added focused repository, service, controller, and tool/registry coverage.
- `.internal-dev/specifications/*`, `docs/*`, and `artifacts/dashboard-widget-suite/validation-summary.json`: updated contracts, docs, and evidence.

# Behavioral Impact

The default Assistant dashboard now starts with Today Planner, Calendar/Schedule, Tasks/Routines, Notes, System, Alerts, and Recent Work. Calendar recurrence entries reflect per-occurrence skip/snooze/restart state without changing parent task status. Tasks/Routines detail supports server-side status/range/recurrence filters and compact subtodo rows. Today Planner exposes overdue and unscheduled work; quick-captured items appear in the unscheduled section after refresh, and review notes persist into the day map. Calendar/Schedule exposes HTMX creation for time blocks and in-dashboard reminders. Planner reminders are in-dashboard records only; no external notification delivery or automatic assignment creation was added.

# Specification Impact

Updated architecture, services, API, web, SimplyPages, and decision specs for the accepted in-dashboard reminder boundary and distinct due date/time block/reminder/recurrence contracts.

# Risks

Browser proof remains pending because the directive forbids this implementation worker from running Playwright. Detail modals currently reuse rich widget bodies rather than a separate full CRUD tab surface; browser validation should confirm the repaired overdue/unscheduled, subtodo, quick-capture, and reminder-form surfaces visually and request scoped follow-up if richer modal tabbing is needed.

# Follow-up Items

Delegate Phase 02 Playwright proof for Today Planner, Tasks/Routines, Calendar/Schedule, quick capture, skip/snooze/restart, and mobile modal scrolling.
