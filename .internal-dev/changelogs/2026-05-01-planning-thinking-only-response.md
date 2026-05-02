## Date

2026-05-01

## Change Summary

Fixed a tool-capable chat turn failure mode where a model response containing only thinking metadata, with no user-visible content and no tool calls, was accepted as a completed assistant turn. Magenta now treats that as incomplete, adds a mode-specific continuation instruction, and retries the model call before persisting the final assistant message.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`

## Behavioral Impact

PLAN mode should no longer silently end after a thinking-only response. The retry can still produce a normal visible planning question or discussion, or re-enter the tool loop if the retry produces tool calls.

## Risks

The retry is bounded to avoid infinite loops. If a model repeatedly returns empty final responses, the existing final response path can still surface an empty response after the retry limit.

## Follow-up Items

- Consider adding an explicit user-facing error or needs-review state if a model exhausts the empty-response retry limit.
