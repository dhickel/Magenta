# Integration Patterns

Implementation-oriented patterns for embedding the current runtime.

## 1) Terminal streaming runner

```java
RuntimeConfig config = RuntimeConfig.loadDefault();
Magenta magenta = new Magenta(config);

SessionConfig sessionConfig = SessionConfig.builder()
        .onTokenStreamHook(System.out::print)
        .onStreamingResponseConsumer(token -> uiBus.publish("stream-token", token))
        .onFullResponseConsumer(text -> uiBus.publish("final", text))
        .onMessageAppendedHook(msg -> {})
        .build();

Session session = magenta.startBaseSession("terminal", sessionConfig);
String finalText = magenta.runUserTurn(session.sessionId(), "Summarize this repository structure.");
System.out.println("\nFinal: " + finalText);
```

Use when live incremental output is needed.

## 2) UI event fanout pattern

```java
SessionConfig sessionConfig = SessionConfig.builder()
        .onTokenStreamHook(token -> uiBus.publish("token", token))
        .onStreamingResponseConsumer(token -> uiBus.publish("stream-token", token))
        .onFullResponseConsumer(text -> uiBus.publish("assistant-final", text))
        .emitStreamingCompletionToFullResponse(true)
        .onMessageAppendedHook(msg -> uiBus.publish("message", msg))
        .onAssistantMsgHook(msg -> uiBus.publish("assistant", msg.content()))
        .build();
```

Use when frontend state should mirror canonical runtime message writes.

## 3) Autonomous execution mode

```java
SessionConfig autonomous = SessionConfig.builder()
        .blockingOnly(false)
        .toolsEnabled(true)
        .toolBridge(agentToolExecutor::execute)
        .onMessageAppendedHook(eventStore::append)
        .build();
```

Use when tool loop automation is desired and caller owns policy and execution control.

## 4) Security-wrapped tool bridge

```java
SessionConfig secured = SessionConfig.builder()
        .toolsEnabled(true)
        .toolBridge(req -> {
            if (!securityPolicy.allowed(req)) {
                return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), "Denied");
            }
            String output = toolExecutor.run(req.toolCall().name(), req.toolCall().argumentsJson());
            return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), output);
        })
        .build();
```

Use when deterministic authorization is required before any side effect.

## 5) External input delivery

```java
Session session = magenta.startBaseSession("agent-a", SessionConfig.defaults());
SessionRoutePolicy policy = new SessionRoutePolicy(
        Set.of(SessionInput.MessageInputKind.BUS_MESSAGE),
        Set.of(SessionInput.EventInputKind.SYSTEM_EVENT),
        Set.of("agent-b", "system")
);

Consumer<SessionInput.MessageInput> msgAdapter = magenta.sessionManager()
        .messageConsumerFor(session.sessionId(), policy);

msgAdapter.accept(new SessionInput.BusMessageInput(
        "Task complete. Review results.",
        "agent-b",
        "corr-123",
        Map.of("topic", "review"),
        true
));
```

Use when session-owned adapters are needed with policy gating before turn execution.
## 6) Blocking-only deterministic mode

```java
SessionConfig deterministic = SessionConfig.builder()
        .blockingOnly(true)
        .toolsEnabled(false)
        .build();
```

Use when reproducibility and simple control flow matter more than stream UX.

## 7) Safe defaults baseline

```java
SessionConfig baseline = SessionConfig.defaults();
```

Behavior:

- streaming allowed if model supports it
- tool loop enabled, but default bridge returns `Tool not handled`
- no callback side effects by default
- streaming completion is replayed to full response consumer by default
