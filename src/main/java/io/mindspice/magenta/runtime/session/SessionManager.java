package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

public final class SessionManager {
    private static final String ANON_TASK_LABEL = "anon task";

    private final RuntimeConfig runtimeConfig;
    private final ContextManager contextManager;
    private final BiFunction<UUID, SessionInput, String> turnSubmitter;
    private final int sessionQueueCapacity;
    private final ExecutorService turnExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<UUID, Session> sessionsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> activeTaskBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, List<String>> activeToolIdsBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Thread> activeTurnThreadsBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AtomicBoolean> abortRequestedBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SessionQueueState> queuesBySession = new ConcurrentHashMap<>();

    public SessionManager(
            RuntimeConfig runtimeConfig,
            ContextManager contextManager,
            BiFunction<UUID, SessionInput, String> turnSubmitter
    ) {
        this.runtimeConfig = runtimeConfig;
        this.contextManager = contextManager;
        this.turnSubmitter = Objects.requireNonNull(turnSubmitter, "turnSubmitter");
        this.sessionQueueCapacity = runtimeConfig.sessionQueueCapacity();
    }

    public Session start(String agentId, String alias, SessionConfig sessionConfig) {
        return start(agentId, alias, sessionConfig, null, null);
    }

    public Session start(String agentId, String alias, SessionConfig sessionConfig, String launchTaskOrNull) {
        return start(agentId, alias, sessionConfig, launchTaskOrNull, null);
    }

    public Session start(String agentId, String alias, SessionConfig sessionConfig, Context existingContextOrNull) {
        return start(agentId, alias, sessionConfig, null, existingContextOrNull);
    }

    public Session start(
            String agentId,
            String alias,
            SessionConfig sessionConfig,
            String launchTaskOrNull,
            Context existingContextOrNull
    ) {
        RuntimeConfig.AgentConfig agent = requireAgent(agentId);
        RuntimeConfig.ModelConfig model = requireModel(agent.modelId(), agentId);

        UUID sessionId = UUID.randomUUID();
        String effectiveAlias = normalizeAlias(alias, sessionId);
        String initialTaskId = resolveLaunchTask(agent, launchTaskOrNull);
        List<ContextElement.PromptSystemElement> systemPrompts = resolveSystemPrompts(agent.promptIds(), initialTaskId);
        List<String> effectiveToolIds = resolveEffectiveToolIds(agent.toolIds(), initialTaskId);
        Context context = contextManager.loadContext(sessionId, existingContextOrNull, systemPrompts);

        Session session = new Session(
                sessionId,
                agent.id(),
                effectiveAlias,
                model,
                effectiveToolIds,
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
        if (initialTaskId != null) {
            activeTaskBySession.put(sessionId, initialTaskId);
        }
        activeToolIdsBySession.put(sessionId, effectiveToolIds);

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
        Session forked = start(source.agentId(), alias, config, copiedContext);
        String activeTask = activeTaskBySession.get(sourceSessionId);
        if (activeTask != null && !activeTask.isBlank()) {
            activeTaskBySession.put(forked.sessionId(), activeTask);
        }
        List<String> activeTools = activeToolIdsBySession.get(sourceSessionId);
        if (activeTools != null && !activeTools.isEmpty()) {
            activeToolIdsBySession.put(forked.sessionId(), List.copyOf(activeTools));
        }
        return forked;
    }

    public List<Session> list() {
        return sessionsById.values().stream()
                .sorted(Comparator.comparing(Session::createdAt))
                .toList();
    }

    public void close(UUID sessionId) {
        requestAbort(sessionId);
        SessionQueueState queueState = queuesBySession.remove(sessionId);
        if (queueState != null) {
            queueState.clear();
        }
        sessionsById.remove(sessionId);
        activeTaskBySession.remove(sessionId);
        activeToolIdsBySession.remove(sessionId);
        activeTurnThreadsBySession.remove(sessionId);
        abortRequestedBySession.remove(sessionId);
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
                runtimeConfig.exposedTaskIds(agent),
                agent.workflows(),
                agent.toolIds(),
                agent.enabled(),
                resolveSystemPrompt(session.context().snapshot()),
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
        Objects.requireNonNull(handle, "handle");
        Session session = null;
        try {
            UUID sessionId = handle.sessionId();
            session = resume(sessionId);
            enqueueOrThrow(sessionId, input, null);
        } catch (SessionQueueFullException queueFullException) {
            emitOnError(session, queueFullException);
            throw queueFullException;
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

    public String submitAndAwait(SessionHandle handle, SessionInput input, long timeoutMs) throws TimeoutException {
        Objects.requireNonNull(handle, "handle");
        UUID sessionId = handle.sessionId();
        Session session = resume(sessionId);
        if (input == null) {
            return "";
        }
        CompletableFuture<String> completion = new CompletableFuture<>();
        try {
            enqueueOrThrow(sessionId, input, completion);
            return completion.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            completion.cancel(true);
            throw timeoutException;
        } catch (SessionQueueFullException queueFullException) {
            emitOnError(session, queueFullException);
            throw queueFullException;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            emitOnError(session, interruptedException);
            throw new IllegalStateException("Interrupted while waiting for queued turn completion", interruptedException);
        } catch (java.util.concurrent.ExecutionException executionException) {
            Throwable cause = executionException.getCause() == null ? executionException : executionException.getCause();
            emitOnError(session, cause);
            throw new IllegalStateException("Queued turn failed: " + cause.getMessage(), cause);
        }
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

    public String activeTaskId(UUID sessionId) {
        if (sessionId == null) {
            return "";
        }
        String taskId = activeTaskBySession.get(sessionId);
        return taskId == null ? "" : taskId;
    }

    public List<String> activeToolIds(UUID sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        Session session = resume(sessionId);
        List<String> active = activeToolIdsBySession.get(sessionId);
        if (active != null) {
            return active;
        }
        List<String> fallback = resolveEffectiveToolIds(requireAgent(session.agentId()).toolIds(), activeTaskBySession.get(sessionId));
        activeToolIdsBySession.put(sessionId, fallback);
        return fallback;
    }

    public boolean requestAbort(UUID sessionId) {
        if (sessionId == null || !isActive(sessionId)) {
            return false;
        }
        abortFlag(sessionId).set(true);
        boolean clearedPending = clearPendingQueue(sessionId) > 0;
        Thread activeThread = activeTurnThreadsBySession.get(sessionId);
        if (activeThread == null) {
            return clearedPending;
        }
        activeThread.interrupt();
        return true;
    }

    public boolean turnInProgress(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        Thread thread = activeTurnThreadsBySession.get(sessionId);
        return thread != null && thread.isAlive();
    }

    public boolean isAbortRequested(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        AtomicBoolean flag = abortRequestedBySession.get(sessionId);
        return flag != null && flag.get();
    }

    public void clearAbort(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        AtomicBoolean flag = abortRequestedBySession.get(sessionId);
        if (flag != null) {
            flag.set(false);
        }
    }

    public String applyTask(UUID sessionId, String taskRef) {
        Session session = resume(sessionId);
        RuntimeConfig.AgentConfig agent = requireAgent(session.agentId());
        String taskId = resolveTaskForAgent(agent, taskRef);

        List<ContextElement> current = session.context().snapshot();
        int firstNonSystem = firstNonSystemIndex(current);
        List<ContextElement> replacement = new ArrayList<>();
        replacement.addAll(resolveSystemPrompts(agent.promptIds(), taskId));
        replacement.addAll(current.subList(firstNonSystem, current.size()));
        session.context().replaceAll(replacement);

        activeTaskBySession.put(sessionId, taskId);
        List<String> effectiveTools = resolveEffectiveToolIds(agent.toolIds(), taskId);
        activeToolIdsBySession.put(sessionId, effectiveTools);
        return taskId;
    }

    public String applyAnonTaskPrompt(UUID sessionId, String promptText) {
        Session session = resume(sessionId);
        RuntimeConfig.AgentConfig agent = requireAgent(session.agentId());
        String normalizedPrompt = promptText == null ? "" : promptText.trim();
        if (normalizedPrompt.isBlank()) {
            throw new IllegalStateException("Task prompt text is required");
        }

        List<ContextElement> current = session.context().snapshot();
        int firstNonSystem = firstNonSystemIndex(current);
        List<ContextElement> replacement = new ArrayList<>();
        replacement.addAll(resolveSystemPrompts(agent.promptIds(), null));
        replacement.add(new ContextElement.SystemTaskMsg(normalizedPrompt));
        replacement.addAll(current.subList(firstNonSystem, current.size()));
        session.context().replaceAll(replacement);

        activeTaskBySession.put(sessionId, ANON_TASK_LABEL);
        return ANON_TASK_LABEL;
    }

    public RuntimeConfig.ModelConfig switchModel(UUID sessionId, String modelRef) {
        Session session = resume(sessionId);
        if (turnInProgress(sessionId)) {
            throw new IllegalStateException("Cannot switch model while a turn is in progress");
        }
        RuntimeConfig.ModelConfig nextModel = resolveModel(modelRef);
        Session updated = new Session(
                session.sessionId(),
                session.agentId(),
                session.alias(),
                nextModel,
                session.toolIds(),
                session.context(),
                session.sessionConfig(),
                session.createdAt()
        );
        sessionsById.put(sessionId, updated);
        return nextModel;
    }

    public List<RuntimeConfig.ModelConfig> availableModels() {
        return runtimeConfig.modelsById().values().stream()
                .filter(RuntimeConfig.ModelConfig::enabled)
                .sorted(Comparator.comparing(RuntimeConfig.ModelConfig::id))
                .toList();
    }

    public List<ContextElement.PromptSystemElement> clearConversationKeepSystemMessages(UUID sessionId) {
        Session session = resume(sessionId);
        List<ContextElement> current = session.context().snapshot();
        int firstNonSystem = firstNonSystemIndex(current);
        List<ContextElement> replacement;
        if (firstNonSystem <= 0) {
            replacement = List.of();
        } else {
            List<ContextElement> retained = new ArrayList<>();
            for (ContextElement message : current.subList(0, firstNonSystem)) {
                if (isStateSystemMessage(message)) {
                    continue;
                }
                retained.add(message);
            }
            replacement = List.copyOf(retained);
        }
        session.context().replaceAll(replacement);
        return replacement.stream()
                .filter(ContextElement.PromptSystemElement.class::isInstance)
                .map(ContextElement.PromptSystemElement.class::cast)
                .toList();
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

    private RuntimeConfig.ModelConfig resolveModel(String modelRef) {
        String normalized = normalizeReferenceToken(modelRef);
        if (normalized.isBlank()) {
            throw new IllegalStateException("Model name is required");
        }

        RuntimeConfig.ModelConfig direct = runtimeConfig.modelsById().get(normalized);
        if (direct != null) {
            if (!direct.enabled()) {
                throw new IllegalStateException("Model is disabled: " + normalized);
            }
            return direct;
        }

        String basename = basename(normalized);
        List<RuntimeConfig.ModelConfig> matches = runtimeConfig.modelsById().entrySet().stream()
                .filter(entry -> basename(entry.getKey()).equals(basename))
                .map(Map.Entry::getValue)
                .filter(RuntimeConfig.ModelConfig::enabled)
                .sorted(Comparator.comparing(RuntimeConfig.ModelConfig::id))
                .toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            String candidates = matches.stream().map(RuntimeConfig.ModelConfig::id).collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalStateException("Ambiguous model reference '" + modelRef + "'. Matches: " + candidates);
        }
        throw new IllegalStateException("Model not found or disabled: " + modelRef);
    }

    private String resolveSystemPrompt(List<ContextElement> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        java.util.ArrayList<String> prompts = new java.util.ArrayList<>();
        for (ContextElement message : messages) {
            if (message instanceof ContextElement.PromptSystemElement prompt) {
                prompts.add(prompt.content());
                continue;
            }
            if (ContextElement.isStateSystemElement(message)) {
                continue;
            }
            break;
        }
        if (prompts.isEmpty()) {
            return "";
        }
        return String.join("\n\n", prompts);
    }

    private List<ContextElement.PromptSystemElement> resolveSystemPrompts(List<String> promptIds, String taskIdOrNull) {
        ArrayList<ContextElement.PromptSystemElement> prompts = new ArrayList<>();
        if (promptIds == null || promptIds.isEmpty()) {
            appendTaskPrompts(prompts, taskIdOrNull);
            return List.copyOf(prompts);
        }

        boolean firstPrompt = true;
        for (String promptId : promptIds) {
            String prompt = runtimeConfig.promptsById().get(promptId);
            if (prompt == null) {
                throw new IllegalStateException("Prompt ID not found: " + promptId);
            }
            if (firstPrompt) {
                prompts.add(new ContextElement.SystemCoreMsg(prompt));
                firstPrompt = false;
            } else {
                prompts.add(new ContextElement.SystemAgentMsg(prompt));
            }
        }
        appendTaskPrompts(prompts, taskIdOrNull);
        return List.copyOf(prompts);
    }

    private String resolveLaunchTask(RuntimeConfig.AgentConfig agent, String launchTaskOrNull) {
        if (launchTaskOrNull == null || launchTaskOrNull.isBlank()) {
            return null;
        }
        return resolveTaskForAgent(agent, launchTaskOrNull);
    }

    private void appendTaskPrompts(List<ContextElement.PromptSystemElement> prompts, String taskIdOrNull) {
        if (taskIdOrNull == null || taskIdOrNull.isBlank()) {
            return;
        }
        RuntimeConfig.TaskConfig task = runtimeConfig.tasksById().get(taskIdOrNull);
        if (task == null || !task.enabled()) {
            throw new IllegalStateException("Task not found or disabled: " + taskIdOrNull);
        }
        for (String promptId : task.promptIds()) {
            String prompt = runtimeConfig.promptsById().get(promptId);
            if (prompt == null) {
                throw new IllegalStateException("Task prompt ID not found: " + taskIdOrNull + " -> " + promptId);
            }
            prompts.add(new ContextElement.SystemTaskMsg(prompt));
        }
    }

    private String resolveTaskForAgent(RuntimeConfig.AgentConfig agent, String taskRef) {
        if (taskRef == null || taskRef.isBlank()) {
            throw new IllegalStateException("Task name is required");
        }
        List<String> exposedTasks = runtimeConfig.exposedTaskIds(agent);
        if (exposedTasks.isEmpty()) {
            throw new IllegalStateException("Agent has no exposed tasks: " + agent.id());
        }

        String normalized = normalizeReferenceToken(taskRef);
        if (exposedTasks.contains(normalized)) {
            return normalized;
        }

        if (normalized.contains(".") && !normalized.contains("/")) {
            String dotted = normalized.replace('.', '/');
            if (exposedTasks.contains(dotted)) {
                return dotted;
            }
        }

        String basename = basename(normalized);
        List<String> matches = exposedTasks.stream()
                .filter(candidate -> basename(candidate).equals(basename))
                .sorted()
                .toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Ambiguous task reference '" + taskRef + "'. Matches: " + String.join(", ", matches));
        }
        throw new IllegalStateException("Task not exposed by agent '" + agent.id() + "': " + taskRef);
    }

    private List<String> resolveEffectiveToolIds(List<String> agentToolIds, String taskIdOrNull) {
        List<String> base = normalizeToolIds(agentToolIds);
        if (taskIdOrNull == null || taskIdOrNull.isBlank()) {
            return base;
        }

        RuntimeConfig.TaskConfig task = runtimeConfig.tasksById().get(taskIdOrNull);
        if (task == null || !task.enabled()) {
            throw new IllegalStateException("Task not found or disabled for tool resolution: " + taskIdOrNull);
        }
        List<String> taskToolIds = normalizeToolIds(task.toolIds());
        if (taskToolIds.isEmpty()) {
            return base;
        }

        boolean baseAll = base.contains("*");
        boolean taskAll = taskToolIds.contains("*");

        if (baseAll && taskAll) {
            return List.of("*");
        }
        if (baseAll) {
            return taskToolIds;
        }
        if (taskAll) {
            return base;
        }

        Set<String> taskSet = Set.copyOf(taskToolIds);
        LinkedHashSet<String> intersection = new LinkedHashSet<>();
        for (String toolId : base) {
            if (taskSet.contains(toolId)) {
                intersection.add(toolId);
            }
        }
        return List.copyOf(intersection);
    }

    private List<String> normalizeToolIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String token = entry.trim();
            if (token.isEmpty()) {
                continue;
            }
            normalized.add(token);
        }
        return List.copyOf(normalized);
    }

    private int firstNonSystemIndex(List<ContextElement> context) {
        int index = 0;
        while (index < context.size()) {
            if (!ContextElement.isSystemElement(context.get(index))) {
                break;
            }
            index++;
        }
        return index;
    }

    private boolean isStateSystemMessage(ContextElement message) {
        return ContextElement.isStateSystemElement(message);
    }

    private String normalizeReferenceToken(String rawToken) {
        if (rawToken == null) {
            return "";
        }
        String token = rawToken.trim().replace('\\', '/');
        if (token.startsWith("./")) {
            token = token.substring(2);
        }
        String lower = token.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".md")) {
            int dot = token.lastIndexOf('.');
            if (dot > 0) {
                token = token.substring(0, dot);
            }
        }
        return token;
    }

    private String basename(String id) {
        int slash = id.lastIndexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }

    private String normalizeAlias(String alias, UUID sessionId) {
        if (alias == null || alias.isBlank()) {
            return "session-" + sessionId.toString().substring(0, 8);
        }
        return alias.trim();
    }

    private void enqueueOrThrow(UUID sessionId, SessionInput input, CompletableFuture<String> completion) {
        if (input == null) {
            if (completion != null) {
                completion.complete("");
            }
            return;
        }
        SessionQueueState queueState = queuesBySession.computeIfAbsent(
                sessionId,
                ignored -> new SessionQueueState(sessionQueueCapacity)
        );
        boolean accepted = queueState.offer(new QueuedInput(input, completion));
        if (!accepted) {
            if (completion != null) {
                completion.completeExceptionally(new SessionQueueFullException(sessionId, sessionQueueCapacity));
            }
            throw new SessionQueueFullException(sessionId, sessionQueueCapacity);
        }
        scheduleDrain(sessionId, queueState);
    }

    private void scheduleDrain(UUID sessionId, SessionQueueState queueState) {
        if (!queueState.draining.compareAndSet(false, true)) {
            return;
        }
        turnExecutor.submit(() -> drainQueue(sessionId, queueState));
    }

    private void drainQueue(UUID sessionId, SessionQueueState queueState) {
        try {
            while (true) {
                QueuedInput queuedInput = queueState.poll();
                if (queuedInput == null) {
                    break;
                }
                try {
                    String output = processQueuedTurn(sessionId, queuedInput.input());
                    if (queuedInput.completion() != null) {
                        queuedInput.completion().complete(output == null ? "" : output);
                    }
                } catch (Throwable throwable) {
                    if (queuedInput.completion() != null) {
                        queuedInput.completion().completeExceptionally(throwable);
                    }
                }
            }
        } finally {
            queueState.draining.set(false);
            if (!queueState.isEmpty()) {
                scheduleDrain(sessionId, queueState);
            }
        }
    }

    private String processQueuedTurn(UUID sessionId, SessionInput input) {
        Session session = null;
        try {
            session = resume(sessionId);
            if (input == null) {
                return "";
            }
            clearAbort(sessionId);
            activeTurnThreadsBySession.put(sessionId, Thread.currentThread());
            return turnSubmitter.apply(sessionId, input);
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            if (message != null && message.contains("Session not found")) {
                throw e;
            }
            emitOnError(session, e);
            throw e;
        } catch (Throwable throwable) {
            emitOnError(session, throwable);
            throw throwable;
        } finally {
            activeTurnThreadsBySession.remove(sessionId, Thread.currentThread());
            Thread.interrupted();
        }
    }

    private int clearPendingQueue(UUID sessionId) {
        SessionQueueState queueState = queuesBySession.get(sessionId);
        if (queueState == null) {
            return 0;
        }
        List<QueuedInput> pending = queueState.drainAll();
        for (QueuedInput queuedInput : pending) {
            if (queuedInput.completion() != null) {
                queuedInput.completion().completeExceptionally(
                        new IllegalStateException("Queued input cleared due to abort")
                );
            }
        }
        return pending.size();
    }

    private AtomicBoolean abortFlag(UUID sessionId) {
        return abortRequestedBySession.computeIfAbsent(sessionId, ignored -> new AtomicBoolean(false));
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

    private record QueuedInput(SessionInput input, CompletableFuture<String> completion) {
    }

    private static final class SessionQueueState {
        private final int capacity;
        private final ArrayDeque<QueuedInput> queue = new ArrayDeque<>();
        private final AtomicBoolean draining = new AtomicBoolean(false);

        private SessionQueueState(int capacity) {
            this.capacity = capacity;
        }

        private synchronized boolean offer(QueuedInput queuedInput) {
            if (queue.size() >= capacity) {
                return false;
            }
            queue.addLast(queuedInput);
            return true;
        }

        private synchronized QueuedInput poll() {
            return queue.pollFirst();
        }

        private synchronized boolean isEmpty() {
            return queue.isEmpty();
        }

        private synchronized List<QueuedInput> drainAll() {
            List<QueuedInput> drained = new ArrayList<>(queue);
            queue.clear();
            return drained;
        }

        private synchronized void clear() {
            queue.clear();
        }
    }
}
