---
date: 2026-05-23
area: avatar-dashboard
type: feature
---

# Avatar Layout Editor UI

Replaced the old flat layout edit modal with a row/widget editor backed by the persisted Avatar dashboard row model.

## Changed

- `/avatar` widget grid now renders persisted dashboard rows through SimplyPages `Row`/`Column` primitives when row layout data exists.
- `/avatar/_edit` opens a compact row editor with add row, row move, add widget catalog, widget move, widget resize, and widget remove controls.
- Layout mutations now autosave per action through HTMX and refresh `#avatar-widget-grid` out of band.
- The old `PUT /avatar/_layout` endpoint is deprecated and no longer accepts the flat position/size form contract.
- Updated Avatar dashboard fragment and layout persistence docs.

## Validation

- `mvn -Dtest='io.mindspice.magenta2.api.web.AvatarDashboardControllerTest,io.mindspice.magenta2.avatar.*Test' test`

## Notes

- This slice does not yet implement the Work Area explorer modal, planner organizer modals, or final Avatar visual redesign validation. Those remain separate lanes in the Avatar refactor plan.
