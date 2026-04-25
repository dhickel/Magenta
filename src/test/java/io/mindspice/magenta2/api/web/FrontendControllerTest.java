package io.mindspice.magenta2.api.web;

import java.util.List;

import io.mindspice.magenta2.ai.chat.service.ChatService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendControllerTest {

    @Test
    void chatPageRendersEmbeddedCssPercentSigns() {
        FrontendController controller = new FrontendController(new StubChatService());

        String html = controller.chat(null, null);

        assertThat(html).contains("max-width: 100%;");
        assertThat(html).contains("id=\"chat-token-usage\"");
        assertThat(html).contains("data-active-conversation-id=\"00000000-0000-0000-0000-000000000001\"");
    }

    private static class StubChatService extends ChatService {

        StubChatService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public String newConversationId() {
            return "00000000-0000-0000-0000-000000000001";
        }

        @Override
        public String defaultModel() {
            return "qwen3";
        }

        @Override
        public List<String> availableModels() {
            return List.of("qwen3");
        }
    }
}
