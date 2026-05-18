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

## Implementation Status

- 2026-05-18: Implemented clean `schema.sql` drift fixes for `plan_runs.temp_workspace_path`, current `run_output_artifacts` attribution columns, and attribution indexes from `WorkspaceRepository` bootstrap.
- 2026-05-18: Kept repository guarded add-column migrations in place for warm DB compatibility.
- 2026-05-18: Added a focused clean SQLite schema drift test that applies `schema.sql` before repository construction and verifies plan/output columns and output indexes remain unchanged after `PlanRepository` and `WorkspaceRepository` bootstrap.
- Pending: external validation gate and later domain 05 subplans for inbox ownership and orphan `job_work_items`.
