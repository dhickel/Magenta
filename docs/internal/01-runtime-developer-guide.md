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
- immutable `SessionSettingsView` (`handle.settingsView()`) containing flattened session + agent + model settings snapshot

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

Output wrapper event (`OutputRoutingEvent`):

- `sessionId`
- `output` (`SessionOutput`)

`SessionOutput` kinds:

- `StreamedOutput`
- `FinalOutput`
- `ContextMessageOutput`
- `ToolMessageOutput`

## SessionConfig contract

`SessionConfig` fields:

- `params` (`SessionParams`: `blockingOnly`, `toolsEnabled`, `streamingEnabled`)
- `toolBridge`
- `onError`

There is no `SessionConfig` builder in the current API; construct it directly with `new SessionConfig(...)`.

## Turn flow

1. Caller submits typed input through `SessionRouter` consumer.
2. Router enforces handle liveness + input policy and emits `InputRoutingEvent`.
3. `SessionManager` submits to turn execution and catches internal failures to emit `SessionConfig.onError`.
4. `Magenta` appends persisted input to context and emits `ContextMessageOutput`.
5. `Magenta` computes stream mode:
   - `sessionConfig.params().streamingEnabled() && sessionRouter.hasStreamedOutputListeners(handle)`
6. `ModelRunner` executes the turn:
   - streaming emits `StreamedOutput` chunks
   - final assistant text always emits `FinalOutput`
   - appended messages emit `ContextMessageOutput`/`ToolMessageOutput`
7. Tool calls use `SessionConfig.toolBridge`.

## Streaming contract

- If `streamingEnabled == false`, registering output routes that request streamed output throws validation error.
- If `streamingEnabled == true`, streamed output is still per-turn and only enabled when streamed-output listeners exist.
- `StreamedOutput` payload is provider chunk content from Ollama; chunk boundaries are provider-defined and are not guaranteed to be one token.
- `Session` and `ModelRunner` stay router-agnostic.

## Known constraints

- Session and routing registries are in-memory only.
- `ContextManager.storeContext(...)` is currently a no-op seam.
- Security service centralization is future-phase; `toolBridge` remains in `SessionConfig`.

## Related docs

- Internal API contract: `16-public-api-contract.md`
- External usage guide: `../quickstart-chat-loop.md`
