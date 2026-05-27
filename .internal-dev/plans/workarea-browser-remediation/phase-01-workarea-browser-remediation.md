# Work Area Browser Remediation

## Context

Classification: small.

This is one single-agent remediation unit for the Work Area/workspace browser UI. Do not expand this into a medium or large orchestration suite: the edits are numerous but all belong to one coupled domain, the Avatar Work Area explorer and its workspace file metadata/tagging support.

Verified current anchors:

- `.internal-dev/specifications/web.md:22` locks the Work Area explorer as Magenta-local details/list fragments with a separate inspector and stable columns.
- `.internal-dev/specifications/web.md:27` locks the MVP browser surface to selected Work Areas and project directories, HTMX-first and service-owned.
- `.internal-dev/specifications/simplypages.md:20-23` requires HTMX-first Work Area/project browser interactions and no raw internal-root editors.
- `.internal-dev/specifications/services.md:19` makes `WorkAreaExplorerService` responsible for confined create/rename/move/copy/preview/save/delete/labels/action-log behavior.
- `.internal-dev/specifications/api.md:19` covers Work Area file API compatibility and status behavior.
- Current UI targets are `WorkAreaExplorerFragments` (`src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java:28-177`), Avatar Work Area routes (`src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:528-802`), Work Area cards (`src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1033-1063`), explorer CSS (`src/main/resources/static/css/avatar-dashboard.css:894-1045`), shared selector components (`src/main/java/io/mindspice/magenta2/api/web/selector/EntitySelectorComponents.java:19-77`), and workspace metadata (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataRepository.java:28-116`).

## Goal

Repair the broken Work Area/workspace browser implementation so users can enter Work Areas by clicking the card, browse with compact icon controls, create folders/files, preview metadata and content in a collapsible side panel, persist and reload typed file/directory tags through a shared progressive-search selector pattern, and move/copy files with a directory picker instead of typing internal paths.

## In Scope

- Work Area cards in the Avatar Work Areas surface.
- Avatar Work Area explorer toolbar, list/table, row actions, preview/metadata panel, and related CSS.
- HTMX fragment routes needed by the explorer in `AvatarDashboardController` and `WorkAreaExplorerFragments`.
- `WorkAreaExplorerService`, `WorkspaceFileMetadataService`, `WorkspaceFileMetadataRepository`, `WorkspaceFileLabel*` records, and route/request records only as needed for tag persistence/type guards, directory picking, and move/copy destination validation.
- Shared selector pattern reuse through `api/web/selector` or a narrow equivalent using the same progressive-search HTMX behavior.
- Focused tests under existing web/workspace/selector test packages, plus affected docs and `.internal-dev` closeout artifacts after implementation.

Likely editable targets:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java` if API label/tag/move/copy behavior changes.
- `src/main/java/io/mindspice/magenta2/api/web/selector/*` if the shared selector needs tag support/create-option support.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileLabel*.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkAreaControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/selector/*Test.java` if selector behavior changes.
- Workspace metadata service/repository tests if present or added.
- `docs/end-user/projects-and-workspaces.md`, `docs/end-user/avatar-dashboard.md`, `docs/technical/workspaces-tools-outputs.md`, and `docs/api/00-index.md` if route/payload docs change.

## Out of Scope

- No upstream SimplyPages PR or generic reusable file-browser module.
- No browser history, app navigation stack, URL stack, or custom back-stack behavior. `Back` means parent directory only.
- No exposing internal agent workspace roots, run staging, structural data-root paths, or system outputs as normal editable browser surfaces.
- No broad route rename, schema migration campaign, typed-error refactor, security/auth redesign, or unrelated Avatar dashboard layout refactor.
- No manual internal path entry for move/copy destinations in the final UI.
- No raw HTML/JS rewrite when SimplyPages components, existing fragments, or HTMX patterns are practical. Narrow JavaScript is acceptable only for mouse-positioned popover placement or other clearly local interaction that HTMX cannot express cleanly.

## Acceptance Criteria

- Work Area cards have no Browse button and no owner/path subtitle; clicking the card navigates into that Work Area browser surface.
- Toolbar uses icon buttons for Back, Refresh, New Folder, and New File. Back means parent directory only.
- New Folder creates a directory. New File lets the user choose txt or markdown before creation.
- The right side panel is a collapsible preview/metadata panel whose first visible content is the selected item full display name, then the tag editor, then preview/details.
- Tags save, reload, display, and remove correctly; remove `x` is red and vertically aligned with tag text.
- Tag selection uses the existing progressive-search selector pattern or a narrow extension of it, supports select-existing and create-new flows, filters by file/directory target type, and rejects wrong-type assignment server-side.
- Move and Copy are available from the actions menu and open a mouse-positioned popover/module with a directory picker destination UI.
- Move/copy cannot require typed internal paths and cannot escape the current agent/Work Area space.
- Long filenames do not grow the browser width; list names truncate/ellipsis, and full names remain visible in the panel plus tooltip/title or equivalent.
- Updated tests, docs, `.internal-dev` closeout, and final commit are completed according to repo workflow.

## Negative Checks

- No disabled Forward button or history-style Back behavior.
- No visible Browse button or "Choose Browse" text remains on Work Area cards/placeholder.
- No path/owner subtitle remains under Work Area card names.
- No move/copy text box that asks users to type an internal destination path.
- No client-only tag type guard; wrong-type assignment must fail on the server.
- No filename wrapping/overflow that widens the list/table or creates incoherent gutters.
- No duplicated modal/fragment IDs in HTMX swap targets.
- No broad SimplyPages/core framework change or unrelated Avatar dashboard layout refactor.

## Experience Contract

- Desktop layout: dense operational file-manager surface, stable list/table width, compact icon toolbar, preview/metadata panel on the side, no oversized editor blocks pushing the browser down.
- Mobile layout: Work Area cards remain tappable; explorer stacks without horizontal page overflow; long names truncate in the list and remain readable in the panel.
- Interaction model: HTMX handles navigation, CRUD, tag mutation, and partial refreshes. JavaScript, if any, is limited to local pointer-positioned popover placement.
- Visual failure examples to reject: stranded empty columns, giant text-heavy forms in the list area, clipped toolbar labels, tag chips with misaligned remove buttons, row height explosions from long filenames, and a collapsed panel affordance that is undiscoverable.

## Senior Guidance

Treat this as remediation of a broken implementation, not polish over the current behavior. Preserve the service-owned confinement contract, use the existing details/list explorer as the baseline, and keep UI state explicit in HTMX targets. When in doubt, prefer a smaller server-rendered fragment over a broader client-side state machine.

## Implementation Steps

1. Read governance and current contracts before editing:
   - `AGENTS.md`
   - `.internal-dev/AGENTS.md`
   - `.internal-dev/specifications/AGENTS.md`
   - `.internal-dev/specifications/web.md`
   - `.internal-dev/specifications/simplypages.md`
   - `.internal-dev/specifications/services.md`
   - `.internal-dev/specifications/api.md`
   - `.internal-dev/specifications/architecture.md` if path/workspace semantics are touched
   - `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
   - `.internal-dev/knowledge/workspace-file-architecture-rules.md`
   - `.internal-dev/knowledge/entity-selector-htmx-pattern.md`
   - closest package `AGENTS.md` files before editing code under `api/web`, `api/web/selector`, or `ai/orchestration/workspaces`.

2. Inspect current implementation before mutating:
   - `WorkAreaExplorerFragments`, `AvatarDashboardController`, `AvatarDashboardComponents`, `WorkAreaController`.
   - `WorkAreaExplorerService`, `WorkspaceFileMetadataService`, `WorkspaceFileMetadataRepository`.
   - `EntitySelectorComponents`, `EntityLookupService`, `EntityKind`, `EntitySelectorController`.
   - `avatar-dashboard.css`, relevant controller/service tests, and docs under `docs/end-user/` and `docs/technical/`.

3. Fix Work Area card entry:
   - Remove the visible `Browse` button from Work Area cards.
   - Make the whole card/list row clickable via HTMX to load `/avatar/_work-areas/{id}/explorer` into `#avatar-workarea-surface`.
   - Remove path/owner text under Work Area names. Keep names visually scannable and accessible; update the empty placeholder so it no longer says "Choose Browse".

4. Repair explorer toolbar:
   - Replace the current Up/Back/Forward text affordances with compact icon buttons.
   - `Back` uses an arrow icon and navigates to the parent directory, exactly the current Up behavior. At root, it is disabled or absent, but it must not call browser history or any app-level navigation stack.
   - `Refresh`, `New Folder`, and `New File` are icon buttons with accessible labels/tooltips.
   - `New Folder` opens a create-folder HTMX action.
   - `New File` opens a small dropdown/menu for `txt` and `markdown` and then launches the existing create-file flow for the selected type.

5. Repair browser list stability:
   - Long filenames must not grow the browser/list width. Use stable table/list columns, `min-width: 0`, fixed/responsive name cell constraints, and ellipsis/truncation by default.
   - Full display name must remain available in the preview/metadata panel and likely through a `title` attribute on the list name.
   - Avoid wrapping that creates tall, unstable file rows for ordinary long names.

6. Convert the side panel into a collapsible preview/metadata panel:
   - Panel top always shows the selected file/directory full display name.
   - Then render the tag editor.
   - Then render preview/details: file metadata, directory metadata, supported text/Markdown/image preview affordance, and unsupported state where applicable.
   - The panel must collapse to the side to give the browser more space without losing selected state. Keep the collapsed affordance compact and accessible.

7. Fix tags as a first-class editor:
   - Tags must save, reload, and display after refresh/reselection.
   - Tag removal `x` must align with tag text and be red.
   - Use or reuse the progressive-search selector module/pattern from `api/web/selector`: users can select an existing tag or type a new tag and choose a create option.
   - Group/filter tags by selected item type: directory tags for selected directories, file tags for selected files.
   - Add server-side guards so a tag typed/created for one target type cannot be assigned to the wrong target type. Existing legacy untyped assignments must not be lost; preserve display and choose the smallest compatibility behavior that avoids data loss.
   - Prefer using existing `metadata_json` for tag target type if it is sufficient. Do not add a schema migration unless current persistence cannot satisfy the requirement safely.

8. Add Move and Copy to the actions menu:
   - Add Move and Copy actions to row/panel action menus.
   - Opening Move/Copy shows a mouse-positioned module/popover. If a small JS helper is required for pointer placement, keep it local, documented by class/data attributes, and leave transport/mutations HTMX-first.
   - Destination selection must be a mini browser/directory picker like an OS folder select. Users must navigate/select directories within the current agent/Work Area space; they must not type internal paths.
   - Server-side move/copy still validates destination confinement under the same Work Area/agent-owned space and rejects traversal, absolute paths, symlink escapes, collisions, and invalid target types.

9. Update tests and docs:
   - Update focused controller/fragment tests for card clickability, toolbar icon/Back behavior, create-folder/create-file dropdown, panel ordering/collapse markup, tag add/remove/reload/type guard, move/copy menu and directory picker, and long filename truncation classes/attributes.
   - Update service/repository tests if tag typing, tag lookup, destination picking, or metadata persistence changes.
   - Update `docs/end-user/projects-and-workspaces.md`, `docs/end-user/avatar-dashboard.md`, `docs/technical/workspaces-tools-outputs.md`, API docs if routes/payloads change, and affected `.internal-dev/specifications/*` only if intended contracts change.
   - Add a `.internal-dev/changelogs/<date>-workarea-browser-remediation.md` closeout entry after implementation. Update `.internal-dev/knowledge/` only for reusable lessons or corrected assumptions.

## Validation

Implementation worker validation:

- Run focused tests first, for example:
  - `mvn -Dtest=AvatarDashboardControllerTest test`
  - `mvn -Dtest=WorkAreaControllerTest test`
  - `mvn -Dtest=EntitySelectorComponentsTest test` if selector behavior changes
  - targeted workspace metadata/service tests if repository/service logic changes
- Run broader automated validation after focused tests:
  - `mvn test`
  - `git diff --check`
- If Spring wiring, routes, repositories, or constructor dependencies changed, run bounded startup:
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Do not run Playwright inline as the implementation worker. Browser proof is delegated after code-level validation.

Validator checklist:

- Confirm this remained one work unit in the Work Area/workspace browser domain and did not grow unrelated architecture or generic SimplyPages work.
- Check all acceptance criteria in this file against code, tests, docs, and `.internal-dev` updates.
- Check application-contract fit against `WEB-20260525-04`, `WEB-20260526-01`, `SP-20260525-04`, `SP-20260526-01`, `SVC-20260525-06`, and `API-20260525-01`.
- Verify controllers remain thin and path/tag/destination security is enforced in services/repositories, not only in the UI.
- Verify the tag selector reuses the existing progressive-search HTMX pattern or a narrow extension of it, creates tags intentionally, filters by item type, and rejects wrong-type assignment server-side.
- Verify Move/Copy destination selection is a directory picker, not a text box for internal paths, and remains confined to the current agent/Work Area space.
- Verify filenames cannot expand list/table width; full names are still available in the panel and via tooltip/title or equivalent.
- Verify docs and `.internal-dev` closeout are accurate; no stale "Browse button", "manual destination path", "planned/not implemented", or obsolete artifact references remain.
- Verify automated test evidence and bounded startup evidence are present, or explicit blockers are reported.

Playwright/browser validation checklist for later browser agent:

- Start the app with a real local server and visit `/avatar` Work Areas on desktop and mobile viewports.
- Capture screenshots of the Work Areas surface before opening a Work Area, after opening a Work Area, with the preview/metadata panel expanded, and with it collapsed.
- Confirm clicking a Work Area card opens the explorer; no Browse button or path text appears under card names.
- Confirm toolbar icon buttons render cleanly: Back, Refresh, New Folder, New File. Back at a nested directory navigates to the parent directory only; Back at root is disabled/absent and never uses browser history.
- Confirm New Folder creates a folder and refreshes the list/panel.
- Confirm New File dropdown creates both `.txt` and Markdown files through the selected option.
- Confirm selecting a file and a directory shows full display name at the top, tag editor second, and preview/details below.
- Confirm adding an existing tag, creating a new tag, refreshing/reselecting, and removing a tag all work and display correctly. Removal `x` must be red and aligned with tag text.
- Confirm directory tags are offered for directories and file tags for files; attempt a wrong-type assignment if possible and verify it is rejected.
- Confirm Move and Copy open a mouse-positioned popover/module, allow directory picker navigation, and complete within the Work Area without manual path typing.
- Confirm a very long filename does not widen the browser/list area; it truncates/ellipsizes while the full name remains visible in the panel and tooltip/title.
- Include a visual critique: density, alignment, gutters, row height stability, panel collapse affordance, icon affordance, text wrapping, mobile stacking, and whether the Work Area browser still matches the dense operational Avatar/dashboard style.

## Exit Criteria

- One implementation worker can execute this file without additional architectural invention.
- All locked user requirements are implemented or explicitly blocked with a concrete reason.
- Focused and broad tests pass, or failures are documented with blocker status.
- Browser validation has been delegated and reconciled before final sign-off.
- Affected docs/specs/knowledge/changelog are updated according to `.internal-dev` workflow.
- Final commit includes implementation plus `.internal-dev` and docs updates unless the user explicitly says not to commit.

Do not close unless:

- The worker report lists every file changed and the reason it was changed.
- Test commands and results are recorded.
- Any skipped validation has a concrete blocker, not a substitute sign-off.
- Browser-validation handoff includes the checklist above and the local URL/fixture setup needed to run it.
- The worktree is ready for commit, or the final commit hash is reported if the worker/main thread performs the commit.

## Stop Conditions

- Stop and ask the main thread before adding a database migration, upstream SimplyPages changes, generic file-browser framework, auth/security posture changes, or browser/app history behavior.
- Stop if the existing selector infrastructure cannot support create-option tag selection without a broad rewrite; propose the smallest selector extension instead.
- Stop if real Work Area execution/browser validation is blocked by missing local services, secrets, or fixture setup; do not mark unit-only tests as full completion.
- Stop if implementation would expose internal workspace roots or require users to type internal paths for move/copy.
