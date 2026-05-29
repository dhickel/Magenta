---
schema_version: 1
document_type: worker-directive
status: planning
phase: 02
role: personal-planning-core
worker_model: gpt-5.5
worker_reasoning: high
validator_model: gpt-5.5
validator_reasoning: xhigh
---

# Phase 02 Personal Planning Core Directive

## Objective

Implement Today Planner, Tasks/Routines, and Calendar/Schedule widgets on the Phase 01 platform, with service-owned day map, recurrence/projection, time blocks, in-dashboard reminders, and non-punitive skip/snooze/restart behavior.

## Editable Targets

- Planner/avatar service/repository classes under `src/main/java/io/mindspice/magenta2/avatar/**`.
- Dashboard widget renderers/routes under `avatar/dashboard` and `api/web`.
- `avatar-schema.sql` for additive planner/reminder/timeblock/habit prerequisites.
- `AvatarAssistantTools` and `AvatarAssistantToolService` only for planner tools needed by these widgets.
- CSS/JS only where narrow calendar/timeblock behavior is justified.
- Tests/docs/specs for planner/calendar routes and behavior.

## Forbidden Scope

- No external notification delivery.
- No automatic assignment creation from planner tasks.
- No third-party calendar JS library without main-thread approval after analysis.

## Implementation Steps

1. Build planner read model that unifies current todos/daily tasks/planner tasks/calendar items behind Today/Tasks/Calendar services.
2. Add or adapt tables for day maps, time blocks, reminder records, recurrence occurrence status, skip/snooze/restart metadata.
3. Implement Today Planner summary/detail/settings: top priorities, now/next/later, overdue, unscheduled, time blocks, quick capture, restart day, daily review.
4. Implement Tasks/Routines summary/detail/settings: filters, recurrence, ranges, subtasks, project links, status, skip/snooze/restart.
5. Implement Calendar/Schedule summary/detail/settings: day/week/month or day/week/agenda/month views, recurrence projection, task timeboxing, reminder affordances.
6. Extend tools with compact service-backed planner/calendar/reminder read/mutation methods and registry descriptors.
7. Update docs/specs for accepted in-dashboard reminder boundary.

## Acceptance Criteria

- Calendar visibly renders calendar/agenda structure, not a renamed list.
- Due date, scheduled time block, reminder, and recurrence are distinct in services and UI.
- Missed recurring tasks can be skipped/snoozed/restarted without broken state.
- Today Planner is useful from summary and rich in detail modal.
- Tool names are registered and tested.

## Validation Commands

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest,AvatarToolsTest,ChatToolRegistryTest test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Browser Checklist

Seed planner data. Capture Today Planner detail, Tasks/Routines detail, Calendar views, quick capture, skip/snooze/restart, mobile modal scrolling, HTMX fragment refresh boundaries.

## Stop Conditions

Stop if recurrence migration risks data loss. Stop if calendar library adoption seems necessary. Stop if external reminders become necessary to satisfy acceptance.

## Do Not Close Unless

- Service/repository/controller/tool tests pass.
- Browser screenshots prove usable planner/calendar UX.
- Specs/docs/changelog updates are ready for phase commit.
