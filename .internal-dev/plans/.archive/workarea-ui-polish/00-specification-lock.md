# Work Area UI Polish Specification Lock

## Classification

Small: one coherent implementation unit plus validation. This is focused remediation of the Avatar Work Area browser, inspector, action controls, and markdown/text editor modal. It is not a broad redesign.

## Tooling Constraints

- TOOLING_CONSTRAINT: the user requested a `gpt-5.2-xhigh` advanced planning agent, but this tool context is fixed to the available `advanced_planning_agent` behavior (`gpt-5.5` high). Proceeded with this planning agent and recorded the mismatch.
- TOOLING_CONSTRAINT: the UI standards sidecar requested as `gpt-5.2-xhigh` was unavailable in this tool context; the sidecar proceeded as `gpt-5.5` high/xhigh-equivalent planning review and edited no files. Its acceptance note is incorporated into this plan.
- Execution defaults for this plan: implementation worker `gpt-5.3` high unless the main-thread dispatcher must honor the user-supplied override to `gpt-5.2-high` and can actually access it; validator `gpt-5.5` high; browser proof agent per current repo/tool availability with any model fallback recorded as TOOLING_CONSTRAINT in evidence.

## Source Contracts

Verified local contracts:

- `.internal-dev/specifications/web.md`: Avatar and Work Area web surfaces are dense operational UI, HTMX-first where feasible, with focused Playwright screenshots and visual critique for UI changes. `WEB-20260525-04` keeps the Work Area explorer as Magenta-local details/list fragments with separate inspector, stable columns, full-row selection, and modal tag management. `WEB-20260526-01` requires compact text/markdown editing with explicit save, local undo/redo/revert, and unsaved markdown preview/split modes.
- `.internal-dev/specifications/simplypages.md`: Work Area explorer remains Magenta-local; CRUD, filtering, row actions, form submissions, and fragment refreshes stay HTMX-first unless narrow JavaScript is the simpler local interaction. Editor JavaScript is allowed for dirty/history state, mode switching, and unsaved preview synchronization.
- `.internal-dev/specifications/api.md`: Work Area file APIs own preview/save/image behavior. Markdown preview route `POST /avatar/_work-areas/{workAreaId}/viewer/markdown-preview` is non-persistent and must not call save.
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`: UI must keep file-manager information architecture, service-owned path confinement, stable HTMX modal host, and browser validation on the styled `/avatar` surface.
- Package guides for `api.web`, `avatar`, and `ai.orchestration.workspaces`: controllers stay thin; filesystem/path/security policy stays in workspace services; Avatar UI follows the compact operational console style.

## Current-State Anchors

- `WorkAreaExplorerFragments.shell()` currently labels the header command `Close`, not `Close Workspace`.
- `WorkAreaExplorerFragments.inspector()` currently renders `Preview & Details`, hint prose from `viewerHint(...)`, metadata, and bottom row action buttons. This conflicts with the requested simplified inspector.
- `WorkAreaExplorerFragments.row()` currently uses text action buttons for Open/View, Rename, Delete, Copy, and Move. It already protects full-row selection from button clicks through the `hx-trigger` guard.
- `WorkAreaExplorerFragments.textEditor()` currently renders mode controls and wide text buttons in one toolbar and uses the generic modal shell.
- `avatar-workarea-editor.js` already owns local dirty/history, undo/redo/revert, mode switching, and unsaved markdown preview synchronization. Keep this JS narrow.
- `WorkAreaExplorerService.preview(...)` already enforces regular-file, safe type, size, UTF-8, and image/text/markdown preview policy. Do not duplicate or weaken those policies in UI code.

## Acceptance Criteria

- Inspector expanded: selected name is clear and has a tooltip; compact path/metadata, tag chips, `Tag Editor`, metadata summary, and a bounded preview box are present. Bottom action buttons are removed. `Preview & Details` and old explanatory hint prose are gone.
- Inspector preview box: directories and unavailable/unsupported content show `Preview unavailable`; images show a contained thumbnail; text and markdown show a compact preview using existing preview/rendering policy where reasonable.
- Inspector collapsed: renders a real slim rail or compact panel with a visible expand icon button, no clipped title-only state, and re-expands while preserving selected path when available.
- Browser list: full-row selection still works; action clicks do not select/navigate rows; long names truncate inside the name column with `title`/tooltip; horizontal overflow is avoided where feasible by compressing/hiding secondary columns at narrower widths before sacrificing actions.
- Row actions: Open/View, Rename, Delete, Copy, and Move are compact icon buttons with `aria-label`, `title`, consistent sizing, and visible focus styling. They remain HTMX actions.
- Shell close command says `Close Workspace`, keeps existing target behavior, and is styled as a compact command.
- Editor modal: opens as a full modal window with stable dimensions, max viewport bounds, internal scrolling, and a CSS resize corner affordance where feasible. It has top-right close, top-left icon controls for Save/Undo/Redo/Revert, and real Edit/Preview/Split tabs below the command row.
- Markdown editor: Edit/Preview/Split toggles do not resize or jump the modal/editor frame; preview/split render unsaved content without saving; save persists explicitly through HTMX. Split is two columns on desktop and stacks cleanly on mobile.
- Plain text editor: only Edit mode is visible; markdown preview/split controls are hidden.
- Rendered markdown spacing remains scoped and readable for lists, blockquotes, and code blocks.
- Existing tag editor modal/create/assign behavior still works; tag deletion remains out of scope.
- Image preview in inspector/viewer remains confined with no stretching or overflow.
- No path traversal, symlink, size-limit, or filesystem security behavior is weakened.

## Negative Criteria

- Do not add tag deletion.
- Do not introduce a broad file explorer redesign, new generic SimplyPages module, or upstream SimplyPages refactor.
- Do not replace HTMX CRUD/file actions with broad JavaScript transport.
- Do not introduce editor libraries such as CodeMirror.
- Do not make inspector preview perform unsafe or large file reads beyond existing `WorkAreaExplorerService` preview policy.
- Do not change Back semantics beyond simple directory-up.
- Do not mark complete without code-level validation, delegated browser screenshots, and visual critique.
- Do not leave active plan artifacts unarchived during closeout.

## Assumptions

- Existing Work Area fixtures or controlled temporary fixtures can cover long filenames, markdown, text, image, and unsupported/directory preview states during Playwright validation.
- If inline inspector text/markdown/image preview can reuse existing service/controller behavior without broad API changes, that is preferred. If a small helper/route adjustment is required, keep it local to `AvatarDashboardController`/`WorkAreaExplorerFragments` and cover it with controller tests.
- The branch `workarea-markdown-editor-followup` is the intended implementation branch.

## User Decision Gates

Stop and ask the user before:

- Adding a new generic SimplyPages module or dependency.
- Expanding Work Area API contracts beyond confined preview/editor support.
- Changing filesystem/path/security service behavior.
- Deferring browser validation or startup validation for reasons other than a clear, user-approved blocker.

## Stop Rules

- Stop if preview implementation would require bypassing `WorkAreaExplorerService.preview(...)` policy.
- Stop if narrow UI changes cannot avoid horizontal overflow without removing required actions/selection.
- Stop if Playwright cannot run due to missing infrastructure; report the blocker instead of treating unit tests as full sign-off.
