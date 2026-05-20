package io.mindspice.magenta2.api.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.mindspice.magenta2.ai.chat.service.ChatService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendControllerTest {

    private static ChatService stubChatService() {
        return new StubChatService();
    }

    @Test
    void homePageRendersWithDashboardLinks() {
        FrontendController controller = new FrontendController(stubChatService());
        String html = controller.home(null, null);

        assertThat(html).contains("/css/magenta.css?v=2");
        assertThat(html).doesNotContain("/js/alpha-security.js?v=1");
        assertThat(html).contains("Magenta Portal");
        assertThat(html).contains("/chat");
        assertThat(html).contains("/dashboard");
        assertThat(html).contains("/webjars/htmx.org/dist/htmx.min.js");
        assertThat(html).doesNotContain("hx-get=\"/chat\"");
        assertThat(html).doesNotContain("hx-get=\"/dashboard\"");
        assertThat(html).doesNotContain("<style>");
    }

    @Test
    void chatPageRendersSimplyPagesChatShell() {
        FrontendController controller = new FrontendController(stubChatService());

        String html = controller.chat(null, null, null, null);

        assertThat(html).contains("/css/magenta.css?v=2");
        assertThat(html).doesNotContain("/js/alpha-security.js?v=1");
        assertThat(html).contains("id=\"chat-token-usage\"");
        assertThat(html).contains("id=\"chat-plan-evidence\"");
        assertThat(html).contains("id=\"magenta-chat-module\"");
        assertThat(html).contains("data-sp-chat=\"true\"");
        assertThat(html).contains("/api/fragments/chat/transcript");
        assertThat(html).contains("/js/chat-client.js?v=26");
        assertThat(html).contains("id=\"chat-planning-panel\"");
        assertThat(html).contains("class=\"chat-session-bulk-actions\"");
        assertThat(html).doesNotContain(".chat-session-hash-chip");
        assertThat(html).contains("id=\"chat-session-select-all\"");
        assertThat(html).contains("data-bulk-action=\"delete\"");
        assertThat(html).doesNotContain("id=\"chat-session-bulk-list\"");
        assertThat(html).contains("data-active-conversation-id");
        assertThat(html).contains("<code id=\"chat-active-session\">New chat</code>");
        assertThat(html).contains("/webjars/htmx.org/dist/htmx.min.js");
        assertThat(html).doesNotContain("<style>");
    }

    @Test
    void chatPageIsolatesFromOrchestrationScripts() {
        FrontendController controller = new FrontendController(stubChatService());
        String html = controller.chat(null, null, null, null);

        // Chat page must NOT load orchestration dashboard scripts
        assertThat(html).doesNotContain("/js/orchestration/dashboard.js");
        assertThat(html).doesNotContain("/js/orchestration/plans.js");
        assertThat(html).doesNotContain("/js/orchestration/workflows.js");
        assertThat(html).doesNotContain("/js/orchestration/inbox.js");
        assertThat(html).doesNotContain("data-orchestration-page");
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
        assertThat(js).contains("data-planning-approval-preview");
        assertThat(js).contains("approvalHtml");
        assertThat(js).contains("Planning mode received.");
        assertThat(js).contains("Approve And Exec");
        assertThat(js).contains("/plan/execute/stream");
        assertThat(js).contains("'Accept': 'text/event-stream, application/json'");
        assertThat(js).doesNotContain("/plan/execute',");
        assertThat(js).doesNotContain("Execution request received.");
        assertThat(js).doesNotContain("Execute now");
        assertThat(js).contains("data-transient-assistant");
        assertThat(js).contains("function clearPlanningPanel()");
        assertThat(js).contains("clearPlanningPanel();");
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
