package io.mindspice.magenta.runtime.session.config;

import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.SessionException;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;
import java.util.function.Function;

public final class SessionConfig {
    private final SessionParams params;
    private final Function<ToolRequest, ToolResult> toolBridge;
    private final RoutingEventLevel routingEventLevel;
    private final Consumer<RoutingEvent> onRouting;
    private final Consumer<SessionException> onError;

    public SessionConfig(
            @NonNull SessionParams params,
            @NonNull Function<ToolRequest, ToolResult> toolBridge,
            @NonNull RoutingEventLevel routingEventLevel,
            @NonNull Consumer<RoutingEvent> onRouting,
            @NonNull Consumer<SessionException> onError
    ) {
        this.params = params;
        this.toolBridge = toolBridge;
        this.routingEventLevel = routingEventLevel;
        this.onRouting = onRouting;
        this.onError = onError;
    }

    public SessionConfig(
            @NonNull SessionParams params,
            @NonNull Function<ToolRequest, ToolResult> toolBridge,
            @NonNull Consumer<SessionException> onError
    ) {
        this(params, toolBridge, RoutingEventLevel.NONE, null, onError);
    }

    public Function<ToolRequest, ToolResult> toolBridge() { return toolBridge; }

    public RoutingEventLevel routingEventLevel() { return routingEventLevel; }

    public Consumer<RoutingEvent> onRouting() { return onRouting; }

    public Consumer<SessionException> onError() { return onError; }

    public SessionParams params() { return params; }
}
