---
schema_version: 1
document_type: changelog
status: active
created: 2026-05-25
owner: codex
---

# Avatar Chat Resize Handle Follow-up

## Date

2026-05-25

## Change Summary

- Reworked Avatar chat horizontal resize bounds so desktop drag can compress/expand the dashboard lane with a materially wider range.
- Replaced the text glyph handle with a proper two-axis SVG resize icon and tuned control sizing/placement near the chat bottom-right corner.
- Bumped Avatar shell asset version to ensure browser cache refresh for the updated resize logic and icon.

## Files

- `src/main/resources/static/js/avatar-shell.js`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/technical/avatar-dashboard-fragments.md`

## Behavioral Impact

- Desktop corner drag now supports practical two-axis resize behavior: left/right changes chat rail width and squeezes/expands dashboard width; up/down changes chat panel height.
- The resize control now visually communicates both horizontal and vertical resizing.

## Validation

- `mvn -Dtest=AvatarDashboardControllerTest test` passed with 14 tests and no failures.

## Follow-up Items

- Perform delegated Playwright visual/interaction validation on `/avatar` to verify drag affordance quality and real pointer drag behavior in-browser.
