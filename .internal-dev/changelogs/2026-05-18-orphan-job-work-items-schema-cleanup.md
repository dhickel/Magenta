# Date

2026-05-18

# Change Summary

Removed the orphan `job_work_items` table from clean `schema.sql` after confirming it had no production code, repository, controller, or test owner. Current job persistence remains represented by `job_definitions`, `job_runs`, and runtime-owned `orchestration_job_items`.

# Files

- `src/main/resources/schema.sql`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/plans/public-alpha-remediation/05-schema-data-ownership/subplan-04-orphan-schema-cleanup.md`

# Behavioral Impact

Fresh SQLite databases no longer create an unexplained `job_work_items` table. Warm databases with a legacy local `job_work_items` table are left untouched because no current code reads or writes that table and no owner-approved destructive cleanup migration is needed.

# Validation

- `rg -n "job_work_items" src/main src/test .internal-dev/plans/public-alpha-remediation .internal-dev/changelogs/2026-05-18-orphan-job-work-items-schema-cleanup.md` found no production schema/code usage after cleanup; remaining hits are the intentional test and plan/changelog/history references.
- `mvn -Dtest=WorkspaceRepositorySchemaMigrationTest,OrchestrationRuntimeTest,OperationalUiContractControllerTest test` passed with 47 tests.
- Clean SQLite schema probe returned `job_definitions`, `job_runs`, and `orchestration_job_items`, with no `job_work_items`.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` on port `39667` with isolated SQLite DB `/tmp/domain05-subplan04-parent.sqlite` before the timeout stopped it.
- Post-startup SQLite probe on `/tmp/domain05-subplan04-parent.sqlite` returned `job_definitions`, `job_runs`, and `orchestration_job_items`, with no `job_work_items`.

# Risks

Local warm databases may still contain an unused legacy `job_work_items` table until a future explicit migration policy chooses to remove it.

# Follow-up Items

- None.
