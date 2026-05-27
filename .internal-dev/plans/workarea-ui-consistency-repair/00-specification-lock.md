# Work Area UI Consistency Repair Specification Lock

## Planning Contract

Planning model requested by user: `gpt-5.5-high`.

Work classification: small. This is one coherent Work Area UI repair unit covering the browser/inspector, tag manager modal, and markdown/text editor modal. Do not expand into a large multi-agent suite.

Implementation model directive: use `gpt-5.4-high` because the current visible subagent route supports `gpt-5.4` and the user explicitly selected it for implementation in this plan request.

Model correction: browser proof defaults to `gpt-5.4` medium. Earlier lower-version wording was an erroneous local-default reference and should not be reused.

## Acceptance Criteria

- Collapsed inspector rail renders only a clear expand affordance. It must not show selected filename, `drafts`, root `.`, stale title text, or a stranded bottom icon.
- Expanded inspector and browser keep long filenames, paths, tag names, and descriptions within fixed layout bounds using `minmax(0, ...)`, `min-width: 0`, wrapping, truncation, and useful `title` text. Long content must not widen, hide-push, or horizontally overflow the browser/inspector.
- Whole browser rows select when clicking anywhere in the row except explicit controls such as buttons, anchors, inputs, selects, textareas, labels, and summaries.
- Inspector tag management opens through a visible real button, not an empty/hidden click target.
- Tag editor modal renders above the top navigation, fits the viewport, has a dedicated scroll body, and uses a compact row/table management UI.
- Tag manager has a top filter for directory/file tags. Tag rows show identity, type, and truncated LLM-friendly description. Clicking a row opens a focused edit modal/area. Create/edit constrains tags to directory or file. Deletion remains unavailable.
- Markdown/text editor modal renders above the top navigation, matches `/dashboard`, `/agents`, and `/avatar` operational styling, and has top-left icon controls, top-right close, real Edit/Preview/Split segmented tabs, stable outer dimensions across modes, bounded split panes, and a desktop resize corner.
- UI style consistency is a validation requirement, not a cosmetic follow-up. Browser proof must compare changed surfaces against `/dashboard`, `/agents`, and `/avatar`.
- Static CSS/JS asset versioning is bumped if cacheable assets change.
- Closeout updates relevant `.internal-dev`, `docs/`, and changelog artifacts. Email summary later is a main-thread responsibility, not part of this implementation worker directive.

## Negative Criteria

- Do not pass if a control is wired but visually reads as an empty box, hidden click target, browser-default form, or ambiguous icon without a useful label/title.
- Do not pass if the tag manager uses stacked card rows, scrolls the page behind the modal, exposes deletion, or allows unconstrained directory/file type edits.
- Do not pass if long paths widen the list/inspector, force page-level horizontal scrolling, or clip key metadata without a bounded/truncated presentation.
- Do not pass if editor mode switching changes the modal shell size, moves persistent controls, creates unbounded panes, or lets modal content render under the topnav.
- Do not pass browser validation if it is DOM-only. It must include screenshot-backed visual critique.

## Constraints

- Keep controllers thin and keep filesystem/path/tag validation in services.
- Preserve Work Area path confinement and existing service guards; do not weaken traversal, symlink, protected-root, active descendant, download-size, or text-edit checks.
- Keep HTMX as the default for standard CRUD, row actions, modal loads, form submissions, and dependent fragment refreshes.
- Keep JavaScript narrow to editor-local mode switching, dirty state, undo/redo/revert, preview sync, and resize behavior where it is the simplest path.
- Match the Avatar/agent dashboard visual system: dense operational tooling, compact blue-gray bordered panels, small-radius controls, icon buttons for common commands, semantic chips, row/list management surfaces, and bounded scrollable modals.
- Avoid broad SimplyPages upstream work. If topnav layering proves to be a SimplyPages shell bug rather than local modal/CSS layering, document the evidence and stop/escalate before changing the library.

## Assumptions

- `WorkAreaExplorerFragments.java`, `avatar-dashboard.css`, and `avatar-workarea-editor.js` are the primary implementation surfaces.
- Existing tests in `AvatarDashboardControllerTest` and `WorkAreaExplorerServiceTest` are the likely focused test anchors.
- Relevant living contracts are `.internal-dev/specifications/web.md` entries `WEB-20260525-04` and `WEB-20260526-01`, plus `.internal-dev/specifications/simplypages.md` entries `SP-20260525-04` and `SP-20260526-01`.
- The tag manager can be made row-based and focused without a new reusable SimplyPages module in this small scope.

## Non-Goals

- No tag deletion.
- No generic file explorer rewrite.
- No Work Area service/path architecture rewrite.
- No large SimplyPages framework rewrite.
- No broad end-to-end production browser campaign beyond focused changed-surface validation.
- No email sending from worker or validator phases.

## User Decision Gates

- Stop and return to the main thread if the topnav/modal layering requires upstream SimplyPages changes.
- Stop and return to the main thread if validation/browser model requirements cannot be satisfied by available routes without an unapproved fallback.
- Stop and return to the main thread before adding any new durable product capability beyond the locked UI repairs, such as tag deletion, persisted editor geometry, or reusable framework extraction.
