package io.mindspice.magenta.systems;

import io.mindspice.magenta.systems.config.RuntimeConfig.AgentConfig;
import io.mindspice.magenta.systems.config.RuntimeConfig;
import io.mindspice.magenta.systems.config.RuntimeConfig.ModelConfig;
import io.mindspice.magenta.systems.model.ModelRunner;
import io.mindspice.magenta.systems.model.OllamaClient;
import io.mindspice.magenta.systems.session.ContextManager;
import io.mindspice.magenta.systems.session.Session;
import io.mindspice.magenta.systems.session.SessionConfig;
import io.mindspice.magenta.systems.session.SessionInput;
import io.mindspice.magenta.systems.session.SessionMessage;
import io.mindspice.magenta.systems.session.SessionManager;
import io.mindspice.magenta.systems.session.SessionRoutePolicy;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class Magenta {

    private final RuntimeConfig runtimeConfig;
    private final ContextManager contextManager;
    private final SessionManager sessionManager;
    private final ModelRunner modelRunner;
    private final ConcurrentMap<UUID, SessionRoutePolicy> routePolicies = new ConcurrentHashMap<>();

    public Magenta(RuntimeConfig runtimeConfig) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.contextManager = new ContextManager();
        this.modelRunner = new ModelRunner(new OllamaClient());
        this.sessionManager = new SessionManager(runtimeConfig, contextManager, this::runSessionTurn);
    }

    public Session startBaseSession(String alias, SessionConfig sessionConfig) {
        return sessionManager.start(runtimeConfig.baseAgentId(), alias, sessionConfig);
    }

    public Session startSession(String agentId, String alias, SessionConfig sessionConfig) {
        return sessionManager.start(agentId, alias, sessionConfig);
    }

    public Session resumeSession(UUID sessionId) {
        return sessionManager.resume(sessionId);
    }

    public Session forkSession(UUID sourceSessionId, String alias) {
        return sessionManager.fork(sourceSessionId, alias);
    }

    public Session forkSession(UUID sourceSessionId, String alias, SessionConfig sessionConfigOverride) {
        return sessionManager.fork(sourceSessionId, alias, sessionConfigOverride);
    }

    public String runUserTurn(UUID sessionId, String userInput) {
        return runSessionTurn(sessionId, SessionInput.userMessage(userInput));
    }

    public String runSessionTurn(UUID sessionId, SessionInput input) {
        Session session = null;
        try {
            session = sessionManager.resume(sessionId);

            contextManager.compactIfNeeded(
                    session.sessionId(),
                    session.context(),
                    session.modelConfig(),
                    messages -> modelRunner.summarize(
                            compactionModelConfig(),
                            compactionSystemPrompt(),
                            messages
                    )
            );

            SessionInput effectiveInput = input == null ? SessionInput.userMessage("") : input;
            session.sessionConfig().emitInputReceived(effectiveInput);
            if (effectiveInput.persist()) {
                SessionMessage message = toSessionMessage(effectiveInput);
                session.context().append(message);
                session.sessionConfig().emitMessageAppended(message);
            }

            return modelRunner.runTurn(session, runtimeConfig.maxTurns());
        } catch (Throwable throwable) {
            if (session != null) {
                try {
                    session.sessionConfig().onError().accept(throwable);
                } catch (Throwable ignored) {
                    // Keep original throwable as the primary failure signal.
                }
            }
            throw throwable;
        }
    }

    public Consumer<SessionInput> sessionInputConsumer(UUID sessionId) {
        return input -> runSessionTurn(sessionId, input);
    }

    public void registerSessionRoute(UUID sessionId, SessionRoutePolicy policy) {
        sessionManager.resume(sessionId);
        routePolicies.put(sessionId, policy == null ? SessionRoutePolicy.defaults() : policy);
    }

    public void unregisterSessionRoute(UUID sessionId) {
        routePolicies.remove(sessionId);
    }

    public int publishToSessions(SessionInput input) {
        if (input == null) {
            return 0;
        }

        int delivered = 0;
        for (Map.Entry<UUID, SessionRoutePolicy> entry : routePolicies.entrySet()) {
            SessionRoutePolicy policy = entry.getValue();
            if (policy == null || !policy.allows(input)) {
                continue;
            }
            runSessionTurn(entry.getKey(), input);
            delivered++;
        }
        return delivered;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public ContextManager contextManager() {
        return contextManager;
    }

    public ModelRunner modelRunner() {
        return modelRunner;
    }

    public RuntimeConfig runtimeConfig() {
        return runtimeConfig;
    }

    private SessionMessage toSessionMessage(SessionInput input) {
        return switch (input) {
            case SessionInput.UserMessageInput userMessageInput -> new SessionMessage.UserMsg(userMessageInput.text());
            case SessionInput.MessageInput messageInput -> new SessionMessage.InboundMsg(
                    "message",
                    messageInput.kind().name(),
                    messageInput.sourceId(),
                    messageInput.text(),
                    messageInput.correlationId(),
                    messageInput.metadata()
            );
            case SessionInput.EventInput eventInput -> new SessionMessage.InboundMsg(
                    "event",
                    eventInput.kind().name(),
                    eventInput.sourceId(),
                    eventInput.text(),
                    eventInput.correlationId(),
                    eventInput.metadata()
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
}
