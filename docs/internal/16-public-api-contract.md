# Public API Contract

## Scope

This contract defines the intended external runtime API for application developers embedding Magenta2.

## Stable entrypoint

- Primary runtime entrypoint: `io.mindspice.magenta.Magenta`
- Startup config contract: `io.mindspice.magenta.runtime.config.RuntimeConfig`

## Supported interaction contract

Applications should treat these types as supported API surface for v1 chat/runtime usage:

- `Magenta`
- `RuntimeConfig`
- `SessionConfig`
- `SessionHandle`
- `SessionSettingsView` (via `Magenta.settingsFor(handle)`)
- `SessionParams` (via `SessionConfig.params()`)
- `SessionInput`
- `RouteHandle`
- `Route` (`Route.InputRoute`, `Route.OutputRoute`)
- `InputRoutePolicy`, `InputRoutingEvent`
- `OutputRoutePolicy`, `OutputRoutingEvent`
- `RoutingEvent`, `RoutingEventLevel`
- `SessionException`
- `SecurityManager.ToolPolicy` (via `Magenta.toolPolicy(handle)` / `Magenta.setToolPolicy(handle, ...)`)

## Lifecycle contract

- Start: `startBaseSession(...)` / `startSession(...)` returns `SessionHandle`.
- Reattach: `resumeSession(handle)` returns a fresh `SessionHandle`.
- Fork: `forkSession(sourceHandle, ...)` clones context into a new session id.
- Close: `closeSession(handle)` deactivates handle and prunes routes.
- Tool policy: `toolPolicy(handle)` reads active session tool policy and `setToolPolicy(handle, policy)` atomically replaces it.

## Route contract

- Route table is keyed by `SessionHandle` identity (`sessionId` equality).
- Input routes: multiple per session; evaluated in insertion order with first-approve short-circuit.
- Output routes: zero to many routes per session; fanout to all matching output routes.
- Route identity for remove/lookup is `RouteHandle`.
- Streamed-output listeners require `SessionConfig.params().streamingEnabled() == true`.

## Callback contract

- `onRouting`: optional session-level observability callback for input/output routing results, controlled by `RoutingEventLevel`.
- `onSecurity`: optional session-level observability callback for tool security decisions.
- `onError`: `Consumer<SessionException>` including `SessionHandle`.
- Output listeners are delivery callbacks, not diagnostics callbacks.

## Compaction-agent contract

- `RuntimeConfig.compactionAgentId` must resolve to an enabled agent during config load.
- Compaction does not create a separate session or long-lived compaction runtime object.
- On compaction, `Magenta` resolves compaction model/prompt data from loaded config and calls `ModelRunner.summarize(...)` directly.

## Facade boundary policy

- `Magenta` is the supported orchestration facade for normal runtime usage.
- Internal service classes (`SessionManager`, `SessionRouter`, `ContextManager`, `ModelRunner`) are not exposed through `Magenta`.

## Stability notes

- Package moves and internal service structure may still change in this rewrite phase.
- External consumers should prefer `Magenta` + handle/route APIs as the compatibility target.
