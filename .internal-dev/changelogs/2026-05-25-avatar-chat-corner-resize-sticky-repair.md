---
schema_version: 1
document_type: changelog
status: active
created: 2026-05-25
owner: implementation_worker_agent
---

# Avatar Chat Corner Resize Sticky Repair

## Date

2026-05-25

## Change Summary

- Replaced the Avatar chat rail's full-height divider resize affordance with a bottom-right chat corner handle.
- Reworked the desktop Avatar shell grid so the chat width CSS variable owns the left column and the dashboard fills the remaining space.
- Added desktop chat height resizing through `--avatar-chat-panel-height` and browser-local persistence.
- Scoped the sticky/follow repair to Avatar by classing the SimplyPages content target and mirroring the framework sticky-sidebar scroll model only for `/avatar`.
- Tightened AC6 height bounds after Playwright showed the corner handle could move below the viewport: the chat max height now uses the panel's current viewport top offset and persists the clamped height.
- Tightened the AC7/AC8 rerun failures by removing the non-scrolling Avatar content-wrapper overflow ancestor and hiding the corner handle through both scoped mobile CSS and JavaScript media state.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/knowledge/avatar-chat-sticky-resize-container.md`
- `.internal-dev/plans/.archive/avatar-chat-corner-resize-sticky-repair/`

## Behavioral Impact

- `/avatar` uses a left chat rail and right dashboard layout on desktop, with dashboard width responding to chat corner resize.
- The chat rail follows document scroll and pins near the top of the viewport instead of scrolling away with the dashboard.
- Tablet/mobile layouts stack and do not expose the desktop resize handle.

## Risks

- The corner-resize implementation uses narrow JavaScript because CSS native `resize` cannot coordinate dashboard grid width.
- Browser-local width and height persistence is intentionally clamped during restore; users with old or oversized saved dimensions may see their saved layout corrected.

## Validation

- `mvn -Dtest=AvatarDashboardControllerTest test` passed with 14 tests and no failures.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached successful Spring startup on an ephemeral port before the timeout stopped the app gracefully.
- AC6 remediation reran `mvn -Dtest=AvatarDashboardControllerTest test` successfully with 14 tests and no failures.
- AC6 remediation reran `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`; startup reached embedded Tomcat on an ephemeral port before the timeout stopped the app gracefully.
- AC7/AC8 remediation reran `mvn -Dtest=AvatarDashboardControllerTest test` successfully with 14 tests and no failures.
- AC7/AC8 remediation ran `git diff --check` successfully with no whitespace errors.
- AC7/AC8 remediation reran `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`; startup reached embedded Tomcat on an ephemeral port before the timeout stopped the app gracefully.
- Final focused Playwright validation passed on live `http://localhost:18080/avatar` with `/css/avatar-dashboard.css?v=3` and `/js/avatar-shell.js?v=4`.
- Final Playwright artifacts were saved under `target/playwright-avatar-corner-resize-final/`.
- Final Playwright proved the old divider hook count is `0`, the desktop corner handle is visible and hit-testable, expanding changes chat `480px -> 640px` while main width changes `821.20px -> 661.20px`, shrinking changes chat `640px -> 420px` while main width recovers `661.20px -> 881.20px`, document scroll pins the chat/rail at `20px` while main content scrolls to `-844.98px`, and the narrow `1024px` viewport has no horizontal overflow with the handle `hidden`, `display:none`, `visibility:hidden`, and `pointer-events:none`.

## Follow-up Items

- None.
