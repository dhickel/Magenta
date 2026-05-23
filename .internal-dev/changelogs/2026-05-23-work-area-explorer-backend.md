---
date: 2026-05-23
area: avatar-work-areas
type: feature
---

# Work Area Explorer Backend

Added the backend contract for the Avatar Work Areas/file explorer surface.

## Changed

- Added `WorkAreaExplorerService` for confined directory listing, safe text preview/save, bounded download resolution, directory creation, sibling rename, recursive delete, and nested Work Area marking.
- Added `WorkAreaController` under `/api/work-areas` for Work Area metadata and explorer operations.
- Added explicit safe text editing limits: preview up to 256 KB and save up to 1 MB for known text-like extensions.
- Added recursive delete guardrails for typed confirmation, Work Area roots, Home/system Work Areas, marked Work Area descendants, active assignment/output target references, traversal, and symlink paths.
- Documented the `/api/work-areas` route family and explorer behavior.

## Validation

- `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest' test`
- `mvn -DskipTests compile`

## Notes

- This is the backend/API slice only. The Avatar Work Areas widget and SimplyPages/HTMX explorer modal are still future UI work in the broader Avatar refactor.
