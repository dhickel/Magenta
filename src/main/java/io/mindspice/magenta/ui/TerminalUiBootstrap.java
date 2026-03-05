package io.mindspice.magenta.ui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.tools.ToolManager;
import io.mindspice.magenta.ui.prompt.JlinePromptService;
import io.mindspice.magenta.ui.render.UiRenderer;
import io.mindspice.magenta.ui.slash.SlashCommandRegistry;
import io.mindspice.magenta.ui.slash.SlashCompleter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

        SessionConfig sessionConfig = new SessionConfig(
                config.session().params(),
                ToolManager.withBuiltIns(magenta.runtimeConfig())::execute,
                config.session().routingEventLevel(),
                event -> {
                    if (config.session().routingEventLevel() != RoutingEventLevel.NONE) {
                        config.callbacks().onRouting().accept(event);
                    }
                },
                event -> config.callbacks().onSecurity().accept(event),
                error -> {
                    renderer.printError("session-error: " + error.getCause().getMessage());
                    config.callbacks().onError().accept(error);
                }
        );

        var handle = magenta.startBaseSession(config.session().alias(), sessionConfig);
        magenta.addInputRoute(handle, InputRoutePolicy.defaults());

        AtomicBoolean streamInProgress = TerminalUiRuntime.streamFlag();
        var outputRoute = magenta.addOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(
                                SessionOutput.StreamedOutput.FILTER_TAG,
                                SessionOutput.FinalOutput.FILTER_TAG
                        ))
                        .build(),
                event -> {
                    switch (event.output()) {
                        case SessionOutput.StreamedOutput stream -> {
                            if (!streamInProgress.get()) {
                                renderer.printStreamToken("assistant> ");
                            }
                            streamInProgress.set(true);
                            renderer.printStreamToken(stream.text());
                        }
                        case SessionOutput.FinalOutput finalOutput -> {
                            if (streamInProgress.getAndSet(false)) {
                                renderer.finishStreamLine();
                            } else {
                                renderer.printAssistant("assistant> " + finalOutput.text());
                            }
                        }
                        default -> {
                            // not subscribed
                        }
                    }
                }
        );

        TerminalUiSession uiSession = new TerminalUiSession(
                handle,
                outputRoute,
                magenta.messageInputConsumer(handle),
                magenta.eventInputConsumer(handle),
                magenta.contextUsageSupplier(handle)
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
}
