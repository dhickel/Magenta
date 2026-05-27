# Work Area UI Consistency Repair Final Orchestration Plan

## Scope

Small single-agent implementation plan for branch `workarea-ui-consistency-repair`.

Main thread remains coordinator-only. Do not implement product code on the main thread.

## Dispatch Order

1. Dispatch one implementation worker with `worker-directives/phase-01-workarea-ui-repair.md`.
2. Require the worker to complete internal checkpoints in this order:
   - browser/inspector;
   - tag modal;
   - editor modal;
   - closeout updates and code-level validation.
3. Review worker output and command results against `00-specification-lock.md` and `shared/validation-matrix.md`.
4. Dispatch a separate validation/browser route for Playwright screenshots and visual critique after implementation. Do not run browser proof inline on the main implementation thread.
5. Reconcile browser findings with the same validation criteria. If failures are product defects, use a scoped repair worker with the selected worker model. If failures are browser harness defects, repair evidence/harness first.

## Model And Tooling Gates

- Planning requested: `gpt-5.5-high`.
- Implementation worker: `gpt-5.4-high`.
- Browser/validation: default browser proof should use `gpt-5.4` medium. The earlier lower-version wording was an erroneous local-default reference and should not be reused.
- Do not instruct hidden model fallback.

## Validation Gate

Code-level validation must include at minimum:

- `mvn -Dtest=AvatarDashboardControllerTest test`
- `mvn -Dtest=WorkAreaExplorerServiceTest test` if service/tag behavior changed
- Bounded startup smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

Playwright validation must include:

- Screenshots and visual critique of `/dashboard`, `/agents`, and `/avatar` as style references.
- Desktop and mobile Work Area explorer.
- Collapsed and expanded inspector.
- Long filename/path selection and overflow checks.
- Visible tag manager button.
- Tag modal filter, scroll body, row UI, focused edit, type constraints, no deletion, and topnav overlay.
- Markdown/text editor Edit/Preview/Split stability, bounded panes, resize affordance, top-left controls, top-right close, and topnav overlay.
- Row whitespace selection and explicit action-control non-selection.

Final pass cannot be recorded from DOM assertions alone.

## Remediation Rules

- Product code defects go to a fresh scoped repair worker using `gpt-5.4-high`, unless the validator finds an obvious one-place/few-line simple fix allowed by repo policy.
- Browser harness defects must be repaired in the browser/evidence path before changing product code.
- Plan defects return to planning for revised criteria/directives.
- If the same targeted issue fails validation twice after repair attempts, stop and ask the main thread/user for escalation model approval rather than silently substituting.

## Closeout Gate

Before final user report:

- Confirm relevant `.internal-dev/specifications/` updates or changelog `Specification Impact: none`.
- Confirm `.internal-dev/knowledge/workarea-operational-ui-consistency.md` was updated with reusable lessons.
- Confirm relevant `docs/` updates for user-facing/technical behavior changes.
- Confirm changelog entry under `.internal-dev/changelogs/`.
- Run stale-reference sweep for old artifact paths, `/tmp` evidence, stale agent ids, pending/planned/not implemented claims, TODO markers, and outdated phase wording.
- Create a git commit with implementation plus `.internal-dev`/docs updates unless the user explicitly says not to commit.
- Email summary, if requested later, is main-thread responsibility through the global email workflow.

## Handoff Expectations

Worker returns:

- Summary of changed files and why.
- Validation commands run and exact pass/fail/blocker status.
- Notes on CSS/JS asset versioning.
- Any screenshots/evidence paths produced by local checks, if applicable.
- Explicit statement whether topnav layering was fixed locally or requires escalation.

Validator/browser agent returns:

- Pass/fail against every acceptance criterion.
- Screenshot artifact paths.
- Visual critique against `/dashboard`, `/agents`, and `/avatar`.
- Residual risks and any TOOLING_CONSTRAINT entries.
