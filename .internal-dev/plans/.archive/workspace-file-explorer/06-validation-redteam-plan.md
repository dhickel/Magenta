# Validation And Red-Team Plan

## Validation Matrix

| Criterion | Proof |
| --- | --- |
| AC1 root confinement | path traversal, absolute path, symlink, stale path, root mutation tests |
| AC2 native explorer | Playwright desktop/mobile navigation and screenshot critique |
| AC3 actions | service/controller/browser tests for rename/delete/copy/move/tag |
| AC4 tag persistence/follow | repository/service tests for add/remove/rename/move/copy/delete |
| AC5 action logging | repository/service tests proving log rows for each mutation |
| AC6 delete confirmation | controller/browser tests for file one-step and directory two-step modals |
| AC7 text policy | BOM, UTF-8, CRLF/LF, size limit tests |
| AC8 markdown | View/Edit save/rerender tests and browser checks |
| AC9 images/binary | image endpoint tests and binary fallback browser check |
| AC10 picker | mode-specific controller/browser tests |
| AC11 upstream module | SimplyPages unit/demo/browser tests |
| AC12 integration boundary | code review: Magenta-specific logic absent upstream; UI uses module |
| AC13 docs | docs/API/internal-dev closeout review |
| AC14 Playwright | delegated screenshots plus visual critique |

## Java Test Targets

Magenta focused tests:

- `WorkAreaExplorerServiceTest`
- new `WorkspaceFileMetadataRepositoryTest`
- new `WorkspaceFileMetadataServiceTest`
- new `WorkspaceFileActionLogRepositoryTest`
- new `WorkspaceFileActionServiceTest`
- new/updated `WorkAreaControllerTest`
- updated `AvatarDashboardControllerTest`
- any markdown renderer tests if renderer changes

Commands:

```bash
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkspaceFileMetadataRepositoryTest,WorkspaceFileMetadataServiceTest,WorkspaceFileActionLogRepositoryTest,WorkspaceFileActionServiceTest,WorkAreaControllerTest,AvatarDashboardControllerTest
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If local services/secrets block startup, record the exact dependency and stop for user approval before marking validation complete.

SimplyPages focused tests:

```bash
./mvnw -pl simplypages test
./mvnw -pl demo test
```

Add narrower `-Dtest=...` runs during remediation, but final upstream PR should pass module/demo relevant tests.

## Security Red-Team Cases

Paths:

- `../secret`
- `subdir/../../secret`
- `/etc/passwd`
- `C:\Windows\system32`
- `foo\..\bar`
- encoded traversal in query params if controller decodes before service.
- empty path, `.`, root delete/rename/move/copy.
- symlink file inside root pointing outside.
- symlink directory component inside root pointing outside.
- stale stored absolute paths from previous data root.

Expected:

- Browse/edit/mutate rejects safely.
- Error response does not leak absolute paths.
- No filesystem mutation occurs outside root.

## File Operation Tests

Create:

- folder valid.
- folder collision.
- `.txt` valid.
- `.md` valid.
- invalid extension for create text/markdown.

Rename:

- file rename.
- directory rename.
- collision rejection.
- newName traversal rejection.
- tag assignments moved.

Move:

- file to directory.
- directory to directory.
- move into descendant rejected.
- collision rejected.
- active Work Area protections preserved.
- tag subtree moved.

Copy:

- file copy duplicates tags.
- directory copy duplicates subtree tags.
- symlink copy rejected.
- collision rejected.

Delete:

- file one-step modal endpoint then execution.
- directory first confirmation returns second-step modal.
- second-step recursive delete executes.
- protected Home/system Work Area root rejected.
- active Work Area descendant rejected.
- tag assignments removed.
- action log written.

## Text/Markdown/Encoding Tests

Text:

- UTF-8 read/save.
- UTF-8 BOM stripped.
- existing LF preserved.
- existing CRLF preserved.
- mixed line endings choose existing dominant policy or documented policy.
- invalid UTF-8 rejected for edit.
- extensionless safe text behavior if retained.

Markdown:

- <=5 MB renders/edits normally.
- >5 MB to <=25 MB returns warning/open-anyway state.
- >25 MB read-only.
- View/Edit tab returns rendered content after save.
- unsafe raw HTML/script is escaped/sanitized.

Binary:

- unsupported binary shows metadata only.
- save route rejects binary.

Image:

- safe image path streams only inside root.
- unsupported image/svg policy follows target design.
- binary disguised with image extension does not crash.

## Picker Tests

Mode matrix:

- `OPEN_FILE`: directories navigate; files selectable; current directory not selectable as file.
- `OPEN_DIRECTORY`: current directory and directories selectable; files not selectable.
- `SAVE_FILE`: filename input; create folder; create `.txt`/`.md`; collision confirmation if implemented.
- `SAVE_DIRECTORY`: current directory selectable; create folder; folder rename.

Path results:

- Submitted values are root-relative paths.
- Display never exposes host absolute path.
- Picker callback updates caller hidden input/visible label.

## Playwright Validation

Must be run by `validation_redteam_agent`.

Magenta setup:

- Start app with isolated test data root if feasible.
- Seed or create an agent/project Work Area with:
  - nested directories,
  - `.txt`,
  - `.md`,
  - image,
  - unsupported binary,
  - large file boundary fixture if practical.

Routes:

- `/avatar?tab=work-areas`
- Work Areas widget from dashboard tab if still present.
- One picker integration route.

Desktop viewport:

- 1440x900 or similar.

Mobile viewport:

- 390x844 or similar.

Interactions:

- Open explorer.
- Navigate into directory by click.
- Up/back/forward/refresh.
- Breadcrumb segment click.
- Switch card/list mode if implemented.
- Select file and inspect tags.
- Add/remove `note`.
- Mark directory as Work Area.
- Rename file.
- Copy file.
- Move file.
- File delete confirmation.
- Directory two-step delete confirmation.
- Open text editor and save.
- Open markdown View/Edit, save, return View.
- Open image viewer.
- Open binary fallback.
- Use picker to select a path.

Visual critique must cover:

- alignment,
- spacing,
- gutters,
- density,
- scan hierarchy,
- first viewport usefulness,
- toolbar wrapping,
- button/icon affordance,
- text wrapping,
- overflow/clipping,
- mobile stacking,
- modal size and focus,
- inspector usefulness,
- whether standard CRUD stayed HTMX-first,
- whether JavaScript is narrowly justified.

Failure examples:

- stranded empty columns,
- giant modal replacing the actual explorer experience,
- controls clipped or overlapping,
- path/breadcrumb wrapping over actions,
- low-density cards hiding useful content below fold,
- mobile horizontal overflow,
- delete confirmation ambiguity,
- binary rendered as text.

## Upstream Browser Validation

Run SimplyPages demo app:

```bash
./mvnw -pl demo spring-boot:run
```

Validate:

- demo file explorer route,
- demo picker route,
- desktop/mobile screenshots,
- no dependency on Magenta routes,
- module renders nonblank and stable.

## Final Validation Report Shape

The validation worker must report:

- command results,
- pass/fail by AC and VC,
- screenshot artifact paths,
- browser console/network issues,
- exact file/line for suspected defects,
- remediation priority,
- residual risks.

