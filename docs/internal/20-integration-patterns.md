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
                event -> uiBus.publish("routing-event", event),
                security -> uiBus.publish("security-event", security),
                error -> System.err.println("session error: " + error.getMessage())
        )
);

magenta.addInputRoute(handle, InputRoutePolicy.defaults());
magenta.addOutputRoute(handle, OutputRoutePolicy.defaults(), event -> uiBus.publish("session-output", event));
```

## 2) Session tool policy update (approved roots + command rules)

```java
SessionHandle handle = magenta.startBaseSession("secured");
SecurityManager.ToolPolicy current = magenta.toolPolicy(handle);

SecurityManager.ToolPolicy hardened = new SecurityManager.ToolPolicy(
        RuntimeConfig.SecurityMode.APPROVE_ALL,
        current.devYoloOverride(),
        current.allowedTools(),
        current.deniedTools(),
        List.of("."),                      // approved roots
        Set.of("rg", "git"),             // first-token allow-list
        current.webAccess(),
        List.of(
                new SecurityManager.CommandRule("allow-rg", RuntimeConfig.SecurityRuleAction.ALLOW, List.of("rg"), ""),
                new SecurityManager.CommandRule("prompt-git", RuntimeConfig.SecurityRuleAction.PROMPT, List.of("git"), "review required")
        )
);

magenta.setToolPolicy(handle, hardened);
```

## 3) Security-aware custom callback wiring

```java
SessionConfig sessionConfig = new SessionConfig(
        SessionParams.ofStreaming(true),
        request -> ToolResult.notHandled(request.toolCall()),
        RoutingEventLevel.ALL,
        routingEvent -> routingAudit.write(routingEvent),
        securityEvent -> securityAudit.write(securityEvent),
        sessionException -> errorAudit.write(sessionException)
);

SessionHandle handle = magenta.startBaseSession("audited", sessionConfig);
```

## 4) Final-only UI updates

```java
RouteHandle finalOnlyRoute = magenta.addOutputRoute(
        handle,
        OutputRoutePolicy.builder()
                .allowedOutputTags(Set.of(SessionOutput.FinalOutput.FILTER_TAG))
                .build(),
        event -> uiBus.publish("assistant-final", event)
);
```

## 5) Internal Lanterna terminal UI bootstrap

```java
RuntimeConfig runtimeConfig = RuntimeConfig.loadDefault();
ToolApprovalPromptAdapter approval = new ToolApprovalPromptAdapter();
Magenta magenta = new Magenta(runtimeConfig, null, approval);

TerminalUiConfig uiConfig = TerminalUiConfig.defaults();
TerminalUiRuntime runtime = TerminalUiBootstrap.bootstrap(magenta, uiConfig, approval);
runtime.runLoop();
```

## 6) Session close hygiene

```java
magenta.removeRoute(finalOnlyRoute);
magenta.closeSession(handle);
```

Session close also prunes any remaining routes and clears session security policy state.
