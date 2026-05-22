package io.mindspice.magenta2.ai.chat.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.ChatSession;
import io.mindspice.magenta2.ai.chat.plan.PlanRepository;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanStatus;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.chat.repository.RepositoryBackedChatMemory;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.ai.chat.tool.ToolTranscriptService;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvingSavedPlanExecutionPreservesExistingTranscriptRows() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        PlanService planService = new PlanService(new PlanRepository(jdbcTemplate, objectMapper), memoryRepository);
        ChatService chatService = new ChatService(
            new RepositoryBackedChatMemory(memoryRepository),
            memoryRepository,
            metadataRepository,
            null,
            aiConfig(),
            null,
            null,
            null,
            null,
            null,
            null,
            planService
        );

        memoryRepository.saveAll("conversation-1", List.of(
            new UserMessage("User planning request"),
            AssistantMessage.builder().content("Assistant planning response").build()
        ));
        planService.beginPlan("conversation-1");
        planService.saveDraftPlan(
            "conversation-1",
            "Preserve chat history",
            "Transcript Plan",
            "Keep existing chat transcript rows.",
            null,
            List.of("Resolve saved plan execution"),
            List.of("Existing transcript rows are present"),
            List.of("History readback still contains the original user and assistant messages")
        );
        planService.approvePlan("conversation-1");

        ResolvedChatRequest request = chatService.resolveSavedPlanExecution("conversation-1");

        assertThat(request.conversationId()).isEqualTo("conversation-1");
        assertThat(planService.activePlan("conversation-1").orElseThrow().status())
            .isEqualTo(PlanStatus.EXECUTING);
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .containsExactly("User planning request", "Assistant planning response");
    }

    @Test
    void resolvingCleanSavedPlanExecutionFlagsPromptContextWithoutClearingTranscriptRows() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        PlanService planService = new PlanService(new PlanRepository(jdbcTemplate, objectMapper), memoryRepository);
        ChatService chatService = new ChatService(
            new RepositoryBackedChatMemory(memoryRepository),
            memoryRepository,
            metadataRepository,
            null,
            aiConfig(),
            null,
            null,
            null,
            null,
            null,
            null,
            planService
        );

        memoryRepository.saveAll("conversation-1", List.of(
            new UserMessage("User planning request"),
            AssistantMessage.builder().content("Assistant planning response").build()
        ));
        planService.beginPlan("conversation-1");
        planService.saveDraftPlan(
            "conversation-1",
            "Preserve chat history",
            "Transcript Plan",
            "Keep existing chat transcript rows.",
            null,
            List.of("Resolve saved plan execution"),
            List.of("Existing transcript rows are present"),
            List.of("History readback still contains the original user and assistant messages")
        );
        planService.approvePlan("conversation-1");

        ResolvedChatRequest request = chatService.resolveSavedPlanExecution("conversation-1", true);

        assertThat(request.omitStoredMessages()).isTrue();
        assertThat(request.message()).contains("Approved anonymous plan");
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .containsExactly("User planning request", "Assistant planning response");
    }

    @Test
    void streamExecutionFinalizationMarksStillExecutingPlanNeedsReviewInsteadOfCompleted() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        PlanService planService = new PlanService(new PlanRepository(jdbcTemplate, objectMapper), memoryRepository);
        ChatService chatService = new ChatService(
            new RepositoryBackedChatMemory(memoryRepository),
            memoryRepository,
            metadataRepository,
            null,
            aiConfig(),
            null,
            null,
            null,
            null,
            null,
            null,
            planService
        );

        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("execute this")));
        planService.beginPlan("conversation-1");
        planService.saveDraftPlan(
            "conversation-1",
            "Gate completion",
            "Completion Gate",
            "Do not trust ordinary assistant text.",
            null,
            List.of("Validated result"),
            List.of("Run work"),
            List.of("plan_complete validates completion")
        );
        planService.markExecuting("conversation-1");
        memoryRepository.saveAll("conversation-1", List.of(
            new UserMessage("execute this"),
            AssistantMessage.builder().content("Ordinary model completion").build()
        ));

        chatService.handlePlanExecutionStreamFinished("conversation-1", "Ordinary model completion");

        assertThat(planService.activePlan("conversation-1").orElseThrow().status())
            .isEqualTo(PlanStatus.NEEDS_REVIEW);
        assertThat(planService.activePlan("conversation-1").orElseThrow().finalMessage()).isNull();
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .containsExactly(
                "execute this",
                "Plan execution needs review. Magenta could not verify completion through plan_complete after retrying. Review the saved execution evidence and validation feedback before trusting the result."
            );
    }

    @Test
    void streamExecutionFinalizationPersistsFinalArtifactOnlyAfterValidatedCompletion() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        Path dataRoot = Files.createDirectories(tempDir.resolve("validated-stream-data"));
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(aiConfig(dataRoot));
        PlanService planService = new PlanService(
            new PlanRepository(jdbcTemplate, objectMapper),
            memoryRepository,
            null,
            new ChatMarkdownRenderer(),
            directoryService,
            null
        );
        ChatService chatService = new ChatService(
            new RepositoryBackedChatMemory(memoryRepository),
            memoryRepository,
            metadataRepository,
            null,
            aiConfig(dataRoot),
            null,
            null,
            null,
            null,
            null,
            null,
            planService
        );

        planService.beginPlan("conversation-validated");
        planService.saveDraftPlan(
            "conversation-validated",
            "Gate completion",
            "Validated Completion",
            "Persist only validator-approved final output.",
            null,
            List.of("Validated result"),
            List.of("Run work"),
            List.of("plan_complete validates completion")
        );
        planService.markExecuting("conversation-validated");
        planService.markCompleted("conversation-validated", "Validated final message");

        chatService.handlePlanExecutionStreamFinished("conversation-validated", "Validated final message");

        Path finalMessage = dataRoot.resolve("chats/conversation-validated/files/final-message.md");
        assertThat(finalMessage).exists();
        assertThat(Files.readString(finalMessage)).isEqualTo("Validated final message\n");
    }

    @Test
    void listSessionsIncludesChatFileOutputCount() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        Path dataRoot = Files.createDirectories(tempDir.resolve("new-magenta-root/root"));
        ChatFileService chatFileService = new ChatFileService(
            new io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService(aiConfig(dataRoot))
        );
        ChatService chatService = new ChatService(
            new RepositoryBackedChatMemory(memoryRepository),
            memoryRepository,
            metadataRepository,
            null,
            aiConfig(dataRoot),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            objectMapper,
            null,
            null,
            null,
            chatFileService,
            null
        );
        String conversationId = "00000000-0000-0000-0000-000000000001";
        memoryRepository.saveAll(conversationId, List.of(new UserMessage("hello")));
        Path files = Files.createDirectories(dataRoot.resolve("chats/" + conversationId + "/files/nested"));
        Files.writeString(files.resolve("seeded.md"), "seeded");

        assertThat(chatService.listSessions())
            .singleElement()
            .extracting(ChatSession::outputCount)
            .isEqualTo(1);
    }

    @Test
    void malformedToolCallArgumentsAreReportedWithoutExecutingTools() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        ToolTranscriptService transcriptService = new ToolTranscriptService(objectMapper);
        ContextUsageTracker usageTracker = new ContextUsageTracker();
        MalformedThenFinalChatModel chatModel = new MalformedThenFinalChatModel();
        ToolCallingManager toolCallingManager = mock(ToolCallingManager.class);
        ChatToolRegistry toolRegistry = new ChatToolRegistry(List.of(new SampleToolCallback()), List.of());
        ChatModelRouter router = new TestChatModelRouter(chatModel);
        AiConfig config = aiConfig(Path.of("."), List.of("sample_tool"));
        ContextManagementAdvisor contextAdvisor = new ContextManagementAdvisor(
            memoryRepository,
            config,
            router,
            new CharacterTokenEstimator(),
            usageTracker,
            transcriptService,
            null
        );
        ChatService chatService = new ChatService(
            new RepositoryBackedChatMemory(memoryRepository),
            memoryRepository,
            metadataRepository,
            new ChatMarkdownRenderer(),
            config,
            contextAdvisor,
            usageTracker,
            router,
            toolCallingManager,
            toolRegistry,
            transcriptService,
            null
        );

        var response = chatService.chat("conversation-1", "use the tool", "main");

        assertThat(response.response()).isEqualTo("Recovered after diagnostic.");
        assertThat(chatModel.prompts()).hasSize(2);
        assertThat(chatModel.prompts().get(1)).contains("malformed JSON arguments");
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(Message::getText)
            .anySatisfy(text -> assertThat(text).contains("Malformed tool-call arguments"))
            .anySatisfy(text -> assertThat(text).contains("no tools in that batch were executed"))
            .contains("Recovered after diagnostic.");
        assertThat(response.toolActivities())
            .singleElement()
            .satisfies(activity -> {
                assertThat(activity.toolName()).isEqualTo("sample_tool");
                assertThat(activity.status()).isEqualTo("error");
                assertThat(activity.summary()).contains("Malformed tool-call arguments");
            });
        verifyNoInteractions(toolCallingManager);
    }

    @Test
    void planningAnswerModelFailureReturnsControlledResponseAfterSavingAnswer() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        PlanService planService = new PlanService(new PlanRepository(jdbcTemplate, objectMapper), memoryRepository);
        ContextUsageTracker usageTracker = new ContextUsageTracker();
        ChatModelRouter router = new TestChatModelRouter(new AuthenticationFailureChatModel());
        AiConfig config = aiConfig();
        ContextManagementAdvisor contextAdvisor = new ContextManagementAdvisor(
            memoryRepository,
            config,
            router,
            new CharacterTokenEstimator(),
            usageTracker,
            new ToolTranscriptService(objectMapper),
            null
        );
        ChatService chatService = new ChatService(
            new RepositoryBackedChatMemory(memoryRepository),
            memoryRepository,
            metadataRepository,
            new ChatMarkdownRenderer(),
            config,
            contextAdvisor,
            usageTracker,
            router,
            null,
            null,
            null,
            planService
        );

        planService.beginPlan("conversation-1");
        planService.askQuestions("conversation-1", List.of("What should Magenta build?"));

        var response = chatService.submitPlanAnswer("conversation-1", "A reliable planning flow.", null, 1);

        assertThat(response.response()).contains("I saved your planning answer");
        assertThat(response.planState().promptQuestion()).isNull();
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(Message::getText)
            .contains(
                "Planning answer\n\nQuestion: What should Magenta build?\n\nAnswer: A reliable planning flow.",
                "I saved your planning answer, but Magenta could not continue the planning turn because the configured planning model request failed. Check the selected planning model and API credentials, then send another planning message to continue."
            );
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        return new JdbcTemplate(dataSource);
    }

    private AiConfig aiConfig() {
        return aiConfig(Path.of("."));
    }

    private AiConfig aiConfig(Path dataRoot) {
        return aiConfig(dataRoot, List.of());
    }

    private AiConfig aiConfig(Path dataRoot, List<String> approvedTools) {
        return new AiConfig(
            "default-agent",
            "main",
            "main",
            "main",
            null,
            10,
            dataRoot,
            null,
            Map.of("main", new ModelConfig("qwen3", "http://localhost:11434", EndpointType.OLLAMA, 8192, null, null)),
            Map.of("default-agent", new AgentConfig("main", "You are Magenta.", approvedTools))
        );
    }

    private static final class MalformedThenFinalChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> prompts = new java.util.ArrayList<>();

        @Override
        public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
            prompts.add(prompt.getInstructions().stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n")));
            if (calls.getAndIncrement() == 0) {
                AssistantMessage toolCallMessage = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "sample_tool",
                        "{\"path\":"
                    )))
                    .build();
                return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(toolCallMessage)));
            }
            return new org.springframework.ai.chat.model.ChatResponse(List.of(
                new Generation(new AssistantMessage("Recovered after diagnostic."))
            ));
        }

        List<String> prompts() {
            return prompts;
        }
    }

    private static final class TestChatModelRouter extends ChatModelRouter {
        private final ChatModel chatModel;

        TestChatModelRouter(ChatModel chatModel) {
            super(null, null, null);
            this.chatModel = chatModel;
        }

        @Override
        public ChatModel chatModel(String model) {
            return chatModel;
        }

        @Override
        public ToolCallingChatOptions toolCallingOptions(String model) {
            return OllamaChatOptions.builder().model("qwen3").build();
        }

        @Override
        public ToolCallingChatOptions chatOptions(String model) {
            return toolCallingOptions(model);
        }
    }

    private static final class AuthenticationFailureChatModel implements ChatModel {
        @Override
        public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
            throw new NonTransientAiException(
                "401 - {\"error\":{\"message\":\"Authentication Fails, Your api key is invalid\"}}"
            );
        }
    }

    private static final class SampleToolCallback implements ToolCallback {
        private final ToolDefinition definition = new DefaultToolDefinition(
            "sample_tool",
            "Sample test tool",
            "{\"type\":\"object\"}"
        );

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return "{\"ok\":true}";
        }
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
}
