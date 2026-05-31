# Phase 01 Worker Directive: SQL Identifier Hardening (#9)

## Objective

Remediate GitHub issue #9 by removing SQL injection risk from repository string-concatenated identifiers while preserving current schema compatibility behavior.

## User-Visible Outcome

Repository helpers no longer accept arbitrary column/table/type identifiers, and old injection-shaped inputs fail safely in tests.

## Issues

- #9 `[CRITICAL] SQL injection vectors via string concatenation in repositories`

## Direct Targets

Inspect/edit only as needed:

- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRepository.java`
- Existing or new focused tests under:
  - `src/test/java/io/mindspice/magenta2/ai/chat/repository/`
  - `src/test/java/io/mindspice/magenta2/ai/chat/plan/`
- Docs/specs if behavior is clarified:
  - `.internal-dev/specifications/schema.md`
  - `.internal-dev/specifications/services.md`
  - relevant `docs/technical/*` persistence docs if present
  - `.internal-dev/changelogs/2026-05-31-sql-identifier-hardening.md`

## Forbidden Scope

- Do not introduce Flyway/Liquibase.
- Do not redesign repository schema bootstrapping beyond this issue.
- Do not broaden SQL changes into unrelated repositories without filing a separate bug.

## Supporting Docs To Read

- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/knowledge/regression-gap-test-patterns.md`

## Implementation Steps

1. Run `git status --short --branch`; preserve unrelated changes.
2. Confirm the current issue locations with `rg`/line reads.
3. Add focused tests that prove unsafe identifier payloads are rejected or unreachable:
   - `ChatSessionMetadataRepository` boolean flag columns only allow known flag columns.
   - `AuditRepository` migration column/type construction is from an internal whitelist, not caller input.
   - `PlanRepository.addColumnIfMissing` only accepts known table/column DDL pairs or a strict private whitelist; avoid parameterizing SQLite pragma identifiers in an unsupported way.
4. Implement whitelists or enum/private helper records close to each repository.
5. Keep expected idempotent schema creation behavior intact.
6. Update docs/spec/changelog if the repository contract is clarified.

## Senior-Engineer Guidance

- Identifier names cannot be JDBC parameters in normal SQL positions; use private whitelists and constants.
- Regex-only validation is not enough for #9 if arbitrary valid-looking table names still flow into SQL.
- Prefer private helpers such as `requireMetadataFlagColumn(String)` returning a known constant.
- Tests should assert old payloads fail before they can alter/read unexpected schema.

## Acceptance Criteria

- No user-controlled value is concatenated into SQL identifier/type positions without whitelist validation.
- Existing metadata flag behavior still works.
- Existing schema bootstrap works on fresh and already-migrated SQLite databases.
- Focused tests cover malicious identifier payloads and normal behavior.

## Negative Checks

- No broad migration framework.
- No swallowed SQL exceptions added.
- No behavior change to chat session favorite/archive flags except safer validation.

## Validation Commands

- `mvn -q -Dtest=ChatSessionMetadataRepositoryTest,AuditRepositoryTest,PlanRepositoryTest test`
- If test names differ, run the nearest focused repository tests and record exact names.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` if Spring wiring changes.

## Evidence Expectations

- Worker summary lists changed SQL construction sites.
- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-01-validation-report.md`

## Closeout Expectations

After validation passes, main thread commits, pushes, closes #9 with commit reference, and emails the completion report.

## Stop Conditions

- Stop if remediation requires a broad schema migration framework.
- Stop if a repository has a public API accepting arbitrary identifiers for a real feature not described in #9.

## Do Not Close Unless

- #9 tests fail on old injection class or prove it unreachable.
- All changed repository behavior is covered by focused tests.
- Changelog/spec impact is recorded.
