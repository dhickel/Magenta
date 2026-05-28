# Assistant Dashboard Refactor Final Orchestration Plan

## Plan Shape

Classification: medium, single-domain, single implementation phase.

Worker:

- Phase 01: `worker-directives/phase-01-assistant-dashboard-refactor.md`
- Model: `gpt-5.5-high`

Validator:

- Code/doc validation after Phase 01 implementation.
- Browser proof by separate Playwright subagent after code-level validation defines the final checklist.
- Repo testing/Playwright policy requires `gpt-5.3-codex` medium for testing agents where available; record `TOOLING_CONSTRAINT` and stop before fallback if unavailable.

No integration validator is required because this is one work unit. Use a fresh validator if remediation changes criteria, touches a new domain, or materially changes risk.

## Execution Gates

1. Main thread creates a dedicated branch before implementation because this is a non-trivial plan.
2. Dispatch Phase 01 implementation worker with the worker directive and model lock.
3. Review worker result and `git status`; do not overwrite user changes.
4. Run code/doc validator.
5. If validator finds code defects, dispatch scoped repair worker using `gpt-5.5-high` unless the issue is an allowed simple validator edit.
6. After code-level validation passes, dispatch Playwright/browser validation subagent with the concrete checklist in `shared/validation-matrix.md`.
7. Return Playwright evidence to the validator for reconciliation.
8. Require final stale-reference sweep and evidence index at `artifacts/assistant-dashboard-refactor/validation-summary.json`.
9. Require `.internal-dev` closeout: specs, docs, changelog, and plan status.
10. Commit implementation plus `.internal-dev` updates at the phase gate unless the user explicitly says not to commit.

## Validation Gates

Do not mark complete until:

- focused controller/service tests pass;
- full `mvn test` passes or a specific user-approved blocker is recorded;
- Spring Boot startup smoke passes or a specific user-approved blocker is recorded;
- Playwright screenshots and visual critique pass for all required surfaces;
- Work Area browser relocation is proven in agent detail;
- route cleanup is tested without redirect/deprecation requirements;
- no normal user-facing Avatar labels remain;
- docs/spec closeout is complete.

## Remediation Policy

- `code_defect`: fresh scoped repair worker, model `gpt-5.5-high`, unless the validator can make an obvious one-place simple edit.
- `docs_or_evidence_defect`: fresh scoped repair worker unless it is a trivial stale reference or typo.
- `browser_harness_defect`: repair the browser script/evidence first; change product code only if evidence proves a product bug.
- `plan_defect`: return to planning before more coding.
- Same targeted issue failing twice after repair requires escalation to a fresh `gpt-5.5-high` repair worker and revised validation scope.

## Known Open Decisions For Worker Confirmation

These are recommended decisions, not user blockers:

- Remove `/avatar` as a maintained user-facing route; do not add redirect/deprecation support.
- Use `/manage` as primary operational dashboard route and update old `/dashboard` references rather than preserving redirect compatibility.
- Model dashboards as agent-agnostic user-widget containers.
- Use general dashboard tables for this refactor and seed `Assistant` from the intended default widget composition.
- Expose Work Areas through agent detail rather than Assistant.

If implementation discovers contrary hard constraints, stop and return to planning.
