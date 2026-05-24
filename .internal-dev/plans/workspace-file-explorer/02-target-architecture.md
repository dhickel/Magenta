# Target Architecture

## Design Summary

Use a split architecture:

- Magenta workspace domain services resolve owner roots/Work Areas, enforce confinement, perform file operations, manage DB-backed file labels/tags, preserve text compatibility, and log file actions.
- Magenta web controllers expose JSON/API and HTMX fragment endpoints as thin use-case entry points.
- Magenta Avatar/controller rendering uses a reusable SimplyPages FileExplorer/FilePicker module, configured with Magenta routes and inspector content.
- SimplyPages upstream provides generic explorer/picker render modules, view models, confirmation modal components, inspector slots, breadcrumb/list/card rendering, and optional narrow JS hooks, with no filesystem or database responsibilities.

## Responsibilities

### Magenta Workspace Domain

Target package:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces`

Proposed classes/records:

- `WorkspaceFileExplorerService`
- `WorkspaceFileActionService`
- `WorkspaceFileMetadataService`
- `WorkspaceFileMetadataRepository`
- `WorkspaceFileActionLogRepository`
- `WorkspaceFileRoot`
- `WorkspaceFilePath`
- `WorkspaceFileEntry`
- `WorkspaceDirectoryListing`
- `WorkspaceFilePreview`
- `WorkspaceFileKind`
- `WorkspaceFileActionType`
- `WorkspaceFileActionRecord`
- `WorkspaceFileLabel`
- `WorkspaceFileLabelAssignment`
- `TextFilePolicy`
- `TextFileReadResult`
- `TextFileSaveRequest`
- `FileOperationResult`

Do not put UI, Avatar, or HTTP details in these services.

### Magenta Web/API

Target package:

- `src/main/java/io/mindspice/magenta2/api/web`

Targets:

- Evolve or wrap `WorkAreaController`.
- Add HTMX fragment endpoints for explorer shell, directory listing, inspector, viewer/editor, action modals, picker dialogs, and result refresh fragments.
- Keep JSON/API routes stable or version-compatible where existing callers rely on `/api/work-areas`.
- Prefer records for request/response payloads.
- Route failures should become fragment-friendly HTML for HTMX routes and clear status/error payloads for API routes.

### Magenta Avatar Integration

Targets:

- `AvatarDashboardController`
- `AvatarDashboardComponents`
- `src/main/resources/static/css/avatar-dashboard.css`
- possibly shared operational CSS under `src/main/resources/static/css/orchestration.css` or `magenta.css` if the explorer is no longer Avatar-only.

The Avatar Work Areas tab/widget should become a consumer of the reusable explorer module instead of owning custom browser markup. Keep Avatar shell style: dense operational panels, thin borders, compact controls, first-viewport usefulness, and HTMX-first fragments.

### SimplyPages Upstream

Target repo:

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework`

Target packages:

- `simplypages/src/main/java/io/mindspice/simplypages/components/fileexplorer` or `.../components/navigation` plus `.../modules/FileExplorerModule`
- `simplypages/src/main/resources/static/css/framework.css`
- optional `simplypages/src/main/resources/static/js/framework-file-explorer.js` or narrow addition to `framework.js`
- demo route under `demo/src/main/java/io/mindspice/demo/...`
- docs under `docs/reference/`, `docs/patterns/`, and index updates.

SimplyPages must consume app-provided view models and endpoint URLs. It must not know Magenta workspace ids, Work Areas, SQLite, tags, audit logs, or Avatar.

## Data Model

### Labels/Tags

Add runtime DB tables in `schema.sql` and repository self-creation:

```sql
create table if not exists workspace_file_labels (
    id text primary key,
    slug text not null unique,
    display_name text not null,
    color text,
    system_flag integer not null default 0,
    metadata_json text not null default '{}',
    created_at text not null,
    updated_at text not null
);

create table if not exists workspace_file_label_assignments (
    id text primary key,
    workspace_id text not null,
    owner_type text not null,
    owner_id text not null,
    root_relative_path text not null,
    file_relative_path text not null,
    label_id text not null,
    metadata_json text not null default '{}',
    created_at text not null,
    updated_at text not null,
    unique(workspace_id, file_relative_path, label_id),
    foreign key(workspace_id) references workspaces(id),
    foreign key(label_id) references workspace_file_labels(id)
);
```

Indexes:

- by `workspace_id, file_relative_path`
- by `label_id`
- by `owner_type, owner_id`

Seed system labels:

- `note`
- `work-area`

Semantics:

- `note` marks a file as a note for Magenta/Avatar surfaces.
- `work-area` is assigned to directories that are marked or suggested as Work Areas. The durable Work Area record remains source of truth for execution routing.
- Custom tags are ordinary rows with `system_flag = 0`.

Path identity:

- V1 stores metadata by workspace id plus root-relative file path.
- Magenta-managed rename/move updates assignments to new path.
- Magenta-managed copy duplicates assignments to the copied target.
- External filesystem changes may orphan metadata; document and defer reconciliation.

### File Action Log

Prefer a dedicated table unless implementation proves `orchestration_events` is clearly the right service:

```sql
create table if not exists workspace_file_actions (
    id text primary key,
    workspace_id text not null,
    owner_type text not null,
    owner_id text not null,
    work_area_id text,
    actor_type text,
    actor_id text,
    action_type text not null,
    source_relative_path text,
    target_relative_path text,
    result text not null,
    payload_json text not null default '{}',
    created_at text not null,
    foreign key(workspace_id) references workspaces(id)
);
```

Indexes:

- by `workspace_id, created_at desc`
- by `owner_type, owner_id, created_at desc`
- by `work_area_id, created_at desc`
- by `action_type, created_at desc`

Action types:

- `CREATE_FOLDER`
- `CREATE_TEXT_FILE`
- `CREATE_MARKDOWN_FILE`
- `RENAME`
- `COPY`
- `MOVE`
- `DELETE_FILE`
- `DELETE_DIRECTORY`
- `TAG_ADD`
- `TAG_REMOVE`
- `SAVE_TEXT`
- `SAVE_MARKDOWN`
- `MARK_WORK_AREA`
- `UNMARK_WORK_AREA`

Payload should include file kind, byte size, deleted count, confirmation step, size-warning override, encoding result, and error message when applicable. Do not store file contents.

## Path Confinement

All operations must:

1. Resolve context through owner type/id and optional Work Area id.
2. Resolve root with `WorkAreaService.resolve(...)`, `WorkAreaService.ownerRoot(...)`, `WorkspaceService`, and `WorkspaceDirectoryService`.
3. Accept only relative paths from the browser/picker.
4. Normalize `\` to `/`.
5. Reject absolute paths.
6. Reject any `..` segment after normalization.
7. Resolve parent paths using `toRealPath` when they exist.
8. Reject symlink components and symlink targets for browse/edit/mutate unless a future explicit symlink policy is designed.
9. For write targets, ensure real parent starts with root.
10. Refuse root delete/rename/move/copy.

The browser must never show full host absolute paths. Display relative paths from explorer root.

## File Operation Semantics

### Rename

- Same-parent rename.
- Plain path segment validation for new name.
- Reject target if exists unless an explicit overwrite UX is designed later.
- Update tag assignments and action log in one service transaction after filesystem success.

### Move

- Source relative path and destination directory relative path.
- Optional new name, validated as plain segment.
- Reject moving a directory into itself or descendant.
- Use `Files.move`; if atomic move fails for cross-filesystem edge, use non-atomic move only if source and destination remain under same managed root and metadata update remains consistent.
- Update tag assignments for the moved path subtree.

### Copy

- Source relative path and destination directory relative path.
- Copy files or directory trees under root.
- Reject symlinks.
- Resolve collision policy before copying. V1 should reject existing targets rather than silently overwrite.
- Duplicate tag assignments to copied target paths.

### Delete

- File delete requires one modal confirmation.
- Directory delete requires two modal confirmations: delete intent, then recursive delete confirmation.
- No typed folder name.
- Preflight walks tree, rejects symlinks, rejects protected Work Area descendants and active Work Area references.
- Delete action log captures deleted count and target kind.

### Create

- Create folder uses plain segment validation.
- Create `.txt` and `.md` enforce extension and initial empty content.
- Creation routes return refreshed directory listing and optionally open the new editor.

## Text And Viewer Policy

### Text Detection

Use a bounded text policy:

- Extension allowlist for common text formats.
- UTF-8 validation for actual bytes.
- Strip UTF-8 BOM from editor content.
- Preserve existing LF/CRLF line endings on save by detecting dominant line ending from current file; new files default to `\n`.
- Unknown or invalid UTF-8 files return read-only metadata/download fallback.

### Size Policy

- Text edit normal limit: 10 MB.
- Markdown edit/render normal limit: 5 MB.
- Soft warning/open anyway: up to 25 MB.
- Above 25 MB: read-only metadata/download until chunked editing exists.

Implementation detail:

- The service should return `requiresWarning=true` with a reason and `maxSafeBytes` when a file is above normal but below hard limit.
- Save should reject if content bytes exceed the hard allowed edit limit for that type.

### Markdown

- `.md` and `.markdown` use tabbed View/Edit.
- View renders safe Markdown.
- Edit saves UTF-8 text with line-ending preservation.
- Returning to View after save rerenders from saved content.
- JavaScript may only manage local tab state/dirty state; server remains source of rendered content.

### Images

- Support safe image extensions and content types: PNG, JPEG, GIF, WebP, SVG only if sanitized or served as download; avoid inline SVG render unless safe policy exists.
- Use a controller endpoint that resolves root-confined path and emits content with safe content type.
- Show metadata and dimensions if cheap; do not block v1 on dimensions.

### Unsupported/Binary

- Show name, relative path, size, modified time, kind, and download action if allowed.
- Do not attempt text rendering or editing.

## Picker Modes

Generic modes:

- `OPEN_FILE`
- `OPEN_DIRECTORY`
- `SAVE_FILE`
- `SAVE_DIRECTORY`

Mode behavior:

- Open-file: directories navigate, files selectable.
- Open-directory: directories navigate and current directory selectable.
- Save-file: directories navigate, file name input visible, create `.txt`/`.md` if requested, existing collision requires confirmation.
- Save-directory: directories navigate and current directory/new directory selectable.

Picker must support:

- Creating folders.
- Renaming folders.
- Creating `.txt`/`.md` files where appropriate.
- Returning selected relative path(s) to the caller through HTMX fragment, hidden inputs, or a configured callback endpoint.

## API/Fragment Contract Shape

Use clear route families. Exact names may be adjusted during implementation, but keep responsibilities stable.

API candidates:

- `GET /api/work-areas/{id}/explorer?path=...`
- `GET /api/work-areas/{id}/explorer/preview?path=...`
- `PUT /api/work-areas/{id}/explorer/text?path=...`
- `POST /api/work-areas/{id}/explorer/folders`
- `POST /api/work-areas/{id}/explorer/files`
- `POST /api/work-areas/{id}/explorer/rename`
- `POST /api/work-areas/{id}/explorer/copy`
- `POST /api/work-areas/{id}/explorer/move`
- `DELETE /api/work-areas/{id}/explorer/files`
- `GET/POST/DELETE /api/work-areas/{id}/explorer/tags...`
- `GET /api/work-areas/{id}/explorer/image?path=...`

Fragment candidates:

- `GET /avatar/_work-areas/{id}/explorer`
- `GET /avatar/_work-areas/{id}/explorer/list`
- `GET /avatar/_work-areas/{id}/explorer/inspector`
- `GET /avatar/_work-areas/{id}/explorer/viewer`
- `GET /avatar/_work-areas/{id}/explorer/actions/{action}`
- mutation fragments returning OOB updates for list, inspector/viewer, status, and modal close.

## UI Design

Primary explorer layout:

- Toolbar: Back, Forward, Up, Refresh, New Folder, New Text, New Markdown, mode toggle.
- Breadcrumb: clickable relative segments from root.
- Main pane: card/list views. Directories use folder icon affordance; files use type/kind affordance.
- Right inspector: selected entry details, tags/labels, actions, note/work-area toggles.
- Viewer/editor area: text/markdown/image/metadata views, either in inspector lower pane or detail panel depending viewport.
- Modals: rename, copy, move, delete confirmations, tag creation, picker callbacks.

Responsive behavior:

- Desktop: two-pane explorer with right inspector.
- Mobile: toolbar wraps compactly, inspector becomes below-list/detail drawer, actions remain reachable and not clipped.

HTMX:

- Navigation and CRUD actions return fragments.
- Use OOB swaps for listing + inspector + modal/status updates.
- Use `hx-push-url` only where route history should be addressable and stable.

JavaScript:

- Narrow module for local browser history stack if HTMX history is insufficient.
- Dirty-state protection for editors.
- Markdown tab switch can request rendered fragment or refresh after save.
- No JS transport layer for standard CRUD.

## Compatibility And Migration

- Existing `/api/work-areas` routes may remain as compatibility wrappers during transition.
- Existing Work Area records remain valid.
- Additive schema migrations only.
- Existing Avatar Work Areas tab should retain route usability while UI changes.
- Old typed confirmation behavior should be removed from UI and service API once modal steps are implemented. If API compatibility demands keeping `confirm`, it should become a server-issued nonce/step token, not a typed folder name.

## Concurrency

- Filesystem operations are not globally locked in v1. Use atomic preflight plus postcondition checks.
- DB metadata updates should occur in service transactions after filesystem success.
- If DB update fails after filesystem mutation, log an error action and return a clear partial-failure response. Remediation should include metadata repair guidance.
- Avoid holding long DB transactions while recursively copying/deleting large trees. Preflight, mutate, then metadata/log transaction is acceptable, but tag-follow consistency must be tested.

## Security

- Never expose absolute host paths.
- Reject path traversal, absolute paths, symlinks, root mutation, active Work Area deletes, unsupported edit encodings, and oversized edits.
- Escape all rendered names, paths, tags, and metadata through SimplyPages components.
- Do not render raw Markdown HTML unless renderer sanitizes it.
- Keep controller authorization/CSRF behavior aligned with existing alpha route policy.

## Observability

- File action log is the durable observability source for user-visible workspace mutations.
- Include action ids in mutation responses where practical.
- Record failed attempts for destructive/mutating actions when they pass basic request validation but fail service preflight.
- Do not log file contents, secrets, or full host absolute paths.

