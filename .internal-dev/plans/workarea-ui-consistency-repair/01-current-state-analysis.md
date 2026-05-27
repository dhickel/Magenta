# Work Area UI Consistency Repair Current State Analysis

## Verified Inputs

- `.internal-dev/plans/workarea-ui-consistency-repair/preplanning-handoff.md` locks this as small, single-agent UI repair.
- `.internal-dev/reviews/2026-05-27-workarea-ui-expectation-review.md` identifies the collapsed inspector, tag manager, long-name bounds, and editor modal as the failing surfaces.
- `.internal-dev/knowledge/workarea-operational-ui-consistency.md` gives the target operational UI contract and browser-proof expectations.
- `.internal-dev/specifications/web.md` owns Work Area explorer and browser validation contracts.
- `.internal-dev/specifications/simplypages.md` owns HTMX-first and SimplyPages composition expectations.
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md` requires thin controllers, service-owned Work Area guards, reusable components where practical, and screenshot-backed browser validation for web/UI changes.

## Current Code Observations

- `WorkAreaExplorerFragments.inspector(...)` currently renders a collapsed inspector label from the selected entry name or `Inspector`. This is the likely source of stale filename/root-dot text in the collapsed rail.
- `avatar-dashboard.css` styles `.file-explorer-inspector-collapsed-body` with `align-content: space-between` and a vertical label, which can strand the expand icon and create dead space.
- `WorkAreaExplorerFragments.row(...)` already has row-level `hx-get` with `hx-trigger="click[!event.target.closest(...)]"`. The worker should preserve and test this behavior, expanding exclusions only if required for explicit controls.
- Inspector tag management uses `button("Tag Editor", ...)`, but CSS/layout review must ensure it renders as a visible, labeled, discoverable button and not as an empty or hidden target.
- `tagEditorModal(...)` currently groups assigned tags, create form, directory tags, and file tags as stacked sections using `.file-entry-tag-editor`; rows exist but are visually card-like and there is no top segmented/filter control or focused edit row/area.
- `tagGroup(...)` displays display name, slug, type, description, and assign button, but description can wrap/widen in uncontrolled ways and row click/edit behavior is missing.
- `modal(...)` uses `.avatar-modal` with `z-index: 20`; editor modal uses `.avatar-modal-workarea-editor` with `z-index: 10000`. Tag modal likely needs the same high overlay discipline or a dedicated Work Area modal class.
- `textEditor(...)`, `modalEditor(...)`, `avatar-dashboard.css`, and `avatar-workarea-editor.js` already provide editor controls, tabs, preview sync, undo/redo/revert, and a resizable editor panel, but visual hierarchy and stable bounded dimensions require tightening.
- `AvatarDashboardControllerTest` already covers Work Area shell/list/inspector/modal/editor rendering and tag type mismatch behavior; update these focused assertions for the new structure.

## Architecture Fit

- The repair fits existing architecture if it stays in fragments, CSS, narrow editor JS, controller rendering tests, and service tests only where tag behavior is touched.
- No schema, persistence, route family, or Work Area filesystem service redesign is required by the locked scope.
- The UI should continue returning dependent regions through HTMX and OOB swaps where current routes already do so.
- Browser validation must be performed through the styled `/avatar` surface because direct fragment HTML can miss z-index, shell, and visual-system regressions.

## Risk Areas

- Long-name regressions are easy to miss without explicit fixtures. Tests and Playwright should use long filenames, long paths, long tag slugs, and long descriptions.
- Table/list fixed-layout CSS can still leak width if nested flex children lack `min-width: 0`.
- Modal z-index bugs may be local overlay layering or shell/topnav layering. Do not assume framework fault before capturing local evidence.
- Making tag rows clickable while preserving assign/edit controls can conflict with HTMX nested triggers. Explicit buttons/forms must remain isolated from row click behavior.
- Mobile stacking can regress if desktop fixed dimensions are applied without responsive bounds.
- Static asset caching can hide CSS/JS fixes unless the relevant version query is bumped.

## Validation Blind Spots To Close

- Screenshot-backed visual review must inspect desktop and mobile states, not just DOM assertions.
- Validation must compare visual language against `/dashboard`, `/agents`, and `/avatar`.
- Topnav layering must be observed with the live shell, especially tag and editor modals.
- Browser proof must check row selection by clicking whitespace in a row and also confirm buttons do not accidentally select/open the row.
- Editor proof must switch Edit, Preview, and Split and verify shell dimensions remain stable.
- Tag proof must verify filter behavior, scroll body, row identity/type/description display, row edit opening, type constraints, and no delete affordance.
