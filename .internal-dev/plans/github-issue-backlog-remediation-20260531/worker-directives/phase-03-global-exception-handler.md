# Phase 03 Worker Directive: GlobalExceptionHandler Constructor Cleanup (#11)

## Objective

Remediate GitHub issue #11 by removing unsafe/null-constructor behavior from `GlobalExceptionHandler` while preserving optional audit behavior.

## User-Visible Outcome

HTTP error handling remains stable and cannot be accidentally constructed through an ambiguous no-arg path that hides missing dependencies.

## Issues

- #11 `[HIGH] GlobalExceptionHandler passes null to its own constructor`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/api/web/GlobalExceptionHandler.java`
- `src/test/java/io/mindspice/magenta2/api/web/GlobalExceptionHandlerTest.java`
- `.internal-dev/specifications/api.md` only if error response contract changes
- `.internal-dev/changelogs/2026-05-31-global-exception-handler.md`

## Forbidden Scope

- Do not redesign global error payloads.
- Do not make audit mandatory unless startup proves optional injection is no longer needed.
- Do not change status-code mappings except to fix this issue.

## Supporting Docs To Read

- `.internal-dev/specifications/api.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`

## Implementation Steps

1. Run git status and preserve unrelated work.
2. Remove the no-arg constructor or replace it with an explicit package-private/test-only constructor only if tests require it and it cannot be confused with Spring construction.
3. Keep `AuditService` optional only through one clear Spring constructor if optional audit is intended.
4. Update tests to instantiate with an explicit dependency or null-safe test factory, not the public no-arg constructor.
5. Verify all exception mappings still return the same payload/status.

## Senior-Engineer Guidance

- The bug is ambiguity and hidden null dependency. The fix should make construction intent explicit.
- `recordIfConversation` may remain null-safe if audit is intentionally optional.
- Keep controller advice simple.

## Acceptance Criteria

- No public no-arg constructor delegates to `this(null)`.
- Spring can construct the advice.
- Existing handler methods preserve response statuses and error bodies.
- Tests cover null/absent audit safety if optional audit remains supported.

## Negative Checks

- No new NPE path in exception handling.
- No API-wide response envelope refactor.

## Validation Commands

- `mvn -q -Dtest=GlobalExceptionHandlerTest test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-03-validation-report.md`

## Closeout Expectations

Main thread closes #11 after validation, commit, push, and email.

## Stop Conditions

- Stop if removing the constructor reveals an unexpected Spring bean-cycle or test-only dependency problem requiring broader web wiring changes.

## Do Not Close Unless

- Constructor ambiguity is gone.
- Focused tests and startup pass.
