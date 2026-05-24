# Current-State Analysis

## Verified Magenta Areas

### Web/API

- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java:33` owns `/api/work-areas`.
- Current API routes include list/ensure home/mark/unmark, directory files, preview, download, save text, create directory, rename, delete, and mark nested Work Area at `WorkAreaController.java:46`, `WorkAreaController.java:83`, `WorkAreaController.java:91`, `WorkAreaController.java:99`, `WorkAreaController.java:125`, `WorkAreaController.java:134`, `WorkAreaController.java:142`, `WorkAreaController.java:150`, and `WorkAreaController.java:159`.
- `WorkAreaController.java:36` currently limits downloads to 10 MB. The target design requires a different viewer/download policy, including read-only/download fallback above 25 MB.
- `WorkAreaController.java:150` exposes delete with `confirm`, currently passed through to typed-name confirmation in the service. Target UX removes typed confirmation and uses modal confirmation steps.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:534` exposes HTMX Avatar explorer fragments under `/avatar/_work-areas/{workAreaId}/...`.
- `AvatarDashboardController.java:570`, `AvatarDashboardController.java:586`, `AvatarDashboardController.java:603`, `AvatarDashboardController.java:620`, and `AvatarDashboardController.java:636` handle save, create directory, create text file, mark, and delete with Avatar-specific fragments.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:978` renders the current explorer inside an Avatar edit modal.
- `AvatarDashboardComponents.java:989` renders two inline create forms, `AvatarDashboardComponents.java:994` renders a parent `..` row, and `AvatarDashboardComponents.java:1105` renders row actions.

Current issue:

- The UI is a modal list browser rather than a native-feeling file explorer. It lacks Back/Forward state, Up button as a real toolbar control, breadcrumbs, side inspector, tag management, card/list mode, copy/move, image viewer, markdown View/Edit tabs, size/encoding policy, picker modes, and reusable SimplyPages ownership.

### Workspace Services

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java:17` owns current browse/preview/save/mutate behavior.
- Current limits are `MAX_TEXT_BYTES = 1 MB` and `MAX_PREVIEW_BYTES = 256 KB` at `WorkAreaExplorerService.java:19`. Target limits are 10 MB text, 5 MB markdown render/edit normal, warning/open anyway to 25 MB, read-only/metadata/download above 25 MB.
- Directory listing uses `Files.list`, maps entries, sorts directories first, and returns `DirectoryListing` at `WorkAreaExplorerService.java:28`.
- Preview reads UTF-8 text for safe extensions and otherwise returns `text=false` at `WorkAreaExplorerService.java:46`.
- Save writes UTF-8 bytes without preserving newline style or explicitly handling BOM/encoding at `WorkAreaExplorerService.java:75`.
- Create directory uses `Files.createDirectories` at `WorkAreaExplorerService.java:98`.
- Rename uses `Files.move(..., ATOMIC_MOVE)` within the same parent only at `WorkAreaExplorerService.java:113`; no copy/move-to-directory action exists.
- Delete is recursive, requires typed confirmation matching filename, rejects symlink paths, and deletes after preflight at `WorkAreaExplorerService.java:131`.
- Mark nested directory as Work Area delegates to `WorkAreaService.markDirectory` at `WorkAreaExplorerService.java:160`.
- Path confinement rejects absolute paths, traversal, symlink path components, and paths outside root at `WorkAreaExplorerService.java:179`, `WorkAreaExplorerService.java:193`, `WorkAreaExplorerService.java:211`, and `WorkAreaExplorerService.java:249`.
- Current safe text detection is extension-based at `WorkAreaExplorerService.java:270`.

Current issue:

- The service already has useful root confinement behavior but mixes v1 browser actions, typed delete UX, text compatibility shortcuts, and no metadata/tag/action-log semantics. It should become a more deliberate workspace file service or delegate to new focused collaborators.

### Work Area Metadata

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaService.java:13` owns Work Area metadata use cases.
- `WorkAreaService.java:47` creates/ensures `home/`.
- `WorkAreaService.java:54` marks existing directories and reactivates existing inactive records.
- `WorkAreaService.java:102` deactivates non-system Work Areas after active-use checks.
- `WorkAreaService.java:117` resolves a Work Area to a real directory.
- `WorkAreaService.java:125` resolves owner root.
- `WorkAreaService.java:235` normalizes Work Area relative paths and rejects root marking, absolute paths, and traversal.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaRepository.java:202` self-creates `work_areas`.
- Canonical schema defines `work_areas` at `src/main/resources/schema.sql:458`.

Current issue:

- Work Areas are directory metadata, not generic file tags. The new tag model should integrate `work-area` as a semantic tag/action without overloading `work_areas` for file labels.

### Workspace Roots And Confinement

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java:15` states all workspace paths are relative to configured `dataRoot`.
- Agent workspace roots are `agents/<id>/workspace` at `WorkspaceDirectoryService.java:55`.
- Project workspace roots are `projects/<projectId>/workspace` at `WorkspaceDirectoryService.java:235`.
- Durable workspace child dirs are created through typed helpers at `WorkspaceDirectoryService.java:89` through `WorkspaceDirectoryService.java:126`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RootRelativePathService.java:10` converts Magenta-owned filesystem paths to data-root-relative stored values and resolves them under current data root.
- `RootRelativePathService.java:81` rejects absolute stale paths outside current data root and traversal.

Current issue:

- Explorer/picker root resolution must reuse these services rather than accepting arbitrary filesystem paths.

### Assignment And Runtime Context

- `src/main/resources/schema.sql:279` defines `work_assignments`.
- Assignment records already have `selected_work_area_id`, `output_route_type`, `output_work_area_id`, and `output_direct_relative_path` at `schema.sql:292`.
- `docs/technical/workspaces-tools-outputs.md:85` documents Work Area routing metadata.
- `docs/technical/workspaces-tools-outputs.md:96` documents runtime alias behavior: `workspace/` resolves to selected Work Area and `root/` to owner root.

Current issue:

- The file explorer must not reinterpret assignment runtime aliases as arbitrary browser roots. It should resolve a requested owner/workspace/Work Area context through existing services and expose relative paths only.

### Audit/Event Logging

- `src/main/resources/schema.sql:131` defines `audit_event`, an append-only chat lifecycle table keyed by `conversation_id` and `sequence`.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AuditService.java:20` describes audit as chat turn side effects.
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java:34` self-creates/migrates the chat audit table and serializes inserts by conversation id at `AuditRepository.java:114`.
- `src/main/resources/schema.sql:403` defines `orchestration_events` with event type, source, payload, created/handled timestamps.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java:32` publishes orchestration events and optionally triggers reactions.
- Avatar has separate `avatar_events` in `src/main/resources/avatar-schema.sql:184`, and `AvatarService.appendEvent(...)` delegates to Avatar persistence at `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java:264`.

Current issue:

- `audit_event` appears chat-specific and is not a clean file-action log. `orchestration_events` is durable and generic but may imply reaction processing. A minimal workspace file action log is likely cleaner if Avatar visibility needs non-chat user actions without automation side effects.

### Tags/Labels

- No generic file tag table was found in `schema.sql`.
- Avatar notes have `tags_json` in `src/main/resources/avatar-schema.sql:106`, but that lives in `avatar.sqlite` and is note-specific.
- `AvatarNote` exposes tags, and `AvatarService.matches(...)` searches note tags at `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java:346`.

Current issue:

- Reusing Avatar note tags would violate the boundary that workspace metadata belongs in runtime/workspaces, not Avatar user-centric persistence. Add a runtime DB-backed generic file label model.

### Docs

- `docs/api/00-index.md:21` already documents `/api/work-areas` as confined browse/preview/download/text edit/directory/rename/delete/mark controls.
- `docs/technical/workspaces-tools-outputs.md:57` documents Work Areas and `docs/technical/workspaces-tools-outputs.md:105` documents current explorer behavior.
- `docs/end-user/avatar-dashboard.md:35` documents the current confined file explorer.
- `docs/technical/frontend-htmx.md:28` defines HTMX default policy; `docs/technical/frontend-htmx.md:64` defines SimplyPages reuse expectations.

Current issue:

- Docs will need updates for the new explorer UX, file action routes, tag schema, size/encoding policy, picker modes, and upstream reusable module dependency.

## Verified SimplyPages Areas

- Root guide `/home/hickelpickle/Code/Java/cannasite/java-html-framework/AGENTS.md` says SimplyPages is Java-first SSR, minimal JavaScript, HTMX-oriented, with package guides and changelog/docs/test requirements.
- Component guide says generic components should follow `HtmlTag` patterns, safe escaping, minimal composable APIs, and tests under component tests.
- Module guide says modules compose in `buildContent()`, avoid demo coupling, keep fluent APIs, and use module tests.
- Layout guide says Row/Column/Grid own layout and `Column.withWidth(int)` accepts `1..12`.
- Demo guide says demo code must be illustrative and must not redefine framework internals.
- HTMX docs require stable target contracts and fragment-friendly errors at `docs/patterns/03-htmx-endpoint-and-swap-patterns.md:61`.
- SlotKey docs recommend per-request `RenderContext` and Template reuse at `docs/core/03-template-rendercontext-slotkey-reference.md:51`.
- Catalog lists existing primitives useful for the module: `Div`, `Markdown`, forms, `Card`, `DataTable`, `Table`, `Alert`, `Badge`, `Tag`, `InfoBox`, `Image`, `Breadcrumb`, `Link`, `Row`, and `Column` at `docs/reference/components-and-modules-catalog.md:7`.

Current issue:

- There is no existing generic FileExplorer/FilePicker module in SimplyPages. The upstream PR should add one without Magenta-specific services or route assumptions.

## Known Dirty State

- Magenta worktree has user-modified `.internal-dev/inbox/queue.md` and `.internal-dev/inbox/read.md`; do not touch.
- SimplyPages checkout currently has unrelated dirty files (`.idea/workspace.xml`, `demo/pom.xml`, `docs/core/05-css-defaults-overrides-and-structure.md`, `pom.xml`, `simplypages/pom.xml`, `.gemini/`, `demo/.playwright-mcp/`). Upstream implementation must not overwrite these.

## Risk Summary

- Root escape is the highest-severity risk. Every browse/mutate path must stay relative and real-path-confined.
- Tag follow semantics are easy to miss on copy/move/rename; they need service-level transactional behavior and tests.
- Cross-filesystem `ATOMIC_MOVE` may fail. Move/copy implementation needs fallback and metadata consistency rules.
- External filesystem changes can orphan DB metadata; v1 must document this explicitly.
- Markdown rendering must avoid unsafe HTML/script injection.
- Browser back/forward state can become JS-heavy if not scoped. Keep it a narrow island or lean on HTMX history patterns where possible.
- Upstream SimplyPages branch/PR work is blocked until dirty-state isolation is resolved.

