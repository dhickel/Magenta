# Phase 02 Worker Directive: Workflow Migration Error Handling (#10)

## Objective

Remediate GitHub issue #10 by replacing silent migration exception swallows in `WorkflowRepository` with explicit idempotent handling and visible failure for unexpected migration errors.

## User-Visible Outcome

Workflow schema drift or migration failures no longer disappear silently; expected already-existing columns remain harmless.

## Issues

- #10 `[CRITICAL] 25+ silent exception swallows in WorkflowRepository schema migrations`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepositoryTest.java`
- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/architecture.md` if migration posture changes
- `.internal-dev/changelogs/2026-05-31-workflow-migration-errors.md`

## Forbidden Scope

- Do not add Flyway/Liquibase without stopping for user approval.
- Do not rewrite workflow persistence broadly.
- Do not log-and-continue unexpected migration failures if they can leave schema unusable.

## Supporting Docs To Read

- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/knowledge/workflow-route-model.md`

## Implementation Steps

1. Run git status and preserve unrelated work.
2. Add a small helper for `ALTER TABLE ... ADD COLUMN` migration that:
   - Checks existing columns before altering where practical, or catches only the known duplicate-column/idempotent condition.
   - Logs a clear warning or throws an `IllegalStateException` for unexpected failures.
3. Replace every `catch (Exception ignored) { }` in `WorkflowRepository.ensureTables()`.
4. Add tests for:
   - Fresh schema creation.
   - Warm schema creation with existing columns.
   - Simulated unexpected migration failure, if practical through a test double or malformed fixture, proving it is visible.
5. Update schema/architecture docs only if the intended migration policy is clarified.

## Senior-Engineer Guidance

- The core invariant is visibility: unexpected migration failure must not produce a partially migrated repository that later fails cryptically.
- SQLite duplicate-column messages vary; prefer pre-checking `pragma_table_info` over parsing exceptions when possible.
- Keep helper private and boring; this is not a migration framework phase.

## Acceptance Criteria

- No silent empty catch remains for workflow schema migration statements.
- Expected idempotent warm-start migrations pass.
- Unexpected migration failures are logged with context and/or fail startup/repository construction visibly.
- Focused tests cover the migration helper behavior.

## Negative Checks

- No unrelated workflow route/runtime changes.
- No broad schema versioning dependency.
- No suppression of `DataAccessException` without context.

## Validation Commands

- `mvn -q -Dtest=WorkflowRepositoryTest test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Evidence Expectations

- Worker summary lists the previous silent catch sites and replacement policy.
- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-02-validation-report.md`

## Closeout Expectations

Main thread closes #10 only after validation, commit, push, and email report.

## Stop Conditions

- Stop if current SQLite/Spring behavior makes robust idempotent detection impossible without a migration framework.

## Do Not Close Unless

- Empty migration catch blocks are gone from `WorkflowRepository`.
- Tests prove fresh and warm schema paths still work.
