package io.mindspice.magenta2.ai.chat.service;

import java.util.UUID;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String OLLAMA_TOOLS_UNSUPPORTED_MESSAGE = "does not support tools";
    static final List<String> PLAN_MODE_TOOLS = List.of(
        "file_list",
        "file_read",
        "file_search",
        "shell_exec",
        "web_search",
        "web_fetch",
        "plan_save"
    );
    private static final List<String> NORMAL_BLOCKED_TOOLS = List.of("plan_save", "plan_report");
    private static final List<String> EXECUTION_BLOCKED_TOOLS = List.of("plan_save");
    private static final String EXECUTE_PLAN_MESSAGE = "Execute the saved plan now. Work through the plan directly and report the completed result.";
    private static final String BEGIN_PLAN_MESSAGE = "The user is ready to plan. Begin the planning conversation.";

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
        @Autowired(required = false) AgentJobService agentJobService
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
        List<ToolCallback> approvedTools = approvedTools(resolvedRequest);
        if (!approvedTools.isEmpty() && supportsTools(resolvedRequest.model())) {
            try {
                return toolChat(resolvedRequest, approvedTools);
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
            contextUsage(resolvedRequest.conversationId(), resolvedRequest.model()),
            planState(resolvedRequest.conversationId())
        );
    }

    public ResolvedChatRequest resolve(ChatRequest request) {
        if (request instanceof ChatRequest.MsgRequest msgRequest) {
            return resolve(msgRequest.conversationId(), msgRequest.message(), msgRequest.model());
        }
        throw new IllegalArgumentException("message request is required");
    }

    public Flux<ChatMessage> stream(ResolvedChatRequest request) {
        List<ToolCallback> approvedTools = approvedTools(request);
        if (!approvedTools.isEmpty() && supportsTools(request.model())) {
            // Try the tool-capable path first. Models that reject tools are remembered and use plain chat later.
            return Flux.<ChatMessage>create(sink -> {
                try {
                    ChatMessage finalMessage = toolChatMessage(request, approvedTools, sink::next);
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
        requirePlanService();
        planService.beginPlan(conversationId);
        return chat(resolve(conversationId, BEGIN_PLAN_MESSAGE, storedConversationModel(conversationId)).withoutTitleJob());
    }

    public void exitPlan(String conversationId) {
        requirePlanService();
        planService.exitPlan(conversationId);
        if (contextUsageTracker != null) {
            contextUsageTracker.clear(conversationId);
        }
    }

    public ChatResponse.MsgResponse executeSavedPlan(String conversationId, boolean clearContext) {
        requirePlanService();
        String model = storedConversationModel(conversationId);
        if (clearContext) {
            planService.clearConversationForExecution(conversationId);
            if (contextUsageTracker != null) {
                contextUsageTracker.clear(conversationId);
            }
        }
        planService.markExecuting(conversationId);
        try {
            ChatResponse.MsgResponse response = chat(resolve(conversationId, EXECUTE_PLAN_MESSAGE, model).withoutTitleJob());
            planService.recordFallbackExecutionEvidence(conversationId);
            planService.markNeedsReview(conversationId);
            return new ChatResponse.MsgResponse(
                response.conversationId(),
                response.model(),
                response.response(),
                response.contextUsage(),
                planState(conversationId),
                response.toolActivities()
            );
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    public ChatPlanState planState(String conversationId) {
        return planService == null ? ChatPlanState.normal() : planService.view(conversationId);
    }

    public List<String> listConversationIds() {
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
        return listConversationIds().stream()
            .map(conversationId -> new ChatSession(
                conversationId,
                conversationTitle(conversationId),
                conversationTitleJobStatus(conversationId)
            ))
            .toList();
    }

    public boolean conversationExists(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        return chatMemoryRepository.findConversationIds().contains(conversationId);
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
                return trackedUsage;
            }
        }
        if (contextManagementAdvisor == null || chatMemoryRepository == null) {
            return null;
        }
        String resolvedModel = StringUtils.hasText(model) ? model : storedConversationModel(conversationId);
        return contextManagementAdvisor.estimateStoredUsage(conversationId, resolvedModel);
    }

    public String storedConversationModel(String conversationId) {
        return chatSessionMetadataRepository.findModel(conversationId).orElse(null);
    }

    public String conversationTitle(String conversationId) {
        return chatSessionMetadataRepository.findTitle(conversationId).orElse(null);
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

    public List<String> availableModels() {
        return aiConfig.models().values().stream()
            .map(config -> config.remoteModelName())
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private ChatResponse.MsgResponse toolChat(ResolvedChatRequest request, List<ToolCallback> approvedTools) {
        return toolChat(request, approvedTools, null).response();
    }

    private ToolChatResult toolChat(
        ResolvedChatRequest request,
        List<ToolCallback> approvedTools,
        Consumer<ChatMessage> toolMessageConsumer
    ) {
        if (chatModelRouter == null || toolCallingManager == null || contextManagementAdvisor == null || toolTranscriptService == null) {
            throw new IllegalStateException("Tool execution requires model routing, ToolCallingManager, context management, and tool transcripts");
        }
        chatSessionMetadataRepository.saveModel(request.conversationId(), request.model());

        List<Message> currentInstructions = currentInstructions(request);
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
            org.springframework.ai.chat.model.ChatResponse response = chatModelRouter.chatModel(request.model()).call(prompt);
            List<Message> messagesToPersist = new ArrayList<>();
            List<ChatToolActivity> toolActivities = new ArrayList<>();
            List<String> thinkingParts = new ArrayList<>();
            ToolLoopGuard toolLoopGuard = new ToolLoopGuard();

            while (response != null && response.hasToolCalls()) {
                toolLoopGuard.recordToolCalls(response.getResult().getOutput().getToolCalls());
                collectThinking(response, thinkingParts);
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
                toolLoopGuard.recordToolResponses(toolExecutionResult);
                List<ToolTranscriptEntry> toolTranscriptEntries = toolTranscriptEntries(response, toolExecutionResult);
                for (ToolTranscriptEntry entry : toolTranscriptEntries) {
                    Message transcriptMessage = toolTranscriptService.message(entry);
                    messagesToPersist.add(transcriptMessage);
                    ChatMessage toolMessage = toolMessage(transcriptMessage);
                    if (toolMessage.toolActivity() != null) {
                        toolActivities.add(toolMessage.toolActivity());
                    }
                    if (toolMessageConsumer != null) {
                        toolMessageConsumer.accept(toolMessage);
                    }
                }
                prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
                response = chatModelRouter.chatModel(request.model()).call(prompt);
            }
            collectThinking(response, thinkingParts);

            AssistantMessage finalAssistantMessage = response == null || response.getResult() == null
                ? new AssistantMessage("")
                : assistantMessageWithThinking(response.getResult(), combinedThinking(thinkingParts));
            if (finalAssistantMessage != null) {
                messagesToPersist.add(finalAssistantMessage);
            }
            contextManagementAdvisor.saveAssistantMessages(request.conversationId(), messagesToPersist);
            if (contextUsageTracker != null) {
                contextUsageTracker.record(
                    request.conversationId(),
                    contextManagementAdvisor.estimateStoredUsage(request.conversationId(), request.model())
                );
            }
            ChatResponse.MsgResponse chatResponse = new ChatResponse.MsgResponse(
                request.conversationId(),
                request.model(),
                finalAssistantMessage == null ? "" : finalAssistantMessage.getText(),
                contextUsage(request.conversationId(), request.model()),
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
        Consumer<ChatMessage> toolMessageConsumer
    ) {
        ToolChatResult result = toolChat(request, approvedTools, toolMessageConsumer);
        List<ChatMessage> history = history(request.conversationId());
        if (!history.isEmpty()) {
            ChatMessage lastMessage = history.get(history.size() - 1);
            if (isAssistantRole(lastMessage.role())) {
                return lastMessage;
            }
        }
        return result.finalMessage();
    }

    private OllamaChatOptions toolOptions(String model, List<ToolCallback> approvedTools) {
        OllamaChatOptions options = StringUtils.hasText(model)
            ? OllamaChatOptions.builder().model(model).enableThinking().build()
            : OllamaChatOptions.builder().enableThinking().build();
        options.setInternalToolExecutionEnabled(false);
        options.setToolCallbacks(approvedTools);
        return options;
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
        private final Deque<Boolean> recentToolErrors = new ArrayDeque<>();
        private int recentErrorCount = 0;

        void recordToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
            for (AssistantMessage.ToolCall toolCall : toolCalls == null ? List.<AssistantMessage.ToolCall>of() : toolCalls) {
                String key = toolCall.name() + "\n" + normalizeArguments(toolCall.arguments());
                int count = identicalToolCallCounts.merge(key, 1, Integer::sum);
                if (count >= IDENTICAL_TOOL_CALL_LIMIT) {
                    throw new IllegalStateException(
                        "Tool execution stopped after " + count + " identical calls to " + toolCall.name()
                    );
                }
            }
        }

        void recordToolResponses(ToolExecutionResult toolExecutionResult) {
            ToolResponseMessage latestToolResponseMessage = latestToolResponseMessage(toolExecutionResult);
            if (latestToolResponseMessage == null) {
                recordToolResult(false);
                return;
            }
            for (ToolResponseMessage.ToolResponse response : latestToolResponseMessage.getResponses()) {
                recordToolResult(isToolError(response.responseData()));
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

        private void recordToolResult(boolean error) {
            recentToolErrors.addLast(error);
            if (error) {
                recentErrorCount++;
            }
            while (recentToolErrors.size() > TOOL_ERROR_WINDOW_SIZE) {
                if (Boolean.TRUE.equals(recentToolErrors.removeFirst())) {
                    recentErrorCount--;
                }
            }
            if (recentToolErrors.size() == TOOL_ERROR_WINDOW_SIZE && recentErrorCount >= TOOL_ERROR_WINDOW_LIMIT) {
                throw new IllegalStateException(
                    "Tool execution stopped after " + recentErrorCount + " errors in the last "
                        + TOOL_ERROR_WINDOW_SIZE + " tool responses"
                );
            }
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

    private ResolvedChatRequest resolve(String conversationId, String message, String model) {
        String resolvedConversationId = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        boolean newConversation = !conversationExists(resolvedConversationId);
        String storedModel = storedConversationModel(resolvedConversationId);
        String selectedModel = StringUtils.hasText(model)
            ? model
            : (StringUtils.hasText(storedModel) ? storedModel : defaultModel());
        return new ResolvedChatRequest(resolvedConversationId, message, selectedModel, newConversation, true);
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
            prompt = prompt.options(OllamaChatOptions.builder().model(request.model()).enableThinking().build());
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
