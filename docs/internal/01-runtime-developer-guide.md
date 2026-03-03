# Magenta2 Runtime Developer Guide

Primary implementation guide for the current runtime slice.

## Implemented services

- `RuntimeConfig`
- `Magenta`
- `SessionManager`
- `ContextManager`
- `SessionRouter`
- `ModelRunner`
- `OllamaClient`

Future targets (`MindStore`, `SchedulerService`, `SecurityService`) are not implemented in this slice.

## Runtime at a glance

```text
RuntimeConfig.load(...) -> Magenta
  -> SessionManager (start/resume/fork/close)
  -> SessionRouter (input + output route registry)
  -> ContextManager (state + compaction)
  -> ModelRunner (turn + tool loop)
      -> OllamaClient (HTTP transport)
```

## Public API (handle-first)

`Magenta` lifecycle returns `SessionHandle`:

- `startBaseSession(alias)`
- `startBaseSession(alias, sessionConfig)`
- `startSession(agentId, alias)`
- `startSession(agentId, alias, sessionConfig)`
- `resumeSession(sessionId)`
- `forkSession(sourceSessionId, alias)`
- `forkSession(sourceSessionId, alias, sessionConfigOverride)`
- `closeSession(handle)`

`SessionHandle` fields:

- `sessionId`
- `isActiveSupplier` (via `isActive()`)
- immutable `SessionConfigView`

## Routing API

Input routing (single active route per session):

- `registerInputRoute(handle, policy, routingEventLevel, routingEventListener)`
- `updateInputRoute(handle, ...)`
- `unregisterInputRoute(handle)`
- `getMessageInputConsumer(handle)`
- `getEventInputConsumer(handle)`

Output routing (multiple routes per session):

- `registerOutputRoute(handle, outputPolicy, outputListener) -> routeId`
- `unregisterOutputRoute(handle, routeId)`

Output event ADT (`OutputRoutingEvent`):

- `PartialToken`
- `AssistantFinal`
- `MessageAppended`
- `ToolMessageAppended`

## SessionConfig contract

`SessionConfig` fields:

- `blockingOnly`
- `toolsEnabled`
- `bypassSecurity`
- `streamingEnabled`
- `toolBridge`
- `onError`

Defaults:

- `blockingOnly = false`
- `toolsEnabled = true`
- `bypassSecurity = false`
- `streamingEnabled = true`
- `toolBridge = ToolResult.notHandled(...)`
- `onError = no-op`

## Turn flow

1. Caller submits typed input through `SessionRouter` consumer.
2. Router enforces handle liveness + input policy and emits `InputRoutingEvent`.
3. `SessionManager` submits to turn execution and catches internal failures to emit `SessionConfig.onError`.
4. `Magenta` appends persisted input to context and emits `MessageAppended`.
5. `Magenta` computes stream mode:
   - `sessionConfig.streamingEnabled && sessionRouter.hasPartialTokenListeners(handle)`
6. `ModelRunner` executes the turn:
   - streaming emits only `PartialToken`
   - final assistant text always emits `AssistantFinal`
   - appended messages emit `MessageAppended`/`ToolMessageAppended`
7. Tool calls use `SessionConfig.toolBridge`.

## Streaming contract

- If `streamingEnabled == false`, registering output routes that request partial tokens throws validation error.
- If `streamingEnabled == true`, partial streaming is still per-turn and only enabled when partial listeners exist.
- `Session` and `ModelRunner` stay router-agnostic.

## Known constraints

- Session and routing registries are in-memory only.
- `ContextManager.storeContext(...)` is currently a no-op seam.
- Security service centralization is future-phase; `toolBridge` remains in `SessionConfig`.

## Related docs

- Internal API contract: `16-public-api-contract.md`
- External usage guide: `../quickstart-chat-loop.md`
