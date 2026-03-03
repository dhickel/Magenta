package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

public final class SessionManager {

    private final RuntimeConfig runtimeConfig;
    private final ContextManager contextManager;
    private final BiFunction<UUID, SessionInput, String> turnSubmitter;
    private final ConcurrentMap<UUID, Session> sessionsById = new ConcurrentHashMap<>();

    public SessionManager(
            RuntimeConfig runtimeConfig,
            ContextManager contextManager,
            BiFunction<UUID, SessionInput, String> turnSubmitter
    ) {
        this.runtimeConfig = runtimeConfig;
        this.contextManager = contextManager;
        this.turnSubmitter = Objects.requireNonNull(turnSubmitter, "turnSubmitter");
    }

    public Session start(String agentId, String alias, SessionConfig sessionConfig) {
        return start(agentId, alias, sessionConfig, null);
    }

    public Session start(String agentId, String alias, SessionConfig sessionConfig, Context existingContextOrNull) {
        RuntimeConfig.AgentConfig agent = requireAgent(agentId);
        RuntimeConfig.ModelConfig model = requireModel(agent.modelId(), agentId);

        UUID sessionId = UUID.randomUUID();
        String effectiveAlias = normalizeAlias(alias, sessionId);
        String systemPrompt = resolveSystemPrompt(agent.promptIds());
        Context context = contextManager.loadContext(existingContextOrNull, systemPrompt);

        Session session = new Session(
                sessionId,
                agent.id(),
                effectiveAlias,
                model,
                agent.toolIds(),
                context,
                sessionConfig == null
                        ? new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
                        : sessionConfig,
                Instant.now()
        );

        Session prior = sessionsById.putIfAbsent(sessionId, session);
        if (prior != null) {
            throw new IllegalStateException("Session ID collision: " + sessionId);
        }

        return session;
    }

    public Session resume(UUID sessionId) {
        Session session = sessionsById.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }
        return session;
    }

    public Session fork(UUID sourceSessionId, String alias) {
        Session source = resume(sourceSessionId);
        return fork(sourceSessionId, alias, source.sessionConfig());
    }

    public Session fork(UUID sourceSessionId, String alias, SessionConfig overrideOrNull) {
        Session source = resume(sourceSessionId);
        SessionConfig config = overrideOrNull == null ? source.sessionConfig() : overrideOrNull;
        Context copiedContext = contextManager.copyContext(source.context());
        return start(source.agentId(), alias, config, copiedContext);
    }

    public List<Session> list() {
        return sessionsById.values().stream()
                .sorted(Comparator.comparing(Session::createdAt))
                .toList();
    }

    public void close(UUID sessionId) {
        sessionsById.remove(sessionId);
    }

    public SessionHandle handleFor(UUID sessionId) {
        Session session = resume(sessionId);
        return new SessionHandle(
                session.sessionId(),
                isActiveSupplier(session.sessionId()),
                session.sessionConfig().params()
        );
    }

    public void submitFromRoute(UUID sessionId, SessionInput input) {
        submit(sessionId, input);
    }

    public boolean isActive(UUID sessionId) {
        return sessionId != null && sessionsById.containsKey(sessionId);
    }

    private RuntimeConfig.AgentConfig requireAgent(String agentId) {
        RuntimeConfig.AgentConfig agent = runtimeConfig.agentsById().get(agentId);
        if (agent == null || !agent.enabled()) {
            throw new IllegalStateException("Agent missing or disabled: " + agentId);
        }
        return agent;
    }

    private RuntimeConfig.ModelConfig requireModel(String modelId, String agentId) {
        RuntimeConfig.ModelConfig model = runtimeConfig.modelsById().get(modelId);
        if (model == null || !model.enabled()) {
            throw new IllegalStateException("Model missing or disabled for agent " + agentId + ": " + modelId);
        }
        return model;
    }

    private String resolveSystemPrompt(List<String> promptIds) {
        if (promptIds == null || promptIds.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String promptId : promptIds) {
            String prompt = runtimeConfig.promptsById().get(promptId);
            if (prompt == null) {
                throw new IllegalStateException("Prompt ID not found: " + promptId);
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(prompt);
        }
        return sb.toString();
    }

    private String normalizeAlias(String alias, UUID sessionId) {
        if (alias == null || alias.isBlank()) {
            return "session-" + sessionId.toString().substring(0, 8);
        }
        return alias.trim();
    }

    private void submit(UUID sessionId, SessionInput input) {
        Session session = null;
        try {
            session = resume(sessionId);
            if (input == null) {
                return;
            }
            turnSubmitter.apply(sessionId, input);
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            if (message != null && message.contains("Session not found")) {
                throw e;
            }
            emitOnError(session, e);
        } catch (Throwable throwable) {
            emitOnError(session, throwable);
        }
    }

    private BooleanSupplier isActiveSupplier(UUID sessionId) {
        return () -> isActive(sessionId);
    }

    private void emitOnError(Session session, Throwable throwable) {
        if (session == null) {
            return;
        }
        try {
            session.sessionConfig().onError().accept(throwable);
        } catch (Throwable ignored) {
            // Secondary callback failures must not escape external ingress path.
        }
    }
}
