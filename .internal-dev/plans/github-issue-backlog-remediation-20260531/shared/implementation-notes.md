# Shared Implementation Notes

## Repo Workflow

- Before each phase, run `git status --short --branch`.
- Preserve unrelated changes in `.gitignore`, `AGENTS.md`, and `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md`.
- Start the dedicated branch before implementation begins if the main thread has not already done so. Suggested branch: `fix/github-issue-backlog-20260531`.
- Commit after each completed issue or coherent combined fix. Keep commits rollback-friendly.

## Required Closeout Per Phase

Each worker must update the affected durable records before handing off:

- Specs in `.internal-dev/specifications/` when intended behavior changes.
- Relevant docs under `docs/` for route/API/service/UI/schema behavior changes.
- Knowledge only when a reusable lesson, corrected assumption, or validation gotcha emerges.
- Changelog under `.internal-dev/changelogs/` for finalized phase work.
- Move any finalized local bug/plan artifact only when it is actually finalized; do not archive this active plan until the whole plan is complete.

The main thread handles GitHub issue closure and email after validation/commit/push gates, not workers.

## Testing Baseline

Use focused tests first, then broader checks appropriate to the phase:

- Repository/security: focused repository tests plus `mvn -q -Dtest=<TestClass> test`.
- API/controller: focused controller tests plus relevant API docs check.
- Runtime/concurrency: focused unit/service tests with bounded timeouts; avoid flakey sleeps where latches can prove ordering.
- UI: focused controller tests, bounded startup, and delegated Playwright browser validation.
- Wiring-affecting work: bounded startup such as `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`.

## Browser Validation

For #33, and for #14/#15 if browser SSE behavior changes, use a separate Playwright/browser validation agent after code validation. Browser proof must:

- Start the app with isolated SQLite state.
- Capture desktop and mobile screenshots for changed UI surfaces.
- Include console/network review.
- Include a visual critique for UI phases.
- Return evidence to the phase validator for reconciliation.

## Stop Conditions

Stop and report to the orchestrator if:

- Required local services/secrets are unavailable and no approved stub/fallback is allowed.
- Browser validation is blocked after recovery steps.
- A phase requires a broader architecture/product decision than the issue scope.
- The worker discovers the issue is already fixed; provide evidence and closeout-only recommendation instead of changing product code.

## Deferred Dashboard Issue

#8 is intentionally skipped and left open by user direction. Do not implement dashboard empty-row/density remediation in this plan. Dashboard/static work is allowed only where required by #33 SlotKey/package-guide enforcement.
