package io.mindspice.magenta2.ai.chat.service;

import java.util.UUID;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.model.ContextUsage;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class ChatService {
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("(?is)<think>(.*?)</think>");

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatSessionMetadataRepository chatSessionMetadataRepository;
    private final ChatMarkdownRenderer chatMarkdownRenderer;
    private final AiConfig aiConfig;
    private final ContextManagementAdvisor contextManagementAdvisor;
    private final ContextUsageTracker contextUsageTracker;

    public ChatService(
        ChatClient chatClient,
        ChatMemory chatMemory,
        ChatMemoryRepository chatMemoryRepository,
        ChatSessionMetadataRepository chatSessionMetadataRepository,
        ChatMarkdownRenderer chatMarkdownRenderer,
        AiConfig aiConfig
    ) {
        this(
            chatClient,
            chatMemory,
            chatMemoryRepository,
            chatSessionMetadataRepository,
            chatMarkdownRenderer,
            aiConfig,
            null,
            null
        );
    }

    @Autowired
    public ChatService(
        ChatClient chatClient,
        ChatMemory chatMemory,
        ChatMemoryRepository chatMemoryRepository,
        ChatSessionMetadataRepository chatSessionMetadataRepository,
        ChatMarkdownRenderer chatMarkdownRenderer,
        AiConfig aiConfig,
        ContextManagementAdvisor contextManagementAdvisor,
        ContextUsageTracker contextUsageTracker
    ) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatSessionMetadataRepository = chatSessionMetadataRepository;
        this.chatMarkdownRenderer = chatMarkdownRenderer;
        this.aiConfig = aiConfig;
        this.contextManagementAdvisor = contextManagementAdvisor;
        this.contextUsageTracker = contextUsageTracker;
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
        chatSessionMetadataRepository.saveModel(request.conversationId(), request.model());
        StringBuilder responseText = new StringBuilder();
        return prompt(request)
            .stream()
            .content()
            .filter(chunk -> chunk != null)
            .doOnNext(responseText::append)
            .doOnComplete(() -> saveStreamedAssistantMessage(request.conversationId(), request.model(), responseText.toString()));
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

    private ResolvedChatRequest resolve(String conversationId, String message, String model) {
        String resolvedConversationId = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        String storedModel = storedConversationModel(resolvedConversationId);
        String selectedModel = StringUtils.hasText(model)
            ? model
            : (StringUtils.hasText(storedModel) ? storedModel : defaultModel());
        return new ResolvedChatRequest(resolvedConversationId, message, selectedModel);
    }

    private ChatClient.ChatClientRequestSpec prompt(ResolvedChatRequest request) {
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
