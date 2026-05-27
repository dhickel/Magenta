# Implementation Worker Directive - Work Area Markdown Editor Follow-up

## Objective

Implement the small Work Area markdown/text editor follow-up: normalized rendered-markdown layout, Avatar-style Edit/Preview/Split markdown editing with unsaved preview, explicit save, local undo controls, focused tests, docs/spec updates, and changelog.

## Editable Scope

Primary editable targets:

- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/css/orchestration.css`
- A new narrow static JS file only if needed for local editor mode/dirty/undo/live-preview behavior, likely under `src/main/resources/static/js/`
- Existing tests:
  - `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
  - `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java`
  - `src/test/java/io/mindspice/magenta2/ai/chat/rendering/ChatMarkdownRendererTest.java` only if renderer behavior changes
- Docs/spec/changelog listed in `00-specification-lock.md`

Conditional target:

- `src/main/java/io/mindspice/magenta2/ai/chat/rendering/ChatMarkdownRenderer.java` only if renderer alignment is chosen and remains small. Do not rewrite renderer behavior broadly.

## Forbidden Scope

- Do not implement image editing, annotation, crop, paint-style editing, or future media workflows.
- Do not replace the whole markdown rendering library without evidence from focused tests.
- Do not weaken OWASP/CommonMark sanitization or allow script/raw HTML injection.
- Do not redesign the whole Work Area explorer or Avatar shell.
- Do not introduce a SPA framework or shift standard save/CRUD to custom JavaScript.
- Do not use global list resets or broad page-wide markdown CSS that affects unrelated UI.
- Do not perform unrelated cleanup, dependency churn, or route renames.

## Required Reading Before Edits

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/rendering/AGENTS.md` if touching renderer code
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/api.md` if adding/changing routes
- `.internal-dev/knowledge/rendered-markdown-spacing.md`
- `.internal-dev/knowledge/thinking-block-markdown-alignment.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`

## Implementation Steps

1. Confirm current anchors and tests.
   - Inspect `WorkAreaExplorerFragments.viewer`, `textViewer`, `textEditor`, `safeMarkdown`, and `renderMarkdown`.
   - Inspect `AvatarDashboardController` Work Area viewer/editor/save routes.
   - Inspect existing CSS rules for `.chat-message-body`, `.planning-preview-document`, `.chat-thinking-body`, and `.avatar-workarea-rendered`.
   - Inspect current `AvatarDashboardControllerTest` markdown editor/viewer assertions before changing test expectations.

2. Add shared rendered-markdown styling.
   - Introduce a scoped reusable class such as `.magenta-rendered-markdown`.
   - Normalize only descendants inside that class: first/last child margins, headings, paragraphs, `ul`/`ol` with safe left padding, nested lists, `li` spacing, blockquotes, `pre`, `code`, tables, images if already rendered, and overflow behavior.
   - Apply the class to Work Area rendered markdown containers and, where safe, existing chat/planning containers without changing their visible semantics.
   - Preserve compact thinking-block behavior if its existing alignment intentionally differs.

3. Add unsaved markdown preview route.
   - Add a non-persistent preview fragment route in `AvatarDashboardController`, likely a `POST` under `/avatar/_work-areas/{workAreaId}/viewer/markdown-preview`.
   - Accept current source content from the editor form and return sanitized rendered HTML only.
   - Keep controller thin. Prefer a fragment helper method for preview rendering and reuse existing renderer/sanitization where feasible.
   - Do not call `saveText` or otherwise persist content in the preview route.

4. Rework the Work Area text/markdown modal into a compact editor shell.
   - For markdown files, render segmented controls for `Edit`, `Preview`, and `Split`.
   - Include compact Save, Undo, Redo or Revert Unsaved Changes, and Close controls.
   - Add dirty/error/status affordances.
   - Keep the textarea always available in edit/split modes.
   - Preview and split must render current unsaved text, not only server-loaded content.
   - For plain text files, keep raw editor/save behavior with matching Avatar-style controls and local undo/revert where practical; do not show markdown preview controls.

5. Add narrow client behavior only for local state.
   - If HTMX alone becomes awkward, add one focused JS file for mode switching, dirty state, undo/redo or revert, and debounced preview sync.
   - Standard file save remains HTMX `PUT`.
   - Preview requests should use HTMX or a narrow fetch path that submits current textarea content to the preview endpoint.
   - Ensure repeated modal replacement initializes behavior without duplicate event handlers.
   - If adding JS, ensure the shell loads it once through the existing asset pattern; avoid inline scripts unless that is already the local convention for this surface.

6. Update tests.
   - `AvatarDashboardControllerTest` should cover:
     - markdown viewer/editor renders the new editor shell controls;
     - preview endpoint returns rendered sanitized HTML for unsaved content;
     - preview endpoint does not persist content;
     - save persists raw content and refreshed rendered output reflects saved markdown;
     - text editor still renders/saves without markdown preview controls;
     - script/raw HTML injection remains escaped/sanitized.
   - Add/adjust fragment tests if helper methods are exposed for testing.
   - `WorkAreaExplorerServiceTest` only changes if service guards are touched.
   - `ChatMarkdownRendererTest` only changes if renderer alignment is touched.

7. Update docs/spec/changelog.
   - Update docs and specs listed in `00-specification-lock.md`.
   - Add a changelog entry with behavior, files, validation, specification impact, risks, and follow-ups.
   - Record image preview/editing ideas only as horizon/deferred context if implementation discussion introduces durable future scope.

## Experience Contract

- Desktop:
  - Editor modal remains compact and operational, with controls in a tight header/action row.
  - `Edit` shows a readable monospaced source editor with first-pass markdown-friendly styling or clear enhancement hooks.
  - `Preview` shows sanitized rendered markdown in a bordered, scrollable, normalized container.
  - `Split` uses two usable panes without cramped controls; panes must not overlap or force horizontal page overflow.
- Mobile:
  - Split stacks vertically or changes to another usable layout.
  - Buttons wrap cleanly without overlapping text or clipping labels.
  - Rendered markdown tables and code blocks scroll inside their container.
- Visual failure examples to reject:
  - Bullet markers protrude left of the panel.
  - Tables/code blocks expand the modal beyond the viewport.
  - Preview requires saving first.
  - Dirty/undo state is ambiguous.
  - Controls feel like browser-default form clutter instead of Avatar operational UI.

## Validation Commands

Run focused tests first:

```bash
mvn -Dtest=AvatarDashboardControllerTest test
```

If service or renderer changes are made, also run:

```bash
mvn -Dtest=WorkAreaExplorerServiceTest test
mvn -Dtest=ChatMarkdownRendererTest test
```

Run broader validation before handoff when feasible:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Browser validation is required through a separate Playwright/browser validation agent after code-level validation. Do not run it inline as the implementation worker.

## Stop Conditions

- Stop and return to planning if the implementation requires replacing markdown libraries, adding CodeMirror or another editor bundle, or changing broad asset/build strategy.
- Stop and ask the main thread if preview requires a new persistence/security contract beyond a non-persistent sanitized fragment.
- Stop if existing specs conflict with the handoff objective.
- Stop if startup or browser validation is blocked by missing local services/secrets; report the blocker rather than substituting unit-only completion.
- Stop if JavaScript begins owning save/CRUD transport instead of only local editor state and preview synchronization.

## Do Not Close Unless

- Acceptance criteria from `00-specification-lock.md` are met or explicitly blocked.
- Focused tests pass.
- `mvn test` and bounded startup were run, or blockers are documented.
- Browser validation handoff artifacts exist or the blocker is documented and user-approved.
- Docs/spec/changelog updates are complete.
- No unrelated product code was changed.
- Any fallback agent/model/tool constraint is recorded as `TOOLING_CONSTRAINT`.
