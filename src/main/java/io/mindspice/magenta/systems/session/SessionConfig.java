package io.mindspice.magenta.systems.session;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SessionConfig {

    private final Consumer<SessionMessage> onMessageAppendedHook;
    private final Consumer<SessionMessage.UserMsg> onUserMsgHook;
    private final Consumer<SessionMessage.AssistantMsg> onAssistantMsgHook;
    private final Consumer<SessionMessage.ToolMsg> onToolMsgHook;
    private final Consumer<SessionMessage.SystemMsg> onSystemMsgHook;
    private final Consumer<SessionMessage.SummaryMsg> onSummaryMsgHook;
    private final Consumer<SessionMessage.InboundMsg> onInboundMsgHook;
    private final Consumer<SessionInput.MessageInput> onMessageInputHook;
    private final Consumer<SessionInput.EventInput> onEventInputHook;
    private final Consumer<String> onTokenStreamHook;
    private final Consumer<String> onStreamingResponseConsumer;
    private final Consumer<String> onFullResponseConsumer;
    private final Function<ToolRequest, ToolResult> toolBridge;
    private final Consumer<Throwable> onErrorHook;
    private final boolean emitStreamingCompletionToFullResponse;
    private final boolean blockingOnly;
    private final boolean toolsEnabled;

    private SessionConfig(
            Consumer<SessionMessage> onMessageAppendedHook,
            Consumer<SessionMessage.UserMsg> onUserMsgHook,
            Consumer<SessionMessage.AssistantMsg> onAssistantMsgHook,
            Consumer<SessionMessage.ToolMsg> onToolMsgHook,
            Consumer<SessionMessage.SystemMsg> onSystemMsgHook,
            Consumer<SessionMessage.SummaryMsg> onSummaryMsgHook,
            Consumer<SessionMessage.InboundMsg> onInboundMsgHook,
            Consumer<SessionInput.MessageInput> onMessageInputHook,
            Consumer<SessionInput.EventInput> onEventInputHook,
            Consumer<String> onTokenStreamHook,
            Consumer<String> onStreamingResponseConsumer,
            Consumer<String> onFullResponseConsumer,
            Function<ToolRequest, ToolResult> toolBridge,
            Consumer<Throwable> onErrorHook,
            boolean emitStreamingCompletionToFullResponse,
            boolean blockingOnly,
            boolean toolsEnabled
    ) {
        this.onMessageAppendedHook = onMessageAppendedHook;
        this.onUserMsgHook = onUserMsgHook;
        this.onAssistantMsgHook = onAssistantMsgHook;
        this.onToolMsgHook = onToolMsgHook;
        this.onSystemMsgHook = onSystemMsgHook;
        this.onSummaryMsgHook = onSummaryMsgHook;
        this.onInboundMsgHook = onInboundMsgHook;
        this.onMessageInputHook = onMessageInputHook;
        this.onEventInputHook = onEventInputHook;
        this.onTokenStreamHook = onTokenStreamHook;
        this.onStreamingResponseConsumer = onStreamingResponseConsumer;
        this.onFullResponseConsumer = onFullResponseConsumer;
        this.toolBridge = toolBridge;
        this.onErrorHook = onErrorHook;
        this.emitStreamingCompletionToFullResponse = emitStreamingCompletionToFullResponse;
        this.blockingOnly = blockingOnly;
        this.toolsEnabled = toolsEnabled;
    }

    public static SessionConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Consumer<SessionMessage> onMessageAppendedHook() {
        return onMessageAppendedHook;
    }

    public Consumer<SessionMessage.UserMsg> onUserMsgHook() {
        return onUserMsgHook;
    }

    public Consumer<SessionMessage.AssistantMsg> onAssistantMsgHook() {
        return onAssistantMsgHook;
    }

    public Consumer<SessionMessage.ToolMsg> onToolMsgHook() {
        return onToolMsgHook;
    }

    public Consumer<SessionMessage.SystemMsg> onSystemMsgHook() {
        return onSystemMsgHook;
    }

    public Consumer<SessionMessage.SummaryMsg> onSummaryMsgHook() {
        return onSummaryMsgHook;
    }

    public Consumer<SessionMessage.InboundMsg> onInboundMsgHook() {
        return onInboundMsgHook;
    }

    public Consumer<SessionInput.MessageInput> onMessageInputHook() {
        return onMessageInputHook;
    }

    public Consumer<SessionInput.EventInput> onEventInputHook() {
        return onEventInputHook;
    }

    public Consumer<String> onTokenStreamHook() {
        return onTokenStreamHook;
    }

    public Consumer<String> onStreamingResponseConsumer() {
        return onStreamingResponseConsumer;
    }

    public Consumer<String> onFullResponseConsumer() {
        return onFullResponseConsumer;
    }

    public Function<ToolRequest, ToolResult> toolBridge() {
        return toolBridge;
    }

    public Consumer<Throwable> onErrorHook() {
        return onErrorHook;
    }

    public boolean emitStreamingCompletionToFullResponse() {
        return emitStreamingCompletionToFullResponse;
    }

    public boolean blockingOnly() {
        return blockingOnly;
    }

    public boolean toolsEnabled() {
        return toolsEnabled;
    }

    public void emitMessageAppended(SessionMessage message) {
        onMessageAppendedHook.accept(message);
        switch (message) {
            case SessionMessage.UserMsg userMsg -> onUserMsgHook.accept(userMsg);
            case SessionMessage.AssistantMsg assistantMsg -> onAssistantMsgHook.accept(assistantMsg);
            case SessionMessage.ToolMsg toolMsg -> onToolMsgHook.accept(toolMsg);
            case SessionMessage.SystemMsg systemMsg -> onSystemMsgHook.accept(systemMsg);
            case SessionMessage.SummaryMsg summaryMsg -> onSummaryMsgHook.accept(summaryMsg);
            case SessionMessage.InboundMsg inboundMsg -> onInboundMsgHook.accept(inboundMsg);
        }
    }

    public void emitInputReceived(SessionInput input) {
        switch (input) {
            case SessionInput.MessageInput messageInput -> onMessageInputHook.accept(messageInput);
            case SessionInput.EventInput eventInput -> onEventInputHook.accept(eventInput);
        }
    }

    public void emitStreamingResponse(String token) {
        onStreamingResponseConsumer.accept(token);
    }

    public void emitFullResponse(String fullText, boolean cameFromStreaming) {
        if (cameFromStreaming && !emitStreamingCompletionToFullResponse) {
            return;
        }
        onFullResponseConsumer.accept(fullText);
    }

    public static final class Builder {
        private Consumer<SessionMessage> onMessageAppendedHook = msg -> {};
        private Consumer<SessionMessage.UserMsg> onUserMsgHook = msg -> {};
        private Consumer<SessionMessage.AssistantMsg> onAssistantMsgHook = msg -> {};
        private Consumer<SessionMessage.ToolMsg> onToolMsgHook = msg -> {};
        private Consumer<SessionMessage.SystemMsg> onSystemMsgHook = msg -> {};
        private Consumer<SessionMessage.SummaryMsg> onSummaryMsgHook = msg -> {};
        private Consumer<SessionMessage.InboundMsg> onInboundMsgHook = msg -> {};
        private Consumer<SessionInput.MessageInput> onMessageInputHook = input -> {};
        private Consumer<SessionInput.EventInput> onEventInputHook = input -> {};
        private Consumer<String> onTokenStreamHook = token -> {};
        private Consumer<String> onStreamingResponseConsumer = token -> {};
        private Consumer<String> onFullResponseConsumer = text -> {};
        private Function<ToolRequest, ToolResult> toolBridge = req -> ToolResult.notHandled(req.toolCall());
        private Consumer<Throwable> onErrorHook = err -> {};
        private boolean emitStreamingCompletionToFullResponse = true;
        private boolean blockingOnly = false;
        private boolean toolsEnabled = true;

        public Builder onMessageAppendedHook(Consumer<SessionMessage> callback) {
            this.onMessageAppendedHook = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onUserMsgHook(Consumer<SessionMessage.UserMsg> callback) {
            this.onUserMsgHook = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onAssistantMsgHook(Consumer<SessionMessage.AssistantMsg> callback) {
            this.onAssistantMsgHook = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onToolMsgHook(Consumer<SessionMessage.ToolMsg> callback) {
            this.onToolMsgHook = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onSystemMsgHook(Consumer<SessionMessage.SystemMsg> callback) {
            this.onSystemMsgHook = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onSummaryMsgHook(Consumer<SessionMessage.SummaryMsg> callback) {
            this.onSummaryMsgHook = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onInboundMsgHook(Consumer<SessionMessage.InboundMsg> callback) {
            this.onInboundMsgHook = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onMessageInputHook(Consumer<SessionInput.MessageInput> callback) {
            this.onMessageInputHook = Objects.requireNonNullElse(callback, input -> {});
            return this;
        }

        public Builder onEventInputHook(Consumer<SessionInput.EventInput> callback) {
            this.onEventInputHook = Objects.requireNonNullElse(callback, input -> {});
            return this;
        }

        public Builder onTokenStreamHook(Consumer<String> callback) {
            this.onTokenStreamHook = Objects.requireNonNullElse(callback, token -> {});
            return this;
        }

        public Builder onStreamingResponseConsumer(Consumer<String> callback) {
            this.onStreamingResponseConsumer = Objects.requireNonNullElse(callback, token -> {});
            return this;
        }

        public Builder onFullResponseConsumer(Consumer<String> callback) {
            this.onFullResponseConsumer = Objects.requireNonNullElse(callback, text -> {});
            return this;
        }

        public Builder toolBridge(Function<ToolRequest, ToolResult> bridge) {
            this.toolBridge = Objects.requireNonNullElse(bridge, req -> ToolResult.notHandled(req.toolCall()));
            return this;
        }

        public Builder onErrorHook(Consumer<Throwable> callback) {
            this.onErrorHook = Objects.requireNonNullElse(callback, err -> {});
            return this;
        }

        public Builder emitStreamingCompletionToFullResponse(boolean enabled) {
            this.emitStreamingCompletionToFullResponse = enabled;
            return this;
        }

        public Builder blockingOnly(boolean blockingOnly) {
            this.blockingOnly = blockingOnly;
            return this;
        }

        public Builder toolsEnabled(boolean toolsEnabled) {
            this.toolsEnabled = toolsEnabled;
            return this;
        }

        public SessionConfig build() {
            return new SessionConfig(
                    onMessageAppendedHook,
                    onUserMsgHook,
                    onAssistantMsgHook,
                    onToolMsgHook,
                    onSystemMsgHook,
                    onSummaryMsgHook,
                    onInboundMsgHook,
                    onMessageInputHook,
                    onEventInputHook,
                    onTokenStreamHook,
                    onStreamingResponseConsumer,
                    onFullResponseConsumer,
                    toolBridge,
                    onErrorHook,
                    emitStreamingCompletionToFullResponse,
                    blockingOnly,
                    toolsEnabled
            );
        }
    }
}
