package io.mindspice.magenta2.ai.chat.service;

import java.util.UUID;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mindspice.magenta2.ai.agent.job.AgentJobService;
import io.mindspice.magenta2.ai.agent.job.AgentJobStatus;
import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.model.ChatSession;
import io.mindspice.magenta2.ai.chat.model.ChatToolActivity;
import io.mindspice.magenta2.ai.chat.model.ContextUsage;
import io.mindspice.magenta2.ai.chat.plan.ExecutionPlan;
import io.mindspice.magenta2.ai.chat.plan.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanToolContext;
import io.mindspice.magenta2.ai.chat.plan.PlanToolExecutionContext;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.ai.chat.tool.ToolTranscriptService;
import io.mindspice.magenta2.ai.chat.tool.ToolTranscriptService.ToolTranscriptEntry;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.execution.ActiveTurnPhase;
import io.mindspice.magenta2.ai.execution.ActiveTurnRegistry.ActiveTurn;
import io.mindspice.magenta2.ai.execution.ConversationTurnCoordinator;
import io.mindspice.magenta2.ai.execution.MagentaWorkRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class ChatService {
    static final String THINKING_METADATA_KEY = "thinking";
    static final String MESSAGE_THINKING_METADATA_KEY = "magenta.thinking";

    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("(?is)<think>(.*?)</think>");
    private static final int TOOL_ERROR_WINDOW_SIZE = 8;
    private static final int TOOL_ERROR_WINDOW_LIMIT = 5;
    private static final int IDENTICAL_TOOL_CALL_LIMIT = 5;
    private static final int EMPTY_FINAL_RESPONSE_RETRY_LIMIT = 2;
    private static final int PLAN_TURN_REPAIR_RETRY_LIMIT = 2;
    private static final int EXECUTION_COMPLETION_REPAIR_RETRY_LIMIT = 2;
    private static final String OLLAMA_TOOLS_UNSUPPORTED_MESSAGE = "does not support tools";
    static final List<String> PLAN_MODE_TOOLS = List.of(
        "file_list",
        "file_read",
        "file_search",
        "shell_exec",
        "web_search",
        "web_fetch",
        "plan_set_goal",
        "plan_set_task",
        "plan_put_item",
        "plan_delete_item",
        "plan_ask_questions",
        "plan_ready_for_approval"
    );
    private static final List<String> NORMAL_BLOCKED_TOOLS = List.of(
        "plan_update", "plan_set_goal", "plan_set_task", "plan_put_item", "plan_delete_item",
        "plan_ask_questions", "plan_ready_for_approval", "plan_report", "plan_complete"
    );
    private static final List<String> EXECUTION_BLOCKED_TOOLS = List.of(
        "plan_update", "plan_set_goal", "plan_set_task", "plan_put_item", "plan_delete_item",
        "plan_ask_questions", "plan_ready_for_approval"
    );
    private static final String EXECUTE_PLAN_MESSAGE = "Execute the saved plan now. Work through the plan directly and report the completed result.";
    private static final String BEGIN_PLAN_MESSAGE = "The user is ready to plan. Begin the structured planning workflow by asking the user about their goal.";

    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatSessionMetadataRepository chatSessionMetadataRepository;
    private final ChatMarkdownRenderer chatMarkdownRenderer;
    private final AiConfig aiConfig;
    private final ContextManagementAdvisor contextManagementAdvisor;
    private final ContextUsageTracker contextUsageTracker;
    private final ChatModelRouter chatModelRouter;
    private final ToolCallingManager toolCallingManager;
    private final ChatToolRegistry chatToolRegistry;
    private final ToolTranscriptService toolTranscriptService;
    private final PlanService planService;
    private final AgentJobService agentJobService;
    private final ConversationTurnCoordinator turnCoordinator;
    private final Set<String> toolUnsupportedModels = ConcurrentHashMap.newKeySet();

    public ChatService(
        ChatMemory chatMemory,
        ChatMemoryRepository chatMemoryRepository,
        ChatSessionMetadataRepository chatSessionMetadataRepository,
        ChatMarkdownRenderer chatMarkdownRenderer,
        AiConfig aiConfig
    ) {
        this(
            chatMemory,
            chatMemoryRepository,
            chatSessionMetadataRepository,
            chatMarkdownRenderer,
            aiConfig,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public ChatService(
        ChatMemory chatMemory,
        ChatMemoryRepository chatMemoryRepository,
        ChatSessionMetadataRepository chatSessionMetadataRepository,
        ChatMarkdownRenderer chatMarkdownRenderer,
        AiConfig aiConfig,
        ContextManagementAdvisor contextManagementAdvisor,
        ContextUsageTracker contextUsageTracker,
        ChatModelRouter chatModelRouter,
        ToolCallingManager toolCallingManager,
        ChatToolRegistry chatToolRegistry,
        ToolTranscriptService toolTranscriptService,
        PlanService planService
    ) {
        this(
            chatMemory,
            chatMemoryRepository,
            chatSessionMetadataRepository,
            chatMarkdownRenderer,
            aiConfig,
            contextManagementAdvisor,
            contextUsageTracker,
            chatModelRouter,
            toolCallingManager,
            chatToolRegistry,
            toolTranscriptService,
            planService,
            null,
            null
        );
    }

    @Autowired
    public ChatService(
        ChatMemory chatMemory,
        ChatMemoryRepository chatMemoryRepository,
        ChatSessionMetadataRepository chatSessionMetadataRepository,
        ChatMarkdownRenderer chatMarkdownRenderer,
        AiConfig aiConfig,
        ContextManagementAdvisor contextManagementAdvisor,
        ContextUsageTracker contextUsageTracker,
        ChatModelRouter chatModelRouter,
        ToolCallingManager toolCallingManager,
        ChatToolRegistry chatToolRegistry,
        ToolTranscriptService toolTranscriptService,
        @Autowired(required = false) PlanService planService,
        @Autowired(required = false) AgentJobService agentJobService,
        @Autowired(required = false) ConversationTurnCoordinator turnCoordinator
    ) {
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatSessionMetadataRepository = chatSessionMetadataRepository;
        this.chatMarkdownRenderer = chatMarkdownRenderer;
        this.aiConfig = aiConfig;
        this.contextManagementAdvisor = contextManagementAdvisor;
        this.contextUsageTracker = contextUsageTracker;
        this.chatModelRouter = chatModelRouter;
        this.toolCallingManager = toolCallingManager;
        this.chatToolRegistry = chatToolRegistry;
        this.toolTranscriptService = toolTranscriptService;
        this.planService = planService;
        this.agentJobService = agentJobService;
        this.turnCoordinator = turnCoordinator;
    }

    public ChatResponse chat(ChatRequest request) {
        if (!(request instanceof ChatRequest.MsgRequest msgRequest)) {
            throw new IllegalArgumentException("message request is required");
        }
        ResolvedChatRequest resolvedRequest = resolve(msgRequest);
        return chat(resolvedRequest);
    }

    public ChatResponse.MsgResponse chat(String conversationId, String message, String model) {
        ResolvedChatRequest resolvedRequest = resolve(conversationId, message, model);
        return chat(resolvedRequest);
    }

    private ChatResponse.MsgResponse chat(ResolvedChatRequest resolvedRequest) {
        if (turnCoordinator != null) {
            return await(turnCoordinator.submit(
                resolvedRequest.conversationId(),
                MagentaWorkRequest.CHAT_PRIORITY,
                "chat turn " + resolvedRequest.conversationId(),
                () -> chatNow(resolvedRequest, null)
            ));
        }
        return chatNow(resolvedRequest, null);
    }

    private ChatResponse.MsgResponse chatNow(ResolvedChatRequest resolvedRequest, ActiveTurn activeTurn) {
        List<ToolCallback> approvedTools = approvedTools(resolvedRequest);
        if (!approvedTools.isEmpty() && supportsTools(resolvedRequest.model())) {
            try {
                return toolChat(resolvedRequest, approvedTools, null, activeTurn).response();
            } catch (NonTransientAiException exception) {
                if (!isToolUnsupported(exception)) {
                    throw exception;
                }
                rememberToolUnsupportedModel(resolvedRequest.model());
            }
        }
        return plainChat(resolvedRequest);
    }

    private ChatResponse.MsgResponse plainChat(ResolvedChatRequest resolvedRequest) {
        ChatClient.ChatClientRequestSpec prompt = prompt(resolvedRequest);

        ChatClientResponse chatClientResponse = prompt.call().chatClientResponse();
        String response = chatClientResponse.chatResponse().getResult().getOutput().getText();
        chatSessionMetadataRepository.saveModel(resolvedRequest.conversationId(), resolvedRequest.model());
        enqueueTitleJobIfFirstTurn(resolvedRequest);
        return new ChatResponse.MsgResponse(
            resolvedRequest.conversationId(),
            resolvedRequest.model(),
            response,
            maintainContextUsage(resolvedRequest.conversationId(), resolvedRequest.model()).usage(),
            planState(resolvedRequest.conversationId())
        );
    }

    public ResolvedChatRequest resolve(ChatRequest request) {
        if (request instanceof ChatRequest.MsgRequest(String conversationId, String message, String model)) {
            return resolve(conversationId, message, model);
        }
        throw new IllegalArgumentException("message request is required");
    }

    private ResolvedChatRequest resolve(String conversationId, String message, String model) {
        String resolvedConversationId = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        boolean newConversation = !conversationExists(resolvedConversationId);
        String storedModel = storedConversationModel(resolvedConversationId);
        String selectedModel = StringUtils.hasText(model)
                ? model
                : (StringUtils.hasText(storedModel) ? storedModel : defaultModel());
        if (planService != null && planService.mode(resolvedConversationId) == PlanMode.PLAN) {
            selectedModel = planningModel();
        }
        return new ResolvedChatRequest(resolvedConversationId, message, selectedModel, newConversation, true);
    }

    public Flux<ChatMessage> stream(ResolvedChatRequest request) {
        return stream(request, null);
    }

    public Flux<ChatMessage> stream(ResolvedChatRequest request, ActiveTurn activeTurn) {
        if (turnCoordinator != null) {
            return Flux.create(sink -> {
                var future = turnCoordinator.submit(
                    request.conversationId(),
                    MagentaWorkRequest.CHAT_PRIORITY,
                    "streaming chat turn " + request.conversationId(),
                    () -> {
                        streamNow(request, activeTurn).doOnNext(sink::next).blockLast();
                        return null;
                    }
                );
                sink.onCancel(() -> future.cancel(true));
                future.whenComplete((ignored, error) -> {
                    if (error != null) {
                        sink.error(unwrap(error));
                    } else {
                        sink.complete();
                    }
                });
            });
        }
        return streamNow(request, activeTurn);
    }

    private Flux<ChatMessage> streamNow(ResolvedChatRequest request, ActiveTurn activeTurn) {
        List<ToolCallback> approvedTools = approvedTools(request);
        if (!approvedTools.isEmpty() && supportsTools(request.model())) {
            // Try the tool-capable path first. Models that reject tools are remembered and use plain chat later.
            return Flux.<ChatMessage>create(sink -> {
                try {
                    ChatMessage finalMessage = toolChatMessage(request, approvedTools, sink::next, activeTurn);
                    sink.next(finalMessage);
                    sink.complete();
                } catch (RuntimeException exception) {
                    sink.error(exception);
                }
            })
                .onErrorResume(NonTransientAiException.class, exception -> {
                    if (!isToolUnsupported(exception)) {
                        return Flux.error(exception);
                    }
                    rememberToolUnsupportedModel(request.model());
                    return plainStream(request);
                });
        }
        return plainStream(request);
    }

    private Flux<ChatMessage> plainStream(ResolvedChatRequest request) {
        chatSessionMetadataRepository.saveModel(request.conversationId(), request.model());
        return Flux.defer(() -> {
            ChatClientResponse chatClientResponse = prompt(request).call().chatClientResponse();
            StoredContextUsage maintenance = maintainContextUsage(request.conversationId(), request.model());
            if (maintenance.compacted()) {
                return Flux.just(
                    systemMessage(ContextManagementAdvisor.COMPACTION_NOTICE),
                    renderAssistantMessage(chatClientResponse.chatResponse())
                );
            }
            return Flux.just(renderAssistantMessage(chatClientResponse.chatResponse()));
        }).doOnComplete(() -> enqueueTitleJobIfFirstTurn(request));
    }

    public ChatMessage renderAssistantMessage(String text) {
        MessageParts messageParts = splitThinkingFallback(text == null ? "" : text);
        return renderAssistantMessage(messageParts);
    }

    public ChatMessage renderAssistantMessage(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return renderAssistantMessage("");
        }
        return renderAssistantMessage(response.getResult());
    }

    private ChatMessage renderAssistantMessage(org.springframework.ai.chat.model.Generation generation) {
        if (generation == null) {
            return renderAssistantMessage("");
        }
        String text = generation.getOutput() == null || generation.getOutput().getText() == null
            ? ""
            : generation.getOutput().getText();
        String thinking = thinkingText(generation);
        MessageParts messageParts = StringUtils.hasText(thinking)
            ? new MessageParts(text, thinking)
            : splitThinkingFallback(text);
        return renderAssistantMessage(messageParts);
    }

    private ChatMessage renderAssistantMessage(MessageParts messageParts) {
        String visibleText = messageParts.visibleText();
        String thinkingText = messageParts.thinkingText();
        return new ChatMessage(
            "assistant",
            visibleText,
            chatMarkdownRenderer.render(visibleText),
            StringUtils.hasText(thinkingText) ? chatMarkdownRenderer.render(thinkingText) : null
        );
    }

    public void clearConversation(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            chatMemory.clear(conversationId);
            chatSessionMetadataRepository.deleteByConversationId(conversationId);
            if (planService != null) {
                planService.exitPlan(conversationId);
            }
            if (contextUsageTracker != null) {
                contextUsageTracker.clear(conversationId);
            }
        }
    }

    public ChatResponse.MsgResponse beginPlan(String conversationId) {
        return beginPlan(conversationId, null);
    }

    public ChatResponse.MsgResponse beginPlan(String conversationId, String selectedModel) {
        requirePlanService();
        String prePlanningModel = StringUtils.hasText(selectedModel)
            ? selectedModel
            : storedConversationModel(conversationId);
        if (!StringUtils.hasText(prePlanningModel)) {
            prePlanningModel = defaultModel();
        }
        planService.beginPlan(conversationId, prePlanningModel, planningModel());
        return chat(resolve(conversationId, BEGIN_PLAN_MESSAGE, planningModel()).withoutTitleJob());
    }

    public void exitPlan(String conversationId) {
        requirePlanService();
        String prePlanningModel = planService.prePlanningModel(conversationId);
        planService.exitPlan(conversationId);
        if (StringUtils.hasText(prePlanningModel)) {
            chatSessionMetadataRepository.saveModel(conversationId, prePlanningModel);
        }
        if (contextUsageTracker != null) {
            contextUsageTracker.clear(conversationId);
        }
    }

    public ChatResponse.MsgResponse submitPlanAnswer(String conversationId, String answer, String notes) {
        return submitPlanAnswer(conversationId, answer, notes, null);
    }

    public ChatResponse.MsgResponse submitPlanAnswer(String conversationId, String answer, String notes, Integer questionIndex) {
        requirePlanService();
        ExecutionPlan plan = planService.recordPromptAnswer(conversationId, answer, notes, questionIndex);
        if (plan.hasPendingQuestion()) {
            String model = planningModel();
            return new ChatResponse.MsgResponse(
                conversationId,
                model,
                "",
                maintainContextUsage(conversationId, model).usage(),
                planState(conversationId)
            );
        }
        return chat(resolve(conversationId, "Continue planning using the updated structured planning state.", planningModel()).withoutTitleJob());
    }

    public ChatPlanState approvePlan(String conversationId) {
        requirePlanService();
        planService.approvePlan(conversationId);
        return planState(conversationId);
    }

    public ChatPlanState continuePlanning(String conversationId) {
        requirePlanService();
        planService.askQuestions(
            conversationId,
            List.of("What should we clarify, change, or add before approving this plan?")
        );
        return planState(conversationId);
    }

    public ChatPlanState savePlanAsTask(String conversationId) {
        requirePlanService();
        String prePlanningModel = planService.prePlanningModel(conversationId);
        planService.saveAsTask(conversationId);
        if (StringUtils.hasText(prePlanningModel)) {
            chatSessionMetadataRepository.saveModel(conversationId, prePlanningModel);
        }
        return planState(conversationId);
    }

    public ChatResponse.MsgResponse executeSavedPlan(String conversationId, boolean clearContext) {
        return executeSavedPlan(conversationId);
    }

    public ChatResponse.MsgResponse executeSavedPlan(String conversationId) {
        ResolvedChatRequest request = resolveSavedPlanExecution(conversationId);
        try {
            ChatResponse.MsgResponse response = chat(request);
            planService.recordFallbackExecutionEvidence(conversationId);
            if (planService.mode(conversationId) == PlanMode.EXECUTE_PLAN) {
                planService.markNeedsReview(conversationId);
            }
            return new ChatResponse.MsgResponse(
                response.conversationId(),
                response.model(),
                response.response(),
                response.contextUsage(),
                planState(conversationId),
                response.toolActivities()
            );
        } catch (RuntimeException exception) {
            recordExecutionFailure(conversationId, exception);
            throw new IllegalStateException("Plan execution failed: " + rootCauseMessage(exception), exception);
        }
    }

    public ResolvedChatRequest resolveSavedPlanExecution(String conversationId) {
        requirePlanService();
        String model = planService.executionModel(conversationId);
        if (!StringUtils.hasText(model)) {
            model = planService.prePlanningModel(conversationId);
        }
        if (!StringUtils.hasText(model)) {
            model = storedConversationModel(conversationId);
        }
        if (!StringUtils.hasText(model)) {
            model = defaultModel();
        }
        planService.clearConversationForExecution(conversationId);
        if (contextUsageTracker != null) {
            contextUsageTracker.clear(conversationId);
        }
        planService.markExecuting(conversationId);
        return resolve(conversationId, EXECUTE_PLAN_MESSAGE, model).withoutTitleJob();
    }

    public void handlePlanExecutionStreamFinished(String conversationId) {
        requirePlanService();
        planService.recordFallbackExecutionEvidence(conversationId);
        if (planService.mode(conversationId) == PlanMode.EXECUTE_PLAN) {
            planService.markNeedsReview(conversationId);
        }
    }

    public void recordExecutionFailure(String conversationId, RuntimeException exception) {
        requirePlanService();
        try {
            planService.recordExecutionReport(
                conversationId,
                "Execution failed before completion.",
                List.of(),
                List.of(rootCauseMessage(exception)),
                List.of("Saved plan execution did not complete."),
                List.of()
            );
            planService.markNeedsReview(conversationId);
        } catch (RuntimeException reportException) {
            exception.addSuppressed(reportException);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        String message = root == null ? null : root.getMessage();
        if (!StringUtils.hasText(message) && throwable != null) {
            message = throwable.getMessage();
        }
        return StringUtils.hasText(message) ? message : "unknown execution error";
    }

    public ChatPlanState planState(String conversationId) {
        return planService == null ? ChatPlanState.normal() : planService.view(conversationId);
    }

    public List<String> listConversationIds() {
        return listSessions().stream()
            .map(ChatSession::conversationId)
            .toList();
    }

    private List<String> rawConversationIds() {
        List<String> conversationIds = new ArrayList<>(chatMemoryRepository.findConversationIds());
        if (planService != null) {
            for (String planConversationId : planService.listConversationIds()) {
                if (!conversationIds.contains(planConversationId)) {
                    conversationIds.add(planConversationId);
                }
            }
        }
        return conversationIds;
    }

    public List<ChatSession> listSessions() {
        return rawConversationIds().stream()
            .map(conversationId -> new ChatSession(
                conversationId,
                conversationTitle(conversationId),
                conversationTitleJobStatus(conversationId),
                chatSessionMetadataRepository.isFavorite(conversationId),
                chatSessionMetadataRepository.isArchived(conversationId),
                chatSessionMetadataRepository.findUpdatedAt(conversationId).orElse(null)
            ))
            .filter(session -> !session.archived())
            .sorted(java.util.Comparator
                .comparing(ChatSession::favorite).reversed()
                .thenComparing(
                    session -> session.updatedAt() == null ? "" : session.updatedAt(),
                    java.util.Comparator.reverseOrder()
                )
                .thenComparing(ChatSession::conversationId))
            .toList();
    }

    public boolean conversationExists(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        return rawConversationIds().contains(conversationId);
    }

    public List<ChatMessage> history(String conversationId) {
        List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);
        return toHistory(messages);
    }

    public void discardLastUserMessage(String conversationId, String messageText) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        List<Message> messages = new ArrayList<>(chatMemoryRepository.findByConversationId(conversationId));
        if (messages.isEmpty()) {
            return;
        }
        Message lastMessage = messages.get(messages.size() - 1);
        if (lastMessage instanceof UserMessage && java.util.Objects.equals(lastMessage.getText(), messageText)) {
            messages.remove(messages.size() - 1);
            chatMemoryRepository.saveAll(conversationId, messages);
        }
    }

    public ContextUsage contextUsage(String conversationId, String model) {
        if (contextUsageTracker != null) {
            ContextUsage trackedUsage = contextUsageTracker.find(conversationId);
            if (trackedUsage != null) {
                if (trackedUsage.usedTokens() <= trackedUsage.triggerTokens() || contextManagementAdvisor == null) {
                    return trackedUsage;
                }
                return maintainContextUsage(conversationId, model).usage();
            }
        }
        if (contextManagementAdvisor == null || chatMemoryRepository == null) {
            return null;
        }
        String resolvedModel = StringUtils.hasText(model) ? model : storedConversationModel(conversationId);
        return maintainContextUsage(conversationId, resolvedModel).usage();
    }

    public StoredContextUsage maintainContextUsage(String conversationId, String model) {
        if (contextManagementAdvisor == null || chatMemoryRepository == null) {
            ContextUsage trackedUsage = contextUsageTracker == null ? null : contextUsageTracker.find(conversationId);
            return new StoredContextUsage(trackedUsage, false);
        }
        String resolvedModel = StringUtils.hasText(model) ? model : storedConversationModel(conversationId);
        ContextManagementAdvisor.StoredContextMaintenance maintenance =
            contextManagementAdvisor.maintainStoredContext(conversationId, resolvedModel);
        return new StoredContextUsage(maintenance.usage(), maintenance.compacted());
    }

    public String storedConversationModel(String conversationId) {
        return chatSessionMetadataRepository.findModel(conversationId).orElse(null);
    }

    public String conversationTitle(String conversationId) {
        String storedTitle = chatSessionMetadataRepository.findTitle(conversationId).orElse(null);
        if (StringUtils.hasText(storedTitle)) {
            return storedTitle;
        }
        if (planService == null) {
            return null;
        }
        return planService.activePlan(conversationId)
            .map(plan -> {
                if (StringUtils.hasText(plan.goal())) {
                    return "Plan for " + compactTitle(plan.goal());
                }
                return StringUtils.hasText(plan.title()) ? plan.title() : null;
            })
            .filter(StringUtils::hasText)
            .orElse(null);
    }

    public ChatSession renameConversation(String conversationId, String title) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId is required");
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("title is required");
        }
        String normalizedTitle = title.trim().replaceAll("\\s+", " ");
        if (normalizedTitle.length() > 120) {
            normalizedTitle = normalizedTitle.substring(0, 120).replaceAll("\\s+\\S*$", "").trim();
        }
        chatSessionMetadataRepository.updateTitle(conversationId, normalizedTitle);
        return session(conversationId);
    }

    private String compactTitle(String value) {
        String title = value.trim().replaceAll("\\s+", " ");
        String prefix = "Plan for ";
        int maxGoalLength = Math.max(16, 80 - prefix.length());
        if (title.length() <= maxGoalLength) {
            return title;
        }
        return title.substring(0, maxGoalLength).replaceAll("\\s+\\S*$", "").trim();
    }

    public ChatSession setConversationFavorite(String conversationId, boolean favorite) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId is required");
        }
        chatSessionMetadataRepository.setFavorite(conversationId, favorite);
        return session(conversationId);
    }

    public ChatSession setConversationArchived(String conversationId, boolean archived) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId is required");
        }
        chatSessionMetadataRepository.setArchived(conversationId, archived);
        return session(conversationId);
    }

    private ChatSession session(String conversationId) {
        return new ChatSession(
            conversationId,
            conversationTitle(conversationId),
            conversationTitleJobStatus(conversationId),
            chatSessionMetadataRepository.isFavorite(conversationId),
            chatSessionMetadataRepository.isArchived(conversationId),
            chatSessionMetadataRepository.findUpdatedAt(conversationId).orElse(null)
        );
    }

    public String conversationTitleJobStatus(String conversationId) {
        if (agentJobService == null) {
            return null;
        }
        return agentJobService.latestConversationTitleStatus(conversationId)
            .map(AgentJobStatus::name)
            .orElse(null);
    }

    public String newConversationId() {
        return UUID.randomUUID().toString();
    }

    public String defaultModel() {
        String defaultAgentName = aiConfig.defaultAgent();
        String modelKey = aiConfig.agents().get(defaultAgentName).model();
        return aiConfig.models().get(modelKey).remoteModelName();
    }

    public String planningModel() {
        if (aiConfig == null || aiConfig.models() == null) {
            return defaultModel();
        }
        String modelKey = aiConfig.resolvedPlanningModelKey();
        ModelConfig model = aiConfig.models().get(modelKey);
        return model == null ? defaultModel() : model.remoteModelName();
    }

    public List<String> availableModels() {
        return aiConfig.models().values().stream()
            .map(config -> config.remoteModelName())
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private ChatResponse.MsgResponse toolChat(ResolvedChatRequest request, List<ToolCallback> approvedTools) {
        return toolChat(request, approvedTools, null, null).response();
    }

    private ToolChatResult toolChat(
        ResolvedChatRequest request,
        List<ToolCallback> approvedTools,
        Consumer<ChatMessage> toolMessageConsumer,
        ActiveTurn activeTurn
    ) {
        if (chatModelRouter == null || toolCallingManager == null || contextManagementAdvisor == null || toolTranscriptService == null) {
            throw new IllegalStateException("Tool execution requires model routing, ToolCallingManager, context management, and tool transcripts");
        }
        chatSessionMetadataRepository.saveModel(request.conversationId(), request.model());

        List<Message> currentInstructions = currentInstructions(request);
        List<Message> currentSystemInstructions = currentInstructions.stream()
            .filter(SystemMessage.class::isInstance)
            .toList();
        ContextManagementAdvisor.PreparedPrompt preparedPrompt = contextManagementAdvisor.preparePrompt(
            request.conversationId(),
            currentInstructions,
            request.model()
        );
        OllamaChatOptions options = toolOptions(request.model(), approvedTools);
        Prompt prompt = new Prompt(preparedPrompt.messages(), options);
        PlanMode mode = planService == null ? PlanMode.NORMAL : planService.mode(request.conversationId());
        PlanToolExecutionContext.set(new PlanToolContext(request.conversationId(), mode));
        try {
            phase(activeTurn, ActiveTurnPhase.MODEL_CALL);
            org.springframework.ai.chat.model.ChatResponse response = chatModelRouter.chatModel(request.model()).call(prompt);
            List<Message> messagesToPersist = new ArrayList<>();
            List<ChatToolActivity> toolActivities = new ArrayList<>();
            List<String> thinkingParts = new ArrayList<>();
            ToolLoopGuard toolLoopGuard = new ToolLoopGuard();
            List<Message> activeToolMessages = new ArrayList<>();
            boolean compactionNoticeEmitted = false;

            ToolUseAbort toolUseAbort = null;
            List<Message> conversationHistory = null;
            int emptyFinalResponseRetries = 0;
            int planTurnRepairRetries = 0;
            int executionCompletionRepairRetries = 0;
            boolean continueModelLoop = true;
            while (continueModelLoop) {
                continueModelLoop = false;
                while (response != null && response.hasToolCalls()) {
                    try {
                        toolLoopGuard.recordToolCalls(response.getResult().getOutput().getToolCalls());
                    } catch (ToolUseAbort abort) {
                        toolUseAbort = abort;
                        conversationHistory = new ArrayList<>(prompt.getInstructions());
                        break;
                    }
                    collectThinking(response, thinkingParts);
                    phase(activeTurn, ActiveTurnPhase.TOOL_CALL);
                    int promptMessageCount = prompt.getInstructions().size();
                    ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
                    List<ToolTranscriptEntry> toolTranscriptEntries = toolTranscriptEntries(response, toolExecutionResult);
                    List<ChatMessage> pendingToolMessages = new ArrayList<>();
                    for (ToolTranscriptEntry entry : toolTranscriptEntries) {
                        Message transcriptMessage = toolTranscriptService.message(entry);
                        messagesToPersist.add(transcriptMessage);
                        ChatMessage toolMessage = toolMessage(transcriptMessage);
                        if (toolMessage.toolActivity() != null) {
                            toolActivities.add(toolMessage.toolActivity());
                        }
                        pendingToolMessages.add(toolMessage);
                    }
                    conversationHistory = new ArrayList<>(toolExecutionResult.conversationHistory());
                    if (conversationHistory.size() > promptMessageCount) {
                        activeToolMessages.addAll(conversationHistory.subList(promptMessageCount, conversationHistory.size()));
                    }
                    phase(activeTurn, ActiveTurnPhase.TOOL_CHECKPOINT);
                    if (activeTurn != null) {
                        java.util.Optional<String> nextInterrupt = activeTurn.pollInterrupt();
                        while (nextInterrupt.isPresent()) {
                            String interrupt = nextInterrupt.get();
                            UserMessage interruptMessage = new UserMessage(interrupt);
                            conversationHistory.add(interruptMessage);
                            activeToolMessages.add(interruptMessage);
                            messagesToPersist.add(interruptMessage);
                            if (toolMessageConsumer != null) {
                                toolMessageConsumer.accept(userMessage(interrupt));
                            }
                            nextInterrupt = activeTurn.pollInterrupt();
                        }
                    }
                    try {
                        toolLoopGuard.recordToolResponses(toolExecutionResult);
                    } catch (ToolUseAbort abort) {
                        toolUseAbort = abort;
                        break;
                    }
                    ContextManagementAdvisor.ToolLoopPrompt checkpoint = contextManagementAdvisor.prepareToolLoopPrompt(
                        request.conversationId(),
                        activeToolMessages,
                        currentSystemInstructions,
                        request.model()
                    );
                    activeToolMessages = new ArrayList<>(checkpoint.activeMessages());
                    conversationHistory = new ArrayList<>(checkpoint.messages());
                    prompt = new Prompt(conversationHistory, options);
                    if (checkpoint.compacted() && !compactionNoticeEmitted) {
                        compactionNoticeEmitted = true;
                        if (!hasCompactionNotice(request.conversationId())) {
                            messagesToPersist.add(compactionNoticeMessage());
                        }
                        if (toolMessageConsumer != null) {
                            toolMessageConsumer.accept(systemMessage(ContextManagementAdvisor.COMPACTION_NOTICE));
                        }
                    }
                    if (!checkpoint.toolUseAllowed()) {
                        toolUseAbort = new ToolUseAbort(
                            "Context is too large to safely continue tool use after compaction."
                        );
                        break;
                    }
                    recordContextUsage(request.conversationId(), checkpoint.usage());
                    if (toolMessageConsumer != null) {
                        pendingToolMessages.forEach(toolMessageConsumer);
                    }
                    phase(activeTurn, ActiveTurnPhase.MODEL_CALL);
                    response = chatModelRouter.chatModel(request.model()).call(prompt);
                }

                if (toolUseAbort != null) {
                    Message controlMessage = toolUseAbortControlMessage(toolUseAbort);
                    messagesToPersist.add(controlMessage);
                    activeToolMessages.add(controlMessage);
                    ContextManagementAdvisor.ToolLoopPrompt checkpoint = contextManagementAdvisor.prepareToolLoopPrompt(
                        request.conversationId(),
                        activeToolMessages,
                        currentSystemInstructions,
                        request.model()
                    );
                    recordContextUsage(request.conversationId(), checkpoint.usage());
                    if (!checkpoint.toolUseAllowed()) {
                        throw new IllegalStateException(
                            "Context is too large to send safely after tool compaction: "
                                + checkpoint.usage().usedTokens() + " estimated tokens exceeds trigger budget "
                                + checkpoint.usage().triggerTokens()
                        );
                    }
                    prompt = new Prompt(checkpoint.messages(), toolFinalOptions(request.model()));
                    phase(activeTurn, ActiveTurnPhase.MODEL_CALL);
                    response = chatModelRouter.chatModel(request.model()).call(prompt);
                    toolUseAbort = null;
                    continueModelLoop = true;
                    continue;
                }

                if (isEmptyFinalResponse(response) && emptyFinalResponseRetries < EMPTY_FINAL_RESPONSE_RETRY_LIMIT) {
                    collectThinking(response, thinkingParts);
                    emptyFinalResponseRetries++;
                    Message controlMessage = emptyFinalResponseControlMessage(mode);
                    List<Message> retryMessages = new ArrayList<>(prompt.getInstructions());
                    retryMessages.add(controlMessage);
                    prompt = new Prompt(retryMessages, prompt.getOptions());
                    phase(activeTurn, ActiveTurnPhase.MODEL_CALL);
                    response = chatModelRouter.chatModel(request.model()).call(prompt);
                    continueModelLoop = true;
                    continue;
                }

                if (requiresPlanTurnRepair(request.conversationId(), mode)
                    && planTurnRepairRetries < PLAN_TURN_REPAIR_RETRY_LIMIT) {
                    collectThinking(response, thinkingParts);
                    planTurnRepairRetries++;
                    Message controlMessage = invalidPlanTurnControlMessage();
                    List<Message> retryMessages = new ArrayList<>(prompt.getInstructions());
                    retryMessages.add(controlMessage);
                    prompt = new Prompt(retryMessages, prompt.getOptions());
                    phase(activeTurn, ActiveTurnPhase.MODEL_CALL);
                    response = chatModelRouter.chatModel(request.model()).call(prompt);
                    continueModelLoop = true;
                    continue;
                }

                if (requiresExecutionCompletionRepair(request.conversationId(), mode)
                    && executionCompletionRepairRetries < EXECUTION_COMPLETION_REPAIR_RETRY_LIMIT) {
                    collectThinking(response, thinkingParts);
                    executionCompletionRepairRetries++;
                    Message controlMessage = invalidExecutionCompletionControlMessage();
                    List<Message> retryMessages = new ArrayList<>(prompt.getInstructions());
                    retryMessages.add(controlMessage);
                    prompt = new Prompt(retryMessages, prompt.getOptions());
                    phase(activeTurn, ActiveTurnPhase.MODEL_CALL);
                    response = chatModelRouter.chatModel(request.model()).call(prompt);
                    continueModelLoop = true;
                }
            }
            String forcedPlanningQuestion = null;
            if (requiresPlanTurnRepair(request.conversationId(), mode)) {
                forcedPlanningQuestion = "What should we clarify, change, or add before continuing this plan?";
                planService.askQuestions(request.conversationId(), List.of(forcedPlanningQuestion));
            }
            phase(activeTurn, ActiveTurnPhase.COMPLETING);
            collectThinking(response, thinkingParts);

            AssistantMessage finalAssistantMessage = StringUtils.hasText(forcedPlanningQuestion)
                ? new AssistantMessage(forcedPlanningQuestion)
                : response == null || response.getResult() == null
                ? new AssistantMessage("")
                : assistantMessageWithThinking(response.getResult(), combinedThinking(thinkingParts));
            if (finalAssistantMessage != null) {
                messagesToPersist.add(finalAssistantMessage);
            }
            contextManagementAdvisor.saveAssistantMessages(request.conversationId(), messagesToPersist);
            StoredContextUsage maintenance = maintainContextUsage(request.conversationId(), request.model());
            if (maintenance.compacted() && toolMessageConsumer != null) {
                toolMessageConsumer.accept(systemMessage(ContextManagementAdvisor.COMPACTION_NOTICE));
            }
            ChatResponse.MsgResponse chatResponse = new ChatResponse.MsgResponse(
                request.conversationId(),
                request.model(),
                finalAssistantMessage == null ? "" : finalAssistantMessage.getText(),
                maintenance.usage(),
                planState(request.conversationId()),
                List.copyOf(toolActivities)
            );
            ChatMessage finalMessage = renderAssistantMessage(assistantMessageParts(
                finalAssistantMessage,
                finalAssistantMessage == null ? "" : finalAssistantMessage.getText()
            ));
            enqueueTitleJobIfFirstTurn(request);
            return new ToolChatResult(chatResponse, finalMessage);
        } finally {
            PlanToolExecutionContext.clear();
        }
    }

    private List<Message> currentInstructions(ResolvedChatRequest request) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = effectiveSystemPrompt(request);
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(request.message()));
        return messages;
    }

    private ChatMessage toolChatMessage(
        ResolvedChatRequest request,
        List<ToolCallback> approvedTools,
        Consumer<ChatMessage> toolMessageConsumer,
        ActiveTurn activeTurn
    ) {
        ToolChatResult result = toolChat(request, approvedTools, toolMessageConsumer, activeTurn);
        List<ChatMessage> history = history(request.conversationId());
        if (!history.isEmpty()) {
            ChatMessage lastMessage = history.get(history.size() - 1);
            if (isAssistantRole(lastMessage.role())) {
                return lastMessage;
            }
        }
        return result.finalMessage();
    }

    private ChatMessage userMessage(String text) {
        return new ChatMessage("user", text, chatMarkdownRenderer.render(text), null);
    }

    private ChatMessage systemMessage(String text) {
        return new ChatMessage("system", text, chatMarkdownRenderer.render(text), null);
    }

    public ChatMessage systemNotice(String text) {
        return systemMessage(text);
    }

    private void recordContextUsage(String conversationId, io.mindspice.magenta2.ai.chat.model.ContextUsage usage) {
        if (contextUsageTracker != null) {
            contextUsageTracker.record(conversationId, usage);
        }
    }

    private SystemMessage compactionNoticeMessage() {
        return new SystemMessage(ContextManagementAdvisor.NOTICE_PREFIX + ContextManagementAdvisor.COMPACTION_NOTICE);
    }

    private boolean hasCompactionNotice(String conversationId) {
        if (chatMemoryRepository == null || contextManagementAdvisor == null) {
            return false;
        }
        return chatMemoryRepository.findByConversationId(conversationId).stream()
            .anyMatch(contextManagementAdvisor::isCompactionNotice);
    }

    private void phase(ActiveTurn activeTurn, ActiveTurnPhase phase) {
        if (activeTurn != null) {
            activeTurn.phase(phase);
        }
    }

    private OllamaChatOptions toolOptions(String model, List<ToolCallback> approvedTools) {
        OllamaChatOptions options = StringUtils.hasText(model)
            ? chatModelRouter.ollamaOptions(model)
            : OllamaChatOptions.builder().build();
        options.setInternalToolExecutionEnabled(false);
        options.setToolCallbacks(approvedTools);
        return options;
    }

    private OllamaChatOptions toolFinalOptions(String model) {
        OllamaChatOptions options = StringUtils.hasText(model)
            ? chatModelRouter.ollamaOptions(model)
            : OllamaChatOptions.builder().build();
        options.setInternalToolExecutionEnabled(false);
        options.setToolCallbacks(List.of());
        return options;
    }

    private SystemMessage toolUseAbortControlMessage(ToolUseAbort abort) {
        StringBuilder message = new StringBuilder("""
            Tool use was aborted by Magenta before another tool call was allowed.
            Reason: %s
            """.formatted(abort.getMessage()).trim());
        if (!abort.recentErrors().isEmpty()) {
            message.append("\nRecent tool errors:");
            for (String error : abort.recentErrors()) {
                message.append("\n- ").append(error);
            }
        }
        message.append(
            "\nThe prior tool results remain available in the conversation context. "
                + "Do not request more tools for this turn; explain the failure state or continue from the available information."
        );
        return new SystemMessage(message.toString());
    }

    private boolean isEmptyFinalResponse(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.hasToolCalls()) {
            return false;
        }
        AssistantMessage output = response.getResult().getOutput();
        return output == null || !StringUtils.hasText(output.getText());
    }

    private SystemMessage emptyFinalResponseControlMessage(PlanMode mode) {
        String instruction = switch (mode) {
            case PLAN -> """
                Your previous response had thinking but no user-visible message and no tool calls, so Magenta cannot treat it as a completed planning turn.
                Continue the PLAN-mode turn now. Use planning edit tools if the draft state should change, then end by calling plan_ask_questions or plan_ready_for_approval.
                Do not return an empty assistant message.
                """;
            case EXECUTE_PLAN -> """
                Your previous response had thinking but no user-visible message and no tool calls, so Magenta cannot treat it as completed saved-plan execution.
                Continue executing the approved plan now. Use tools as needed and call plan_complete before any final user-visible completion answer.
                Do not return an empty assistant message.
                """;
            case NORMAL -> """
                Your previous response had thinking but no user-visible message and no tool calls.
                Continue the turn now with a concise user-visible answer or an appropriate tool call.
                Do not return an empty assistant message.
                """;
        };
        return new SystemMessage(instruction.trim());
    }

    private boolean requiresPlanTurnRepair(String conversationId, PlanMode mode) {
        if (mode != PlanMode.PLAN || planService == null) {
            return false;
        }
        ChatPlanState state = planService.view(conversationId);
        return !"READY_FOR_APPROVAL".equals(state.status())
            && !StringUtils.hasText(state.promptQuestion());
    }

    private boolean requiresExecutionCompletionRepair(String conversationId, PlanMode mode) {
        return mode == PlanMode.EXECUTE_PLAN
            && planService != null
            && planService.mode(conversationId) == PlanMode.EXECUTE_PLAN;
    }

    private SystemMessage invalidPlanTurnControlMessage() {
        return new SystemMessage("""
            Your PLAN-mode turn attempted to finish without a queued clarification question or a plan ready for approval.
            Continue the same turn now. Update the draft with keyed planning tools as needed, then call exactly one terminal planning tool:
            - plan_ask_questions if the user needs to clarify, choose an approach, confirm constraints, or provide more context.
            - plan_ready_for_approval only when the plan is complete enough to execute without guessing.
            Do not finish with ordinary assistant text.
            """.trim());
    }

    private SystemMessage invalidExecutionCompletionControlMessage() {
        return new SystemMessage("""
            Your saved-plan execution attempted to finish without validator-gated completion.
            Continue the same execution turn now. You may keep working or report incomplete work, but before any final user-visible completion answer you must call plan_complete.
            plan_complete must include one evidence entry per approved validation criterion, formatted as:
            Criterion: <exact criterion text> | Evidence: <specific proof>
            If a criterion is not met, include it in unmetCriteria with the specific missing work or evidence.
            If validation fails, address the returned remediation and call plan_complete again.
            Do not finish with ordinary assistant text until plan_complete has passed validation.
            """.trim());
    }

    private List<ToolTranscriptEntry> toolTranscriptEntries(
        org.springframework.ai.chat.model.ChatResponse response,
        ToolExecutionResult toolExecutionResult
    ) {
        if (toolExecutionResult == null || toolExecutionResult.conversationHistory() == null) {
            return List.of();
        }
        List<AssistantMessage.ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();
        Map<String, AssistantMessage.ToolCall> callsById = toolCalls.stream()
            .collect(java.util.stream.Collectors.toMap(
                AssistantMessage.ToolCall::id,
                java.util.function.Function.identity(),
                (left, right) -> left
            ));

        ToolResponseMessage latestToolResponseMessage = null;
        for (Message message : toolExecutionResult.conversationHistory()) {
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                latestToolResponseMessage = toolResponseMessage;
            }
        }
        if (latestToolResponseMessage == null) {
            return List.of();
        }

        return latestToolResponseMessage.getResponses().stream()
            .map(toolResponse -> {
                AssistantMessage.ToolCall toolCall = callsById.get(toolResponse.id());
                String arguments = toolCall == null ? "" : toolCall.arguments();
                return toolTranscriptService.fullResultEntry(
                    toolResponse.id(),
                    toolResponse.name(),
                    arguments,
                    toolResponse.responseData()
                );
            })
            .toList();
    }

    private ChatMessage toolMessage(Message message) {
        String text = toolTranscriptService.renderForHistory(message);
        return new ChatMessage(
            "tool",
            text,
            chatMarkdownRenderer.render(text),
            null,
            toolTranscriptService.activityFor(message)
        );
    }

    static final class ToolLoopGuard {
        private final Map<String, Integer> identicalToolCallCounts = new java.util.HashMap<>();
        private final Deque<ToolOutcome> recentToolOutcomes = new ArrayDeque<>();
        private int recentErrorCount = 0;

        void recordToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
            for (AssistantMessage.ToolCall toolCall : toolCalls == null ? List.<AssistantMessage.ToolCall>of() : toolCalls) {
                String key = toolCall.name() + "\n" + normalizeArguments(toolCall.arguments());
                int count = identicalToolCallCounts.merge(key, 1, Integer::sum);
                if (count >= IDENTICAL_TOOL_CALL_LIMIT) {
                    throw new ToolUseAbort("Tool execution stopped after " + count + " identical calls to " + toolCall.name());
                }
            }
        }

        void recordToolResponses(ToolExecutionResult toolExecutionResult) {
            ToolResponseMessage latestToolResponseMessage = latestToolResponseMessage(toolExecutionResult);
            if (latestToolResponseMessage == null) {
                recordToolResult(false, null);
                return;
            }
            for (ToolResponseMessage.ToolResponse response : latestToolResponseMessage.getResponses()) {
                String responseData = response.responseData();
                recordToolResult(isToolError(responseData), responseData);
            }
        }

        private ToolResponseMessage latestToolResponseMessage(ToolExecutionResult toolExecutionResult) {
            if (toolExecutionResult == null || toolExecutionResult.conversationHistory() == null) {
                return null;
            }
            ToolResponseMessage latest = null;
            for (Message message : toolExecutionResult.conversationHistory()) {
                if (message instanceof ToolResponseMessage toolResponseMessage) {
                    latest = toolResponseMessage;
                }
            }
            return latest;
        }

        private void recordToolResult(boolean error, String responseData) {
            recentToolOutcomes.addLast(new ToolOutcome(error, error ? summarizeToolError(responseData) : null));
            if (error) {
                recentErrorCount++;
            }
            while (recentToolOutcomes.size() > TOOL_ERROR_WINDOW_SIZE) {
                if (recentToolOutcomes.removeFirst().error()) {
                    recentErrorCount--;
                }
            }
            if (recentToolOutcomes.size() == TOOL_ERROR_WINDOW_SIZE && recentErrorCount >= TOOL_ERROR_WINDOW_LIMIT) {
                throw new ToolUseAbort(
                    "Tool execution stopped after " + recentErrorCount + " errors in the last "
                        + TOOL_ERROR_WINDOW_SIZE + " tool responses",
                    recentErrors()
                );
            }
        }

        private List<String> recentErrors() {
            return recentToolOutcomes.stream()
                .filter(ToolOutcome::error)
                .map(ToolOutcome::detail)
                .filter(StringUtils::hasText)
                .toList();
        }

        private String summarizeToolError(String responseData) {
            if (!StringUtils.hasText(responseData)) {
                return "Tool returned an empty error response.";
            }
            String summary = responseData.replaceAll("\\s+", " ").trim();
            return summary.length() > 500 ? summary.substring(0, 500) + " [truncated]" : summary;
        }

        private boolean isToolError(String responseData) {
            if (!StringUtils.hasText(responseData)) {
                return false;
            }
            String normalized = responseData.toLowerCase(java.util.Locale.ROOT);
            return normalized.contains("\"exitcode\":1")
                || normalized.contains("\"exitcode\":2")
                || normalized.contains("\"exitcode\":3")
                || normalized.contains("\"exitcode\":4")
                || normalized.contains("\"exitcode\":5")
                || normalized.contains("\"exitcode\":6")
                || normalized.contains("\"exitcode\":7")
                || normalized.contains("\"exitcode\":8")
                || normalized.contains("\"exitcode\":9")
                || normalized.contains("\"timedout\":true")
                || normalized.contains("exception")
                || normalized.contains("error")
                || normalized.contains("failed")
                || normalized.contains("does not match current file content")
                || normalized.contains("not found")
                || normalized.contains("permission denied");
        }

        private String normalizeArguments(String arguments) {
            return StringUtils.hasText(arguments) ? arguments.replaceAll("\\s+", " ").trim() : "";
        }

        private record ToolOutcome(boolean error, String detail) {
        }
    }

    static final class ToolUseAbort extends IllegalStateException {
        private final List<String> recentErrors;

        ToolUseAbort(String message) {
            this(message, List.of());
        }

        ToolUseAbort(String message, List<String> recentErrors) {
            super(message);
            this.recentErrors = recentErrors == null ? List.of() : List.copyOf(recentErrors);
        }

        List<String> recentErrors() {
            return recentErrors;
        }
    }

    private List<ToolCallback> approvedTools(ResolvedChatRequest request) {
        if (chatToolRegistry == null || aiConfig == null || !StringUtils.hasText(aiConfig.defaultAgent())) {
            return List.of();
        }
        AgentConfig defaultAgent = aiConfig.agents().get(aiConfig.defaultAgent());
        if (defaultAgent == null) {
            return List.of();
        }
        PlanMode mode = planService == null ? PlanMode.NORMAL : planService.mode(request.conversationId());
        if (mode == PlanMode.PLAN) {
            return chatToolRegistry.resolveApprovedTools(defaultAgent.approvedTools(), PLAN_MODE_TOOLS);
        }
        if (mode == PlanMode.EXECUTE_PLAN) {
            return chatToolRegistry.resolveApprovedTools(defaultAgent.approvedTools()).stream()
                .filter(callback -> !EXECUTION_BLOCKED_TOOLS.contains(callback.getToolDefinition().name()))
                .toList();
        }
        return chatToolRegistry.resolveApprovedTools(defaultAgent.approvedTools()).stream()
            .filter(callback -> !NORMAL_BLOCKED_TOOLS.contains(callback.getToolDefinition().name()))
            .toList();
    }

    private boolean supportsTools(String model) {
        return !StringUtils.hasText(model) || !toolUnsupportedModels.contains(model);
    }

    private void rememberToolUnsupportedModel(String model) {
        if (StringUtils.hasText(model)) {
            toolUnsupportedModels.add(model);
        }
    }

    static boolean isToolUnsupported(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(OLLAMA_TOOLS_UNSUPPORTED_MESSAGE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }



    private void enqueueTitleJobIfFirstTurn(ResolvedChatRequest request) {
        if (agentJobService == null || request == null || !request.newConversation() || !request.titleJobEligible()) {
            return;
        }
        agentJobService.submitConversationTitle(request.conversationId(), request.model(), request.message());
    }

    private ChatClient.ChatClientRequestSpec prompt(ResolvedChatRequest request) {
        ChatClient chatClient = chatModelRouter == null
            ? null
            : ChatClient.builder(chatModelRouter.chatModel(request.model()))
                .defaultAdvisors(contextManagementAdvisor)
                .build();
        if (chatClient == null) {
            throw new IllegalStateException("Chat execution requires model routing");
        }
        ChatClient.ChatClientRequestSpec prompt = chatClient.prompt()
            .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, request.conversationId()));

        String systemPrompt = effectiveSystemPrompt(request);
        if (StringUtils.hasText(systemPrompt)) {
            prompt = prompt.system(systemPrompt);
        }

        prompt = prompt.user(request.message());

        if (StringUtils.hasText(request.model())) {
            prompt = prompt.options(chatModelRouter.ollamaOptions(request.model()));
        }
        return prompt;
    }

    String defaultSystemPrompt() {
        if (aiConfig == null || !StringUtils.hasText(aiConfig.defaultAgent()) || aiConfig.agents() == null) {
            return null;
        }
        AgentConfig defaultAgent = aiConfig.agents().get(aiConfig.defaultAgent());
        return defaultAgent == null ? null : defaultAgent.systemPrompt();
    }

    String effectiveSystemPrompt(ResolvedChatRequest request) {
        PlanMode mode = planService == null ? PlanMode.NORMAL : planService.mode(request.conversationId());
        String systemPrompt = defaultSystemPrompt();
        String runtimePrompt = planService == null ? "" : planService.runtimeInstructions(request.conversationId());
        if (mode == PlanMode.PLAN) {
            return runtimePrompt;
        }
        if (!StringUtils.hasText(runtimePrompt)) {
            return systemPrompt;
        }
        if (!StringUtils.hasText(systemPrompt)) {
            return runtimePrompt;
        }
        return systemPrompt + "\n\n" + runtimePrompt;
    }

    private void requirePlanService() {
        if (planService == null) {
            throw new IllegalStateException("Plan service is not available");
        }
    }

    private List<ChatMessage> toHistory(List<Message> messages) {
        return messages.stream()
            .filter(message -> contextManagementAdvisor == null || !contextManagementAdvisor.isHiddenSummary(message))
            .map(message -> {
                if (toolTranscriptService != null && toolTranscriptService.isToolTranscript(message)) {
                    return toolMessage(message);
                }
                String role = message.getMessageType().getValue();
                String sourceText = contextManagementAdvisor != null && contextManagementAdvisor.isCompactionNotice(message)
                    ? contextManagementAdvisor.visibleNoticeText(message)
                    : (message.getText() == null ? "" : message.getText());
                MessageParts messageParts = isAssistantRole(role)
                    ? assistantMessageParts(message, sourceText)
                    : new MessageParts(sourceText, "");
                String visibleText = messageParts.visibleText();
                String thinkingText = messageParts.thinkingText();

                return new ChatMessage(
                    role,
                    visibleText,
                    chatMarkdownRenderer.render(visibleText),
                    StringUtils.hasText(thinkingText) ? chatMarkdownRenderer.render(thinkingText) : null
                );
            })
            .toList();
    }

    private boolean isAssistantRole(String role) {
        return "assistant".equalsIgnoreCase(role);
    }

    private MessageParts assistantMessageParts(Message message, String sourceText) {
        String thinkingText = message == null || message.getMetadata() == null
            ? null
            : stringValue(message.getMetadata().get(MESSAGE_THINKING_METADATA_KEY));
        return StringUtils.hasText(thinkingText)
            ? new MessageParts(sourceText, thinkingText)
            : splitThinkingFallback(sourceText);
    }

    AssistantMessage assistantMessageWithThinking(org.springframework.ai.chat.model.Generation generation) {
        return assistantMessageWithThinking(generation, thinkingText(generation));
    }

    AssistantMessage assistantMessageWithThinking(
        org.springframework.ai.chat.model.Generation generation,
        String thinking
    ) {
        AssistantMessage output = generation == null ? null : generation.getOutput();
        if (output == null) {
            return new AssistantMessage("");
        }
        if (!StringUtils.hasText(thinking)) {
            return output;
        }
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(output.getMetadata());
        metadata.put(MESSAGE_THINKING_METADATA_KEY, thinking);
        return AssistantMessage.builder()
            .content(output.getText())
            .properties(metadata)
            .toolCalls(output.getToolCalls())
            .media(output.getMedia())
            .build();
    }

    private void collectThinking(org.springframework.ai.chat.model.ChatResponse response, List<String> thinkingParts) {
        if (response == null || response.getResult() == null) {
            return;
        }
        String thinking = thinkingText(response.getResult());
        if (StringUtils.hasText(thinking)) {
            thinkingParts.add(thinking.trim());
        }
    }

    private String combinedThinking(List<String> thinkingParts) {
        return thinkingParts.stream()
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private String thinkingText(org.springframework.ai.chat.model.Generation generation) {
        if (generation == null || generation.getMetadata() == null) {
            return null;
        }
        return stringValue(generation.getMetadata().get(THINKING_METADATA_KEY));
    }

    private String stringValue(Object value) {
        return value instanceof String text && StringUtils.hasText(text) ? text : null;
    }

    private MessageParts splitThinkingFallback(String text) {
        Matcher matcher = THINK_TAG_PATTERN.matcher(text);
        StringBuilder visible = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        int lastEnd = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            foundAny = true;
            visible.append(text, lastEnd, matcher.start());
            String innerThinking = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (!innerThinking.isEmpty()) {
                if (!thinking.isEmpty()) {
                    thinking.append("\n\n");
                }
                thinking.append(innerThinking);
            }
            lastEnd = matcher.end();
        }

        if (!foundAny) {
            return new MessageParts(text, "");
        }

        visible.append(text.substring(lastEnd));
        return new MessageParts(visible.toString().trim(), thinking.toString().trim());
    }

    private record MessageParts(String visibleText, String thinkingText) {
    }

    private record ToolChatResult(ChatResponse.MsgResponse response, ChatMessage finalMessage) {
    }

    public record StoredContextUsage(ContextUsage usage, boolean compacted) {
    }

    private <T> T await(java.util.concurrent.CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chat turn was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof ExecutionException || current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record ResolvedChatRequest(
        String conversationId,
        String message,
        String model,
        boolean newConversation,
        boolean titleJobEligible
    ) {
        public ResolvedChatRequest(String conversationId, String message, String model) {
            this(conversationId, message, model, false, false);
        }

        public ResolvedChatRequest(String conversationId, String message, String model, boolean newConversation) {
            this(conversationId, message, model, newConversation, false);
        }

        ResolvedChatRequest withoutTitleJob() {
            return new ResolvedChatRequest(conversationId, message, model, newConversation, false);
        }
    }
}
