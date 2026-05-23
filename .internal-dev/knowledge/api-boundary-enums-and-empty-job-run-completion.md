---
schema_version: 1
document_type: knowledge
date: 2026-05-23
owner: unassigned
status: active
---

# API Boundary Enums And Empty Job Run Completion

## Context

Two issue fixes exposed reusable boundary rules:

- API-facing enum fields should not rely on Jackson's default exact enum-name binding when clients reasonably send lowercase or mixed-case wire values.
- Assignment-owned job execution must leave durable run rows terminal even when there are no child work items.

## Pattern

For request enum fields, add a narrow `@JsonCreator` normalizer on the enum when the wire contract should be case-insensitive. Accept only known values after trimming, keep truly optional null values null, and reject explicit blank or unknown strings. Pair it with `@JsonValue` when response serialization should remain stable.

For job runs, do not make child item updates the only successful terminal path. Empty jobs have no item update that can calculate an aggregate terminal status, so the runner needs an explicit successful run-completion service method.

## Reuse Guidance

- Add tests for lowercase, mixed-case, unknown, and blank enum values when changing API enum binding.
- Keep empty submitted jobs as truthful no-op executions unless product direction changes to reject them at submission boundaries.
- If product direction changes to reject empty job runs, update public job submission validation and docs deliberately rather than hiding that policy in the runner.
- Run lifecycle tests at both the service level and the assignment-runner integration level when terminal state spans multiple records.
