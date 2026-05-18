# Date

2026-05-18

# Change Summary

Implemented public alpha remediation domain 05 subplan 01 for workspace lease preservation. Clean schema no longer creates deprecated `workspace_roots`, and `workspace_leases` now references `workspaces(id)`. Warm repository bootstrap now migrates legacy roots and preserves existing lease rows instead of dropping the lease table.

# Files

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

Warm startup with legacy `workspace_roots` and active, release-requested, or released `workspace_leases` keeps the lease rows and migrates the lease foreign key to `workspaces`. Clean startup creates only the current workspace and lease ownership shape.

# Validation

- `mvn -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspacePathSegmentValidationTest,WorkspaceRepositorySchemaMigrationTest test` passed with 27 tests.
- Full `mvn test` passed with 526 tests.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` on port `34269` with isolated SQLite DB `/tmp/magenta2-lease-schema-parent.sqlite`.
- SQLite probe on `/tmp/magenta2-lease-schema-parent.sqlite` found `workspaces` and `workspace_leases`, no `workspace_roots`, and `workspace_leases.workspace_id` referencing `workspaces(id)`.
- Validator clean/warm startup probes confirmed active, release-requested, and released lease rows survive legacy-root migration, and current-FK warm DBs keep leases intact even when stale `workspace_roots` exists.

# Risks

The broader canonical schema drift, inbox ownership, and orphan schema cleanup findings remain out of scope for this subplan and are still tracked by later domain 05 subplans.

# Follow-up Items

Run the domain validation gate for bug-07, including clean and warm startup probes, before marking the finding passed.
