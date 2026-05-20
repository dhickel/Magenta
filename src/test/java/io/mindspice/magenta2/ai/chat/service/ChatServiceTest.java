package io.mindspice.magenta2.ai.chat.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.ChatSession;
import io.mindspice.magenta2.ai.chat.plan.PlanRepository;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanStatus;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.chat.repository.RepositoryBackedChatMemory;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

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
    void listSessionsIncludesChatFileOutputCount() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        Path dataRoot = Files.createDirectories(tempDir.resolve("data"));
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
        Files.writeString(files.resolve("summary.txt"), "summary");

        assertThat(chatService.listSessions())
            .singleElement()
            .extracting(ChatSession::outputCount)
            .isEqualTo(1);
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        return new JdbcTemplate(dataSource);
    }

    private AiConfig aiConfig() {
        return aiConfig(Path.of("."));
    }

    private AiConfig aiConfig(Path dataRoot) {
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
            Map.of("default-agent", new AgentConfig("main", "You are Magenta.", List.of()))
        );
    }
}
