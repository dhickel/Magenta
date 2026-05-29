---
schema_version: 1
document_type: worker-directive
status: planning
phase: 05
role: tracking-alerts-context
worker_model: gpt-5.5
worker_reasoning: high
validator_model: gpt-5.5
validator_reasoning: xhigh
---

# Phase 05 Tracking, Alerts, And Context Directive

## Objective

Implement Habits/Trackers, Reminders/Alerts, and read-only Dashboard Context Panel on top of the planner/widget platform.

## Editable Targets

- Avatar planner/habit/reminder services/repositories/schema.
- Dashboard widget renderers/routes/settings.
- Tool classes/services for habit/reminder operations if accepted in descriptors.
- Docs/specs for dashboard-aware context and reminder boundary.

## Forbidden Scope

- No external push/email/PWA notifications.
- No dashboard chat action tools beyond existing approved tools.
- No automatic assignment creation from reminders/habits.

## Implementation Steps

1. Add habit/tracker domain with build/quit, period, target quantity/unit, display days/time range, logs/history correction, archived state.
2. Implement Habits/Trackers summary/detail/settings with non-punitive progress/trend chips and optional streaks.
3. Implement Reminders/Alerts in-dashboard inbox with snooze, complete, reschedule, linked source, and alert summary.
4. Add Dashboard Context Panel as read-only summary of selected dashboard/widget state and visible tool contracts.
5. Add tool descriptors/static tools for habits/reminders if needed, with compact JSON and service validation.
6. Update deferred specs to clarify external notifications remain deferred.

## Acceptance Criteria

- Habits support period targets and history correction.
- Reminders are useful in-dashboard and do not imply external delivery.
- Context panel is read-only and clearly separate from action tools.
- Missed habits/reminders can be skipped/restarted/snoozed without punitive UX.

## Validation Commands

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest,AvatarToolsTest,ChatToolRegistryTest test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Browser Checklist

Habits progress/detail/history correction, reminders inbox/snooze/reschedule/complete, context panel read-only display, mobile modal safety, visual critique.

## Stop Conditions

Stop if user-facing acceptance requires external notifications. Stop if context panel starts implying unapproved chat/tool behavior.

## Do Not Close Unless

- Reminder boundary is documented.
- Habit UX is visibly non-punitive.
- Tool descriptors and registry stay consistent.
