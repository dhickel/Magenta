# Workspace File Explorer Details/List Rewrite

## Topic

Reusable implementation lessons from replacing the Avatar Work Area file browser with a familiar details/list file explorer.

## Source References

- `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/02-target-design.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/workspaces-tools-outputs.md`

External UI grounding used during planning:

- KDE Dolphin Details view and additional columns.
- GNOME Files/Nautilus list columns.
- Windows File Explorer toolbar/path/details pane behavior.

## Key Takeaways

- The product contract of a file explorer is information architecture first: path controls, stable columns, compact rows, selection, and a separate details panel.
- A card grid can pass route tests while still failing the user expectation of a file manager.
- Backend path confinement must not rely on UI controls. Hidden form fields, destination names, and selected paths must be treated as untrusted input.
- Mutation responses should refresh every dependent region. For this explorer that means modal host, table/list, and inspector together.
- HTMX modal hosts need a single stable container. Returning duplicate IDs into an `innerHTML` target causes browser DOM drift that string tests can miss.
- Row metadata should not perform expensive preview work. Full text decoding belongs to preview/save routes; list/inspect can use extension, size, and bounded probes.
- Copy/move forms need operation-specific labels and required destination fields. Silent destination defaults make browser validation and user intent ambiguous.
- For Markdown, raw text access is part of the safety contract. Render failures should be visible but non-fatal.

## Engine Relevance

- Future Work Area UI work should start from `WorkAreaExplorerService` for filesystem truth and `WorkAreaExplorerFragments` for HTMX target IDs.
- Keep controllers thin: translate route/form input, call services, and render fragments.
- Keep file operations under Work Area roots. Reject traversal, absolute paths, symlink path components, symlink trees, protected Work Areas, and active Work Area descendants.
- Use direct AgentMail daemon/wait workflow for long-running coordination; do not reintroduce `.internal-dev/inbox`.
- Browser validation for UI changes must inspect styled `/avatar` surfaces, not only direct fragment URLs.

## Open Questions

- Should the current 10 MiB download cap remain for Work Area file downloads, or should large confined downloads stream with clearer UI affordances?
- Should copy/move grow a destination picker in a later phase, or is explicit path entry sufficient for alpha?
