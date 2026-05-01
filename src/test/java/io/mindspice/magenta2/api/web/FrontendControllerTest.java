package io.mindspice.magenta2.api.web;

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(html).contains("id=\"chat-plan-evidence\"");
        assertThat(html).contains(".chat-tool");
        assertThat(html).contains(".chat-sessions summary::after");
        assertThat(html).contains("grid-template-columns: auto minmax(5rem, 10rem) auto minmax(0, 1fr);");
        assertThat(html).contains("flex-direction: column;");
        assertThat(html).contains("width: 100%;");
        assertThat(html).contains("/js/chat-client.js?v=13");
        assertThat(html).contains("data-active-conversation-id=\"\"");
        assertThat(html).contains("<code id=\"chat-active-session\">New chat</code>");
    }

    @Test
    void chatClientHandlesUnsavedConversationState() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/chat-client.js"));

        assertThat(js).contains("return value ? value : null;");
        assertThat(js).contains("activeEl.textContent = title || conversationId || 'New chat';");
        assertThat(js).contains("renderSessions(data.sessions || data.conversationIds);");
        assertThat(js).contains("pollConversationTitle(completedConversationId);");
        assertThat(js).contains("if (!conversationId) {");
        assertThat(js).contains("renderHistory([]);");
    }

    private static class StubChatService extends ChatService {

        StubChatService() {
            super(null, null, null, null, null);
        }

        @Override
        public String newConversationId() {
            throw new AssertionError("/chat should not allocate a conversation id");
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
