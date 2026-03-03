# Sequence Walkthroughs

## 1) Startup and runtime construction

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
    -> new SessionManager(...)
    -> new SessionRouter(...)
```

## 2) Routed user turn (no tools)

```text
messageConsumer.accept(userInput)
  -> SessionRouter (liveness + input policy)
  -> SessionManager.submitFromRoute
  -> Magenta.executeTurn
      -> append input message (if persist)
      -> SessionRouter.emit(MessageAppended)
      -> compute shouldStream
      -> ModelRunner.runTurn(...)
          -> ollama chat (blocking or streaming)
          -> emit PartialToken events (if streaming)
          -> append AssistantMsg
          -> emit MessageAppended + AssistantFinal
          -> return
```

## 3) Tool loop turn

```text
iteration 1: AssistantMsg(toolCalls)
           -> toolBridge call(s)
           -> append ToolMsg
           -> emit MessageAppended + ToolMessageAppended
iteration 2+: context includes ToolMsg results
exit: no tool calls OR maxIterations reached
```

## 4) Streaming gating path

```text
SessionConfig.streamingEnabled == false
  -> partial output route registration rejected
  -> turn executes blocking

SessionConfig.streamingEnabled == true
  -> Magenta checks SessionRouter.hasPartialTokenListeners(handle)
  -> turn streams only when listeners exist
```

## 5) Error handling

```text
SessionManager.submitFromRoute
  -> try executeTurn
  -> catch(Throwable)
      -> sessionConfig.onError(t)
      -> swallow for external ingress stability
```
