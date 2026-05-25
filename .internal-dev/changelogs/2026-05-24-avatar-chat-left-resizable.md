# Date

2026-05-24

# Change Summary

Implemented the Phase 01 Avatar shell geometry fix so `/avatar` uses a left chat rail with a dedicated divider, grid-relative drag resizing, no click-only width jump persistence, and right-side dashboard fill behavior.

# Files

- `.internal-dev/changelogs/2026-05-24-avatar-chat-left-resizable.md`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `.internal-dev/knowledge/avatar-shell-resizable-rail-geometry.md`
- `.internal-dev/plans/.archive/avatar-chat-left-resizable/`

# Behavioral Impact

- `/avatar` now renders left-to-right as chat rail, divider, then main dashboard/tab content.
- Desktop divider resizing now uses `.avatar-shell-grid` coordinate space and clamp bounds, avoiding viewport-edge math drift on wide screens.
- Pointer click without meaningful drag movement no longer applies or persists rail width.
- Desktop chat rail uses sticky container behavior so the chat stays visible while long dashboard content scrolls.
- The shell no longer caps the working area at `1680px`, allowing dashboard content to fill remaining right-side space.
- Mobile/tablet keeps stacked layout and hides the divider.

# Risks

- The desktop width clamp keeps a fixed minimum right-side main content budget (`AVATAR_MAIN_MIN`) in `avatar-shell.js`; future UX tuning may adjust this constant.
- The JavaScript minimum rail width is intentionally coupled to the CSS `minmax(...)` lower bound so persisted widths match visible browser behavior.
- JS and CSS rail minimums are now intentionally aligned (`22.85rem` in CSS, `366px` in JS), so if root font sizing changes materially, both values must be updated together.

# Follow-up Items

- None in this phase.

# Validation

- `mvn -Dtest=AvatarDashboardControllerTest test`: passed.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: passed (app reached startup before timeout).
