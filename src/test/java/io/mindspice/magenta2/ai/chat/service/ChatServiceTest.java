package io.mindspice.magenta2.ai.chat.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.plan.ChatPlanRepository;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import io.mindspice.magenta2.ai.chat.repository.SQLiteChatMemoryRepository;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

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
            new SQLiteChatMemoryRepository(jdbcTemplate, new ObjectMapper())
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
    void detectsOllamaToolUnsupportedErrors() {
        NonTransientAiException exception = new NonTransientAiException(
            "HTTP 400 - {\"error\":\"registry.ollama.ai/library/model:latest does not support tools\"}"
        );

        assertThat(ChatService.isToolUnsupported(exception)).isTrue();
        assertThat(ChatService.isToolUnsupported(new NonTransientAiException("HTTP 500 - other failure"))).isFalse();
    }

    @Test
    void discardLastUserMessageRemovesOnlyMatchingDanglingUserTurn() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        SQLiteChatMemoryRepository memoryRepository = new SQLiteChatMemoryRepository(jdbcTemplate, new ObjectMapper());
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

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
