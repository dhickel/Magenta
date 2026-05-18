# Date

2026-05-18

# Change Summary

Completed domain 05 schema/data ownership remediation. Clean SQLite schema now reflects current workspace lease ownership, plan/output persistence columns, explicit inbox table ownership, and owned job tables without the orphan `job_work_items` table. Warm workspace-root migration now preserves active lease rows instead of dropping lease state.

# Files

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/inbox-table-ownership.md`

# Behavioral Impact

Clean startup no longer recreates deprecated `workspace_roots`, and `workspace_leases` references `workspaces(id)`. Warm startup migrates legacy workspace roots while preserving active, release-requested, and released leases. Clean schema includes plan run temp workspace paths, run output attribution columns/indexes, both intentional inbox tables, and current owned job tables.

# Validation

- Focused domain tests passed with 75 tests: `/tmp/domain05-validation-9560d66/focused-tests.log`.
- Full `mvn test` passed with 530 tests: `/tmp/domain05-validation-9560d66/full-mvn-test.log`.
- `git diff --check` passed: `/tmp/domain05-validation-9560d66/git-diff-check.log`.
- Clean schema probe passed: `/tmp/domain05-validation-9560d66/clean-schema-probe.log`.
- Clean bounded startup and post-startup DB probe passed: `/tmp/domain05-validation-9560d66/clean-startup.log`, `/tmp/domain05-validation-9560d66/clean-startup-summary.log`.
- Warm legacy startup preserved lease rows and migrated root ownership: `/tmp/domain05-validation-9560d66/warm-legacy-seed-probe.log`, `/tmp/domain05-validation-9560d66/warm-legacy-startup.log`, `/tmp/domain05-validation-9560d66/warm-legacy-summary.log`.
- Current-FK warm DB with stale roots preserved leases: `/tmp/domain05-validation-9560d66/warm-current-fk-stale-roots-summary.log`.
- Inbox ownership and orphan schema scans passed: `/tmp/domain05-validation-9560d66/inbox-ownership-static-scan.log`, `/tmp/domain05-validation-9560d66/job-work-items-static-scan.log`.

# Risks

Existing warm local databases that already contain unused `job_work_items` are left untouched because no current code owns or reads that table and no destructive cleanup migration was approved.

# Follow-up Items

None for domain 05.
