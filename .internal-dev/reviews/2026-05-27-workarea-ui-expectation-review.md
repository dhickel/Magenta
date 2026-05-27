# Work Area UI Expectation Review

## Scope

Review-only pass for Work Area file explorer side inspector, tag editor modal, and markdown/text editor modal before implementation planning.

## Findings

### Visual System

Magenta Work Area UI should remain in the Avatar/agent-dashboard operational system:

- dense operational surfaces, not consumer dashboard cards or marketing layout;
- thin blue-gray borders, low shadow, small radii, compact controls, compact headings;
- stable details/list file explorer with path controls, fixed columns, full-row selection, and separate inspector;
- icon toolbar buttons for common actions, semantic chips for tags/status, concise labels where clarity matters;
- bounded panels and modals with no content under top navigation and no hidden click targets;
- row/table-like management surfaces for tags, not nested card stacks;
- HTMX-first CRUD, row actions, modal refresh, and inspector/table OOB refresh, with narrow JS only for editor-local mode switching, dirty state, undo/redo/revert, preview sync, and resize behavior.

### Side Inspector

- Collapsed inspector currently compresses selected filename/title into a 3.1rem rail, then spaces the expand icon toward the bottom. This matches the visible stray title/period and stranded icon symptom.
- Long filenames and paths are partially ellipsized in the table, but inspector and tag rows still use nowrap/flex patterns that can create hidden width instead of wrapping/truncating inside fixed bounds.
- The tag editor affordance must be a visible button such as `Manage Tags`, not an empty or ambiguous click area.

### Tag Editor

- Generic modal z-index and whole-panel overflow are insufficient for this surface. It needs a high overlay layer, dedicated header/body/footer geometry, and an internal scroll body.
- Existing tag rows are visually card-like. The requested UI is a compact row/table management view with columns for display name, slug, type, truncated description, and action.
- Directory/file type must be explicit and constrained; deletion remains out of scope.

### Markdown/Text Editor

- The current editor has the right ingredients: save/undo/redo/revert, mode buttons, local JS, and resizable panel.
- It still needs stronger Avatar/agent-dashboard hierarchy: header row, compact command group, segmented tab switcher, bounded editor/preview panes, stable dimensions, clear close button, visible resize corner, and validated top-nav layering.

## Risk Assessment

- Visual regressions are likely if validation checks only DOM wiring and not screenshots.
- Long filename fixtures are required because normal names hide the layout bug.
- Modal z-index/top-nav issues may be project CSS layering or SimplyPages shell interaction; implementation should diagnose locally before assuming upstream fault.
- Tag management has enough UI structure risk that it should be treated as a small rewrite of that modal, not a polish pass.

## Recommendations

- Keep this as one focused Work Area UI repair unit.
- Require Playwright screenshots for desktop, narrow desktop/tablet, and mobile.
- Require screenshot-backed critique against `/dashboard`, `/agents`, and `/avatar` visual language.
- Validate collapsed/expanded inspector, long filename selection, explicit tag button, tag modal scroll/filter/row view, markdown editor tab stability, and z-index/top-nav behavior.

## Follow-ups

- Consider promoting the row-based tag manager and editor modal shell into reusable SimplyPages/Magenta components if a second surface needs the same pattern.
