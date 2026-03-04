# Routing and Callback Contract Architecture

## Design intent

Use one concrete routing service (`SessionRouter`) as the external IO boundary, while keeping `ModelRunner` and `Session` decoupled from router internals.

## SessionConfig contract

`SessionConfig` remains intentionally small:

- `params` (`blockingOnly`, `toolsEnabled`, `streamingEnabled`)
- `toolBridge`
- `onError`

Only `toolBridge` and `onError` are callbacks in this phase.

## Input routing contract

- One active input route per session (`register` replaces previous route).
- Policy enforcement uses `InputRoutePolicy`.
- Route outcomes are emitted as `InputRoutingEvent` with `APPROVED`, `DENIED_POLICY`, or `SESSION_INACTIVE`.
- Unknown/inactive handles produce deterministic validation errors.

## Output routing contract

- Multiple output routes per session.
- Each route has `OutputRoutePolicy` filtering by `SessionOutput` `FilterTag` selectors.
- Emitted event types:
  - `OutputRoutingEvent(sessionId, StreamedOutput)`
  - `OutputRoutingEvent(sessionId, FinalOutput)`
  - `OutputRoutingEvent(sessionId, ContextMessageOutput)`
  - `OutputRoutingEvent(sessionId, ToolMessageOutput)`
- Listener failures are isolated and emitted via router diagnostics.

## Streaming contract

- Session-level gate: `SessionConfig.streamingEnabled`.
- If disabled, routes requesting streamed output are rejected.
- If enabled, turn streaming occurs only when streamed-output listeners exist.
- Streamed payloads are provider chunks, not guaranteed to align to single-token boundaries.
- No parallel callback bypass path for streamed tokens.

## Error semantics

- Input submit path catches internal turn failures and calls `SessionConfig.onError`.
- Router event/listener callbacks are observability only and must not break ingress/turn execution.

## Defaults

`Magenta` session-start overloads without explicit `SessionConfig` use an internal default config:

- `params = SessionParams.ofStreaming(true)` (`blockingOnly=false`, `toolsEnabled=true`, `streamingEnabled=true`)
- `toolBridge = ToolResult.notHandled(...)`
- `onError = no-op`
