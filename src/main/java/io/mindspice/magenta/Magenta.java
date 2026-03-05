package io.mindspice.magenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
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
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.Session;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionManager;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.SessionSettingsView;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolManager;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Primary runtime facade for session lifecycle and routed IO orchestration.
 */
public final class Magenta {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final RuntimeConfig runtimeConfig;
    private final ContextManager contextManager;
    private final SessionManager sessionManager;
    private final SessionRouter sessionRouter;
    private final ModelRunner modelRunner;
    private final ToolManager toolManager;
    private final SecurityManager securityManager;

    public Magenta(RuntimeConfig runtimeConfig) {
        this(runtimeConfig, null, null);
    }

    public Magenta(
            RuntimeConfig runtimeConfig,
            ToolManager toolManager,
            SecurityManager.ApprovalCallback approvalCallback
    ) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.toolManager = toolManager == null ? ToolManager.withBuiltIns(runtimeConfig) : toolManager;
        this.securityManager = new SecurityManager(runtimeConfig.security(), runtimeConfig.workspaceRoot(), approvalCallback);
        this.contextManager = new ContextManager();
        this.modelRunner = new ModelRunner(new OllamaClient());
        this.sessionManager = new SessionManager(runtimeConfig, contextManager, this::executeTurn);
        this.sessionRouter = new SessionRouter(sessionManager::submitFromRoute, sessionManager::onRoutingEvent, ignored -> {});
    }

    public SessionHandle startBaseSession(String alias) {
        return startBaseSession(alias, defaultSessionConfig());
    }

    public SessionHandle startBaseSession(String alias, SessionConfig sessionConfig) {
        String agentId = runtimeConfig.baseAgentId();
        Session session = sessionManager.start(agentId, alias, securedSessionConfig(agentId, sessionConfig));
        securityManager.initializePolicy(session.sessionId());
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionHandle startSession(String agentId, String alias) {
        return startSession(agentId, alias, defaultSessionConfig());
    }

    public SessionHandle startSession(String agentId, String alias, SessionConfig sessionConfig) {
        Session session = sessionManager.start(agentId, alias, securedSessionConfig(agentId, sessionConfig));
        securityManager.initializePolicy(session.sessionId());
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionHandle resumeSession(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return sessionManager.handleFor(handle.sessionId());
    }

    public SessionHandle forkSession(SessionHandle sourceHandle, String alias) {
        Objects.requireNonNull(sourceHandle, "sourceHandle");
        Session session = sessionManager.fork(sourceHandle.sessionId(), alias);
        securityManager.copyPolicy(sourceHandle.sessionId(), session.sessionId());
        return sessionManager.handleFor(session.sessionId());
    }

    public SessionHandle forkSession(SessionHandle sourceHandle, String alias, SessionConfig sessionConfigOverride) {
        Objects.requireNonNull(sourceHandle, "sourceHandle");
        String agentId = sessionManager.resume(sourceHandle.sessionId()).agentId();
        Session session = sessionManager.fork(
                sourceHandle.sessionId(),
                alias,
                securedSessionConfig(agentId, sessionConfigOverride)
        );
        securityManager.copyPolicy(sourceHandle.sessionId(), session.sessionId());
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
        securityManager.clearPolicy(handle.sessionId());
        sessionManager.close(handle.sessionId());
    }

    public void setToolPolicy(SessionHandle handle, SecurityManager.ToolPolicy policy) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(policy, "policy");
        if (!handle.isActive()) {
            throw new IllegalStateException("Session handle is inactive: " + handle.sessionId());
        }
        securityManager.setToolPolicy(handle.sessionId(), policy);
    }

    public SecurityManager.ToolPolicy toolPolicy(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return securityManager.toolPolicy(handle.sessionId());
    }

    public SessionContextUsage contextUsage(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Session session = sessionManager.resume(handle.sessionId());
        RuntimeConfig.ModelConfig modelConfig = session.modelConfig();
        var snapshot = session.context().snapshot();
        int maxContext = modelConfig.maxContext();
        int estimatedTokens = SessionTokenEstimator.estimate(
                snapshot,
                modelConfig.tokenizerEncodingOrDefault()
        );
        double percent = maxContext <= 0 ? 0.0 : (estimatedTokens * 100.0) / maxContext;

        return new SessionContextUsage(
                session.sessionId(),
                modelConfig.id(),
                modelConfig.model(),
                modelConfig.tokenizerEncodingOrDefault(),
                estimatedTokens,
                maxContext,
                percent,
                snapshot.size()
        );
    }

    public Supplier<SessionContextUsage> contextUsageSupplier(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return () -> contextUsage(handle);
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
        List<ToolSpecification> toolSpecifications = session.sessionConfig().params().toolsEnabled()
                && session.modelConfig().supportsToolCalling()
                ? toolManager.toolSpecificationsFor(session.toolIds())
                : List.of();
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
                ),
                toolSpecifications
        );
    }

    private SessionConfig defaultSessionConfig() {
        return new SessionConfig(
                SessionParams.ofStreaming(true),
                toolManager::execute,
                ignored -> {}
        );
    }

    private SessionConfig securedSessionConfig(String agentId, SessionConfig originalOrNull) {
        SessionConfig original = originalOrNull == null ? defaultSessionConfig() : originalOrNull;
        RuntimeConfig.AgentConfig agentConfig = runtimeConfig.agentsById().get(agentId);
        if (agentConfig == null) {
            throw new IllegalStateException("Agent not found for security policy wiring: " + agentId);
        }
        Set<String> agentToolIds = Set.copyOf(agentConfig.toolIds());
        Function<ToolRequest, ToolResult> delegate = original.toolBridge();

        Function<ToolRequest, ToolResult> securedBridge = request -> {
            SecurityManager.Decision decision = securityManager.authorize(request, agentToolIds);
            emitSecurityCallback(original, request, decision);
            if (!decision.allowed()) {
                return ToolResult.handled(request.toolCall().id(), request.toolCall().name(), deniedPayload(decision));
            }
            ToolResult result = delegate.apply(request);
            return result == null ? ToolResult.notHandled(request.toolCall()) : result;
        };

        return new SessionConfig(
                original.params(),
                securedBridge,
                original.routingEventLevel(),
                original.onRouting(),
                original.onSecurity(),
                original.onError()
        );
    }

    private void emitSecurityCallback(SessionConfig sessionConfig, ToolRequest request, SecurityManager.Decision decision) {
        try {
            if (sessionConfig.onSecurity() != null) {
                sessionConfig.onSecurity().accept(securityManager.toEvent(request, decision));
            }
        } catch (Throwable ignored) {
            // Security observability callback failures are non-fatal.
        }
    }

    private String deniedPayload(SecurityManager.Decision decision) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("status", "failed");
            root.put("code", decision.code().name().toLowerCase());
            root.put("message", decision.reason());
            ObjectNode data = MAPPER.createObjectNode();
            data.put("decisionCode", decision.code().name().toLowerCase());
            data.put("reason", decision.reason());
            data.put("mode", decision.mode().name().toLowerCase());
            root.set("data", data);
            return MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return "{\"status\":\"failed\",\"code\":\"security_denied\",\"message\":\"Tool request denied\"}";
        }
    }

    public record SessionContextUsage(
            UUID sessionId,
            String modelId,
            String modelName,
            String tokenizerEncoding,
            int estimatedContextTokens,
            int maxContextTokens,
            double percentOfMaxContext,
            int messageCount
    ) {
        public SessionContextUsage {
            Objects.requireNonNull(sessionId, "sessionId");
            modelId = modelId == null ? "" : modelId;
            modelName = modelName == null ? "" : modelName;
            tokenizerEncoding = tokenizerEncoding == null ? "" : tokenizerEncoding;
        }
    }
}
