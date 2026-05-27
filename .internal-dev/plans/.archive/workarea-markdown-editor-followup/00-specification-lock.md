# Work Area Markdown Editor Follow-up - Specification Lock

## Classification

Small: one coherent implementation loop covering a focused Work Area markdown/text editor improvement plus scoped rendered-markdown layout fixes.

## Locked Objective

Fix markdown rendering/layout issues and improve the Avatar Work Area text/markdown editor so rendered markdown stays inside its containers and markdown files can be edited, previewed, split-previewed, saved, and locally undone without a save-to-switch workflow.

## User-Visible Outcome

- Rendered markdown lists, bullets, blockquotes, code blocks, and tables remain visually inside their containing panels.
- Work Area markdown/text editing feels consistent with the Avatar operational UI: compact, dense, sleek, and usable.
- Markdown editor supports `Edit`, `Preview`, and `Split` modes.
- Preview renders the current unsaved textarea content.
- Save remains explicit and persists raw content.
- Syntax/highlighting claims are honest: either first-pass source styling and enhancement hooks are visible, or no full syntax-highlighting claim is made.

## Assumptions

- Existing service contracts are mostly adequate; `WorkAreaExplorerService.preview(...)` and `saveText(...)` remain the persistence/read policy boundary unless the worker finds a concrete service guard gap.
- Work Area markdown visual consistency can be fixed primarily through shared scoped CSS and fragment markup.
- A non-persistent markdown preview endpoint can live in `AvatarDashboardController`, with controller logic kept thin and rendering/sanitization delegated to existing renderer/fragment helpers.
- Narrow JavaScript is acceptable for local editor mode switching, dirty state, undo/redo, and debounced preview synchronization because those states live in the browser before save.
- HTMX remains the default for standard save/CRUD and fragment refresh behavior.
- The configured `implementation_worker_agent` role may be unavailable in this environment; orchestration may need the fallback `worker` role with `gpt-5.3-codex` because of prior tooling constraints. Record that as `TOOLING_CONSTRAINT` if it occurs.

## Constraints

- Follow `AGENTS.md`, `.internal-dev/AGENTS.md`, and `.internal-dev/specifications/AGENTS.md`.
- Before mutating code, the worker must re-read the closest package guides and the relevant living specs:
  - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/rendering/AGENTS.md` if touching `ChatMarkdownRenderer`
  - `.internal-dev/specifications/web.md`
  - `.internal-dev/specifications/simplypages.md`
  - `.internal-dev/specifications/api.md` if adding/changing routes
- Read relevant knowledge before implementation:
  - `.internal-dev/knowledge/rendered-markdown-spacing.md`
  - `.internal-dev/knowledge/thinking-block-markdown-alignment.md`
  - `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
  - `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
  - `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before browser validation only
- Use SimplyPages/HTMX patterns first; do not turn this into a SPA.
- Do not weaken markdown sanitization or allow raw HTML/script injection.
- Do not implement image preview/editing/crop/annotation features.
- Do not replace the markdown library unless focused tests prove parser output is malformed and no CSS/renderer-alignment fix can solve the observed issue.

## Current State Anchors

- `WorkAreaExplorerFragments.viewer(...)` renders markdown via `safeMarkdown(...)` inside `.avatar-workarea-rendered`.
- `WorkAreaExplorerFragments.textEditor(...)` currently presents a plain textarea and `Save File`; markdown `Rendered`/`Text` switching reloads server fragments and cannot preview unsaved DOM edits.
- `AvatarDashboardController` already owns Work Area viewer/editor/save routes near `workAreaTextViewer`, `workAreaPreview`, `workAreaTextEditor`, and `saveWorkAreaText`.
- `avatar-dashboard.css` currently has `.avatar-workarea-rendered` max height, overflow, border, background, and padding but lacks normalized markdown child spacing.
- `magenta.css` and `orchestration.css` already contain scoped markdown spacing for chat/planning surfaces.

## Acceptance Criteria

- Work Area rendered markdown lists and bullets stay inside the rendered container and do not visually run off the left edge.
- Headings, paragraphs, lists, nested lists, blockquotes, code blocks, inline code, and tables have readable spacing and do not crowd or overlap.
- Markdown editor lets a user change source and view `Preview` or `Split` before saving.
- Preview uses current unsaved textarea content through a non-persistent sanitized rendering path.
- Save persists raw content, and the refreshed preview reflects the saved text.
- Text files still edit and save correctly.
- Undo controls behave predictably for local edit sessions; if only session-level revert is implemented, labels and behavior make that scope clear.
- Mobile layout does not overlap or overflow; split mode stacks or otherwise remains usable.
- Any source styling/highlighting claim is supported by visible behavior and tests, or the UI/docs describe it only as first-pass styling/enhancement hooks.
- Specs/docs replace old rendered-tab-active/save-to-switch wording with the new edit/preview/split behavior.

## Negative Checks

- No image editing/annotation/crop features.
- No global markdown/list CSS resets.
- No broad workspace/file-browser redesign.
- No unsanitized markdown HTML in preview or saved rendering.
- No save-on-preview side effect.
- No Playwright proof that creates arbitrary user files as the main evidence path; use existing/seeded demo files or controlled setup.
- No claim of full syntax highlighting unless a real editor/highlighting implementation exists.

## Required Documentation And Internal Closeout

The worker must update affected documentation and internal records as part of the same implementation loop:

- `.internal-dev/specifications/web.md`: Work Area markdown editor/render behavior.
- `.internal-dev/specifications/simplypages.md`: HTMX plus narrow-JS editor composition expectations if behavior materially changes.
- `.internal-dev/specifications/api.md`: only if a new preview route or payload contract is added.
- `.internal-dev/specifications/horizon-ideas.md` or `.internal-dev/specifications/deferred-features.md`: only if the image preview/editing idea needs durable future context; prefer horizon unless accepted as deferred capability.
- `docs/end-user/avatar-dashboard.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/changelogs/<date>-workarea-markdown-editor-followup.md`
- Archive this plan under `.internal-dev/plans/.archive/` only after implementation, validation, and closeout are complete.
