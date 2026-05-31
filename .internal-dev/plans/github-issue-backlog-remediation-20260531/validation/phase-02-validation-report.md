# Scope

Independent validation for Phase 02 only: GitHub issue #10 WorkflowRepository migration error handling.

Validated directive:

- `.internal-dev/plans/github-issue-backlog-remediation-20260531/worker-directives/phase-02-workflow-migration-errors.md`

Validated changed phase files:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepositoryTest.java`
- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/changelogs/2026-05-31-workflow-migration-errors.md`

Pre-existing unrelated dirty files noted and ignored unless they affected phase scope:

- `.gitignore`
- `AGENTS.md`
- `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md`

# Criteria Checked

| Criterion | Result | Evidence |
| --- | --- | --- |
| Actual phase diff inspected | PASS | Reviewed `WorkflowRepository`, `WorkflowRepositoryTest`, schema/architecture spec edits, and changelog. |
| No silent empty migration catches remain in `WorkflowRepository` | PASS | `rg` found no empty ignored catch blocks in `WorkflowRepository`; migration `DataAccessException` paths now either skip confirmed existing columns or throw contextual `IllegalStateException`. |
| Fresh schema path passes | PASS | `createsFreshWorkflowSchema` covers new in-memory schema creation and expected tables/columns; focused Maven test passed. |
| Warm schema path passes | PASS | `currentWorkflowSchemaWarmStartIsIdempotent` constructs repository twice on the same connection and persists/reads a workflow; focused Maven test passed. |
| Unexpected migration failures are visible with useful context | PASS | `unexpectedDefinitionMigrationFailureIsVisible` forces an `ALTER TABLE workflow_definitions add column routes_json` failure and asserts `IllegalStateException` with `workflow_definitions.routes_json` context and original `DataAccessResourceFailureException` root cause. Code inspection also confirms schema inspection failures throw `IllegalStateException` naming the table and column. |
| Idempotent duplicate-column behavior is safe without swallowing unexpected failures | PASS | `addColumnIfMissing` pre-checks via `hasColumn`, retries `hasColumn` only after an alter failure to tolerate a concurrently/apparently already-applied column, and otherwise throws. There is no broad log-and-continue path for unexpected `DataAccessException`. |
| `hasColumn` table-name interpolation posture | PASS with residual risk | Current helper is private and all production call sites pass internal literal table and column names from `ensureTables`; no user input reaches `pragma_table_info('...')` or generated `ALTER TABLE`. A whitelist or enum-like migration record would be a reasonable hardening follow-up if future code makes the helper reusable or accepts dynamic identifiers, but it is not required remediation for this phase. |
| Docs/spec/changelog updates appropriate and not overbroad | PASS | `schema.md` adds one compact workflow inline migration entry; `architecture.md` updates the existing orchestration hardening/drift rows without introducing formal migration tooling; changelog has required headings and accurately records behavior, validation, and residual inline-migration risk. |
| No unrelated workflow route/runtime changes | PASS | Diff is limited to repository migration handling/tests and required `.internal-dev` closeout docs. |
| No broad schema versioning dependency introduced | PASS | No Flyway/Liquibase or new migration framework added. |
| No suppression of `DataAccessException` without context | PASS | The only caught `DataAccessException` paths either throw contextual `IllegalStateException` or log the already-applied-column race/idempotency case with table/column context. |

# Commands Run

```bash
find .internal-dev/knowledge -maxdepth 1 -type f -printf '%f\n' | sort
sed -n '1,240p' .internal-dev/plans/github-issue-backlog-remediation-20260531/worker-directives/phase-02-workflow-migration-errors.md
sed -n '1,220p' .internal-dev/AGENTS.md
sed -n '1,220p' .internal-dev/specifications/AGENTS.md
sed -n '1,220p' .internal-dev/knowledge/workflow-route-model.md
sed -n '1,120p' .internal-dev/specifications/schema.md
sed -n '1,80p' .internal-dev/specifications/architecture.md
find src/main/java/io/mindspice/magenta2 -path '*/AGENTS.md' -print
sed -n '1,220p' src/main/java/io/mindspice/magenta2/AGENTS.md
sed -n '1,240p' src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md
git status --short
git diff --stat
git diff -- src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepositoryTest.java .internal-dev/specifications/schema.md .internal-dev/specifications/architecture.md .internal-dev/changelogs/2026-05-31-workflow-migration-errors.md
nl -ba src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java | sed -n '1,260p'
nl -ba src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepositoryTest.java | sed -n '1,260p'
sed -n '1,220p' .internal-dev/changelogs/2026-05-31-workflow-migration-errors.md
rg -n "catch \([^)]*\) \{\s*\}|ignored|addColumnIfMissing\(|hasColumn\(|pragma_table_info|alter table" src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepositoryTest.java
git diff --check
mvn -q -Dtest=WorkflowRepositoryTest test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Command results:

- `git diff --check`: PASS, no whitespace errors.
- `mvn -q -Dtest=WorkflowRepositoryTest test`: PASS. Output contained JVM/sqlite warnings only.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: PASS for bounded startup. Application reached `Started Magenta2Application` on port `36211`; command exited `124` after timeout stopped the running server.

# Evidence Reviewed

- Directive acceptance criteria, negative checks, validation commands, and supporting docs list.
- `.internal-dev/AGENTS.md` and `.internal-dev/specifications/AGENTS.md` governance.
- Package governance for `io.mindspice.magenta2` and `io.mindspice.magenta2.ai.orchestration`.
- `.internal-dev/knowledge/workflow-route-model.md`.
- Actual implementation diff and current code around `WorkflowRepository.ensureTables()`, `addColumnIfMissing()`, and `hasColumn()`.
- Test coverage added to `WorkflowRepositoryTest`.
- Spec and changelog edits.

# Browser Proof Status

Not applicable. Phase 02 is repository migration/startup behavior with no web or UI surface changes.

# Findings

No blocking findings.

Observation: `hasColumn` interpolates the table name into SQLite `pragma_table_info`, but the helper is private and current production call sites pass only internal string literals from `ensureTables`. This is acceptable for Phase 02 and does not reintroduce a user-controlled identifier path. Future reuse should add a whitelist or typed migration records before accepting dynamic table or column names.

# Required Remediation

None.

# Residual Risk

- Inline SQLite compatibility migrations remain in use by plan directive; formal migration tooling remains explicitly out of scope.
- The forced failure test covers an `ALTER TABLE` failure path. Schema inspection failures are visible by code inspection through contextual `IllegalStateException`, but not separately exercised by a dedicated test.
- The private migration helper should not be generalized to dynamic identifiers without adding identifier allowlisting.

# Pass/Fail

PASS.
