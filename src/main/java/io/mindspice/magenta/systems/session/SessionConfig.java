package io.mindspice.magenta.systems.session;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SessionConfig {

    private final Consumer<SessionMessage> onMessageAppended;
    private final Consumer<SessionMessage.UserMsg> onUserMsg;
    private final Consumer<SessionMessage.AssistantMsg> onAssistantMsg;
    private final Consumer<SessionMessage.ToolMsg> onToolMsg;
    private final Consumer<SessionMessage.SystemMsg> onSystemMsg;
    private final Consumer<SessionMessage.SummaryMsg> onSummaryMsg;
    private final Consumer<SessionMessage.InboundMsg> onInboundMsg;
    private final Consumer<SessionInput.MessageInput> onMessageInput;
    private final Consumer<SessionInput.EventInput> onEventInput;
    private final Consumer<String> onTokenStream;
    private final Function<ToolRequest, ToolResult> toolBridge;
    private final Consumer<Throwable> onError;
    private final boolean blockingOnly;
    private final boolean toolsEnabled;

    private SessionConfig(
            Consumer<SessionMessage> onMessageAppended,
            Consumer<SessionMessage.UserMsg> onUserMsg,
            Consumer<SessionMessage.AssistantMsg> onAssistantMsg,
            Consumer<SessionMessage.ToolMsg> onToolMsg,
            Consumer<SessionMessage.SystemMsg> onSystemMsg,
            Consumer<SessionMessage.SummaryMsg> onSummaryMsg,
            Consumer<SessionMessage.InboundMsg> onInboundMsg,
            Consumer<SessionInput.MessageInput> onMessageInput,
            Consumer<SessionInput.EventInput> onEventInput,
            Consumer<String> onTokenStream,
            Function<ToolRequest, ToolResult> toolBridge,
            Consumer<Throwable> onError,
            boolean blockingOnly,
            boolean toolsEnabled
    ) {
        this.onMessageAppended = onMessageAppended;
        this.onUserMsg = onUserMsg;
        this.onAssistantMsg = onAssistantMsg;
        this.onToolMsg = onToolMsg;
        this.onSystemMsg = onSystemMsg;
        this.onSummaryMsg = onSummaryMsg;
        this.onInboundMsg = onInboundMsg;
        this.onMessageInput = onMessageInput;
        this.onEventInput = onEventInput;
        this.onTokenStream = onTokenStream;
        this.toolBridge = toolBridge;
        this.onError = onError;
        this.blockingOnly = blockingOnly;
        this.toolsEnabled = toolsEnabled;
    }

    public static SessionConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Consumer<SessionMessage> onMessageAppended() {
        return onMessageAppended;
    }

    @Deprecated(since = "0.1.0", forRemoval = false)
    public Consumer<SessionMessage> onMessageStored() {
        return onMessageAppended;
    }

    public Consumer<SessionMessage.UserMsg> onUserMsg() {
        return onUserMsg;
    }

    public Consumer<SessionMessage.AssistantMsg> onAssistantMsg() {
        return onAssistantMsg;
    }

    public Consumer<SessionMessage.ToolMsg> onToolMsg() {
        return onToolMsg;
    }

    public Consumer<SessionMessage.SystemMsg> onSystemMsg() {
        return onSystemMsg;
    }

    public Consumer<SessionMessage.SummaryMsg> onSummaryMsg() {
        return onSummaryMsg;
    }

    public Consumer<SessionMessage.InboundMsg> onInboundMsg() {
        return onInboundMsg;
    }

    public Consumer<SessionInput.MessageInput> onMessageInput() {
        return onMessageInput;
    }

    public Consumer<SessionInput.EventInput> onEventInput() {
        return onEventInput;
    }

    public Consumer<String> onTokenStream() {
        return onTokenStream;
    }

    public Function<ToolRequest, ToolResult> toolBridge() {
        return toolBridge;
    }

    public Consumer<Throwable> onError() {
        return onError;
    }

    public boolean blockingOnly() {
        return blockingOnly;
    }

    public boolean toolsEnabled() {
        return toolsEnabled;
    }

    public void emitMessageAppended(SessionMessage message) {
        onMessageAppended.accept(message);
        switch (message) {
            case SessionMessage.UserMsg userMsg -> onUserMsg.accept(userMsg);
            case SessionMessage.AssistantMsg assistantMsg -> onAssistantMsg.accept(assistantMsg);
            case SessionMessage.ToolMsg toolMsg -> onToolMsg.accept(toolMsg);
            case SessionMessage.SystemMsg systemMsg -> onSystemMsg.accept(systemMsg);
            case SessionMessage.SummaryMsg summaryMsg -> onSummaryMsg.accept(summaryMsg);
            case SessionMessage.InboundMsg inboundMsg -> onInboundMsg.accept(inboundMsg);
        }
    }

    public void emitInputReceived(SessionInput input) {
        switch (input) {
            case SessionInput.MessageInput messageInput -> onMessageInput.accept(messageInput);
            case SessionInput.EventInput eventInput -> onEventInput.accept(eventInput);
        }
    }

    public static final class Builder {
        private Consumer<SessionMessage> onMessageAppended = msg -> {};
        private Consumer<SessionMessage.UserMsg> onUserMsg = msg -> {};
        private Consumer<SessionMessage.AssistantMsg> onAssistantMsg = msg -> {};
        private Consumer<SessionMessage.ToolMsg> onToolMsg = msg -> {};
        private Consumer<SessionMessage.SystemMsg> onSystemMsg = msg -> {};
        private Consumer<SessionMessage.SummaryMsg> onSummaryMsg = msg -> {};
        private Consumer<SessionMessage.InboundMsg> onInboundMsg = msg -> {};
        private Consumer<SessionInput.MessageInput> onMessageInput = input -> {};
        private Consumer<SessionInput.EventInput> onEventInput = input -> {};
        private Consumer<String> onTokenStream = token -> {};
        private Function<ToolRequest, ToolResult> toolBridge = req -> ToolResult.notHandled(req.toolCall());
        private Consumer<Throwable> onError = err -> {};
        private boolean blockingOnly = false;
        private boolean toolsEnabled = true;

        public Builder onMessageAppended(Consumer<SessionMessage> callback) {
            this.onMessageAppended = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }


        public Builder onUserMsg(Consumer<SessionMessage.UserMsg> callback) {
            this.onUserMsg = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onAssistantMsg(Consumer<SessionMessage.AssistantMsg> callback) {
            this.onAssistantMsg = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onToolMsg(Consumer<SessionMessage.ToolMsg> callback) {
            this.onToolMsg = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onSystemMsg(Consumer<SessionMessage.SystemMsg> callback) {
            this.onSystemMsg = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onSummaryMsg(Consumer<SessionMessage.SummaryMsg> callback) {
            this.onSummaryMsg = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onInboundMsg(Consumer<SessionMessage.InboundMsg> callback) {
            this.onInboundMsg = Objects.requireNonNullElse(callback, msg -> {});
            return this;
        }

        public Builder onMessageInput(Consumer<SessionInput.MessageInput> callback) {
            this.onMessageInput = Objects.requireNonNullElse(callback, input -> {});
            return this;
        }

        public Builder onEventInput(Consumer<SessionInput.EventInput> callback) {
            this.onEventInput = Objects.requireNonNullElse(callback, input -> {});
            return this;
        }

        public Builder onTokenStream(Consumer<String> callback) {
            this.onTokenStream = Objects.requireNonNullElse(callback, token -> {});
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
                    onMessageAppended,
                    onUserMsg,
                    onAssistantMsg,
                    onToolMsg,
                    onSystemMsg,
                    onSummaryMsg,
                    onInboundMsg,
                    onMessageInput,
                    onEventInput,
                    onTokenStream,
                    toolBridge,
                    onError,
                    blockingOnly,
                    toolsEnabled
            );
        }
    }
}
