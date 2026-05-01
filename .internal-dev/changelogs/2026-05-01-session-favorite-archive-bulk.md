# Session Favorite Archive Bulk

## Date

2026-05-01

## Change Summary

Added favorite and archive metadata for chat sessions, sorted visible sessions with favorites first and recent activity next, and added a top sidebar management panel for selected-session delete, archive, and favorite actions.

## Files

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSession.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSessionFavoriteRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSessionArchiveRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

## Behavioral Impact

Session rows now include a star toggle and archive action. Archived sessions are hidden from the normal session list. The management panel shows checkboxes for visible sessions and applies delete/archive/favorite to selected sessions. Favorite bulk action only adds favorites.

## Risks

There is no archive restore UI yet, so archived sessions are hidden until a future restore workflow is added.

## Follow-up Items

- Add archived-session browsing and restore when needed.
