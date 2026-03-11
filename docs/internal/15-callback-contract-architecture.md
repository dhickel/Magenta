# Routing and Event Contract Architecture

## Design intent

Use one concrete routing service (`SessionRouter`) as the external IO boundary, while keeping `ModelRunner` and `Session` decoupled from router internals.

## Session event contract

Observability and reactive integration now route through per-session typed events:

- `toolBridge`: tool execution callback
- `SessionEvent` hub: session-scoped typed listener registration/unregistration
- listener registration forms:
  - `on(Class<T>, Consumer<T>)`
  - `on(Class<T>, Predicate<T>, Consumer<T>)`
- major event families: `MessageIn`, `MessageOut`, `Action`, `RoutingDecision`, `SecurityDecision`, `ErrorEvent`

## SessionConfig callback compatibility

`SessionConfig.onRouting`, `onSecurity`, and `onError` remain as compatibility adapters:

- callbacks are registered as typed event listeners at session start/fork
- callbacks are no longer a separate primary emission path
- callback failures remain observability-only

## Input routing contract

- Input routes are immutable route entries in the router route table.
- Multiple input routes can exist per session.
- Evaluation is insertion-order and stops at first approval.
- Exhaustion emits a final denied routing decision.
- Routing callback adapter delivery is controlled by `RoutingEventLevel`:
  - `NONE`
  - `FINAL`
  - `ALL`

## Output routing contract

- Output routes are immutable route entries with per-route delivery callbacks.
- Output dispatch fans out to all matching output routes.
- Listener failures are isolated and emitted through router diagnostics.

## Streaming contract

- Session-level gate: `SessionConfig.params().streamingEnabled()`.
- `Magenta` only streams turns when streamed listeners exist.
- Streamed payloads are provider chunks, not guaranteed single-token boundaries.

## Error semantics

- Input submit path catches internal turn failures and emits `SessionEvent.ErrorEvent`.
- Legacy `onError` callback receives those errors through adapter wiring.
- Listener/callback failures are observability-only and must not break ingress/turn execution.

## Defaults

`Magenta` default session config uses:

- `params = SessionParams.ofStreaming(true)`
- `toolBridge = ToolManager.execute(...)` (empty manager defaults to not-handled)
- `routingEventLevel = NONE`
- `onRouting = unset` (compat adapter)
- `onSecurity = no-op` (compat adapter)
- `onError = no-op` (compat adapter)

## Tool bridge security wrapping

- `Magenta` wraps session `toolBridge` with `SecurityManager.authorize(...)` before delegation.
- Authorization decisions (allow/deny/validation/override) emit `SessionEvent.SecurityDecision`.
- Legacy `onSecurity` receives those events via callback adapter wiring.
- Denied tool calls return structured failure payloads to the model/tool loop; tool handlers are not executed on deny.
