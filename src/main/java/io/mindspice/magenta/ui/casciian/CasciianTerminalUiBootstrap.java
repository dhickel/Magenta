package io.mindspice.magenta.ui.casciian;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.ui.AssistantOutputWriter;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.TerminalUiSession;
import io.mindspice.magenta.ui.ToolApprovalPromptAdapter;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class CasciianTerminalUiBootstrap {

    private CasciianTerminalUiBootstrap() {
    }

    public static CasciianTerminalUiRuntime bootstrap(
            Magenta magenta,
            TerminalUiConfig config,
            ToolApprovalPromptAdapter approvalAdapter
    ) throws Exception {
        AtomicReference<CasciianTerminalUiRuntime> runtimeRef = new AtomicReference<>();

        SessionConfig sessionConfig = new SessionConfig(
                config.session().params(),
                magenta::executeTool,
                config.session().routingEventLevel(),
                event -> {
                    CasciianTerminalUiRuntime runtime = runtimeRef.get();
                    if (runtime != null) {
                        runtime.onRoutingEvent(event);
                    }
                    if (config.session().routingEventLevel() != RoutingEventLevel.NONE) {
                        config.callbacks().onRouting().accept(event);
                    }
                },
                event -> {
                    CasciianTerminalUiRuntime runtime = runtimeRef.get();
                    if (runtime != null) {
                        runtime.onSecurityEvent(event);
                    }
                    config.callbacks().onSecurity().accept(event);
                },
                error -> {
                    CasciianTerminalUiRuntime runtime = runtimeRef.get();
                    if (runtime != null) {
                        runtime.onSessionError(error);
                    }
                    config.callbacks().onError().accept(error);
                }
        );

        var handle = magenta.startBaseSession(config.session().alias(), sessionConfig);
        Supplier<Magenta.SessionContextUsage> contextUsageSupplier = magenta.contextUsageSupplier(handle);
        magenta.addInputRoute(handle, InputRoutePolicy.defaults());
        AtomicReference<AssistantOutputWriter> outputWriterRef = new AtomicReference<>();

        var settings = magenta.settingsFor(handle);
        boolean streamingExpected = settings.streamingEnabled()
                                    && settings.modelSupportsStreaming()
                                    && !settings.blockingOnly();

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
                    AssistantOutputWriter writer = outputWriterRef.get();
                    if (writer != null) {
                        writer.onOutput(event.output());
                    }
                    CasciianTerminalUiRuntime runtime = runtimeRef.get();
                    if (runtime == null) {
                        return;
                    }
                    switch (event.output()) {
                        case SessionOutput.FinalOutput ignored -> {
                            runtime.onFinalOutputReceived();
                            runtime.onContextBudgetUpdate();
                        }
                        case SessionOutput.ToolMessageOutput ignored -> runtime.onContextBudgetUpdate();
                        case SessionOutput.StreamedOutput ignored -> {
                            // no-op
                        }
                        case SessionOutput.ToolCallOutput ignored -> {
                            // no-op
                        }
                    }
                }
        );

        TerminalUiSession runtimeSession = new TerminalUiSession(
                handle,
                outputRoute,
                magenta.messageInputConsumer(handle),
                magenta.eventInputConsumer(handle),
                contextUsageSupplier
        );

        CasciianTerminalUiRuntime runtime = new CasciianTerminalUiRuntime(
                magenta.runtimeConfig(),
                magenta,
                config,
                runtimeSession
        );
        runtimeRef.set(runtime);
        approvalAdapter.setPromptService(runtime.promptService());
        outputWriterRef.set(new AssistantOutputWriter(
                new CasciianAssistantOutputTarget(runtime),
                streamingExpected,
                settings.agentId()
        ));

        magenta.addEventListener(handle, SessionEvent.Action.ContextCompacted.class, runtime::onContextCompacted);
        magenta.addEventListener(handle, SessionEvent.Action.ContextSendBudget.class, ignored -> runtime.onContextBudgetUpdate());

        return runtime;
    }
}
