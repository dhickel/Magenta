# Date

2026-05-18

# Change Summary

Updated clean `schema.sql` to include the current plan run temp workspace path, run output attribution columns, and run output attribution indexes already expected by repository bootstrap. Added a focused clean SQLite schema drift test that applies `schema.sql` before repository construction and confirms the target plan/output shapes remain stable after `PlanRepository` and `WorkspaceRepository` initialization.

# Files

- `src/main/resources/schema.sql`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/plans/public-alpha-remediation/05-schema-data-ownership/subplan-02-canonical-schema-drift.md`

# Behavioral Impact

Clean SQLite databases now start with the same key plan/output schema shape that repositories expect, without relying on repository construction order to patch those columns or indexes. Existing warm DB add-column migrations remain in place.

# Validation

- `mvn -Dtest=WorkspaceRepositorySchemaMigrationTest,WorkspaceRepositoryAttributionTest,PlanRepositoryTest test` passed with 16 tests.
- Clean SQLite schema probe confirmed `plan_runs.temp_workspace_path`, `run_output_artifacts.agent_id`, `job_id`, `project_id`, `workspace_id`, `run_type`, and output attribution indexes exist after applying `schema.sql`.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` on port `42715` with isolated SQLite DB `/tmp/domain05-subplan02-startup.sqlite`.

# Risks

This change intentionally does not resolve inbox table ownership or orphan `job_work_items`; those remain assigned to later domain 05 subplans.

# Follow-up Items

- Run the domain validation gate and record validator evidence.
- Complete subplan 03 for inbox table ownership.
- Complete subplan 04 for orphan schema cleanup.
