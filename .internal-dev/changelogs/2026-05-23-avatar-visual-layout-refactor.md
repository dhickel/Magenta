# Date

2026-05-23

# Change Summary

Refactored the Avatar dashboard layout workflow so dashboard editing happens in place on the rendered `/avatar` surface. Added stricter agent instructions and SimplyPages knowledge for future UI work, including mandatory visual Playwright critique for layout changes.

# Files

- `AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarRepositoryTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/plans/.archive/avatar-visual-layout-refactor/`

# Behavioral Impact

- `/avatar?edit=true` now renders row and widget layout controls directly on the live dashboard surface.
- `/avatar/_widgets?edit=true` returns the widget grid with live edit decorations.
- Widget headers expose a **Details** action with stable `data-avatar-detail-trigger` attributes for module-specific detail modals.
- Layout mutations refresh the live dashboard grid through OOB swaps and clear the shared edit container as needed.
- Add-widget catalog rendering is inline rather than a full-screen overlay, preventing pointer interception against row controls.
- Empty dashboard rows can be deleted after widgets are removed.
- Normal dashboard rows use available width more coherently in view mode while preserving 12-column sizing in edit mode.
- Agent instructions now require SimplyPages docs/demo inspection, scratch-page discipline, and visual Playwright validation for layout work.

# Risks

- Edit mode is functionally validated but remains visually dense on mobile; this is tracked as follow-up polish.
- Existing `files` widget key remains for compatibility while the UI label stays **Work Areas**.
- The legacy `/avatar/_edit` modal remains as fallback/compatibility; the preferred workflow is in-place edit mode.

# Follow-up Items

- Improve edit-mode control hierarchy and mobile hit-target ergonomics when the next Avatar polish pass is scheduled.
