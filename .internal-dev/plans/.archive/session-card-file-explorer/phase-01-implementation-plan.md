# Session Card File Explorer Implementation Plan

## Context

Magenta ordinary chat turns already install a chat-scoped file context in `ChatService.installChatFileContextIfNeeded(...)`. File tools therefore write persistent chat files under the data-root path exposed by `WorkspaceDirectoryService.chatFiles(conversationId)`, currently documented as `chats/<conversationId>/files/`. These files are not `run_output_artifacts`; output artifacts are run-based materialized outputs owned by `OutputArtifactService`, while ordinary chat files are conversation-local filesystem content.

The user wants chat session cards to expose file availability with a separate row reading `Outputs: <n>` only when a session has files. The active chat page should also gain a right-side file list for the selected session, showing each file clearly with its format and a download button. Inline viewers are explicitly out of scope for now.

Primary files likely affected:

- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSession.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- New chat-file service/model files under `src/main/java/io/mindspice/magenta2/ai/chat/...`
- New controller or endpoints under `src/main/java/io/mindspice/magenta2/api/web`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/css/magenta.css`
- Tests under `src/test/java/io/mindspice/magenta2/api/web`, `src/test/java/io/mindspice/magenta2/ai/chat`, and possibly `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces`
- Docs in `docs/end-user/chat.md`, `docs/technical/api-reference.md`, `docs/api/00-index.md`, and `docs/technical/workspaces-tools-outputs.md`

Relevant existing behavior:

- `GET /api/chat/sessions` returns `ChatSessions`, whose `sessions` are rendered by `chat-client.js`.
- `chat-client.js` owns the browser-side session cards and active session switching.
- The server-side fragment route `GET /api/fragments/chat/sessions` also renders session cards, though the primary `/chat` page currently uses the JSON endpoint and JS renderer.
- Existing global output download routes are `/api/outputs/{artifactId}/download`, but those are artifact-id based and do not cover ordinary chat files.
- `OutputController` and `OutputArtifactService.loadContent(...)` already demonstrate data-root confinement and download response patterns.

## Goal

Implement a session-scoped file explorer for ordinary chat files. Sessions with files show a second line on the session card: `Outputs: <n>`. The active chat page shows a right-hand outputs panel listing the selected session's files with readable names, relative paths, format labels, file sizes, modified timestamps, and download buttons.

The result should be the smallest complete feature that makes files discoverable from the chat UI without introducing file preview/rendering machinery. It should use the existing chat file directory as the source of truth and should preserve the current chat send, stream, planning, session rename, favorite, archive, delete, and bulk-action behaviors.

## In Scope

- Count regular files under each visible non-archived ordinary chat session's persistent chat file directory.
- Add the count to the `ChatSession` JSON contract.
- Render `Outputs: <n>` as its own session-card row only when `n > 0`.
- Add a right-side chat outputs panel to `/chat`.
- For the active session, list regular files from `chats/<conversationId>/files/`.
- Show simple metadata per file: display name, relative path when useful, extension/format, byte size, and last modified time.
- Add download links/buttons for each listed file.
- Refresh the outputs panel after session switch, session list reload, chat completion, and plan execution completion.
- Add focused unit/controller tests and UI contract tests.
- Update user, technical, and API docs for the implemented routes and visible behavior.
- Validate with Playwright MCP in a running browser and visually inspect desktop and mobile layouts.

## Out of Scope

- Inline file viewers, markdown preview, image preview, PDF preview, syntax highlighting, diffing, or editing.
- Security hardening beyond existing reasonable path confinement, UUID validation, and response bounds.
- Moving chat files into `run_output_artifacts`.
- Database schema changes for chat file metadata.
- Indexing file contents or search inside chat files.
- Bulk download or zip creation.
- Cross-session combined output views on the chat page.
- Changing global `/outputs` behavior.

## Inputs And Assumptions

Confirmed:

- Chat files are persistent under `WorkspaceDirectoryService.chatFiles(conversationId)`.
- `ChatService` installs chat file context with `workspaceId = conversationId` and `runType = CHAT`, and both host workspace and output paths point at the chat files directory.
- Session cards on `/chat` are primarily client-rendered by `chat-client.js` from `/api/chat/sessions`.
- Existing global output APIs do not list ordinary chat files.

Assumptions to verify before coding:

- The intended count should include all regular files recursively under `chats/<conversationId>/files/`, including nested directories created by file tools.
- Empty directories should not count as outputs.
- Hidden files should be shown unless they are not regular files. If the user later wants dotfiles hidden, record that as a deferred idea.
- File format can be derived from extension for now, with extensionless files labeled `file`.
- Downloading files up to the existing 10 MB controller limit is acceptable for the first pass. If larger chat files must download, confirm before changing the limit.

## Target Design

### Backend Components

Add a small chat-file read service, for example `ChatFileService` under `io.mindspice.magenta2.ai.chat.service` or `io.mindspice.magenta2.ai.chat.files`.

Responsibilities:

- Resolve a conversation's chat files directory through `WorkspaceDirectoryService.chatFiles(conversationId)`.
- Count regular files recursively, returning `0` when the directory does not exist or contains no regular files.
- List regular files recursively in stable sorted order by relative path.
- Return lightweight descriptors with no file content:
  - `relativePath`
  - `fileName`
  - `extension`
  - `formatLabel`
  - `sizeBytes`
  - `lastModified`
- Resolve a requested relative path for download by normalizing under the chat files directory and rejecting traversal or directories.

Suggested records:

```java
public record ChatFileSummary(
    String relativePath,
    String fileName,
    String extension,
    String formatLabel,
    long sizeBytes,
    Instant lastModified
) {}

public record ChatFileListing(
    String conversationId,
    int count,
    List<ChatFileSummary> files
) {}
```

Extend `ChatSession` with an `int outputCount` or `int fileCount`. Use `outputCount` because the visible UI language is `Outputs: <n>`.

Compatibility rule:

- Existing constructor overloads must remain, defaulting `outputCount` to `0`, so current tests and older construction call sites stay simple.

### API Contract

Add chat-file routes. Prefer a focused controller, for example `ChatFileController`, instead of growing `ChatController`.

Routes:

- `GET /api/chat/{conversationId}/files`
  - Returns `ChatFileListing`.
  - Requires a valid existing conversation.
  - Returns count `0` and `files: []` when the directory is empty.

- `GET /api/chat/{conversationId}/files/download?path=<relativePath>`
  - Downloads one regular file from that conversation's chat files directory.
  - Uses `Content-Disposition: attachment`.
  - Uses a simple media type resolver comparable to `OutputController.resolveMediaType(...)`.
  - Applies the same 10 MB max download limit unless explicitly changed later.

Do not add file content/read APIs in this phase. The right panel is a list, not a viewer.

### Frontend Design

Update `/chat` from a two-column layout to a three-column desktop layout:

- Left: session sidebar.
- Center: chat transcript/composer.
- Right: session outputs panel.

The right panel should be operational and compact, not a marketing-style card. Use a bordered utility panel matching the existing chat toolbar/session styling.

Add to `FrontendController.chat(...)`:

- A new aside or div with `id="chat-session-files"` and class such as `chat-files-panel`.
- Header text: `Outputs`.
- Body target with `id="chat-session-files-body"`.

The panel states:

- No active session: `Select a chat to view outputs.`
- Active session with no files: `No outputs for this chat.`
- Active session with files: list rows with format badge, filename, metadata, and download button.
- Error loading files: small status text in the panel, while preserving the rest of chat.

Session cards:

- `renderSessions(...)` uses `session.outputCount`.
- If `outputCount > 0`, render a separate row under the title: `Outputs: <n>`.
- Do not render the row at all for zero, null, or missing counts.

Download controls:

- Use normal anchors with `href="/api/chat/{id}/files/download?path=..."`.
- Add `download` where practical.
- Label as `Download`.

JavaScript:

- Add `loadActiveFiles()` and `renderSessionFiles(listing)` to `chat-client.js`.
- Call `loadActiveFiles()` after:
  - Initial page load after active conversation is established.
  - `loadHistory(conversationId)`.
  - Session switch click handling.
  - `sendMessage(...)` completion after `loadSessions()`.
  - `executeApprovedPlan(...)` completion after `loadSessions()`.
  - Deleting/archiving the active session should clear the panel.
- Keep JS narrowly scoped because the existing chat page already uses JS for session rendering and SSE. Do not introduce a new frontend framework.

HTMX note:

- This feature can use the existing JS session renderer because session cards are already client-rendered. A pure HTMX rework of sessions is not required for this scope. If an implementer chooses to add a fragment for the right panel, keep it additive and do not replace the current session/SSE client.

### Styling

Add CSS in `magenta.css`:

- `grid-template-columns: auto minmax(0, 1fr) minmax(17rem, 22rem)` for desktop `.chat-layout`.
- `.chat-files-panel`, `.chat-files-header`, `.chat-files-body`, `.chat-file-list`, `.chat-file-item`, `.chat-file-format`, `.chat-file-meta`, `.chat-file-actions`, `.chat-session-output-row`.
- Mobile under `900px`: stack the right panel below chat or below sessions using `grid-template-columns: 1fr`; keep the file list readable and avoid horizontal overflow.

Avoid nested cards. Use compact rows with clear borders and stable spacing.

## Implementation Steps

### 1. Create Branch And Shared Notes

- Create a dedicated implementation branch before coding, for example:

```bash
git checkout -b session-card-file-explorer
```

- Create orchestration notes before launching agents:

```text
.codex-orchestration/session-card-file-explorer/notes.md
```

Include sections:

- `Global Assumptions`
- `Active Agents`
- `Completed Work`
- `Validation Results`
- `Remediation Notes`
- `Blockers`
- `Final Validation Status`
- `Handoff Notes`

### 2. Backend File Listing Service

Edit/add:

- `src/main/java/io/mindspice/magenta2/ai/chat/.../ChatFileService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatFileSummary.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatFileListing.java`

Behavior:

- Use `WorkspaceDirectoryService.chatFiles(conversationId)`.
- Walk recursively with `Files.walk(root)`.
- Filter `Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)`.
- Sort by normalized relative path string.
- Bound the list to a reasonable max, such as 500 files, to protect the UI. Return a `truncated` flag only if the UI will display it; otherwise keep the first pass at an expected small number and document the cap. Prefer adding `boolean truncated` if implementing the cap.
- Format labels:
  - `.md` -> `Markdown`
  - `.txt` -> `Text`
  - `.json` -> `JSON`
  - `.csv` -> `CSV`
  - `.html` -> `HTML`
  - `.xml` -> `XML`
  - `.png/.jpg/.jpeg/.gif/.webp` -> `Image`
  - `.pdf` -> `PDF`
  - otherwise uppercase extension or `file`

Download resolution pseudocode:

```java
Path root = workspaceDirectoryService.chatFiles(conversationId).toRealPath();
Path target = root.resolve(relativePath.replace('\\', '/')).normalize();
if (!target.startsWith(root)) throw new IllegalArgumentException("file path escapes chat files");
Path real = target.toRealPath();
if (!real.startsWith(root) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) throw ...
```

Use the real path for `Files.size`, media type, and `InputStreamResource`.

### 3. Session Count Contract

Edit:

- `ChatSession.java`
- `ChatService.java`

Add `outputCount` to `ChatSession`.

In `ChatService.session(String conversationId)`, inject/use `ChatFileService` if available and compute:

```java
int outputCount = chatFileService == null ? 0 : chatFileService.countFiles(conversationId);
```

Keep constructor overloads defaulting to `0`.

Gotcha:

- `listSessions()` maps each session to `session(conversationId)`, so counting files there can become N filesystem walks. Keep count implementation cheap and bounded. If this becomes slow later, add caching or a filesystem metadata table as a future task; do not add that now.

### 4. API Download And Listing Routes

Add:

- `src/main/java/io/mindspice/magenta2/api/web/ChatFileController.java`

Suggested dependencies:

- `ChatService` for `conversationExists(...)`
- `ChatFileService` for list/download resolution

Routes:

```java
@GetMapping("/api/chat/{conversationId}/files")
public ChatFileListing files(@PathVariable String conversationId)

@GetMapping("/api/chat/{conversationId}/files/download")
public ResponseEntity<?> download(@PathVariable String conversationId, @RequestParam("path") String relativePath)
```

Validation:

- Reuse or duplicate the UUID validation style from `ChatController.requireValidUuid(...)` if it is private. If duplication feels wrong, extract a tiny shared validator only if local code already has a suitable pattern.
- For nonexistent conversations, return 404.
- For nonexistent/missing/download-invalid files, return 404 or 400 with a small JSON error body.
- For too-large files, return 400 with a clear size error.

Do not expose absolute file paths in the API response.

### 5. Chat Page Markup

Edit:

- `FrontendController.java`

Add the right-side panel as a sibling of `chat-main` inside `.chat-layout`.

Example structure:

```java
.withChild(new HtmlTag("aside").withClass("chat-files-panel")
    .withChild(new Div().withClass("chat-files-header")
        .withChild(new HtmlTag("span").withInnerText("Outputs")))
    .withChild(new Div().withId("chat-session-files-body")
        .withClass("chat-files-body")
        .withInnerText("Select a chat to view outputs.")))
```

Keep it server-rendered as an empty target; JS fills it for the active session.

### 6. Session Card And Right Panel Rendering

Edit:

- `chat-client.js`

Add helpers:

- `sessionOutputCount(session)`
- `loadActiveFiles()`
- `loadSessionFiles(conversationId)`
- `renderSessionFiles(listing)`
- `formatBytes(bytes)`
- `formatFileTime(value)`
- `formatFileLabel(file)`

Session card output row:

```js
const outputCount = Number(session.outputCount || 0);
const outputsRow = outputCount > 0
    ? '<div class="chat-session-output-row">Outputs: ' + outputCount.toLocaleString() + '</div>'
    : '';
```

Add `outputsRow` below the title link, inside `.chat-session-entry`.

Right panel fetch:

```js
const data = await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/files');
renderSessionFiles(data);
```

Download URL:

```js
'/api/chat/' + encodeURIComponent(conversationId)
    + '/files/download?path=' + encodeURIComponent(file.relativePath)
```

Race guard:

- Store the conversation id requested for file loading.
- If the active conversation changes before the response returns, ignore the stale result.

### 7. CSS

Edit:

- `magenta.css`

Desktop:

- Expand `.chat-layout` to three columns.
- Keep `.chat-files-panel` width stable.
- Ensure file names wrap or ellipsize without overflowing.

Mobile:

- Under `900px`, stack the panel and remove fixed widths.
- Keep `#chat-session-list` max-height behavior intact.

Visual details:

- Format badges should be compact and non-dominant.
- Download buttons should use the existing button/link visual language.
- Avoid text overlap by using `minmax(0, 1fr)`, `overflow-wrap: anywhere`, and stable row gaps.

### 8. Tests

Backend tests:

- Add `ChatFileServiceTest`:
  - Counts zero when directory empty.
  - Counts and lists nested regular files.
  - Ignores directories.
  - Rejects traversal on download resolution.
  - Produces expected format labels.

- Update `ChatServiceTest` or a focused new test:
  - `listSessions()` includes `outputCount` for a conversation with files.
  - Constructor compatibility for `ChatSession` remains intact.

Controller tests:

- Add `ChatFileControllerTest` or extend `ChatControllerTest` only if routes live there:
  - `GET /api/chat/{id}/files` returns descriptors without absolute paths.
  - Download route returns attachment with expected filename and body.
  - Missing conversation returns 404.
  - Traversal path is rejected.

Frontend/controller tests:

- Update `FrontendControllerTest.chatPageRendersSimplyPagesChatShell()`:
  - Asserts `id="chat-session-files-body"` and `class="chat-files-panel"` exist.
  - Bump `/js/chat-client.js?v=...` if cache busting changes.

- Update `FrontendControllerTest.chatClientHandlesUnsavedConversationState()`:
  - Asserts JS contains `outputCount`, `Outputs:`, `/files/download`, and stale-response protection.

Existing tests to run:

```bash
mvn test -Dtest=FrontendControllerTest,ChatControllerTest,ChatServiceTest,AgentFileToolServiceTest,OutputArtifactServiceAttributionTest
```

Run broader tests if these touch shared constructors or Spring wiring:

```bash
mvn test
```

### 9. Docs

Update:

- `docs/end-user/chat.md`
  - Explain that chat sessions with generated files show `Outputs: <n>`.
  - Explain the right-side outputs list and download button.

- `docs/technical/api-reference.md`
  - Add the chat file listing/download routes.

- `docs/api/00-index.md`
  - Update `/api/chat` route summary or add the new chat file route entries.

- `docs/technical/workspaces-tools-outputs.md`
  - Clarify that ordinary chat files are listed from `chats/<conversationId>/files/` and are separate from `run_output_artifacts`.

- `docs/technical/frontend-htmx.md`
  - Note that this chat-page interaction remains in `chat-client.js` because the current session list and SSE transport are already JS-owned; standard CRUD elsewhere remains HTMX-first.

### 10. Internal Dev Workflow

After implementation and validation:

- Add `.internal-dev/changelogs/<date>-session-card-file-explorer.md`.
- Capture reusable knowledge in `.internal-dev/knowledge/` if implementation reveals important chat file path or Playwright validation details not already documented.
- Log any out-of-scope bugs immediately in `.internal-dev/bugs/`.
- Record deferred ideas in `.internal-dev/notes/` only after confirming they are out of scope.
- Archive this plan directory after the feature is finalized.
- Commit implementation, docs, tests, and `.internal-dev` updates together.

## Orchestration Plan

Shared notes path:

```text
.codex-orchestration/session-card-file-explorer/notes.md
```

Execution graph:

1. Parallel non-mutating prep:
   - Backend API contract review.
   - UI layout/test-design review.
2. Serial code edit 1:
   - Backend service, models, session contract, API routes, backend tests.
3. Validation gate 1:
   - Focused backend/controller tests.
4. Serial code edit 2:
   - Chat page markup, JS rendering, CSS.
5. Validation gate 2:
   - Frontend unit/contract tests and static inspection.
6. Serial code edit 3:
   - Docs and `.internal-dev` changelog/knowledge updates.
7. Final validation:
   - Full relevant Maven tests.
   - Spring Boot bounded startup.
   - Playwright MCP visual validation on desktop and mobile.
   - Review shared notes and resolve/remediate blockers.

Subagent roster for implementation:

- Backend worker
  - Model: default implementation model or `gpt-5.5`, medium reasoning.
  - May modify files.
  - Ownership: chat file service/model, session contract, API routes, backend tests.
  - Expected output: changed files, route contract, test results, blockers.

- Frontend worker
  - Model: default implementation model or `gpt-5.5`, medium reasoning.
  - May modify files only after backend worker is complete and validated.
  - Ownership: `FrontendController.java`, `chat-client.js`, `magenta.css`, focused frontend tests.
  - Expected output: changed files, interaction behavior, layout notes, test results.

- Documentation worker
  - Model: `gpt-5.3-codex`, medium reasoning.
  - May modify docs and `.internal-dev` artifacts after code contracts are stable.
  - Ownership: docs listed above and changelog/knowledge artifacts.
  - Expected output: changed files and source references checked.

- Playwright validation agent
  - Model: `gpt-5.3-codex`, medium reasoning, per repo testing instruction.
  - Must not modify files unless explicitly assigned remediation later.
  - Ownership: running browser validation and screenshots.
  - Must read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before testing.
  - Expected output: server command used, browser scenarios, screenshot paths or observations, console/network errors, visual findings, blockers.

Code-modifying workers must run serially. Non-mutating review and validation can run in parallel with planning or after code checkpoints, but no two agents should edit code at the same time.

Remediation policy:

- If a validation gate fails, pause subsequent code-editing phases.
- Assign exactly one remediation owner for the failing area.
- Re-run the failed test/check before proceeding.
- Record failure, remediation, and re-test result in shared notes.

## Validation

Automated validation:

```bash
mvn test -Dtest=FrontendControllerTest,ChatControllerTest,ChatServiceTest,AgentFileToolServiceTest
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If `ChatFileControllerTest` or `ChatFileServiceTest` are added, include them explicitly in the focused command.

Playwright MCP validation:

1. Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
2. Start the app against an isolated database on an allowed MCP origin, preferably port `18080`:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-session-files-ui.sqlite --magenta.executor.chat-threads=4'
```

3. Seed a session with at least two chat files. Prefer doing this through API/database plus filesystem setup to avoid depending on a live model:
   - Create a conversation in `ai_chat_memory` and metadata, or use a lightweight chat request if local model services are available.
   - Create files under the matching data root `chats/<conversationId>/files/`, including at least one nested file and one markdown/text file.
4. Open `http://localhost:18080/chat?conversationId=<id>`.
5. Verify desktop viewport:
   - Session card shows `Outputs: 2` or expected count.
   - Sessions with no files do not show an outputs row.
   - Right panel lists files with names, format labels, size, and download controls.
   - Download link returns HTTP 200 and attachment headers for a known file.
   - No unexpected console errors.
6. Switch between a session with files and one without files:
   - Right panel updates and does not show stale file data.
7. Send or simulate a chat turn that writes a file if model/tool environment is available:
   - After completion, the session card count and right panel refresh.
8. Mobile viewport under `900px`:
   - Session list, chat, and outputs panel stack cleanly.
   - No overlapping text or horizontal overflow.
9. Capture screenshots for desktop and mobile and visually inspect layout before sign-off.

Acceptance criteria:

- A session with `n` regular files in `chats/<conversationId>/files/` returns `outputCount = n` from `/api/chat/sessions`.
- Session cards render `Outputs: <n>` only when `n > 0`.
- The active chat outputs panel lists the selected session's files without exposing absolute paths.
- Each listed file has a working download button.
- Switching sessions updates the right panel without stale data.
- Existing chat, planning, session mutation, bulk action, and stream behavior still pass focused tests.
- Docs accurately describe the new behavior and routes.
- Playwright visual validation is completed by a subagent, or the exact blocker is recorded and not treated as fully validated.

## Exit Criteria

- Backend APIs and session JSON contract implemented and covered by tests.
- UI displays session output counts and active-session file list with download links.
- Desktop and mobile layouts have been visually inspected through Playwright screenshots.
- Relevant docs are updated.
- `.internal-dev` changelog and any required knowledge/bug/note artifacts are created.
- Implementation is committed on the dedicated branch with both code and `.internal-dev` updates.
