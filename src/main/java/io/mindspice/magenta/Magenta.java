package io.mindspice.magenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.events.SessionEventHub;
import io.mindspice.magenta.runtime.events.SessionEventListenerHandle;
import io.mindspice.magenta.runtime.events.SessionEventLogSink;
import io.mindspice.magenta.runtime.model.ModelClientException;
import io.mindspice.magenta.runtime.model.ModelRunner;
import io.mindspice.magenta.runtime.model.OllamaClient;
import io.mindspice.magenta.runtime.persistence.DatabaseService;
import io.mindspice.magenta.runtime.persistence.SessionContextCommand;
import io.mindspice.magenta.runtime.persistence.SessionContextResult;
import io.mindspice.magenta.runtime.persistence.ToolCommand;
import io.mindspice.magenta.runtime.persistence.ToolCommandResult;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutingEvent;
import io.mindspice.magenta.runtime.routing.Route;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.SessionRouter;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionException;
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
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Primary runtime facade for session lifecycle and routed IO orchestration.
 */
public final class Magenta {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final int DEFAULT_DELEGATION_TIMEOUT_MS = 30_000;
    private static final int MAX_DELEGATION_TIMEOUT_MS = 180_000;
    private static final int COMPACTION_TOOL_SCAN_LIMIT = 40;
    private static final int COMPACTION_TODO_LIMIT = 50;
    private static final int RECENT_TOOL_CALLS_LIMIT = 4;
    private static final int RECENT_TODO_UPDATES_LIMIT = 4;
    private static final int OPEN_TODO_QUEUE_LIMIT = 20;
    private static final int OPEN_TODO_QUEUE_MIN_RESERVE_CHARS = 500;
    private static final int COMPLETION_GUARD_OPEN_TODO_LIST_LIMIT = 20;
    private static final int PROTECTED_STATE_MAX_CHARS = 1_500;
    private static final String TURN_ABORTED_TEXT = "[turn-aborted] request cancelled by user";

    private final RuntimeConfig runtimeConfig;
    private final DatabaseService databaseService;
    private final ContextManager contextManager;
    private final SessionManager sessionManager;
    private final SessionRouter sessionRouter;
    private final SessionEventHub eventHub;
    private final SessionEventLogSink eventLogSink;
    private final ModelRunner modelRunner;
    private final ToolManager toolManager;
    private final SecurityManager securityManager;
    private final ConcurrentMap<UUID, LegacySessionCallbacks> legacyCallbacksBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, List<SessionEventListenerHandle>> legacyCallbackListenerHandles = new ConcurrentHashMap<>();

    public Magenta(RuntimeConfig runtimeConfig) {
        this(runtimeConfig, null, null);
    }

    public Magenta(
            RuntimeConfig runtimeConfig,
            ToolManager toolManager,
            SecurityManager.ApprovalCallback approvalCallback
    ) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.databaseService = new DatabaseService(runtimeConfig.workspaceRoot());
        DatabaseService databaseService = this.databaseService;
        Function<ToolCommand, ToolCommandResult> toolCommandBridge = databaseService::execute;
        Function<SessionContextCommand, SessionContextResult> sessionContextBridge = databaseService::execute;
        this.toolManager = toolManager == null
                ? ToolManager.withBuiltIns(runtimeConfig, toolCommandBridge, this::delegateAgentTool)
                : toolManager;
        validateEnabledAgentToolSurface();
        this.securityManager = new SecurityManager(
                runtimeConfig.security(),
                runtimeConfig.workspaceRoot(),
                approvalCallback,
                this.toolManager.securityDescriptorsByName()
        );
        this.contextManager = new ContextManager(sessionContextBridge);
        this.modelRunner = new ModelRunner(new OllamaClient(runtimeConfig.modelRequestTimeoutMs()));
        this.eventHub = new SessionEventHub(ignored -> {});
        this.eventLogSink = new SessionEventLogSink(runtimeConfig.workspaceRoot(), runtimeConfig.observability());
        this.sessionManager = new SessionManager(runtimeConfig, contextManager, this::executeTurn);
        this.sessionRouter = new SessionRouter(
                this::submitFromRoute,
                sessionManager::onRoutingEvent,
                ignored -> {}
        );
    }

    public ToolResult executeTool(ToolRequest request) {
        return toolManager.execute(request);
    }

    public SessionHandle startBaseSession(String alias) {
        return startBaseSession(alias, defaultSessionConfig());
    }

    public SessionHandle startBaseSession(String alias, SessionConfig sessionConfig) {
        return startBaseSession(alias, null, sessionConfig);
    }

    public SessionHandle startBaseSession(String alias, String launchTaskOrNull, SessionConfig sessionConfig) {
        String agentId = runtimeConfig.baseAgentId();
        SessionConfig requestedConfig = sessionConfig == null ? defaultSessionConfig() : sessionConfig;
        Session session = sessionManager.start(agentId, alias, securedSessionConfig(agentId, requestedConfig), launchTaskOrNull);
        securityManager.initializePolicy(session.sessionId());
        SessionHandle handle = sessionManager.handleFor(session.sessionId());
        registerLegacyCallbacks(handle, requestedConfig);
        emitEvent(new SessionEvent.Action.SessionStarted(handle, session.agentId(), session.alias()));
        return handle;
    }

    public SessionHandle startSession(String agentId, String alias) {
        return startSession(agentId, alias, defaultSessionConfig());
    }

    public SessionHandle startSession(String agentId, String alias, SessionConfig sessionConfig) {
        return startSession(agentId, alias, null, sessionConfig);
    }

    public SessionHandle startSession(String agentId, String alias, String launchTaskOrNull, SessionConfig sessionConfig) {
        SessionConfig requestedConfig = sessionConfig == null ? defaultSessionConfig() : sessionConfig;
        Session session = sessionManager.start(agentId, alias, securedSessionConfig(agentId, requestedConfig), launchTaskOrNull);
        securityManager.initializePolicy(session.sessionId());
        SessionHandle handle = sessionManager.handleFor(session.sessionId());
        registerLegacyCallbacks(handle, requestedConfig);
        emitEvent(new SessionEvent.Action.SessionStarted(handle, session.agentId(), session.alias()));
        return handle;
    }

    public SessionHandle resumeSession(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return sessionManager.handleFor(handle.sessionId());
    }

    public SessionHandle forkSession(SessionHandle sourceHandle, String alias) {
        Objects.requireNonNull(sourceHandle, "sourceHandle");
        Session session = sessionManager.fork(sourceHandle.sessionId(), alias);
        securityManager.copyPolicy(sourceHandle.sessionId(), session.sessionId());
        SessionHandle handle = sessionManager.handleFor(session.sessionId());
        LegacySessionCallbacks legacyCallbacks = legacyCallbacksBySession.get(sourceHandle.sessionId());
        if (legacyCallbacks != null) {
            registerLegacyCallbacks(handle, legacyCallbacks);
        }
        emitEvent(new SessionEvent.Action.SessionStarted(handle, session.agentId(), session.alias()));
        return handle;
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
        SessionHandle handle = sessionManager.handleFor(session.sessionId());
        LegacySessionCallbacks callbacks = sessionConfigOverride == null
                ? legacyCallbacksBySession.getOrDefault(sourceHandle.sessionId(), LegacySessionCallbacks.from(defaultSessionConfig()))
                : LegacySessionCallbacks.from(sessionConfigOverride);
        registerLegacyCallbacks(handle, callbacks);
        emitEvent(new SessionEvent.Action.SessionStarted(handle, session.agentId(), session.alias()));
        return handle;
    }

    public SessionSettingsView settingsFor(SessionHandle handle) {
        return sessionManager.settingsFor(handle);
    }

    public String applyTask(SessionHandle handle, String taskName) {
        Objects.requireNonNull(handle, "handle");
        if (!handle.isActive()) {
            throw new IllegalStateException("Session handle is inactive: " + handle.sessionId());
        }
        return sessionManager.applyTask(handle.sessionId(), taskName);
    }

    public String activeTask(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return sessionManager.activeTaskId(handle.sessionId());
    }

    public RouteHandle addInputRoute(SessionHandle handle, InputRoutePolicy policy) {
        RouteHandle routeHandle = sessionRouter.addInputRoute(handle, policy);
        emitEvent(new SessionEvent.Action.InputRouteAdded(handle, agentIdFor(handle.sessionId()), SessionEvent.UUIDLike.from(routeHandle)));
        return routeHandle;
    }

    public RouteHandle addOutputRoute(SessionHandle handle, OutputRoutePolicy outputPolicy, Consumer<OutputRoutingEvent> outputListener) {
        SessionSettingsView settings = settingsFor(handle);
        if (!settings.streamingEnabled() && outputPolicy.requestsStreamedOutput()) {
            throw new IllegalArgumentException("Streamed output routes require streamingEnabled=true for session " + handle.sessionId());
        }
        RouteHandle routeHandle = sessionRouter.addOutputRoute(handle, outputPolicy, outputListener);
        emitEvent(new SessionEvent.Action.OutputRouteAdded(handle, agentIdFor(handle.sessionId()), SessionEvent.UUIDLike.from(routeHandle)));
        return routeHandle;
    }

    public void removeRoute(RouteHandle routeHandle) {
        Route route = route(routeHandle);
        sessionRouter.removeRoute(routeHandle);
        emitEvent(new SessionEvent.Action.RouteRemoved(
                route.sessionHandle(),
                agentIdFor(route.sessionHandle().sessionId()),
                SessionEvent.UUIDLike.from(routeHandle)
        ));
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

    public boolean abortTurn(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return sessionManager.requestAbort(handle.sessionId());
    }

    public boolean turnInProgress(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return sessionManager.turnInProgress(handle.sessionId());
    }

    public void closeSession(SessionHandle handle) {
        if (handle == null) {
            return;
        }
        emitEvent(new SessionEvent.Action.SessionClosed(handle, agentIdFor(handle.sessionId())));
        sessionRouter.pruneSession(handle);
        unregisterLegacyCallbacks(handle.sessionId());
        eventHub.pruneSession(handle);
        legacyCallbacksBySession.remove(handle.sessionId());
        securityManager.clearPolicy(handle.sessionId());
        sessionManager.close(handle.sessionId());
    }

    public <T extends SessionEvent> SessionEventListenerHandle addEventListener(
            SessionHandle handle,
            Class<T> eventType,
            Consumer<T> listener
    ) {
        Objects.requireNonNull(handle, "handle");
        return eventHub.on(handle, eventType, listener);
    }

    public <T extends SessionEvent> SessionEventListenerHandle addEventListener(
            SessionHandle handle,
            Class<T> eventType,
            Predicate<T> predicate,
            Consumer<T> listener
    ) {
        Objects.requireNonNull(handle, "handle");
        return eventHub.on(handle, eventType, predicate, listener);
    }

    public void removeEventListener(SessionEventListenerHandle handle) {
        eventHub.off(handle);
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

    private String buildProtectedCompactionStateBlock(UUID sessionId) {
        if (sessionId == null) {
            return "";
        }
        SessionContextResult result = databaseService.execute(new SessionContextCommand.LoadCompactionState(
                sessionId.toString(),
                COMPACTION_TOOL_SCAN_LIMIT,
                COMPACTION_TODO_LIMIT
        ));
        if (!(result instanceof SessionContextResult.CompactionStateLoaded loaded)) {
            return "";
        }

        List<SessionContextResult.CompactionToolMessage> toolMessages = loaded.recentToolMessages();
        List<SessionContextResult.CompactionTodoItem> todos = loaded.todos();
        List<SessionContextResult.CompactionTodoItem> openTodos = todos.stream()
                .filter(todo -> "open".equalsIgnoreCase(todo.status()))
                .limit(OPEN_TODO_QUEUE_LIMIT)
                .toList();
        java.util.Map<String, SessionContextResult.CompactionTodoItem> todosById = todos.stream()
                .filter(todo -> todo.todoId() != null && !todo.todoId().isBlank())
                .collect(Collectors.toMap(
                        SessionContextResult.CompactionTodoItem::todoId,
                        todo -> todo,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));

        List<ToolCallState> recentToolCalls = new ArrayList<>();
        List<TodoUpdateState> recentTodoUpdates = new ArrayList<>();
        java.util.Set<String> seenTodoUpdateIds = new java.util.LinkedHashSet<>();
        FileState fileState = null;
        String currentTodoId = "";

        for (SessionContextResult.CompactionToolMessage message : toolMessages) {
            ParsedToolState parsed = parseToolState(message.toolName(), message.content());
            if (recentToolCalls.size() < RECENT_TOOL_CALLS_LIMIT) {
                recentToolCalls.add(new ToolCallState(
                        message.toolCallId(),
                        message.toolName(),
                        parsed.status(),
                        parsed.target(),
                        parsed.targetName()
                ));
            }

            if (fileState == null && parsed.path() != null && !parsed.path().isBlank()) {
                fileState = new FileState(parsed.path(), fileActionFromTool(message.toolName()), parsed.snapshotId());
            }

            if (isTodoTool(message.toolName()) && currentTodoId.isBlank() && parsed.todoId() != null && !parsed.todoId().isBlank()) {
                currentTodoId = parsed.todoId();
            }

            if (isTodoTool(message.toolName())
                && parsed.todoId() != null
                && !parsed.todoId().isBlank()
                && recentTodoUpdates.size() < RECENT_TODO_UPDATES_LIMIT
                && seenTodoUpdateIds.add(parsed.todoId())) {
                SessionContextResult.CompactionTodoItem canonical = todosById.get(parsed.todoId());
                String resolvedTitle = parsed.todoTitle().isBlank() && canonical != null ? canonical.title() : parsed.todoTitle();
                String resolvedStatus = parsed.todoStatus().isBlank() && canonical != null ? canonical.status() : parsed.todoStatus();
                long resolvedUpdatedAtMs = parsed.todoUpdatedAtMs() > 0
                        ? parsed.todoUpdatedAtMs()
                        : canonical != null
                            ? canonical.updatedAtMs()
                            : message.createdAtMs();
                recentTodoUpdates.add(new TodoUpdateState(
                        parsed.todoId(),
                        resolvedTitle,
                        resolvedStatus,
                        resolvedUpdatedAtMs
                ));
            }
        }

        if (recentTodoUpdates.size() < RECENT_TODO_UPDATES_LIMIT) {
            for (SessionContextResult.CompactionTodoItem todo : todos) {
                if (recentTodoUpdates.size() >= RECENT_TODO_UPDATES_LIMIT) {
                    break;
                }
                if (todo.todoId() == null || todo.todoId().isBlank() || !seenTodoUpdateIds.add(todo.todoId())) {
                    continue;
                }
                recentTodoUpdates.add(new TodoUpdateState(todo.todoId(), todo.title(), todo.status(), todo.updatedAtMs()));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("todos:\n");
        sb.append("openTodoCount=").append(loaded.openTodoCount()).append('\n');
        sb.append("openTodoQueue:\n");
        if (openTodos.isEmpty()) {
            sb.append("none\n");
        } else {
            for (int i = 0; i < openTodos.size(); i++) {
                SessionContextResult.CompactionTodoItem todo = openTodos.get(i);
                sb.append(i + 1)
                        .append(") todoId=").append(stateValue(todo.todoId()))
                        .append(" title=").append(stateValue(todo.title()))
                        .append(" status=").append(stateValue(todo.status()))
                        .append(" updatedAtMs=").append(todo.updatedAtMs())
                        .append('\n');
            }
        }
        SessionContextResult.CompactionTodoItem currentTodo = currentTodoId.isBlank()
                ? null
                : todosById.get(currentTodoId);
        sb.append("currentTodoId=").append(stateValue(currentTodoId)).append('\n');
        sb.append("currentTodoStatus=").append(stateValue(currentTodo == null ? "" : currentTodo.status())).append('\n');
        sb.append("currentTodoTitle=").append(stateValue(currentTodo == null ? "" : currentTodo.title())).append('\n');
        sb.append("recentTodoUpdates:\n");
        if (recentTodoUpdates.isEmpty()) {
            sb.append("none\n");
        } else {
            for (int i = 0; i < recentTodoUpdates.size(); i++) {
                TodoUpdateState update = recentTodoUpdates.get(i);
                sb.append(i + 1)
                        .append(") todoId=").append(stateValue(update.todoId()))
                        .append(" title=").append(stateValue(update.title()))
                        .append(" status=").append(stateValue(update.status()))
                        .append(" updatedAtMs=").append(update.updatedAtMs())
                        .append('\n');
            }
        }

        sb.append("files:\n");
        if (fileState == null) {
            sb.append("none\n");
        } else {
            sb.append("lastPath=").append(stateValue(fileState.path()))
                    .append(" action=").append(stateValue(fileState.action()))
                    .append(" snapshotId=").append(stateValue(fileState.snapshotId()))
                    .append('\n');
        }

        sb.append("recentToolCalls:\n");
        if (recentToolCalls.isEmpty()) {
            sb.append("none\n");
        } else {
            for (int i = 0; i < recentToolCalls.size(); i++) {
                ToolCallState call = recentToolCalls.get(i);
                sb.append(i + 1)
                        .append(") id=").append(stateValue(call.toolCallId()))
                        .append(" tool=").append(stateValue(call.toolName()))
                        .append(" status=").append(stateValue(call.status()))
                        .append(" target=").append(stateValue(call.target()))
                        .append(" targetName=").append(stateValue(call.targetName()))
                        .append('\n');
            }
        }
        String block = capStateBlock(sb.toString().trim());
        if (!block.isBlank()) {
            return block;
        }
        SessionContextResult.CompactionSnapshot snapshot = loaded.latestSnapshot();
        if (snapshot == null || snapshot.manifestText().isBlank()) {
            return "";
        }
        return capStateBlock(snapshot.manifestText());
    }

    private ParsedToolState parseToolState(String toolName, String payloadContent) {
        JsonNode root = parseJson(payloadContent);
        JsonNode data = root == null ? null : root.path("data");

        String status = readText(root, "status");
        String path = firstNonBlank(
                readText(data, "path"),
                readText(data, "filePath"),
                readText(data, "rootPath")
        );
        String snapshotId = firstNonBlank(
                readText(data, "snapshotIdAfter"),
                readText(data, "snapshotId"),
                readText(data, "snapshotIdBefore")
        );
        String todoId = firstNonBlank(
                readText(data, "todo", "todoId"),
                readText(data, "todoId"),
                readText(data, "id")
        );
        String todoTitle = firstNonBlank(
                readText(data, "todo", "title"),
                readText(data, "title")
        );
        String todoStatus = firstNonBlank(
                readText(data, "todo", "status"),
                readText(data, "status")
        );
        long todoUpdatedAtMs = firstLong(
                readLong(data, "todo", "updatedAtMs"),
                readLong(data, "updatedAtMs")
        );

        String target = firstNonBlank(
                path,
                todoId,
                readText(data, "targetAgentId"),
                readText(data, "dbPath")
        );
        String targetName = firstNonBlank(
                todoTitle,
                readText(data, "delegatedAlias"),
                basename(path)
        );
        if (targetName.isBlank() && isTodoTool(toolName) && !todoId.isBlank()) {
            targetName = "todo";
        }
        return new ParsedToolState(status, target, targetName, path, snapshotId, todoId, todoTitle, todoStatus, todoUpdatedAtMs);
    }

    private JsonNode parseJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(content);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readText(JsonNode root, String... path) {
        if (root == null || path == null || path.length == 0) {
            return "";
        }
        JsonNode cursor = root;
        for (String element : path) {
            if (cursor == null || cursor.isMissingNode() || element == null || element.isBlank()) {
                return "";
            }
            cursor = cursor.path(element);
        }
        if (cursor == null || cursor.isMissingNode() || cursor.isNull()) {
            return "";
        }
        if (cursor.isTextual()) {
            return cursor.asText();
        }
        if (cursor.isNumber() || cursor.isBoolean()) {
            return cursor.asText();
        }
        return "";
    }

    private long readLong(JsonNode root, String... path) {
        if (root == null || path == null || path.length == 0) {
            return 0L;
        }
        JsonNode cursor = root;
        for (String element : path) {
            if (cursor == null || cursor.isMissingNode() || element == null || element.isBlank()) {
                return 0L;
            }
            cursor = cursor.path(element);
        }
        if (cursor == null || cursor.isMissingNode() || cursor.isNull()) {
            return 0L;
        }
        if (cursor.canConvertToLong()) {
            return cursor.asLong();
        }
        try {
            return Long.parseLong(cursor.asText().trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private long firstLong(long... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        for (long value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0L;
    }

    private String basename(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String fileActionFromTool(String toolName) {
        if (toolName == null) {
            return "";
        }
        return switch (toolName) {
            case "read_file" -> "read";
            case "file_metadata" -> "metadata";
            case "write_file" -> "write";
            case "search_replace" -> "edit";
            case "delete_file" -> "delete";
            case "list_directory" -> "list";
            case "grep_files" -> "grep";
            default -> toolName.trim();
        };
    }

    private boolean isTodoTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return toolName.startsWith("todo_");
    }

    private String stateValue(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isEmpty()) {
            return "none";
        }
        if (normalized.length() > 160) {
            return normalized.substring(0, 157) + "...";
        }
        return normalized;
    }

    private String capStateBlock(String block) {
        if (block == null) {
            return "";
        }
        String safe = block.trim();
        if (safe.length() <= PROTECTED_STATE_MAX_CHARS) {
            return safe;
        }
        String withoutTools = dropSection(safe, "recentToolCalls:");
        if (withoutTools.length() <= PROTECTED_STATE_MAX_CHARS) {
            return withoutTools;
        }
        String withoutRecentUpdates = dropSection(withoutTools, "recentTodoUpdates:");
        if (withoutRecentUpdates.length() <= PROTECTED_STATE_MAX_CHARS) {
            return withoutRecentUpdates;
        }

        String normalizedQueue = withoutRecentUpdates.replaceAll("(?m)title=([^\\n]{80})[^\\n]*", "title=$1...");
        if (normalizedQueue.length() <= PROTECTED_STATE_MAX_CHARS) {
            return normalizedQueue;
        }

        int queueStart = normalizedQueue.indexOf("openTodoQueue:");
        if (queueStart >= 0) {
            int reserve = Math.min(normalizedQueue.length(), Math.max(OPEN_TODO_QUEUE_MIN_RESERVE_CHARS, PROTECTED_STATE_MAX_CHARS / 3));
            String queuePrefix = normalizedQueue.substring(queueStart, Math.min(normalizedQueue.length(), queueStart + reserve));
            int max = Math.max(0, PROTECTED_STATE_MAX_CHARS - queuePrefix.length() - 20);
            String head = normalizedQueue.substring(0, Math.min(max, normalizedQueue.length()));
            return (head + "\n" + queuePrefix + "\n...[truncated]").substring(0, Math.min(PROTECTED_STATE_MAX_CHARS, head.length() + queuePrefix.length() + 17));
        }

        int max = Math.max(0, PROTECTED_STATE_MAX_CHARS - 16);
        return normalizedQueue.substring(0, max) + "\n...[truncated]";
    }

    private String dropSection(String content, String sectionHeader) {
        if (content == null || content.isBlank() || sectionHeader == null || sectionHeader.isBlank()) {
            return content == null ? "" : content;
        }
        int sectionStart = content.indexOf(sectionHeader);
        if (sectionStart < 0) {
            return content;
        }
        int nextSectionStart = content.indexOf("\n", sectionStart + sectionHeader.length());
        if (nextSectionStart < 0) {
            return content.substring(0, sectionStart).trim();
        }
        int cursor = nextSectionStart + 1;
        while (cursor < content.length()) {
            int lineEnd = content.indexOf('\n', cursor);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(cursor, lineEnd).trim();
            boolean looksLikeSection = line.endsWith(":")
                                       && !line.startsWith("1)")
                                       && !line.startsWith("2)")
                                       && !line.startsWith("3)");
            if (looksLikeSection) {
                break;
            }
            cursor = lineEnd + 1;
        }
        String before = content.substring(0, sectionStart).trim();
        String after = cursor >= content.length() ? "" : content.substring(cursor).trim();
        if (before.isBlank()) {
            return after;
        }
        if (after.isBlank()) {
            return before;
        }
        return before + "\n" + after;
    }

    private String executeTurn(UUID sessionId, SessionInput input) {
        Session session = sessionManager.resume(sessionId);
        SessionHandle handle = sessionManager.handleFor(sessionId);

        SessionInput effectiveInput = input == null ? SessionInput.userMessage("") : input;
        if (effectiveInput.addToContext()) {
            ContextElement message = toContextElement(effectiveInput);
            session.context().append(message);
        }

        boolean shouldStream = settingsFor(handle).streamingEnabled() && sessionRouter.hasStreamedOutputListeners(handle);
        List<String> activeToolIds = sessionManager.activeToolIds(session.sessionId());
        boolean yolo = securityManager.toolPolicy(session.sessionId()).devYoloOverride();
        List<ToolSpecification> toolSpecifications = session.sessionConfig().params().toolsEnabled()
                && session.modelConfig().supportsToolCalling()
                ? toolManager.toolSpecificationsFor(yolo ? List.of("*") : activeToolIds)
                : List.of();
        Runnable beforeModelCallHook = () -> {
            boolean thresholdCompactionApplied = contextManager.compactIfNeeded(
                            session.sessionId(),
                            session.context(),
                            session.modelConfig(),
                            messages -> modelRunner.summarize(
                                    compactionModelConfig(),
                                    compactionSystemPrompt(),
                                    messages
                            ),
                            () -> buildProtectedCompactionStateBlock(session.sessionId())
                    )
                    .map(compaction -> {
                        emitCompactionEvent(handle, session.agentId(), compaction);
                        return true;
                    })
                    .orElse(false);
            boolean hardGuardCompactionApplied = contextManager.enforceMaxContext(
                            session.sessionId(),
                            session.context(),
                            session.modelConfig()
                    )
                    .map(compaction -> {
                        emitCompactionEvent(handle, session.agentId(), compaction);
                        return true;
                    })
                    .orElse(false);
            int estimatedTokens = SessionTokenEstimator.estimate(
                    session.context().snapshot(),
                    session.modelConfig().tokenizerEncodingOrDefault()
            );
            int maxContext = session.modelConfig().maxContext();
            double percent = maxContext <= 0 ? 0.0 : (estimatedTokens * 100.0) / maxContext;
            emitEvent(new SessionEvent.Action.ContextSendBudget(
                    handle,
                    session.agentId(),
                    estimatedTokens,
                    maxContext,
                    percent,
                    thresholdCompactionApplied,
                    hardGuardCompactionApplied,
                    estimatedTokens <= maxContext
            ));
            if (estimatedTokens > maxContext) {
                throw ModelClientException.of(
                        ModelClientException.Reason.CONTEXT_OVERFLOW,
                        "Context exceeds maxContext after guard compaction (" + estimatedTokens + " > " + maxContext + ")"
                );
            }
        };
        try {
            String output = modelRunner.runTurn(
                    session,
                    handle,
                    runtimeConfig.maxTurns(),
                    shouldStream,
                    event -> {
                        emitOutputEvents(handle, session.agentId(), event.output());
                        sessionRouter.emit(handle, event);
                    },
                    beforeModelCallHook,
                    toolSpecifications,
                    runtimeConfig.toolLoopGuard()
            );
            if (!shouldApplyCompletionGuard(output)) {
                return output;
            }

            List<ToolCommandResult.TodoItem> openTodos = openTodosForCompletionGuard(session.sessionId());
            if (openTodos.isEmpty()) {
                return output;
            }

            String guardMessage = buildCompletionGuardMessage(openTodos);
            session.context().append(new ContextElement.SystemMsg(guardMessage));
            return modelRunner.runTurn(
                    session,
                    handle,
                    runtimeConfig.maxTurns(),
                    shouldStream,
                    event -> {
                        emitOutputEvents(handle, session.agentId(), event.output());
                        sessionRouter.emit(handle, event);
                    },
                    beforeModelCallHook,
                    toolSpecifications,
                    runtimeConfig.toolLoopGuard()
            );
        } catch (ModelClientException modelFailure) {
            if (sessionManager.isAbortRequested(sessionId) && interruptedModelFailure(modelFailure)) {
                emitOutputEvents(handle, session.agentId(), new SessionOutput.FinalOutput(TURN_ABORTED_TEXT));
                sessionRouter.emit(handle, new SessionOutput.FinalOutput(TURN_ABORTED_TEXT));
                session.context().append(new ContextElement.AssistantMsg(TURN_ABORTED_TEXT, List.of()));
                return TURN_ABORTED_TEXT;
            }
            emitEvent(new SessionEvent.Action.ModelFailure(
                    handle,
                    session.agentId(),
                    modelFailure.reason().code(),
                    modelFailure.statusCode(),
                    modelFailure.doneReason(),
                    modelFailure.getMessage()
            ));
            throw modelFailure;
        } finally {
            sessionManager.clearAbort(sessionId);
        }
    }

    private boolean shouldApplyCompletionGuard(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return false;
        }
        String normalized = modelOutput.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("task completed")
               || normalized.contains("all tasks completed")
               || normalized.contains("work is complete")
               || normalized.contains("completed successfully");
    }

    private List<ToolCommandResult.TodoItem> openTodosForCompletionGuard(UUID sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        ToolCommandResult result = databaseService.execute(new ToolCommand.TodoList(
                sessionId.toString(),
                "open",
                COMPLETION_GUARD_OPEN_TODO_LIST_LIMIT
        ));
        if (result instanceof ToolCommandResult.TodoListed listed) {
            return listed.todos();
        }
        return List.of();
    }

    private String buildCompletionGuardMessage(List<ToolCommandResult.TodoItem> openTodos) {
        if (openTodos == null || openTodos.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Do not conclude completion. Open todos remain and must be resolved before final completion:\n");
        for (int i = 0; i < openTodos.size(); i++) {
            ToolCommandResult.TodoItem todo = openTodos.get(i);
            sb.append(i + 1)
                    .append(") todoId=").append(stateValue(todo.todoId()))
                    .append(" title=").append(stateValue(todo.title()))
                    .append(" status=").append(stateValue(todo.status()))
                    .append('\n');
        }
        sb.append("Continue execution and update the existing todo IDs, do not recreate duplicates.");
        return sb.toString();
    }

    private boolean interruptedModelFailure(ModelClientException failure) {
        if (failure == null) {
            return false;
        }
        String message = failure.getMessage();
        if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("interrupt")) {
            return true;
        }
        Throwable cause = failure.getCause();
        if (cause instanceof InterruptedException) {
            return true;
        }
        return cause != null
               && cause.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("interrupt");
    }

    private void emitCompactionEvent(
            SessionHandle handle,
            String agentId,
            ContextManager.CompactionOutcome compaction
    ) {
        emitEvent(new SessionEvent.Action.ContextCompacted(
                handle,
                agentId,
                compaction.tokensBefore(),
                compaction.tokensAfter(),
                compaction.messagesBefore(),
                compaction.messagesAfter(),
                compaction.compactThreshold(),
                compaction.strategy(),
                compaction.protectedSystemCount(),
                compaction.summarizedCount(),
                compaction.preservedRecentCount()
        ));
    }

    private SessionConfig defaultSessionConfig() {
        return new SessionConfig(
                SessionParams.ofStreaming(true),
                toolManager::execute,
                ignored -> {}
        );
    }

    private void validateEnabledAgentToolSurface() {
        Set<String> availableToolIds = toolManager.toolSpecificationsFor(null).stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toUnmodifiableSet());
        if (availableToolIds.isEmpty()) {
            return;
        }

        for (RuntimeConfig.AgentConfig agent : runtimeConfig.agentsById().values()) {
            if (!agent.enabled()) {
                continue;
            }
            for (String toolId : agent.toolIds()) {
                if ("*".equals(toolId)) {
                    continue;
                }
                if (!availableToolIds.contains(toolId)) {
                    throw new IllegalStateException(
                            "Enabled agent references unresolved tool id: " + agent.id() + " -> " + toolId
                    );
                }
            }
        }
    }

    private ToolResult delegateAgentTool(
            ToolRequest request,
            String targetAgentIdRaw,
            String promptRaw,
            Integer timeoutMsRaw
    ) {
        if (request == null || request.toolCall() == null) {
            return new ToolResult(
                    "",
                    "",
                    ToolPayloads.payload("failed", "validation_error", "Missing tool request", null),
                    true
            );
        }

        String targetAgentId = targetAgentIdRaw == null ? "" : targetAgentIdRaw.trim();
        if (targetAgentId.isEmpty()) {
            return ToolPayloads.failure(request, "validation_error", "targetAgentId is required", null, true);
        }

        String prompt = promptRaw == null ? "" : promptRaw.trim();
        if (prompt.isEmpty()) {
            return ToolPayloads.failure(request, "validation_error", "prompt is required", null, true);
        }

        int timeoutMs = timeoutMsRaw == null ? DEFAULT_DELEGATION_TIMEOUT_MS : timeoutMsRaw;
        if (timeoutMs <= 0) {
            return ToolPayloads.failure(request, "validation_error", "timeoutMs must be > 0", null, true);
        }
        timeoutMs = Math.min(timeoutMs, MAX_DELEGATION_TIMEOUT_MS);

        UUID parentSessionId;
        try {
            parentSessionId = UUID.fromString(request.sessionId());
        } catch (Exception ignored) {
            return ToolPayloads.failure(request, "validation_error", "Invalid parent session id", null, true);
        }

        RuntimeConfig.AgentConfig targetAgent = runtimeConfig.agentsById().get(targetAgentId);
        if (targetAgent == null || !targetAgent.enabled()) {
            return ToolPayloads.failure(request, "target_agent_not_found", "Target agent not found or disabled", null, true);
        }

        long startedAt = System.nanoTime();
        String alias = "delegate-" + targetAgentId + "-" + UUID.randomUUID().toString().substring(0, 8);
        SessionHandle delegated = null;

        try {
            delegated = startSession(targetAgentId, alias);
            UUID delegatedSessionId = delegated.sessionId();
            securityManager.copyPolicy(parentSessionId, delegatedSessionId);

            String output;
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var task = executor.submit(() -> executeTurn(delegatedSessionId, SessionInput.userMessage(prompt)));
                output = task.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("targetAgentId", targetAgentId);
                data.put("delegatedSessionId", delegatedSessionId.toString());
                data.put("timeoutMs", timeoutMs);
                return ToolPayloads.failure(request, "delegate_timeout", "Delegated session timed out", data, true);
            }

            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            ObjectNode data = MAPPER.createObjectNode();
            data.put("targetAgentId", targetAgentId);
            data.put("delegatedSessionId", delegatedSessionId.toString());
            data.put("delegatedAlias", alias);
            data.put("durationMs", durationMs);
            data.put("timeoutMs", timeoutMs);
            data.put("output", output == null ? "" : output);
            return ToolPayloads.success(request, "Delegation completed", data);
        } catch (Exception e) {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("targetAgentId", targetAgentId);
            data.put("errorType", e.getClass().getSimpleName());
            return ToolPayloads.failure(request, "delegate_error", "Delegation failed: " + e.getMessage(), data, true);
        } finally {
            if (delegated != null && delegated.isActive()) {
                closeSession(delegated);
            }
        }
    }

    private SessionConfig securedSessionConfig(String agentId, SessionConfig originalOrNull) {
        SessionConfig original = originalOrNull == null ? defaultSessionConfig() : originalOrNull;
        RuntimeConfig.AgentConfig agentConfig = runtimeConfig.agentsById().get(agentId);
        if (agentConfig == null) {
            throw new IllegalStateException("Agent not found for security policy wiring: " + agentId);
        }
        Function<ToolRequest, ToolResult> delegate = original.toolBridge();

        Function<ToolRequest, ToolResult> securedBridge = request -> {
            Set<String> agentToolIds = activeToolIdsForRequest(agentConfig, request);
            SecurityManager.Decision decision = securityManager.authorize(request, agentToolIds);
            emitSecurityEvent(request, decision);
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
                routingEvent -> onRoutingEvent(agentId, routingEvent),
                securityEvent -> {
                    // Security events are emitted directly from authorization flow.
                },
                sessionException -> emitEvent(new SessionEvent.ErrorEvent(
                        sessionException.sessionHandle(),
                        agentId,
                        sessionException
                ))
        );
    }

    private Set<String> activeToolIdsForRequest(RuntimeConfig.AgentConfig agentConfig, ToolRequest request) {
        if (request == null || request.sessionId() == null) {
            return Set.copyOf(agentConfig.toolIds());
        }
        try {
            UUID sessionId = UUID.fromString(request.sessionId());
            List<String> active = sessionManager.activeToolIds(sessionId);
            if (active.isEmpty()) {
                return Set.copyOf(agentConfig.toolIds());
            }
            return Set.copyOf(active);
        } catch (Exception ignored) {
            return Set.copyOf(agentConfig.toolIds());
        }
    }

    private void emitSecurityEvent(ToolRequest request, SecurityManager.Decision decision) {
        SessionHandle sessionHandle;
        try {
            sessionHandle = sessionManager.handleFor(UUID.fromString(request.sessionId()));
        } catch (Exception ignored) {
            return;
        }
        try {
            SecurityManager.SecurityEvent securityEvent = securityManager.toEvent(request, decision);
            emitEvent(new SessionEvent.SecurityDecision(sessionHandle, request.agentId(), securityEvent));
        } catch (Throwable ignored) {
            // Security event emission failures are observability-only.
        }
    }

    private void onRoutingEvent(String agentId, RoutingEvent routingEvent) {
        if (routingEvent == null) {
            return;
        }
        emitEvent(new SessionEvent.RoutingDecision(
                routingEvent.sessionHandle(),
                agentId == null ? "" : agentId,
                routingEvent
        ));
    }

    private void submitFromRoute(SessionHandle handle, SessionInput input) {
        emitEvent(new SessionEvent.MessageIn(handle, agentIdFor(handle.sessionId()), input));
        sessionManager.submitFromRoute(handle, input);
    }

    private void emitOutputEvents(SessionHandle handle, String agentId, SessionOutput output) {
        emitEvent(new SessionEvent.MessageOut(handle, agentId, output));

        switch (output) {
            case SessionOutput.ToolCallOutput toolCallOutput -> emitEvent(new SessionEvent.Action.ToolCall(
                    handle,
                    agentId,
                    toolCallOutput.toolCall().name(),
                    toolCallOutput.toolCall().id(),
                    toolCallOutput.toolCall().argumentsJson()
            ));
            case SessionOutput.ToolMessageOutput toolMessageOutput -> emitEvent(new SessionEvent.Action.ToolResult(
                    handle,
                    agentId,
                    toolMessageOutput.message().toolName(),
                    toolMessageOutput.message().toolCallId(),
                    toolMessageOutput.message().content()
            ));
            default -> {
                // No action payload for non-tool output variants.
            }
        }
    }

    private void emitEvent(SessionEvent event) {
        if (event == null) {
            return;
        }
        try {
            eventHub.emit(event);
        } catch (Throwable ignored) {
            // Event listeners are observability-only.
        }
        try {
            eventLogSink.append(event);
        } catch (Throwable ignored) {
            // Event logging is observability-only.
        }
    }

    private String agentIdFor(UUID sessionId) {
        try {
            return sessionManager.resume(sessionId).agentId();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void registerLegacyCallbacks(SessionHandle handle, SessionConfig config) {
        registerLegacyCallbacks(handle, LegacySessionCallbacks.from(config == null ? defaultSessionConfig() : config));
    }

    private void registerLegacyCallbacks(SessionHandle handle, LegacySessionCallbacks callbacks) {
        if (handle == null || callbacks == null) {
            return;
        }
        unregisterLegacyCallbacks(handle.sessionId());
        legacyCallbacksBySession.put(handle.sessionId(), callbacks);

        List<SessionEventListenerHandle> handles = new java.util.ArrayList<>();
        handles.add(addEventListener(handle, SessionEvent.RoutingDecision.class, event -> {
            if (callbacks.routingEventLevel() == io.mindspice.magenta.runtime.routing.RoutingEventLevel.NONE) {
                return;
            }
            if (callbacks.routingEventLevel() == io.mindspice.magenta.runtime.routing.RoutingEventLevel.FINAL
                && event.routingEvent() instanceof RoutingEvent.InputResult inputResult
                && inputResult.phase() != io.mindspice.magenta.runtime.routing.InputRoutingEvent.Phase.FINAL) {
                return;
            }
            callbacks.onRouting().accept(event.routingEvent());
        }));
        handles.add(addEventListener(handle, SessionEvent.SecurityDecision.class,
                event -> callbacks.onSecurity().accept(event.securityEvent())));
        handles.add(addEventListener(handle, SessionEvent.ErrorEvent.class,
                event -> callbacks.onError().accept(event.error())));
        legacyCallbackListenerHandles.put(handle.sessionId(), List.copyOf(handles));
    }

    private void unregisterLegacyCallbacks(UUID sessionId) {
        List<SessionEventListenerHandle> handles = legacyCallbackListenerHandles.remove(sessionId);
        if (handles == null || handles.isEmpty()) {
            return;
        }
        for (SessionEventListenerHandle handle : handles) {
            eventHub.off(handle);
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

    private record ToolCallState(
            String toolCallId,
            String toolName,
            String status,
            String target,
            String targetName
    ) {
    }

    private record FileState(String path, String action, String snapshotId) {
    }

    private record TodoUpdateState(
            String todoId,
            String title,
            String status,
            long updatedAtMs
    ) {
    }

    private record ParsedToolState(
            String status,
            String target,
            String targetName,
            String path,
            String snapshotId,
            String todoId,
            String todoTitle,
            String todoStatus,
            long todoUpdatedAtMs
    ) {
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

    private record LegacySessionCallbacks(
            io.mindspice.magenta.runtime.routing.RoutingEventLevel routingEventLevel,
            Consumer<RoutingEvent> onRouting,
            Consumer<SecurityManager.SecurityEvent> onSecurity,
            Consumer<SessionException> onError
    ) {
        static LegacySessionCallbacks from(SessionConfig config) {
            if (config == null) {
                return new LegacySessionCallbacks(
                        io.mindspice.magenta.runtime.routing.RoutingEventLevel.NONE,
                        ignored -> {},
                        ignored -> {},
                        ignored -> {}
                );
            }
            return new LegacySessionCallbacks(
                    config.routingEventLevel(),
                    config.onRouting() == null ? ignored -> {} : config.onRouting(),
                    config.onSecurity() == null ? ignored -> {} : config.onSecurity(),
                    config.onError() == null ? ignored -> {} : config.onError()
            );
        }
    }
}
