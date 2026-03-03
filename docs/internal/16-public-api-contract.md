# Public API Contract

## Scope

This contract defines the intended external runtime API for application developers embedding Magenta2.

## Stable entrypoint

- Primary runtime entrypoint: `io.mindspice.magenta.Magenta`
- Startup config contract: `io.mindspice.magenta.runtime.config.RuntimeConfig`

## Supported interaction contract

Applications should treat these types as the supported API surface for v1 chat/runtime usage:

- `Magenta`
- `RuntimeConfig`
- `SessionConfig`
- `SessionHandle`
- `SessionConfigView`
- `SessionInput`
- `InputRoutePolicy`, `InputRoutingEventLevel`, `InputRoutingEvent`
- `OutputRoutePolicy`, `OutputRoutingEvent`

## Lifecycle contract

- Start: `startBaseSession(alias)` / `startBaseSession(alias, sessionConfig)` or
  `startSession(agentId, alias)` / `startSession(agentId, alias, sessionConfig)` returns `SessionHandle`.
- Reattach: `resumeSession(sessionId)` returns a fresh `SessionHandle` for in-memory sessions.
- Fork: `forkSession(...)` clones context into a new session id.
- Close: `closeSession(handle)` deactivates handle and prunes routes.

## Route contract

- Input routes: exactly one active input route per session; `register` replaces existing route.
- Output routes: zero to many routes per session; each route is identified by UUID.
- Partial-token listeners require `SessionConfig.streamingEnabled == true`.

## Compaction-agent contract

- `RuntimeConfig.compactionAgentId` must resolve to an enabled agent during config load.
- Compaction does not create a separate session or long-lived compaction runtime object.
- On compaction, `Magenta` resolves compaction model/prompt data from loaded config and calls `ModelRunner.summarize(...)` directly.
- Because the graph is validated at startup and stored immutably, compaction-agent existence is guaranteed post-start.

## Facade boundary policy

- `Magenta` is the supported orchestration facade for normal runtime usage.
- Internal service classes (`SessionManager`, `SessionRouter`, `ContextManager`, `ModelRunner`) are not exposed through `Magenta`.
- Advanced consumers can compose services directly by constructing their own runtime wiring, but that path is intentionally outside the default facade contract.

## Stability notes

- Package moves and internal service structure may still change in this rewrite phase.
- External consumers should prefer `Magenta` + handle/routing APIs as the compatibility target.
- The handle-first routed API is the compatibility target for upcoming chat-facing dogfooding.
