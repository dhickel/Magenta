# Root-Relative Workspace Migration Orchestration State

## Current Goal

Implement the user-approved root-relative workspace migration direction after the handoff review:

- Move toward a clean `.magenta` root structure.
- Store the SQLite database inside the root.
- Treat Magenta-owned persisted paths as root-relative.
- Keep existing chat files by copying them into the new root.
- Drop existing workspace files from the old poor structure.
- Keep sessions/chat UX behavior stable.
- Document import/repair/admin migration tooling as future work rather than building it in this pass.

## Branch

- `root-relative-workspace-migration`

## Shared Notes

- `.codex-orchestration/root-relative-workspace-migration/notes.md`

## Phase Plan

1. Planning and risk pass.
2. Serial implementation pass for config/root/database/path behavior.
3. Serial implementation pass for chat file carry-forward/new-root initialization behavior.
4. Validation and remediation gates.
5. Documentation and `.internal-dev` closeout.
6. Final high-review and final commit.

## User Decisions

- SQLite database should live in the root.
- Root-relative storage is preferred long term.
- Future migration/import/admin repair is documentation-only for now.
- No symlink/old-root archive behavior is required.
- Existing chat files must be preserved.
- Existing workspace files may be discarded.
- Active work state can be ignored for this cleanup.

## Open Assumptions For Planner

- The implementation should avoid destructive filesystem deletion unless the user explicitly asks for a cleanup command.
- The new root should be safe on fresh installs and should not require manual directory pre-creation.
- Chat file preservation should be implemented as a controlled copy/bootstrap path, not an implicit delete or external move.
- Existing sessions view should remain backed by current chat memory/session persistence.
