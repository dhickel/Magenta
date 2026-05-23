# Date

2026-05-23

## Change Summary

Added Avatar dashboard row/widget layout persistence as the first implementation phase of the Avatar Agent UI refactor. The new schema and service methods support row ordering, single-instance widget placement, 12-column widths, row/widget movement, widget resizing, removal, and compatibility seeding from the current flat dashboard layout.

## Files

- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarDashboardRow.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarDashboardRowWidget.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- `docs/technical/avatar-dashboard-layout-persistence.md`
- `.codex-orchestration/avatar-agent-ui-refactor/notes.md`

## Behavioral Impact

No current `/avatar` browser behavior changes in this phase. Existing flat layout APIs remain available, and legacy layout saves seed or update the new row/widget records so the later HTMX layout editor can switch over without losing current widget choices.

## Risks

- The current UI still uses the legacy `AvatarDashboardWidget` contract until the layout editor phase lands.
- Existing `avatar.sqlite` files with unusual legacy sizes fall back to standard 4-column width unless the size is `wide` or `compact`.

## Follow-up Items

- Build the SimplyPages row/column edit mode against the new row/widget service contract.
- Add Work Area persistence and runtime output routing in later phases before exposing Work Area submit controls.
