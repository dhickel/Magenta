# Domain Work-Unit Plans

Each work unit maps to acceptance criteria from `00-specification-lock.md`. Downstream implementation agents must read that file first.

## WU-01: Baseline Branch, Inventory, And Migration Guard

Criteria: AC13

Ownership:

- Main orchestrator or implementation worker before code edits.

Dependencies:

- None.

Exact targets:

- Create dedicated branch, suggested: `feature/workspace-file-explorer`.
- Verify Magenta dirty state and preserve `.internal-dev/inbox/queue.md`, `.internal-dev/inbox/read.md`.
- Re-read relevant `AGENTS.md` files before code edits.
- Record any newly discovered blockers in the plan progress artifact if the orchestrator creates one.

Expected behavior:

- Implementation starts from a known branch and does not disturb unrelated user files.

Validation:

- `git status --short` before edits.
- Branch name reported.

Stop conditions:

- Stop if unrelated dirty files overlap intended edit targets.
- Stop if user-modified inbox files are needed for implementation.

Senior Engineer Notes:

This repo has strict `.internal-dev` and phase-commit expectations. Do not let workers casually edit focus/inbox/changelog files mid-implementation. Planning artifacts are the only files created in this request; implementation closeout is separate and must follow `07-closeout-plan.md`.

## WU-02: Workspace File Domain Model, Path Policy, And Service Core

Criteria: AC1, AC3, AC6, AC7, AC9, AC10

Ownership:

- Workspace service worker.

Dependencies:

- WU-01.

Exact edit targets:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java`
- New records/classes under `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java`
- New service tests under same test package.

Implementation steps:

1. Introduce explicit root/path records for explorer requests.
2. Extract path normalization and symlink rejection into reusable methods or collaborator.
3. Implement list, preview metadata, text read policy, image metadata endpoint support, create folder, create `.txt`, create `.md`, rename, move, copy, delete preflight, and delete execute.
4. Replace typed delete confirmation in service API with operation-step validation suitable for modal flow.
5. Keep compatibility wrappers for existing API methods until controllers are migrated.

Expected behavior:

- All browse/mutate operations stay inside resolved root.
- Mutations return enough data to refresh listing and inspector.
- Copy/move/rename reject collisions and unsafe paths.

Validation:

- Path traversal tests.
- Symlink component tests.
- Root mutation rejection tests.
- Directory-into-descendant move rejection.
- Copy/move collision tests.
- Delete preflight/delete execute tests.

Stop conditions:

- Any path escape succeeds.
- Any root delete/rename/move succeeds.
- Recursive delete can traverse or delete symlink targets.

Senior Engineer Notes:

The existing service already has useful confinement behavior at `resolveNormalized`, `resolveExisting`, and `rejectSymbolicPath`. Preserve that rigor while broadening the use cases. Avoid a superficial UI-only fix; file actions must be service-owned because tags and action logs need one authoritative mutation path.

## WU-03: DB-Backed File Labels/Tags

Criteria: AC3, AC4

Ownership:

- Workspace persistence worker.

Dependencies:

- WU-02 path identity records.

Exact edit targets:

- `src/main/resources/schema.sql`
- New `WorkspaceFileMetadataRepository.java`
- New `WorkspaceFileMetadataService.java`
- Workspace package tests.
- Potential package guide update if responsibility materially changes.

Implementation steps:

1. Add `workspace_file_labels` and `workspace_file_label_assignments`.
2. Add repository self-creation/migration consistent with existing repository style.
3. Seed or ensure system labels `note` and `work-area`.
4. Implement add/remove/list labels for a workspace-relative path.
5. Implement metadata follow helpers: `onRename`, `onMove`, `onCopy`, `onDelete`.
6. Call helpers from WU-02 mutation service paths.

Expected behavior:

- Labels persist and are queryable by file path.
- Rename/move updates label assignments.
- Copy duplicates label assignments.
- Delete removes label assignments for deleted path subtree.

Validation:

- Repository migration/idempotency tests.
- Add/remove/list tests.
- Rename/move/copy/delete tag-follow tests.
- External missing file returns metadata orphan state without crashing.

Stop conditions:

- Tag follow behavior is missing for any Magenta-managed mutation.
- Tags are stored in `avatar.sqlite` or Work Area metadata JSON instead of runtime DB tables.

Senior Engineer Notes:

Do not overload `work_areas` as generic file tags. Work Areas remain execution-routing metadata around directories; labels are generic metadata around files/directories. Keeping this separation prevents later custom tags from corrupting runtime assignment semantics.

## WU-04: Durable File Action Logging

Criteria: AC5, AC13

Ownership:

- Workspace persistence/runtime worker.

Dependencies:

- WU-02 action service shape.

Exact edit targets:

- `src/main/resources/schema.sql`
- New `WorkspaceFileActionLogRepository.java`
- New `WorkspaceFileActionLogService.java` or methods inside action service if small.
- Docs/API updates in later WU.
- Tests under workspace package.

Implementation steps:

1. Decide final log sink after inspecting `orchestration_events` semantics in implementation context.
2. Prefer `workspace_file_actions` if `orchestration_events` reaction semantics are not appropriate.
3. Log successful mutations and meaningful failed preflight attempts.
4. Include actor/source where available from controller context; otherwise use `actor_type=system` or `web`.
5. Expose recent actions query for future Avatar visibility; v1 UI display can be deferred.

Expected behavior:

- Every mutating/destructive action generates durable action evidence without file contents or absolute host paths.

Validation:

- Action-log repository tests.
- Service tests verifying create, rename, copy, move, delete, tag add/remove, and save produce log rows.

Stop conditions:

- Destructive actions can complete without durable log entry.
- Log records file contents or host absolute paths.

Senior Engineer Notes:

Chat `audit_event` is not a natural fit because it is conversation-sequenced. `orchestration_events` may trigger reactions. A purpose-built action log is likely cleaner and easier to present later in Avatar without unintended automation.

## WU-05: Text, Markdown, Image, And Binary Viewer Policies

Criteria: AC7, AC8, AC9

Ownership:

- Workspace service + web fragment worker.

Dependencies:

- WU-02, WU-03 for metadata display.

Exact edit targets:

- Workspace service text policy classes.
- `WorkAreaController.java`
- `AvatarDashboardController.java`
- rendering components/helper classes in `api/web`.
- `src/test/java/io/mindspice/magenta2/ai/chat/rendering/ChatMarkdownRendererTest.java` only if using existing renderer.
- New tests under workspace/api web packages.

Implementation steps:

1. Implement UTF-8 detection and BOM stripping.
2. Preserve LF/CRLF line endings on save.
3. Add size policy response states.
4. Add markdown View/Edit fragment flow.
5. Add image viewer endpoint and safe content type policy.
6. Add unsupported/binary metadata fallback.

Expected behavior:

- Text/markdown/image/binary files open through correct viewer mode.
- Save respects compatibility rules and action logging.

Validation:

- UTF-8 BOM tests.
- CRLF preservation tests.
- Invalid UTF-8 refusal tests.
- Size boundary tests: <=5 MB markdown, <=10 MB text, warning to 25 MB, >25 MB read-only.
- Markdown rerender test.
- Image content type/path confinement test.

Stop conditions:

- Unknown encoding is silently rewritten.
- Markdown renders unsafe raw HTML/script.
- Large file policy can be bypassed.

Senior Engineer Notes:

The current `Files.readString(... UTF_8)` path is too blunt for editor semantics. Treat text compatibility as a first-class service concern. Do not mix UI confirmation logic with encoding policy; controllers should only present the service state.

## WU-06: Magenta HTMX Explorer UI Integration

Criteria: AC2, AC3, AC6, AC8, AC9, AC12, AC14

Ownership:

- Magenta frontend worker.

Dependencies:

- WU-02 through WU-05.
- WU-09 upstream module may be implemented first or in parallel as non-mutating design until API is stable.

Exact edit targets:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- possible shared CSS in `magenta.css` or `orchestration.css`
- narrow JS file if needed under `src/main/resources/static/js/`
- `AvatarDashboardControllerTest.java`
- Web/controller tests for fragments.

Implementation steps:

1. Replace modal list browser with explorer shell using reusable module/view model.
2. Add toolbar, breadcrumb, card/list modes, inspector, action modals, and viewer/editor fragments.
3. Implement HTMX navigation and mutation refreshes.
4. Add narrowly scoped JS only for local history/dirty-state/tab behavior if HTMX cannot cleanly handle it.
5. Keep Avatar tab/widget style aligned with operational console.

Expected behavior:

- Work Areas tab/widget opens a native-feeling explorer.
- Users can navigate, inspect, edit, tag, and mutate within root.
- UI does not become low-density or modal-only.

Validation:

- Controller tests for stable fragment ids and routes.
- Playwright validation in WU-12.

Stop conditions:

- Standard CRUD is implemented as a JS transport surface.
- UI regresses to large modal-only layout.
- Controls overlap, clip, or become unusable on mobile.

Senior Engineer Notes:

Current Avatar code has custom methods for every browser fragment. The desired end state is not just prettier markup; it is reducing Avatar-specific file-browser ownership. Use SimplyPages-native composition and leave Magenta with route/view-model wiring.

## WU-07: Picker Dialog Integration

Criteria: AC10, AC12, AC14

Ownership:

- Magenta frontend/API worker.

Dependencies:

- WU-02, WU-06, WU-09.

Exact edit targets:

- New picker fragment/controller methods in `api/web`.
- Existing flow selected for first integration, likely output direct directory selection or Work Area output routing in operational submit forms.
- Tests in web package.
- Docs updates later.

Implementation steps:

1. Add generic picker configuration records for mode, root context, selected path callback, allowed extensions, and creation permissions.
2. Integrate one concrete Magenta flow first.
3. Support hidden-input callback or HTMX callback endpoint to write selected relative path into caller form.
4. Support create folder, folder rename, and `.txt`/`.md` creation where mode allows.

Expected behavior:

- App flows can use a desktop-style picker instead of raw path text fields.

Validation:

- Picker mode tests for open-file, open-directory, save-file, save-directory.
- Browser test for selecting a directory/file and reflecting it in caller form.

Stop conditions:

- Picker can choose paths outside root.
- Picker conflates display absolute path with submitted relative path.

Senior Engineer Notes:

Do not attempt to replace every selector in one sweep. Prove the reusable picker contract on one real Magenta flow, then leave broader rollout as follow-up unless user expands scope.

## WU-08: API And Route Compatibility

Criteria: AC1, AC3, AC6, AC13

Ownership:

- API/controller worker.

Dependencies:

- WU-02 through WU-07.

Exact edit targets:

- `WorkAreaController.java`
- API tests under `src/test/java/io/mindspice/magenta2/api/web`
- `docs/api/00-index.md`
- `docs/technical/api-reference.md` if detailed routes live there.

Implementation steps:

1. Define stable API payloads for listings, entries, previews, actions, tags, warnings, and errors.
2. Keep compatibility methods where practical.
3. Map validation failures to `400`, missing files to `404`, conflict/collision to `409`, and oversized/encoding states to explicit payloads.
4. Ensure HTMX fragment routes and JSON/API routes do not leak implementation internals.

Expected behavior:

- Existing clients are not broken unnecessarily.
- New UI has complete route coverage.

Validation:

- Controller tests for all major routes and error cases.

Stop conditions:

- Controller starts owning filesystem logic.
- Error shapes hide root-cause information needed for the UI.

Senior Engineer Notes:

The current controller is thin and should stay that way. The risk here is slowly moving file-operation branching into controller methods because HTMX needs fragment-specific responses. Keep service results structured and have renderers convert them to fragments.

## WU-09: SimplyPages Upstream Reusable Module

Criteria: AC11, AC12, AC14

Ownership:

- Upstream SimplyPages worker.

Dependencies:

- WU-02 target view-model shape can be developed in parallel, but final integration requires contract alignment.

Exact targets:

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework`
- New `FileExplorerModule`, `FilePickerModule`, records/configs, or component package.
- `simplypages/src/main/resources/static/css/framework.css`
- optional narrow static JS.
- demo controller/page/tests/docs.

Implementation steps:

1. Isolate dirty upstream checkout safely.
2. Add generic view model and module renderers.
3. Add breadcrumb, toolbar, card/list entries, inspector slot, viewer slot, modal confirmation components, picker modes.
4. Add demo route and docs.
5. Open draft PR.

Expected behavior:

- Magenta can consume the module by supplying endpoint URLs and view models.
- No Magenta-specific code enters SimplyPages.

Validation:

- Upstream unit tests.
- Demo integration tests.
- Demo browser validation.

Stop conditions:

- Upstream work would overwrite dirty user changes.
- Module requires app-specific filesystem services.

Senior Engineer Notes:

Prior upstream module work was handled as a real branch/PR. Repeat that discipline. The module should be useful for any server-rendered app with its own backend file provider, not just this workspace browser.

## WU-10: Documentation And Internal Guidance

Criteria: AC13

Ownership:

- Documentation worker.

Dependencies:

- WU-02 through WU-09 behavior stabilized.

Exact edit targets:

- `docs/api/00-index.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/end-user/avatar-dashboard.md`
- `docs/end-user/projects-and-workspaces.md` if picker/output path behavior changes.
- `docs/technical/frontend-htmx.md` if new JS island or reusable module policy is added.
- Package `AGENTS.md` files if responsibilities changed.

Implementation steps:

1. Update route/API summaries.
2. Document user behavior and size/encoding limits.
3. Document metadata orphan caveat for external filesystem changes.
4. Document SimplyPages module dependency and JavaScript justification.
5. Update docs index links if new docs are created.

Expected behavior:

- Docs match implemented behavior.

Validation:

- Link/path checks by inspection.
- Documentation references to source anchors remain accurate.

Stop conditions:

- Docs claim behavior not implemented.
- Future reconciliation/chunked editing is documented as present behavior.

Senior Engineer Notes:

Docs should be practical and operator-facing. Do not bury size/encoding/destructive delete behavior only in code comments; users need to know what happens when large or non-text files are opened.

## WU-11: Magenta Automated Validation

Criteria: AC1 through AC14

Ownership:

- Implementation worker for unit/controller tests; validation subagent for independent test run.

Dependencies:

- WU-02 through WU-10.

Validation:

- Focused service/repository/controller tests.
- Relevant package tests.
- Full `mvn test` unless time/user constraints narrow it.
- Bounded Spring startup.

Stop conditions:

- Any AC has no automated proof where automated proof is practical.
- Spring context cannot start and user has not approved deferral.

Senior Engineer Notes:

Do not substitute unit-only proof for filesystem-backed execution validation. File explorer work is only meaningful if real filesystem operations under isolated temp roots pass.

## WU-12: Delegated Playwright Visual/Interaction Validation

Criteria: AC2, AC6, AC8, AC9, AC10, AC14

Ownership:

- `validation_redteam_agent` using model `gpt-5.3-codex`, reasoning effort `medium`.

Dependencies:

- App running from completed implementation.

Validation scope:

- `/avatar` Work Areas tab and widget.
- Explorer desktop and mobile.
- Navigation toolbar, breadcrumb, Back/Forward/Up/Refresh.
- File/folder cards and list mode if implemented.
- Inspector tags/labels.
- Delete confirmation modals for file and directory two-step.
- Text editor, Markdown View/Edit, image viewer, binary fallback.
- One picker integration flow.

Stop conditions:

- No screenshots.
- No visual critique.
- UI controls overlap, clip, or become unusable.
- JavaScript use is not explicitly justified.

Senior Engineer Notes:

Playwright here is not just route smoke testing. The validator must inspect spacing, density, scan hierarchy, mobile stacking, first-viewport usefulness, overflow, and whether the explorer feels like an operational file tool rather than a form-heavy modal.

