package io.mindspice.magenta2.ai.chat.service;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.model.ContextUsage;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.ai.chat.tool.ToolTranscriptService;
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
import org.springframework.ai.chat.model.MessageAggregator;
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
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("(?is)<think>(.*?)</think>");
    private static final int MAX_TOOL_CALL_ITERATIONS = 8;
    private static final String OLLAMA_TOOLS_UNSUPPORTED_MESSAGE = "does not support tools";

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
        ToolTranscriptService toolTranscriptService
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
    }

    public ChatResponse chat(ChatRequest request) {
        if (!(request instanceof ChatRequest.MsgRequest msgRequest)) {
            throw new IllegalArgumentException("message request is required");
        }
        ResolvedChatRequest resolvedRequest = resolve(msgRequest);
        return chat(resolvedRequest.conversationId(), resolvedRequest.message(), resolvedRequest.model());
    }

    public ChatResponse.MsgResponse chat(String conversationId, String message, String model) {
        ResolvedChatRequest resolvedRequest = resolve(conversationId, message, model);
        List<ToolCallback> approvedTools = approvedTools();
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
        return new ChatResponse.MsgResponse(
            resolvedRequest.conversationId(),
            resolvedRequest.model(),
            response,
            contextUsage(resolvedRequest.conversationId(), resolvedRequest.model())
        );
    }

    public ResolvedChatRequest resolve(ChatRequest request) {
        if (request instanceof ChatRequest.MsgRequest msgRequest) {
            return resolve(msgRequest.conversationId(), msgRequest.message(), msgRequest.model());
        }
        throw new IllegalArgumentException("message request is required");
    }

    public Flux<String> stream(ResolvedChatRequest request) {
        List<ToolCallback> approvedTools = approvedTools();
        if (!approvedTools.isEmpty() && supportsTools(request.model())) {
            // Try the tool-capable stream first. Models that reject tools are remembered and use plain streaming later.
            return toolStream(request, approvedTools)
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

    private Flux<String> plainStream(ResolvedChatRequest request) {
        chatSessionMetadataRepository.saveModel(request.conversationId(), request.model());
        StringBuilder responseText = new StringBuilder();
        return prompt(request)
            .stream()
            .content()
            .filter(chunk -> chunk != null)
            .doOnNext(responseText::append)
            .doOnComplete(() -> saveStreamedAssistantMessage(request.conversationId(), request.model(), responseText.toString()));
    }

    private Flux<String> toolStream(ResolvedChatRequest request, List<ToolCallback> approvedTools) {
        if (chatModelRouter == null || toolCallingManager == null || contextManagementAdvisor == null || toolTranscriptService == null) {
            throw new IllegalStateException("Tool streaming requires model routing, ToolCallingManager, context management, and tool transcripts");
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
        return toolStreamPrompt(request, prompt, options, new ArrayList<>(), 0);
    }

    /*
     * Stream text as soon as the model emits it, but keep an aggregated copy of the same model turn.
     * Tool calls are only reliable once the turn is complete, so actual tool execution happens after
     * the stream finishes. If tools were requested, continue with a new streamed model turn that includes
     * the tool responses in the conversation history.
     */
    private Flux<String> toolStreamPrompt(
        ResolvedChatRequest request,
        Prompt prompt,
        OllamaChatOptions options,
        List<Message> messagesToPersist,
        int iterations
    ) {
        if (iterations >= MAX_TOOL_CALL_ITERATIONS) {
            return Flux.error(new IllegalStateException("Tool execution exceeded " + MAX_TOOL_CALL_ITERATIONS + " iterations"));
        }

        AtomicReference<org.springframework.ai.chat.model.ChatResponse> aggregate = new AtomicReference<>();
        Flux<org.springframework.ai.chat.model.ChatResponse> responseStream = new MessageAggregator()
            .aggregate(chatModelRouter.chatModel(request.model()).stream(prompt), aggregate::set);

        return responseStream
            .map(this::textChunk)
            .filter(chunk -> chunk != null)
            .concatWith(Flux.defer(() -> {
                org.springframework.ai.chat.model.ChatResponse finalResponse = aggregate.get();
                if (finalResponse != null && finalResponse.hasToolCalls()) {
                    // Blocking is intentional here: tool calls mutate/read local state and must finish before the next turn.
                    ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, finalResponse);
                    messagesToPersist.addAll(toolTranscriptMessages(finalResponse, toolExecutionResult));
                    Prompt nextPrompt = new Prompt(toolExecutionResult.conversationHistory(), options);
                    return toolStreamPrompt(request, nextPrompt, options, messagesToPersist, iterations + 1);
                }

                AssistantMessage finalAssistantMessage = finalResponse == null || finalResponse.getResult() == null
                    ? new AssistantMessage("")
                    : finalResponse.getResult().getOutput();
                messagesToPersist.add(finalAssistantMessage);
                contextManagementAdvisor.saveAssistantMessages(request.conversationId(), messagesToPersist);
                if (contextUsageTracker != null) {
                    contextUsageTracker.record(
                        request.conversationId(),
                        contextManagementAdvisor.estimateStoredUsage(request.conversationId(), request.model())
                    );
                }
                return Flux.empty();
            }));
    }

    private String textChunk(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    public ChatMessage renderAssistantMessage(String text) {
        MessageParts messageParts = splitThinking(text == null ? "" : text);
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
            if (contextUsageTracker != null) {
                contextUsageTracker.clear(conversationId);
            }
        }
    }

    public List<String> listConversationIds() {
        return chatMemoryRepository.findConversationIds();
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
        org.springframework.ai.chat.model.ChatResponse response = chatModelRouter.chatModel(request.model()).call(prompt);
        List<Message> messagesToPersist = new ArrayList<>();
        int iterations = 0;

        while (response != null && response.hasToolCalls()) {
            if (++iterations > MAX_TOOL_CALL_ITERATIONS) {
                throw new IllegalStateException("Tool execution exceeded " + MAX_TOOL_CALL_ITERATIONS + " iterations");
            }
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
            messagesToPersist.addAll(toolTranscriptMessages(response, toolExecutionResult));
            prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
            response = chatModelRouter.chatModel(request.model()).call(prompt);
        }

        AssistantMessage finalAssistantMessage = response == null || response.getResult() == null
            ? new AssistantMessage("")
            : response.getResult().getOutput();
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
        return new ChatResponse.MsgResponse(
            request.conversationId(),
            request.model(),
            finalAssistantMessage == null ? "" : finalAssistantMessage.getText(),
            contextUsage(request.conversationId(), request.model())
        );
    }

    private List<Message> currentInstructions(ResolvedChatRequest request) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = defaultSystemPrompt();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(request.message()));
        return messages;
    }

    private OllamaChatOptions toolOptions(String model, List<ToolCallback> approvedTools) {
        OllamaChatOptions options = StringUtils.hasText(model)
            ? OllamaChatOptions.builder().model(model).build()
            : OllamaChatOptions.builder().build();
        options.setInternalToolExecutionEnabled(false);
        options.setToolCallbacks(approvedTools);
        return options;
    }

    private List<Message> toolTranscriptMessages(
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
                return (Message) toolTranscriptService.fullResult(
                    toolResponse.id(),
                    toolResponse.name(),
                    arguments,
                    toolResponse.responseData()
                );
            })
            .toList();
    }

    private List<ToolCallback> approvedTools() {
        if (chatToolRegistry == null || aiConfig == null || !StringUtils.hasText(aiConfig.defaultAgent())) {
            return List.of();
        }
        AgentConfig defaultAgent = aiConfig.agents().get(aiConfig.defaultAgent());
        if (defaultAgent == null) {
            return List.of();
        }
        return chatToolRegistry.resolveApprovedTools(defaultAgent.approvedTools());
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
        String storedModel = storedConversationModel(resolvedConversationId);
        String selectedModel = StringUtils.hasText(model)
            ? model
            : (StringUtils.hasText(storedModel) ? storedModel : defaultModel());
        return new ResolvedChatRequest(resolvedConversationId, message, selectedModel);
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

        String systemPrompt = defaultSystemPrompt();
        if (StringUtils.hasText(systemPrompt)) {
            prompt = prompt.system(systemPrompt);
        }

        prompt = prompt.user(request.message());

        if (StringUtils.hasText(request.model())) {
            prompt = prompt.options(OllamaChatOptions.builder().model(request.model()).build());
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

    private List<ChatMessage> toHistory(List<Message> messages) {
        return messages.stream()
            .filter(message -> contextManagementAdvisor == null || !contextManagementAdvisor.isHiddenSummary(message))
            .map(message -> {
                if (toolTranscriptService != null && toolTranscriptService.isToolTranscript(message)) {
                    String text = toolTranscriptService.renderForHistory(message);
                    return new ChatMessage(
                        "tool",
                        text,
                        chatMarkdownRenderer.render(text),
                        null
                    );
                }
                String role = message.getMessageType().getValue();
                String sourceText = contextManagementAdvisor != null && contextManagementAdvisor.isCompactionNotice(message)
                    ? contextManagementAdvisor.visibleNoticeText(message)
                    : (message.getText() == null ? "" : message.getText());
                MessageParts messageParts = isAssistantRole(role) ? splitThinking(sourceText) : new MessageParts(sourceText, "");
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

    private void saveStreamedAssistantMessage(String conversationId, String model, String responseText) {
        if (!StringUtils.hasText(responseText) || chatMemoryRepository == null) {
            return;
        }
        List<Message> messages = new java.util.ArrayList<>(chatMemoryRepository.findByConversationId(conversationId));
        messages.add(new AssistantMessage(responseText));
        chatMemoryRepository.saveAll(conversationId, messages);
        if (contextManagementAdvisor != null && contextUsageTracker != null) {
            contextUsageTracker.record(conversationId, contextManagementAdvisor.estimateStoredUsage(conversationId, model));
        }
    }

    private MessageParts splitThinking(String text) {
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

    public record ResolvedChatRequest(String conversationId, String message, String model) {
    }
}
