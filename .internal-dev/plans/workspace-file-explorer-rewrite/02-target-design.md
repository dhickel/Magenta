# Target Design

Status: target design for implementation workers
Created: 2026-05-24

## Product Shape

The Work Area explorer becomes a familiar file-manager details view embedded in the Avatar Work Areas tab or selected Work Area surface.

Desktop layout:

- Top: compact Work Area selector/list area as existing Avatar navigation requires.
- Main Work Area panel: two-column file-manager layout.
- Left/main column: bounded explorer table module.
- Right column: separate metadata/inspect panel.
- Viewer/action modals render over the page through a stable modal container.

Mobile layout:

- Work Area selector remains above the explorer.
- Explorer table becomes horizontally manageable with compact columns or responsive priority hiding only when necessary.
- Inspect panel stacks below the table and remains reachable after row selection.
- Modal viewer uses full-width mobile sizing.

## Reference-Driven UI Criteria

Grounding:

- KDE Dolphin Details view emphasizes row-based listing and configurable detail columns such as size, date, type, rating/tags/comment. It keeps `Name` as the primary column and treats additional information as columns.
- GNOME Files/Nautilus list view exposes visible columns like Name, Size, Type, Modified, Created, Owner/Permissions, and Name cannot be hidden.
- Windows File Explorer has top navigation/path controls, toolbar actions such as copy/paste/rename/delete, details sorting by name/date/type, copy/paste/move flows, and a right-side details pane pattern.
- Dolphin information panel and Windows details pane patterns support a selected/hovered file/folder metadata rail separate from the main file list.

Implementation criteria:

- Use a table-like row layout, not cards.
- Put `Name` first and make it the widest/scannable column.
- Show file type as a human label: `Folder`, `Markdown`, `Text`, `Image`, `JSON`, `YAML`, `Binary`, `Unknown`, etc.
- Show size blank/dash for directories and formatted for files.
- Show created as known timestamp or `Unknown` if unavailable.
- Show modified from filesystem last modified.
- Show first tags as compact chips, plus `+N` when hidden.
- Keep row actions to compact icon or short-icon buttons: eye/view, rename, delete.
- Keep copy/move in inspect/operation controls to avoid crowding table rows.

Failure examples that validation must reject:

- Cards for files/directories.
- Table rows without column headers.
- Main file list hidden behind modal-only flows.
- File action dropdown as the only way to view/rename/delete.
- Inspect metadata rendered inside each row instead of a right panel.
- Large decorative cards, hero text, or low-density marketing-like layout.
- Buttons wrapping into unreadable stacks on desktop rows.
- Mobile layout requiring root-level horizontal page scrolling.

## Domain Contract

### Explorer Entry Model

Workers should evolve or wrap `WorkAreaExplorerService.Entry` into a richer view/service model with:

- `name`
- `path`
- `directory`
- `regularFile`
- `fileType`
- `sizeBytes`
- `sizeLabel`
- `createdAt` nullable/optional
- `modifiedAt`
- `viewerKind`: `TEXT`, `MARKDOWN`, `IMAGE`, `UNSUPPORTED`, `TOO_LARGE`, `INVALID_UTF8`
- `tags`
- operation support flags: `canView`, `canRename`, `canDelete`, `canCopy`, `canMove`, `canTag`

Service code owns filesystem truth. UI code may format labels but must not resolve paths directly.

### Tags

Existing `workspace_file_labels` and `workspace_file_label_assignments` are the preferred v1 persistence model if validation confirms they support:

- user-created custom tags;
- system tags `note` and `work-area`;
- assignment to file paths and directory paths;
- `labelsForPath`;
- `labelsForSubtree`;
- `moveSubtree`;
- `copySubtree`;
- `deleteSubtree`.

Add thin service/API methods rather than bypassing `WorkspaceFileMetadataService` from controllers. If schema changes are required, update repository self-migration and canonical `schema.sql`.

### Operations

Operations stay under `WorkAreaExplorerService` or a narrow helper service in the same package:

- list/inspect
- view/preview
- save text
- rename
- delete preflight/execute
- copy
- move
- create directory/text/Markdown if still exposed
- tag list/create/add/remove
- recent action logs if displayed

Every mutation must:

- resolve source/destination through root-confined real-path checks;
- reject absolute/traversal/symlink escape;
- reject root/system/home protected operations;
- reject directory move/copy into self or descendant;
- reject collisions unless explicit overwrite behavior is designed later;
- update/copy/delete tag metadata consistently;
- record durable action log where existing action log supports it.

## API And Fragment Contract

Existing JSON API routes may remain compatible. Add or refine routes only as needed for the details/list UI.

Preferred Avatar fragment routes:

- `GET /avatar/_work-areas/{workAreaId}/explorer?path=...&selected=...`
- `GET /avatar/_work-areas/{workAreaId}/explorer/list?path=...&selected=...`
- `GET /avatar/_work-areas/{workAreaId}/inspect?path=...`
- `GET /avatar/_work-areas/{workAreaId}/viewer?path=...`
- `GET /avatar/_work-areas/{workAreaId}/viewer/text?path=...&tab=rendered|text`
- `PUT /avatar/_work-areas/{workAreaId}/text?path=...`
- `GET /avatar/_work-areas/{workAreaId}/modal/{rename|delete|copy|move|tag}?path=...`
- `POST /avatar/_work-areas/{workAreaId}/files/rename`
- `POST /avatar/_work-areas/{workAreaId}/files/delete`
- `POST /avatar/_work-areas/{workAreaId}/files/action/{copy|move}`
- `POST /avatar/_work-areas/{workAreaId}/tags`
- `POST /avatar/_work-areas/{workAreaId}/files/tags`
- `DELETE /avatar/_work-areas/{workAreaId}/files/tags`

HTMX target contract:

- `#avatar-workarea-explorer-shell`: full explorer module including table and inspect panel.
- `#avatar-workarea-list-region`: table/list only when a focused list refresh is safe.
- `#avatar-workarea-inspector`: right inspect panel.
- `#avatar-workarea-modal`: modal container.
- Use OOB swaps after mutations to update table, inspect panel, and modal container together.

## UI Rendering Contract

The final renderer may be:

- Magenta-local SimplyPages components/helpers under `api/web`, or
- a corrected SimplyPages reusable module if available and exact.

It must not use string replacement on rendered HTML to inject toolbar behavior. Build the desired structure directly from components.

Left/main explorer region:

- `.avatar-workarea-explorer-shell`
- `.workspace-explorer-toolbar`
- `.workspace-explorer-pathbar`
- `.workspace-explorer-table-region`
- `<table>` or table-like semantic structure with headers
- compact rows with selected state

Right inspect region:

- selected name/type/path summary
- timestamps and size
- full tags with add/remove
- view if supported
- rename/delete
- copy/move destination controls
- safe unavailable messaging for unsupported actions

Viewer modal:

- Markdown: tabs `Rendered` and `Text`; rendered tab default; text tab includes raw/edit surface where allowed; render error at bottom if parsing/rendering fails.
- Plain text: raw tab default; no Markdown render affordance.
- Image: safe image with metadata and download/open link.
- Unsupported: no viewer modal or modal with clear unsupported message only if invoked from inspect.

## Security And Failure Handling

- Never expose absolute host paths in normal UI.
- Error fragments must be user-visible, not silent transport failures.
- Markdown uses safe rendering by default. Catch renderer/runtime exceptions and render friendly error at bottom.
- Unsupported/binary files must not be sent through text edit paths.
- Delete confirmation must be explicit and distinguish file vs recursive directory.
- Copy/move forms must validate destination exists under root and reject collisions.

## Validation Architecture

Use serial mutating workers and non-mutating red-team validation after every phase. UI validation must run via Playwright subagent against a running app, capture screenshots, and critique visual quality.

Backend validation must include:

- targeted tests per phase;
- full `mvn test` before final quality review;
- bounded Spring startup:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

## Senior Engineer Notes

This is not a styling pass over the old explorer. The product contract is the information architecture of a file manager: path controls, stable details columns, row selection, and a side details pane. Keep that shape intact even if an existing component offers an easier card/modal path. When tradeoffs arise, prefer a slightly simpler row/table explorer that is obviously a file manager over a richer-looking UI that obscures columns, selection, or root confinement.
