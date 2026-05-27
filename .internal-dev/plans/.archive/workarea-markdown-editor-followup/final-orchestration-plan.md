# Final Orchestration Plan - Work Area Markdown Editor Follow-up

## Plan Shape

Small single-agent implementation loop with one worker and one validator, followed by separate browser validation because this is a UI behavior/layout change.

## Execution Order

1. Main thread creates or confirms a dedicated branch before implementation because this is a non-trivial feature/fix loop.
2. Dispatch one implementation worker with `01-implementation-worker-directive.md`.
3. Worker implements code, tests, docs/spec/changelog, then reports changed files and validation output.
4. Dispatch one validator with `02-validation-checklist.md`.
5. If code-level validation passes, dispatch separate Playwright/browser validation using the checklist produced by the validator.
6. Return browser results to the validator for reconciliation and final pass/fail.
7. If validation fails, route remediation by failure type from `02-validation-checklist.md`.
8. After pass, perform `.internal-dev` closeout:
   - confirm changelog exists;
   - confirm spec/docs are current;
   - archive this active plan under `.internal-dev/plans/.archive/`;
   - commit implementation and `.internal-dev` updates per repo policy unless the user explicitly says not to commit.

## Handoff Expectations

Implementation worker handoff must include:

- final changed file list;
- short behavior summary;
- exact tests/commands run and results;
- startup smoke result;
- docs/spec/changelog updates;
- browser validation setup notes or blockers;
- any `TOOLING_CONSTRAINT`, including fallback from unavailable `implementation_worker_agent` to `worker` with `gpt-5.3-codex`.

Validator handoff must include:

- pass/fail against acceptance criteria;
- code/contract risks;
- command evidence reviewed;
- browser checklist and final browser reconciliation;
- stale-reference sweep result;
- required remediation target if failed.

Browser validation handoff must include:

- desktop and mobile screenshot artifact paths;
- tested route/setup;
- interaction results for edit, preview, split, save, undo/revert, and text editing;
- visual quality critique;
- console/server-log observations;
- any blocked or skipped assertions.

## Stop Rules

- Stop before implementation if the worker cannot access required specs, package guides, or knowledge files.
- Stop if the solution requires broad editor dependency integration, markdown-library replacement, or asset pipeline changes; return to planning/user for scope confirmation.
- Stop if sanitization cannot be preserved for unsaved preview.
- Stop if no controlled Work Area markdown/text validation fixture can be used for browser proof.
- Stop if startup or browser validation is blocked by missing local dependencies; report exact blocker and wait for user approval before treating work as complete.
- Stop if fallback tooling cannot produce valid implementation or validation evidence.

## Main Thread Notes

- Email/remote-work status checks remain out-of-band main-thread responsibility. Do not encode mail wait mechanics into the worker directive.
- The previous email instruction says to execute after planning; this plan only authorizes the next implementation loop after the main thread accepts or proceeds with this small handoff.
- Because browser validation applies, final sign-off is not valid until the validator reconciles Playwright evidence with screenshots and logs.
