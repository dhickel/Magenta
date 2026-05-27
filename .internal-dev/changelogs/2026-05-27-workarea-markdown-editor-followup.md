# Date
2026-05-27

# Change Summary
- Reworked the Avatar Work Area markdown/text modal into a compact editor shell.
- Added markdown `Edit`, `Preview`, and `Split` modes that use current unsaved textarea content for preview rendering.
- Kept save explicit via existing HTMX `PUT /avatar/_work-areas/{workAreaId}/text`.
- Added browser-local undo/redo/revert controls and dirty status messaging.
- Follow-up repair: undo now flushes any pending debounced snapshot before applying history, and programmatic snapshot application clears stale pending history timers so redo reliably restores the undone unsaved edit.
- Normalized rendered-markdown spacing/layout with a shared scoped `.magenta-rendered-markdown` class and applied it to Work Area rendered markdown containers.
- Added a non-persistent sanitized preview route for markdown editor sync.
- Updated tests and docs/specs to replace legacy Rendered/Text-tab wording.
- TOOLING_CONSTRAINT: plan requested `implementation_worker_agent`; execution used fallback `worker` with `gpt-5.3-codex` in this environment.

# Files
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/js/avatar-workarea-editor.js`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/api.md`
- `docs/end-user/avatar-dashboard.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/avatar-dashboard-fragments.md`

# Behavioral Impact
- Markdown and text files now open in one compact editor flow with explicit save and local editing controls.
- Markdown preview/split no longer requires save-to-switch behavior.
- Rendered markdown lists/quotes/code/tables are container-normalized via a shared scoped class.
- Save persistence behavior is unchanged and still service-owned through Work Area explorer service policies.

# Specification Impact
- Updated `.internal-dev/specifications/web.md`, `.internal-dev/specifications/simplypages.md`, and `.internal-dev/specifications/api.md` to reflect editor mode behavior, HTMX save boundary, narrow JS responsibilities, and the new non-persistent preview route.

# Risks
- Editor local history is client-side snapshot-based, so undo granularity is timer-based rather than per-keystroke native history.
- No dedicated JavaScript unit-test harness currently exists in this repo for static editor modules; redo behavior remains covered by browser validation rather than JS unit tests.
- Browser validation is still required to verify split-mode ergonomics and compact control wrapping across desktop/mobile.

# Follow-up Items
- Dispatch focused Playwright validation (desktop and mobile) for markdown viewer/editor modes, save persistence, local undo/redo/revert affordances, and rendered markdown layout quality.
- Use stable root fixtures for reruns (`demo-fixtures/briefing.md` plus `demo-fixtures/plain-text-fixture.txt`) and fail validation if `pw-*` files are generated.
