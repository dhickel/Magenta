---
document_type: changelog
status: finalized
created: 2026-05-23
---

# Avatar Width Picker

## Date

2026-05-23

## Change Summary

Replaced the Avatar edit-mode width cycle control with an anchored width picker that offers preset sizes plus a custom `n/12` input, then extended the Avatar grid override so all `1..12` widths render correctly.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-layout-edit.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/avatar-dashboard-layout-persistence.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`

## Behavioral Impact

- Clicking a widget width control in `/avatar?edit=true` now opens a compact popover near the trigger instead of cycling widths blindly.
- The popover offers common preset widths and a custom numeric width input constrained to the remaining row capacity.
- The picker closes on outside click, Escape, or successful apply.
- Avatar layout persistence now accepts any width from `1` to `12`, as long as the full row still fits within the 12-column limit.
- The Avatar grid override now renders all column classes from `col-1` through `col-12`, so custom widths display correctly.

## Risks

- Mobile continues to stack dashboard columns to one column visually, so a width change updates persisted class/state there without necessarily changing on-screen pixel width.
- The legacy `width-cycle` route remains for compatibility; future cleanup can remove it if no callers remain.

## Follow-up Items

- None required for this picker flow after the delegated desktop/mobile validation pass.
