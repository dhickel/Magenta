# Work Units

Status: domain work-unit map

## Dependency Graph

1. WU-01 Research/spec reconciliation
2. WU-02 Domain entry metadata and tag semantics
3. WU-03 Safe operations hardening
4. WU-04 API and fragment contract
5. WU-05 Details/list explorer UI
6. WU-06 Viewer modal and text/Markdown/image behavior
7. WU-07 Copy/move/rename/delete UX completion
8. WU-08 Validation, docs, closeout, supersession

WU-02 and WU-03 precede WU-04. WU-04 precedes WU-05/WU-06/WU-07. WU-05 precedes full Playwright validation. WU-08 runs only after phase validation passes.

## WU-01 Research/Spec Reconciliation

Why: confirm branch, source drift, dependency drift, current plan supersession, and exact editable files before implementation.

Edit areas: `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md` only unless criteria ambiguity requires replanning.

Mapped phase: Phase 01.

## WU-02 Domain Entry Metadata And Tags

Why: the UI requires file type, created, modified, size, tags, and support flags per row. Tags must work for files and directories and support custom user tags.

Edit areas:

- `WorkAreaExplorerService.java`
- `WorkspaceFileMetadataService.java`
- `WorkspaceFileMetadataRepository.java`
- supporting records under `ai/orchestration/workspaces`
- `schema.sql` only if schema must change
- workspace service/repository tests

Mapped phase: Phase 02.

## WU-03 Safe Operations Hardening

Why: copy/move/rename/delete must be reliable and root-confined before UI exposes them more prominently.

Edit areas:

- `WorkAreaExplorerService.java`
- action log repository/service records as needed
- workspace tests

Mapped phases: Phase 02 and Phase 05 if UI exposes additional operation semantics.

## WU-04 API And Fragment Contract

Why: UI needs stable HTMX fragments for table, inspect panel, viewer modal, tags, and operation modals.

Edit areas:

- `WorkAreaController.java`
- `AvatarDashboardController.java`
- small web view-model/helper classes under `api/web`
- controller tests

Mapped phase: Phase 03.

## WU-05 Details/List Explorer UI

Why: the core miss was visual/interaction model. The replacement must be an immediately recognizable details/list explorer.

Edit areas:

- `AvatarDashboardComponents.java`
- new component/helper classes under `api/web` if needed
- `avatar-dashboard.css`
- minimal `magenta.css` only for shared reusable styling if justified
- Avatar controller/component tests

Mapped phase: Phase 04.

## WU-06 Viewer Modal

Why: text, Markdown, and image viewing/editing have specific modal/tab/default/failure contracts.

Edit areas:

- `AvatarDashboardController.java`
- `AvatarDashboardComponents.java`
- viewer helper classes under `api/web` if needed
- `avatar-dashboard.css`
- narrow JS only if necessary for tab switching/dirty state
- controller/component tests

Mapped phase: Phase 05.

## WU-07 Copy/Move/Rename/Delete UX

Why: operations must be surfaced from rows and inspect panel with consistent refreshes.

Edit areas:

- `AvatarDashboardController.java`
- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- operation tests

Mapped phase: Phase 05.

## WU-08 Validation, Docs, Closeout, Supersession

Why: feature is not done without docs, `.internal-dev`, final validation, phase commits, email report, and low-token listening.

Edit areas:

- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/avatar-dashboard.md` if present
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/frontend-htmx.md`
- package `AGENTS.md` files only if responsibilities changed
- `.internal-dev/changelogs/<date>-workspace-file-explorer-rewrite.md`
- `.internal-dev/knowledge/<topic>.md`
- `.internal-dev/focus/unfinished-work.md` for deferred/blocking items
- `.internal-dev/focus/architecture-focus.md` and `.internal-dev/focus/decisions.md` only for durable direction changes
- archive/supersession of prior plan only if closeout criteria pass

Mapped phase: Phase 06.

## Senior Engineer Notes

The work-unit split keeps backend invariants ahead of UI exposure. Resist doing UI first because that tends to normalize unsafe assumptions in form fields and fragment refreshes. Conversely, do not overbuild backend abstractions beyond what the details/list explorer needs. The target is concrete: row metadata, tags, inspect panel, viewer modal, and confined operations.
