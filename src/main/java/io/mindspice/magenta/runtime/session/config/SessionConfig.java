package io.mindspice.magenta.runtime.session.config;

import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.function.Consumer;
import java.util.function.Function;

public final class SessionConfig {
    private final SessionParams params;
    private final Function<ToolRequest, ToolResult> toolBridge;
    private final Consumer<Throwable> onError;

    private SessionConfig(
            SessionParams params,
            Function<ToolRequest, ToolResult> toolBridge,
            Consumer<Throwable> onError
    ) {
        this.params = params;
        this.toolBridge = toolBridge;
        this.onError = onError;
    }


    public Function<ToolRequest, ToolResult> toolBridge() { return toolBridge; }

    public Consumer<Throwable> onError() { return onError; }

    public SessionParams params() { return params; }

}
