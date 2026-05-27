# Work Area UI Consistency Repair Preplanning Handoff

## Objective

Repair the existing Work Area browser, inspector, tag editor, and markdown/text editor UI so the feature feels like a coherent Magenta operational surface rather than a partially wired prototype.

## User-Visible Outcome

The Work Area browser should have full-row selection, stable fixed-width list/detail layout, a sane collapsible inspector, an explicit tag management button, a scrollable row-based tag manager, and a markdown/text editor modal that matches the Avatar/agent dashboard style without rendering under the top navigation.

## Work Type

Small single-agent implementation plan with one coherent Work Area UI repair unit. The code surface is coupled enough that splitting into many workers would create unnecessary merge and visual consistency risk.

## Source Context Read

- Root `AGENTS.md` user-provided instructions.
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/knowledge/workarea-operational-ui-consistency.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-workarea-editor.js`
- Relevant SimplyPages docs/demo for row/column layout, HTMX swaps, and the editing demo.

## Model And Tooling Constraints

- Planning must use `advanced_planning_agent` with model `gpt-5.5`, reasoning `high`.
- Implementation should use `implementation_worker_agent` with model `gpt-5.4`, reasoning `high`.
- Model correction: the browser validation default should be `gpt-5.4` medium. Earlier lower-version wording was erroneous and should not be reused.
- Testing/Playwright validation follows repo policy: use a separate validation/browser agent, with user overrides only if explicitly supported by the current route. If a requested model is unavailable, record `TOOLING_CONSTRAINT` and stop before fallback.

## In Scope

- Work Area explorer side inspector collapse/expand behavior.
- Long filename and path wrapping/truncation in inspector and browser layout.
- Browser row selection target so the whole row selects, not only the name text.
- Explicit inspector tag editor button replacing hidden/empty click affordance.
- Tag editor modal rewrite into bounded scrollable operational modal:
  - top filter for directory/file tags;
  - row-based list, not cards;
  - each row shows tag display/display slug, type, truncated LLM-friendly description, and assign/edit affordance;
  - click row opens focused modal/edit area;
  - create/edit controls constrain type to directory or file;
  - deletion remains out of scope.
- Markdown/text editor modal visual repair:
  - top-nav overlay/z-index bug fixed or diagnosed in project/upstream boundary;
  - desktop resize corner;
  - close control in the top-right;
  - save/undo/redo/revert icon controls in the top-left;
  - Edit/Preview/Split as real tabbed/switcher UI inside the editor;
  - stable outer dimensions when mode changes;
  - bounded source/preview panes that use available space.
- Focused docs/knowledge/changelog/spec closeout.
- Focused tests and Playwright visual validation with screenshots.

## Out Of Scope

- Tag deletion.
- Broad SimplyPages upstream changes unless root cause proves the modal/topnav issue is a framework bug.
- Replacing the Work Area filesystem service or path confinement rules.
- Deep end-to-end production browser campaign beyond changed Work Area surfaces.
- New file creation Playwright proof; file creation can be covered by existing focused tests or smoke only if touched.

## Constraints

- Keep Work Area operations confined to the agent/work area space through service validation.
- Controllers stay thin; filesystem, path, tag, and destination validation stay service-owned.
- HTMX remains the default for CRUD, row actions, modal loads, form submissions, and dependent region refreshes.
- JavaScript remains narrow for editor-local state such as mode switching, undo/redo/revert, preview sync, and resize behavior if needed.
- Match Magenta operational UI style: compact blue-gray controls, small radii, dense rows, semantic chips, no nested cards for list rows, no hero/marketing layout.
- Use icons for common actions where possible and keep buttons text-readable where the command needs clarity, such as `Close Workspace`.
- Do not widen browser/list columns for long filenames. Lock widths and wrap/truncate text inside fixed bounds.
- Modal bodies must be scrollable and must render above the shell top navigation.

## Acceptance Criteria

- Collapsed inspector rail shows only a clear expand affordance; no stale selected filename, no `drafts`, no root `.` label, no stranded bottom icon.
- Expanded inspector keeps long file names and paths within its locked width using wrapping/truncation and title text where useful.
- Browser list row selection works when clicking anywhere on the row except explicit action controls.
- The tag editor is opened by an obvious button in the inspector; no empty hidden click target remains.
- Tag modal fits within viewport, scrolls internally, renders above top navigation, and uses directory/file filtering plus row-based tag inventory.
- Tag rows display tag identity, type, and truncated LLM-friendly description; create/edit controls enforce directory or file type.
- Markdown/text editor modal renders above top navigation, has a consistent dashboard-like shell, top-left icon controls, top-right close, proper tab/switcher UI, stable size across modes, and desktop resize affordance.
- Visual pass confirms no horizontal overflow, clipped text, overlapping controls, stranded dead zones, or modal content hidden behind nav on desktop and mobile.
- Asset versions are bumped if static CSS/JS changes require cache invalidation.

## Negative Criteria

- Do not pass if a control merely exists but looks like an empty box, hidden click target, or browser-default form.
- Do not pass if tag rows are card stacks or if the tag modal scrolls the page behind it instead of its own body.
- Do not pass if long names widen hidden columns, force horizontal page scroll, or obscure inspector metadata.
- Do not pass if editor mode changes alter the modal shell size or move controls.
- Do not pass if browser proof is DOM-only without screenshot-backed visual critique.

## Validation Expectations

- Unit/controller rendering tests for changed fragment structure and HTMX target behavior.
- Focused Java/service tests for tag assignment/type guard behavior if touched.
- Static checks for JavaScript if changed.
- Bounded Spring Boot startup smoke.
- Playwright validation by a separate agent against a live app with screenshots and critique:
  - desktop and mobile Work Area explorer;
  - collapsed/expanded inspector;
  - long filename selection;
  - tag editor open/filter/create/edit row behavior;
  - markdown editor Edit/Preview/Split stability;
  - modal z-index/topnav behavior;
  - row selection and action controls.

## Planner Deliverables

Use this handoff and the UI expectation review to produce a compact small-plan suite:

- one specification/criteria lock;
- one current-state analysis;
- one worker directive for the implementation worker;
- one validation checklist/matrix;
- final orchestration notes with Playwright gates and closeout requirements.

The plan must require phased implementation with screenshot review after each major UI surface: inspector/browser, tag modal, editor modal, then final integration/browser pass.
