---
task: avatar-simplypages-demo-parity
created: 2026-05-23
status: active
---

# Avatar SimplyPages Demo Parity Orchestration Notes

## Global Assumptions

- The SimplyPages HTMX editing demo at `http://localhost:8080/demos/htmx-editing` is the visual and interaction reference for Avatar layout editing.
- Avatar editing must happen in place on the rendered dashboard; modal flows are reserved for deep single-module iteration.
- Normal mode may show only a small top-corner module decorator for module detail/iteration. Edit mode should reveal sleek top-corner edit/delete/move/resize affordances plus add-module and add-row controls modeled after the demo.
- The scratch/demo area may be used for temporary design validation, but it is not a source of truth and must not be referenced as canonical knowledge.

## Active Agents

- Main Codex: orchestration, code integration, docs, closeout.
- Turing: high-level design review requested by Dwight over email.

## Completed Work

- Cancelled stale AgentMail reply wait from the rejected previous pass.
- Started a fresh repo-local orchestration record for the third refactor.
- Implemented SimplyPages demo-style Avatar edit mode with compact widget decorators, row micro controls, insert-row catalog behavior, and a width-cycle action.
- Updated AGENTS guidance, Avatar docs, SimplyPages knowledge, focus records, and changelog.
- Captured Dwight-requested high-level design review in `.internal-dev/reviews/2026-05-23-avatar-high-level-design-review.md`.

## Validation Results

- Focused Avatar controller/service/repository tests passed.
- Full `mvn -q test` passed.
- Bounded startup smoke passed before final remediation; live app was also restarted on port 18082 for Playwright validation.
- Initial Playwright validation failed on static controls, width-select spill, row strips, and empty insert rows.
- Fresh Playwright rerun passed after remediation.

## Remediation Notes

- Replaced visible widget width select with one `W` width-cycle action.
- Moved row controls to absolute micro chrome and changed insert-row to open the add-widget catalog for the new row.

## Blockers

- None currently. The high-level design review remains in progress as a closeout input.

## Closeout Work

- Updated `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`.
- Updated relevant `AGENTS.md` guidance.
- Updated user/developer docs for Avatar layout/edit behavior.
- Wrote changelog and focus updates.
- Wrote high-level design review and follow-up tracking.
- Archive finalized plan artifacts.
- Commit implementation plus `.internal-dev` updates.

## Final Validation Status

- Implementation validation passed. High-level design review completed. Email delivery and reply wait remain before final closeout.

## Handoff Notes

- Subagents must read and append concise results here before finishing.

## SimplyPages Demo Parity Research - 2026-05-23

Source files inspected:

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/pages/HtmxEditingDemoPage.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/java/io/mindspice/simplypages/modules/EditableModule.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/java/io/mindspice/simplypages/editing/EditModalBuilder.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/java/io/mindspice/simplypages/editing/EditableRow.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/resources/static/css/framework.css`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/getting-started/03-editing-system-first-implementation.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/editing-api-reference.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`

Concrete contract to copy/adapt:

- `/demos/htmx-editing` is only the launcher. Its button text is `Load editing demo fragment`, with `hx-get="/editing-demo"`, `hx-target="#editing-fragment"`, and `hx-swap="innerHTML"`. The canonical editable surface is `/editing-demo`.
- The editable surface renders one stable page target and one stable modal target: `#page-content` plus `#edit-modal-container`. HX requests receive only the content fragment; normal requests receive the shell.
- Rows are real SimplyPages layout rows wrapped in `Div.withClass("editable-row-wrapper")`. Each module sits in `Column.create().withWidth(module.width)`, using 12-column widths from the module state.
- Modules are real `ContentModule` or `SimpleListModule` instances wrapped with `EditableModule.wrap(displayModule)`. Copy the decorator chain shape: `.withEditUrl(...)`, `.withDeleteUrl(...)`, `.withDeleteTarget("#page-content")`, `.withDeleteSwap("none")`, `.withDeleteConfirm(...)`, `.withCanEdit(...)`, `.withCanDelete(...)`, `.withEditMode(...)`.
- Decorator selectors are exact: wrapper `.editable-module-wrapper`; edit button `.module-edit-btn`; delete button `.module-delete-btn`. CSS positions edit at `top: 2px; right: 30px` and delete at `top: 2px; right: 2px`, both absolute with `z-index: 10`, transparent background, no border.
- Add-module controls use `.add-module-section` with centered `+ Add Module to Row`, `hx-get="/editing-demo/add-module-modal/{rowId}"`, `hx-target="#edit-modal-container"`, `hx-swap="innerHTML"`. CSS uses `margin: 16px 0`, centered text, dashed border button, `12px 24px` padding.
- Insert-row controls use `.insert-row-section` after each row, with centered `+ Insert Row Below`, `hx-post="/editing-demo/insert-row/{position}"`, `hx-target="#edit-modal-container"`, `hx-swap="innerHTML"`. CSS uses `margin: 24px 0`, `padding: 16px 0`, and a top border separator.
- The add-module modal is a module/catalog modal, not a layout modal. It contains title, content textarea, width select values `3/4/6/8/12`, edit-mode select `OWNER_EDIT/USER_EDIT`, and permission checkboxes. The submit button uses `hx-post`, `hx-swap="none"`, and `hx-include=".modal-body input, .modal-body textarea, .modal-body select"`.
- Save/add/delete/approve/reject responses should use OOB swaps: clear `#edit-modal-container` and replace `#page-content` or the Avatar grid target with `hx-swap-oob="true"`. Keep regular buttons targeting the modal or grid and let the response update multiple targets.
- Modal editing is for module properties, add-module/catalog selection, pending approval, and nested child editing. Placement, row insert, add-module entry point, permissions visibility, and delete affordances are in-place on the displayed dashboard.

Visual acceptance criteria for Avatar:

- View mode remains operational and dense; edit mode decorates the same rendered dashboard instead of switching to a detached layout editor.
- Module edit/delete affordances live in the top-right corner of each widget/module, do not resize the card, and remain visible without covering headings or content.
- Row wrappers have subtle hover/background treatment, `16px` internal padding, and roughly `32px` row separation; they should not look like nested decorative cards.
- Add-module controls read as centered dashed insertion affordances inside rows. Insert-row controls read as low-emphasis row separators between rows.
- Widget/card bodies should keep the SimplyPages module structure: `.content-module.module`, `.module-title`, `.module-content` or equivalent Avatar panel classes with the same hierarchy.
- Width changes are reflected through 12-column classes/columns, not CSS-only pixel hacks. Expected presets for Avatar controls should include `3`, `4`, `6`, `8`, and `12`.
- One shared modal container handles deep editing. Layout mutations refresh the live grid/page via OOB swaps without a full reload.

Avatar gotchas:

- The demo has no `Enable Editing` button or auth gate. On `/demos/htmx-editing`, the button is `Load editing demo fragment`; in current Avatar docs/code the comparable control is `Edit Layout In Place` / `Exit Layout Edit`.
- Playwright MCP must allow the app origin. Current repo guidance lists `--allowed-origins=http://localhost:8080;http://localhost:18080`; ports outside that list can fail with `ERR_BLOCKED_BY_CLIENT`.
- Local demo validation requires the SimplyPages demo server running on `localhost:8080` and HTMX loading from `/webjars/htmx.org/dist/htmx.min.js`. Curl and Playwright both reached `http://localhost:8080/demos/htmx-editing` during this pass, and Playwright successfully clicked `Load editing demo fragment`, edit, and add-module controls.
- `EditableModule` is mutable and built lazily on first render. For Avatar, create request-scoped wrappers and do not reuse a rendered wrapper instance across requests.
- The demo's `EditableRow` helper auto-equalizes widths, but `EditingDemoController` is a better Avatar model because it preserves explicit per-module widths.
- `insert-row` creates the row then opens the add-module modal. Avatar should decide whether canceling that modal leaves an empty row; if empty rows are allowed, render their add-widget affordance clearly.

## Avatar Current-State Review - 2026-05-23

- Current `/avatar?edit=true` is functionally in-place, but visually not demo-parity: row and widget controls render as large text/form blocks before widget content, so edit mode becomes a dense editor surface instead of lightweight top-corner decorations on the displayed dashboard.
- `AvatarDashboardComponents.editModal(...)`, `editRow(...)`, and `editWidget(...)` are legacy modal/list-editor paths and should no longer be the primary layout editor. Keep only as fallback if needed; the preferred flow should be the rendered grid plus one shared modal for add-widget/detail.
- Every layout mutation currently targets `#avatar-edit-container` and relies on OOB `#avatar-widget-grid` replacement. This works, but control buttons on the live grid should target a shared modal only when opening add-widget/detail; movement, resize, delete, add-row, and insert-row should feel like direct live-grid actions and clear the modal via OOB.
- Replace visible text-heavy controls (`Left`, `Right`, `Up`, `Down`, `Resize`, `Remove`, repeated row labels) with compact icon/top-right affordances, tooltip/title text, and low-emphasis row insertion bands modeled after `.editable-module-wrapper`, `.module-edit-btn`, `.module-delete-btn`, `.add-module-section`, and `.insert-row-section`.
- Remove or retire CSS selectors that support the heavy editor treatment: `.avatar-widget-decoration`, `.avatar-widget-decoration-title`, `.avatar-widget-decoration-width`, `.avatar-dashboard-row-shell-editing` heavy dashed panel styling, `.avatar-edit-row`, `.avatar-edit-widget`, and modal/list-editor-only `.avatar-edit-widgets` where they are not used by organizer/workarea modals.
- Preserve the existing row/widget persistence model, endpoints, and 12-column width presets. The refactor should be presentation and HTMX target cleanup, not a schema or service rewrite.
- Desktop acceptance should compare against `/dashboard`, `/agents`, and SimplyPages editing demo: first viewport remains dense and useful, controls do not resize widget cards, row insert/add-widget affordances are clear but quiet, and the chat rail remains usable.
- Mobile acceptance should prove a single-column stack with 44px-ish hit targets, no horizontal overflow, no clipped selects/buttons, and edit controls that do not dominate every widget before content.
- Tests to update: `AvatarDashboardControllerTest` should assert preferred edit markup classes/targets and no primary `avatar-edit-modal` layout editor in `avatar(true)`; keep route coverage for `_edit` only if it remains compatibility. Add CSS/markup regression assertions for add-widget/insert-row bands and top-corner widget controls.

## Validation Pass - 2026-05-23 Avatar Third Refactor

Result: FAIL for SimplyPages demo visual parity; functional HTMX checks mostly pass.

Artifacts:

- `target/playwright-avatar-demo-parity/avatar-view-desktop.png`
- `target/playwright-avatar-demo-parity/avatar-edit-desktop.png`
- `target/playwright-avatar-demo-parity/avatar-view-mobile.png`
- `target/playwright-avatar-demo-parity/avatar-edit-mobile.png`
- `target/playwright-avatar-demo-parity/simplypages-htmx-editing-demo-desktop.png`
- `target/playwright-avatar-demo-parity/avatar-detail-opened.png`
- `target/playwright-avatar-demo-parity/avatar-add-widget-catalog-opened.png`
- `target/playwright-avatar-demo-parity/avatar-insert-row-below.png`
- `target/playwright-avatar-demo-parity/validation-results.json`

Setup note: Playwright MCP navigation to `http://127.0.0.1:18082/avatar` failed with `net::ERR_BLOCKED_BY_CLIENT`, matching the known stale allow-list issue. Validation used a local Playwright Chromium process instead, with the same live app and demo origins.

Criteria:

- PASS: `/avatar` and `/avatar?edit=true` loaded on desktop and mobile; screenshots captured.
- PASS: SimplyPages demo loaded, clicked `Load editing demo fragment`, and captured the canonical editable surface.
- PASS: detail control opened a modal/container (`Daily Tasks Close Add Daily...`).
- PASS: add-widget catalog opened in `#avatar-edit-container` and exposed widget width choices.
- PASS: insert row below added a row (`rowsBeforeInsert=10`, `rowsAfterInsert=11`).
- PASS: add-widget/add-row affordances are visible; not hidden.
- PASS: mobile had no measured horizontal overflow.
- PASS: no obvious overlap/clipping or console/request failures were captured.
- FAIL: edit mode still has text-heavy stacked widget controls before content; measured 10 widget control blocks starting with `i R < > ^ v 3 4 6 8 12 W X`.
- FAIL: controls are static in the card flow, not top-right absolute decorators; first controls report `position=static` and consume the first row of each widget.
- FAIL: row controls still render as repeated button strips (`+ Add Widget to Row`, `Row up`, `Row down`, `Delete Row`) before insert bands; measured 11 row-control text blocks.
- FAIL: obvious dead zones/gaps remain; measured 6 empty row shells containing only add/row controls.
- FAIL/PARTIAL: insert row works mechanically, but it leaves another empty edit band and does not present the demo-style add-module modal/catalog after insertion.
- PASS: layout editing is not modal-only; the rendered dashboard remains the primary surface.

Visual blockers:

- Avatar edit mode is much heavier than the SimplyPages demo: the demo uses tiny top-corner edit/delete decorators plus quiet dashed add/insert affordances, while Avatar still turns every widget and empty row into visible control strips.
- The desktop first viewport has a large unused center/right area and the chat rail is stranded while the editable grid uses narrow columns.
- Mobile stacks correctly without horizontal overflow, but edit controls dominate each widget before the user reaches content.

## Validation Rerun - 2026-05-23 Fresh After Restart

Result: PASS for the requested remediation checks against `http://127.0.0.1:18082`.

Artifacts:

- `target/playwright-avatar-demo-parity-rerun/simplypages-htmx-editing-demo-desktop.png`
- `target/playwright-avatar-demo-parity-rerun/avatar-view-desktop.png`
- `target/playwright-avatar-demo-parity-rerun/avatar-edit-desktop.png`
- `target/playwright-avatar-demo-parity-rerun/avatar-insert-row-catalog-opened.png`
- `target/playwright-avatar-demo-parity-rerun/avatar-edit-after-insert-desktop.png`
- `target/playwright-avatar-demo-parity-rerun/avatar-edit-mobile.png`
- `target/playwright-avatar-demo-parity-rerun/validation-results.json`

Criteria:

- PASS: SimplyPages demo reference loaded and rendered editable modules/add/insert controls.
- PASS: Avatar view and edit surfaces loaded from the restarted app with no console errors or request failures.
- PASS: widget decorators are compact top-corner absolute controls; measured 9 widget decorators, all `position=absolute`, with text `i R < > ^ v W X`.
- PASS: widget decorators contain no select/dropdown width controls, no option elements, and no visible numeric preset buttons; each decorator has exactly one `W` cycle button.
- PASS: row controls are top-right micro chrome; measured 11 row control groups at about `73px x 22px`, all `position=absolute`, with button text `^ v X`, and no centered strip candidates.
- PASS: insert-row added one new row and opened the add-widget catalog in `#avatar-edit-container`; catalog form `hx-post` targets reference the new row id `row-94291b9f-788f-4e7d-9ed6-618881e9e58c`.
- PASS: mobile edit mode measured no horizontal overflow (`scrollWidth=390`, viewport width `390`) and no overflowing elements.

Remaining visual blockers:

- None for the requested remediation criteria. The live data still contains prior test todos and multiple empty rows, so screenshots show a long scrolling edit surface, but the control placement, width-cycle behavior, insert-row catalog behavior, and mobile overflow checks all passed.

## High-Level Product/UI Design Review - 2026-05-23

### Executive Summary

Avatar is now recognizably part of Magenta's operational UI rather than a disconnected personal-dashboard experiment. The strongest current qualities are its shared shell, compact widget vocabulary, first-pass personal assistant scope, and the fact that edit mode is in-place instead of a separate layout form. The remaining design gap is not basic functionality; it is polish and hierarchy. View mode still wastes wide-screen space, edit mode exposes too much repetitive row chrome, and the chat panel does not yet inherit the confidence and focus of `/chat`.

### Evidence Reviewed

- Live Magenta app at `http://127.0.0.1:18082`.
- SimplyPages demo at `http://localhost:8080/demos/htmx-editing`.
- Screenshots captured under `target/avatar-high-level-design-review/`:
  - `avatar-desktop-full.png`
  - `avatar-edit-desktop-full.png`
  - `avatar-edit-add-widget-opened.png`
  - `avatar-mobile-full.png`
  - `avatar-edit-mobile-full.png`
  - `chat-desktop-full.png`
  - `chat-mobile-full.png`
  - `dashboard-desktop-full.png`
  - `agents-desktop-full.png`
  - `agent-dashboard-detail-full.png`
  - `simplypages-htmx-editing-initial.png`
  - `simplypages-htmx-editing-demo-loaded.png`
- DOM and overflow metrics saved at `target/avatar-high-level-design-review/review-metrics.json`.

### Avatar Findings

Avatar does well when it behaves like an operational command surface: compact cards, thin borders, low-radius panels, semantic sections, and practical widgets for todos, daily tasks, notes, calendar, outputs, work areas, system state, alerts, recent work, and chat. The page has no measured horizontal overflow on desktop or mobile, and the mobile stack remains readable.

The main view still needs stronger spatial discipline. On desktop, the left/middle widget grid uses a narrow region while the right chat rail and lower page leave large pale empty areas. The page reads as "widgets placed on a canvas" more than a deliberately balanced command surface. Todo test data also overwhelms the first viewport and makes the dashboard feel noisier than the intended personal assistant surface.

Edit mode has improved by moving widget controls into top-corner micro chrome, but it still falls short of the SimplyPages demo's visual calm. Repeated `+ Add Widget to Row` and `+ Insert Row Below` bands dominate long stretches of empty rows. The add-widget catalog opens reliably, but visually it becomes a large form block at the bottom of an already long edit surface rather than a focused, local picker.

### Chat Style Lessons

`/chat` gets the primary-work hierarchy right. It has a clear session rail, a central conversation/composer lane, and an outputs rail. The controls are predictable, the model selectors are grouped as task setup, the transcript area has obvious ownership, and the composer feels like the page's main action.

Avatar should borrow that mental model for its embedded chat. The Avatar chat card should feel like a small but real chat workspace: transcript first, composer second, status/model/session treatment visible where relevant, and output/context links nearby. Right now it is useful but lightweight compared with `/chat`; it reads more like a widget than the assistant's conversational entry point.

### Agent Dashboard/Edit Demo Lessons

`/dashboard` and the agent detail pages show the right broader language for Magenta agents: a left operational nav, dense status strips, tables/fact grids, compact action bars, and master-detail panels. Avatar should keep personal features, but present them with that same scan-first rhythm.

The SimplyPages HTMX editing demo remains the best edit-mode comparator. Its module content stays primary, edit/delete controls sit as tiny decorators, add-module controls are centered and quiet, and insert-row separators are low-emphasis. Avatar should keep the in-place editing model but reduce repeated row controls, collapse empty rows, and make add/insert affordances feel like decorators on the real dashboard rather than separate editor content.

### Recommended Improvements

1. Collapse empty edit rows into low-emphasis insert separators until the user actively adds a widget.
2. Convert repeated `+ Add Widget to Row` controls into a smaller icon or compact centered affordance modeled after the SimplyPages demo.
3. Open add-widget selection as a focused modal/drawer or local picker, not a full-width block that appears after a long scroll.
4. Rebalance desktop layout so the widget grid uses available width intentionally and the chat rail does not feel stranded.
5. Make Avatar chat inherit more from `/chat`: clearer transcript hierarchy, stronger composer affordance, and visible session/status context where useful.
6. Clean seeded/test todos from review/demo states or visually constrain long lists so one noisy widget cannot consume the first viewport.
7. Standardize remaining browser-default buttons such as Done/Delete/Browse with the Magenta operational button/chip vocabulary.
8. On mobile, keep the current no-overflow behavior but make edit controls less repetitive and easier to hit without forcing every widget to begin with dense chrome.

### Future Agent Rules

Do start from `/chat`, `/dashboard`, `/agents`, and the SimplyPages editing demo before designing agent surfaces. Do keep agents as operational tools: dense, calm, scannable, panel-based, and HTMX-first for standard mutations. Do use in-place decorators for layout editing and modal/drawer views for deeper item-specific edits.

Do not turn agent dashboards into landing pages, card collages, or decorative personal-product dashboards. Do not let empty rows, add controls, resize controls, or editor labels become the main visual content. Do not invent one-off layout editors when SimplyPages already provides row/module/editing patterns. Do not use JavaScript as a general transport layer when HTMX fragments fit the interaction.

### Risks/Follow-ups

- The current live data includes many old Playwright/debug todos and empty rows, which makes Avatar look worse than a clean user state; future review should repeat with representative seeded data.
- Current focus already notes Avatar may need user review for the next durable direction; this design review supports treating Avatar polish as follow-up rather than declaring the surface complete.
- If more Magenta agent dashboards are planned, consider extracting a reusable SimplyPages/Magenta dashboard editing decorator so future agents inherit consistent edit chrome instead of re-solving this page by page.
