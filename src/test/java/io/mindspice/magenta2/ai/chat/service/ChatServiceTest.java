package io.mindspice.magenta2.ai.chat.service;

import io.mindspice.magenta2.ai.chat.model.ChatHistoryMessage;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
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
        ChatHistoryMessage message = chatService.renderAssistantMessage(
            "<think>private **notes**</think>\n\nVisible **answer**"
        );

        assertThat(message.role()).isEqualTo("assistant");
        assertThat(message.text()).isEqualTo("Visible **answer**");
        assertThat(message.renderedHtml()).contains("<strong>answer</strong>");
        assertThat(message.thinkingHtml()).contains("<strong>notes</strong>");
    }
}
