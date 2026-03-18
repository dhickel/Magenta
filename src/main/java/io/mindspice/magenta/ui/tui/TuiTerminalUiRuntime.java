package io.mindspice.magenta.ui.tui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.events.SessionEventListenerHandle;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.prompt.PromptService;
import io.mindspice.magenta.ui.prompt.UiPromptRequest;
import io.mindspice.magenta.ui.prompt.UiPromptResponse;
import io.mindspice.magenta.ui.tui.chat.ChatBinding;
import io.mindspice.magenta.ui.tui.chat.ChatWindow;
import io.mindspice.magenta.ui.tui.chat.ChatWindowController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class TuiTerminalUiRuntime {
    private final RuntimeConfig runtimeConfig;
    private final Magenta magenta;
    private final TerminalUiConfig config;
    private final WorkspaceHost workspaceHost;
    private final TuiThemeRegistry themeRegistry;
    private final TuiApplication app;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final SessionHandle sessionHandle;
    private final RouteHandle outputRoute;
    private final List<SessionEventListenerHandle> eventListenerHandles;
    private final ChatWindowController chatController;

    private final PromptService promptService = request -> switch (request) {
        case UiPromptRequest.ConfirmPrompt ignored -> new UiPromptResponse.ConfirmResponse(false);
        case UiPromptRequest.SelectPrompt select -> new UiPromptResponse.SelectResponse(
                select.defaultIndex(),
                select.options().isEmpty() ? "" : select.options().get(Math.min(select.defaultIndex(), select.options().size() - 1))
        );
        case UiPromptRequest.TextPrompt text -> new UiPromptResponse.TextResponse(text.defaultValue());
    };

    public TuiTerminalUiRuntime(RuntimeConfig runtimeConfig, Magenta magenta, TerminalUiConfig config) throws Exception {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.magenta = Objects.requireNonNull(magenta, "magenta");
        this.config = Objects.requireNonNull(config, "config");

        this.workspaceHost = new WorkspaceHost();
        this.themeRegistry = new TuiThemeRegistry();
        this.app = new TuiApplication(themeRegistry, workspaceHost);

        AtomicReference<ChatWindowController> controllerRef = new AtomicReference<>();

        SessionConfig sessionConfig = new SessionConfig(
                config.session().params(),
                magenta::executeTool,
                config.session().routingEventLevel(),
                event -> {
                    ChatWindowController controller = controllerRef.get();
                    if (controller != null) {
                        controller.onRoutingEvent(event);
                    }
                    if (config.session().routingEventLevel() != RoutingEventLevel.NONE) {
                        config.callbacks().onRouting().accept(event);
                    }
                },
                event -> {
                    ChatWindowController controller = controllerRef.get();
                    if (controller != null) {
                        controller.onSecurityEvent(event);
                    }
                    config.callbacks().onSecurity().accept(event);
                },
                error -> {
                    ChatWindowController controller = controllerRef.get();
                    if (controller != null) {
                        controller.onSessionError(error);
                    }
                    config.callbacks().onError().accept(error);
                }
        );

        this.sessionHandle = magenta.startBaseSession(config.session().alias(), sessionConfig);

        ChatBinding binding = new ChatBinding(
                sessionHandle,
                input -> {
                    if (input instanceof SessionInput.MessageInput messageInput) {
                        magenta.messageInputConsumer(sessionHandle).accept(messageInput);
                        return;
                    }
                    throw new IllegalArgumentException("Chat binding only accepts message inputs");
                },
                magenta.contextUsageSupplier(sessionHandle)
        );

        int width = Math.max(72, app.getScreen().getWidth());
        int height = Math.max(20, app.getScreen().getHeight() - 1);
        ChatWindow chatWindow = new ChatWindow(
                app,
                "Chat",
                width,
                height,
                new NoOpChatController(),
                config
        );

        this.chatController = new ChatWindowController(magenta, config, binding, chatWindow, this::close);
        controllerRef.set(chatController);
        chatWindow.setController(chatController);

        app.registerWindow("default", "chat", chatWindow);
        chatWindow.activate();

        magenta.addInputRoute(sessionHandle, InputRoutePolicy.defaults());
        this.outputRoute = magenta.addOutputRoute(
                sessionHandle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(
                                SessionOutput.StreamedOutput.FILTER_TAG,
                                SessionOutput.FinalOutput.FILTER_TAG,
                                SessionOutput.ToolCallOutput.FILTER_TAG,
                                SessionOutput.ToolMessageOutput.FILTER_TAG
                        ))
                        .build(),
                event -> chatController.onOutput(event.output())
        );

        List<SessionEventListenerHandle> listeners = new ArrayList<>();
        listeners.add(magenta.addEventListener(sessionHandle, SessionEvent.Action.ContextCompacted.class, chatController::onContextCompacted));
        listeners.add(magenta.addEventListener(sessionHandle, SessionEvent.Action.ContextSendBudget.class, ignored -> chatController.onContextBudgetUpdate()));
        this.eventListenerHandles = List.copyOf(listeners);

        chatController.initializeWindowState();
    }

    public void runLoop() {
        try {
            app.run();
        } finally {
            close();
        }
    }

    public PromptService promptService() {
        return promptService;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            chatController.shutdown();
        } catch (Exception ignored) {
        }

        for (SessionEventListenerHandle listenerHandle : eventListenerHandles) {
            try {
                magenta.removeEventListener(listenerHandle);
            } catch (Exception ignored) {
            }
        }

        try {
            magenta.removeRoute(outputRoute);
        } catch (Exception ignored) {
        }

        try {
            magenta.closeSession(sessionHandle);
        } catch (Exception ignored) {
        }

        try {
            app.invokeLater(app::exit);
        } catch (Exception ignored) {
        }
    }

    private static final class NoOpChatController implements io.mindspice.magenta.ui.tui.chat.ChatController {
        @Override
        public boolean submitComposerText(String text) {
            return false;
        }

        @Override
        public void requestAbort() {
        }
    }
}
