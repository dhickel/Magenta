## Date

2026-04-29

## Change Summary

Polished the chat UI tool cards, sessions sidebar, and composer layout. Changed `/chat` and `/new` to use an unsaved "New chat" state so empty conversation ids are not created until the first real message is sent.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`

## Behavioral Impact

- `/chat` renders with no active persisted conversation and displays `New chat`.
- `/new` clears the client into the same unsaved state without allocating a UUID or adding a session entry.
- The first normal message from the unsaved state sends a null conversation id; the stream start event supplies the allocated id.
- Tool cards use tighter alignment, fixed summary columns, shorter summaries, 10,000-character expanded caps, and pretty-printed JSON detail text.
- The composer textarea spans the full width with the submit button below and right-aligned.

## Risks

- Browser behavior depends on the SSE `start` event arriving before history reload after the first unsaved message.
- Pretty-printed JSON can consume the 10,000-character cap faster than compact JSON.

## Follow-up Items

- None.
