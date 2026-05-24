---
document_type: changelog
status: finalized
created: 2026-05-23
---

# Avatar Edit Icon Controls

## Date

2026-05-23

## Change Summary

Replaced ambiguous text-based Avatar edit controls with compact inline SVG icon buttons so widget actions and row actions are recognizable during in-place layout editing.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`

## Behavioral Impact

- Widget corner controls now render icon affordances for settings, refresh, move, width cycling, and remove actions.
- Todo, daily-task, and calendar row actions now use icon affordances instead of text-only micro controls where appropriate.
- Edit-mode controls keep accessible titles and ARIA labels while preserving the compact SimplyPages-style density.

## Risks

- Row-level controls remain intentionally low-emphasis and could still merit a later spacing pass if broader edit-mode cleanup resumes.
- Validation on the stale `:8080` app instance would still show old controls; the verified build was the fresh `:8081` runtime started from current source.

## Follow-up Items

- Consider a later pass on row-control proximity and overall empty-row noise if the broader Avatar edit-mode polish track resumes.
- No user-facing docs update was required for this icon-only control clarity fix.
