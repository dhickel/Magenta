## Date

2026-05-01

## Change Summary

Repeated tool-call aborts now become a model-visible control message instead of aborting the chat turn and dropping the error state. Tool transcripts are persisted before the abort notice, and the final recovery model call runs without tool callbacks.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`

## Behavioral Impact

When tool use stops because of repeated identical calls or too many recent tool errors, the model receives a system control message with the abort reason. For repeated tool errors, recent error snippets are included so the model can explain the failure or continue from available context.

## Risks

The abort control message is persisted as a normal system message and may be visible in chat history like other non-hidden system messages.

## Follow-up Items

None.
