# Worker Directive: Work Area Browser And Editor Remediation

## Objective

Fix the broken Avatar Work Area browser/inspector and markdown/text editor implementation so it is compact, professional, responsive, and stable. Keep this scoped to the Work Area browser rows, inspector/preview panel, row/header actions, and editor modal.

## Editable Scope

Primary editable targets:

- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-workarea-editor.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

Conditional targets:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java` only for tightly scoped preview wiring using existing service behavior.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java` only if service behavior changes, which should be avoided.
- Docs/closeout: `docs/end-user/avatar-dashboard.md`, `docs/end-user/projects-and-workspaces.md`, `docs/technical/avatar-dashboard-fragments.md`, `docs/technical/workspaces-tools-outputs.md`, relevant `.internal-dev/specifications/*.md` only if contracts shift, and a new `.internal-dev/changelogs/2026-05-27-workarea-ui-polish.md`.

## Forbidden Scope

- No tag deletion.
- No broad Work Area API redesign.
- No broad JS transport for CRUD/file operations.
- No CodeMirror or other editor dependency.
- No new reusable SimplyPages file explorer module unless the existing code already makes a trivial local extraction obvious.
- No path confinement, symlink, size-limit, workspace layout, or filesystem security changes unless a current test proves a defect directly caused by this UI work.

## Required Reading

Read before editing:

- `.internal-dev/plans/workarea-ui-polish/00-specification-lock.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/rendered-markdown-spacing.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`

## Implementation Steps

1. Inspect the current fragment/CSS/JS implementation and identify the smallest edits that satisfy the locked criteria.
2. Update `WorkAreaExplorerFragments.shell()` so the header command reads `Close Workspace` and keeps the current HTMX target/swap.
3. Update inspector rendering:
   - expanded state renders clear selected name, path, tags, `Tag Editor`, compact metadata, and a bounded preview box only;
   - remove bottom view/rename/delete/copy/move actions from inspector;
   - remove `Preview & Details` and `viewerHint(...)` prose;
   - collapsed state renders a compact rail/panel with a visible expand button and selected-path preserving route when available.
4. Implement inspector preview as a bounded UI fragment:
   - directories/unsupported/unavailable: `Preview unavailable`;
   - image: contained thumbnail using existing image view route;
   - text: compact escaped text excerpt from existing preview content when available;
   - markdown: compact rendered markdown excerpt/panel using existing safe markdown rendering where feasible;
   - do not read files outside existing service preview policy.
5. Replace row action text buttons with compact icon buttons:
   - Open/View, Rename, Delete, Copy, Move;
   - preserve existing HTMX routes, targets, swaps, picker behavior, and row click guard;
   - add needed `iconSvg(...)` cases in the existing local style;
   - include `aria-label` and `title` on every icon action.
6. Refine browser list CSS:
   - truncate long names and path/tag text;
   - constrain actions to fixed compact hit targets;
   - hide/compress secondary columns at narrow widths before actions;
   - avoid page-level horizontal overflow where feasible.
7. Refactor editor modal markup/CSS:
   - full modal window with max viewport bounds, internal scrolling, stable min/body dimensions, and CSS `resize` affordance where feasible;
   - top-right close button;
   - top-left icon controls for Save, Undo, Redo, Revert;
   - Edit/Preview/Split as real tabs below command row;
   - plain text editor shows only Edit.
8. Adjust `avatar-workarea-editor.js` only as needed for class/tab names, ARIA selected state, stable mode switching, local history controls, and optional resize behavior. Do not move save/file CRUD into JS.
9. Preserve rendered markdown spacing and overflow containment using the existing `.magenta-rendered-markdown`/Work Area markdown styles.
10. Update focused controller/fragment tests for markup contracts, icon actions, inspector states, preview fallbacks, close label, and editor chrome/tabs. Add service tests only if service policy changes.
11. Update docs and `.internal-dev` closeout artifacts. If specifications did not materially change, state `Specification Impact: none` in the changelog with a reason.

## Experience Contract

- Desktop: dense two-pane file-manager feel; browser list gets most width when inspector is collapsed; expanded inspector is useful but not dominant; row actions are visually scannable icon controls.
- Mobile/narrow: no horizontal page overflow; secondary metadata columns may hide; actions remain reachable; inspector and editor stack cleanly.
- Collapsed inspector: must look like an intentional collapsed rail with an expand affordance, not a broken clipped panel.
- Editor: modal should feel like a real editor surface, not a small confirmation dialog. Mode changes must not visibly shift overall modal height/width.
- Visual failures that block completion: clipped buttons, text overflowing action controls, horizontal page overflow, stranded collapsed inspector, inaccessible row actions, editor frame jumping between tabs, stretched image previews, flattened/overflowing markdown blocks.

## Acceptance Criteria

Use the acceptance criteria in `00-specification-lock.md` as binding. In addition, the worker must verify:

- `hx-trigger="click[!event.target.closest(...)]"` or equivalent row-action guard remains effective.
- Each icon action has a meaningful accessible label and tooltip.
- Inspector no longer contains bottom action group markup.
- Text/markdown preview and editor preview still escape/sanitize through existing rendering helpers.
- Save response still refreshes the modal/editor and relevant list/inspector regions as before.

## Validation Commands

Run after implementation:

- `git diff --check`
- Focused tests, at minimum the relevant `AvatarDashboardControllerTest` methods/classes, for example `mvn -Dtest=AvatarDashboardControllerTest test`
- `mvn test`
- Bounded startup smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

If startup fails due to missing secrets/services rather than code, capture the exact blocker for validator review.

## Do Not Close Unless

- All required code/docs/tests are updated.
- Automated validation commands have been run or their blockers are explicit.
- Browser validation handoff has enough detail for the Playwright agent to create fixtures and capture screenshots.
- Changelog exists and plan closeout requirements are ready for main-thread archival/commit.
