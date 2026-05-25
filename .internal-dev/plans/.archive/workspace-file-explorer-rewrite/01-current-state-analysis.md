# Current State Analysis

Status: planning-only analysis
Created: 2026-05-24

## Verified Branch And Dirty State

- Current branch at planning time: `feature/workspace-file-explorer`.
- At planning time, the removed repo-local email ledger files were dirty remnants of a superseded email-ledger workflow.
- The email ledger has been removed. Email coordination for this plan uses direct AgentMail daemon/wait state through `mailctl status`, `mailctl next`, and `mailctl wait`.

## Existing Plan Relationship

Existing suite `.internal-dev/plans/workspace-file-explorer/` defined the earlier approach and execution map. It is now superseded for product direction because it accepted a card-capable/card-oriented explorer outcome. This new suite is authoritative for the rewrite.

Do not move/archive `.internal-dev/plans/workspace-file-explorer/` during planning or early implementation. Phase 06 may mark/archive it only during closeout after this rewrite passes validation.

## Existing Workspace Domain Surface

Primary files:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileActionLogRepository.java`
- Supporting records in `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- `src/main/resources/schema.sql`

Observed behavior:

- `WorkAreaExplorerService` lists directories, previews safe text/Markdown/image metadata, saves safe text, creates folders/text/Markdown, renames, moves, copies, deletes with preflight, marks nested Work Areas, labels paths, and records file actions.
- Path resolution rejects absolute paths, traversal, symlink path components, and non-existing paths for read operations.
- Copy/move/rename/delete call metadata update/copy/delete hooks.
- `Entry` currently exposes name/path/directory/regular/size/modified, but not a dedicated file type label, created timestamp, per-row tags, or viewer support flags.
- Created timestamp portability is not handled in the current exposed entry model.
- Tag repository currently uses `workspace_file_labels` and `workspace_file_label_assignments`; it supports slug creation, assignment, removal, labels for path/subtree, move/copy/delete subtree behavior, and system labels `note` and `work-area`.
- There is no obvious first-class UI/API for creating custom tags apart from `ensureLabel` through add-label by slug.
- Action logging exists through `workspace_file_actions`.

Risks:

- Some controller errors still map message text to status codes; prior unfinished work already flags typed domain errors as deferred.
- Metadata can become orphaned after external filesystem changes; this is acceptable only if docs state the v1 caveat.
- Directory copy/delete walks must be revalidated for symlink rejection and self/descendant behavior.
- Created timestamp may be unavailable on Linux filesystems or Java providers and must be represented honestly.

## Existing Web/API Surface

Primary files:

- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`

Current API routes include:

- `GET /api/work-areas`
- `POST /api/work-areas/home`
- `POST /api/work-areas`
- `DELETE /api/work-areas/{workAreaId}`
- `GET /api/work-areas/{workAreaId}/files`
- `GET /api/work-areas/{workAreaId}/files/preview`
- `GET /api/work-areas/{workAreaId}/files/view`
- `GET /api/work-areas/{workAreaId}/files/download`
- `PUT /api/work-areas/{workAreaId}/files/text`
- `POST /api/work-areas/{workAreaId}/directories`
- `POST /api/work-areas/{workAreaId}/files/text`
- `POST /api/work-areas/{workAreaId}/files/markdown`
- `POST /api/work-areas/{workAreaId}/files/rename`
- `POST /api/work-areas/{workAreaId}/files/move`
- `POST /api/work-areas/{workAreaId}/files/copy`
- `GET /api/work-areas/{workAreaId}/files/delete/preflight`
- `POST /api/work-areas/{workAreaId}/files/delete`
- compatibility `DELETE /api/work-areas/{workAreaId}/files`
- `GET|POST|DELETE /api/work-areas/{workAreaId}/files/labels`
- `GET /api/work-areas/{workAreaId}/files/actions/recent`
- `POST /api/work-areas/{workAreaId}/files/mark-work-area`

Avatar fragment docs currently list Work Area routes under `/avatar/_work-areas/{workAreaId}/...`. Current components render a modal/panel explorer and use imported `io.mindspice.simplypages.modules.file.FileExplorerModule`.

Current UI problem:

- `AvatarDashboardComponents` currently sets `FileExplorerMode.CARDS` for Work Area explorer state.
- Component code includes fallback card/list-pane classes such as `file-explorer-cards`, `file-explorer-entry`, and `file-entry-actions`.
- `enhanceWorkAreaExplorerModule(...)` modifies rendered module HTML with string replacements. This is brittle and must not survive as the primary rewrite technique.
- The explorer is opened inside an Avatar edit/modal surface rather than as a stable file-manager-like module with a separate right inspect panel.

## Existing Frontend Assets

Primary files:

- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/js/avatar-dashboard.js` if present
- Existing Avatar shell JS under `src/main/resources/static/js/avatar-*.js`

Observed CSS:

- Work Area explorer CSS currently accommodates `.file-explorer-module`, `.file-explorer-body`, `.file-explorer-list-pane`, `.file-explorer-inspector-pane`, `.file-explorer-entry`, and `.file-entry-actions`.
- Mobile CSS stacks existing explorer panes.
- Styling is operational and compact in broad Avatar shell, but current file-specific layout does not meet the required familiar details/list explorer contract.

## Existing SimplyPages Context

Required docs/source inspected:

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/components-and-modules-catalog.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/03-template-rendercontext-slotkey-reference.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/java/io/mindspice/simplypages/components/display/Table.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/java/io/mindspice/simplypages/components/display/DataTable.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/java/io/mindspice/simplypages/components/display/Modal.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/java/io/mindspice/simplypages/components/Markdown.java`

Findings:

- SimplyPages has request-scoped mutable `Table` and object-backed `DataTable`; `Table` supports component cells and is a better fit for row actions and tags.
- SimplyPages `Markdown` escapes raw HTML and sanitizes URLs by default.
- SimplyPages `Modal` validates modal ids and supports server-rendered modal bodies, but relies on inline close handlers; use existing patterns carefully.
- HTMX docs prefer stable target IDs, one primary endpoint target, and OOB updates for multi-target changes.
- Magenta currently depends on local Maven `io.mindspice:simplypages:1.1.0a`, whose jar contains `modules/file/*`, but those sources were not present in the checked-out SimplyPages source during this analysis. Treat this as a dependency/source drift risk.

## Existing Tests

Relevant current tests:

- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileActionLogRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkAreaControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

Likely gaps:

- Details/list table contract tests.
- Created timestamp/unknown created behavior.
- Full custom tag create/list UI/API behavior.
- Viewer modal tabs and Markdown failure fragment behavior.
- Inspect panel mirrored rename/delete/copy/move behavior.
- No-card regression tests.
- Playwright screenshots for desktop/mobile details/list explorer.

## Architecture Fit

The rewrite fits current architecture:

- Work Areas remain metadata around confined directories.
- Filesystem behavior stays under `ai.orchestration.workspaces`.
- Web routes stay thin and delegate to services.
- Avatar shell remains operational UI with HTMX fragments.
- No new runtime/workspace abstraction is required.

## Senior Engineer Notes

The useful existing work is the confined filesystem service and metadata persistence, not the current visual system. Preserve and harden service invariants before touching UI. Avoid letting view needs pull filesystem logic into `AvatarDashboardComponents` or controllers. If the local SimplyPages `FileExplorerModule` cannot render exactly the required details/list layout without string post-processing, replace the Magenta usage with a Magenta-specific SimplyPages renderer built from `Table`, `Modal`, `Markdown`, `Button`, and `Div` primitives; do not fight the old module into shape with HTML replacements.
