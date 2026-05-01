## Context

Magenta chat currently mixes servlet-thread non-streaming calls, Reactor boundedElastic streaming calls, and a separate agent job executor. Future delegation and active-turn interrupts need one predictable execution boundary.

## Goal

Implement a Magenta-owned threaded execution layer with per-conversation ordering, title/background job routing, and active streaming turn interrupts during tool loops.

## In Scope

- Add bounded internal execution services for chat, delegation-ready work, and background jobs.
- Serialize chat turns by conversation id while allowing different conversations to run concurrently.
- Route streaming, non-streaming, and title jobs through the execution layer.
- Track active streaming turns and allow same-stream interrupt messages during tool checkpoints.
- Keep tools blocking within the owning turn.

## Out of Scope

- Public queue management APIs.
- Async delegation completion notifications.
- Cancelling already-running tools.
- Durable persisted active turn state.

## Implementation Steps

- Add execution package with work kinds, priority executor, conversation turn coordinator, and active turn registry.
- Wire chat service and controller through the execution APIs.
- Extend stream events and browser client to support active-turn interrupts and local queued follow-up messages.
- Update tests for executor behavior, turn ordering, title job routing, and interrupt handling.

## Validation

- Run focused Java tests for chat controller/service and agent jobs.
- Run full Maven test suite if feasible.
- Run JavaScript syntax check for the browser client.

## Exit Criteria

- Same-conversation requests do not overlap.
- Different conversations can run concurrently.
- Streaming users can submit corrections during tool loops.
- Plain generation messages queue locally and auto-send after completion.
