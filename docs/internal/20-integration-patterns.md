# Integration Patterns

Implementation-oriented patterns for embedding the runtime.

## 1) Terminal streaming via output routes

```java
RuntimeConfig config = RuntimeConfig.loadDefault();
Magenta magenta = new Magenta(config);

SessionHandle handle = magenta.startBaseSession(
        "terminal",
        new SessionConfig(
                SessionParams.ofStreaming(true),
                request -> ToolResult.notHandled(request.toolCall()),
                error -> System.err.println("session error: " + error.getMessage())
        )
);

magenta.registerInputRoute(
        handle,
        InputRoutePolicy.defaults(),
        InputRoutingEvent.Level.ERROR,
        event -> uiBus.publish("input-routing-event", event)
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
                .allowedOutputTags(Set.of(SessionOutput.FinalOutput.FILTER_TAG))
                .build(),
        event -> uiBus.publish("assistant-final", event)
);
```

## 3) Tool bridge with policy wrapper

```java
SessionConfig secured = new SessionConfig(
        SessionParams.ofStreaming(true), // tools enabled by default in this mode
        req -> {
            if (!securityPolicy.allowed(req)) {
                return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), "Denied");
            }
            String output = toolExecutor.run(req.toolCall().name(), req.toolCall().argumentsJson());
            return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), output);
        },
        error -> System.err.println("session error: " + error.getMessage())
);
```

## 4) Blocking deterministic mode

```java
SessionConfig deterministic = new SessionConfig(
        SessionParams.ofBlocking(false), // blocking + tools disabled + streaming disabled
        request -> ToolResult.notHandled(request.toolCall()),
        error -> System.err.println("session error: " + error.getMessage())
);
```

## 5) Session close hygiene

```java
magenta.unregisterOutputRoute(handle, routeId);
magenta.unregisterInputRoute(handle);
magenta.closeSession(handle);
```

Session close also auto-prunes any remaining routes.
