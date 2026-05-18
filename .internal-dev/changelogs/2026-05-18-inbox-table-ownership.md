# Date

2026-05-18

# Change Summary

Made inbox persistence ownership explicit for public alpha schema remediation. Clean `schema.sql` now declares both workflow-owned `inbox_messages` and runtime-owned `agent_inbox_messages`, including ownership comments and recipient indexes. Repository warm bootstraps create the same indexes so clean and repository-created databases converge on the same target shape.

# Files

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/plans/public-alpha-remediation/05-schema-data-ownership/subplan-03-inbox-table-ownership.md`
- `.internal-dev/knowledge/inbox-table-ownership.md`

# Behavioral Impact

Workflow/user approval inbox history and runtime direct-line agent inbox history remain preserved on their existing surfaces. The split is now intentional and test-covered instead of hidden behind repository-only creation of `agent_inbox_messages`.

# Validation

- `mvn -Dtest=WorkspaceRepositorySchemaMigrationTest test` passed with 5 tests.
- `mvn -Dtest=WorkspaceRepositorySchemaMigrationTest,OrchestrationRuntimeTest,WorkflowRunnerTest,OperationalUiContractControllerTest test` passed with 57 tests.
- Full `mvn test` passed with 529 tests.
- Clean SQLite schema probe confirmed `inbox_messages`, `agent_inbox_messages`, `idx_inbox_messages_to`, and `idx_agent_inbox_messages_to`.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` on port `35861` with isolated SQLite DB `/tmp/domain05-subplan03-startup.sqlite`.
- Post-startup SQLite probe confirmed both inbox tables and recipient indexes.
- Validator confirmed workflow inbox and runtime direct-line inbox messages remain readable on distinct surfaces and cross-lookups return empty.

# Risks

The two tables are not unified. Any future product requirement for a single combined inbox timeline will still need an explicit merge/query design and migration plan.

# Follow-up Items

- Subplan 04 still owns orphan `job_work_items` cleanup.
