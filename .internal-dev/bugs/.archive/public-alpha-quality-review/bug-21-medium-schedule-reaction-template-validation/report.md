# Schedule and Reaction Assignment Templates Are Not Validated at Save

## Summary

Schedule and reaction saves validate timing/action basics but not assignment template types; invalid persisted data fails later at runtime.

## Scope

Schedules, event reactions, and assignment template parsing.

## Reproduction

1. Save a schedule or reaction with an invalid assignment type in the template.
2. Let polling/event handling process it.

## Expected

Invalid assignment templates are rejected at save time.

## Actual

Runtime later parses assignment type with `AssignmentType.valueOf(...)`.

## Evidence

- `ScheduleService.java:64` validates cron/timezone but not assignment type.
- `ScheduleService.java:141` parses assignment type later.
- `EventReactionService.java:35` validates event/action only.
- `OrchestrationEventService.java:91` parses assignment type when handling events.

## Impact

Medium: bad persisted operator/API data can repeatedly fail scheduled/event processing.

## Status

Open.

## Next Action

Validate assignment templates at save time and add tests for invalid assignment type rejection.
