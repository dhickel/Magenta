# Routing and Callback Contract Architecture

## Design intent

Use one concrete routing service (`SessionRouter`) as the external IO boundary, while keeping `ModelRunner` and `Session` decoupled from router internals.

## SessionConfig contract

`SessionConfig` remains intentionally small:

- `blockingOnly`
- `toolsEnabled`
- `bypassSecurity`
- `streamingEnabled`
- `toolBridge`
- `onError`

Only `toolBridge` and `onError` are callbacks in this phase.

## Input routing contract

- One active input route per session (`register` replaces previous route).
- Policy enforcement uses `InputRoutePolicy`.
- Route outcomes are reported as `InputRouteReport` with `APPROVED`, `DENIED_POLICY`, or `SESSION_INACTIVE`.
- Unknown/inactive handles produce deterministic validation errors.

## Output routing contract

- Multiple output routes per session.
- Each route has `OutputRoutePolicy` filtering by event kind/source/tag.
- Emitted event types:
  - `PartialToken`
  - `AssistantFinal`
  - `MessageAppended`
  - `ToolMessageAppended`
- Listener failures are isolated and reported via router diagnostics.

## Streaming contract

- Session-level gate: `SessionConfig.streamingEnabled`.
- If disabled, routes requesting partial tokens are rejected.
- If enabled, turn streaming occurs only when partial listeners exist.
- No parallel callback bypass path for streamed tokens.

## Error semantics

- Input submit path catches internal turn failures and calls `SessionConfig.onError`.
- Router reporting/listener callbacks are observability only and must not break ingress/turn execution.

## Defaults

`SessionConfig.defaults()` sets:

- `blockingOnly = false`
- `toolsEnabled = true`
- `bypassSecurity = false`
- `streamingEnabled = true`
- `toolBridge = ToolResult.notHandled(...)`
- `onError = no-op`
