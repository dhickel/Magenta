package io.mindspice.magenta2.ai.chat.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import io.mindspice.magenta2.ai.chat.tool.ToolTranscriptService;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContextManagementAdvisorTest {

    @Test
    void compactionCarriesPreviousHiddenSummaryForward() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        SummaryChatModel summaryModel = new SummaryChatModel();
        ContextManagementAdvisor advisor = new ContextManagementAdvisor(
            memoryRepository,
            aiConfig(),
            new SummaryRouter(summaryModel),
            new CharacterTokenEstimator(),
            new ContextUsageTracker(),
            new ToolTranscriptService(new ObjectMapper()),
            null
        );
        memoryRepository.saveAll("conversation-1", List.of(
            new SystemMessage(ContextManagementAdvisor.SUMMARY_PREFIX + "prior summary to keep"),
            new UserMessage("older message " + "x".repeat(250)),
            new AssistantMessage("older answer " + "x".repeat(250)),
            new UserMessage("older message " + "x".repeat(250)),
            new AssistantMessage("older answer " + "x".repeat(250)),
            new UserMessage("tail one " + "x".repeat(60)),
            new AssistantMessage("tail two " + "x".repeat(60)),
            new UserMessage("tail three " + "x".repeat(60)),
            new AssistantMessage("tail four " + "x".repeat(60)),
            new UserMessage("tail five " + "x".repeat(60)),
            new AssistantMessage("tail six " + "x".repeat(60))
        ));

        advisor.preparePrompt("conversation-1", List.of(new SystemMessage("system"), new UserMessage("current")), "qwen3");

        assertThat(summaryModel.lastPromptText).contains("prior summary to keep");
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .filteredOn(advisor::isCompactionNotice)
            .hasSize(1);
    }

    @Test
    void toolLoopCheckpointCompactsActiveToolMessagesBeforeContinuing() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        SummaryChatModel summaryModel = new SummaryChatModel();
        ContextManagementAdvisor advisor = new ContextManagementAdvisor(
            memoryRepository,
            aiConfig(),
            new SummaryRouter(summaryModel),
            new CharacterTokenEstimator(),
            new ContextUsageTracker(),
            new ToolTranscriptService(new ObjectMapper()),
            null
        );
        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("current")));

        ContextManagementAdvisor.ToolLoopPrompt prompt = advisor.prepareToolLoopPrompt(
            "conversation-1",
            List.of(
                new SystemMessage("active old " + "x".repeat(80)),
                new SystemMessage("active old " + "y".repeat(300)),
                new SystemMessage("active old " + "z".repeat(300)),
                new SystemMessage("active old " + "a".repeat(300)),
                new SystemMessage("active tail zero"),
                new SystemMessage("active tail one"),
                new SystemMessage("active tail two"),
                new SystemMessage("active tail three"),
                new SystemMessage("active tail one"),
                new SystemMessage("active tail two")
            ),
            List.of(new SystemMessage("system")),
            "qwen3"
        );

        assertThat(prompt.compacted()).isTrue();
        assertThat(prompt.activeMessages().getFirst().getText()).contains("Compacted active tool-use summary");
        assertThat(summaryModel.lastPromptText).contains("active old");
    }

    @Test
    void maintainStoredContextCompactsBeforeReturningUsage() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        SummaryChatModel summaryModel = new SummaryChatModel();
        ContextManagementAdvisor advisor = new ContextManagementAdvisor(
            memoryRepository,
            aiConfig(),
            new SummaryRouter(summaryModel),
            new CharacterTokenEstimator(),
            new ContextUsageTracker(),
            new ToolTranscriptService(new ObjectMapper()),
            null
        );
        memoryRepository.saveAll("conversation-1", List.of(
            new UserMessage("older message " + "x".repeat(250)),
            new AssistantMessage("older answer " + "x".repeat(250)),
            new UserMessage("older message " + "x".repeat(250)),
            new AssistantMessage("older answer " + "x".repeat(250)),
            new UserMessage("tail one " + "x".repeat(60)),
            new AssistantMessage("tail two " + "x".repeat(60)),
            new UserMessage("tail three " + "x".repeat(60)),
            new AssistantMessage("tail four " + "x".repeat(60)),
            new UserMessage("tail five " + "x".repeat(60)),
            new AssistantMessage("tail six " + "x".repeat(60))
        ));

        ContextManagementAdvisor.StoredContextMaintenance maintenance = advisor.maintainStoredContext("conversation-1", "qwen3");

        assertThat(maintenance.compacted()).isTrue();
        assertThat(maintenance.usage().usedTokens()).isLessThanOrEqualTo(maintenance.usage().triggerTokens());
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .filteredOn(advisor::isHiddenSummary)
            .hasSize(1);
        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .filteredOn(advisor::isCompactionNotice)
            .hasSize(1);
    }

    private AiConfig aiConfig() {
        return new AiConfig(
            "magenta",
            "summary",
            10,
            null,
            Map.of(
                "main", new ModelConfig("qwen3", "http://localhost:11434", EndpointType.OLLAMA, 1200, null, null),
                "summary", new ModelConfig("summary-model", "http://localhost:11434", EndpointType.OLLAMA, 512, null, null)
            ),
            Map.of("magenta", new AgentConfig("main", "You are Magenta.", List.of()))
        );
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
                    total += estimate(content.getText());
                }
            }
            return total;
        }
    }

    private static final class SummaryRouter extends ChatModelRouter {
        private final ChatClient chatClient;

        SummaryRouter(ChatModel chatModel) {
            super(null, null, null);
            this.chatClient = ChatClient.builder(chatModel).build();
        }

        @Override
        public ChatClient chatClient(String model) {
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

        @Override
        public ToolCallingChatOptions chatOptions(String model) {
            return ollamaOptions(model);
        }

        @Override
        public ToolCallingChatOptions toolCallingOptions(String model) {
            return ollamaOptions(model);
        }
    }

    private static final class SummaryChatModel implements ChatModel {
        private String lastPromptText = "";

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPromptText = prompt.getInstructions().stream()
                .map(Message::getText)
                .collect(java.util.stream.Collectors.joining("\n"));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("short summary"))));
        }
    }
}
