# 2026-05-23 Work Area Persistence Foundation

## Summary

Added the first Work Area persistence slice for the Avatar UI refactor branch. Work Areas are durable metadata records for confined agent/project workspace subdirectories, including a system-owned `home/` default area.

## Changes

- Added `work_areas` schema with owner, workspace, relative path, display name, system/Home/active flags, metadata JSON, and timestamps.
- Added `WorkArea`, `WorkAreaRepository`, and `WorkAreaService`.
- Added Home Work Area creation under `<owner-workspace>/home/`.
- Added mark/list/unmark support for existing confined directories.
- Added duplicate reactivation behavior for inactive Work Areas.
- Added unmark guardrails for Home/system areas and queued/running assignment/output target references.
- Added real-path validation to reject traversal and symlink escapes.
- Updated workspace package guidance and technical docs.

## Validation

- `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepositorySchemaMigrationTest' test`

Result: 14 tests passed, 0 failures, 0 errors.

## Deferred

- Assignment metadata columns and runtime alias changes are still pending.
- Output redirect resolution is still pending.
- File explorer routes/components are still pending.
