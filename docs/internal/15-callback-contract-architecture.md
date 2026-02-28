# Callback Contract Architecture

## Design intent

Provide a small, explicit callback surface for runtime integration without introducing additional service layers.

The callback contract is owned by `SessionConfig` and is intentionally function-first:

- callbacks are plain Java `Consumer`/`Function` values
- runtime owns orchestration and invokes callbacks at stable points
- caller code owns side effects and policy

## Contract surface (`SessionConfig`)

Canonical callbacks:

- `onMessageAppended: Consumer<SessionMessage>`
- `onUserMsg: Consumer<SessionMessage.UserMsg>`
- `onAssistantMsg: Consumer<SessionMessage.AssistantMsg>`
- `onToolMsg: Consumer<SessionMessage.ToolMsg>`
- `onSystemMsg: Consumer<SessionMessage.SystemMsg>`
- `onSummaryMsg: Consumer<SessionMessage.SummaryMsg>`
- `onInboundMsg: Consumer<SessionMessage.InboundMsg>`
- `onMessageInput: Consumer<SessionInput.MessageInput>`
- `onEventInput: Consumer<SessionInput.EventInput>`
- `onTokenStream: Consumer<String>`
- `toolBridge: Function<ToolRequest, ToolResult>`
- `onError: Consumer<Throwable>`

Control flags:

- `blockingOnly: boolean`
- `toolsEnabled: boolean`

Compatibility alias:

- `onMessageStored(...)` is kept as a backward-compatible alias for `onMessageAppended(...)`.

## Message callback semantics

All appended context messages are dispatched through:

1. `onMessageAppended`
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

`Magenta.runSessionTurn(...)`:

1. resumes session
2. compacts context if needed
3. emits input callback (`onMessageInput` or `onEventInput`)
4. appends persisted input message (`UserMsg` or `InboundMsg`)
5. emits message callbacks for appended message
6. runs model turn loop

`ModelRunner.runTurn(...)`:

- emits message callbacks for each appended `AssistantMsg`
- emits message callbacks for each appended `ToolMsg`
- streams token chunks to `onTokenStream` in streaming mode
- calls `toolBridge` for each tool call when tools are enabled

## Error semantics

`onError` is emitted in `Magenta.runSessionTurn(...)` catch handling:

1. any throwable from resume/compaction/model/tool path is caught
2. `sessionConfig.onError` is called once with the throwable
3. original throwable is rethrown

Important behavior:

- callback is notification-only
- runtime does not swallow the original failure
- caller can still apply top-level error policy around runtime calls

## Defaults

`SessionConfig.defaults()` configures:

- no-op callbacks
- `toolBridge` returns `ToolResult.notHandled(...)`
- `blockingOnly = false`
- `toolsEnabled = true`

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
