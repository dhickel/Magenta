# Magenta2 Runtime Developer Guide

Primary implementation guide for the currently implemented runtime slice.

## Scope

This guide covers implemented runtime behavior for:

- `RuntimeConfig`
- `Magenta`
- `SessionManager`
- `ContextManager`
- `ModelRunner`
- `OllamaClient`
- `SessionConfig` callback integration

Future target services (`MindStore`, `SchedulerService`, `SecurityService`) are not implemented in this slice and are documented as future architecture targets only.

## Runtime at a glance

```text
RuntimeConfig.load(...) -> Magenta
  -> SessionManager (start/resume/fork)
  -> ContextManager (state + compaction)
  -> ModelRunner (turn loop)
      -> OllamaClient (HTTP transport)
  -> SessionConfig callbacks (token stream, message sink, tool bridge, error sink)
```

Core design rule: prefer data and callback seams over class proliferation.

## Public API and lifecycle contracts

`Magenta` is the runtime entry surface:

- `startBaseSession(alias, sessionConfig)`
- `startSession(agentId, alias, sessionConfig)`
- `resumeSession(sessionId)`
- `forkSession(sourceSessionId, alias)`
- `forkSession(sourceSessionId, alias, sessionConfigOverride)`
- `runSessionTurn(sessionId, sessionInput)`
- `runUserTurn(sessionId, userInput)`

Lifecycle guarantees:

- `start*` validates enabled agent/model references and creates a new UUID session.
- `resume` is UUID-only and in-memory only.
- `fork` clones context into a new session ID; config inherits unless override provided.
- `runSessionTurn` resumes session first, compacts if needed, appends persisted input, then runs model turn loop.
- `runUserTurn` is a compatibility helper that delegates to `runSessionTurn` with `SessionInput.UserMessageInput`.

## Callback contract (`SessionConfig`)

`SessionConfig` fields:

- `onMessageAppendedHook: Consumer<SessionMessage>`
- `onUserMsgHook`, `onAssistantMsgHook`, `onToolMsgHook`, `onSystemMsgHook`, `onSummaryMsgHook`, `onInboundMsgHook`
- `onMessageInputHook: Consumer<SessionInput.MessageInput>`
- `onEventInputHook: Consumer<SessionInput.EventInput>`
- `onTokenStreamHook: Consumer<String>`
- `onStreamingResponseConsumer: Consumer<String>`
- `onFullResponseConsumer: Consumer<String>`
- `toolBridge: Function<ToolRequest, ToolResult>`
- `onErrorHook: Consumer<Throwable>`
- `emitStreamingCompletionToFullResponse: boolean`
- `blockingOnly: boolean`
- `toolsEnabled: boolean`

Default behavior:

- no-op message/token/error callbacks
- no-op output consumers
- `toolBridge` returns `ToolResult.notHandled(...)`
- `emitStreamingCompletionToFullResponse = true`
- `blockingOnly = false`, `toolsEnabled = true`

## Turn execution behavior

`runSessionTurn` flow:

1. `SessionManager.resume(sessionId)`
2. `ContextManager.compactIfNeeded(...)`
3. Emit input callback (`onMessageInput` or `onEventInput`)
4. Append persisted input (`UserMsg` for `SessionInput.UserMessageInput`, `InboundMsg` otherwise)
5. `ModelRunner.runTurn(session, maxTurns)`
6. On any throwable, invoke `SessionConfig.onError` and rethrow.

`ModelRunner.runTurn` flow:

1. Build request from full typed context snapshot.
3. Choose mode:
   - blocking if `blockingOnly`, or tool loop active, or model streaming unsupported
   - streaming otherwise
4. Call `OllamaClient`.
5. In streaming mode, emit `onTokenStreamHook` and `onStreamingResponseConsumer` per token chunk.
6. Append `AssistantMsg` with parsed tool calls; emit message callbacks.
7. Emit full response to `onFullResponseConsumer`:
   - always for blocking turns
   - for streaming turns when `emitStreamingCompletionToFullResponse` is true
8. If no tool calls (or tools disabled), return assistant text.
9. Otherwise call `toolBridge` per tool call, append `ToolMsg`, emit callbacks, and continue loop.
10. Stop on first no-tool assistant response or when max iterations reached.

Empty/null model text normalization: `"."`.

## Compaction behavior

Trigger: estimated tokens > `model.compactThreshold`.

Strategy:

- `summarize`: summarize older messages, keep recent tail, insert `SummaryMsg`, fallback when needed
- default fallback: `rolling_window`

Summarize fallback cases:

- context too small to summarize
- summarizer returns null/blank
- summarized output still exceeds target tokens

## Known constraints (implemented behavior)

- Session registry is in-memory only.
- `ContextManager.storeContext(...)` is currently a no-op seam.
- Token estimation is heuristic (`length / 4` minimum 1), not tokenizer-accurate.
- Only Ollama transport is implemented.
- Tool loop is callback-owned policy; runtime does not enforce authorization.
- Session route registry is in-memory only.

## Integration examples

Terminal streaming:

```java
SessionConfig cfg = SessionConfig.builder()
        .onTokenStreamHook(System.out::print)
        .onMessageAppendedHook(msg -> eventBus.publish("message", msg))
        .build();
```

Blocking deterministic mode:

```java
SessionConfig cfg = SessionConfig.builder()
        .blockingOnly(true)
        .toolsEnabled(false)
        .build();
```

Security-wrapped tool bridge:

```java
SessionConfig cfg = SessionConfig.builder()
        .toolsEnabled(true)
        .toolBridge(req -> {
            if (!policyAllows(req.toolCall())) {
                return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), "Denied by policy");
            }
            String out = executeTool(req.toolCall().name(), req.toolCall().argumentsJson());
            return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), out);
        })
        .build();
```

See `20-integration-patterns.md` and `21-sequence-walkthroughs.md` for fuller flows.

## Documentation maintenance

When runtime behavior changes:

1. Update this guide and any affected deep-dive docs.
2. Update `docs/internal/00-index.md` if docs are added/removed.
3. Run the checklist in `90-documentation-quality-checklist.md`.
4. Add `.internal-dev/changelogs/<date>-<topic>.md` entry.
5. Record code-doc mismatches explicitly if they remain unresolved.
