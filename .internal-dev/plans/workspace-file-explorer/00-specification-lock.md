# Specification Lock

## Objective

Build a better Magenta2 workspace-root-confined file explorer/directory browser that feels familiar to Linux/Windows users, supports file actions and safe viewers/editors, and provides reusable picker dialogs for app flows. Extract the generic browser/picker UI into SimplyPages and prepare a publishable upstream PR while keeping Magenta-specific workspace, tag, audit, and Avatar semantics in Magenta.

## Source Inputs

Verified repo guidance:

- `AGENTS.md` requires `.internal-dev` beginning pass, plans under `.internal-dev/plans/`, HTMX-first SimplyPages UI, docs updates for feature/API/schema changes, Playwright screenshots for UI changes, Spring startup smoke tests, phase commits, and dedicated branches for multi-phase work.
- `.internal-dev/AGENTS.md` defines the plan/changelog/bug/knowledge/focus workflow and controlled `.internal-dev` access.
- `.internal-dev/focus/current-focus.md` says Avatar sprint implementation is complete and may need user review for next durable direction.
- `.internal-dev/focus/unfinished-work.md` contains deferred Avatar refresh/history work but no active file-explorer blocker.
- `.internal-dev/focus/architecture-focus.md` keeps Avatar Work Areas as runtime-owned metadata around confined agent/project directories.
- `.internal-dev/focus/decisions.md` records HTMX-first Avatar and SimplyPages-native layout decisions.
- `.internal-dev/notes/current-architecture-focus.md` defines effective workspace rules, runtime aliases, and workspace/output boundaries.
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md` and `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md` define Avatar operational UI style and SimplyPages reuse expectations.

Verified source anchors:

- Current Work Area API: `src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java:33`.
- Current explorer service: `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java:17`.
- Work Area metadata service: `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaService.java:13`.
- Work Area repository/schema bootstrapping: `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaRepository.java:13`.
- Canonical workspace/action schema anchors: `src/main/resources/schema.sql:131`, `src/main/resources/schema.sql:458`, `src/main/resources/schema.sql:279`, `src/main/resources/schema.sql:403`.
- Current Avatar explorer fragments: `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:534` and `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:978`.
- Current explorer docs: `docs/technical/workspaces-tools-outputs.md:57` and `docs/end-user/avatar-dashboard.md:35`.
- SimplyPages HTMX patterns: `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md:61`.
- SimplyPages SlotKey/Template guidance: `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/03-template-rendercontext-slotkey-reference.md:51`.
- SimplyPages catalog includes forms, display, media, navigation, and module primitives: `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/components-and-modules-catalog.md:7`.

Memory-derived guidance:

- Durable planning artifacts belong in `.internal-dev/plans/<slug>/`.
- Prior SimplyPages upstream module work should be treated as publishable branch/PR work, not as only local implementation.
- These are guidance memories and must be revalidated during implementation against live branch state and upstream dirty worktree state.

## Locked Decisions

1. Scope is workspace-root inward only. No arbitrary filesystem browsing and no navigation above the resolved Magenta owner root or selected Work Area root.
2. The main explorer must provide native-feeling directory navigation: click-to-open directories, Back, Forward, Up, clickable relative breadcrumb/path, refresh after mutations, file/folder cards, and compact list mode if feasible without disrupting v1.
3. File and folder actions must include rename, delete, copy, move, and tag/label.
4. Right-side inspector/menu must support labels/tags. V1 tag meanings include `note` and `work-area`; schema/model must allow future custom user tags.
5. Tags live in the Magenta DB and are generic labels. Tags follow files during Magenta-managed rename, move, and copy. External filesystem changes may orphan metadata unless a future reconciliation design is explicitly added.
6. Mutating/destructive file actions must be logged for later Avatar dashboard visibility. Prefer an existing durable event/audit service if appropriate; otherwise add a minimal durable file action log and defer richer Avatar display.
7. Delete confirmations are modal-based: one confirmation for files; two-step confirmation for directories, first delete intent then recursive delete confirmation. No typed folder name.
8. Text files open in a viewer/editor with save.
9. Markdown files use View/Edit tabs, render on View, and rerender when returning to View after edits.
10. Images use a simple viewer.
11. Unsupported/binary files show metadata and safe fallback/download; they must not be blindly rendered or edited.
12. Text save compatibility: write UTF-8, strip UTF-8 BOM, preserve existing LF/CRLF line endings on save, and refuse unknown/non-UTF-8 rewrite unless the user explicitly chooses a safe path designed later.
13. Size limits: normal text edit limit 10 MB; normal markdown edit/render limit 5 MB; soft warning/open anyway up to 25 MB; above 25 MB read-only/metadata/download unless later chunked editing exists.
14. Picker dialog must support open/save style flows for app features that choose directories, files, or save locations. It supports file/directory selection by mode, folder creation, folder rename, and creating `.txt`/`.md` files where appropriate.
15. File/folder creation includes create folder, rename folder, create `.txt`, and create `.md`.
16. SimplyPages owns a reusable generic module; Magenta owns root resolution, authorization/confinement, DB-backed tags, action logging, Avatar semantics, Work Area semantics, and application-specific route wiring.
17. Default UI interaction policy is HTMX-first. JavaScript is allowed only for narrow local behaviors such as browser-side tab switch state, editor dirty-state prompts, markdown refresh/viewer affordances, and back/forward history when HTMX alone is awkward.

## Assumptions To Verify

- Existing `WorkAreaExplorerService` can be refactored or wrapped rather than replaced wholesale.
- SQLite migration style remains repository-owned self-creation plus canonical `schema.sql`.
- Existing `orchestration_events` may be a better fit than chat `audit_event` for non-chat file action visibility; this must be verified before choosing the final table/service.
- Current app security posture is alpha/open access, but route safety must still enforce path confinement and destructive confirmation.
- SimplyPages upstream checkout may have unrelated dirty files; upstream worker must isolate via branch/worktree/temp clone or preserve those changes.
- Markdown rendering can reuse existing `ChatMarkdownRenderer` only if its package boundary and security behavior are appropriate; otherwise use a dedicated safe renderer or SimplyPages `Markdown` where suitable.

## Open Questions

1. Should `note` and `work-area` labels be system-reserved tags with immutable slugs, or user-visible tags with protected semantics only in Magenta service code?
2. Should copy inherit tags by default, or should copied entries get duplicated tags with provenance metadata? Locked default for implementation should be copy-inherits-tags unless user overrides.
3. Should file action logging use `orchestration_events`, a new `workspace_file_actions` table, or both? The plan prefers a new minimal action-log table if `orchestration_events` semantics are too automation-oriented.
4. Should the picker launch inside Avatar only in v1, or should it immediately replace all relevant app selectors that choose output paths? Implementation should start with one integration path and avoid broad app rewrites.
5. Should image viewing stream through a controller endpoint or use existing download URLs with inline content disposition for safe image types? Implementation must choose based on route security and browser behavior.

## User-Decision Gates

- Gate U1: If implementation proves that `orchestration_events` is unsuitable and a new durable action-log table is needed, proceed with the minimal table unless the user rejects schema growth.
- Gate U2: If upstream SimplyPages dirty state prevents clean branch/PR work, stop and ask whether to use a temporary clean clone, a worktree, or the current checkout. Do not overwrite existing upstream changes.
- Gate U3: If any existing user flow requires arbitrary filesystem browsing outside workspace roots, stop and ask. It is out of scope.
- Gate U4: If non-UTF-8 editing is requested during implementation, stop and design explicit encoding handling; do not silently rewrite.

## Non-Goals

- No arbitrary filesystem browser.
- No navigation above workspace/Work Area root.
- No external filesystem metadata reconciliation in v1.
- No chunked large-file editor.
- No rich binary preview beyond simple images.
- No full Avatar dashboard file-action timeline beyond durable logging hook.
- No broad project/workspace runtime redesign.
- No replacement of ordinary `/chat` conversation file handling unless explicitly integrated later.
- No JavaScript-heavy SPA rewrite.
- No implementation while this planning request is active.

## Constraints

- Preserve user-modified `.internal-dev/inbox/queue.md` and `.internal-dev/inbox/read.md`.
- Use existing Magenta package boundaries: web/API under `io.mindspice.magenta2.api`, workspace behavior under `ai.orchestration.workspaces`, Avatar data under `avatar`, shared utility under `core`.
- Controllers remain thin; services own use-case behavior; repositories own persistence.
- Request/response payloads and internal carriers should use Java records where practical.
- Path resolution must normalize separators, reject absolute paths and traversal, reject symlink escape, and resolve real paths under configured data root.
- UI must use SimplyPages components/modules and HTMX fragments by default.
- Multi-phase implementation must start on a dedicated branch and commit each phase.

## Acceptance Criteria

AC1. Explorer is confined to a Magenta workspace/Work Area root and cannot navigate or mutate above it, including through `..`, absolute paths, separator tricks, stale stored paths, or symlinks.

AC2. Main explorer renders a native-feeling browser with Back, Forward, Up, breadcrumb path, refresh, directory click navigation, card mode, and compact list mode if feasible.

AC3. File/folder actions support rename, delete, copy, move, and tag/label, with refreshed explorer state after each mutation.

AC4. Tags are persisted in DB as generic labels, support v1 `note` and `work-area` semantics, support future custom tags, and follow Magenta-managed rename/move/copy operations.

AC5. Mutating/destructive file actions are durably logged with actor/source, owner/workspace context, action type, source path, target path where relevant, result, timestamp, and enough payload for later Avatar dashboard visibility.

AC6. Delete UX uses one modal confirmation for files and two-step modal confirmation for directories without typed folder-name confirmation.

AC7. Text and Markdown viewers/editors obey size and encoding rules, strip UTF-8 BOM, preserve LF/CRLF line endings on save, and refuse unknown/non-UTF-8 silent rewrites.

AC8. Markdown supports View/Edit tabs, safe render on View, save in Edit, and rerender after returning to View.

AC9. Images render through a simple safe viewer; unsupported/binary files show metadata and safe fallback/download only.

AC10. Picker dialog supports open-file, open-directory, save-file, and save-directory-style flows as applicable, with mode-specific selection, folder creation, folder rename, and `.txt`/`.md` creation.

AC11. SimplyPages upstream PR adds reusable generic FileExplorer/FilePicker rendering/module primitives, docs, demo, CSS/tests, and no Magenta-specific workspace/tag/audit code.

AC12. Magenta integrates the reusable SimplyPages module while keeping Magenta-specific route/service/schema behavior local.

AC13. Docs and API references reflect the implemented user behavior, route contracts, schema additions, validation limits, and known external-change metadata orphan caveat.

AC14. UI validation includes Playwright screenshots, desktop/mobile coverage, interaction checks, and visual quality critique.

## Validation Criteria

- VC1. Focused Java service tests cover path traversal, symlink escape, root confinement, rename/move/copy/delete/create, size limits, UTF-8/BOM/newline behavior, unsupported binary fallback, and tag-follow semantics.
- VC2. Repository/schema tests cover tag tables, file metadata records if added, action-log persistence, idempotent migrations, and clean startup from empty SQLite.
- VC3. Controller/API tests cover success and error status/fragment shapes for browse, preview, edit, save, create, rename, copy, move, delete confirmation steps, tags, image view, and picker modes.
- VC4. Spring context smoke test passes with bounded startup.
- VC5. Playwright validation by a validation subagent covers `/avatar` Work Areas and at least one picker integration target on desktop and mobile, including screenshots and critique of density, hierarchy, overflow, spacing, controls, tabs, modals, and root confinement affordances.
- VC6. Upstream SimplyPages tests cover module rendering, HTMX attribute contracts, inspector slot, breadcrumb, modes, confirmation modal markup, and demo route integration.
- VC7. Upstream SimplyPages demo runs and visually proves explorer/picker shells without Magenta dependencies.
- VC8. Validation red-team tries malicious paths, stale paths, symlinks, deep directories, large files, binary files, concurrent mutation collisions, and external filesystem metadata drift.

## Stop Rules

- Stop if any path traversal, symlink escape, or navigation above root succeeds.
- Stop if delete can remove the Work Area/root itself or active protected Work Area descendants.
- Stop if unknown/non-UTF-8 content is silently rewritten.
- Stop if tags do not follow Magenta-managed rename/move/copy.
- Stop if destructive actions are not logged durably.
- Stop if UI validation cannot run and the user has not approved deferral.
- Stop if upstream SimplyPages work would overwrite unrelated dirty changes.
- Stop if implementation requires broad runtime/workspace redesign outside this suite.

