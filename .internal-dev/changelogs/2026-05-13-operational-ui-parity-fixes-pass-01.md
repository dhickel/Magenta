## Date

2026-05-13

## Change Summary

Implemented first pass of operational UI parity fixes focused on high-impact broken interactions:
- Plan editor collection row edits now persist via HTMX `PUT` routes.
- Agent detail tabs now load corresponding tab fragments via HTMX.
- Plan/job/project model selectors now populate from configured available models.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `.internal-dev/reviews/2026-05-13-operational-ui-divergence-review.md`
- `.internal-dev/bugs/2026-05-13-operational-ui-divergence-audit/report.md`

## Behavioral Impact

- Deliverable/step/validation/assumption inline edits in plan editor are no longer inert.
- Agent queue/inbox/jobs/workspace/outputs/history tabs can be opened from agent detail.
- Operators can choose non-default models in orchestration editors.

## Risks

- Full-form plan save semantics for all complex collections are still not fully normalized.
- Settings/inbox/outputs remain partially JS-driven and still need parity hardening.

## Follow-up Items

1. Add focused controller tests for new plan list-item update routes and agent tab HTMX wiring.
2. Implement settings HTMX save path.
3. Implement task/workflow run chat and gate-management UI pass.
