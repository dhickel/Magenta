## Date
2026-05-18

## Change Summary
Added targeted regression tests for public-alpha ro-17 blocker classes that previously passed the old suite: removed direct-run route binding, empty workflow submit no-op prevention, workflow XSS rendering, warm workspace-root migration preservation, and agent queue ownership.

## Files
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/regression-gap-test-patterns.md`

## Behavioral Impact
No production behavior changed. The test suite now fails if public plan direct-run routes reappear, empty workflow submit paths enqueue workflow assignments, workflow node payloads render as executable DOM, legacy workspace-root migration loses root fields or leases, or cross-agent assignment cancellation mutates the owner queue.

## Risks
The new Spring route test asserts `POST /api/plans/{planId}/runs` remains unbound with 405; intentional future API changes to that route must update the direct-run contract test. The workflow XSS regression is a focused server-rendered DOM check and complements, rather than replaces, browser-origin Playwright XSS proof.

## Follow-up Items
Parent validation should review and commit this subplan. Playwright execution remains governed by the validation-subagent policy when live browser proof is requested.
