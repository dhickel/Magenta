# Inbox Persistence Is Split Across Two Tables

## Summary

Workflow/user inbox and runtime/agent inbox use separate tables with split schema ownership.

## Scope

Inbox persistence schema and repositories.

## Reproduction

1. Inspect clean or warm DB tables.
2. Observe both `inbox_messages` and `agent_inbox_messages`.

## Expected

Inbox message ownership and surfaces should be explicit and unified where user/operator history expects a single message model.

## Actual

`schema.sql` and workflow repository own `inbox_messages`, while runtime repository creates/uses `agent_inbox_messages` absent from `schema.sql`.

## Evidence

- `schema.sql:298` declares `inbox_messages`.
- `WorkflowRepository.java:108` owns `inbox_messages`.
- `OrchestrationRuntimeRepository.java:584` uses `agent_inbox_messages`.
- `OrchestrationRuntimeRepository.java:1031` creates `agent_inbox_messages`.
- DB probe showed both tables in clean/warm DBs.

## Impact

Medium: operator-visible message history can split by surface and schema ownership remains unclear.

## Status

Open.

## Next Action

Document or unify inbox table responsibilities and add migration/schema tests for both surfaces.
