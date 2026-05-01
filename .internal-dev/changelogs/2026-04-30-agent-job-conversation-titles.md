# Agent Job Conversation Titles

## Date

2026-04-30

## Change Summary

Added persisted Magenta-owned agent jobs with `CONVERSATION_TITLE` as the first job type. New conversations enqueue one bounded background title job after successful chat completion, titles are stored as chat session metadata, and chat session/history payloads expose nullable title fields. The browser session list renders titles when available and briefly polls after a new conversation starts.

## Files

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/agent/job/*`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSession.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSessions.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatHistory.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatResponse.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/ai/agent/job/*`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

## Behavioral Impact

New chat conversations keep their UUID as the stable id and gain a nullable display title once the background job succeeds. Title jobs use the selected chat model without tools or chat-memory advisors. The agent job executor allows two active jobs and queues overflow.

## Risks

Title generation depends on model availability and may fail independently of the chat turn. Failures are recorded in the internal job table and leave the conversation title null.

## Follow-up Items

- Add a private operator view for internal agent jobs when there is a concrete debugging workflow.
