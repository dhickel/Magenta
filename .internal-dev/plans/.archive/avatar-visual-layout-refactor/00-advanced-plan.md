# Avatar Visual Layout Refactor Advanced Plan

## 1. Objective

Repair `/avatar` so it reads as a professional Magenta operational console instead of a sparse, unbalanced page. The target is a compact first viewport with clear hierarchy, aligned panels, useful density, responsive behavior, and in-place decorated layout editing on a 12-column grid.

This plan is also a process correction. Agents must be explicitly guided to compare UI work against SimplyPages docs/demos, use a scratch page for planning experiments, run Playwright visual review after UI changes, and capture reusable SimplyPages knowledge without treating scratch experiments as source truth.

## 2. Inputs And Assumptions

Confirmed inputs:

- User-provided screenshots from `http://localhost:8080/avatar` show the failure: huge empty space, centered narrow widgets, disconnected chat rail, excessive top chrome, poor column balance, cramped row actions, inconsistent widget sizing, and weak scan hierarchy.
- Current Avatar UI lives mainly in `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`, `AvatarDashboardComponents.java`, and `src/main/resources/static/css/avatar-dashboard.css`.
- Current code already has row/widget persistence and 12-column widths, but the edit interface is still rendered as a separate modal-like panel through `/avatar/_edit`.
- SimplyPages docs and demo code contain relevant patterns for `Row`, `Column`, `EditableModule.wrap(...)`, `EditModalBuilder`, OOB swaps, insert-row controls, add-module modals, and 3/4/6/8/12 width choices.
- Existing Avatar style guidance says `/avatar` must align with `/dashboard` and `/agents`: compact operational panels, thin blue-gray borders, low radii, semantic chips, and HTMX-first fragments.

Assumptions to verify before risky changes:

- The refactor may preserve existing Avatar layout persistence if row/widget behavior is sound.
- In-place layout editing should replace the separate full editor modal for move, resize, add-row, add-widget, and delete actions.
- A per-widget detail/edit icon may still open a modal for deeper module-specific iteration, but layout positioning must happen in place.
- A scratch page may be added under an internal/dev-only route and used for design experiments during planning and validation.

## 3. Scope

In scope:

- `/avatar` visual composition and responsive layout.
- In-place decorated edit mode for dashboard rows and widgets.
- 12-column move/resize/add-row/add-widget/delete controls visible on the real dashboard surface.
- A compact always-available per-widget detail icon for module-specific modal iteration.
- Root and Avatar package `AGENTS.md` guidance for visual Playwright testing, UI quality criteria, SimplyPages research, scratch page rules, and knowledge updates.
- New reusable knowledge file for SimplyPages Avatar layout/editing patterns.
- Playwright validation with screenshots and explicit visual critique.
- Phase email updates and final email wait.

Out of scope for this corrective pass:

- Replacing Avatar persistence tables unless validation proves they are defective.
- Reworking Work Area runtime/output routing that already exists.
- General `/dashboard`, `/agents`, or `/chat` redesign.
- Treating scratch-page code as reusable production code without extracting and documenting the stable pattern.

## 4. Current-State Analysis

`AvatarDashboardController` builds a separate shell for `/avatar` and wires `/avatar/_edit` to `AvatarDashboardComponents.editModal(...)`. Layout mutations already exist under `/avatar/_layout/**`, and most return both editor and grid fragments.

`AvatarDashboardComponents.page(...)` renders a page header, toolbar, widget grid, and chat rail. The CSS grid currently reserves a main area plus sticky chat column, but the resulting dashboard in the screenshots does not use available horizontal space well. Widgets visually stack in a narrow center-left region while the right half of the page is mostly unused below the chat. The header/banner/nav consumes vertical space and reads as a separate landing surface rather than a compact operational console.

`AvatarDashboardComponents.editRow(...)` and `editWidget(...)` contain useful move/resize/add controls, but they live in an editor panel instead of decorating the actual row/widget frames. That separation likely contributed to agents designing for the editor surface rather than the real display.

SimplyPages demo code in `EditingDemoController` shows the core direction:

- rows wrap modules directly;
- modules are wrapped with `EditableModule.wrap(...)`;
- insert-row and add-module controls sit around the real page structure;
- 12-column widths use `Column.create().withWidth(width)`;
- detail editing can still use `EditModalBuilder` and OOB swaps.

## 5. Target Design

### Visual Shell

The `/avatar` first viewport should have:

- Compact page chrome with no oversized hero treatment.
- A balanced operational layout with primary dashboard content and chat/status rail using the full usable width.
- A visible first viewport containing several useful widgets without excessive scrolling.
- Consistent widget widths, gutters, and aligned top/bottom edges where content allows.
- Compact controls that do not crowd form inputs or force narrow text wrapping.
- No stranded columns or large unused voids at desktop widths.

### In-Place Edit Mode

The dashboard supports a decorated mode, toggled from the toolbar. In edit mode:

- The real dashboard grid receives an edit class such as `avatar-edit-mode`.
- Each row renders a row decoration bar above or inside the row wrapper with move up/down, add widget, add row below, and delete empty row when safe.
- Each widget renders a compact decoration area with move left/right/up/down, width select or segmented width controls for 3/4/6/8/12, delete/remove, and a detail icon.
- The detail icon remains available when not in edit mode and opens a modal/detail drawer for module-specific iteration only.
- Layout mutations remain HTMX-first and return OOB swaps for the affected dashboard grid, toolbar/edit state, modal container, and status fragment.
- The old separate edit modal may remain only as a temporary fallback during implementation and must be removed or demoted before completion.

### Scratch Page

Implementation may add an internal scratch page, for example `/avatar/_scratch/layout`, that:

- Is explicitly dev/planning-only.
- Renders sample Avatar rows/widgets with dense real-like content.
- Lets agents test layout composition and editing controls with Playwright before touching the production page.
- Must not be referenced from knowledge files as a source of truth.
- May provide extracted examples only after those examples are stabilized in production components or knowledge docs.

### Knowledge And Instructions

Root and package `AGENTS.md` must tell agents to:

- Inspect SimplyPages docs and demos before UI edits.
- Diagnose current code against SimplyPages examples before inventing UI.
- Use Playwright visual screenshots for any UI change.
- Evaluate alignment, density, scan hierarchy, spacing, gaps, wrapping, responsive behavior, control affordances, and practical usability.
- Update `.internal-dev/knowledge/` with stable lessons.
- Never cite or rely on scratch pages as source truth.

## 6. Implementation Plan

1. Create process artifacts.
   - Add this plan suite.
   - Update root `AGENTS.md` and Avatar package `AGENTS.md`.
   - Add `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`.
   - Email phase completion.

2. Refactor visual shell and CSS.
   - Tighten `/avatar` page chrome.
   - Replace the current sparse layout with a balanced dashboard shell using CSS grid constraints and responsive breakpoints.
   - Keep SimplyPages `Row`/`Column` for widget rows.
   - Preserve HTMX widget refresh behavior.

3. Convert edit modal into in-place decorated edit mode.
   - Add edit-state request parameter or route mode, such as `/avatar?edit=true` and `/avatar/_widgets?edit=true`.
   - Render row and widget decoration controls directly in the dashboard grid.
   - Change layout mutation targets from `#avatar-edit-container` to the dashboard grid/status surfaces.
   - Keep module detail modals separate from layout editing.

4. Add or formalize scratch page.
   - Create a dev-only scratch route if useful.
   - Use it only for Playwright design experimentation.
   - Do not link it from production navigation.

5. Validate and remediate.
   - Run unit/controller tests for changed routes.
   - Run bounded Spring startup.
   - Launch Playwright validation subagent for desktop and mobile screenshots, edit-mode interactions, and visual critique.
   - Remediate any significant visual issues before closeout.

6. Close out.
   - Update docs, changelog, knowledge, focus decisions, and unfinished work as applicable.
   - Email detailed completion report and wait for email instructions.
   - Commit implementation and internal-dev updates.

## 7. Validation Plan

Automated:

- Avatar service/controller tests that cover existing layout mutation endpoints.
- Any new scratch route or edit-mode endpoint tests.
- `mvn test` or the narrowest equivalent plus affected tests.
- Bounded application startup: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`.

Playwright visual validation:

- Desktop `/avatar` normal mode screenshot.
- Desktop `/avatar` edit mode screenshot.
- Mobile `/avatar` normal mode screenshot.
- Mobile `/avatar` edit mode screenshot.
- Interact with move, resize, add-row, add-widget/catalog, delete/remove, refresh, and detail modal controls where available.
- Compare against `/dashboard` and `/agents` visual language.
- Report visual issues explicitly: alignment, gaps, stranded columns, excessive whitespace, text overflow, cramped controls, poor density, weak hierarchy, overlapping content, broken sticky chat behavior, inaccessible controls, and mobile wrapping.

Acceptance criteria:

- No visually obvious stranded dashboard column or massive unused desktop void.
- Edit controls are in-place on the real layout, not only inside a separate modal editor.
- Layout editing uses 12-column widths and persists via HTMX.
- Detail modal/drawer is module-specific and does not own layout positioning.
- First viewport is meaningfully useful at desktop size.
- Mobile stacks coherently without horizontal overflow.
- Knowledge and AGENTS guidance are updated.

## 8. Handoff Checklist

- [ ] Read SimplyPages docs/demo references before UI edits.
- [ ] Preserve Avatar persistence/runtime boundaries.
- [ ] Implement visual shell correction.
- [ ] Implement in-place decorated edit mode.
- [ ] Add scratch page only as dev/planning support if useful.
- [ ] Run tests and startup.
- [ ] Run delegated Playwright visual validation.
- [ ] Remediate visual findings.
- [ ] Update docs, knowledge, changelog, focus records.
- [ ] Email phase reports and final report.
- [ ] Wait for final email instructions.
