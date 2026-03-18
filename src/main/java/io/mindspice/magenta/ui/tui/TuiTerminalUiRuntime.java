package io.mindspice.magenta.ui.tui;

import casciian.TWindow;
import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.events.SessionEventListenerHandle;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionException;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.ui.RoutingEventFormatter;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.prompt.PromptService;
import io.mindspice.magenta.ui.prompt.UiPromptRequest;
import io.mindspice.magenta.ui.prompt.UiPromptResponse;
import io.mindspice.magenta.ui.tui.chat.ChatBinding;
import io.mindspice.magenta.ui.tui.chat.ChatWindow;
import io.mindspice.magenta.ui.tui.chat.ChatWindowController;
import io.mindspice.magenta.ui.tui.windows.DocumentViewerWindow;
import io.mindspice.magenta.ui.tui.windows.EventViewerWindow;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactory;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactoryRegistry;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceConfigLoader;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceDefinition;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceOverlayStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final Map<TWindow, ChatWindowController> chatControllersByWindow = new ConcurrentHashMap<>();
    private final List<EventViewerWindow> eventViewers = new CopyOnWriteArrayList<>();
    private final RoutingEventFormatter routingFormatter = new RoutingEventFormatter();

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

        SessionConfig sessionConfig = new SessionConfig(
                config.session().params(),
                magenta::executeTool,
                config.session().routingEventLevel(),
                this::onRoutingEvent,
                this::onSecurityEvent,
                this::onSessionError
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

        List<WindowKindFactory> factories = List.of(
                chatFactory(binding),
                eventViewerFactory(),
                documentViewerFactory()
        );
        WindowKindFactoryRegistry windowKindRegistry = WindowKindFactoryRegistry.fromFactories(factories);
        WorkspaceConfigLoader workspaceLoader = new WorkspaceConfigLoader(windowKindRegistry);
        Map<String, WorkspaceDefinition> workspaceMap = workspaceLoader.load(runtimeConfig.rootDir());

        this.workspaceHost = new WorkspaceHost(
                workspaceMap,
                windowKindRegistry,
                new WorkspaceOverlayStore(runtimeConfig.workspaceRoot()),
                this::onWorkspaceEvent,
                error -> onSessionError(new SessionException(sessionHandle, error))
        );

        TuiApplication.configureFrameworkChromeDefaults();
        this.themeRegistry = new TuiThemeRegistry(runtimeConfig.rootDir());
        this.app = new TuiApplication(themeRegistry, workspaceHost);
        this.app.activateInitialWorkspace();

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
                event -> onOutput(event.output())
        );

        List<SessionEventListenerHandle> listeners = new ArrayList<>();
        listeners.add(magenta.addEventListener(sessionHandle, SessionEvent.Action.ContextCompacted.class, event -> {
            ChatWindowController controller = activeChatController();
            if (controller != null) {
                controller.onContextCompacted(event);
            }
        }));
        listeners.add(magenta.addEventListener(sessionHandle, SessionEvent.Action.ContextSendBudget.class, ignored -> {
            ChatWindowController controller = activeChatController();
            if (controller != null) {
                controller.onContextBudgetUpdate();
            }
        }));
        listeners.add(magenta.addEventListener(sessionHandle, SessionEvent.class, this::onSessionEvent));
        this.eventListenerHandles = List.copyOf(listeners);

        ChatWindowController controller = activeChatController();
        if (controller != null) {
            controller.initializeWindowState();
        }
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

        chatControllersByWindow.values().forEach(controller -> {
            try {
                controller.shutdown();
            } catch (Exception ignored) {
            }
        });

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

    private void onOutput(SessionOutput output) {
        if (output == null || closed.get()) {
            return;
        }

        ChatWindowController controller = activeChatController();
        if (controller != null) {
            controller.onOutput(output);
        }

        switch (output) {
            case SessionOutput.ToolCallOutput toolCallOutput -> {
                appendEvent("tool_call", List.of(toolCallOutput.toolCall().name()));
            }
            case SessionOutput.ToolMessageOutput toolMessageOutput -> {
                appendEvent("tool_result", List.of(toolMessageOutput.message().toolName()));
            }
            case SessionOutput.FinalOutput finalOutput -> {
                appendEvent("final", List.of(truncate(finalOutput.text(), 180)));
            }
            case SessionOutput.StreamedOutput ignored -> {
            }
        }
    }

    private void onRoutingEvent(RoutingEvent event) {
        ChatWindowController controller = activeChatController();
        if (controller != null) {
            controller.onRoutingEvent(event);
        }

        appendEvent("routing", routingFormatter.format(event));
        if (config.session().routingEventLevel() != RoutingEventLevel.NONE) {
            config.callbacks().onRouting().accept(event);
        }
    }

    private void onSecurityEvent(SecurityManager.SecurityEvent event) {
        ChatWindowController controller = activeChatController();
        if (controller != null) {
            controller.onSecurityEvent(event);
        }

        appendEvent("security", List.of(event.decisionCode().name().toLowerCase(Locale.ROOT)
                + " " + event.toolName()));
        config.callbacks().onSecurity().accept(event);
    }

    private void onSessionError(SessionException error) {
        ChatWindowController controller = activeChatController();
        if (controller != null) {
            controller.onSessionError(error);
        }

        appendEvent("error", List.of(error.getMessage() == null ? "session error" : error.getMessage()));
        config.callbacks().onError().accept(error);
    }

    private void onSessionEvent(SessionEvent event) {
        if (event == null) {
            return;
        }
        appendEvent("session", List.of(event.getClass().getSimpleName()));
    }

    private WindowKindFactory chatFactory(ChatBinding binding) {
        return new WindowKindFactory() {
            @Override
            public String kind() {
                return "chat";
            }

            @Override
            public TWindow create(WorkspaceDefinition.WindowDescriptor descriptor, TuiApplication app) {
                int width = descriptor.geometry() == null
                        ? 96
                        : descriptor.geometry().width();
                int height = descriptor.geometry() == null
                        ? 26
                        : descriptor.geometry().height();

                ChatWindow chatWindow = new ChatWindow(
                        app,
                        descriptor.title(),
                        width,
                        height,
                        new NoOpChatController(),
                        config
                );
                ChatWindowController controller = new ChatWindowController(magenta, config, binding, chatWindow, TuiTerminalUiRuntime.this::close);
                chatWindow.setController(controller);
                controller.initializeWindowState();
                chatControllersByWindow.put(chatWindow, controller);
                return chatWindow;
            }
        };
    }

    private WindowKindFactory eventViewerFactory() {
        return new WindowKindFactory() {
            @Override
            public String kind() {
                return "event_viewer";
            }

            @Override
            public TWindow create(WorkspaceDefinition.WindowDescriptor descriptor, TuiApplication app) {
                int width = descriptor.geometry() == null
                        ? 64
                        : descriptor.geometry().width();
                int height = descriptor.geometry() == null
                        ? 16
                        : descriptor.geometry().height();

                EventViewerWindow window = new EventViewerWindow(app, descriptor.title(), width, height, 800);
                eventViewers.add(window);
                return window;
            }
        };
    }

    private WindowKindFactory documentViewerFactory() {
        return new WindowKindFactory() {
            @Override
            public String kind() {
                return "document_viewer";
            }

            @Override
            public TWindow create(WorkspaceDefinition.WindowDescriptor descriptor, TuiApplication app) {
                int width = descriptor.geometry() == null
                        ? 64
                        : descriptor.geometry().width();
                int height = descriptor.geometry() == null
                        ? 14
                        : descriptor.geometry().height();

                DocumentViewerWindow window = new DocumentViewerWindow(
                        app,
                        descriptor.title(),
                        width,
                        height,
                        runtimeConfig.workspaceRoot(),
                        runtimeConfig.maxFileReadLines(),
                        runtimeConfig.maxToolOutputBytes()
                );
                String configuredPath = descriptor.binding("path");
                window.openDocument(configuredPath);
                return window;
            }
        };
    }

    private ChatWindowController activeChatController() {
        TWindow chatWindow = workspaceHost.firstWindowByKind("chat");
        if (chatWindow == null) {
            return null;
        }
        return chatControllersByWindow.get(chatWindow);
    }

    private void onWorkspaceEvent(WorkspaceHost.WorkspaceEvent event) {
        if (event == null) {
            return;
        }
        appendEvent("workspace", List.of(
                "type=" + safe(event.type())
                        + " workspace=" + safe(event.workspaceId())
                        + " window=" + safe(event.windowId())
                        + " status=" + safe(event.status())
                        + " code=" + safe(event.code())
                        + " message=" + safe(event.message())
        ));
    }

    private void appendEvent(String source, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (EventViewerWindow viewer : eventViewers) {
            viewer.appendEvent(source, lines);
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        int limit = Math.max(16, maxChars);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit - 3) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
