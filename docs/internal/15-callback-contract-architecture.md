# Callback Contract Architecture

## Design intent

Provide a small, explicit callback surface for runtime integration without introducing additional service layers.

The callback contract is owned by `SessionConfig` and is intentionally function-first:

- callbacks are plain Java `Consumer`/`Function` values
- runtime owns orchestration and invokes callbacks at stable points
- caller code owns side effects and policy

## Contract surface (`SessionConfig`)

Canonical callbacks:

- `onMessageAppendedHook: Consumer<SessionMessage>`
- `onUserMsgHook: Consumer<SessionMessage.UserMsg>`
- `onAssistantMsgHook: Consumer<SessionMessage.AssistantMsg>`
- `onToolMsgHook: Consumer<SessionMessage.ToolMsg>`
- `onSystemMsgHook: Consumer<SessionMessage.SystemMsg>`
- `onSummaryMsgHook: Consumer<SessionMessage.SummaryMsg>`
- `onInboundMsgHook: Consumer<SessionMessage.InboundMsg>`
- `onMessageInputHook: Consumer<SessionInput.MessageInput>`
- `onEventInputHook: Consumer<SessionInput.EventInput>`
- `onTokenStreamHook: Consumer<String>`
- `onStreamingResponseConsumer: Consumer<String>`
- `onFullResponseConsumer: Consumer<String>`
- `toolBridge: Function<ToolRequest, ToolResult>`
- `onErrorHook: Consumer<Throwable>`

Control flags:

- `blockingOnly: boolean`
- `toolsEnabled: boolean`
- `emitStreamingCompletionToFullResponse: boolean`

## Message callback semantics

All appended context messages are dispatched through:

1. `onMessageAppendedHook`
2. exactly one typed message callback based on `SessionMessage` subtype

Dispatch subtypes:

- `UserMsg`
- `AssistantMsg`
- `ToolMsg`
- `SystemMsg`
- `SummaryMsg`
- `InboundMsg`

`SessionMessage.content()` is the canonical text representation for logging/context/token estimation.

## Turn-path callback behavior

Router-backed submit path:

1. `SessionInputRouter` validates liveness and policy
2. `SessionManager` submits approved input to internal turn executor
3. turn executor resumes session
4. compacts context if needed
5. emits input callback (`onMessageInputHook` or `onEventInputHook`)
6. appends persisted input message (`UserMsg` or `InboundMsg`)
7. emits message callbacks for appended message
8. runs model turn loop

`ModelRunner.runTurn(...)`:

- emits message callbacks for each appended `AssistantMsg`
- emits message callbacks for each appended `ToolMsg`
- streams token chunks to `onTokenStreamHook` and `onStreamingResponseConsumer` in streaming mode
- emits full assistant text to `onFullResponseConsumer` for blocking turns and optionally for streaming turns
- calls `toolBridge` for each tool call when tools are enabled

## Error semantics

`onErrorHook` is emitted from `SessionManager` ingress submit catch handling:

1. any throwable from internal turn execution path is caught
2. `sessionConfig.onErrorHook` is called once with the throwable
3. throwable is swallowed for external consumer/router ingress

Important behavior:

- callback is notification-only
- router/consumer ingress does not throw external exceptions

## Defaults

`SessionConfig.defaults()` configures:

- no-op callbacks
- no-op output consumers
- `toolBridge` returns `ToolResult.notHandled(...)`
- `blockingOnly = false`
- `toolsEnabled = true`
- `emitStreamingCompletionToFullResponse = true`

## Extension boundaries

In scope for callback contract evolution:

- add new typed callbacks when new `SessionMessage` variants are added
- add small data fields to callback inputs
- keep compatibility aliases during rename transitions

Out of scope for callback contract:

- scheduler ownership
- bus persistence/durability
- centralized security policy service

Those concerns should stay in runtime/service boundaries and feed into session turns via `SessionInput`.
