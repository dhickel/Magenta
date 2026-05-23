# Chat Session Scope Filter

## Date

2026-05-22

## Change Summary

Added explicit chat surface tagging so the `/chat` session list only shows browser-surface conversations in normal mode. Avatar chat, agent chat, and planning/internal conversation flows are tagged or filtered out at the persistence boundary instead of leaking into the browser session sidebar.

## Behavioral Impact

- `/api/chat/sessions` now returns only browser `/chat` sessions that are in normal chat mode.
- `/api/chat/stream` request payloads can carry a `surface` marker so the browser and Avatar frontends can tag their own conversations.
- Session metadata now persists a `surface` field alongside the existing conversation origin and agent attribution.
- Planning conversations stay out of the browser session list even when they share chat infrastructure.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSessionSurface.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/schema.sql`
- `src/main/resources/static/js/avatar-chat.js`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `docs/end-user/chat.md`
- `docs/technical/api-reference.md`

## Risks

Historical sessions created before `surface` existed are ambiguous. The fix prioritizes avoiding cross-surface leakage over showing untagged legacy conversations in the `/chat` sidebar.

## Follow-up Items

- Historical untagged sessions will remain ambiguous until they are reopened or deliberately migrated with an explicit surface marker.
- GitHub issue #7 tracks making `ChatSessionSurface` deserialization case-insensitive for direct API callers.

## Validation

- Focused chat service tests should cover browser, Avatar, agent, and planning conversation filtering.
- Controller and browser client tests should be rerun after the payload shape change.
