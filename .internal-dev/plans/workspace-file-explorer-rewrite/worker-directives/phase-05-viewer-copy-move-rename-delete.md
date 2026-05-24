# Phase 05 Worker Directive: Viewer, Copy, Move, Rename, Delete

## Objective

Complete user-facing file operations and viewer behavior: text/Markdown/image modal viewer, Markdown/Text tabs, happy Markdown failure handling, copy/move controls, mirrored rename/delete from row and inspect panel, and consistent HTMX refreshes.

## Required Supporting Docs To Read

- `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/02-target-design.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/senior-engineer-guidance.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/validation-matrix.md`
- `docs/technical/frontend-htmx.md`
- SimplyPages `Markdown` and `Modal` source/docs.
- External references in `00-specification-lock.md`.

## Exact Editable Files/Modules/Routes

May edit:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- New viewer/operation helper classes under `src/main/java/io/mindspice/magenta2/api/web/`
- `src/main/resources/static/css/avatar-dashboard.css`
- Narrow JS under `src/main/resources/static/js/` only for modal tab switching/dirty-state behavior if HTMX is not clean enough
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkAreaControllerTest.java`
- Workspace tests only if a backend gap is discovered and can be fixed narrowly
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`

## Forbidden Scope

- No card-view resurrection.
- No broad backend redesign.
- No arbitrary filesystem browser.
- No unsupported binary rendering/editing.
- No repo-local email ledger ; use direct AgentMail daemon/wait state only.

## Experience Contract

Viewer modal:

- Eye action on Markdown opens modal with `Rendered` tab active and `Text` tab available.
- Markdown Text tab exposes raw content/edit form where safe.
- Saving Markdown returns to a coherent modal/refresh state and rendered tab reflects latest content.
- Markdown render failure shows a friendly error at the bottom of rendered area and leaves Text tab usable.
- Eye action on plain text opens modal with raw text active/default and no Markdown rendered tab.
- Eye action on image opens modal with contained image preview, metadata, and download link.
- Unsupported/binary files do not show an active eye action; if invoked through stale UI, show clear unsupported message.

Operations:

- Row actions: view/eye, rename, delete.
- Inspect actions: view if supported, rename, delete, copy, move, tag controls.
- Rename/delete behavior is equivalent from row and inspect panel.
- Copy/move forms accept destination under current root and optional new name.
- After rename/copy/move/delete/tag/save, table and inspect panel refresh consistently and modal closes or shows errors visibly.

Mobile:

- Viewer modal fits screen.
- Operation forms are usable without clipped buttons.

Proof:

- Playwright screenshots for Markdown viewer, text viewer, image viewer, inspect operation controls, and post-mutation refreshed state.

## Implementation Sequence

1. Verify Phase 04 UI structure is still intact.
2. Implement viewer modal fragments for Markdown, plain text, image, and unsupported fallback.
3. Wrap Markdown render in error handling; render bottom error on failure.
4. Implement tab behavior with HTMX or narrow JS, documenting the choice.
5. Implement inspect panel copy/move forms and mirrored rename/delete forms/actions.
6. Ensure row actions call the same route semantics as inspect actions.
7. Ensure mutation responses use OOB swaps to update table, inspect, and modal container.
8. Add tests for viewer modal defaults, Markdown failure, plain text no-render, image viewer, unsupported file, copy/move/rename/delete from row and inspect.
9. Run targeted tests and append evidence.

## Acceptance Criteria

- Viewer behavior matches Markdown/plain text/image/unsupported contracts.
- Markdown render failure is non-fatal and visible at bottom.
- Copy/move/rename/delete work from UI routes and refresh UI consistently.
- Rename/delete are mirrored in row and inspect panel.
- Copy/move are available from inspect/operation controls.
- Tests and Playwright checks cover all major flows.

## Negative Checks

- Fail if Markdown failure breaks the modal.
- Fail if plain text gets a misleading Markdown rendered tab.
- Fail if unsupported/binary file renders in text area or image tag.
- Fail if mutation refresh leaves stale inspect metadata.
- Fail if copy/move can target outside root.
- Fail if UI uses dropdown-only actions.

## Validation Commands

```bash
mvn test -Dtest=AvatarDashboardControllerTest,WorkAreaControllerTest,WorkAreaExplorerServiceTest
git status --short
```

Playwright validation subagent must execute viewer and operation flows from `shared/validation-matrix.md`.

## Stop Conditions

- Markdown safety/error behavior cannot be implemented with current renderer without broader library changes.
- Copy/move backend gap threatens root confinement.
- Playwright viewer/operation validation is blocked and user has not approved deferral.

## Senior Engineer Notes

Operation UX quality is mostly consistency. Users should not have to learn one path for row rename and a different path for inspect rename. Keep the visible flows boring and predictable, and put complexity in backend validation and OOB refresh handling.

## Do Not Close Unless

- [ ] Markdown modal defaults to rendered tab.
- [ ] Markdown Text tab works.
- [ ] Markdown render failure shows bottom error and raw text remains accessible.
- [ ] Plain text raw default has no Markdown render.
- [ ] Image viewer works.
- [ ] Unsupported/binary fallback works.
- [ ] Rename/delete work from row and inspect.
- [ ] Copy/move work from inspect/operation controls.
- [ ] OOB refresh keeps table and inspect panel consistent.
- [ ] Playwright evidence exists.
- [ ] Validation red-team has passed before Phase 06 starts.
