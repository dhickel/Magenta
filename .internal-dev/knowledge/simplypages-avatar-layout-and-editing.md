---
document_type: knowledge
status: active
created: 2026-05-23
---

# Topic

SimplyPages Avatar layout and editing patterns.

## Source References

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/getting-started/02-dynamic-pages-with-slotkey-rendercontext.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/getting-started/03-editing-system-first-implementation.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/03-template-rendercontext-slotkey-reference.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/editing-api-reference.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- Production Avatar UI files under `src/main/java/io/mindspice/magenta2/api/web/` and `src/main/resources/static/css/avatar-dashboard.css`

## Key Takeaways

- Use SimplyPages `Row` and `Column.create().withWidth(...)` for 12-column dashboard composition. Keep widths to practical presets such as 3, 4, 6, 8, and 12.
- Use in-place row/module controls for layout operations. The SimplyPages editing demo renders rows and modules as the real page structure, then places add-module and insert-row controls around that structure.
- Use `EditableModule.wrap(...)` and `EditModalBuilder` for module-specific editing where appropriate, but do not put layout positioning in a separate modal-only editor.
- Match the demo's visual hierarchy: the module/card content stays primary, edit/detail/delete controls are tiny top-corner decorators, `add-module-section` is a centered dashed affordance, and `insert-row-section` is a quiet separator. Large row headers, stacked movement buttons, and widget-internal resize panels are failure patterns for production `/avatar` edit mode.
- Use HTMX and OOB swaps for save/delete/add/move responses so the dashboard grid, modal container, and status fragments can update without a full page reload.
- Use `Template`, `SlotKey`, and `RenderContext` when a stable component structure is reused across requests. Do not share mutable component instances across concurrent requests.
- A scratch layout page can be useful for planning and Playwright experiments, but it is not a source of truth. Extract stable lessons into production components or this knowledge file.

## Engine Relevance

Avatar dashboard work must be treated as operational UI, not a landing page. Agents should compare screenshots to `/dashboard` and `/agents`, inspect SimplyPages docs/demos before changing UI, and actively critique practical layout quality.

For `/avatar`, a good layout has balanced width usage, coherent first-viewport density, aligned rows, clear hierarchy, compact action areas, no excessive dead space, no clipped controls, and responsive stacking without horizontal overflow.

In-place edit mode should decorate the real dashboard:

- row controls for move up/down and safe row delete as secondary controls near the row-level add-module affordance;
- centered add-widget controls and low-emphasis insert-row separators modeled after `.add-module-section` and `.insert-row-section`;
- widget top-corner controls for move left/right/up/down, resize to 3/4/6/8/12, remove, refresh, and open detail without consuming the widget content area;
- module detail modal/drawer for content-specific iteration only.

Validation must compare `/avatar?edit=true` against the live SimplyPages editing demo when layout editing is touched. A pass requires screenshot review for practical UI quality: no oversized editor chrome, no modal-only layout workflow, no stranded columns or massive gaps, no overlapped/clipped controls, and no mobile horizontal overflow.

## Open Questions

- Whether SimplyPages should grow a reusable first-class dashboard decorator component if this pattern repeats across more Magenta pages.
- Whether the Avatar scratch page should remain as an internal route after the refactor or be removed once production components stabilize.
