# UI Standards Gate

## Scope

This gate applies only to the Avatar Work Area browser, inspector/preview panel, row/header actions, and markdown/text editor modal.

## Work Area Browser

- Use dense operational styling consistent with `/dashboard` and per-agent dashboard surfaces: thin blue-gray borders, compact controls, small radii, semantic chips, restrained shadows, and practical scan hierarchy.
- Long filenames, paths, tags, and metadata must truncate or wrap deliberately inside their own containers. They must not widen the list, action column, page, or modal.
- Avoid horizontal page overflow where feasible. At narrower widths, hide or compress low-priority columns such as type/size/dates before compromising name readability, row selection, or action access.
- Preserve full-row selection and simple directory-up Back semantics.

## Inspector

- Expanded inspector order: selected name, compact path/metadata, tags plus `Tag Editor`, metadata summary, bounded preview box.
- No bottom action buttons.
- No `Preview & Details` heading.
- No old explanatory hint prose.
- Collapsed inspector must be an intentional rail/compact panel with a clear expand icon button and enough width returned to the browser list.
- Preview box states:
  - directory/unsupported/unavailable: `Preview unavailable`;
  - image: contained thumbnail;
  - text/markdown: compact, bounded, escaped/sanitized preview using existing preview/rendering support.

## Row And Header Actions

- Row actions use icon buttons for Open/View, Rename, Delete, Copy, and Move.
- Every icon button has `aria-label`, `title`, visible focus styling, and a stable hit target.
- Do not rely on color alone to distinguish destructive actions.
- `Close Workspace` is a clear shell command, not a weak generic `Close`.

## Editor Modal

- The editor opens in a full modal window with max viewport bounds, stable body dimensions, internal scrolling, and CSS resize affordance where feasible.
- Modal chrome:
  - top-left icon controls: Save, Undo, Redo, Revert;
  - top-right close button;
  - tab row below commands: Edit, Preview, Split for markdown; Edit only for plain text.
- Mode toggles must not resize or jump the modal/editor frame.
- Source and preview panes share stable min heights. Split is two columns on desktop and stacks cleanly on mobile.
- Markdown rendering keeps scoped spacing for lists, blockquotes, and code blocks and prevents overflow.

## Blocking Visual Failures

Any of the following blocks sign-off:

- horizontal page overflow on target desktop/mobile widths;
- clipped icon controls or illegible labels/tooltips;
- collapsed inspector that looks like a broken clipped title;
- row action clicks selecting/navigating the row;
- editor modal jumping between modes;
- stretched image preview;
- flattened or overflowing rendered markdown;
- cramped or stranded mobile layout.
