# Work Area UI Consistency Repair Validation Matrix

## Code-Level Validation

| Area | Checks | Suggested Commands |
| --- | --- | --- |
| Fragment rendering | Collapsed inspector has only expand affordance; visible tag button; row click HTMX trigger excludes explicit controls; tag modal has filter/rows/scroll body; editor modal has icon controls, close, tabs, stable shell hooks. | `mvn -Dtest=AvatarDashboardControllerTest test` |
| Service/tag guard behavior | Existing directory/file tag mismatch and single-path assignment guards still pass. Add/update only if implementation touches service behavior. | `mvn -Dtest=WorkAreaExplorerServiceTest test` |
| Static assets | CSS/JS syntax remains valid; changed selectors are scoped to Avatar/Work Area surfaces; asset version query is bumped if cacheable CSS/JS changes. | `mvn test -DskipTests` only if no narrower static check exists; otherwise use focused build/test commands available in repo |
| Startup smoke | Application context starts after UI changes. | `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` |

## Browser Validation

Browser validation must run after implementation by a separate validation/browser agent against a live app. It must include screenshots and a visual critique. If the current route cannot launch the requested/default browser model, record `TOOLING_CONSTRAINT` and stop for main-thread/user approval before fallback.

Required surfaces:

- `/dashboard`, `/agents`, and `/avatar` as visual references.
- `/avatar` Work Area explorer with an active Work Area containing long filenames/paths and tags/descriptions.
- Collapsed inspector and expanded inspector.
- Tag editor modal.
- Markdown/text editor modal in Edit, Preview, and Split modes.
- Mobile/narrow viewport for explorer, tag modal, and editor modal.

Required assertions:

- Collapsed inspector contains no selected filename, `drafts`, root `.`, or stranded bottom icon; only clear expand affordance is visible.
- Long filenames/paths/tags/descriptions do not cause page-level horizontal overflow or hidden column push.
- Clicking row whitespace selects the row; clicking explicit row buttons does not accidentally select the row.
- Tag manager button is visible and discoverable.
- Tag modal renders above topnav, has a fixed header/filter plus dedicated scroll body, uses row/table layout, supports directory/file filtering, opens focused edit UI from row click, constrains type to directory/file, and exposes no deletion.
- Editor modal renders above topnav, has top-left save/undo/redo/revert icon controls, top-right close, segmented Edit/Preview/Split tabs, stable outer dimensions while switching modes, bounded panes, and desktop resize corner.
- Mobile layout stacks without clipped text, overlapping controls, or horizontal page scroll.

## Visual Quality Criteria

Pass requires screenshot-backed critique confirming:

- Style consistency with `/dashboard`, `/agents`, and `/avatar`: compact blue-gray borders, small radii, dense rows, semantic chips, restrained shadows, clear hierarchy.
- No nested-card treatment for tag rows.
- No browser-default-looking controls for primary Work Area actions.
- No stranded columns, excessive dead zones, incoherent gutters, clipped controls, or topnav overlap.
- HTMX remains the transport for standard Work Area CRUD/fragment interactions; JavaScript use remains justified and editor-local.

## Stale-Reference And Closeout Sweep

Before final sign-off, sweep changed docs and `.internal-dev` artifacts for stale references to planned/pending/not implemented claims, old artifact paths, `/tmp` evidence, stale agent ids, TODO markers, and outdated phase wording.

Required closeout artifacts:

- Update relevant `.internal-dev/specifications/` entries if behavior contract changes; otherwise changelog must say `Specification Impact: none` with explanation.
- Update or extend `.internal-dev/knowledge/workarea-operational-ui-consistency.md` with reusable UI lessons from implementation/validation.
- Add a finalized changelog under `.internal-dev/changelogs/`.
- Update relevant `docs/` files for user-facing or technical behavior changes.
- Move finalized plan artifacts to `.archive/` only at final closeout if repo workflow treats the plan as complete.
- Create a git commit including implementation and `.internal-dev`/docs updates unless the user explicitly says not to commit.
