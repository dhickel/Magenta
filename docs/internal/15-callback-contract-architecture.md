# Routing and Callback Contract Architecture

## Design intent

Use one concrete routing service (`SessionRouter`) as the external IO boundary, while keeping `ModelRunner` and `Session` decoupled from router internals.

## SessionConfig callback contract

`SessionConfig` remains small but explicit:

- `toolBridge`: tool execution callback
- `onRouting`: optional session-scoped routing observability callback for both input and output routing results
- `onError`: session-scoped execution error callback (`SessionException`)

## Input routing contract

- Input routes are immutable route entries in the router route table.
- Multiple input routes can exist per session.
- Evaluation is insertion-order and stops at first approval.
- Exhaustion emits a final denied routing decision.
- Routing callback emission level is controlled by `RoutingEventLevel`:
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

- Input submit path catches internal turn failures and calls `SessionConfig.onError` with `SessionException`.
- Routing callback failures are observability-only and must not break ingress/turn execution.

## Defaults

`Magenta` default session config uses:

- `params = SessionParams.ofStreaming(true)`
- `toolBridge = ToolResult.notHandled(...)`
- `routingEventLevel = NONE`
- `onRouting = unset`
- `onError = no-op`
