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
      -> append input message (if addToContext)
      -> SessionRouter.emit(ContextMessageOutput)
      -> compute shouldStream
      -> ModelRunner.runTurn(...)
          -> ollama chat (blocking or streaming)
          -> emit StreamedOutput events (if streaming)
          -> append AssistantMsg
          -> emit ContextMessageOutput + FinalOutput
          -> return
```

## 3) Tool loop turn

```text
iteration 1: AssistantMsg(toolCalls)
           -> toolBridge call(s)
           -> SecurityManager.authorize
              (descriptor-driven validate path/command/url + policy decision)
           -> allowed: ToolManager.execute + normalize
           -> denied: structured failure payload (no tool execution)
           -> append ToolMsg
           -> emit ContextMessageOutput + ToolMessageOutput
iteration 2+: context includes ToolMsg results
exit: no tool calls OR maxIterations reached
```

## 4) Streaming gating path

```text
SessionConfig.streamingEnabled == false
  -> streamed output route registration rejected
  -> turn executes blocking

SessionConfig.streamingEnabled == true
  -> Magenta checks SessionRouter.hasStreamedOutputListeners(handle)
  -> turn streams only when streamed-output listeners exist
```

## 5) Error handling

```text
SessionManager.submitFromRoute
  -> try executeTurn
  -> catch(Throwable)
      -> sessionConfig.onError(t)
      -> swallow for external ingress stability
```
