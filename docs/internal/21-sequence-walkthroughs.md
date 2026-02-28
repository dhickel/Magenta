# Sequence Walkthroughs

Narrative and flow-diagram walkthroughs for key runtime paths.

## 1) Startup and config resolution

Narrative:

1. Caller invokes `RuntimeConfig.loadDefault()` or `RuntimeConfig.load(path)`.
2. Runtime parses `magenta.yaml`, resolves include sets, and loads models/agents/prompts.
3. Runtime validates base/compaction agent and enabled graph references.
4. Caller constructs `Magenta`, which wires managers and model runner/client.

```text
Caller
  -> RuntimeConfig.load(...)
    -> parse root YAML
    -> resolve include files
    -> load models/agents/prompts
    -> validate graph
    <- RuntimeConfig
  -> new Magenta(config)
    -> new ContextManager
    -> new ModelRunner(new OllamaClient)
  -> new SessionManager(config, contextManager, turnSubmitter)
```

## 2) User turn with no tool calls

Narrative:

1. `runUserTurn` resumes session.
2. Context compaction check runs (no-op if within threshold).
3. `runUserTurn` delegates to `runSessionTurn` with `SessionInput.UserMessageInput`.
4. Runtime appends persisted `UserMsg`.
5. `ModelRunner` executes model request.
6. In streaming mode, each token is emitted to `onTokenStreamHook` and `onStreamingResponseConsumer`.
7. Assistant response has no tool calls; runtime appends `AssistantMsg`.
8. Full text is emitted to `onFullResponseConsumer` (stream replay controlled by config boolean).
9. Loop returns final assistant text.

```text
runUserTurn
  -> runSessionTurn(SessionInput.UserMessageInput)
  -> SessionManager.resume
  -> ContextManager.compactIfNeeded
  -> append UserMsg
  -> ModelRunner.runTurn
      -> ollama chat (blocking or streaming)
      -> stream token callbacks (streaming mode)
      -> append AssistantMsg (no tools)
      -> full response callback
      <- return assistant text
```

## 3) User turn with tool call loop

Narrative:

1. First assistant response contains tool calls.
2. Runtime invokes `toolBridge` for each call and appends `ToolMsg` entries.
3. Runtime re-enters next turn iteration with expanded context.
4. Loop exits when assistant returns with no tool calls or max turns reached.

```text
iteration 1: UserMsg -> AssistantMsg(toolCalls)
           -> ToolRequest -> toolBridge -> ToolMsg
iteration 2+: context includes ToolMsg results
           -> AssistantMsg(...)
exit: no tool calls OR maxIterations reached
```

## 4) Error callback emission

Narrative:

1. `runSessionTurn` wraps resume/compaction/model execution in catch-all handling.
2. On exception, runtime calls `sessionConfig.onError`.
3. Runtime rethrows the original exception.

```text
runSessionTurn
  -> try { resume + compact + model loop }
  -> catch (Throwable t)
      -> onErrorHook(t)
      -> throw t
```
## 5) Compaction summarize path with fallback

Narrative:

1. Context exceeds `compactThreshold`.
2. Strategy resolves to `summarize`.
3. Older segment is summarized, recent tail retained.
4. If summary is empty or still too large, fallback rolling window is applied.

```text
compactIfNeeded
  -> tokens > threshold ?
    -> yes: strategy = summarize
        -> summarizer(old segment)
        -> summary blank? yes -> rolling_window fallback
        -> summary present -> build [system?, SummaryMsg, recent]
        -> too large? yes -> rolling_window fallback
  -> replace context messages
```
