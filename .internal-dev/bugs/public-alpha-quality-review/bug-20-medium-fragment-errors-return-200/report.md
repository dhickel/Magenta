# HTMX Fragment Errors Often Return 200 OK

## Summary

Several HTMX fragment handlers catch broad exceptions and return an error fragment with normal HTTP success status.

## Scope

Operational UI mutation fragments and error handling.

## Reproduction

1. Trigger shell exec, hard delete, settings save, or queue delete failure.
2. Inspect HTTP status.

## Expected

Failed mutations should use appropriate HTTP error semantics and structured logging/audit where applicable.

## Actual

Handlers often return `200 OK` with embedded `.orch-error` content.

## Evidence

- `OrchestrationController.java:5767` shell exec catches all exceptions and returns error fragment.
- `OrchestrationController.java:6183` hard delete catches exceptions and returns fragment.
- `OrchestrationController.java:6550` settings save catches exceptions and returns fragment.
- `OrchestrationController.java:5007` queue delete returns normal list fragment with embedded message.

## Impact

Medium: browser/automation can see success responses for failed mutations, and central error logging/handlers are bypassed.

## Status

Open.

## Next Action

Return meaningful non-2xx statuses for failed mutations while still rendering HTMX-friendly error fragments.
