package io.mindspice.magenta2.ai.chat.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.plan.ChatPlanRepository;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.ai.chat.tool.ToolTranscriptService;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatServiceTest {

    private final ChatService chatService = new ChatService(
        null,
        null,
        null,
        new ChatMarkdownRenderer(),
        null
    );

    @Test
    void renderAssistantMessageUsesLegacyThinkTagsAsFallback() {
        ChatMessage message = chatService.renderAssistantMessage(
            "<think>private **notes**</think>\n\nVisible **answer**"
        );

        assertThat(message.role()).isEqualTo("assistant");
        assertThat(message.text()).isEqualTo("Visible **answer**");
        assertThat(message.renderedHtml()).contains("<strong>answer</strong>");
        assertThat(message.thinkingHtml()).contains("<strong>notes</strong>");
    }

    @Test
    void renderAssistantMessagePrefersStructuredThinkingMetadata() {
        ChatResponse response = new ChatResponse(List.of(new Generation(
            new AssistantMessage("<think>literal tag</think>\n\nVisible **answer**"),
            ChatGenerationMetadata.builder()
                .metadata(ChatService.THINKING_METADATA_KEY, "structured **notes**")
                .build()
        )));

        ChatMessage message = chatService.renderAssistantMessage(response);

        assertThat(message.text()).isEqualTo("<think>literal tag</think>\n\nVisible **answer**");
        assertThat(message.renderedHtml()).contains("<strong>answer</strong>");
        assertThat(message.thinkingHtml()).contains("<strong>notes</strong>");
    }

    @Test
    void assistantMessageCanCarryCombinedToolAndFinalThinking() {
        Generation finalGeneration = new Generation(
            new AssistantMessage("Visible answer"),
            ChatGenerationMetadata.builder()
                .metadata(ChatService.THINKING_METADATA_KEY, "final notes")
                .build()
        );

        AssistantMessage message = chatService.assistantMessageWithThinking(
            finalGeneration,
            "tool-call notes\n\nfinal notes"
        );

        assertThat(message.getText()).isEqualTo("Visible answer");
        assertThat(message.getMetadata())
            .containsEntry(ChatService.MESSAGE_THINKING_METADATA_KEY, "tool-call notes\n\nfinal notes");
    }

    @Test
    void defaultSystemPromptUsesConfiguredDefaultAgent() {
        AiConfig aiConfig = new AiConfig(
            "magenta",
            "magenta",
            10,
            null,
            Map.of(),
            Map.of(
                "other", new AgentConfig("other-model", "Wrong prompt.", java.util.List.of()),
                "magenta", new AgentConfig("local-qwen", "You are Magenta.", java.util.List.of())
            )
        );
        ChatService service = new ChatService(
            null,
            null,
            null,
            new ChatMarkdownRenderer(),
            aiConfig
        );

        assertThat(service.defaultSystemPrompt()).isEqualTo("You are Magenta.");
    }

    @Test
    void planModeSystemPromptReplacesDefaultSystemPrompt() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanService planService = new PlanService(
            new ChatPlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        planService.beginPlan("conversation-1");
        AiConfig aiConfig = new AiConfig(
            "magenta",
            "magenta",
            10,
            null,
            Map.of(),
            Map.of("magenta", new AgentConfig("local-qwen", "You are the default agent.", java.util.List.of()))
        );
        ChatService service = new ChatService(
            null,
            null,
            null,
            new ChatMarkdownRenderer(),
            aiConfig,
            null,
            null,
            null,
            null,
            null,
            null,
            planService
        );

        String prompt = service.effectiveSystemPrompt(new ChatService.ResolvedChatRequest(
            "conversation-1",
            "message",
            "local-qwen"
        ));

        assertThat(prompt)
            .contains("You are Magenta in PLAN mode")
            .doesNotContain("You are the default agent.");
    }

    @Test
    void planModeAllowsShellExecutionTool() {
        assertThat(ChatService.PLAN_MODE_TOOLS).contains("shell_exec");
        assertThat(ChatService.PLAN_MODE_TOOLS).contains("web_search", "web_fetch");
    }

    @Test
    void detectsOllamaToolUnsupportedErrors() {
        NonTransientAiException exception = new NonTransientAiException(
            "HTTP 400 - {\"error\":\"registry.ollama.ai/library/model:latest does not support tools\"}"
        );

        assertThat(ChatService.isToolUnsupported(exception)).isTrue();
        assertThat(ChatService.isToolUnsupported(new NonTransientAiException("HTTP 500 - other failure"))).isFalse();
    }

    @Test
    void toolLoopGuardStopsAfterFiveIdenticalToolCalls() {
        ChatService.ToolLoopGuard guard = new ChatService.ToolLoopGuard();
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("call-1", "function", "file_read", "{\"path\":\"a\"}");

        for (int i = 0; i < 4; i++) {
            guard.recordToolCalls(List.of(call));
        }

        assertThatThrownBy(() -> guard.recordToolCalls(List.of(call)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("5 identical calls to file_read");
    }

    @Test
    void toolLoopGuardStopsAfterFiveErrorsInEightResponses() {
        ChatService.ToolLoopGuard guard = new ChatService.ToolLoopGuard();

        for (String response : List.of(
            "{\"timedOut\":true}",
            "{\"ok\":true}",
            "startAnchor hash does not match current file content",
            "{\"ok\":true}",
            "file not found",
            "permission denied",
            "{\"ok\":true}"
        )) {
            guard.recordToolResponses(toolResult(response));
        }

        assertThatThrownBy(() -> guard.recordToolResponses(toolResult("tool failed")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("5 errors in the last 8 tool responses");
    }

    @Test
    void toolLoopGuardAllowsLongSuccessfulToolSequences() {
        ChatService.ToolLoopGuard guard = new ChatService.ToolLoopGuard();

        for (int i = 0; i < 20; i++) {
            guard.recordToolCalls(List.of(new AssistantMessage.ToolCall("call-" + i, "function", "file_read", "{\"path\":\"" + i + "\"}")));
            guard.recordToolResponses(toolResult("{\"ok\":true}"));
        }
    }

    @Test
    void repeatedToolErrorsArePersistedBeforeControlMessageAndFinalModelCall() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ToolTranscriptService transcriptService = new ToolTranscriptService(objectMapper);
        FakeChatModel chatModel = new FakeChatModel();
        FakeToolCallingManager toolCallingManager = new FakeToolCallingManager();
        ChatService service = new ChatService(
            null,
            memoryRepository,
            new ChatSessionMetadataRepository(jdbcTemplate),
            new ChatMarkdownRenderer(),
            toolAiConfig(),
            new FakeContextManagementAdvisor(memoryRepository),
            null,
            new FakeChatModelRouter(chatModel),
            toolCallingManager,
            new ChatToolRegistry(List.of(new FakeToolCallback()), List.of()),
            transcriptService,
            null
        );

        io.mindspice.magenta2.ai.chat.model.ChatResponse.MsgResponse response = service.chat(
            "conversation-1",
            "try the tool",
            "qwen3"
        );

        assertThat(response.response()).isEqualTo("I could not continue using tools after repeated errors.");
        assertThat(chatModel.prompts).hasSize(9);
        assertThat(chatModel.finalPromptText())
            .contains("Tool use was aborted by Magenta")
            .contains("8 errors in the last 8 tool responses")
            .contains("Recent tool errors:")
            .contains("tool failed 8");
        assertThat(((ToolCallingChatOptions) chatModel.prompts.getLast().getOptions()).getToolCallbacks()).isEmpty();

        List<Message> storedMessages = memoryRepository.findByConversationId("conversation-1");
        assertThat(storedMessages)
            .extracting(Message::getText)
            .anySatisfy(text -> assertThat(text).contains("tool failed 8"))
            .anySatisfy(text -> assertThat(text).contains("Tool use was aborted by Magenta"))
            .last()
            .satisfies(text -> assertThat(text).isEqualTo("I could not continue using tools after repeated errors."));
    }

    @Test
    void discardLastUserMessageRemovesOnlyMatchingDanglingUserTurn() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        memoryRepository.saveAll("conversation-1", java.util.List.of(
            new UserMessage("keep"),
            new AssistantMessage("answer"),
            new UserMessage("failed")
        ));
        ChatService service = new ChatService(
            null,
            memoryRepository,
            null,
            new ChatMarkdownRenderer(),
            null
        );

        service.discardLastUserMessage("conversation-1", "failed");

        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .containsExactly("keep", "answer");
    }

    @Test
    void historyIncludesStructuredToolActivity() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ToolTranscriptService transcriptService = new ToolTranscriptService(objectMapper);
        memoryRepository.saveAll("conversation-1", java.util.List.of(
            transcriptService.fullResult(
                "call-1",
                "shell_exec",
                "{\"command\":\"pwd\",\"workingDirectory\":\".\"}",
                "{\"command\":\"pwd\",\"commandLine\":\"pwd\",\"args\":[],\"workingDirectory\":\".\",\"exitCode\":0,\"stdout\":\"/tmp\\n\",\"stderr\":\"\",\"timedOut\":false,\"truncated\":false}"
            )
        ));
        ChatService service = new ChatService(
            null,
            memoryRepository,
            null,
            new ChatMarkdownRenderer(),
            null,
            null,
            null,
            null,
            null,
            null,
            transcriptService,
            null
        );

        ChatMessage message = service.history("conversation-1").getFirst();

        assertThat(message.role()).isEqualTo("tool");
        assertThat(message.toolActivity()).isNotNull();
        assertThat(message.toolActivity().summary()).contains("Ran `pwd`");
        assertThat(message.toolActivity().callDetail()).contains("\"command\" : \"pwd\"");
    }

    @Test
    void historyIncludesCompactionNoticeAsSystemMessage() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        memoryRepository.saveAll("conversation-1", java.util.List.of(
            new org.springframework.ai.chat.messages.SystemMessage(
                ContextManagementAdvisor.NOTICE_PREFIX + ContextManagementAdvisor.COMPACTION_NOTICE
            )
        ));
        ChatService service = new ChatService(
            null,
            memoryRepository,
            null,
            new ChatMarkdownRenderer(),
            null,
            new FakeContextManagementAdvisor(memoryRepository),
            null,
            null,
            null,
            null,
            null,
            null
        );

        ChatMessage message = service.history("conversation-1").getFirst();

        assertThat(message.role()).isEqualTo("system");
        assertThat(message.text()).isEqualTo(ContextManagementAdvisor.COMPACTION_NOTICE);
        assertThat(message.renderedHtml()).contains("Context compacted");
    }

    @Test
    void toolLoopStopsToolsWhenContextCheckpointCannotContinue() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ToolTranscriptService transcriptService = new ToolTranscriptService(objectMapper);
        FakeChatModel chatModel = new FakeChatModel();
        FakeToolCallingManager toolCallingManager = new FakeToolCallingManager();
        FakeContextManagementAdvisor contextAdvisor = new FakeContextManagementAdvisor(memoryRepository);
        contextAdvisor.allowToolUse = false;
        ChatService service = new ChatService(
            null,
            memoryRepository,
            new ChatSessionMetadataRepository(jdbcTemplate),
            new ChatMarkdownRenderer(),
            toolAiConfig(),
            contextAdvisor,
            null,
            new FakeChatModelRouter(chatModel),
            toolCallingManager,
            new ChatToolRegistry(List.of(new FakeToolCallback()), List.of()),
            transcriptService,
            null
        );

        io.mindspice.magenta2.ai.chat.model.ChatResponse.MsgResponse response = service.chat(
            "conversation-1",
            "try the tool",
            "qwen3"
        );

        assertThat(response.response()).isEqualTo("I could not continue using tools after repeated errors.");
        assertThat(chatModel.prompts).hasSize(2);
        assertThat(((ToolCallingChatOptions) chatModel.prompts.getLast().getOptions()).getToolCallbacks()).isEmpty();
        assertThat(chatModel.finalPromptText()).contains("Context is too large to safely continue tool use");
    }

    @Test
    void toolChatCompactsStoredContextBeforeReturningUsage() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ToolTranscriptService transcriptService = new ToolTranscriptService(objectMapper);
        ContextUsageTracker usageTracker = new ContextUsageTracker();
        MultiToolThenFinalChatModel chatModel = new MultiToolThenFinalChatModel();
        ContextManagementAdvisor contextAdvisor = new ContextManagementAdvisor(
            memoryRepository,
            compactingToolAiConfig(),
            new SummaryRouter(chatModel),
            new CharacterTokenEstimator(),
            usageTracker,
            transcriptService,
            null
        );
        ChatService service = new ChatService(
            null,
            memoryRepository,
            new ChatSessionMetadataRepository(jdbcTemplate),
            new ChatMarkdownRenderer(),
            compactingToolAiConfig(),
            contextAdvisor,
            usageTracker,
            new FakeChatModelRouter(chatModel),
            new SuccessfulToolCallingManager(),
            new ChatToolRegistry(List.of(new FakeToolCallback()), List.of()),
            transcriptService,
            null
        );

        io.mindspice.magenta2.ai.chat.model.ChatResponse.MsgResponse response = service.chat(
            "conversation-1",
            "use several tools",
            "qwen3"
        );

        assertThat(response.contextUsage().usedTokens()).isLessThanOrEqualTo(response.contextUsage().triggerTokens());
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .filteredOn(contextAdvisor::isHiddenSummary)
            .hasSize(1);
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .filteredOn(contextAdvisor::isCompactionNotice)
            .hasSize(1);
    }

    @Test
    void planModeRetriesThinkingOnlyFinalResponseBeforePersistingAssistantMessage() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanService planService = new PlanService(
            new ChatPlanRepository(jdbcTemplate, objectMapper),
            memoryRepository
        );
        planService.beginPlan("conversation-1");
        ToolTranscriptService transcriptService = new ToolTranscriptService(objectMapper);
        ThinkingOnlyThenFinalChatModel chatModel = new ThinkingOnlyThenFinalChatModel();
        ChatService service = new ChatService(
            null,
            memoryRepository,
            new ChatSessionMetadataRepository(jdbcTemplate),
            new ChatMarkdownRenderer(),
            planToolAiConfig(),
            new FakeContextManagementAdvisor(memoryRepository),
            null,
            new FakeChatModelRouter(chatModel),
            new SuccessfulToolCallingManager(),
            new ChatToolRegistry(List.of(new FakeToolCallback("plan_set_goal")), List.of()),
            transcriptService,
            planService
        );

        io.mindspice.magenta2.ai.chat.model.ChatResponse.MsgResponse response = service.chat(
            "conversation-1",
            "start planning",
            "qwen3"
        );

        assertThat(response.response()).isEqualTo("What should we clarify, change, or add before continuing this plan?");
        assertThat(response.planState().promptQuestion()).isEqualTo("What should we clarify, change, or add before continuing this plan?");
        assertThat(chatModel.prompts).hasSize(4);
        assertThat(chatModel.finalPromptText())
            .contains("Your previous response had thinking but no user-visible message and no tool calls")
            .contains("Continue the PLAN-mode turn now");
        List<Message> storedMessages = memoryRepository.findByConversationId("conversation-1");
        assertThat(storedMessages).extracting(Message::getText)
            .doesNotContain("")
            .last()
            .isEqualTo("What should we clarify, change, or add before continuing this plan?");
    }

    @Test
    void planAnswerDoesNotResumeModelUntilQueuedQuestionsAreComplete() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanService planService = new PlanService(
            new ChatPlanRepository(jdbcTemplate, objectMapper),
            memoryRepository
        );
        planService.beginPlan("conversation-1");
        planService.askQuestions("conversation-1", List.of("First?", "Second?"));
        FakeChatModel chatModel = new FakeChatModel();
        ChatService service = new ChatService(
            null,
            memoryRepository,
            new ChatSessionMetadataRepository(jdbcTemplate),
            new ChatMarkdownRenderer(),
            planToolAiConfig(),
            null,
            null,
            new FakeChatModelRouter(chatModel),
            null,
            null,
            null,
            planService
        );

        io.mindspice.magenta2.ai.chat.model.ChatResponse.MsgResponse first = service.submitPlanAnswer(
            "conversation-1",
            "A",
            null,
            1
        );

        assertThat(first.planState().promptQuestion()).isEqualTo("Second?");
        assertThat(chatModel.prompts).isEmpty();
    }

    @Test
    void executePlanRetriesFinalAnswerUntilPlanCompleteRuns() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanService planService = savedPlanService(jdbcTemplate, objectMapper, memoryRepository);
        planService.markExecuting("conversation-1");
        ToolTranscriptService transcriptService = new ToolTranscriptService(objectMapper);
        FinalThenPlanCompleteChatModel chatModel = new FinalThenPlanCompleteChatModel();
        ChatService service = new ChatService(
            null,
            memoryRepository,
            new ChatSessionMetadataRepository(jdbcTemplate),
            new ChatMarkdownRenderer(),
            executionToolAiConfig(),
            new FakeContextManagementAdvisor(memoryRepository),
            null,
            new FakeChatModelRouter(chatModel),
            new CompletingPlanToolCallingManager(planService),
            new ChatToolRegistry(List.of(new FakeToolCallback("plan_complete")), List.of()),
            transcriptService,
            planService
        );

        io.mindspice.magenta2.ai.chat.model.ChatResponse.MsgResponse response = service.chat(
            "conversation-1",
            "Execute the saved plan now.",
            "qwen3"
        );

        assertThat(response.response()).isEqualTo("Validated completion is done.");
        assertThat(planService.activePlan("conversation-1").orElseThrow().status().name()).isEqualTo("COMPLETED");
        assertThat(planService.finalMessage("conversation-1")).isEqualTo("Validated completion is done.");
        assertThat(chatModel.prompts).hasSize(2);
        assertThat(chatModel.prompts.get(1).getInstructions().stream()
            .map(message -> message.getText() == null ? "" : message.getText())
            .collect(java.util.stream.Collectors.joining("\n")))
            .contains("attempted to finish without validator-gated completion")
            .contains("must call plan_complete");
    }

    @Test
    void executeSavedPlanFallsBackToNeedsReviewAfterSkippedPlanCompleteRetries() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanService planService = savedPlanService(jdbcTemplate, objectMapper, memoryRepository);
        AlwaysFinalChatModel chatModel = new AlwaysFinalChatModel("I finished without validation.");
        ChatService service = new ChatService(
            null,
            memoryRepository,
            new ChatSessionMetadataRepository(jdbcTemplate),
            new ChatMarkdownRenderer(),
            executionToolAiConfig(),
            new FakeContextManagementAdvisor(memoryRepository),
            null,
            new FakeChatModelRouter(chatModel),
            new SuccessfulToolCallingManager(),
            new ChatToolRegistry(List.of(new FakeToolCallback("plan_complete")), List.of()),
            new ToolTranscriptService(objectMapper),
            planService
        );

        io.mindspice.magenta2.ai.chat.model.ChatResponse.MsgResponse response = service.executeSavedPlan("conversation-1");

        assertThat(response.response()).isEqualTo("I finished without validation.");
        assertThat(response.planState().status()).isEqualTo("NEEDS_REVIEW");
        assertThat(response.planState().executionEvidence())
            .contains("Deviation: execution returned without a structured completion ledger.");
        assertThat(chatModel.prompts).hasSize(3);
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }

    private ToolExecutionResult toolResult(String responseData) {
        Message responseMessage = ToolResponseMessage.builder()
            .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "tool", responseData)))
            .build();
        return ToolExecutionResult.builder()
            .conversationHistory(List.of(responseMessage))
            .build();
    }

    private AiConfig toolAiConfig() {
        return new AiConfig(
            "magenta",
            "magenta",
            10,
            null,
            Map.of("local-qwen", new ModelConfig("qwen3", "http://localhost:11434", EndpointType.OLLAMA, 8192, false)),
            Map.of("magenta", new AgentConfig("local-qwen", "You are Magenta.", List.of("test_tool")))
        );
    }

    private AiConfig compactingToolAiConfig() {
        return new AiConfig(
            "magenta",
            "summary",
            10,
            null,
            Map.of(
                "local-qwen", new ModelConfig("qwen3", "http://localhost:11434", EndpointType.OLLAMA, 1400, false),
                "summary", new ModelConfig("summary-model", "http://localhost:11434", EndpointType.OLLAMA, 512, false)
            ),
            Map.of("magenta", new AgentConfig("local-qwen", "You are Magenta.", List.of("test_tool")))
        );
    }

    private AiConfig planToolAiConfig() {
        return new AiConfig(
            "magenta",
            "local-qwen",
            "local-qwen",
            10,
            null,
            Map.of("local-qwen", new ModelConfig("qwen3", "http://localhost:11434", EndpointType.OLLAMA, 8192, false)),
            Map.of("magenta", new AgentConfig("local-qwen", "You are Magenta.", List.of("plan_set_goal")))
        );
    }

    private AiConfig executionToolAiConfig() {
        return new AiConfig(
            "magenta",
            "local-qwen",
            "local-qwen",
            10,
            null,
            Map.of("local-qwen", new ModelConfig("qwen3", "http://localhost:11434", EndpointType.OLLAMA, 8192, false)),
            Map.of("magenta", new AgentConfig("local-qwen", "You are Magenta.", List.of("plan_complete")))
        );
    }

    private PlanService savedPlanService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        ChatMemoryRepository memoryRepository
    ) {
        PlanService planService = new PlanService(
            new ChatPlanRepository(jdbcTemplate, objectMapper),
            memoryRepository
        );
        planService.beginPlan("conversation-1", "qwen3", "qwen3");
        planService.saveDraftPlan(
            "conversation-1",
            "Goal",
            "Plan",
            "Summary",
            null,
            List.of("Do the work."),
            List.of("Assumption"),
            List.of("Validate result")
        );
        return planService;
    }

    private static final class CharacterTokenEstimator implements TokenCountEstimator {
        @Override
        public int estimate(String text) {
            return text == null ? 0 : text.length();
        }

        @Override
        public int estimate(MediaContent content) {
            return content == null ? 0 : estimate(content.getText());
        }

        @Override
        public int estimate(Iterable<MediaContent> contents) {
            int total = 0;
            if (contents != null) {
                for (MediaContent content : contents) {
                    total += estimate(content);
                }
            }
            return total;
        }
    }

    private static final class SummaryRouter extends ChatModelRouter {
        private final org.springframework.ai.chat.client.ChatClient chatClient;

        SummaryRouter(ChatModel chatModel) {
            super(null, null, null);
            this.chatClient = org.springframework.ai.chat.client.ChatClient.builder(chatModel).build();
        }

        @Override
        public org.springframework.ai.chat.client.ChatClient chatClient(String model) {
            return chatClient;
        }

        @Override
        public OllamaChatOptions ollamaOptions(String model) {
            return OllamaChatOptions.builder().model(model).build();
        }

        @Override
        public OllamaChatOptions.Builder ollamaOptionsBuilder(String model) {
            return OllamaChatOptions.builder().model(model);
        }
    }

    private static final class FakeContextManagementAdvisor extends ContextManagementAdvisor {
        private final ChatMemoryRepository memoryRepository;
        private boolean allowToolUse = true;
        private int toolLoopPromptCalls = 0;

        FakeContextManagementAdvisor(ChatMemoryRepository memoryRepository) {
            super(null, null, null, null, null, null, null);
            this.memoryRepository = memoryRepository;
        }

        @Override
        public PreparedPrompt preparePrompt(String conversationId, List<Message> currentInstructions, String model) {
            List<Message> storedMessages = new java.util.ArrayList<>(memoryRepository.findByConversationId(conversationId));
            storedMessages.add(new Prompt(currentInstructions).getLastUserOrToolResponseMessage());
            memoryRepository.saveAll(conversationId, storedMessages);
            return new PreparedPrompt(currentInstructions, new io.mindspice.magenta2.ai.chat.model.ContextUsage(0, 8192, 7372, 0.0));
        }

        @Override
        public void saveAssistantMessages(String conversationId, List<Message> messages) {
            List<Message> storedMessages = new java.util.ArrayList<>(memoryRepository.findByConversationId(conversationId));
            storedMessages.addAll(messages);
            memoryRepository.saveAll(conversationId, storedMessages);
        }

        @Override
        public io.mindspice.magenta2.ai.chat.model.ContextUsage estimateStoredUsage(String conversationId, String remoteModelName) {
            return new io.mindspice.magenta2.ai.chat.model.ContextUsage(0, 8192, 7372, 0.0);
        }

        @Override
        public StoredContextMaintenance maintainStoredContext(String conversationId, String remoteModelName) {
            return new StoredContextMaintenance(estimateStoredUsage(conversationId, remoteModelName), false);
        }

        @Override
        public ToolLoopPrompt prepareToolLoopPrompt(
            String conversationId,
            List<Message> activeMessages,
            List<Message> currentSystemInstructions,
            String model
        ) {
            toolLoopPromptCalls++;
            List<Message> messages = new java.util.ArrayList<>();
            messages.addAll(currentSystemInstructions == null ? List.of() : currentSystemInstructions);
            messages.addAll(memoryRepository.findByConversationId(conversationId));
            messages.addAll(activeMessages == null ? List.of() : activeMessages);
            return new ToolLoopPrompt(
                messages,
                activeMessages == null ? List.of() : List.copyOf(activeMessages),
                new io.mindspice.magenta2.ai.chat.model.ContextUsage(0, 8192, 7372, 0.0),
                allowToolUse || toolLoopPromptCalls > 1,
                false
            );
        }
    }

    private static final class FakeChatModelRouter extends ChatModelRouter {
        private final ChatModel chatModel;

        FakeChatModelRouter(ChatModel chatModel) {
            super(null, null, null);
            this.chatModel = chatModel;
        }

        @Override
        public ChatModel chatModel(String model) {
            return chatModel;
        }

        @Override
        public OllamaChatOptions ollamaOptions(String model) {
            return OllamaChatOptions.builder().model(model).build();
        }

        @Override
        public OllamaChatOptions.Builder ollamaOptionsBuilder(String model) {
            return OllamaChatOptions.builder().model(model);
        }
    }

    private static final class FakeChatModel implements ChatModel {
        private final List<Prompt> prompts = new java.util.ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            if (prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks().isEmpty()) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "I could not continue using tools after repeated errors."
                ))));
            }
            if (prompts.size() <= 8) {
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-" + prompts.size(),
                        "function",
                        "test_tool",
                        "{\"attempt\":" + prompts.size() + "}"
                    )))
                    .build())));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                "I could not continue using tools after repeated errors."
            ))));
        }

        private String finalPromptText() {
            return prompts.getLast().getInstructions().stream()
                .map(message -> message.getText() == null ? "" : message.getText())
                .collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    private static final class MultiToolThenFinalChatModel implements ChatModel {
        private int calls;

        @Override
        public ChatResponse call(Prompt prompt) {
            calls++;
            String promptText = prompt.getInstructions().stream()
                .map(message -> message.getText() == null ? "" : message.getText())
                .collect(java.util.stream.Collectors.joining("\n"));
            if (promptText.contains("Summarize the previous Magenta conversation")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("short tool summary"))));
            }
            if (calls <= 7) {
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-" + calls,
                        "function",
                        "test_tool",
                        "{\"attempt\":" + calls + "}"
                    )))
                    .build())));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("final answer " + "x".repeat(500)))));
        }
    }

    private static final class ThinkingOnlyThenFinalChatModel implements ChatModel {
        private final List<Prompt> prompts = new java.util.ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            if (prompts.size() == 1) {
                return new ChatResponse(List.of(new Generation(
                    new AssistantMessage(""),
                    ChatGenerationMetadata.builder()
                        .metadata(ChatService.THINKING_METADATA_KEY, "draft analysis")
                        .build()
                )));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                "What constraint should guide this plan?"
            ))));
        }

        private String finalPromptText() {
            return prompts.getLast().getInstructions().stream()
                .map(message -> message.getText() == null ? "" : message.getText())
                .collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    private static final class FinalThenPlanCompleteChatModel implements ChatModel {
        private final List<Prompt> prompts = new java.util.ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            if (prompts.size() == 1) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("I finished without validation."))));
            }
            if (prompts.size() == 2) {
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "plan-complete-call",
                        "function",
                        "plan_complete",
                        "{\"summary\":\"Done\",\"evidence\":[\"Criterion: Validate result | Evidence: checked\"],\"deviations\":[],\"unmetCriteria\":[],\"artifactPaths\":[]}"
                    )))
                    .build())));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Validated completion is done."))));
        }
    }

    private static final class AlwaysFinalChatModel implements ChatModel {
        private final List<Prompt> prompts = new java.util.ArrayList<>();
        private final String response;

        private AlwaysFinalChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }
    }

    private static final class FakeToolCallingManager implements ToolCallingManager {
        private int count = 0;

        @Override
        public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
            return List.of();
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            count++;
            Message responseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                    "call-" + count,
                    "test_tool",
                    "tool failed " + count
                )))
                .build();
            List<Message> history = new java.util.ArrayList<>(prompt.getInstructions());
            history.add(chatResponse.getResult().getOutput());
            history.add(responseMessage);
            return ToolExecutionResult.builder()
                .conversationHistory(history)
                .build();
        }
    }

    private static final class SuccessfulToolCallingManager implements ToolCallingManager {
        private int count = 0;

        @Override
        public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
            return List.of();
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            count++;
            Message responseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                    "call-" + count,
                    "test_tool",
                    "{\"ok\":true,\"result\":\"" + "x".repeat(80) + "\"}"
                )))
                .build();
            List<Message> history = new java.util.ArrayList<>(prompt.getInstructions());
            history.add(chatResponse.getResult().getOutput());
            history.add(responseMessage);
            return ToolExecutionResult.builder()
                .conversationHistory(history)
                .build();
        }
    }

    private static final class CompletingPlanToolCallingManager implements ToolCallingManager {
        private final PlanService planService;

        private CompletingPlanToolCallingManager(PlanService planService) {
            this.planService = planService;
        }

        @Override
        public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
            return List.of();
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            planService.markCompleted("conversation-1", "Validated completion is done.");
            Message responseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                    "plan-complete-call",
                    "plan_complete",
                    "Plan validation passed. The plan is marked COMPLETED."
                )))
                .build();
            List<Message> history = new java.util.ArrayList<>(prompt.getInstructions());
            history.add(chatResponse.getResult().getOutput());
            history.add(responseMessage);
            return ToolExecutionResult.builder()
                .conversationHistory(history)
                .build();
        }
    }

    private static final class FakeToolCallback implements ToolCallback {
        private final String name;

        private FakeToolCallback() {
            this("test_tool");
        }

        private FakeToolCallback(String name) {
            this.name = name;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder()
                .name(name)
                .description("Test tool")
                .inputSchema("{}")
                .build();
        }

        @Override
        public String call(String toolInput) {
            return "{}";
        }
    }
}
