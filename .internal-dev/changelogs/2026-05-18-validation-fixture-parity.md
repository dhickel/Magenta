# Date

2026-05-18

# Change Summary

Implemented Domain 07 Subplan 03 fixture parity for ro-15 and ro-16.

- Enabled SQLite FK enforcement in active repository/service/controller test fixtures.
- Added a Spring-context schedule/reaction parity test with feature flags enabled.
- Fixed the production schema/repository artifact table contract so workflow output artifacts are not blocked by a plan-run-only FK.
- Updated workspace attribution fixture setup to create parent workspace rows before lease persistence.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/main/resources/schema.sql`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleReactionFeatureParitySpringTest.java`
- SQLite fixture updates across repository/service/controller tests under `src/test/java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/sqlite-fixture-foreign-key-parity.md`

# Behavioral Impact

Tests now exercise SQLite foreign key behavior closer to production. Schedule and reaction runtime/save behavior is covered by a Spring context with the production-style feature flags enabled.

`run_output_artifacts.run_id` remains an indexed logical run id, but no longer has an invalid FK to only `plan_runs(id)` because artifacts can also belong to workflow runs.

# Risks

Warm SQLite startup now includes a table recreation path for legacy `run_output_artifacts` tables that still carry the old plan-run FK. The migration preserves current artifact columns and rows.

# Follow-up Items

Parent validation should rerun the focused fixture tests and any desired full-suite pass before committing the subplan.
