# Final Orchestration Plan

## Objective

Implement Magenta's first-class Agent Skills system through delegated phased work, with DB-backed metadata and assignments, root skill repository discovery/loading, browser create/edit/assign UI, docs/spec closeout, browser proof, and final official-spec adherence validation.

## Main-Thread Setup

1. Create a dedicated git branch before Phase 01 begins.
2. Dispatch each worker with exactly one directive from `worker-directives/`.
3. Require every worker and validator to re-open the official Agent Skills pages before spec-sensitive work or approval.
4. Keep `.internal-dev/research/agent-skills-specification-research.md` as context only.
5. Use consistent worker and validator sessions for each phase. Resume the same sessions for remediation unless replacement criteria are met.
6. Commit completed work at the end of each validated phase on the dedicated branch.

## Phase Gates

| phase | directive | dependencies | validation gate |
| --- | --- | --- | --- |
| 01 | `worker-directives/phase-01-contract-docs-guidance.md` | branch only | spec/docs/guidance validator pass |
| 02 | `worker-directives/phase-02-skill-repository-loader-parser.md` | Phase 01 pass | parser/discovery/metadata tests and validator pass |
| 03 | `worker-directives/phase-03-skill-assignments-chat-activation.md` | Phase 02 pass | assignment/catalog/activation/chat tests and validator pass |
| 04 | `worker-directives/phase-04-skill-api-file-management.md` | Phase 02 pass; avoid Phase 03-owned files until stable | API/file-management tests and validator pass |
| 05 | `worker-directives/phase-05-skill-browser-guided-creation-ui.md` | Phases 03 and 04 pass | UI tests, validator pass, Playwright proof reconciled |
| 06 | `worker-directives/phase-06-integration-spec-adherence-closeout.md` | Phases 01-05 pass | full tests, startup, integration validator, final spec validator, closeout |

## Parallelization Rules

- Phase 01 is serial and must finish first.
- Phase 02 starts after Phase 01.
- Phase 03 starts after Phase 02.
- Phase 04 may start after Phase 02 if it avoids files owned by active Phase 03 work; otherwise serialize it after Phase 03.
- Phase 05 is serial after Phase 03 and Phase 04.
- Phase 06 is serial after all phase validators pass.

## Validator Expectations

- Validators read the directive first, then the changed files, named specs/docs, worker report, and relevant official Agent Skills pages.
- Validators check acceptance criteria, negative criteria, application contracts, architecture fit, security-sensitive filesystem confinement, stale metadata/cache behavior, docs drift, test quality, `.internal-dev` workflow, and release risk.
- If validation fails, validators write a remediation handoff for the same worker.
- If criteria are ambiguous or flawed, return to planning before more coding.

## Browser Validation

Browser validation applies because this plan changes user-facing web UI.

The Playwright/browser validation agent runs only after Phase 05 code-level validation provides a concrete checklist. Required proof includes:

- `/skills` desktop and mobile screenshots.
- Skill browse/filter/detail.
- Valid and malformed skill display.
- `SKILL.md` edit and revalidation.
- File add/edit flow.
- Agent assign/unassign flow.
- Guided creation flow.
- Visual quality critique covering alignment, spacing, density, hierarchy, wrapping, overflow, affordance clarity, mobile stacking, and first-viewport usefulness.

Results return to the Phase 05 validator or Phase 06 integration validator for reconciliation. Browser proof is not passed until a validator accepts the screenshots, logs, and observed behavior against criteria.

## Integration Validation

After unit validators pass, run an integration validator with `gpt-5.5` high reasoning. The integration validator must verify:

- Root `skills/` repository behavior matches docs/specs/code/tests.
- Parser/loader status behavior is documented and covered by tests.
- DB metadata and assignment records are separate from tool assignment fields.
- Runtime catalog filtering and activation deduplication compose correctly with chat/default agent behavior.
- APIs and UI do not escape the skill repository root.
- UI claims match backend capabilities.
- Deferred project-local and layered assignment behavior is not accidentally active.
- Full `mvn test`, bounded startup, and Playwright evidence are reconciled.

## Final Spec-Adherence Validation

Run a final non-mutating `validation_redteam_agent` session using `gpt-5.5` xhigh reasoning. It must use the official Agent Skills pages as source of truth and check adherence outside Magenta's explicitly documented divergences.

Failure conditions:

- undocumented divergence from required `SKILL.md` format behavior;
- missing tests for required validation matrix cases;
- runtime catalog violates progressive disclosure;
- optional directories are eagerly loaded;
- malformed skills can crash discovery/runtime/UI;
- docs/specs claim unsupported project-local/layered behavior.

## Closeout Gates

- Complete `closeout-report-plan.md`.
- Update affected specifications, docs, package guides, knowledge, changelog, and any bug reports.
- Mirror `.internal-dev/bugs/` reports to GitHub issues if this repo has a GitHub remote and the bug remains active.
- Move finalized plan artifacts to `.internal-dev/plans/.archive/` only after implementation, validation, closeout, and commit gates complete.
- Commit final closeout on the dedicated branch.
- Main thread reports implementation outcome, validation evidence, residual risks, and spec-divergence status to the user.
