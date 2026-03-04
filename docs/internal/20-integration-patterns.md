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
                RoutingEventLevel.FINAL,
                event -> uiBus.publish("input-routing-event", event),
                error -> System.err.println("session error: " + error.getMessage())
        )
);

magenta.addInputRoute(handle, InputRoutePolicy.defaults());

RouteHandle outputRoute = magenta.addOutputRoute(
        handle,
        OutputRoutePolicy.defaults(),
        event -> uiBus.publish("session-output", event)
);

magenta.messageInputConsumer(handle).accept(SessionInput.userMessage("Summarize this repository."));
```

## 2) Final-only UI updates

```java
RouteHandle finalOnlyRoute = magenta.addOutputRoute(
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
        SessionParams.ofStreaming(true),
        req -> {
            if (!securityPolicy.allowed(req)) {
                return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), "Denied");
            }
            String output = toolExecutor.run(req.toolCall().name(), req.toolCall().argumentsJson());
            return ToolResult.handled(req.toolCall().id(), req.toolCall().name(), output);
        },
        RoutingEventLevel.NONE,
        ignored -> {},
        error -> System.err.println("session error: " + error.getMessage())
);
```

## 4) Blocking deterministic mode

```java
SessionConfig deterministic = new SessionConfig(
        SessionParams.ofBlocking(false),
        request -> ToolResult.notHandled(request.toolCall()),
        RoutingEventLevel.NONE,
        ignored -> {},
        error -> System.err.println("session error: " + error.getMessage())
);
```

## 5) Session close hygiene

```java
magenta.removeRoute(outputRoute);
magenta.closeSession(handle);
```

Session close also auto-prunes any remaining routes.
