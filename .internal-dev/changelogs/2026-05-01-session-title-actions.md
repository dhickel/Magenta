# Session Title Actions

## Date

2026-05-01

## Change Summary

Added chat session sidebar controls for manual rename and delete. Session rename edits the title in place and persists through chat metadata. Delete uses the existing conversation deletion endpoint after a confirmation prompt that displays the visible chat name and UUID.
Generated title jobs now only fill empty title metadata so a later job completion cannot overwrite a manual rename.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSessionTitleRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/agent/job/AgentJobService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/agent/job/AgentJobServiceTest.java`

## Behavioral Impact

Users can rename generated chat titles from the collapsible session list without changing the conversation UUID. Users can delete a session from the same list after confirming the title and id shown in the prompt.
Manual titles take precedence over pending generated title jobs.

## Risks

Delete still clears immediately after browser confirmation; there is no undo.

## Follow-up Items

- None.
