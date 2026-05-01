## Date

2026-05-01

## Change Summary

Implemented Magenta-owned threaded execution foundations with per-conversation turn coordination, active streaming turn tracking, and tool-loop interrupt support.

## Files

- Added `ai.execution` classes for work lanes, priority submission, conversation turn queues, and active turn interrupts.
- Updated chat controller/service flow to route turns through the coordinator and expose turn interrupt handling.
- Updated browser chat client to keep input available during active turns and queue follow-up messages when interrupts cannot be injected.

## Behavioral Impact

- Same-conversation chat turns are serialized while different conversations can run concurrently.
- Streaming turns expose a turn id and interrupt token.
- User corrections sent during a tool loop are injected before the next model call; corrections during plain generation are queued client-side for the next turn.
- Conversation title jobs can run through the shared Magenta work executor.

## Risks

- Running tools are still blocking and are not cancelled by interrupt messages.
- Controller keeps a Reactor subscription boundary for compatibility while ChatService routes real work through the coordinator.

## Follow-up Items

- Add first delegation tool/workflow on top of the delegation lane.
- Add configuration properties for executor lane sizes and queue capacity.
