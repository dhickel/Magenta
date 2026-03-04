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
  -> SessionManager (start/resume/fork/close + settings lookup)
  -> SessionRouter (route table keyed by SessionHandle)
  -> ContextManager (state + compaction)
  -> ModelRunner (turn + tool loop)
      -> OllamaClient (HTTP transport)
```

## Public API (handle-first)

Lifecycle methods return `SessionHandle`:

- `startBaseSession(alias)`
- `startBaseSession(alias, sessionConfig)`
- `startSession(agentId, alias)`
- `startSession(agentId, alias, sessionConfig)`
- `resumeSession(handle)`
- `forkSession(sourceHandle, alias)`
- `forkSession(sourceHandle, alias, sessionConfigOverride)`
- `closeSession(handle)`

`SessionHandle` is intentionally lightweight:

- `sessionId`
- `isActiveSupplier` (via `isActive()`)

Session settings are looked up explicitly:

- `settingsFor(handle) -> SessionSettingsView`

## Routing API

Route identity and lookup:

- `RouteHandle` (`routeId`, `isActive()`)
- `Route` ADT:
  - `Route.InputRoute`
  - `Route.OutputRoute`

Route operations:

- `addInputRoute(handle, policy) -> RouteHandle`
- `addOutputRoute(handle, outputPolicy, outputListener) -> RouteHandle`
- `removeRoute(routeHandle)`
- `route(routeHandle) -> Route`
- `routes(handle) -> Set<Route>`
- `messageInputConsumer(handle)`
- `eventInputConsumer(handle)`

Input routing behavior:

- multiple input routes per session
- insertion-order evaluation
- first approval short-circuits submit
- final deny is emitted after route exhaustion

Output routing behavior:

- fanout to all matching output routes
- listener failures are isolated and diagnostics-only

## SessionConfig contract

`SessionConfig` fields:

- `params` (`SessionParams`: `blockingOnly`, `toolsEnabled`, `streamingEnabled`)
- `toolBridge`
- `routingEventLevel` (`NONE`, `FINAL`, `ALL`)
- `onRouting`
- `onError` (`Consumer<SessionException>`)

## Turn flow

1. Caller submits typed input through `messageInputConsumer(handle)` / `eventInputConsumer(handle)`.
2. Router applies input route policies and emits input routing trace events.
3. Approved input is submitted through `SessionManager`.
4. `Magenta` appends persisted input to context and emits `ContextMessageOutput`.
5. `Magenta` computes stream mode:
   - `settingsFor(handle).streamingEnabled() && sessionRouter.hasStreamedOutputListeners(handle)`
6. `ModelRunner` executes the turn and emits routed outputs.
7. Tool calls use `SessionConfig.toolBridge`.

## Streaming contract

- Session-level gate: `SessionConfig.params().streamingEnabled()`.
- Streamed output listeners can only be added when session streaming is enabled.
- Streamed payload is provider chunk content from Ollama.
- `Session` and `ModelRunner` remain router-agnostic.

## Known constraints

- Session and routing registries are in-memory only.
- `ContextManager.storeContext(...)` is currently a no-op seam.
- Security service centralization is future-phase; `toolBridge` remains in `SessionConfig`.

## Related docs

- Internal API contract: `16-public-api-contract.md`
- External usage guide: `../quickstart-chat-loop.md`
