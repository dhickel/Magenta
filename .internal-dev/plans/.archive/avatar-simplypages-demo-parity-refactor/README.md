---
document_type: advanced-plan
status: active
created: 2026-05-23
owner: codex
---

# Avatar SimplyPages Demo Parity Refactor

## Objective

Refactor `/avatar` layout editing to match the SimplyPages `htmx-editing` demo interaction model: modules render as real dashboard cards, edit/delete/detail controls are small top-corner decorators, and add-module/add-row controls live in place between rows. The prior large row toolbar and widget-internal layout editor are rejected for visual quality.

## Inputs And Assumptions

- The reference implementation is `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`.
- The reference styling is `framework.css` classes `.editable-module-wrapper`, `.module-edit-btn`, `.module-delete-btn`, `.add-module-section`, and `.insert-row-section`.
- Playwright validation must compare `/avatar` normal and edit mode against `http://localhost:8080/demos/htmx-editing`.
- Normal mode may expose one small top-corner detail decorator; edit mode exposes layout controls.

## Scope

In scope:

- Replace bulky Avatar edit chrome with demo-inspired in-place decorations.
- Preserve HTMX-first layout operations and OOB grid refresh.
- Add insert-row-below support so controls mirror the demo.
- Update tests, docs, knowledge, and agent guidance.
- Run delegated Playwright visual validation with screenshots.

Out of scope:

- Rewriting all Avatar widget content forms.
- Making the SimplyPages library change upstream.
- Treating the scratch/demo area as a source of truth.

## Target Design

`AvatarDashboardComponents.widgetGrid` renders rows as `editable-row-wrapper`-style blocks. Each row contains a SimplyPages `Row` and `Column.create().withWidth(...)` layout. In edit mode, the row adds a centered `+ Add Widget to Row` section and the page adds a centered `+ Insert Row Below` separator after every row.

Each widget card owns a small top-right control strip. Normal mode shows a single detail icon. Edit mode adds refresh, compact resize form, move arrows, and delete. These controls are absolutely positioned and must not consume content space or create stacked control blocks.

Deep widget iteration continues to use the existing modal container. Layout position and sizing stay in place on the rendered dashboard surface.

## Implementation Steps

1. Change `AvatarDashboardComponents`:
   - Remove `rowDecoration`, `widgetDecoration`, `editModal`, and old edit widget panel usage from the visible layout.
   - Render `editable-row-wrapper` and demo-named add/insert sections.
   - Render `avatar-widget-corner-controls` in each widget; use icon labels/titles, not text-heavy control bars.
   - Change widget catalog from modal overlay to in-place add-module modal/section behavior that does not block row controls.
2. Change `AvatarDashboardController` and Avatar services/repository:
   - Add insert-row-after endpoint using row id.
   - Keep legacy `/avatar/_edit` close behavior for modal container, but do not use it as the layout editor source of truth.
3. Change `avatar-dashboard.css`:
   - Import/adapt demo-style classes for row wrappers, add-module, insert-row, and module decorators.
   - Remove visual rules that create large blue row/widget edit panels.
   - Ensure mobile stacks without overflowing or turning controls into a vertical wall.
4. Update tests:
   - Assert `editable-row-wrapper`, `add-module-section`, `insert-row-section`, and `avatar-widget-corner-controls`.
   - Assert old `avatar-row-decoration` and `avatar-widget-decoration` are gone from edit render.
5. Update `.internal-dev/knowledge`, relevant `AGENTS.md`, and docs.
6. Validate with unit tests, startup smoke, and delegated Playwright screenshots against Avatar and the SimplyPages demo.

## Validation

- `mvn -q -Dtest=AvatarDashboardControllerTest,AvatarServiceTest,AvatarRepositoryTest test`
- `mvn -q test`
- bounded Spring startup smoke.
- delegated Playwright validation using model `gpt-5.5` high per user request:
  - capture `/avatar`, `/avatar?edit=true`, mobile variants;
  - capture `http://localhost:8080/demos/htmx-editing`, click/enable editing if needed;
  - verify no large row/widget control panels, no modal-only layout editor, no stranded massive gaps, no clipped controls, no control overlap.

## Exit Criteria

- Avatar edit mode visually follows the demo structure and decorator hierarchy.
- Layout actions remain HTMX-backed and in-place.
- Knowledge and agent instructions make future agents diagnose against SimplyPages examples before approving UI.
- Final work is committed with `.internal-dev` closeout artifacts.
