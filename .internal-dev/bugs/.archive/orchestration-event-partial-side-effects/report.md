# Summary

Event reaction handling can commit partial side effects and leave an event unhandled if a later reaction fails.

# Scope

Confirmed in `OrchestrationEventService.publish()` and `handle()`.

# Reproduction

Configure multiple enabled reactions for the same event type where an earlier reaction creates an assignment and a later reaction throws during assignment creation.

# Expected

Event persistence, reaction assignment creation, and `handledAt` update should commit or roll back as one unit.

# Actual

Earlier reaction side effects can commit before the event is marked handled.

# Evidence

`publish()` saves the event, calls `handle()`, and `handle()` creates assignments before saving the event with `handledAt`. No transactional boundary is present.

# Impact

The runtime can create duplicate or orphaned assignments during retries or manual recovery because the event appears unhandled while some reaction side effects already exist.

# Status

Fixed in this pass.

# Next Action

Archived after adding transaction boundaries around event publish/reaction handling.
