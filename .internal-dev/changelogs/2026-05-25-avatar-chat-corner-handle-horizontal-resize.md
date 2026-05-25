---
schema_version: 1
document_type: changelog
status: active
created: 2026-05-25
owner: codex
---

# Avatar Chat Corner Handle Horizontal Resize

## Date

2026-05-25

## Change Summary

- Updated the Avatar chat corner handle visual to a compact sideways double-arrow and moved it tight to the bottom-right corner of the chat panel.
- Relaxed desktop shell sizing constraints so the chat rail can expand/squish the dashboard lane more reliably on common viewport widths.
- Bumped the Avatar shell script asset version to ensure the updated resize behavior is loaded immediately.

## Files

- `src/main/resources/static/js/avatar-shell.js`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `docs/technical/avatar-dashboard-fragments.md`

## Behavioral Impact

- Desktop users can drag the chat corner handle left/right to resize chat width and compress/expand the dashboard lane.
- Desktop users can continue dragging up/down on the same handle to change chat height.
- The corner affordance now matches the common sideways resize icon convention.

## Risks

- The widened horizontal range intentionally allows a narrower dashboard lane than before; content still depends on existing responsive card/table behavior.

## Validation

- `mvn -Dtest=AvatarDashboardControllerTest test` passed with 14 tests and no failures.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached successful Spring startup on an ephemeral port before timeout-triggered graceful shutdown.

## Follow-up Items

- None.
