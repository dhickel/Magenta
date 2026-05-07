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
        assertThat(html).contains("/js/chat-client.js?v=23");
        assertThat(html).contains("id=\"chat-planning-panel\"");
        assertThat(html).contains(".planning-preview-document");
        assertThat(html).contains(".chat-message-transient");
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
        assertThat(js).contains("data-planning-approval-preview");
        assertThat(js).contains("approvalHtml");
        assertThat(js).contains("Planning mode received.");
        assertThat(js).contains("Execution request received.");
        assertThat(js).contains("data-transient-assistant");
        assertThat(js).contains("function clearPlanningPanel()");
        assertThat(js).contains("clearPlanningPanel();");
    }

    @Test
    void taskAndWorkflowPagesExposeEditorAndRunControls() {
        FrontendController controller = new FrontendController(new StubChatService());

        assertThat(controller.tasks())
            .contains("id=\"tasks-page\"")
            .contains("data-orchestration-page=\"tasks\"")
            .contains("Task Editor")
            .contains("id=\"task-inputs\"")
            .contains("id=\"task-outputs\"")
            .contains("id=\"task-run-form\"")
            .contains("id=\"task-run-agent-id\"")
            .contains("modelOverride")
            .contains("/api/tasks")
            .doesNotContain("task-deliverables")
            .doesNotContain("deliverables: lines");
        assertThat(controller.workflows())
            .contains("id=\"workflows-page\"")
            .contains("data-orchestration-page=\"workflows\"")
            .contains("Workflow Editor")
            .contains("Bindings JSON")
            .contains("id=\"workflow-warnings\"")
            .contains("id=\"workflow-run-agent-id\"")
            .contains("modelOverride")
            .contains("/api/workflows");
    }

    @Test
    void orchestrationPagesLoadStaticAssetsWithoutChatClient() {
        FrontendController controller = new FrontendController(new StubChatService());

        for (String html : List.of(
            controller.settings(),
            controller.agents(),
            controller.agentDetail("agent-1"),
            controller.jobs(),
            controller.jobDetail("job-1"),
            controller.tasks(),
            controller.workflows()
        )) {
            assertThat(html).contains("/css/orchestration.css?v=1");
            assertThat(html).contains("/js/orchestration/app.js?v=1");
            assertThat(html).doesNotContain("/js/chat-client.js");
        }
    }

    @Test
    void orchestrationStaticFilesExposeEndpointsAndSseParsing() throws Exception {
        String app = Files.readString(Path.of("src/main/resources/static/js/orchestration/app.js"));
        String api = Files.readString(Path.of("src/main/resources/static/js/orchestration/api.js"));
        String chat = Files.readString(Path.of("src/main/resources/static/js/orchestration/agent-chat.js"));

        assertThat(app)
            .contains("data-orchestration-page")
            .contains("/api/settings/runtime")
            .contains("/api/agents")
            .contains("/api/jobs")
            .contains("/api/tasks")
            .contains("/api/workflows");
        assertThat(api)
            .contains("function parseSse")
            .contains("event:")
            .contains("data:")
            .contains("renderError");
        assertThat(chat)
            .contains("data-agent-chat-panel")
            .contains("/api/agents/${encodeURIComponent(agentId)}/chat/stream")
            .contains("pageContext");
    }

    @Test
    void htmxCompatibilityResourceStopsShell404() {
        FrontendController controller = new FrontendController(new StubChatService());

        assertThat(controller.htmxCompatResource()).contains("window.htmx");
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
