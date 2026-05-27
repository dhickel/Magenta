# Phase 01 Worker Directive: Work Area UI Consistency Repair

## Dispatch

Use `implementation_worker_agent` with model `gpt-5.4`, reasoning `high`.

This is a single coherent implementation unit. Do not split into additional workers unless the main thread revises the plan.

## Objective

Repair the Work Area browser/inspector, tag editor modal, and markdown/text editor modal so the UI matches Magenta's operational dashboard style and satisfies the locked acceptance criteria in `../00-specification-lock.md`.

## Read First

- `.internal-dev/plans/workarea-ui-consistency-repair/00-specification-lock.md`
- `.internal-dev/plans/workarea-ui-consistency-repair/01-current-state-analysis.md`
- `.internal-dev/plans/workarea-ui-consistency-repair/shared/validation-matrix.md`
- `.internal-dev/reviews/2026-05-27-workarea-ui-expectation-review.md`
- `.internal-dev/knowledge/workarea-operational-ui-consistency.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`

## Editable Scope

Primary editable files:

- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-workarea-editor.js`
- Focused tests, likely `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- Service tests only if tag service behavior is touched, likely `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java`
- Asset-version references if CSS/JS cache versioning requires it.
- Closeout docs and `.internal-dev` artifacts required by repo workflow, including changelog and relevant knowledge/spec/docs updates.

Forbidden scope:

- Do not implement tag deletion.
- Do not rewrite Work Area filesystem services, path confinement, persistence schema, or route families unless a focused test proves a directly blocking bug.
- Do not add broad framework abstractions or upstream SimplyPages changes in this phase.
- Do not change auth/security posture.
- Do not use raw ad hoc HTML workarounds where existing fragment/component style is sufficient.
- Do not send email.

## Experience Contract

Visual system:

- Match `/dashboard`, `/agents`, and `/avatar`: dense operational tooling, compact blue-gray bordered panels, small-radius controls, semantic chips, low shadow, row/list management, compact headings.
- Use visible buttons or icon buttons with useful title/aria labels for commands. Avoid empty click boxes and hidden affordances.
- Tag inventory is row/table-like, not card stacks.
- Modals are bounded, scroll internally, and render above top navigation.

Desktop expectations:

- Browser and inspector keep stable widths. Long content wraps/truncates inside fixed bounds.
- Collapsed inspector is a narrow rail with one obvious expand control.
- Editor modal has stable dimensions, resize corner, bounded panes, persistent top-left commands, top-right close, and real segmented tabs.

Mobile expectations:

- Explorer stacks without horizontal page scroll.
- Tag and editor modals fill available viewport without hiding controls behind topnav.
- Split editor mode stacks panes if needed but keeps shell dimensions stable and scroll bounded.

Visual failure examples:

- A collapsed rail showing a vertical filename/root dot.
- A tag manager that looks like stacked dashboard cards.
- A modal where the topnav covers the header.
- Long filenames forcing hidden columns or page horizontal scroll.
- Edit/Preview/Split buttons that look like loose text buttons or move when modes change.

## Implementation Checkpoints

### Checkpoint 1: Browser And Inspector

1. Remove selected-name/root-dot rendering from the collapsed inspector rail.
2. Restyle collapsed inspector geometry so the expand affordance is centered/clear and no icon is stranded at the bottom.
3. Lock browser/inspector width behavior with `minmax(0, ...)`, `min-width: 0`, and bounded wrapping/truncation on text children.
4. Preserve whole-row HTMX selection and explicit-control exclusions. Update tests to prove row whitespace selection wiring and action-control exclusions remain intact.
5. Make the inspector tag manager affordance visibly read as a real button, preferably `Manage Tags`, with a clear target and no hidden empty box.

Do not proceed to the tag modal checkpoint until focused rendering tests cover collapsed rail and row selection wiring.

### Checkpoint 2: Tag Editor Modal

1. Rewrite the tag manager presentation into a bounded operational modal with explicit header/filter/body/footer geometry.
2. Add top filter controls for directory/file tags. Use HTMX where practical for server-rendered filtering; if local filtering is simpler, keep JavaScript narrow and justify it in closeout.
3. Render tags as compact rows with identity/display name, slug, type chip, and truncated LLM-friendly description.
4. Row click opens a focused edit modal/area for that tag; explicit assign/edit buttons must not conflict with row click behavior.
5. Create/edit forms must constrain target type to directory or file. Do not expose deletion.
6. Ensure assigned tags remain visible and removable only if the pre-existing assignment-removal behavior is intentionally still part of the current surface; no tag-definition deletion.
7. Ensure modal overlay/body renders above topnav and scrolls internally.

Do not proceed to editor modal polish until focused tests cover row/filter/edit structure, type constraints, no deletion affordance, and high-overlay modal hooks.

### Checkpoint 3: Markdown/Text Editor Modal

1. Tighten editor modal shell hierarchy: title/path line, top-left icon controls, top-right close, segmented tab row, status row, bounded body.
2. Keep Save/Undo/Redo/Revert controls as icon buttons with title/aria labels.
3. Make Edit/Preview/Split true segmented tabs/switcher controls.
4. Keep outer modal dimensions stable across mode changes. Bound source and preview panes; split mode must not create unbounded horizontal or vertical overflow.
5. Preserve existing narrow JS for mode switching, dirty state, undo/redo/revert, and markdown preview sync.
6. Keep resize affordance on desktop and disable/adjust appropriately on mobile.
7. Ensure editor overlay renders above topnav.

Do not proceed to final validation until focused tests cover editor controls/tabs/modal hooks and asset versioning is addressed if static assets changed.

### Checkpoint 4: Closeout Updates

1. Update relevant specs if the intended contract changes; otherwise record no spec impact in changelog with explanation.
2. Update `.internal-dev/knowledge/workarea-operational-ui-consistency.md` with reusable lessons from the repair.
3. Update relevant `docs/` files for user-facing or technical behavior changes.
4. Add a changelog entry under `.internal-dev/changelogs/`.
5. Leave email summary as main-thread responsibility.

## Validation Commands

Run focused tests first:

```bash
mvn -Dtest=AvatarDashboardControllerTest test
```

If service/tag behavior changed:

```bash
mvn -Dtest=WorkAreaExplorerServiceTest test
```

Run broader relevant test/build checks as needed by touched files, then startup smoke:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

After code-level checks pass, hand off to a separate validation/browser agent for Playwright screenshots and visual critique using `shared/validation-matrix.md`.

## Stop Conditions

- Stop if topnav layering cannot be fixed locally and appears to require SimplyPages framework changes. Capture evidence and return to the main thread.
- Stop if browser/validation model routing cannot satisfy available tool constraints without unapproved fallback.
- Stop before adding tag deletion, persisted editor geometry, reusable framework extraction, schema changes, or broad service rewrites.
- Stop if local services/secrets block startup or browser validation; report the exact dependency.

## Do Not Close Unless

- All acceptance criteria in `00-specification-lock.md` are satisfied or explicitly blocked with evidence.
- Browser/inspector, tag modal, and editor modal checkpoints are implemented and tested.
- Static CSS/JS cache versioning is updated if needed.
- Separate Playwright validation has screenshots and visual critique queued/performed by the main thread validation route.
- `.internal-dev`, `docs/`, and changelog closeout expectations are completed or explicitly assigned to main-thread closeout.
