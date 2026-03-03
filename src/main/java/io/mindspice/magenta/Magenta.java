package io.mindspice.magenta;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.model.ModelRunner;
import io.mindspice.magenta.runtime.model.OllamaClient;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutingEvent;
import io.mindspice.magenta.runtime.routing.SessionRouter;
import io.mindspice.magenta.runtime.session.Session;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionManager;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.Objects;
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
        this.sessionRouter = new SessionRouter(sessionManager::handleFor, sessionManager::submitFromRoute);
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

    public SessionHandle resumeSession(UUID sessionId) {
        return sessionManager.handleFor(sessionId);
    }

    public SessionHandle forkSession(UUID sourceSessionId, String alias) {
        Session session = sessionManager.fork(sourceSessionId, alias);
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionHandle forkSession(UUID sourceSessionId, String alias, SessionConfig sessionConfigOverride) {
        Session session = sessionManager.fork(sourceSessionId, alias, sessionConfigOverride);
        return sessionManager.handleFor(session.sessionId());
    }

    public void registerInputRoute(
            SessionHandle handle,
            InputRoutePolicy policy,
            InputRoutingEvent.Level routingEventLevel,
            Consumer<InputRoutingEvent> routingEventListener
    ) {
        sessionRouter.registerInputRoute(handle, policy, routingEventLevel, routingEventListener);
    }

    public void updateInputRoute(
            SessionHandle handle,
            InputRoutePolicy policy,
            InputRoutingEvent.Level routingEventLevel,
            Consumer<InputRoutingEvent> routingEventListener
    ) {
        sessionRouter.updateInputRoute(handle, policy, routingEventLevel, routingEventListener);
    }

    public void unregisterInputRoute(SessionHandle handle) {
        sessionRouter.unregisterInputRoute(handle);
    }

    public Consumer<SessionInput.MessageInput> getMessageInputConsumer(SessionHandle handle) {
        return sessionRouter.getMessageInputConsumer(handle);
    }

    public Consumer<SessionInput.EventInput> getEventInputConsumer(SessionHandle handle) {
        return sessionRouter.getEventInputConsumer(handle);
    }

    public UUID registerOutputRoute(
            SessionHandle handle,
            OutputRoutePolicy outputPolicy,
            Consumer<OutputRoutingEvent> outputListener
    ) {
        return sessionRouter.registerOutputRoute(handle, outputPolicy, outputListener);
    }

    public void unregisterOutputRoute(SessionHandle handle, UUID routeId) {
        sessionRouter.unregisterOutputRoute(handle, routeId);
    }

    public void closeSession(SessionHandle handle) {
        if (handle == null) {
            return;
        }
        sessionRouter.pruneSession(handle.sessionId());
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
                    java.util.Map.of()
            );
            case SessionInput.EventInput eventInput -> new ContextElement.InboundMsg(
                    "event",
                    "",
                    eventInput.sourceId(),
                    eventInput.text(),
                    "",
                    java.util.Map.of()
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
            sessionRouter.emit(handle, new OutputRoutingEvent(sessionId, new SessionOutput.ContextMessageOutput(message)));
        }

        boolean shouldStream = session.sessionConfig().params().streamingEnabled()
                && sessionRouter.hasStreamedOutputListeners(handle);
        return modelRunner.runTurn(
                session,
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
        return new SessionConfig(
                SessionParams.ofStreaming(true),
                request -> ToolResult.notHandled(request.toolCall()),
                ignored -> {}
        );
    }
}
