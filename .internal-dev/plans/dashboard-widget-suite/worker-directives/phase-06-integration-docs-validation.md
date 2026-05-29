---
schema_version: 1
document_type: worker-directive
status: planning
phase: 06
role: integration-docs-validation
worker_model: gpt-5.5
worker_reasoning: high
validator_model: gpt-5.5
validator_reasoning: xhigh
browser_model: gpt-5.5
browser_reasoning: high
---

# Phase 06 Integration, Docs, And Final Validation Directive

## Objective

Complete cross-widget integration, docs/spec closeout, changelog/archive work, canonical evidence index, stale-reference sweep, integration validation, and final Playwright proof reconciliation.

## Editable Targets

- Docs under `docs/end-user/`, `docs/technical/`, `docs/api/`.
- `.internal-dev/specifications/*` affected files.
- `.internal-dev/changelogs/`.
- `.internal-dev/plans/dashboard-widget-suite/` status/evidence references only.
- `artifacts/dashboard-widget-suite/validation-summary.json` future implementation evidence file.
- Product code only for integration defects found by validators, routed through scoped repair workers.

## Forbidden Scope

- Do not add new feature behavior unless a validator-approved repair requires it.
- Do not mark evidence `fully_validated` until every gate passes and is reconciled.
- Do not commit from this directive unless main thread is executing the future implementation branch policy.

## Implementation Steps

1. Reconcile all widget registry entries, tool descriptors, routes, settings schemas, docs, and specs.
2. Run stale-reference sweep over docs and `.internal-dev`.
3. Update end-user, technical, API docs, specifications, decisions, deferred/horizon as needed, and changelog.
4. Create/update canonical evidence index at `artifacts/dashboard-widget-suite/validation-summary.json`.
5. Run `mvn test` and bounded startup.
6. Dispatch integration validator `gpt-5.5-xhigh`.
7. After code validation passes, dispatch Playwright/browser agent `gpt-5.5-high` or xhigh if selectable. If unavailable, record `TOOLING_CONSTRAINT` and stop before fallback.
8. Return Playwright results to validator for final reconciliation.

## Integration Validation Checklist

- Cross-widget settings and binding consistency.
- No stale route/docs/spec claims.
- Multi-instance and single-instance policies hold after all phases.
- Tool descriptors match registered tool names.
- Dashboard UI remains coherent across normal/edit/settings/detail flows.
- Runtime/project/Work Area boundaries are intact.
- Evidence JSON status is cross-field consistent.

## Final Browser Checklist

Run every scenario in `06-ui-ux-contract-and-visual-validation-criteria.md`, with screenshots and visual critique at desktop and mobile.

## Required Commands

- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Stale sweep examples: `rg -n "TODO|pending|planned|not implemented|/tmp|dashboard-widget-suite" docs .internal-dev`

## Do Not Close Unless

- Integration validator passes.
- Browser proof passes and is reconciled.
- Evidence index says `fully_validated` only when true.
- Specs/docs/changelog/archive work is complete.
