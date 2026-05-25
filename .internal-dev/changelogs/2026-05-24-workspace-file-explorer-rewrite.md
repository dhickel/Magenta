# 2026-05-24 Workspace File Explorer Rewrite

## Date

2026-05-24

## Change Summary

- Replaced the Work Area file browser with a compact details/list explorer modeled on familiar desktop file managers.
- Added required columns for name, file type, size, created time, last modified time, tags, and actions.
- Added a separate inspector panel for selected file/directory metadata, full tag controls, view/rename/delete, and copy/move forms.
- Added user-created custom file and directory tags using existing workspace label tables.
- Completed Markdown, plain text, image, and unsupported-file viewer behavior.
- Added confined copy, move, rename, delete, create-folder, create-text, and create-Markdown routes with consistent HTMX refreshes.
- Retargeted Work Area Browse so the explorer opens inside the Work Areas surface instead of below the full Avatar shell.
- Removed the repo-local AgentMail ledger workflow from active coordination; long-running email coordination now uses direct `mailctl status`, `mailctl next`, and `mailctl wait`.
- Consolidated Work Area UI knowledge and refreshed end-user and technical docs.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/frontend-htmx.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`

## Behavioral Impact

- Users now browse Work Area files in a table/details view instead of a card grid.
- Row actions are compact and predictable: view when supported, rename, and delete.
- The inspector owns full metadata, tags, copy, move, and mirrored actions.
- Markdown opens rendered by default, with raw editing available from the Text tab.
- Plain text opens raw-only.
- Supported images open in a contained modal preview with a download link.
- Unsupported/binary files do not expose a row View action.
- Copy and move require an explicit destination directory and remain Work Area-confined.
- Directory creation validates symlink ancestors before filesystem writes.
- Browse opens the details/list explorer in the selected Work Areas surface; Close restores the local placeholder.

## Risks

- The large-file download cap remains intentionally visible as a follow-up question; current UI exposes safe downloads where the API permits them.
- Mobile explorer usage is functional and validated, but dense by nature because the details table and inspector carry many controls.
- The older `.internal-dev/plans/workspace-file-explorer/` suite has been moved to the plan archive and marked as superseded by the rewrite plan.

## Follow-up Items

- Decide whether the 10 MiB file download cap should remain as an alpha safety limit or be replaced with streaming policy.
- Keep future explorer changes HTMX-first unless a narrowly scoped client-side interaction is demonstrably simpler.
