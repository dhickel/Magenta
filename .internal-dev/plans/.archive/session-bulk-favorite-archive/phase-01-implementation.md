# Session Bulk Favorite Archive

## Context

Chat sessions can be renamed and deleted from the sidebar. The next workflow needs favorite, archive, recent-first ordering, and bulk actions from an expanded management box.

## Goal

Add session favorite/archive metadata, recent-first sorting with favorites pinned, per-session star/archive controls, and bulk delete/archive/favorite operations from a top management panel.

## In Scope

- Session metadata fields for favorite, archived, and updated timestamp.
- API endpoints for favorite and archive state changes.
- Session list sorting: favorites first, then most recent.
- Sidebar management panel with checkboxes and delete/archive/favorite selected buttons.
- Confirmation prompts for delete and archive operations.
- Focused tests and changelog.

## Out of Scope

- Archived-session restore UI.
- Undo after delete/archive.
- Server-side bulk endpoint.

## Implementation Steps

1. Extend session metadata persistence and DTOs.
2. Add service/controller methods for favorite/archive.
3. Update sidebar rendering and JS actions.
4. Update focused tests and run validation.

## Validation

- Run affected controller/frontend/agent tests.
- Run full Maven suite.

## Exit Criteria

- Sessions sort favorites first, then recent active conversations.
- Per-row favorite/archive/delete/rename controls work.
- Bulk panel applies actions to selected sessions.
