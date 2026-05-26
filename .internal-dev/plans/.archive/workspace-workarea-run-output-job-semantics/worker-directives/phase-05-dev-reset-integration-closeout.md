# Phase 05 Worker Directive: Development Reset, Integration Validation, And Closeout

## Objective

Perform schema-backed development filesystem migration/reset, run full validation after directory restructuring finishes, and complete `.internal-dev`/docs closeout artifacts without touching unrelated untracked files.

## Editable Files

- Migration/reset utility or test-only support files if needed under existing repo conventions.
- `.internal-dev/changelogs/<date>-workspace-workarea-run-output-job-semantics.md`
- `.internal-dev/knowledge/*` files that need final reusable lessons.
- `.internal-dev/bugs/*` only for out-of-scope bugs discovered during validation.
- `.internal-dev/plans/workspace-workarea-run-output-job-semantics/*` for final status notes if needed.
- Test fixtures under `.internal-dev/test-fixtures/` only if required.
- No production code unless integration validation exposes a tiny directly coupled fix explicitly approved by the main thread.

## Forbidden Scope

- Do not delete/revert `.internal-dev/reviews/test-suite-quality-review.md`.
- Do not delete/revert `artifacts/playwright/`.
- Do not perform broad cleanup outside Magenta-owned development data roots.
- Do not mark validation complete if startup or Playwright is blocked.
- Do not create GitHub issues unless an actual `.internal-dev/bugs/` report is created.

## Supporting Docs To Read

- All prior worker reports and validator results.
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `docs/AGENTS.md`
- This suite's `shared/validation-matrix.md`

## Implementation Steps

1. Inventory schema-backed records: agents, workspaces, Work Areas, projects, assignments, plan runs, workflow runs, job runs, output artifacts, and chats.
2. Migrate or reset only known schema-backed directories into the target layout. Delete ambiguous loose files only inside approved development data roots.
3. Preserve moved/migrated file evidence where useful for validation.
4. Re-run focused tests impacted by any reset/migration fixes.
5. Run full `mvn test`.
6. Run bounded Spring startup.
7. Dispatch focused Playwright validation on a separate Playwright/browser validation agent using the Phase 04 checklist.
8. Update changelog, final knowledge lessons, bugs/GitHub issues if created, docs/spec closeout, and plan status notes.
9. Report exact validation commands, pass/fail, blockers, residual risk, and git status.

## Acceptance Criteria

- Development filesystem data matches the target layout or is cleanly reset based on schema-backed records.
- Full test suite passes.
- Bounded startup passes.
- Focused Playwright validation is completed and reconciled by validator/integration validator.
- `.internal-dev` closeout and docs closeout are complete.
- Unrelated untracked files remain untouched unless explicitly ingested as evidence.

## Negative Checks

```bash
git status --short
rg -n "runtime/task-runs|runtime/workflow-runs|outputs/jobs|jobs/.*/workspace|scratch/" src/main/java src/test/java docs .internal-dev/specifications .internal-dev/knowledge
find . -maxdepth 3 -type d | sort | rg '(^./agents|^./data|runtime|outputs/jobs|task-runs|workflow-runs|scratch)'
```

## Validation Commands

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
git status --short
```

Playwright is executed by a separate browser validation agent, then reconciled by the same validator or integration validator.

## Stop Conditions

- Stop if schema-backed migration ambiguity could delete meaningful non-development data.
- Stop if full execution validation is blocked by services/secrets; report the blocker and do not mark complete.
- Stop if Playwright cannot run for affected UI surfaces; report the blocker and residual risk.

## Senior Guidance

This phase proves the whole change, not just cleanup. Be strict about evidence and do not hide old-path hits as harmless unless they are explicitly legacy.

## Do Not Close Unless

- Full `mvn test`, startup, and Playwright results are recorded.
- Changelog and reusable knowledge are updated.
- Bugs are mirrored to GitHub if created.
- Git status is reported and unrelated untracked files are still preserved.

