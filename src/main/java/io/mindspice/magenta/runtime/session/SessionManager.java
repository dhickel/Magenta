package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
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
        List<String> systemPrompts = resolveSystemPrompts(agent.promptIds());
        Context context = contextManager.loadContext(sessionId, existingContextOrNull, systemPrompts);

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

        contextManager.initializeSessionPersistence(session);
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
        return new SessionHandle(
                resume(sessionId).sessionId(),
                isActiveSupplier(sessionId)
        );
    }

    public SessionSettingsView settingsFor(SessionHandle handle) {
        return settingsFor(handle.sessionId());
    }

    public SessionSettingsView settingsFor(UUID sessionId) {
        Session session = resume(sessionId);
        RuntimeConfig.AgentConfig agent = requireAgent(session.agentId());
        RuntimeConfig.ModelConfig model = session.modelConfig();
        SessionParams params = session.sessionConfig().params();
        return new SessionSettingsView(
                session.sessionId(),
                session.alias(),
                session.agentId(),
                session.createdAt(),
                params.blockingOnly(),
                params.toolsEnabled(),
                params.streamingEnabled(),
                agent.modelId(),
                agent.promptIds(),
                agent.taskIds(),
                agent.workflowIds(),
                agent.toolIds(),
                agent.enabled(),
                resolveSystemPrompt(agent.promptIds()),
                model.id(),
                model.provider(),
                model.model(),
                model.endpoint(),
                model.maxTokens(),
                model.maxContext(),
                model.compactThreshold(),
                model.temperature(),
                model.compactionStrategyOrDefault(),
                model.tokenizerEncodingOrDefault(),
                model.supportsToolCalling(),
                model.supportsStreaming(),
                model.enabled()
        );
    }

    public void submitFromRoute(SessionHandle handle, SessionInput input) {
        submit(handle.sessionId(), input);
    }

    public void onRoutingEvent(RoutingEvent event) {
        Session session = sessionsById.get(event.sessionHandle().sessionId());
        if (session == null) {
            return;
        }
        SessionConfig config = session.sessionConfig();
        RoutingEventLevel level = config.routingEventLevel();
        if (level == RoutingEventLevel.NONE) {
            return;
        }
        if (level == RoutingEventLevel.FINAL
            && event instanceof RoutingEvent.InputResult inputResult
            && inputResult.phase() != InputRoutingEvent.Phase.FINAL) {
            return;
        }
        try {
            if (config.onRouting() != null) {
                config.onRouting().accept(event);
            }
        } catch (Throwable ignored) {
            // Routing callbacks are observability-only.
        }
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
        List<String> prompts = resolveSystemPrompts(promptIds);
        if (prompts.isEmpty()) {
            return "";
        }
        return String.join("\n\n", prompts);
    }

    private List<String> resolveSystemPrompts(List<String> promptIds) {
        if (promptIds == null || promptIds.isEmpty()) {
            return List.of();
        }

        java.util.ArrayList<String> prompts = new java.util.ArrayList<>();
        for (String promptId : promptIds) {
            String prompt = runtimeConfig.promptsById().get(promptId);
            if (prompt == null) {
                throw new IllegalStateException("Prompt ID not found: " + promptId);
            }
            prompts.add(prompt);
        }
        return List.copyOf(prompts);
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
            SessionHandle handle = new SessionHandle(session.sessionId(), isActiveSupplier(session.sessionId()));
            session.sessionConfig().onError().accept(new SessionException(handle, throwable));
        } catch (Throwable ignored) {
            // Secondary callback failures must not escape external ingress path.
        }
    }
}
