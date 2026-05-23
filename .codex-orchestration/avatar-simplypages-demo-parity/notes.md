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
- Dwight is on the road and email is a standing control channel for this workstream. Keep a reply-wait active after reports/phase updates, acknowledge every inbound email before acting on it, check for more than one inbound message, and make emailed reports detailed enough to stand alone with the full review content in the email body.

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

## Advanced Avatar Polish Plan - 2026-05-23

### 1. Objective

Deliver a focused Avatar UI polish pass that makes `/avatar` feel like a complete Magenta operational assistant surface instead of a functionally correct but visually unfinished dashboard. The implementation should preserve the current Avatar architecture: HTMX-first fragments, in-place SimplyPages-style row/module layout editing, `avatar.sqlite` layout state, and the compact Avatar-specific chat client. The work should not introduce a second runtime, a new frontend framework, or a modal-only layout editor.

The plan targets the exact weaknesses called out in `.internal-dev/reviews/2026-05-23-avatar-high-level-design-review.md`: empty edit rows, add-widget presentation, focused widget selection, desktop balance, Avatar chat hierarchy, long todo constraints, browser-default buttons, and mobile edit ergonomics.

### 2. Inputs And Assumptions

Confirmed source inputs:

- Root `AGENTS.md`, `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`, and `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md` require SimplyPages-native, HTMX-first, in-place Avatar layout editing with Playwright visual validation.
- `.internal-dev/focus/unfinished-work.md` has `UNFINISHED-20260523-04`, which points directly at Avatar high-level visual polish.
- `.internal-dev/focus/architecture-focus.md` and `.internal-dev/focus/decisions.md` preserve the Avatar boundaries: layout/planner/user-centric state in `avatar.sqlite`, runtime/chat/agent services outside Avatar, and in-place layout editing as the durable decision.
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md` requires comparing `/avatar?edit=true` against the SimplyPages HTMX editing demo when layout editing changes.
- `AvatarDashboardComponents.page(...)` currently renders `.avatar-layout` as widget grid plus sticky `.avatar-chat`.
- `AvatarDashboardComponents.widgetGrid(...)`, `rowShell(...)`, `addModuleSection(...)`, `insertRowSection(...)`, and `widgetCatalogModal(...)` are the primary render targets for layout-edit polish.
- `AvatarDashboardController` already exposes the required row/widget HTMX routes and OOB grid updates; this is mostly presentation and interaction polish, not a route or schema redesign.
- `/chat` uses a three-column hierarchy in `FrontendController.chat(...)`: session rail, central chat module/composer, and output rail. Avatar should borrow hierarchy and styling, not the full `/js/chat-client.js` surface.
- The SimplyPages demo pattern is concrete: `EditingDemoController` wraps real rows in `.editable-row-wrapper`, uses `Column.create().withWidth(...)`, opens add-module through a modal target, removes empty rows on delete, and uses OOB updates.

Assumptions to verify before coding:

- It is acceptable for the implementation to auto-prune persisted empty dashboard rows or to hide them in edit rendering as collapsed insertion separators. If unsure, prefer render-time collapsing first and keep persistence cleanup as an explicit service action.
- The add-widget selector may be a centered modal or right drawer. The lowest-risk first implementation is a focused modal in `#avatar-edit-container`; a drawer is acceptable only if it improves mobile ergonomics without new broad JavaScript.
- Avatar chat should remain compact and Avatar-specific. Do not load the full browser chat sidebar/session manager unless Dwight explicitly broadens scope.
- Seed/test data cleanup is a validation concern. Production code should constrain noisy lists rather than depending on clean local data.

### 3. Scope

In scope:

- Collapse or compact empty edit rows so they render as low-emphasis insertion/add-widget affordances instead of long blank row bands.
- Shrink repeated add-widget presentation and make widget selection focused, local, and visually bounded.
- Rebalance desktop layout so the widget grid uses available width and Avatar chat reads as a deliberate assistant rail/workspace.
- Improve Avatar chat hierarchy using `/chat` transcript/composer/status patterns while keeping `avatar-chat.js` narrow.
- Constrain noisy todo/task lists so one widget cannot dominate the first viewport.
- Standardize Avatar buttons, inputs, selects, and row actions under Magenta operational styling.
- Preserve mobile no-overflow behavior while improving touch targets and reducing repetitive edit chrome.
- Add focused tests for changed render contracts and run delegated Playwright visual validation.

Out of scope:

- No new Avatar runtime, scheduling automation, plugin system, planner automation, or cross-database relationships.
- No full `/chat` feature port, session rail, bulk actions, plan mode, or output file panel inside Avatar.
- No broad SimplyPages upstream PR unless implementation exposes a real missing library primitive that cannot be solved cleanly locally.
- No production code edits by this planning agent.

### 4. Current-State Analysis

Primary file targets:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
  - `page(...)`: top-level Avatar layout and chat placement.
  - `widgetGrid(...)`: row iteration and insert-row placement.
  - `rowShell(...)`: empty-row and edit-mode row composition.
  - `widget(...)` and `widgetCornerControls(...)`: widget chrome.
  - `widgetCatalogModal(...)`: currently renders an inline catalog-style block in `#avatar-edit-container`.
  - `compactChat(...)`: currently header, transcript, textarea, send button only.
  - `todos(...)`, `dailyTasks(...)`, and list widgets: noisy data constraints.
  - `action(...)`, `deleteAction(...)`, form builders: button class/style consistency.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
  - Existing endpoints should mostly remain: `/avatar/_layout/rows/{rowId}/catalog`, `/insert-after`, add/remove/move/resize widget routes.
  - Potential controller change: add a close/cancel path for newly inserted empty rows only if render-time collapse does not solve empty-row buildup.
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java` and `AvatarRepository.java`
  - Avoid changes unless implementing an explicit `removeEmptyDashboardRows()` or `cancelEmptyRow(rowId)` flow. Existing 12-column and single-instance enforcement is correct.
- `src/main/resources/static/css/avatar-dashboard.css`
  - Main target for layout balance, button/input styling, edit empty-row treatment, focused picker/modal/drawer styling, chat hierarchy, list caps, and mobile hit targets.
- `src/main/resources/static/js/avatar-chat.js`
  - Keep limited to SSE chat. Add only narrow UI state handling if needed: busy state, disabled submit button, status text, and optional interrupt/queue display if small.
- Reference files, do not copy wholesale:
  - `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
  - `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
  - `src/main/resources/static/css/magenta.css`
  - `src/main/resources/static/css/orchestration.css`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/pages/HtmxEditingDemoPage.java`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/simplypages/src/main/resources/static/css/framework.css`

Important current behaviors:

- `widgetGrid(...)` renders every persisted row and appends an insert-row section after each row in edit mode. Empty persisted rows therefore become repeated visual chrome.
- `insertLayoutRowAfter(...)` inserts a row and returns `layoutEditResponseWithCatalog(...)`, but canceling the catalog can leave a persisted empty row.
- `widgetCatalogModal(...)` uses `avatar-inline-catalog`, so the selector appears as a large block after the grid response rather than a focused modal/drawer.
- Normal mode row CSS currently uses `auto-fit` for row columns, while edit mode uses the explicit 12-column grid. This can help responsiveness but also makes desktop width use feel less deliberate.
- `.avatar-page` is not covered by `.chat-page button` or `.orch-page button` rules in `magenta.css`; several Avatar buttons can rely on default/framework styling unless CSS catches their local selector.
- `newestTodos(...)` already limits todos to 8 open items, but the widget does not provide a stronger bounded visual treatment or summary when local/debug data is noisy.
- `avatar-chat.js` is intentionally narrow; it posts to `/api/chat/stream` with `surface=AVATAR`, appends SSE events, and does not reload browser chat history/session lists.

### 5. Target Design

Layout and edit design:

- Empty rows should not render as full blank dashboard rows. In edit mode, a row with no widgets should render as one compact `.avatar-empty-row-insert` affordance containing:
  - one `Add Widget` trigger for that row;
  - optional tiny row move/delete controls if the row is persisted;
  - no full `.avatar-dashboard-row` blank grid and no large `+ Add Widget to Row` band.
- Non-empty rows remain real SimplyPages `Row` + `Column.create().withWidth(...)` composition. Do not replace this with ad hoc CSS-only placement.
- Add-widget should open a focused selector in `#avatar-edit-container` as a modal or drawer:
  - preferred v1: modal class such as `.avatar-widget-picker-modal`, centered and max-width around `42rem`;
  - use cards/buttons for available widgets, disabled state for used widgets, width segmented/select control per option;
  - submit should HTMX-post to the existing add-widget route and close the modal through OOB response.
- Insert-row behavior should feel like the demo: clicking insert below creates/selects a row and opens the focused widget picker. If the picker is canceled, the row should not create a large visual artifact.
- Widget top-corner controls remain compact. On mobile, controls should be touchable without dominating content: use a small toolbar row or expanded hit areas via padding while preserving visual size.

Desktop layout design:

- Make `.avatar-layout` a deliberate operational work surface, not a stranded grid plus rail. Candidate CSS:
  - under 1180px keep the current single-column stack;
  - 1180-1499px use `grid-template-columns: minmax(0, 1fr) minmax(22rem, 27rem)`;
  - 1500px+ use `grid-template-columns: minmax(0, 1fr) minmax(24rem, 30rem)` with the grid allowed to span available width and rows preserving 12-column structure.
- Consider grouping the chat rail with a compact status/model strip so it looks intentionally primary. Avoid oversized card styling or nested card stacks.
- Normal mode should preserve row intent better. If `auto-fit` causes visual drift, replace it with explicit 12-column spans on desktop and use mobile media queries for stacking.

Avatar chat target:

- Keep `compactChat(...)` inside Avatar, but borrow `/chat` hierarchy:
  - header with title, session chip, model chip, and status text;
  - transcript panel styled closer to `#chat-history` and `.chat-message-*`;
  - composer grid like `#chat-form`, with textarea and send button aligned predictably;
  - small context/status strip such as "Surface: Avatar" and default model, not a full model selector unless the implementation can keep it compact.
- `avatar-chat.js` may add:
  - `data-busy` or `.is-busy` state on the root;
  - submit-button disabled state, not only textarea disabled;
  - streaming status text updates in a dedicated element;
  - accumulated assistant message updates if easy, rather than many separate chunk cards. If this becomes complex, do not overbuild; style separate chunks acceptably.

List/button target:

- List-heavy widgets get bounded bodies:
  - `.avatar-list-constrained` or widget-specific max-height with internal scroll after 5-6 rows;
  - summary line like `Showing 8 open todos` when the source list exceeds the rendered count;
  - row height, text wrapping, and action placement remain stable.
- Add a broad but scoped Avatar control baseline:
  - `.avatar-page button`, `.avatar-page input`, `.avatar-page select`, `.avatar-page textarea` should match Magenta operational controls;
  - specialized micro controls override baseline with their current tiny styling;
  - links styled as buttons should share `.avatar-button` or existing `.orch-primary` conventions.

### 6. Implementation Plan

Phase 0 - Branch and baseline capture

- Create a dedicated branch before implementation, for example `avatar-ui-polish-2026-05-23`.
- Run `git status --short` and stop if unrelated user edits overlap the target files.
- Re-read the review and this plan before editing.
- Start the Magenta app on an available port and the SimplyPages demo on `8080` for baseline screenshots if not already running.
- Capture baseline screenshots through a delegated Playwright validation agent:
  - `/avatar`
  - `/avatar?edit=true`
  - `/chat`
  - `/dashboard`
  - `/agents`
  - `http://localhost:8080/demos/htmx-editing`, after clicking `Load editing demo fragment`

Phase 1 - Empty rows and focused add-widget picker

Files:

- `AvatarDashboardComponents.java`
- `AvatarDashboardController.java` only if cancellation/cleanup route is needed.
- `avatar-dashboard.css`
- `AvatarDashboardControllerTest.java`
- `AvatarServiceTest.java` / `AvatarRepositoryTest.java` only if service cleanup is added.

Steps:

1. Add render helpers in `AvatarDashboardComponents`:
   - `private static boolean emptyRow(AvatarDashboardRow row)`
   - `private static Component emptyRowInsert(AvatarDashboardRow row, int index, int rowCount)`
   - `private static Component focusedWidgetPicker(List<AvatarDashboardRow> rows, String rowId)`
2. Change `rowShell(...)` so an empty row in edit mode returns compact empty-row insert UI, not a full blank `.avatar-dashboard-row-shell` with add/insert bands.
3. Keep non-empty rows unchanged except for smaller add-widget copy, e.g. `+ Widget` or `Add Widget`, with `title`/`aria-label` describing the target row.
4. Convert `widgetCatalogModal(...)` from `.avatar-inline-catalog` to a focused modal/drawer class. Keep the same endpoint and HTMX target. The response should still add/close through existing OOB grid replacement.
5. Add disabled/used widget states that are visually compact and obvious. Avoid full-width catalog blocks that appear after a long scroll.
6. Decide cancel behavior:
   - lowest-risk: closing the picker only clears `#avatar-edit-container`; empty row remains but renders compact.
   - stronger: add route `DELETE /avatar/_layout/rows/{rowId}?ifEmpty=true` or reuse existing delete with explicit close button only for empty inserted rows. If adding, tests must cover non-empty row rejection.
7. Update tests:
   - `avatar(true)` contains empty-row compact class when an empty row exists.
   - `widgetCatalog(rowId)` returns modal/drawer class, not `avatar-inline-catalog`.
   - `insertLayoutRowAfter(rowId)` returns picker markup targeting the inserted row and OOB grid.
   - Existing add/move/resize/remove assertions still pass.

Risk gates:

- Stop if the desired cleanup route would silently delete non-empty rows.
- Stop if the picker needs custom JavaScript for core selection; use HTMX forms first.
- Do not remove the existing 12-column server-side width enforcement.

Phase 2 - Desktop layout balance and normal-mode row intent

Files:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `AvatarDashboardControllerTest.java` if class/markup contracts change.

Steps:

1. Adjust `.avatar-layout` breakpoints so the chat rail has intentional width without starving the widget grid.
2. Review `.avatar-dashboard-row-shell:not(.avatar-dashboard-row-shell-editing) .avatar-dashboard-row` auto-fit behavior. If it causes wide-screen under-use, switch normal rows to explicit 12-column spans on desktop and keep single-column mobile stacking.
3. Add a page-level class or data attribute if needed to distinguish normal/edit layout CSS cleanly; avoid brittle `:has(...)` dependency where not already used.
4. Tune row gaps and widget padding to match `/dashboard` density: no large dead zones, no nested-card feel, no huge spacer rows.
5. Keep first viewport useful with chat plus multiple operational widgets visible on 1440px and 1920px widths.

Risk gates:

- Do not let desktop changes cause mobile horizontal overflow.
- Do not convert dashboard layout into a marketing/landing layout.
- If explicit 12-column normal mode makes content cramped, preserve auto-fit but set better min widths and max rail width.

Phase 3 - Avatar chat hierarchy

Files:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `avatar-chat.js`
- `AvatarDashboardControllerTest.java` or focused rendering assertions.

Steps:

1. Update `compactChat(...)` to render:
   - `.avatar-chat-title-row` with `Avatar Chat`;
   - `.avatar-chat-session-chip` for current/new session;
   - `.avatar-chat-status` with `Ready`/busy text;
   - `.avatar-chat-model-chip` using `defaultModel` when present;
   - transcript with `.avatar-chat-transcript`;
   - composer with `.avatar-chat-composer`.
2. Style transcript and messages using `/chat` as reference but compacted:
   - role label, body, assistant/user/system colors from `magenta.css` family;
   - fixed/min transcript height on desktop, internal scroll;
   - no oversized hero type.
3. In `avatar-chat.js`, disable submit during send, update status, and keep textarea focus. Keep the script limited to SSE behavior.
4. Optional improvement: coalesce `chunk` events into one active assistant article. If this is not simple, leave separate messages and ensure CSS handles them.
5. Tests should assert required chat shell ids/classes and that `data-chat-surface="avatar"` remains.

Risk gates:

- Do not load `chat-client.js` or import browser chat session management.
- Do not add model selection controls unless they remain compact and route through existing `/api/chat/stream` payload.
- Do not make chat dominate the entire Avatar page at the expense of widgets.

Phase 4 - Long list, button, and mobile ergonomics polish

Files:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `AvatarDashboardControllerTest.java`

Steps:

1. Add helper wrappers for constrained lists:
   - `private static Component constrainedList(Div list, int shownCount, int totalCount, String itemLabel)`
   - or simpler widget-local summary/footer helpers.
2. Apply to todos and daily tasks first. Optionally apply to outputs/work areas/recent work if screenshots show the same issue.
3. Add a scoped Avatar control baseline:
   - `.avatar-page button`, `.avatar-page input`, `.avatar-page select`, `.avatar-page textarea`
   - `.avatar-page button:hover/focus`
   - `.avatar-page button[disabled]`
   - specialized overrides for `.avatar-widget-corner-controls button`, `.avatar-row-micro-controls button`, `.avatar-insert-row-section button`, and picker buttons.
4. Mobile edit mode:
   - keep `.avatar-dashboard-row` single-column;
   - make row/picker controls easy to tap with visual compactness and sufficient hit target;
   - ensure modal/drawer width is `calc(100vw - 1rem)` or full-screen on very small screens;
   - ensure picker cards and row actions wrap before overflowing.
5. Add regression assertions where feasible:
   - no primary route markup contains old `avatar-inline-catalog`;
   - todo widget includes bounded-list class when many todos are present;
   - action buttons carry expected Avatar classes or are covered by `.avatar-page` baseline.

Risk gates:

- Do not hide destructive actions without accessible labels.
- Do not make internal scroll traps so severe that keyboard/mobile users cannot reach controls.
- Do not use JavaScript for list expand/collapse unless HTMX/server rendering becomes awkward and the tradeoff is documented.

Phase 5 - Documentation and `.internal-dev` closeout

Files:

- `docs/technical/avatar-dashboard-fragments.md` or the existing Avatar UI docs that describe dashboard fragments/layout editing.
- `.internal-dev/changelogs/<date>-avatar-ui-polish.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md` only if the implementation learns a durable pattern not already captured.
- `.internal-dev/focus/unfinished-work.md` to close or update `UNFINISHED-20260523-04`.
- `.internal-dev/focus/current-focus.md` should be checked. It already warns Avatar focus may be stale after sprint completion; report this in closeout rather than silently changing direction.
- `.internal-dev/focus/architecture-focus.md` and `decisions.md` only if a durable architecture decision changes. Pure visual polish should not need new architecture entries.

Steps:

1. Document user-visible behavior changes: focused widget picker, empty-row treatment, bounded lists, and chat visual hierarchy.
2. Record changelog with validation evidence and screenshot artifact paths.
3. Update unfinished work: close `UNFINISHED-20260523-04` only after implementation and visual validation pass.
4. Archive or update related plan/review artifacts only if the owner/orchestrator wants this workstream finalized.
5. Commit implementation plus docs and `.internal-dev` updates at the phase end.

### 7. Validation Plan

Automated tests:

- Run focused tests after each relevant phase:
  - `mvn -q -Dtest=AvatarDashboardControllerTest test`
  - `mvn -q -Dtest=AvatarServiceTest,AvatarRepositoryTest test` if service/repository cleanup changes are made.
- Run full test suite before final sign-off:
  - `mvn -q test`
- Run bounded Spring startup smoke:
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - If local config/secrets block startup, report exact dependency and do not claim full validation.

Delegated Playwright validation:

- Per repo policy, run Playwright validation on a subagent using model `gpt-5.3-codex` with reasoning effort `medium`. Do not run it inline in the implementation thread.
- Required live targets:
  - Magenta app on the implementation port, preferably `http://127.0.0.1:18080` or the active port recorded by the orchestrator.
  - SimplyPages demo at `http://localhost:8080/demos/htmx-editing`.
- If Playwright MCP blocks with `ERR_BLOCKED_BY_CLIENT`, first check allowed origins/profile-lock guidance. If still blocked, report blocker and use only approved fallback browser-origin probes if the orchestrator/user approves.

Required screenshots/artifacts:

- `/avatar` desktop at 1440px and 1920px.
- `/avatar?edit=true` desktop at 1440px and 1920px.
- `/avatar?edit=true` with add-widget picker open.
- `/avatar?edit=true` after insert-row opens picker.
- `/avatar` mobile at 390px.
- `/avatar?edit=true` mobile at 390px.
- `/avatar?edit=true` mobile with picker open.
- `/chat` desktop reference.
- `/dashboard` desktop reference.
- `/agents` desktop reference.
- `http://localhost:8080/demos/htmx-editing` before and after clicking `Load editing demo fragment`.

Playwright functional checks:

- Toggle edit mode from `/avatar` and verify the rendered dashboard remains the primary surface.
- Click add-widget for a non-empty row; expect focused picker in `#avatar-edit-container`.
- Click insert-row below; expect exactly one new row and focused picker for the new row.
- Close the picker; expect no large empty row band. If an empty row remains, it must render as compact empty-row insert UI.
- Add an available widget with width `3`, `4`, `6`, `8`, or `12`; expect OOB grid update and no full page reload.
- Move a widget left/right/up/down where valid; expect grid update and no layout overflow.
- Remove a widget; if row becomes empty, expect compact empty-row treatment.
- Send a short Avatar chat message only if a local model/backend is available. If not available, validate busy/error UI without treating model unavailability as a UI failure.

Visual acceptance criteria:

- Empty edit rows do not create long blank surfaces or repeated full-width control bands.
- Add-widget presentation is quiet and local; picker is bounded and focused.
- Desktop first viewport uses width coherently: no stranded chat rail, no massive empty center/right area, multiple useful widgets visible.
- Avatar chat looks like a compact chat workspace: clear transcript ownership, composer hierarchy, visible session/status/model context.
- Todo/daily-task data cannot consume the first viewport; long lists scroll internally or show bounded rows plus summary.
- Buttons/inputs/selects do not show browser-default styling inside Avatar.
- Mobile has no horizontal overflow, no clipped controls, no overlapping edit chrome, and controls are tappable enough for validation.
- JavaScript remains limited to streaming chat/local state; HTMX owns layout mutations and widget CRUD.

### 8. Orchestration Guidance

Recommended lane order:

1. Main implementation lane: Phase 1 empty rows and focused picker. This owns `AvatarDashboardComponents.java`, `AvatarDashboardController.java` if needed, `avatar-dashboard.css`, and focused tests. No parallel code edits in these files.
2. Design/CSS lane: after Phase 1 merges locally, Phase 2 desktop balance and Phase 4 button/list/mobile CSS can proceed, but still serial against `avatar-dashboard.css`.
3. Chat lane: Phase 3 can be a separate code-edit lane only if it owns `compactChat(...)`, `avatar-chat.js`, and chat CSS sections exclusively. It must rebase/read after Phase 2 CSS changes before editing.
4. Validation subagent lane: non-mutating Playwright baseline can run before edits; final Playwright validation runs after all implementation phases. The subagent writes findings back to this notes file or a validation artifact path.
5. Docs/closeout lane: runs after implementation and validation pass. It owns docs and `.internal-dev` updates, then final commit.

Parallel-safe work:

- Non-mutating research against `/chat`, `/dashboard`, `/agents`, and SimplyPages demo screenshots.
- Playwright baseline capture while code is untouched.
- Drafting validation scripts/checklists under `target/` or a non-source artifact path.

Serial-only work:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `avatar-chat.js`
- `AvatarDashboardControllerTest.java`
- Any service/repository row cleanup behavior.

Stop rules:

- Stop and ask Dwight/orchestrator if a proposed fix requires a new layout persistence model, a full chat client merge, a SimplyPages upstream change, or JS-managed layout mutation.
- Stop if Playwright cannot validate the changed UI and no approved fallback exists.
- Stop if seeded local data makes visual review impossible; create a clean validation dataset rather than designing around stale debug rows.
- Stop on file ownership overlap with another agent; reconcile with `git status --short` and shared notes before continuing.

Subagent prompt outline for final Playwright validation:

> You are the delegated Playwright visual validation agent for Avatar UI polish in `/home/hickelpickle/Code/Java/magenta2`. Use model `gpt-5.3-codex` with reasoning effort `medium`. Read `AGENTS.md`, `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`, `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`, and this notes section. Validate the live Magenta app plus `http://localhost:8080/demos/htmx-editing`. Capture the required screenshots, compare Avatar normal/edit/mobile/picker states against `/chat`, `/dashboard`, `/agents`, and the SimplyPages editing demo, and report pass/fail with concrete DOM metrics for overflow, empty rows, picker bounds, control positioning, and button styling. Do not edit source files.

### 9. Handoff Checklist

- Dedicated branch created before implementation.
- Empty edit rows render compactly and do not dominate screenshots.
- Add-widget selector is a focused modal/drawer/local picker, not a bottom-of-page catalog block.
- Desktop layout uses available width coherently across 1440px and 1920px.
- Avatar chat has stronger transcript/composer/status hierarchy without importing full `/chat` client behavior.
- Long todo/daily-task lists are visually bounded.
- Avatar controls have Magenta operational styling; micro controls remain compact and accessible.
- Mobile edit mode has no horizontal overflow and controls remain usable.
- Focused tests pass.
- Full `mvn -q test` passes.
- Bounded startup smoke passes or blocker is explicitly reported.
- Delegated Playwright validation passes against Avatar and `http://localhost:8080/demos/htmx-editing`.
- Docs and `.internal-dev` closeout are updated.
- Final commit includes implementation, tests, docs, and `.internal-dev` updates.

## Avatar Polish Playwright Validation - 2026-05-23

Result: PASS against `http://127.0.0.1:18083` and `http://localhost:8080/demos/htmx-editing`.

Blockers:

- None.

Checks:

- PASS: `/avatar` normal desktop loaded and screenshot captured.
- PASS: `/avatar?edit=true` desktop loaded and screenshot captured.
- PASS: add-widget catalog opened from an edit add-widget control as a focused modal picker; inner picker measured `768px x 460px` inside the fixed overlay, not a full-width inline band.
- PASS: empty edit rows render as `.avatar-empty-row-insert`; measured 7 empty rows, each about `71px` tall, with no blank `.avatar-dashboard-row-shell-editing` bands.
- PASS: widget top-corner edit controls are compact absolute decorators; measured 9 groups, all `position=absolute`, about `157px x 22px`, with no select or numeric width-preset buttons.
- PASS: Avatar chat exposes status/chips/transcript/composer hierarchy: status present, 2 chips, transcript container, textarea, and submit button.
- PASS: long todo list is constrained; measured 8 todo rows in `.avatar-list-constrained`, `352px` client height, `1229px` scroll height, `overflow-y:auto`.
- PASS: mobile `/avatar?edit=true` at `390px` viewport had no horizontal overflow (`scrollWidth=390`) and no overflowing elements.
- PASS: SimplyPages demo loaded after clicking `Load editing demo fragment`; measured 9 editable modules, 7 edit buttons, 6 delete buttons, 3 add-module sections, and 5 insert-row sections.
- PASS: `/chat`, `/dashboard`, and `/agents` were reachable and captured as style references.

Artifacts:

- `target/playwright-avatar-polish-validation/validation-results.json`
- `target/playwright-avatar-polish-validation/avatar-normal-desktop.png`
- `target/playwright-avatar-polish-validation/avatar-edit-desktop.png`
- `target/playwright-avatar-polish-validation/avatar-add-widget-catalog-opened.png`
- `target/playwright-avatar-polish-validation/avatar-edit-mobile.png`
- `target/playwright-avatar-polish-validation/simplypages-demo-launcher-desktop.png`
- `target/playwright-avatar-polish-validation/simplypages-demo-loaded-desktop.png`
- `target/playwright-avatar-polish-validation/chat-style-reference-desktop.png`
- `target/playwright-avatar-polish-validation/dashboard-style-reference-desktop.png`
- `target/playwright-avatar-polish-validation/agents-style-reference-desktop.png`
- `target/playwright-avatar-polish-validation/validate-avatar-polish.mjs`

Notes:

- No console warnings/errors or failed network requests were captured during the Playwright run.
- The live data still contains prior Playwright/debug todos and several persisted empty rows, but the todo list is internally constrained and empty rows now render compactly rather than as giant blank row bands.
