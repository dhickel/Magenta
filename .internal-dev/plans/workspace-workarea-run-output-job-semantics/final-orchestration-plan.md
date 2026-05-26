# Final Orchestration Plan

## Objective

Bring Magenta's workspace, Work Area, run staging, output promotion, and job semantics in line with the ironed-out model through delegated implementation, validation, browser proof, and closeout.

## Main-Thread Setup

1. Create a dedicated branch before implementation starts.
2. Treat `review-agent/xhigh-layout-audit.md` and `review-agent/consolidation-plan.md` as part of the planning contract; the review's `WorkspacePathLayout` consolidation is already integrated into Phase 02 and Phase 03.
3. Leave unrelated untracked files alone:
   - `.internal-dev/reviews/test-suite-quality-review.md`
   - `artifacts/playwright/`
4. Dispatch each worker with exactly one directive from `worker-directives/`.
5. Use the same worker and validator session per work unit for remediation unless the plan's replacement criteria are met.
6. Commit each phase after its validator passes.

## Parallel And Serial Gates

- Phase 01 and Phase 02 may run in parallel.
- Phase 03 starts only after Phase 02 validator passes.
- Phase 04 starts after Phase 02 and Phase 03 pass, except narrowly scoped API payload prep may begin once Phase 02 field names are committed.
- Phase 05 starts only after Phases 01-04 pass.
- Full `mvn test`, bounded startup, and final Playwright proof wait until Phase 05 filesystem reset/migration completes.

## Worker Dispatch

| phase | directive | dependencies | validation |
| --- | --- | --- | --- |
| 01 | `worker-directives/phase-01-contract-docs-guidance.md` | branch only | spec/docs `rg` checks and validator review |
| 02 | `worker-directives/phase-02-layout-schema-workareas.md` | branch only | layout/schema/Work Area tests and validator review |
| 03 | `worker-directives/phase-03-runtime-tools-output-promotion.md` | Phase 02 pass | runtime/tool/output/job tests and validator review |
| 04 | `worker-directives/phase-04-api-ui-browser-surfaces.md` | Phase 02/03 pass | controller tests, validator review, Playwright checklist |
| 05 | `worker-directives/phase-05-dev-reset-integration-closeout.md` | Phases 01-04 pass | migration evidence, full tests, startup, Playwright, closeout |

## Validator Expectations

- Each unit validator reads the directive first, then named specs, support docs, implementation report, and changed files.
- Validators check criteria, architecture fit, app contracts, regressions, docs drift, test quality, `.internal-dev` workflow, and release risk.
- If validation fails, validator writes a remediation handoff for the same worker.
- If criteria are flawed or ambiguous, return to planning before more coding.

## Playwright Scope

The Playwright/browser validation agent executes only after Phase 04 code-level validation or final integration validation provides a concrete checklist. Required coverage:

- Work Area browse/edit surface.
- Project browse/edit surface.
- Non-job task/workflow submission with required run display name.
- Outputs/job-bound output view affected by routing.
- Desktop and mobile screenshots.
- Visual quality critique: alignment, spacing, density, hierarchy, wrapping, overflow, affordances, and coherent use of space.

## Integration Validation

After all unit validators pass and Phase 05 completes migration/reset, run an integration validator with high reasoning. The integration validator must verify:

- Contracts, specs, docs, package guides, code, prompts, and tests all use the same target model.
- Static layout source of truth is used consistently.
- Job-owned workspaces are not active behavior.
- Run-local output alias and final promotion compose correctly across task, workflow, and job-bound paths.
- Work Area/project browser assumptions match backend routing.
- Old path terms are legacy/compatibility only.
- Full `mvn test`, startup, and Playwright evidence are reconciled.

## Closeout Gates

- Complete `closeout-report-plan.md`.
- Update changelog and knowledge.
- Create/mirror/archive bugs if any are created.
- Move finalized plan artifacts to `.internal-dev/plans/.archive/` after all implementation and validation work is complete.
- Commit final closeout on the dedicated branch.
- Main thread reports outcome to the user and begins execution after this suite returns.
