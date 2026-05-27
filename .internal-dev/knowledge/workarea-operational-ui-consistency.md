---
schema_version: 1
document_type: knowledge
date: 2026-05-27
owner: codex
status: active
---

# Topic

Work Area operational UI consistency for file browser, inspector, tag management, and editor/viewer surfaces.

## Source References

- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`

## Key Takeaways

- Work Area browser surfaces should read as dense operational tools, matching `/dashboard`, `/agents`, and `/avatar`: thin blue-gray borders, small radii, compact controls, row/list views, semantic chips, and bounded panels.
- Lists and tag inventories should be table-like or row-based, not stacked cards. Use cards for repeated dashboard widgets or framed tools, not for simple management rows.
- Standard file/tag CRUD and fragment refreshes should remain HTMX-first with stable targets and OOB refreshes where dependent regions must update together.
- Long filenames and paths must not widen the browser or inspector. Lock grid widths with `minmax(0, ...)`, use `min-width: 0`, and apply wrapping/truncation at the text element, not by allowing hidden overflow to push columns.
- Collapsed inspector rails should show only an obvious expand/collapse affordance. They should not show selected filenames, stale labels, root dots, or controls stranded at the bottom.
- Inspector tag management needs an explicit button. Empty boxes or hidden click areas are failed affordances even when wired correctly.
- Tag management should provide a bounded, scrollable modal with a top filter for Directory/File tags, a row view showing tag display name/slug/type/description, and a modal or focused editing area for a selected tag. Tag type must be explicitly constrained to directory or file.
- Editor/viewer modals must sit above the shell top navigation, have a clear close control, stable dimensions across Edit/Preview/Split mode changes, resize affordance on desktop, and top-left icon controls for save/undo/redo/revert.
- Markdown editor tabs should look like the project tab/switcher pattern, not loose text buttons. Mode switches must not resize the outer modal or move core controls.
- A CSS-only segmented filter is sufficient for tag Directory/File filtering when the server renders the full tag inventory and each row carries a stable type hook. This keeps tag browsing narrow and avoids adding modal-specific JavaScript for a display-only filter.
- Row-open tag editing should keep the row itself as the focus target and put assign/save controls in the opened detail area. That avoids nested click conflicts with full-row explorer selection and makes explicit buttons visible without adding tag deletion.
- Static Avatar CSS changes need the `avatar-dashboard.css` query version bumped on the Avatar shell; editor JavaScript versioning should only move when `avatar-workarea-editor.js` changes.

## Engine Relevance

Before Work Area UI edits, agents should inspect the existing Avatar dashboard and agent dashboard style plus the Work Area contracts. Implementation and validation should treat visual consistency as a first-class acceptance criterion, not a cosmetic follow-up.

Playwright validation should include screenshots and critique for desktop and mobile states, with explicit checks for top-nav overlap, modal scroll, long filename wrapping/truncation, collapsed rail affordance, visible tag-editor button, row-level click targets, and editor mode-switch stability.

## Open Questions

- Whether the Work Area tag manager should later become a shared SimplyPages row-management module.
- Whether file editor resizing should be implemented with CSS `resize` only or promoted into a reusable persisted geometry helper after more surfaces need it.
