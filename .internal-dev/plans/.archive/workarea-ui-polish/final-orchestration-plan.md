# Final Orchestration Plan

## Sequence

1. Main thread dispatches one implementation worker with `worker-directive.md`.
2. Worker implements the scoped UI/editor remediation, updates focused tests, docs, and `.internal-dev` changelog.
3. Worker runs required automated validation or records exact blockers.
4. Main thread dispatches one validator with `validation-checklist.md`.
5. Validator performs code-level review and automated evidence review.
6. If code-level validation passes, validator defines the concrete Playwright checklist from `validation-checklist.md`; main thread dispatches a separate browser validation agent.
7. Browser results return to the validator. Validator reconciles screenshots, console/network logs, observed UI quality, and automated evidence before final pass/fail.
8. If validation fails, route remediation by failure type:
   - non-trivial code/docs/evidence issues to a fresh scoped repair worker;
   - browser harness issues to browser harness repair first;
   - ambiguous criteria back to planning;
   - same targeted issue failing twice to a fresh scoped `gpt-5.5` high repair worker.
9. After pass, main thread handles repo closeout: move the finalized plan directory to the sibling archive area under `.internal-dev/plans/.archive/`, ensure docs/changelog/spec impact are complete, commit implementation plus `.internal-dev` updates, and report results.

## Required Evidence

The closeout should include:

- command results for `git diff --check`, focused tests, `mvn test`, and bounded startup;
- browser artifact directory with desktop/mobile screenshots;
- visual critique summary covering overflow, clipping, density, collapsed inspector, row action access, modal stability, and markdown spacing;
- TOOLING_CONSTRAINT entries for unavailable requested models/tools;
- residual risks and any user-approved blockers.

For this small plan, a full medium-plan evidence index is optional. If browser/tooling evidence spans multiple agents or reruns, create `artifacts/workarea-ui-polish/validation-summary.json` as the canonical evidence index.

## Closeout Gates

- Relevant docs updated:
  - `docs/end-user/avatar-dashboard.md`
  - `docs/end-user/projects-and-workspaces.md`
  - `docs/technical/avatar-dashboard-fragments.md`
  - `docs/technical/workspaces-tools-outputs.md`
- Specifications updated only if contracts materially shift; otherwise changelog records `Specification Impact: none`.
- `.internal-dev/changelogs/2026-05-27-workarea-ui-polish.md` or equivalent exists.
- No active bug report is required unless implementation discovers an out-of-scope defect; any created bug must be mirrored to GitHub if this repo has a GitHub remote.
- Finalized plan is archived.
- Git commit includes implementation, tests, docs, and `.internal-dev` closeout unless the user explicitly says not to commit.
- Main thread sends a well formatted HTML/plain work summary email to Dwight after execution and validation complete. This is an out-of-band main-thread responsibility through `email-followup-wait`, not worker-owned.

## Handoff Expectations

The implementation worker should not need to synthesize requirements from multiple external messages. Use `worker-directive.md` as the direct prompt, with `00-specification-lock.md`, `UI-standards.md`, and the listed source contracts as supporting material.

The validator should not approve until browser proof has been reconciled. Automated tests alone are insufficient for this UI remediation.
