# schema.sql Is Not Canonical for Current Persistence Shape

## Summary

`schema.sql` omits columns that repositories create/read and includes stale/orphan tables.

## Scope

SQLite schema and repository bootstrap.

## Reproduction

1. Compare `schema.sql` with repository DDL/queries.
2. Start clean/warm DB and inspect table columns.

## Expected

Canonical schema and repository bootstrap agree on current table shape.

## Actual

Repository construction order patches missing columns and stale tables remain.

## Evidence

- `schema.sql:91` omits `plan_runs.temp_workspace_path`.
- `PlanRepository.java:470` adds/uses `temp_workspace_path`.
- `schema.sql:455` omits `run_output_artifacts.agent_id`, `job_id`, `project_id`, `workspace_id`, and `run_type`.
- `WorkspaceRepository.java:639` expects/creates output attribution columns.
- `schema.sql:493` defines likely orphan `job_work_items`.

## Impact

High: clean DB shape differs from post-startup shape; warm migrations depend on repository order and can hide drift.

## Status

Open.

## Next Action

Update `schema.sql` to match current repository-owned schema and add a schema drift test against repository bootstrap.
