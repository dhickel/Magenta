package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SessionConfig {

    private final boolean blockingOnly;
    private final boolean toolsEnabled;
    private final boolean bypassSecurity;
    private final boolean streamingEnabled;
    private final Function<ToolRequest, ToolResult> toolBridge;
    private final Consumer<Throwable> onError;

    private SessionConfig(
            boolean blockingOnly,
            boolean toolsEnabled,
            boolean bypassSecurity,
            boolean streamingEnabled,
            Function<ToolRequest, ToolResult> toolBridge,
            Consumer<Throwable> onError
    ) {
        this.blockingOnly = blockingOnly;
        this.toolsEnabled = toolsEnabled;
        this.bypassSecurity = bypassSecurity;
        this.streamingEnabled = streamingEnabled;
        this.toolBridge = toolBridge;
        this.onError = onError;
    }

    public static SessionConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean blockingOnly() {
        return blockingOnly;
    }

    public boolean toolsEnabled() {
        return toolsEnabled;
    }

    public boolean bypassSecurity() {
        return bypassSecurity;
    }

    public boolean streamingEnabled() {
        return streamingEnabled;
    }

    public Function<ToolRequest, ToolResult> toolBridge() {
        return toolBridge;
    }

    public Consumer<Throwable> onError() {
        return onError;
    }

    public SessionConfigView toView() {
        return new SessionConfigView(blockingOnly, toolsEnabled, bypassSecurity, streamingEnabled);
    }

    public static final class Builder {
        private boolean blockingOnly = false;
        private boolean toolsEnabled = true;
        private boolean bypassSecurity = false;
        private boolean streamingEnabled = true;
        private Function<ToolRequest, ToolResult> toolBridge = req -> ToolResult.notHandled(req.toolCall());
        private Consumer<Throwable> onError = err -> {};

        public Builder blockingOnly(boolean blockingOnly) {
            this.blockingOnly = blockingOnly;
            return this;
        }

        public Builder toolsEnabled(boolean toolsEnabled) {
            this.toolsEnabled = toolsEnabled;
            return this;
        }

        public Builder bypassSecurity(boolean bypassSecurity) {
            this.bypassSecurity = bypassSecurity;
            return this;
        }

        public Builder streamingEnabled(boolean streamingEnabled) {
            this.streamingEnabled = streamingEnabled;
            return this;
        }

        public Builder toolBridge(Function<ToolRequest, ToolResult> bridge) {
            this.toolBridge = Objects.requireNonNullElse(bridge, req -> ToolResult.notHandled(req.toolCall()));
            return this;
        }

        public Builder onError(Consumer<Throwable> callback) {
            this.onError = Objects.requireNonNullElse(callback, err -> {});
            return this;
        }

        public SessionConfig build() {
            return new SessionConfig(
                    blockingOnly,
                    toolsEnabled,
                    bypassSecurity,
                    streamingEnabled,
                    toolBridge,
                    onError
            );
        }
    }
}
