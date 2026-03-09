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
- `SecurityManager`
- `ToolManager`

Future targets (`MindStore`, `SchedulerService`, full cross-domain `SecurityService`) are not implemented in this slice.

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
- `onSecurity`
- `onError` (`Consumer<SessionException>`)

## Turn flow

1. Caller submits typed input through `messageInputConsumer(handle)` / `eventInputConsumer(handle)`.
2. Router applies input route policies and emits input routing trace events.
3. Approved input is submitted through `SessionManager`.
4. `Magenta` appends persisted input to context and emits `ContextMessageOutput`.
5. `Magenta` computes stream mode:
   - `settingsFor(handle).streamingEnabled() && sessionRouter.hasStreamedOutputListeners(handle)`
6. `ModelRunner` executes the turn and emits routed outputs.
7. Tool calls use `SessionConfig.toolBridge`, wrapped by `SecurityManager`.
8. Security authorization is descriptor-driven per tool (`ToolSecurityDescriptor`) and decision events emit through `onSecurity`.
9. Tool specs are discovered from annotation-registered tools and passed to model requests when enabled and supported.

## Tool and security notes

- Built-in tools include file edit/read tools, directory/metadata discovery (`list_directory`, `file_metadata`), shell execution, and SQLite tools.
- `allowedPaths` policy is treated as approved roots for path-bearing tools and evaluated against resolved targets.
- Out-of-root path requests require explicit approval callback decision.
- Shell command policy parsing is quote/escape aware and rejects chained operators under security validation.
- SQLite tool SQL gating is parser-based and fail-closed on unsupported or parse-failed statements.

## Streaming contract

- Session-level gate: `SessionConfig.params().streamingEnabled()`.
- Streamed output listeners can only be added when session streaming is enabled.
- Streamed payload is provider chunk content from Ollama.
- `Session` and `ModelRunner` remain router-agnostic.

## Known constraints

- Session and routing registries are in-memory only.
- `ContextManager.storeContext(...)` is currently a no-op seam.
- Security policy state is session-scoped and currently in-memory.

## Test execution contract

- Functional and policy tests run in the Surefire `test` phase (`*Test`).
- Integration tests run in Failsafe `integration-test` + `verify` phases (`*IT`, `*IntegrationTest`).
- Tool-related changes must include functionality + policy + integration-path tests for affected tool IDs.
- Merge readiness requires `mvn verify`, not `mvn test` alone.

## Related docs

- Runtime architecture: `10-runtime-architecture.md`
- Internal API contract: `16-public-api-contract.md`
- Tool/security deep dive: `17-tools-security-architecture.md`
- Integration patterns: `20-integration-patterns.md`
- Troubleshooting: `30-runtime-troubleshooting.md`
- External usage guide: `../quickstart-chat-loop.md`
