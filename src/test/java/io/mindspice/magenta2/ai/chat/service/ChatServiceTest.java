package io.mindspice.magenta2.ai.chat.service;

import java.util.Map;

import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    private final ChatService chatService = new ChatService(
        null,
        null,
        null,
        null,
        new ChatMarkdownRenderer(),
        null
    );

    @Test
    void renderAssistantMessageSplitsThinkingAndRendersMarkdown() {
        ChatMessage message = chatService.renderAssistantMessage(
            "<think>private **notes**</think>\n\nVisible **answer**"
        );

        assertThat(message.role()).isEqualTo("assistant");
        assertThat(message.text()).isEqualTo("Visible **answer**");
        assertThat(message.renderedHtml()).contains("<strong>answer</strong>");
        assertThat(message.thinkingHtml()).contains("<strong>notes</strong>");
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
            null,
            new ChatMarkdownRenderer(),
            aiConfig
        );

        assertThat(service.defaultSystemPrompt()).isEqualTo("You are Magenta.");
    }
}
