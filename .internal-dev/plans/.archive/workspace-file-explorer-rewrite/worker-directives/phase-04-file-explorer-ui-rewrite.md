# Phase 04 Worker Directive: File Explorer UI Rewrite

## Objective

Replace the current card/modal/dropdown-heavy explorer UI with a Windows/Linux-style details/list file explorer: toolbar, path/breadcrumb, required columns, compact rows, row selection, row actions, and separate right inspect panel. No card view is allowed.

## Required Supporting Docs To Read

- `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/02-target-design.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/senior-engineer-guidance.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/validation-matrix.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `docs/technical/frontend-htmx.md`
- SimplyPages component catalog and `Table`/`Modal` docs/source as needed.
- External references listed in `00-specification-lock.md`.

## Exact Editable Files/Modules

May edit:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java` only for rendering hook adjustments not completed in Phase 03
- New `api/web` component/view helper classes if useful
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/css/magenta.css` only for truly shared table styles
- Narrow JS under `src/main/resources/static/js/` only if explicitly justified in notes
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- New component rendering tests if local patterns support them
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`

## Forbidden Scope

- No file/domain service changes except small compile adaptations explicitly caused by Phase 03 contract.
- No card classes for file/directory entries.
- No raw HTML string replacement of rendered module output.
- No broad Avatar shell redesign unrelated to Work Areas.
- No repo-local email ledger ; use direct AgentMail daemon/wait state only.

## Experience Contract

Desktop:

- Explorer appears as a bounded file-manager module, not a full-screen modal card grid.
- Top toolbar includes Back, Forward if supported or disabled, Up, Refresh, and create controls if existing behavior keeps them.
- Path/breadcrumb is directly under or beside toolbar and shows root plus current segments.
- Table headers are visible: Name, File Type, Size, Created, Last Modified, Tags, Actions.
- Rows are compact, one entry per row, with selected state.
- Directory rows navigate on name/open affordance.
- File rows select/inspect on click and show row actions.
- Right inspect panel sits outside the table region and updates for selected row.
- First viewport shows useful rows and inspect metadata without excessive whitespace.

Mobile:

- Table remains usable without page-level horizontal overflow.
- Inspect panel stacks below the table.
- Actions remain tappable and do not overlap.
- Long filenames wrap or truncate professionally without breaking row height wildly.

Fixture data:

- Root folder with nested directory.
- `notes.md` tagged `note`.
- `plain.txt`.
- `image.png`.
- `data.json`.
- `archive.bin` unsupported.
- Directory tagged `work-area`.
- File/directory with more than three tags to test `+N`.

Visual failure examples:

- Card grid/list of file cards.
- Missing headers.
- Required columns hidden on desktop.
- Inspector inside row or modal-only.
- Row actions wrap into multi-line blocks on normal desktop width.
- Table and inspect panel overlap or leave a huge blank middle.
- Mobile horizontal page scroll.

Proof:

- Playwright screenshots for desktop and mobile must be captured by validation subagent after this phase.

## Implementation Sequence

1. Remove/bypass current card-first Work Area explorer rendering.
2. Build details/list explorer markup directly with SimplyPages components.
3. Add stable CSS classes for toolbar, pathbar, table region, rows, selected state, tag chips, action column, and inspector layout.
4. Wire row selection and directory navigation through HTMX routes from Phase 03.
5. Render required columns with honest fallback values.
6. Render first few tags and overflow count.
7. Render row actions: view/eye when supported, rename, delete.
8. Render inspect panel outside the table with metadata and mirrored rename/delete placeholders if Phase 05 completes actual forms.
9. Add tests asserting required headers, no card classes, inspect panel, row actions, and HTMX targets.
10. Append JS justification if any JS is added.
11. Append evidence to `shared/implementation-notes.md`.

## Acceptance Criteria

- UI renders required details/list structure.
- No file/directory card view remains in the changed explorer.
- Required columns render on desktop.
- Right inspect panel is separate from table.
- Row selection/navigation/action HTMX attributes are present.
- Styling matches Magenta operational density.
- Component tests/controller tests cover structural contract.

## Negative Checks

- Fail if `FileExplorerMode.CARDS`, `file-explorer-cards`, or equivalent card entry classes remain in active Work Area explorer rendering.
- Fail if the only action access is a dropdown/modal.
- Fail if required columns are absent.
- Fail if inspect panel is modal-only.
- Fail if rendered module HTML is modified through string replacement.
- Fail if desktop screenshot still resembles the discarded UI.

## Validation Commands

```bash
mvn test -Dtest=AvatarDashboardControllerTest,WorkAreaControllerTest
git status --short
```

Playwright validation subagent must run focused browser checks per `shared/validation-matrix.md`.

## Stop Conditions

- Existing SimplyPages module cannot satisfy no-card table contract and no Magenta-local replacement is feasible without upstream dependency decision.
- UI cannot be validated by Playwright and user has not approved a blocked state.
- Browser screenshots show major layout failure or card regression.

## Senior Engineer Notes

Do not overdecorate this UI. Familiar file explorers are plain because the information density is the feature. A clean table with good selection/inspect behavior is much better than a stylish panel that hides columns or turns operations into card chrome.

## Do Not Close Unless

- [ ] Required columns render.
- [ ] Active explorer has no card view/classes.
- [ ] Right inspect panel is separate and visible.
- [ ] Row actions include view/rename/delete as applicable.
- [ ] Desktop and mobile Playwright screenshots exist.
- [ ] Visual critique has no must-fix findings.
- [ ] `shared/implementation-notes.md` has phase evidence.
- [ ] Validation red-team has passed before Phase 05 starts.
