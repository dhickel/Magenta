# Topic

SQLite fixture foreign key parity for run artifacts and schedule/reaction tests.

# Source References

- `src/test/resources/application.yml`
- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleReactionFeatureParitySpringTest.java`
- `.internal-dev/plans/public-alpha-remediation/07-validation-harness-regression/subplan-03-fixture-parity.md`

# Key Takeaways

- Repository/service SQLite fixtures should use `jdbc:sqlite::memory:?foreign_keys=true` unless they are deliberately testing a non-SQLite or non-persistence path.
- Enabling FK enforcement is useful because it catches fixture drift and production schema mistakes that permissive in-memory SQLite hides.
- `run_output_artifacts.run_id` is polymorphic: task/plan paths use plan-run ids, while workflow final-output paths use workflow-run ids. Do not constrain it to `plan_runs(id)` unless artifact ownership is redesigned.
- Schedule/reaction tests should not rely only on the global test profile where both flags are disabled. Add focused tests that enable `magenta.features.schedules-enabled=true` and `magenta.features.reactions-enabled=true`, then manually invoke poll/publish paths for deterministic coverage.

# Engine Relevance

Validation-harness work should keep test fixtures aligned with production SQLite behavior. When FK enforcement exposes failures, prefer fixing parent fixture data or incorrect schema assumptions over disabling FK checks.

# Open Questions

- Whether future artifact ownership should split plan, task, workflow, and job run ids into explicit nullable columns instead of the current logical `run_id` plus `run_type` fields.
