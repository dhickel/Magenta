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
        assertThat(html).contains("/js/chat-client.js?v=21");
        assertThat(html).contains(".chat-session-actions");
        assertThat(html).contains(".chat-session-rename");
        assertThat(html).contains(".chat-session-topline");
        assertThat(html).contains(".chat-session-inline-hash");
        assertThat(html).contains(".chat-session-title-label");
        assertThat(html).doesNotContain(".chat-session-title-label::before");
        assertThat(html).doesNotContain(".chat-session-hash-chip");
        assertThat(html).contains("id=\"chat-session-select-all\"");
        assertThat(html).contains("data-bulk-action=\"delete\"");
        assertThat(html).doesNotContain("id=\"chat-session-bulk-list\"");
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
        assertThat(js).contains("data-rename-id");
        assertThat(js).contains("data-delete-id");
        assertThat(js).contains("data-favorite-id");
        assertThat(js).contains("data-archive-id");
        assertThat(js).contains("data-bulk-select");
        assertThat(js).contains("data-bulk-action");
        assertThat(js).contains("shortConversationLabel");
        assertThat(js).contains("syncSelectAllCheckbox");
        assertThat(js).contains("slice(0, 8)");
        assertThat(js).contains("chat-session-inline-hash");
        assertThat(js).contains("chat-session-title-label");
        assertThat(js).contains("chat-session-title-text");
        assertThat(js).doesNotContain("chat-session-hash-chip");
        assertThat(js).contains("selectedSessionIds.clear();");
        assertThat(js).contains("Delete chat");
        assertThat(js).contains("Archive chat");
        assertThat(js).contains("method: 'PATCH'");
        assertThat(js).contains("method: 'DELETE'");
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
