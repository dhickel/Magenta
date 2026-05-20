# Session Card File Explorer

## Date

2026-05-20

## Change Summary

Added ordinary chat file discovery to `/chat`. Session cards now expose `outputCount` as `Outputs: <n>` when persistent chat files exist, and the active chat page includes an outputs panel that lists chat files with metadata and download links.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSession.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatFileListing.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatFileSummary.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatFileService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatFileController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendFragmentController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/css/magenta.css`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatFileServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatFileControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
- `docs/end-user/chat.md`
- `docs/technical/api-reference.md`
- `docs/api/00-index.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/frontend-htmx.md`

## Behavioral Impact

Chat sessions with regular files under `chats/<conversationId>/files/` show an output count in the session sidebar. Selecting a session loads a right-side outputs panel from `/api/chat/{conversationId}/files`; each row has a format label, filename, relative path when useful, size, modified time, and an attachment download link.

## Risks

`ChatService.listSessions()` now performs a filesystem count for each visible ordinary chat session. This is acceptable for the current alpha scale but may need caching or persisted metadata if users accumulate many sessions or large file trees.

## Follow-up Items

- Consider cached or indexed output counts if session-list filesystem walks become slow.
