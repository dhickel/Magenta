# Integration Patterns

Implementation-oriented patterns for embedding the runtime.

## 1) Terminal streaming via output routes

```java
RuntimeConfig config = RuntimeConfig.loadDefault();
Magenta magenta = new Magenta(config);

SessionHandle handle = magenta.startBaseSession(
        "terminal",
        SessionConfig.builder().streamingEnabled(true).build()
);

magenta.registerInputRoute(
        handle,
        InputRoutePolicy.defaults(),
        InputRouteReportLevel.ERROR,
        report -> uiBus.publish("route-report", report)
);

magenta.registerOutputRoute(
        handle,
        OutputRoutePolicy.defaults(),
        event -> uiBus.publish("session-output", event)
);

magenta.getMessageInputConsumer(handle).accept(SessionInput.userMessage("Summarize this repository."));
```

## 2) Final-only UI updates

```java
magenta.registerOutputRoute(
        handle,
        OutputRoutePolicy.builder()
                .eventKinds(Set.of(SessionOutputEvent.Kind.FINAL))
                .build(),
        event -> uiBus.publish("assistant-final", event)
);
```

## 3) Tool bridge with policy wrapper

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

## 4) Blocking deterministic mode

```java
SessionConfig deterministic = SessionConfig.builder()
        .blockingOnly(true)
        .toolsEnabled(false)
        .streamingEnabled(false)
        .build();
```

## 5) Session close hygiene

```java
magenta.unregisterOutputRoute(handle, routeId);
magenta.unregisterInputRoute(handle);
magenta.closeSession(handle);
```

Session close also auto-prunes any remaining routes.
