package io.mindspice.magenta2.api.web;

import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.simplypages.builders.BannerBuilder;
import io.mindspice.simplypages.builders.ShellBuilder;
import io.mindspice.simplypages.builders.ShellTemplate;
import io.mindspice.simplypages.builders.TopNavBuilder;
import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.components.Header;
import io.mindspice.simplypages.components.Paragraph;
import io.mindspice.simplypages.components.TextNode;
import io.mindspice.simplypages.components.chat.ChatTransportMode;
import io.mindspice.simplypages.components.chat.ChatUiConfig;
import io.mindspice.simplypages.components.display.Card;
import io.mindspice.simplypages.components.forms.Button;
import io.mindspice.simplypages.components.forms.Form;
import io.mindspice.simplypages.components.forms.Select;
import io.mindspice.simplypages.components.forms.TextArea;
import io.mindspice.simplypages.components.forms.TextInput;
import io.mindspice.simplypages.components.navigation.Link;
import io.mindspice.simplypages.core.Component;
import io.mindspice.simplypages.core.HtmlTag;
import io.mindspice.simplypages.layout.Column;
import io.mindspice.simplypages.layout.Page;
import io.mindspice.simplypages.layout.Row;
import io.mindspice.simplypages.modules.ChatModule;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

@Controller
public class FrontendController {

    private static final String APP_CSS = "/css/magenta.css?v=2";
    private static final String ORCH_JS = "/js/orchestration/app.js?v=2";
    private static final String TOOLS_JS = "/js/magenta-tools.js?v=1";
    private static final String CHAT_JS = "/js/chat-client.js?v=24";

    private final ChatService chatService;
    private final Component topNavBar;
    private final ShellTemplate pageShell;
    private final ShellTemplate chatShell;
    private final ShellTemplate orchestrationShell;
    private final ShellTemplate toolsShell;

    public FrontendController(ChatService chatService) {
        this.chatService = chatService;
        this.topNavBar = TopNavBuilder.create()
            .addPrimaryLink("Home", "/")
            .addPrimaryLink("Chat", "/chat")
            .addPrimaryLink("Settings", "/settings")
            .addPrimaryLink("Agents", "/agents")
            .addPrimaryLink("Jobs", "/jobs")
            .addPrimaryLink("Tasks", "/tasks")
            .addPrimaryLink("Workflows", "/workflows")
            .build();
        this.pageShell = shell("Magenta Portal", "Magenta Portal", "Operational assistant console")
            .buildTemplate();
        this.chatShell = shell("Magenta Chat", "Magenta Chat", "Session-backed assistant workspace")
            .addCustomJs(CHAT_JS)
            .buildTemplate();
        this.orchestrationShell = shell("Magenta Operations", "Magenta Operations", "Agents, jobs, and runtime controls")
            .buildTemplate();
        this.toolsShell = shell("Magenta Tools", "Magenta Tools", "Reusable tasks and workflows")
            .buildTemplate();
    }

    private ShellBuilder shell(String pageTitle, String title, String subtitle) {
        return ShellBuilder.create()
            .withPageTitle(pageTitle)
            .withCustomCss(APP_CSS)
            .withTopBanner(BannerBuilder.create()
                .withLayout(BannerBuilder.BannerLayout.CENTERED)
                .withTitle(title)
                .withSubtitle(subtitle)
                .build())
            .withTopNav(topNavBar);
    }

    @GetMapping("/")
    @ResponseBody
    public String home(
        @RequestHeader(value = "HX-Request", required = false) String hxRequest,
        HttpServletResponse response
    ) {
        return pageShell.renderWithContent(Page.builder()
            .addComponents(new Div().withClass("mag-page mag-home")
                .withChild(pageHeader("Magenta Portal", "Chat, plan, delegate, and monitor operational assistant work."))
                .withChild(new Row()
                    .addColumn(homeCard("Chat", "Continue an assistant conversation with session management.", "/chat"))
                    .addColumn(homeCard("Agents", "Manage agent profiles, work queues, and direct-line chat.", "/agents"))
                    .addColumn(homeCard("Tasks", "Build and run reusable task definitions.", "/tasks")))
                .withChild(new Row()
                    .addColumn(homeCard("Jobs", "Inspect orchestration jobs and run history.", "/jobs"))
                    .addColumn(homeCard("Workflows", "Compose tasks into ordered workflows.", "/workflows"))
                    .addColumn(homeCard("Settings", "Tune runtime defaults and model routing.", "/settings"))))
            .build());
    }

    @GetMapping(value = "/webjars/htmx.org/dist/htmx.min.js", produces = "application/javascript")
    @ResponseBody
    public String htmxCompatResource() {
        return "window.htmx=window.htmx||{version:\"compat-noop\",process:function(){},onLoad:function(){}};";
    }

    @GetMapping("/chat")
    @ResponseBody
    public String chat(
        @RequestHeader(value = "HX-Request", required = false) String hxRequest,
        HttpServletResponse response
    ) {
        Component chatContent = new Div()
            .withId("chat-page")
            .withAttribute("data-chat-root", "true")
            .withAttribute("data-active-conversation-id", "")
            .withClass("chat-page")
            .withChild(new Div().withClass("chat-layout")
                .withChild(sessionSidebar())
                .withChild(new Div().withClass("chat-main")
                    .withChild(chatToolbar())
                    .withChild(planStatus())
                    .withChild(chatModule())
                    .withChild(tokenUsage())))
            .withChild(new Div().withId("chat-error").withAttribute("role", "status").withAttribute("aria-live", "polite"));
        return chatShell.renderWithContent(chatContent);
    }

    @GetMapping("/settings")
    @ResponseBody
    public String settings() {
        return orchestrationPage("settings", "Runtime Settings", "Default agent, model routing, and context controls.",
            new Div().withClass("orch-layout")
                .withChild(new Div().withClass("orch-panel")
                    .withChild(Header.H2("Model Routing"))
                    .withChild(new Div().withClass("orch-form-grid")
                        .withChild(label("Default Agent", TextInput.create("defaultAgentId").withId("settings-default-agent-id")))
                        .withChild(label("Default Agent Name", TextInput.create("defaultAgentName").withId("settings-default-agent-name")))
                        .withChild(label("Default Model", Select.create("defaultModel").withId("settings-default-model")))
                        .withChild(label("Planning Model", Select.create("planningModel").withId("settings-planning-model")))
                        .withChild(label("Summary Model", Select.create("summaryModel").withId("settings-summary-model")))
                        .withChild(label("Compaction Model", Select.create("compactionModel").withId("settings-compaction-model")))
                        .withChild(label("Context Buffer %", TextInput.number("contextBufferPercent").withId("settings-context-buffer").withMin("0").withMax("90"))))
                    .withChild(new Div().withId("settings-status").withClass("orch-status").withAttribute("role", "status")))
                .withChild(new Div().withClass("orch-panel")
                    .withChild(Header.H2("Available Models"))
                    .withChild(new Div().withId("settings-model-list").withClass("orch-chip-list"))),
            Button.create("Save").withClass("orch-primary").withAttribute("data-action", "save-settings"));
    }

    @GetMapping("/agents")
    @ResponseBody
    public String agents() {
        Component actions = new Div().withClass("orch-actions")
            .withChild(Button.create("Create Agent").withClass("orch-primary").withAttribute("data-action", "create-agent"))
            .withChild(Button.create("Reload").withAttribute("data-action", "reload-agents"));
        Component body = listDetailShell(
            "agent-browser",
            "Agents",
            TextInput.search("agentFilter").withId("agent-filter").withPlaceholder("Filter agents"),
            new Div().withId("agent-cards").withClass("entity-list"),
            new Div().withClass("empty-detail")
                .withChild(Header.H2("Select an agent"))
                .withChild(new Paragraph("Open an agent to review profile, queue, inbox, workspace, and job history.")));
        return orchestrationPage("agents", "Agents", "Operational status, queues, inboxes, and assignments.", body, actions);
    }

    @GetMapping("/agents/{agentId}")
    @ResponseBody
    public String agentDetail(@PathVariable String agentId) {
        Component body = new Div()
            .withId("agent-detail-page")
            .withAttribute("data-orchestration-page", "agent-detail")
            .withAttribute("data-agent-id", agentId)
            .withChild(new Div().withClass("entity-detail-layout")
                .withChild(new Div().withClass("entity-detail-main")
                    .withChild(new Div().withClass("entity-title")
                        .withChild(Header.H2("Agent").withId("agent-detail-title"))
                        .withChild(new Paragraph("Profile, queue, inbox, workspace, and history.").withId("agent-detail-subtitle")))
                    .withChild(tabs("dashboard", "inbox", "queue", "jobs", "workspace", "history"))
                    .withChild(new Div().withId("agent-tab-panel").withClass("orch-panel")))
                .withChild(new Div().withClass("entity-detail-side")
                    .withChild(new Div().withClass("orch-panel")
                        .withChild(Header.H2("Profile"))
                        .withChild(new Div().withId("agent-profile-form").withClass("orch-form-stack")))
                    .withChild(new Div().withClass("orch-panel")
                        .withChild(Header.H2("Submit Work"))
                        .withChild(new Div().withId("agent-assignment-form").withClass("orch-form-stack")))));
        Component actions = new Div().withClass("orch-actions")
            .withChild(Button.create("Chat").withAttribute("data-action", "open-agent-chat"))
            .withChild(Button.create("Save Profile").withClass("orch-primary").withAttribute("data-action", "save-agent"));
        return orchestrationPage("agent-detail", "Agent Detail", "Focused operational view.", body, actions, "data-agent-id", agentId);
    }

    @GetMapping("/jobs")
    @ResponseBody
    public String jobs() {
        Component toolbar = new Div().withClass("entity-toolbar")
            .withChild(Select.create("agentId").withId("jobs-agent-select"))
            .withChild(Button.create("Reload").withAttribute("data-action", "reload-jobs"));
        Component body = listDetailShell(
            "job-browser",
            "Jobs",
            toolbar,
            new Div().withId("job-list").withClass("entity-list"),
            new Div().withClass("empty-detail")
                .withChild(Header.H2("Select a job"))
                .withChild(new Paragraph("Review ordered items, runs, checkpoints, and evidence.")));
        return orchestrationPage("jobs", "Jobs", "Ordered orchestration plans with checkpoints and run history.", body,
            Button.create("Create Job").withClass("orch-primary").withAttribute("data-action", "create-job"));
    }

    @GetMapping("/jobs/{jobId}")
    @ResponseBody
    public String jobDetail(@PathVariable String jobId) {
        Component body = new Div()
            .withId("job-detail-page")
            .withAttribute("data-orchestration-page", "job-detail")
            .withAttribute("data-job-id", jobId)
            .withChild(new Div().withClass("entity-detail-layout")
                .withChild(new Div().withClass("entity-detail-main")
                    .withChild(Header.H2("Job").withId("job-detail-title"))
                    .withChild(new Paragraph("Editor, ordered items, controls, checkpoints, and events.").withId("job-detail-subtitle"))
                    .withChild(new Div().withClass("orch-panel")
                        .withChild(Header.H2("Job Editor"))
                        .withChild(new Div().withId("job-editor-form").withClass("orch-form-stack"))
                        .withChild(Header.H2("Ordered Items"))
                        .withChild(new Div().withId("job-item-editor"))
                        .withChild(Button.create("Add Item").withAttribute("data-action", "add-job-item"))))
                .withChild(new Div().withClass("entity-detail-side")
                    .withChild(new Div().withClass("orch-panel").withChild(Header.H2("Run History")).withChild(new Div().withId("job-runs")))
                    .withChild(new Div().withClass("orch-panel").withChild(Header.H2("Checkpoints & Evidence")).withChild(new Div().withId("job-events")))));
        Component actions = new Div().withClass("orch-actions")
            .withChild(Button.create("Run").withAttribute("data-action", "run-job"))
            .withChild(Button.create("Pause").withAttribute("data-action", "pause-job"))
            .withChild(Button.create("Resume").withAttribute("data-action", "resume-job"))
            .withChild(Button.create("Cancel").withAttribute("data-action", "cancel-job"))
            .withChild(Button.create("Save").withClass("orch-primary").withAttribute("data-action", "save-job"));
        return orchestrationPage("job-detail", "Job Detail", "Run and inspect orchestration jobs.", body, actions, "data-job-id", jobId);
    }

    @GetMapping("/tasks")
    @ResponseBody
    public String tasks() {
        Component body = new Div()
            .withId("tasks-page")
            .withAttribute("data-orchestration-page", "tasks")
            .withChild(new Div().withClass("browser-layout")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Header.H2("Tasks"))
                        .withChild(Button.create("New").withAttribute("data-tool-action", "new-task")))
                    .withChild(new Div().withId("task-list").withClass("entity-list")))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(taskEditor())
                    .withChild(taskRunPanel())))
            .withChild(agentChatHost("task editor"));
        return toolsPage("tasks", "Tasks", "Build, inspect, and run reusable task definitions.", body, null);
    }

    @GetMapping("/workflows")
    @ResponseBody
    public String workflows() {
        Component body = new Div()
            .withId("workflows-page")
            .withAttribute("data-orchestration-page", "workflows")
            .withChild(new Div().withClass("browser-layout")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Header.H2("Workflows"))
                        .withChild(Button.create("New").withAttribute("data-tool-action", "new-workflow")))
                    .withChild(new Div().withId("workflow-list").withClass("entity-list")))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(workflowEditor())
                    .withChild(workflowRunPanel())))
            .withChild(agentChatHost("workflow editor"));
        return toolsPage("workflows", "Workflows", "Compose reusable tasks into ordered runs.", body, null);
    }

    private String orchestrationPage(String pageName, String title, String subtitle, Component body, Component actions) {
        return orchestrationPage(pageName, title, subtitle, body, actions, null, null);
    }

    private String orchestrationPage(
        String pageName,
        String title,
        String subtitle,
        Component body,
        Component actions,
        String extraAttribute,
        String extraValue
    ) {
        Div page = new Div()
            .withClass("orch-page")
            .withAttribute("data-orchestration-page", pageName)
            .withChild(pageHeader(title, subtitle, actions))
            .withChild(body)
            .withChild(agentChatHost(pageName))
            .withChild(moduleScript(ORCH_JS));
        if (extraAttribute != null && extraValue != null) {
            page.withAttribute(extraAttribute, extraValue);
        }
        return orchestrationShell.renderWithContent(page);
    }

    private String toolsPage(String pageName, String title, String subtitle, Component body, Component actions) {
        Div page = new Div()
            .withClass("orch-page tool-page")
            .withAttribute("data-orchestration-page", pageName)
            .withChild(pageHeader(title, subtitle, actions))
            .withChild(body)
            .withChild(moduleScript(ORCH_JS))
            .withChild(moduleScript(TOOLS_JS));
        return toolsShell.renderWithContent(page);
    }

    private Component chatModule() {
        return ChatModule.create()
            .withModuleId("magenta-chat-module")
            .withTranscript(new Div().withId("chat-history"))
            .withComposer(new Div()
                .withChild(new Div().withId("chat-planning-panel").withAttribute("aria-live", "polite"))
                .withChild(Form.create().withId("chat-form")
                    .withChild(TextArea.create("message").withId("chat-input").withRows(6)
                        .withPlaceholder("Type a message (Enter to send, Shift+Enter newline)")
                        .withAttribute("autocomplete", "off"))
                    .withChild(Button.submit("Send"))))
            .withUiConfig(new ChatUiConfig(
                "new",
                ChatTransportMode.SSE,
                "/api/fragments/chat/transcript",
                "/api/chat/stream",
                "#chat-history",
                "outerHTML",
                null
            ));
    }

    private Component sessionSidebar() {
        return new HtmlTag("aside").withClass("chat-sessions")
            .withChild(new HtmlTag("details").withAttribute("open", "open")
                .withChild(new HtmlTag("summary").withChild(new HtmlTag("span").withClass("chat-sessions-label").withInnerText("Sessions")))
                .withChild(new Div().withClass("chat-session-bulk")
                    .withChild(new Div().withClass("chat-session-bulk-actions")
                        .withChild(new HtmlTag("label").withClass("chat-session-select-all").withAttribute("title", "Select all chats")
                            .withChild(new HtmlTag("input", true).withAttribute("type", "checkbox").withId("chat-session-select-all").withAttribute("aria-label", "Select all chats")))
                        .withChild(Button.create("Delete").withAttribute("data-bulk-action", "delete"))
                        .withChild(Button.create("Archive").withAttribute("data-bulk-action", "archive"))
                        .withChild(Button.create("Favorite").withAttribute("data-bulk-action", "favorite"))))
                .withChild(new HtmlTag("ul").withId("chat-session-list")));
    }

    private Component chatToolbar() {
        return new Div().withClass("chat-toolbar")
            .withChild(labelInline("Agent Model", modelSelect("chat-model-select", chatService.defaultModel())))
            .withChild(labelInline("Planning Model", modelSelect("chat-planning-model-select", chatService.planningModel())))
            .withChild(new HtmlTag("span").withInnerText("Session"))
            .withChild(new HtmlTag("code").withId("chat-active-session").withInnerText("New chat"));
    }

    private Component planStatus() {
        return new Div().withId("chat-plan-status").withAttribute("aria-live", "polite")
            .withChild(new Div().withClass("chat-plan-header")
                .withChild(new HtmlTag("span").withId("chat-plan-title"))
                .withChild(new HtmlTag("span").withId("chat-plan-hint")))
            .withChild(new Div().withId("chat-plan-evidence"));
    }

    private Component tokenUsage() {
        return new Div().withId("chat-token-usage").withAttribute("aria-live", "polite")
            .withChild(new Div().withId("chat-token-usage-label")
                .withChild(new HtmlTag("span").withInnerText("Context"))
                .withChild(new HtmlTag("span").withId("chat-token-usage-text").withInnerText("0 / 0 (0%)")))
            .withChild(new Div().withId("chat-token-usage-bar").withChild(new Div().withId("chat-token-usage-fill")));
    }

    private Select modelSelect(String id, String defaultModel) {
        Select select = Select.create(id).withId(id);
        for (String model : models(defaultModel)) {
            select.addOption(model, model, model.equals(defaultModel));
        }
        return select;
    }

    private List<String> models(String defaultModel) {
        List<String> models = new ArrayList<>(chatService.availableModels());
        if (defaultModel != null && !defaultModel.isBlank() && !models.contains(defaultModel)) {
            models.add(0, defaultModel);
        }
        return models;
    }

    private Component taskEditor() {
        return new Div().withClass("orch-panel tool-editor")
            .withChild(Header.H2("Task Editor"))
            .withChild(label("Title", TextInput.create("title").withId("task-title")))
            .withChild(label("Summary", TextArea.create("summary").withId("task-summary").withRows(3)))
            .withChild(label("Goal", TextArea.create("goal").withId("task-goal").withRows(4)))
            .withChild(sectionLabel("Inputs"))
            .withChild(new Div().withId("task-inputs").withClass("field-list"))
            .withChild(Button.create("Add input").withAttribute("data-tool-action", "add-task-input"))
            .withChild(sectionLabel("Outputs"))
            .withChild(new Div().withId("task-outputs").withClass("field-list"))
            .withChild(Button.create("Add output").withAttribute("data-tool-action", "add-task-output"))
            .withChild(label("Steps", TextArea.create("steps").withId("task-steps").withRows(5).withPlaceholder("One per line")))
            .withChild(label("Validation Criteria", TextArea.create("validation").withId("task-validation").withRows(4).withPlaceholder("One per line")))
            .withChild(new Div().withClass("tool-actions")
                .withChild(Button.create("Save").withClass("orch-primary").withAttribute("data-tool-action", "save-task"))
                .withChild(Button.create("Run").withAttribute("data-tool-action", "run-task")));
    }

    private Component taskRunPanel() {
        return new Div().withClass("orch-panel")
            .withChild(Header.H2("Run"))
            .withChild(new Div().withClass("field-row")
                .withChild(TextInput.create("agentId").withId("task-run-agent-id").withPlaceholder("agent id"))
                .withChild(TextInput.create("jobId").withId("task-run-job-id").withPlaceholder("job id"))
                .withChild(TextInput.create("modelOverride").withId("task-run-model").withPlaceholder("model override"))
                .withChild(TextInput.number("priority").withId("task-run-priority").withValue("0").withPlaceholder("priority")))
            .withChild(new Div().withId("task-run-form").withClass("run-inputs"))
            .withChild(new Div().withId("task-run-log").withClass("run-log"));
    }

    private Component workflowEditor() {
        return new Div().withClass("orch-panel tool-editor")
            .withChild(Header.H2("Workflow Editor"))
            .withChild(label("Title", TextInput.create("title").withId("workflow-title")))
            .withChild(label("Summary", TextArea.create("summary").withId("workflow-summary").withRows(3)))
            .withChild(new Div().withId("workflow-steps").withClass("field-list"))
            .withChild(new Div().withClass("tool-actions")
                .withChild(Button.create("Add step").withAttribute("data-tool-action", "add-workflow-step"))
                .withChild(Button.create("Save").withClass("orch-primary").withAttribute("data-tool-action", "save-workflow"))
                .withChild(Button.create("Run").withAttribute("data-tool-action", "run-workflow")))
            .withChild(new Div().withId("workflow-warnings").withClass("warnings"));
    }

    private Component workflowRunPanel() {
        return new Div().withClass("orch-panel")
            .withChild(Header.H2("Run"))
            .withChild(new Div().withClass("field-row")
                .withChild(TextInput.create("agentId").withId("workflow-run-agent-id").withPlaceholder("agent id"))
                .withChild(TextInput.create("jobId").withId("workflow-run-job-id").withPlaceholder("job id"))
                .withChild(TextInput.create("modelOverride").withId("workflow-run-model").withPlaceholder("model override"))
                .withChild(TextInput.number("priority").withId("workflow-run-priority").withValue("0").withPlaceholder("priority")))
            .withChild(new Div().withId("workflow-run-log").withClass("run-log"));
    }

    private Component listDetailShell(String id, String title, Component controls, Component list, Component detail) {
        return new Div().withId(id).withClass("browser-layout")
            .withChild(new Div().withClass("browser-sidebar")
                .withChild(new Div().withClass("browser-sidebar-header").withChild(Header.H2(title)))
                .withChild(new Div().withClass("entity-toolbar").withChild(controls))
                .withChild(list))
            .withChild(new Div().withClass("browser-detail").withChild(detail));
    }

    private Component pageHeader(String title, String subtitle) {
        return pageHeader(title, subtitle, null);
    }

    private Component pageHeader(String title, String subtitle, Component actions) {
        Div header = new Div().withClass("orch-page-header")
            .withChild(new Div()
                .withChild(Header.H1(title))
                .withChild(new Paragraph(subtitle)));
        if (actions != null) {
            header.withChild(actions);
        }
        return header;
    }

    private Column homeCard(String title, String body, String href) {
        return Column.create().withWidth(4).withChild(Card.create()
            .withClass("home-card")
            .withHeader(title)
            .withBody(new Div()
                .withChild(new Paragraph(body))
                .withChild(new Link(href, "Open"))));
    }

    private Component tabs(String... names) {
        HtmlTag nav = new HtmlTag("nav").withClass("orch-tabs").withAttribute("aria-label", "Agent detail views");
        for (String name : names) {
            Button button = Button.create(capitalize(name));
            button.withAttribute("data-tab", name);
            if ("dashboard".equals(name)) {
                button.withClass("active");
            }
            nav.withChild(button);
        }
        return nav;
    }

    private Component label(String text, Component input) {
        return new HtmlTag("label").withChild(new TextNode(text)).withChild(input);
    }

    private Component labelInline(String text, Component input) {
        return new HtmlTag("label").withClass("inline-field").withChild(new TextNode(text)).withChild(input);
    }

    private Component sectionLabel(String text) {
        return new HtmlTag("div").withClass("section-label").withInnerText(text);
    }

    private Component agentChatHost(String context) {
        return new Div().withAttribute("data-agent-chat-panel", "").withAttribute("data-page-context", context);
    }

    private Component moduleScript(String src) {
        return new HtmlTag("script").withAttribute("type", "module").withAttribute("src", src);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
