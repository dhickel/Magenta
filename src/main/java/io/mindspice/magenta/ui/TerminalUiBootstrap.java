package io.mindspice.magenta.ui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.ui.prompt.JlinePromptService;
import io.mindspice.magenta.ui.render.UiRenderBlock;
import io.mindspice.magenta.ui.render.UiRenderer;
import io.mindspice.magenta.ui.render.UiStatusBar;
import io.mindspice.magenta.ui.render.UiStyle;
import io.mindspice.magenta.ui.slash.SlashCommandRegistry;
import io.mindspice.magenta.ui.slash.SlashCompleter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class TerminalUiBootstrap {

    private TerminalUiBootstrap() {
    }

    public static TerminalUiRuntime bootstrap(
            Magenta magenta,
            TerminalUiConfig config,
            ToolApprovalPromptAdapter approvalAdapter
    ) throws IOException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();

        AtomicReference<SlashCommandRegistry> registryRef = TerminalUiRuntime.registryRef();
        SlashCompleter completer = new SlashCompleter(registryRef::get);

        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .option(LineReader.Option.AUTO_MENU, true)
                .build();

        UiRenderer renderer = new UiRenderer(terminal, config.rendering());
        JlinePromptService promptService = new JlinePromptService(lineReader, renderer, config.prompts());
        approvalAdapter.setPromptService(promptService);
        RoutingEventPrinter routePrinter = new RoutingEventPrinter(renderer);
        AtomicReference<Runnable> refreshStatusRef = new AtomicReference<>(() -> {});

        SessionConfig sessionConfig = new SessionConfig(
                config.session().params(),
                magenta::executeTool,
                config.session().routingEventLevel(),
                event -> {
                    if (config.observability().routingLogsEnabled()) {
                        routePrinter.print(event);
                    }
                    if (config.session().routingEventLevel() != RoutingEventLevel.NONE) {
                        config.callbacks().onRouting().accept(event);
                    }
                },
                event -> {
                    if (shouldRenderSecurityEvent(config.security().eventVisibility(), event)) {
                        renderer.renderBlock(new UiRenderBlock(
                                "",
                                List.of(securityDisplayLine(event)),
                                securityStyle(event)
                        ));
                    }
                    refreshStatusRef.get().run();
                    config.callbacks().onSecurity().accept(event);
                },
                error -> {
                    String detail = error.getCause() == null ? "unknown" : String.valueOf(error.getCause().getMessage());
                    renderer.renderBlock(new UiRenderBlock(
                            "error> session",
                            List.of(
                                    "sessionId=" + error.sessionHandle().sessionId(),
                                    "message=" + detail
                            ),
                            UiStyle.ERROR
                    ));
                    refreshStatusRef.get().run();
                    config.callbacks().onError().accept(error);
                }
        );

        var handle = magenta.startBaseSession(config.session().alias(), sessionConfig);
        Supplier<Magenta.SessionContextUsage> contextUsageSupplier = magenta.contextUsageSupplier(handle);
        Runnable refreshStatus = statusRefresh(renderer, magenta, handle, contextUsageSupplier);
        refreshStatusRef.set(refreshStatus);
        magenta.addInputRoute(handle, InputRoutePolicy.defaults());
        var settings = magenta.settingsFor(handle);
        boolean streamingExpected = settings.streamingEnabled()
                                    && settings.modelSupportsStreaming()
                                    && !settings.blockingOnly();

        AssistantOutputWriter outputWriter = new AssistantOutputWriter(
                new TerminalAssistantOutputTarget(renderer),
                streamingExpected,
                settings.agentId()
        );
        var outputRoute = magenta.addOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(
                                SessionOutput.StreamedOutput.FILTER_TAG,
                                SessionOutput.FinalOutput.FILTER_TAG,
                                SessionOutput.ToolCallOutput.FILTER_TAG,
                                SessionOutput.ToolMessageOutput.FILTER_TAG
                        ))
                        .build(),
                event -> {
                    outputWriter.onOutput(event.output());
                    switch (event.output()) {
                        case SessionOutput.FinalOutput ignored -> refreshStatus.run();
                        case SessionOutput.ToolMessageOutput ignored -> refreshStatus.run();
                        case SessionOutput.StreamedOutput ignored -> {
                            // no-op
                        }
                        case SessionOutput.ToolCallOutput ignored -> {
                            // no-op
                        }
                    }
                }
        );

        magenta.addEventListener(handle, SessionEvent.Action.ContextCompacted.class, event -> {
            renderer.renderBlock(new UiRenderBlock(
                    "",
                    List.of(contextCompactionDisplayLine(event)),
                    UiStyle.INFO
            ));
            refreshStatus.run();
        });

        TerminalUiSession uiSession = new TerminalUiSession(
                handle,
                outputRoute,
                magenta.messageInputConsumer(handle),
                magenta.eventInputConsumer(handle),
                contextUsageSupplier
        );

        SlashCommandRegistry slashRegistry = TerminalUiRuntime.defaultCommands(
                magenta,
                config,
                uiSession,
                renderer,
                promptService,
                registryRef
        );
        registryRef.set(slashRegistry);

        return new TerminalUiRuntime(
                magenta.runtimeConfig(),
                magenta,
                terminal,
                lineReader,
                renderer,
                promptService,
                config,
                uiSession,
                slashRegistry
        );
    }

    private static Runnable statusRefresh(
            UiRenderer renderer,
            Magenta magenta,
            SessionHandle handle,
            Supplier<Magenta.SessionContextUsage> contextUsageSupplier
    ) {
        return () -> {
            try {
                UiStatusBar status = TerminalUiRuntime.buildStatusBar(magenta, handle, contextUsageSupplier);
                renderer.renderStatus(status);
            } catch (Exception ignored) {
                // Best effort refresh; session may be closing.
            }
        };
    }

    private static String contextCompactionDisplayLine(SessionEvent.Action.ContextCompacted event) {
        return "[Context] Compacted: tokens " + event.tokensBefore()
               + " -> " + event.tokensAfter()
               + ", messages " + event.messagesBefore()
               + " -> " + event.messagesAfter()
               + ", strategy=" + event.strategy();
    }

    private static boolean shouldRenderSecurityEvent(
            TerminalUiConfig.SecurityEventVisibility visibility,
            SecurityManager.SecurityEvent event
    ) {
        return switch (visibility) {
            case OFF -> false;
            case ALL -> true;
            case DENIALS_ONLY -> event.decisionCode() == SecurityManager.DecisionCode.DENIED
                                 || event.decisionCode() == SecurityManager.DecisionCode.VALIDATION_ERROR;
        };
    }

    private static UiStyle securityStyle(SecurityManager.SecurityEvent event) {
        return switch (event.decisionCode()) {
            case DENIED, VALIDATION_ERROR -> UiStyle.ERROR;
            case ALLOWED, OVERRIDE_ALLOWED -> UiStyle.INFO;
        };
    }

    private static String securityDisplayLine(SecurityManager.SecurityEvent event) {
        String outcome = switch (event.decisionCode()) {
            case ALLOWED -> "Allowed";
            case OVERRIDE_ALLOWED -> "Allowed (Override)";
            case DENIED -> "Denied";
            case VALIDATION_ERROR -> "Validation Error";
        };
        String tool = formatToolName(event.toolName());
        String reason = compact(singleLine(event.reason()));
        if (reason.isBlank()) {
            return "  [Security] " + outcome + " | " + tool;
        }
        return "  [Security] " + outcome + " | " + tool + " | " + reason;
    }

    private static String formatToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "Tool";
        }
        String normalized = toolName.trim().replace('-', '_');
        String[] parts = normalized.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? "Tool" : out.toString();
    }

    private static String singleLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\n', ' ').trim();
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 180) {
            return value;
        }
        return value.substring(0, 177) + "...";
    }
}
