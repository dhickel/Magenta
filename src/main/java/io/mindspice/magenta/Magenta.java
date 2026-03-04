package io.mindspice.magenta;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.model.ModelRunner;
import io.mindspice.magenta.runtime.model.OllamaClient;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutingEvent;
import io.mindspice.magenta.runtime.routing.Route;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.SessionRouter;
import io.mindspice.magenta.runtime.session.Session;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionManager;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.SessionSettingsView;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Primary runtime facade for session lifecycle and routed IO orchestration.
 */
public final class Magenta {

    private final RuntimeConfig runtimeConfig;
    private final ContextManager contextManager;
    private final SessionManager sessionManager;
    private final SessionRouter sessionRouter;
    private final ModelRunner modelRunner;

    public Magenta(RuntimeConfig runtimeConfig) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.contextManager = new ContextManager();
        this.modelRunner = new ModelRunner(new OllamaClient());
        this.sessionManager = new SessionManager(runtimeConfig, contextManager, this::executeTurn);
        this.sessionRouter = new SessionRouter(sessionManager::submitFromRoute, sessionManager::onRoutingEvent, ignored -> {});
    }

    public SessionHandle startBaseSession(String alias) {
        return startBaseSession(alias, defaultSessionConfig());
    }

    public SessionHandle startBaseSession(String alias, SessionConfig sessionConfig) {
        Session session = sessionManager.start(runtimeConfig.baseAgentId(), alias, sessionConfig);
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionHandle startSession(String agentId, String alias) {
        return startSession(agentId, alias, defaultSessionConfig());
    }

    public SessionHandle startSession(String agentId, String alias, SessionConfig sessionConfig) {
        Session session = sessionManager.start(agentId, alias, sessionConfig);
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionHandle resumeSession(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return sessionManager.handleFor(handle.sessionId());
    }

    public SessionHandle forkSession(SessionHandle sourceHandle, String alias) {
        Objects.requireNonNull(sourceHandle, "sourceHandle");
        Session session = sessionManager.fork(sourceHandle.sessionId(), alias);
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionHandle forkSession(SessionHandle sourceHandle, String alias, SessionConfig sessionConfigOverride) {
        Objects.requireNonNull(sourceHandle, "sourceHandle");
        Session session = sessionManager.fork(sourceHandle.sessionId(), alias, sessionConfigOverride);
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionSettingsView settingsFor(SessionHandle handle) {
        return sessionManager.settingsFor(handle);
    }

    public RouteHandle addInputRoute(SessionHandle handle, InputRoutePolicy policy) {
        return sessionRouter.addInputRoute(handle, policy);
    }

    public RouteHandle addOutputRoute(SessionHandle handle, OutputRoutePolicy outputPolicy, Consumer<OutputRoutingEvent> outputListener) {
        SessionSettingsView settings = settingsFor(handle);
        if (!settings.streamingEnabled() && outputPolicy.requestsStreamedOutput()) {
            throw new IllegalArgumentException("Streamed output routes require streamingEnabled=true for session " + handle.sessionId());
        }
        return sessionRouter.addOutputRoute(handle, outputPolicy, outputListener);
    }

    public void removeRoute(RouteHandle routeHandle) {
        sessionRouter.removeRoute(routeHandle);
    }

    public Route route(RouteHandle routeHandle) {
        return sessionRouter.route(routeHandle);
    }

    public Set<Route> routes(SessionHandle handle) {
        return sessionRouter.routes(handle);
    }

    public Consumer<SessionInput.MessageInput> messageInputConsumer(SessionHandle handle) {
        return sessionRouter.messageInputConsumer(handle);
    }

    public Consumer<SessionInput.EventInput> eventInputConsumer(SessionHandle handle) {
        return sessionRouter.eventInputConsumer(handle);
    }

    public void closeSession(SessionHandle handle) {
        if (handle == null) {
            return;
        }
        sessionRouter.pruneSession(handle);
        sessionManager.close(handle.sessionId());
    }

    /**
     * Returns immutable runtime configuration metadata loaded at startup.
     */
    public RuntimeConfig runtimeConfig() {
        return runtimeConfig;
    }

    private ContextElement toContextElement(SessionInput input) {
        return switch (input) {
            case SessionInput.UserMsg userMessage -> new ContextElement.UserMsg(userMessage.text());
            case SessionInput.MessageInput messageInput -> new ContextElement.InboundMsg(
                    "message",
                    "",
                    messageInput.sourceId(),
                    messageInput.text(),
                    "",
                    Map.of()
            );
            case SessionInput.EventInput eventInput -> new ContextElement.InboundMsg(
                    "event",
                    "",
                    eventInput.sourceId(),
                    eventInput.text(),
                    "",
                    Map.of()
            );
        };
    }

    private RuntimeConfig.AgentConfig compactionAgentConfig() {
        String compactionAgentId = runtimeConfig.compactionAgentId();
        RuntimeConfig.AgentConfig agent = runtimeConfig.agentsById().get(compactionAgentId);
        if (agent == null) {
            throw new IllegalStateException("Compaction agent not found: " + compactionAgentId);
        }
        return agent;
    }

    private RuntimeConfig.ModelConfig compactionModelConfig() {
        RuntimeConfig.AgentConfig compactionAgent = compactionAgentConfig();
        RuntimeConfig.ModelConfig model = runtimeConfig.modelsById().get(compactionAgent.modelId());
        if (model == null) {
            throw new IllegalStateException("Compaction model not found: " + compactionAgent.modelId());
        }
        return model;
    }

    private String compactionSystemPrompt() {
        RuntimeConfig.AgentConfig compactionAgent = compactionAgentConfig();
        StringBuilder sb = new StringBuilder();
        for (String promptId : compactionAgent.promptIds()) {
            String prompt = runtimeConfig.promptsById().get(promptId);
            if (prompt == null) {
                throw new IllegalStateException("Compaction prompt not found: " + promptId);
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(prompt);
        }
        return sb.toString();
    }

    private String executeTurn(UUID sessionId, SessionInput input) {
        Session session = sessionManager.resume(sessionId);
        SessionHandle handle = sessionManager.handleFor(sessionId);

        SessionInput effectiveInput = input == null ? SessionInput.userMessage("") : input;
        if (effectiveInput.addToContext()) {
            ContextElement message = toContextElement(effectiveInput);
            session.context().append(message);
            sessionRouter.emit(handle, new OutputRoutingEvent(handle, new SessionOutput.ContextMessageOutput(message)));
        }

        boolean shouldStream = settingsFor(handle).streamingEnabled() && sessionRouter.hasStreamedOutputListeners(handle);
        return modelRunner.runTurn(
                session,
                handle,
                runtimeConfig.maxTurns(),
                shouldStream,
                event -> sessionRouter.emit(handle, event),
                () -> contextManager.compactIfNeeded(
                        session.sessionId(),
                        session.context(),
                        session.modelConfig(),
                        messages -> modelRunner.summarize(
                                compactionModelConfig(),
                                compactionSystemPrompt(),
                                messages
                        )
                )
        );
    }

    private SessionConfig defaultSessionConfig() {
        return new SessionConfig(SessionParams.ofStreaming(true), request -> ToolResult.notHandled(request.toolCall()), ignored -> {});
    }
}
