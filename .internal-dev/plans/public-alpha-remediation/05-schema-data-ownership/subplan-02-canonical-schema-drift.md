# Subplan 02: Canonical Schema Drift

## Goal

Update `schema.sql` to match current repository-owned schema.

## Implementation Steps

1. Compare repository DDL/add-column paths against `schema.sql`.
2. Add missing `plan_runs`, output attribution, and execution table columns.
3. Keep guarded repository migrations only for existing warm DBs.
4. Add schema drift test.

## Validation

Clean DB shape matches repository expectations without relying on startup patch order.
