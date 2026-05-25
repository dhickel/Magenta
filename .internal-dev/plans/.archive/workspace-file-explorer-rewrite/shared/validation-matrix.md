# Validation Matrix

Status: required validation map

## Acceptance Mapping

| criteria | validation evidence |
| --- | --- |
| AC1 root confinement | `WorkAreaExplorerServiceTest` path traversal, absolute path, Windows drive, symlink path/tree, stale path, destination escape tests; controller negative tests; red-team manual review. |
| AC2 details/list layout | Playwright desktop/mobile screenshots; component/controller tests assert table headers and absence of card classes. |
| AC3 required columns | Controller/component tests assert Name, File Type, Size, Created, Last Modified, Tags, Actions; Playwright visual check. |
| AC4 inspect panel | Fragment tests for row selection; Playwright selection updates right panel; screenshot critique. |
| AC5 row actions | Component tests assert view/rename/delete row controls and no view on unsupported binaries; Playwright checks. |
| AC6 mirrored rename/delete | Inspect panel tests and Playwright checks for rename/delete from both row and panel. |
| AC7 copy/move | Service tests for file/dir copy/move, collisions, self-descendant rejection, symlinks, tag copy/move; Playwright operation forms. |
| AC8 viewer modal | Controller/component tests and Playwright for text/image modal open, close, tabs. |
| AC9 Markdown behavior | Unit/fragment tests for default rendered tab, raw Text tab, safe render, render failure bottom error; Playwright check. |
| AC10 plain text behavior | Tests assert raw-only default and no Markdown render affordance; Playwright text viewer check. |
| AC11 file/dir tags | Repository/service tests and UI flow for custom tags on files and directories. |
| AC12 tag row/panel display | Component tests for first few tags and inspect full tags; Playwright screenshot critique. |
| AC13 Magenta style | Playwright visual critique compares operational density/styling against Avatar guidelines. |
| AC14 browser validation | Delegated Playwright report with screenshots, console/network notes, desktop/mobile coverage. |
| AC15 backend validation | Targeted tests, full `mvn test`, bounded Spring startup, red-team negative matrix. |
| AC16 docs/closeout | Docs diff review, `.internal-dev` changelog/knowledge/focus review, final quality review. |

## Required Commands

Targeted backend during domain phases:

```bash
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkspaceFileMetadataRepositoryTest,WorkspaceFileMetadataServiceTest,WorkspaceFileActionLogRepositoryTest
```

API/fragment phases:

```bash
mvn test -Dtest=WorkAreaControllerTest,AvatarDashboardControllerTest
```

Full repository validation before final review:

```bash
mvn test
```

Bounded startup:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Git hygiene:

```bash
git status --short --branch
git diff -- .
```

## Playwright Validation Requirements

Run through a validation subagent, not inline in the implementation worker.

Minimum scenarios:

- Desktop `/avatar?tab=work-areas` with seeded Work Area containing folders, Markdown, plain text, image, binary/unsupported, and tagged file/directory.
- Mobile viewport of the same route.
- Navigate into folder, Up/back/breadcrumb.
- Select file and directory rows; inspect panel updates.
- Verify columns: Name, File Type, Size, Created, Last Modified, Tags, Actions.
- Verify no cards: no file/directory card grid; row/table layout remains.
- Open Markdown with eye; default rendered tab; switch to Text; save; return rendered.
- Force/fixture Markdown render failure; verify bottom error and raw text access.
- Open plain text; verify raw default and no Markdown tab.
- Open supported image; verify modal image render.
- Attempt unsupported/binary view; verify safe fallback/no misleading viewer.
- Rename/delete from row and inspect panel.
- Copy/move from inspect/operation controls.
- Add/remove custom tag to file and directory.
- Confirm row first-few tags and inspect full tags.
- Capture screenshots and include visual critique: alignment, density, spacing, overflow, text wrapping, mobile stacking, action affordances, first-viewport usefulness.

## Red-Team Negative Checks

- `../`, `%2e%2e` if routed, mixed `\` separators, `/absolute`, `C:\Windows\...`.
- Symlink as path component and symlink inside copied/deleted tree.
- Move/copy directory into itself or descendant.
- Rename/copy/move to existing target.
- Delete Work Area root, home/system Work Area, and active Work Area descendant.
- Binary file sent to text save.
- Invalid UTF-8 text file preview/save.
- Oversized text/Markdown.
- External filesystem deletion causing stale metadata.
- Custom tag slug injection or invalid slug.
- HTML/script in Markdown.
- Browser console/network errors during HTMX swaps.

## Gate Email Requirements

Every phase completion email must include:

- gate name;
- files changed;
- implementation summary;
- validation commands and results;
- browser evidence if applicable;
- residual risks;
- blockers and user decisions if any;
- next planned phase.

Final email additionally includes the closeout report plan from `closeout-report-plan.md`.
