package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldType;
import io.mindspice.magenta2.ai.chat.plan.PlanKind;
import io.mindspice.magenta2.ai.chat.plan.PlanRun;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanStatus;
import io.mindspice.magenta2.ai.chat.plan.PlanStep;
import io.mindspice.magenta2.ai.chat.plan.WorkTypeProfile;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.chat.tool.shell.AgentShellToolService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentEventReaction;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentSchedule;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService.AssignmentDiagnostics;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.EventReactionService;
import io.mindspice.magenta2.ai.orchestration.runtime.EventType;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRecurrence;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRun;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItem;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItemType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.Project;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectAgentMembership;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectService;
import io.mindspice.magenta2.ai.orchestration.runtime.ReactionActionType;
import io.mindspice.magenta2.ai.orchestration.runtime.ScheduleService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowNode;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowNodeType;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowRoute;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowRouteType;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowValidator;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.Workspace;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLease;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLink;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import io.mindspice.simplypages.builders.BannerBuilder;
import io.mindspice.simplypages.builders.ShellBuilder;
import io.mindspice.simplypages.builders.ShellTemplate;
import io.mindspice.simplypages.builders.TopNavBuilder;
import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.components.Header;
import io.mindspice.simplypages.components.Paragraph;
import io.mindspice.simplypages.components.TextNode;
import io.mindspice.simplypages.components.display.Table;
import io.mindspice.simplypages.components.forms.Button;
import io.mindspice.simplypages.components.forms.Form;
import io.mindspice.simplypages.components.forms.Select;
import io.mindspice.simplypages.components.forms.TextArea;
import io.mindspice.simplypages.components.forms.TextInput;
import io.mindspice.simplypages.components.navigation.SideNav;
import io.mindspice.simplypages.core.Component;
import io.mindspice.simplypages.core.HtmlTag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class OrchestrationController {
    private static final String DASHBOARD_CSS = "/css/orchestration.css?v=7";
    private static final String DASHBOARD_JS = "/js/orchestration/dashboard.js?v=5";
    private static final String AGENTS_JS = "/js/orchestration/agents.js?v=1";
    private static final String AGENT_CHAT_JS = "/js/orchestration/agent-chat.js?v=2";
    private static final String PLANS_JS = "/js/orchestration/plans.js?v=2";
    private static final String WORKFLOWS_JS = "/js/orchestration/workflows.js?v=2";
    private static final String PROJECTS_JS = "/js/orchestration/projects.js?v=3";
    private static final String INBOX_JS = "/js/orchestration/inbox.js?v=1";
    private static final String OUTPUTS_JS = "/js/orchestration/outputs.js?v=1";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final ChatService chatService;
    private final ShellTemplate dashboardShell;

    // ── Dashboard data services ──
    private final ProjectService projectService;
    private final JobService jobService;
    private final AgentProfileService agentProfileService;
    private final InboxService inboxService;
    private final io.mindspice.magenta2.ai.orchestration.runtime.InboxService runtimeInboxService;
    private final OutputArtifactService outputArtifactService;
    private final RuntimeSettingsService runtimeSettingsService;
    private final WorkspaceService workspaceService;

    // ── Plan editor services ──
    private final PlanService planService;
    private final AssignmentService assignmentService;
    private final ScheduleService scheduleService;
    private final EventReactionService eventReactionService;

    // ── Workflow editor services ──
    private final WorkflowService workflowService;

    // ── Runtime services ──
    private final org.springframework.beans.factory.ObjectProvider<AgentShellToolService> execShellServiceRef;
    private final boolean schedulesEnabled;
    private final boolean reactionsEnabled;

    public OrchestrationController(ChatService chatService,
                                   ProjectService projectService,
                                   JobService jobService,
                                   AgentProfileService agentProfileService,
                                   InboxService inboxService,
                                   io.mindspice.magenta2.ai.orchestration.runtime.InboxService runtimeInboxService,
                                   OutputArtifactService outputArtifactService,
                                   RuntimeSettingsService runtimeSettingsService,
                                   WorkspaceService workspaceService,
                                   PlanService planService,
                                   AssignmentService assignmentService,
                                   ScheduleService scheduleService,
                                   EventReactionService eventReactionService,
                                   WorkflowService workflowService,
                                   org.springframework.beans.factory.ObjectProvider<AgentShellToolService> execShellServiceRef,
                                   @Value("${magenta.features.schedules-enabled:false}") boolean schedulesEnabled,
                                   @Value("${magenta.features.reactions-enabled:false}") boolean reactionsEnabled) {
        this.chatService = chatService;
        this.projectService = projectService;
        this.jobService = jobService;
        this.agentProfileService = agentProfileService;
        this.inboxService = inboxService;
        this.runtimeInboxService = runtimeInboxService;
        this.outputArtifactService = outputArtifactService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.workspaceService = workspaceService;
        this.planService = planService;
        this.assignmentService = assignmentService;
        this.scheduleService = scheduleService;
        this.eventReactionService = eventReactionService;
        this.workflowService = workflowService;
        this.execShellServiceRef = execShellServiceRef;
        this.schedulesEnabled = schedulesEnabled;
        this.reactionsEnabled = reactionsEnabled;
        this.dashboardShell = createDashboardShell(null);
    }

    private ShellTemplate createDashboardShell(String activePath) {
        SideNav sideNav = buildSideNav(activePath);

        return ShellBuilder.create()
            .withPageTitle("Magenta Dashboard")
            .withCustomCss(DASHBOARD_CSS)
            .withTopBanner(BannerBuilder.create()
                .withLayout(BannerBuilder.BannerLayout.CENTERED)
                .withTitle("Magenta Operations")
                .withSubtitle("Orchestration dashboard")
                .build())
            .withTopNav(TopNavBuilder.create()
                .withHtmxNavigation(false)
                .addPrimaryLink("Chat", "/chat")
                .build())
            .withSideNav(sideNav, true)
            .buildTemplate();
    }

    private SideNav buildSideNav(String activePath) {
        SideNav nav = SideNav.create();
        nav.addSection("Orchestration");
        nav.addItem("Dashboard", "/dashboard", isActivePath(activePath, "/dashboard"));
        nav.addItem("Plans", "/plans", isActivePath(activePath, "/plans"));
        nav.addItem("Workflows", "/workflows", isActivePath(activePath, "/workflows"));
        nav.addItem("Jobs", "/jobs", isActivePath(activePath, "/jobs"));
        nav.addItem("Projects", "/projects", isActivePath(activePath, "/projects"));
        nav.addSection("Communication");
        nav.addItem("Inbox", "/inbox", isActivePath(activePath, "/inbox"));
        nav.addItem("Agents", "/agents", isActivePath(activePath, "/agents"));
        nav.addSection("Tools");
        nav.addItem("Outputs", "/outputs", isActivePath(activePath, "/outputs"));
        nav.addItem("Settings", "/settings", isActivePath(activePath, "/settings"));
        return nav;
    }

    private boolean isActivePath(String activePath, String navPath) {
        return activePath != null && (activePath.equals(navPath) || activePath.startsWith(navPath + "/"));
    }

    private String renderPage(Component content) {
        return dashboardShell.renderWithContent(content);
    }

    private String renderPage(Component content, String activePath) {
        return createDashboardShell(activePath).renderWithContent(content);
    }

    // ════════════════════════════════════════════════════════════════
    //  Dashboard landing
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/dashboard")
    @ResponseBody
    public String dashboard(
        @RequestHeader(value = "HX-Request", required = false) String hxRequest,
        HttpServletResponse response
    ) {
        Component body = new Div()
            .withAttribute("data-orchestration-page", "dashboard")
            .withClass("dashboard-operational")
            .withChild(dashboardChatBand())
            .withChild(dashboardStatusStrip())
            .withChild(dashboardMainLayout())
            .withChild(moduleScript(DASHBOARD_JS));
        return renderPage(body, "/dashboard");
    }

    private Component dashboardChatBand() {
        return new Div().withClass("dashboard-chat-band")
            .withChild(new HtmlTag("details").withClass("dashboard-system-chat-accordion")
                .withChild(new HtmlTag("summary")
                    .withClass("dashboard-chat-label")
                    .withInnerText("System Chat"))
                .withChild(new Div().withClass("dashboard-chat-band-inner")
                    .withChild(new Paragraph("Use the existing chat flow for system-level planning and operations."))
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "/chat")
                        .withClass("orch-primary")
                        .withInnerText("Open Chat View"))));
    }

    private Component dashboardStatusStrip() {
        Div container = new Div().withClass("dashboard-stats-wrapper");

        // Stats loaded via HTMX
        container.withChild(new Div().withId("dashboard-stats-container")
            .hxGet("/dashboard/_stats")
            .hxTrigger("load, every 30s")
            .hxSwap("innerHTML")
            .withChild(statsStripPlaceholder()));

        return container;
    }

    private Component statsStripPlaceholder() {
        return new Div().withClass("dashboard-status-strip")
            .withChild(dashboardStat("stat-running", "Running", "—"))
            .withChild(dashboardStat("stat-pending", "Pending", "—"))
            .withChild(dashboardStat("stat-messages", "Messages", "—"))
            .withChild(dashboardStat("stat-failed", "Failed", "—"))
            .withChild(dashboardStat("stat-agents", "Active Agents", "—"));
    }

    private Component dashboardStat(String id, String label, String value) {
        return new Div().withClass("dashboard-stat")
            .withChild(new HtmlTag("span").withClass("dashboard-stat-value")
                .withId(id).withInnerText(value))
            .withChild(new HtmlTag("span").withClass("dashboard-stat-label")
                .withInnerText(label));
    }

    private Component dashboardMainLayout() {
        return new Div().withClass("dashboard-main-layout")
            .withChild(new Div().withClass("dashboard-primary")
                .withChild(dashboardHxSection("Active Work", "active-work-section",
                    "/dashboard/_active-work"))
                .withChild(dashboardHxSection("Open Projects", "open-projects-section",
                    "/dashboard/_open-projects"))
                .withChild(dashboardHxSection("Agents", "agents-section",
                    "/dashboard/_agents")))
            .withChild(new Div().withClass("dashboard-side")
                .withChild(dashboardHxSideSection("Inbox", "side-inbox",
                    "/inbox", "/dashboard/_side-inbox"))
                .withChild(dashboardHxSideSection("Recent Outputs", "side-outputs",
                    "/outputs", "/dashboard/_side-outputs"))
                .withChild(dashboardHxSideSection("Recent Events", "side-events",
                    "/agents", "/dashboard/_side-events")));
    }

    private Component dashboardHxSection(String title, String sectionId, String hxEndpoint) {
        return new Div().withClass("dashboard-section")
            .withChild(Header.H2(title))
            .withChild(new Div().withId(sectionId)
                .hxGet(hxEndpoint)
                .hxTrigger("load, every 30s")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));
    }

    private Component dashboardHxSideSection(String title, String id, String href, String hxEndpoint) {
        Div section = new Div().withClass("dashboard-side-section");
        Div header = new Div().withClass("dashboard-side-header");
        header.withChild(new HtmlTag("h3").withInnerText(title));
        if (href != null) {
            header.withChild(new HtmlTag("a").withAttribute("href", href)
                .withClass("dashboard-side-link").withInnerText("View all"));
        }
        section.withChild(header);
        if (hxEndpoint != null) {
            section.withChild(new Div().withId(id)
                .hxGet(hxEndpoint)
                .hxTrigger("load, every 30s")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));
        } else {
            section.withChild(new Div().withId(id).withClass("dashboard-side-list")
                .withChild(new Div().withClass("dashboard-empty").withInnerText("No recent events")));
        }
        return section;
    }

    private Component loadingPlaceholder() {
        return new Div().withClass("dashboard-empty").withInnerText("Loading...");
    }

    // ════════════════════════════════════════════════════════════════
    //  Dashboard HTMX partial endpoints
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/dashboard/_stats")
    @ResponseBody
    public String dashboardStats() {
        List<JobDefinition> jobs = jobService.listDefinitions();
        List<AgentProfile> agents = agentProfileService.list();
        long messages = inboxService.userInbox().size();

        Map<String, Long> jobsByStatus = jobs.stream()
            .collect(Collectors.groupingBy(
                j -> j.status() == null ? "DRAFT" : j.status(), Collectors.counting()));

        long runningJobs = jobsByStatus.getOrDefault("RUNNING", 0L);
        long pendingJobs = jobsByStatus.getOrDefault("QUEUED", 0L) +
                           jobsByStatus.getOrDefault("DRAFT", 0L);
        long failedItems = jobsByStatus.getOrDefault("FAILED", 0L);
        long activeAgents = agents.stream()
            .filter(a -> a.status() != null && !"DISABLED".equals(a.status().name()))
            .count();

        return new Div().withClass("dashboard-status-strip")
            .withChild(dashboardStat("stat-running", "Running", String.valueOf(runningJobs)))
            .withChild(dashboardStat("stat-pending", "Pending", String.valueOf(pendingJobs)))
            .withChild(dashboardStat("stat-messages", "Messages", String.valueOf(messages)))
            .withChild(dashboardStat("stat-failed", "Failed", String.valueOf(failedItems)))
            .withChild(dashboardStat("stat-agents", "Active Agents", String.valueOf(activeAgents)))
            .render();
    }

    @GetMapping("/dashboard/_active-work")
    @ResponseBody
    public String dashboardActiveWork() {
        List<JobDefinition> jobs = jobService.listDefinitions();
        List<JobDefinition> active = jobs.stream()
            .filter(j -> j.status() != null
                && !"COMPLETED".equals(j.status())
                && !"CANCELLED".equals(j.status()))
            .toList();

        if (active.isEmpty()) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("No active work").render();
        }

        Table table = Table.create()
            .withHeaders("Type", "Title", "Owner", "Status", "Project")
            .withClass("dashboard-table");
        table.withId("active-work-table");

        for (var w : active) {
            table.addRow(
                new HtmlTag("span").withClass("orch-chip").withInnerText("JOB"),
                new HtmlTag("a").withAttribute("href", "/jobs")
                    .withInnerText(w.title() != null ? w.title() : w.id()),
                new HtmlTag("span").withInnerText(
                    w.ownerAgentId() != null ? w.ownerAgentId() : "—"),
                statusBadgeHtml(w.status()),
                new HtmlTag("span").withInnerText(
                    w.projectId() != null ? w.projectId() : "—")
            );
        }
        return table.render();
    }

    @GetMapping("/dashboard/_open-projects")
    @ResponseBody
    public String dashboardOpenProjects() {
        List<Project> projects = projectService.listProjects();

        if (projects.isEmpty()) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("No open projects").render();
        }

        Div grid = new Div().withClass("dashboard-card-grid");
        for (var p : projects) {
            grid.withChild(new Div().withClass("orch-card")
                .withChild(new HtmlTag("h3")
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "/projects/" + escapeAttr(p.id()))
                        .withInnerText(p.name() != null ? p.name() : "Untitled")))
                .withChild(new Div().withClass("orch-meta")
                    .withChild(new HtmlTag("span").withInnerText(
                        "Owner: " + (p.ownerAgentId() != null ? p.ownerAgentId() : "—")))
                    .withChild(new HtmlTag("span").withInnerText(
                        "Updated: " + (p.updatedAt() != null ? formatSince(p.updatedAt()) : "—")))));
        }
        return grid.render();
    }

    @GetMapping("/dashboard/_agents")
    @ResponseBody
    public String dashboardAgents() {
        List<AgentProfile> agents = agentProfileService.list();

        if (agents.isEmpty()) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("No agents").render();
        }

        Table table = Table.create()
            .withHeaders("Name", "Status", "Model", "Queue", "Inbox")
            .withClass("dashboard-table");
        table.withId("agents-table");

        for (var a : agents) {
            table.addRow(
                new HtmlTag("a").withAttribute("href", "/agents/" + escapeAttr(a.id()))
                    .withInnerText(a.name() != null ? a.name() : a.id()),
                statusBadgeHtml(a.status() != null ? a.status().name() : "UNKNOWN"),
                new HtmlTag("span").withInnerText(
                    a.defaultModel() != null ? a.defaultModel() : "unset"),
                new HtmlTag("span").withClass("dashboard-muted").withInnerText("—"),
                new HtmlTag("span").withClass("dashboard-muted").withInnerText("—")
            );
        }
        return table.render();
    }

    @GetMapping("/dashboard/_side-inbox")
    @ResponseBody
    public String dashboardSideInbox() {
        long messages = inboxService.userInbox().size();

        return new Div().withClass("dashboard-side-stat")
            .withChild(new HtmlTag("span").withClass("dashboard-side-value")
                .withInnerText(String.valueOf(messages)))
            .withChild(new HtmlTag("span").withClass("dashboard-side-label")
                .withInnerText(messages == 1 ? "message" : "messages"))
            .render();
    }

    @GetMapping("/dashboard/_side-outputs")
    @ResponseBody
    public String dashboardSideOutputs() {
        List<RunOutputArtifact> outputs = outputArtifactService.query(null, null, null, 5);

        if (outputs.isEmpty()) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("No recent outputs").render();
        }

        Div list = new Div();
        for (var o : outputs) {
            list.withChild(new Div().withClass("dashboard-side-item")
                .withChild(new HtmlTag("span").withClass("dashboard-side-item-name")
                    .withInnerText(o.outputName() != null ? o.outputName() : o.artifactType() != null ? o.artifactType() : "output"))
                .withChild(new HtmlTag("span").withClass("dashboard-side-item-meta")
                    .withInnerText(o.planId() != null ? o.planId() : o.runId() != null ? o.runId() : "")));
        }
        return list.render();
    }

    @GetMapping("/dashboard/_side-events")
    @ResponseBody
    public String dashboardSideEvents() {
        record EventRow(Instant at, String label, String meta) {}
        List<EventRow> rows = new ArrayList<>();

        for (JobDefinition job : jobService.listDefinitions()) {
            rows.add(new EventRow(job.updatedAt(), "Job " + nn(job.status()),
                (job.title() != null ? job.title() : job.id())));
        }
        for (AgentProfile agent : agentProfileService.list()) {
            rows.add(new EventRow(agent.updatedAt(), "Agent " + nn(agent.status() != null ? agent.status().name() : ""),
                agent.name() != null ? agent.name() : agent.id()));
        }
        for (InboxMessage message : inboxService.userInbox()) {
            String state = message.respondedAt() != null ? "responded" : "pending";
            rows.add(new EventRow(message.createdAt(), "Inbox " + state,
                message.fromId() != null ? message.fromId() : "system"));
        }

        rows = rows.stream()
            .sorted((a, b) -> {
                Instant ai = a.at() != null ? a.at() : Instant.EPOCH;
                Instant bi = b.at() != null ? b.at() : Instant.EPOCH;
                return bi.compareTo(ai);
            })
            .limit(8)
            .toList();

        if (rows.isEmpty()) {
            return new Div().withClass("dashboard-empty").withInnerText("No recent events").render();
        }

        Div list = new Div().withClass("dashboard-side-list");
        for (EventRow row : rows) {
            list.withChild(new Div().withClass("dashboard-side-item")
                .withChild(new HtmlTag("span").withClass("dashboard-side-item-name")
                    .withInnerText(row.label()))
                .withChild(new HtmlTag("span").withClass("dashboard-side-item-meta")
                    .withInnerText((row.meta() != null ? row.meta() + " · " : "")
                        + (row.at() != null ? formatSince(row.at()) : "unknown"))));
        }
        return list.render();
    }

    // ════════════════════════════════════════════════════════════════
    //  Plans / Task Editor (HTMX-first)
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/plans")
    @ResponseBody
    public String plans() {
        Component body = new Div()
            .withId("plans-page")
            .withAttribute("data-orchestration-page", "plans")
            .withChild(Header.H1("Plans"))
            .withChild(new Paragraph("Plan and task template definitions. Edit structured inputs, outputs, steps, and submit to agents for execution."))
            .withChild(new Div().withClass("browser-layout browser-layout-wide")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Button.create("New Plan")
                            .withClass("orch-primary")
                            .withAttribute("hx-post", "/plans/_editor/_draft")
                            .hxTarget("#plan-editor-container")
                            .hxSwap("innerHTML"))
                        .withChild(Button.create("New Plan Chat")
                            .withAttribute("type", "button")
                            .withAttribute("onclick", "window.location.href='/chat?startPlanning=true'")
                            .withClass("orch-primary")
                            .withInnerText("New Plan Chat")))
                    .withChild(TextInput.search("planFilter")
                        .withId("plan-filter")
                        .withPlaceholder("Filter plans")
                        .withAttribute("hx-get", "/plans/_list")
                        .withAttribute("hx-trigger", "keyup changed delay:300ms")
                        .withAttribute("hx-target", "#plan-list")
                        .withAttribute("hx-swap", "innerHTML")
                        .withAttribute("hx-include", "#plan-filter"))
                    .withChild(new Div().withId("plan-list")
                        .withClass("entity-list")
                        .hxGet("/plans/_list")
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("plan-editor-container")
                        .withChild(planEditorEmptyState()))))
            .withChild(moduleScript(PLANS_JS));
        return renderPage(body, "/plans");
    }

    private Component planEditorEmptyState() {
        return new Div().withClass("orch-panel")
            .withChild(new Div().withClass("dashboard-empty")
                .withInnerText("Select a plan from the list or create a new one."));
    }

    // ════════════════════════════════════════════════════════════════
    //  Plan HTMX partial endpoints
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/plans/_list")
    @ResponseBody
    public String planListFragment(@RequestParam(value = "planFilter", required = false) String filter) {
        List<PlanDefinition> plans = planService.listTasks();
        if (filter != null && !filter.isBlank()) {
            String f = filter.toLowerCase();
            plans = plans.stream()
                .filter(p -> (p.title() != null && p.title().toLowerCase().contains(f)))
                .toList();
        }
        if (plans.isEmpty()) {
            return new Div().withClass("tool-item").withInnerText("No plans.").render();
        }
        Div list = new Div().withClass("plan-card-list");
        for (var plan : plans) {
            Div row = new Div().withClass("plan-list-card");
            HtmlTag openButton = new HtmlTag("button")
                .withAttribute("type", "button")
                .withClass("tool-item plan-list-open")
                .withAttribute("hx-get", "/plans/_editor/" + escapeAttr(plan.id()))
                .withAttribute("hx-target", "#plan-editor-container")
                .withAttribute("hx-swap", "innerHTML")
                .withChild(new HtmlTag("strong").withInnerText(plan.title() != null ? plan.title() : "Untitled"))
                .withChild(planStatusBadge(plan.status()));
            row.withChild(openButton);
            row.withChild(new HtmlTag("button")
                .withAttribute("type", "button")
                .withClass("plan-list-delete")
                .withAttribute("title", "Delete plan")
                .withAttribute("aria-label", "Delete plan " + escapeAttr(plan.title() != null ? plan.title() : "Untitled"))
                .withAttribute("hx-delete", "/plans/_editor/" + escapeAttr(plan.id()))
                .withAttribute("hx-target", "#plan-list")
                .withAttribute("hx-swap", "innerHTML")
                .withAttribute("hx-include", "#plan-filter")
                .withAttribute("hx-confirm", "Delete this plan?")
                .withInnerText("🗑"));
            list.withChild(row);
        }
        return list.render();
    }

    @DeleteMapping("/plans/_editor/{planId}")
    @ResponseBody
    public String deletePlanEditor(
        @PathVariable String planId,
        @RequestParam(value = "planFilter", required = false) String filter
    ) {
        try {
            planService.deleteTask(planId);
            String listHtml = planListFragment(filter);
            String editorHtml = planEditorEmptyState().render();
            return listHtml + "<div id=\"plan-editor-container\" hx-swap-oob=\"innerHTML\">" + editorHtml + "</div>";
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private Component planStatusBadge(PlanStatus status) {
        if (status == null) {
            return new HtmlTag("span").withClass("plan-status-badge").withInnerText("unknown");
        }
        String text = status.name().toLowerCase(Locale.ROOT);
        String colorClass = switch (status) {
            case DRAFT -> "is-draft";
            case APPROVED -> "is-approved";
            default -> "is-neutral";
        };
        return new HtmlTag("span")
            .withClass("plan-status-badge " + colorClass)
            .withInnerText(text);
    }

    @GetMapping("/plans/_editor/_new")
    @ResponseBody
    public String newPlanEditor() {
        return planEditorFragment(null).render();
    }

    @PostMapping("/plans/_editor/_draft")
    @ResponseBody
    public String createDraftPlanEditor() {
        PlanDefinition created = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            "Untitled Plan",
            null,
            null,
            null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(),
            WorkTypeProfile.CODING_CENTRIC.name(),
            null,
            null,
            null,
            null,
            List.of(),
            0,
            0,
            null,
            null,
            null,
            null
        ));
        return planEditorFragment(created).render();
    }

    @GetMapping("/plans/_editor/{planId}")
    @ResponseBody
    public String planEditor(@PathVariable String planId) {
        try {
            PlanDefinition plan = planService.getTask(planId);
            return planEditorFragment(plan).render();
        } catch (IllegalStateException e) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("dashboard-empty")
                    .withInnerText("Plan not found: " + escapeAttr(planId)))
                .render();
        }
    }

    @PostMapping("/plans/_editor")
    @ResponseBody
    public String createPlanEditor(@RequestParam Map<String, String> params) {
        String title = params.getOrDefault("title", "").trim();
        if (title.isBlank()) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("orch-status")
                    .withInnerText("Title is required."))
                .render();
        }
        PlanDefinition created = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            title,
            nn(params.get("summary")),
            nn(params.get("goal")),
            nn(params.get("notes")),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(),
            resolveWorktype(params),
            nn(params.get("planningModel")),
            nn(params.get("executionModel")),
            nn(params.get("settingsOverrideJson")),
            nn(params.get("planningTask")),
            List.of(),
            0,
            0,
            nn(params.get("finalMessage")),
            null,
            null,
            null
        ));
        return planEditorFragment(created).render();
    }

    @PutMapping("/plans/_editor/{planId}")
    @ResponseBody
    public String updatePlanEditor(@PathVariable String planId, @RequestParam Map<String, String> params) {
        try {
            PlanDefinition current = planService.getTask(planId);
            PlanDefinition updated = new PlanDefinition(
                planId, current.kind(), current.status(),
                params.containsKey("title") ? nn(params.get("title")) : current.title(),
                params.containsKey("summary") ? nn(params.get("summary")) : current.summary(),
                params.containsKey("goal") ? nn(params.get("goal")) : current.goal(),
                params.containsKey("notes") ? nn(params.get("notes")) : current.notes(),
                current.deliverables(),
                current.inputs(),
                current.outputs(),
                current.assumptions(),
                current.steps(),
                current.validationCriteria(),
                current.executionEvidence(),
                current.validationFeedback(),
                params.containsKey("workTypeProfile") ? resolveWorktype(params) : current.promptProfile(),
                params.containsKey("planningModel") ? nn(params.get("planningModel")) : current.planningModel(),
                params.containsKey("executionModel") ? nn(params.get("executionModel")) : current.executionModel(),
                params.containsKey("settingsOverrideJson") ? nn(params.get("settingsOverrideJson")) : current.settingsOverrideJson(),
                params.containsKey("planningTask") ? nn(params.get("planningTask")) : current.planningTask(),
                current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(),
                params.containsKey("finalMessage") ? nn(params.get("finalMessage")) : current.finalMessage(),
                current.conversationId(),
                current.createdAt(), current.updatedAt()
            );
            planService.saveTask(updated);
            return planEditorFragment(planService.getTask(planId)).render();
        } catch (IllegalStateException e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        } catch (IllegalArgumentException e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @PostMapping("/plans/_editor/{planId}/finalize")
    @ResponseBody
    public String finalizePlanEditor(@PathVariable String planId) {
        try {
            planService.finalizeTask(planId);
            return planEditorFragment(planService.getTask(planId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Field add/remove (inputs) ──

    @PostMapping("/plans/_editor/{planId}/inputs")
    @ResponseBody
    public String addInputField(@PathVariable String planId) {
        return addField(planId, true);
    }

    @PutMapping("/plans/_editor/{planId}/inputs")
    @ResponseBody
    public String updateInputField(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateField(planId, params, true);
    }

    @DeleteMapping("/plans/_editor/{planId}/inputs/{index}")
    @ResponseBody
    public String removeInputField(@PathVariable String planId, @PathVariable int index) {
        return removeField(planId, index, true);
    }

    // ── Field add/remove/update (outputs) ──

    @PostMapping("/plans/_editor/{planId}/outputs")
    @ResponseBody
    public String addOutputField(@PathVariable String planId) {
        return addField(planId, false);
    }

    @PutMapping("/plans/_editor/{planId}/outputs")
    @ResponseBody
    public String updateOutputField(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateField(planId, params, false);
    }

    @DeleteMapping("/plans/_editor/{planId}/outputs/{index}")
    @ResponseBody
    public String removeOutputField(@PathVariable String planId, @PathVariable int index) {
        return removeField(planId, index, false);
    }

    private String addField(String planId, boolean isInput) {
        try {
            PlanDefinition current = planService.getTask(planId);
            List<PlanFieldDefinition> fields = new ArrayList<>(isInput ? current.inputs() : current.outputs());
            fields.add(new PlanFieldDefinition("field_" + (fields.size() + 1), PlanFieldType.STRING,
                false, null, false, null));
            PlanDefinition updated = isInput
                ? new PlanDefinition(planId, current.kind(), current.status(),
                    current.title(), current.summary(), current.goal(), current.notes(),
                    current.deliverables(), fields, current.outputs(),
                    current.assumptions(), current.steps(), current.validationCriteria(),
                    current.executionEvidence(), current.validationFeedback(),
                    current.promptProfile(), current.planningModel(), current.executionModel(),
                    current.settingsOverrideJson(),
                    current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                    current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                    current.createdAt(), current.updatedAt())
                : new PlanDefinition(planId, current.kind(), current.status(),
                    current.title(), current.summary(), current.goal(), current.notes(),
                    current.deliverables(), current.inputs(), fields,
                    current.assumptions(), current.steps(), current.validationCriteria(),
                    current.executionEvidence(), current.validationFeedback(),
                    current.promptProfile(), current.planningModel(), current.executionModel(),
                    current.settingsOverrideJson(),
                    current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                    current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                    current.createdAt(), current.updatedAt());
            planService.saveTask(updated);
            return isInput ? planInputsSection(planService.getTask(planId)).render()
                           : planOutputsSection(planService.getTask(planId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private String updateField(String planId, Map<String, String> params, boolean isInput) {
        try {
            // Find the field index from param keys like "inputsName2", "outputsType0"
            String kind = isInput ? "inputs" : "outputs";
            int index = -1;
            for (String key : params.keySet()) {
                if (key.startsWith(kind)) {
                    String suffix = key.substring(kind.length());
                    // suffix looks like "Name2", "Type0", "Required1", etc.
                    String numStr = suffix.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        index = Integer.parseInt(numStr);
                        break;
                    }
                }
            }
            if (index < 0) {
                return new Div().withClass("orch-status")
                    .withInnerText("Error: Cannot determine field index.").render();
            }

            PlanDefinition current = planService.getTask(planId);
            List<PlanFieldDefinition> fields = new ArrayList<>(isInput ? current.inputs() : current.outputs());
            if (index >= fields.size()) {
                return new Div().withClass("orch-status")
                    .withInnerText("Error: Field index " + index + " out of range.").render();
            }

            PlanFieldDefinition existing = fields.get(index);
            String name = params.getOrDefault(kind + "Name" + index, existing.name());
            String typeStr = params.getOrDefault(kind + "Type" + index, existing.type().wireName());
            boolean required = params.containsKey(kind + "Required" + index);
            boolean array = params.containsKey(kind + "Array" + index);
            String desc = params.getOrDefault(kind + "Desc" + index, existing.description());
            String schema = params.getOrDefault(kind + "Schema" + index, existing.schema());

            PlanFieldType type;
            try {
                type = PlanFieldType.fromWireName(typeStr);
            } catch (IllegalArgumentException e) {
                type = existing.type();
            }

            if (name == null || name.isBlank()) {
                name = "field_" + (index + 1);
            }

            fields.set(index, new PlanFieldDefinition(
                name.trim(), type, array, nn(desc), required,
                nn(schema)));

            PlanDefinition updated = isInput
                ? new PlanDefinition(planId, current.kind(), current.status(),
                    current.title(), current.summary(), current.goal(), current.notes(),
                    current.deliverables(), fields, current.outputs(),
                    current.assumptions(), current.steps(), current.validationCriteria(),
                    current.executionEvidence(), current.validationFeedback(),
                    current.promptProfile(), current.planningModel(), current.executionModel(),
                    current.settingsOverrideJson(),
                    current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                    current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                    current.createdAt(), current.updatedAt())
                : new PlanDefinition(planId, current.kind(), current.status(),
                    current.title(), current.summary(), current.goal(), current.notes(),
                    current.deliverables(), current.inputs(), fields,
                    current.assumptions(), current.steps(), current.validationCriteria(),
                    current.executionEvidence(), current.validationFeedback(),
                    current.promptProfile(), current.planningModel(), current.executionModel(),
                    current.settingsOverrideJson(),
                    current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                    current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                    current.createdAt(), current.updatedAt());
            planService.saveTask(updated);
            PlanDefinition reloaded = planService.getTask(planId);
            return isInput ? planInputsSection(reloaded).render()
                           : planOutputsSection(reloaded).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private String removeField(String planId, int index, boolean isInput) {
        try {
            PlanDefinition current = planService.getTask(planId);
            List<PlanFieldDefinition> fields = new ArrayList<>(isInput ? current.inputs() : current.outputs());
            if (index >= 0 && index < fields.size()) {
                fields.remove(index);
            }
            PlanDefinition updated = isInput
                ? new PlanDefinition(planId, current.kind(), current.status(),
                    current.title(), current.summary(), current.goal(), current.notes(),
                    current.deliverables(), fields, current.outputs(),
                    current.assumptions(), current.steps(), current.validationCriteria(),
                    current.executionEvidence(), current.validationFeedback(),
                    current.promptProfile(), current.planningModel(), current.executionModel(),
                    current.settingsOverrideJson(),
                    current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                    current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                    current.createdAt(), current.updatedAt())
                : new PlanDefinition(planId, current.kind(), current.status(),
                    current.title(), current.summary(), current.goal(), current.notes(),
                    current.deliverables(), current.inputs(), fields,
                    current.assumptions(), current.steps(), current.validationCriteria(),
                    current.executionEvidence(), current.validationFeedback(),
                    current.promptProfile(), current.planningModel(), current.executionModel(),
                    current.settingsOverrideJson(),
                    current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                    current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                    current.createdAt(), current.updatedAt());
            planService.saveTask(updated);
            return isInput ? planInputsSection(planService.getTask(planId)).render()
                           : planOutputsSection(planService.getTask(planId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── List item add/remove (deliverables, steps, validation, assumptions) ──

    @PostMapping("/plans/_editor/{planId}/deliverables")
    @ResponseBody
    public String addDeliverable(@PathVariable String planId) {
        return addListItem(planId, "deliverables");
    }

    @DeleteMapping("/plans/_editor/{planId}/deliverables/{index}")
    @ResponseBody
    public String removeDeliverable(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "deliverables");
    }

    @PostMapping("/plans/_editor/{planId}/steps")
    @ResponseBody
    public String addStep(@PathVariable String planId) {
        return addListItem(planId, "steps");
    }

    @DeleteMapping("/plans/_editor/{planId}/steps/{index}")
    @ResponseBody
    public String removeStep(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "steps");
    }

    @PostMapping("/plans/_editor/{planId}/steps/{index}/move-up")
    @ResponseBody
    public String moveStepUp(@PathVariable String planId, @PathVariable int index) {
        return moveStep(planId, index, -1);
    }

    @PostMapping("/plans/_editor/{planId}/steps/{index}/move-down")
    @ResponseBody
    public String moveStepDown(@PathVariable String planId, @PathVariable int index) {
        return moveStep(planId, index, 1);
    }

    @PostMapping("/plans/_editor/{planId}/validation")
    @ResponseBody
    public String addValidationCriterion(@PathVariable String planId) {
        return addListItem(planId, "validation");
    }

    @DeleteMapping("/plans/_editor/{planId}/validation/{index}")
    @ResponseBody
    public String removeValidationCriterion(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "validation");
    }

    @PostMapping("/plans/_editor/{planId}/assumptions")
    @ResponseBody
    public String addAssumption(@PathVariable String planId) {
        return addListItem(planId, "assumptions");
    }

    @PutMapping("/plans/_editor/{planId}/deliverables")
    @ResponseBody
    public String updateDeliverable(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateListItem(planId, "deliverables", params);
    }

    @PutMapping("/plans/_editor/{planId}/steps")
    @ResponseBody
    public String updateStep(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateListItem(planId, "steps", params);
    }

    @PutMapping("/plans/_editor/{planId}/validation")
    @ResponseBody
    public String updateValidationCriterion(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateListItem(planId, "validation", params);
    }

    @PutMapping("/plans/_editor/{planId}/assumptions")
    @ResponseBody
    public String updateAssumption(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateListItem(planId, "assumptions", params);
    }

    @DeleteMapping("/plans/_editor/{planId}/assumptions/{index}")
    @ResponseBody
    public String removeAssumption(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "assumptions");
    }

    @PostMapping("/plans/_editor/{planId}/evidence")
    @ResponseBody
    public String addExecutionEvidence(@PathVariable String planId) {
        return addListItem(planId, "evidence");
    }

    @PutMapping("/plans/_editor/{planId}/evidence")
    @ResponseBody
    public String updateExecutionEvidence(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateListItem(planId, "evidence", params);
    }

    @DeleteMapping("/plans/_editor/{planId}/evidence/{index}")
    @ResponseBody
    public String removeExecutionEvidence(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "evidence");
    }

    @PostMapping("/plans/_editor/{planId}/feedback")
    @ResponseBody
    public String addValidationFeedback(@PathVariable String planId) {
        return addListItem(planId, "feedback");
    }

    @PutMapping("/plans/_editor/{planId}/feedback")
    @ResponseBody
    public String updateValidationFeedback(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateListItem(planId, "feedback", params);
    }

    @DeleteMapping("/plans/_editor/{planId}/feedback/{index}")
    @ResponseBody
    public String removeValidationFeedback(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "feedback");
    }

    @PostMapping("/plans/_editor/{planId}/questions")
    @ResponseBody
    public String addPendingQuestion(@PathVariable String planId) {
        return addListItem(planId, "questions");
    }

    @PutMapping("/plans/_editor/{planId}/questions")
    @ResponseBody
    public String updatePendingQuestion(@PathVariable String planId, @RequestParam Map<String, String> params) {
        return updateListItem(planId, "questions", params);
    }

    @DeleteMapping("/plans/_editor/{planId}/questions/{index}")
    @ResponseBody
    public String removePendingQuestion(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "questions");
    }

    private String addListItem(String planId, String section) {
        try {
            PlanDefinition current = planService.getTask(planId);
            PlanDefinition updated = switch (section) {
                case "deliverables" -> {
                    List<String> items = new ArrayList<>(current.deliverables());
                    items.add("New deliverable");
                    yield new PlanDefinition(planId, current.kind(), current.status(),
                        current.title(), current.summary(), current.goal(), current.notes(),
                        items, current.inputs(), current.outputs(),
                        current.assumptions(), current.steps(), current.validationCriteria(),
                        current.executionEvidence(), current.validationFeedback(),
                        current.promptProfile(), current.planningModel(), current.executionModel(),
                        current.settingsOverrideJson(),
                        current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                        current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                        current.createdAt(), current.updatedAt());
                }
                case "steps" -> {
                    List<PlanStep> items = new ArrayList<>(current.steps());
                    items.add(new PlanStep(items.size() + 1, "New step"));
                    yield new PlanDefinition(planId, current.kind(), current.status(),
                        current.title(), current.summary(), current.goal(), current.notes(),
                        current.deliverables(), current.inputs(), current.outputs(),
                        current.assumptions(), items, current.validationCriteria(),
                        current.executionEvidence(), current.validationFeedback(),
                        current.promptProfile(), current.planningModel(), current.executionModel(),
                        current.settingsOverrideJson(),
                        current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                        current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                        current.createdAt(), current.updatedAt());
                }
                case "validation" -> {
                    List<String> items = new ArrayList<>(current.validationCriteria());
                    items.add("New criterion");
                    yield new PlanDefinition(planId, current.kind(), current.status(),
                        current.title(), current.summary(), current.goal(), current.notes(),
                        current.deliverables(), current.inputs(), current.outputs(),
                        current.assumptions(), current.steps(), items,
                        current.executionEvidence(), current.validationFeedback(),
                        current.promptProfile(), current.planningModel(), current.executionModel(),
                        current.settingsOverrideJson(),
                        current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                        current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                        current.createdAt(), current.updatedAt());
                }
                case "assumptions" -> {
                    List<String> items = new ArrayList<>(current.assumptions());
                    items.add("New assumption");
                    yield withStringList(current, planId, section, items);
                }
                case "evidence", "feedback", "questions" -> {
                    List<String> items = new ArrayList<>(stringList(current, section));
                    items.add(switch (section) {
                        case "evidence" -> "New evidence";
                        case "feedback" -> "New feedback";
                        default -> "New question";
                    });
                    yield withStringList(current, planId, section, items);
                }
                default -> throw new IllegalArgumentException("Unknown section: " + section);
            };
            planService.saveTask(updated);
            return listSectionHtml(planService.getTask(planId), section).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private String removeListItem(String planId, int index, String section) {
        try {
            PlanDefinition current = planService.getTask(planId);
            PlanDefinition updated = switch (section) {
                case "deliverables" -> {
                    List<String> items = new ArrayList<>(current.deliverables());
                    if (index >= 0 && index < items.size()) items.remove(index);
                    yield new PlanDefinition(planId, current.kind(), current.status(),
                        current.title(), current.summary(), current.goal(), current.notes(),
                        items, current.inputs(), current.outputs(),
                        current.assumptions(), current.steps(), current.validationCriteria(),
                        current.executionEvidence(), current.validationFeedback(),
                        current.promptProfile(), current.planningModel(), current.executionModel(),
                        current.settingsOverrideJson(),
                        current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                        current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                        current.createdAt(), current.updatedAt());
                }
                case "steps" -> {
                    List<PlanStep> items = new ArrayList<>(current.steps());
                    if (index >= 0 && index < items.size()) items.remove(index);
                    // Reorder
                    List<PlanStep> reordered = new ArrayList<>();
                    for (int i = 0; i < items.size(); i++) {
                        reordered.add(new PlanStep(i + 1, items.get(i).text()));
                    }
                    yield new PlanDefinition(planId, current.kind(), current.status(),
                        current.title(), current.summary(), current.goal(), current.notes(),
                        current.deliverables(), current.inputs(), current.outputs(),
                        current.assumptions(), reordered, current.validationCriteria(),
                        current.executionEvidence(), current.validationFeedback(),
                        current.promptProfile(), current.planningModel(), current.executionModel(),
                        current.settingsOverrideJson(),
                        current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                        current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                        current.createdAt(), current.updatedAt());
                }
                case "validation" -> {
                    List<String> items = new ArrayList<>(current.validationCriteria());
                    if (index >= 0 && index < items.size()) items.remove(index);
                    yield new PlanDefinition(planId, current.kind(), current.status(),
                        current.title(), current.summary(), current.goal(), current.notes(),
                        current.deliverables(), current.inputs(), current.outputs(),
                        current.assumptions(), current.steps(), items,
                        current.executionEvidence(), current.validationFeedback(),
                        current.promptProfile(), current.planningModel(), current.executionModel(),
                        current.settingsOverrideJson(),
                        current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                        current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                        current.createdAt(), current.updatedAt());
                }
                case "assumptions" -> {
                    List<String> items = new ArrayList<>(current.assumptions());
                    if (index >= 0 && index < items.size()) items.remove(index);
                    yield withStringList(current, planId, section, items);
                }
                case "evidence", "feedback", "questions" -> {
                    List<String> items = new ArrayList<>(stringList(current, section));
                    if (index >= 0 && index < items.size()) items.remove(index);
                    yield withStringList(current, planId, section, items);
                }
                default -> throw new IllegalArgumentException("Unknown section: " + section);
            };
            planService.saveTask(updated);
            return listSectionHtml(planService.getTask(planId), section).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private String updateListItem(String planId, String section, Map<String, String> params) {
        try {
            int index = parseListIndex(section, params);
            if (index < 0) {
                return new Div().withClass("orch-status")
                    .withInnerText("Error: Cannot determine list item index.").render();
            }
            String value = nn(params.get(section + "Value" + index));
            PlanDefinition current = planService.getTask(planId);
            PlanDefinition updated = switch (section) {
                case "deliverables" -> withDeliverableValue(current, planId, index, value);
                case "validation" -> withValidationValue(current, planId, index, value);
                case "assumptions" -> withAssumptionValue(current, planId, index, value);
                case "steps" -> withStepValue(current, planId, index, value);
                case "evidence", "feedback", "questions" -> {
                    List<String> items = new ArrayList<>(stringList(current, section));
                    if (index >= 0 && index < items.size()) {
                        items.set(index, value);
                    }
                    yield withStringList(current, planId, section, items);
                }
                default -> throw new IllegalArgumentException("Unknown section: " + section);
            };
            planService.saveTask(updated);
            return listSectionHtml(planService.getTask(planId), section).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private int parseListIndex(String section, Map<String, String> params) {
        String prefix = section + "Value";
        for (String key : params.keySet()) {
            if (key.startsWith(prefix)) {
                String number = key.substring(prefix.length()).replaceAll("[^0-9]", "");
                if (!number.isBlank()) {
                    return Integer.parseInt(number);
                }
            }
        }
        return -1;
    }

    private List<String> stringList(PlanDefinition current, String section) {
        return switch (section) {
            case "deliverables" -> current.deliverables();
            case "validation" -> current.validationCriteria();
            case "assumptions" -> current.assumptions();
            case "evidence" -> current.executionEvidence();
            case "feedback" -> current.validationFeedback();
            case "questions" -> current.pendingQuestions();
            default -> throw new IllegalArgumentException("Unknown string-list section: " + section);
        };
    }

    private PlanDefinition withStringList(PlanDefinition current, String planId, String section, List<String> items) {
        return switch (section) {
            case "deliverables" -> new PlanDefinition(planId, current.kind(), current.status(),
                current.title(), current.summary(), current.goal(), current.notes(),
                items, current.inputs(), current.outputs(),
                current.assumptions(), current.steps(), current.validationCriteria(),
                current.executionEvidence(), current.validationFeedback(),
                current.promptProfile(), current.planningModel(), current.executionModel(),
                current.settingsOverrideJson(),
                current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                current.createdAt(), current.updatedAt());
            case "validation" -> new PlanDefinition(planId, current.kind(), current.status(),
                current.title(), current.summary(), current.goal(), current.notes(),
                current.deliverables(), current.inputs(), current.outputs(),
                current.assumptions(), current.steps(), items,
                current.executionEvidence(), current.validationFeedback(),
                current.promptProfile(), current.planningModel(), current.executionModel(),
                current.settingsOverrideJson(),
                current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                current.createdAt(), current.updatedAt());
            case "assumptions" -> new PlanDefinition(planId, current.kind(), current.status(),
                current.title(), current.summary(), current.goal(), current.notes(),
                current.deliverables(), current.inputs(), current.outputs(),
                items, current.steps(), current.validationCriteria(),
                current.executionEvidence(), current.validationFeedback(),
                current.promptProfile(), current.planningModel(), current.executionModel(),
                current.settingsOverrideJson(),
                current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                current.createdAt(), current.updatedAt());
            case "evidence" -> new PlanDefinition(planId, current.kind(), current.status(),
                current.title(), current.summary(), current.goal(), current.notes(),
                current.deliverables(), current.inputs(), current.outputs(),
                current.assumptions(), current.steps(), current.validationCriteria(),
                items, current.validationFeedback(),
                current.promptProfile(), current.planningModel(), current.executionModel(),
                current.settingsOverrideJson(),
                current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                current.createdAt(), current.updatedAt());
            case "feedback" -> new PlanDefinition(planId, current.kind(), current.status(),
                current.title(), current.summary(), current.goal(), current.notes(),
                current.deliverables(), current.inputs(), current.outputs(),
                current.assumptions(), current.steps(), current.validationCriteria(),
                current.executionEvidence(), items,
                current.promptProfile(), current.planningModel(), current.executionModel(),
                current.settingsOverrideJson(),
                current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                current.createdAt(), current.updatedAt());
            case "questions" -> new PlanDefinition(planId, current.kind(), current.status(),
                current.title(), current.summary(), current.goal(), current.notes(),
                current.deliverables(), current.inputs(), current.outputs(),
                current.assumptions(), current.steps(), current.validationCriteria(),
                current.executionEvidence(), current.validationFeedback(),
                current.promptProfile(), current.planningModel(), current.executionModel(),
                current.settingsOverrideJson(),
                current.planningTask(), items, Math.min(current.pendingQuestionIndex(), Math.max(0, items.size() - 1)),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                current.createdAt(), current.updatedAt());
            default -> throw new IllegalArgumentException("Unknown section: " + section);
        };
    }

    private PlanDefinition withDeliverableValue(PlanDefinition current, String planId, int index, String value) {
        List<String> items = new ArrayList<>(current.deliverables());
        if (index < 0 || index >= items.size()) return current;
        items.set(index, value);
        return new PlanDefinition(planId, current.kind(), current.status(),
            current.title(), current.summary(), current.goal(), current.notes(),
            items, current.inputs(), current.outputs(),
            current.assumptions(), current.steps(), current.validationCriteria(),
            current.executionEvidence(), current.validationFeedback(),
            current.promptProfile(), current.planningModel(), current.executionModel(),
            current.settingsOverrideJson(),
            current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
            current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
            current.createdAt(), current.updatedAt());
    }

    private PlanDefinition withValidationValue(PlanDefinition current, String planId, int index, String value) {
        List<String> items = new ArrayList<>(current.validationCriteria());
        if (index < 0 || index >= items.size()) return current;
        items.set(index, value);
        return new PlanDefinition(planId, current.kind(), current.status(),
            current.title(), current.summary(), current.goal(), current.notes(),
            current.deliverables(), current.inputs(), current.outputs(),
            current.assumptions(), current.steps(), items,
            current.executionEvidence(), current.validationFeedback(),
            current.promptProfile(), current.planningModel(), current.executionModel(),
            current.settingsOverrideJson(),
            current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
            current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
            current.createdAt(), current.updatedAt());
    }

    private PlanDefinition withAssumptionValue(PlanDefinition current, String planId, int index, String value) {
        List<String> items = new ArrayList<>(current.assumptions());
        if (index < 0 || index >= items.size()) return current;
        items.set(index, value);
        return new PlanDefinition(planId, current.kind(), current.status(),
            current.title(), current.summary(), current.goal(), current.notes(),
            current.deliverables(), current.inputs(), current.outputs(),
            items, current.steps(), current.validationCriteria(),
            current.executionEvidence(), current.validationFeedback(),
            current.promptProfile(), current.planningModel(), current.executionModel(),
            current.settingsOverrideJson(),
            current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
            current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
            current.createdAt(), current.updatedAt());
    }

    private PlanDefinition withStepValue(PlanDefinition current, String planId, int index, String value) {
        List<PlanStep> items = new ArrayList<>(current.steps());
        if (index < 0 || index >= items.size()) return current;
        items.set(index, new PlanStep(items.get(index).order(), value));
        return new PlanDefinition(planId, current.kind(), current.status(),
            current.title(), current.summary(), current.goal(), current.notes(),
            current.deliverables(), current.inputs(), current.outputs(),
            current.assumptions(), items, current.validationCriteria(),
            current.executionEvidence(), current.validationFeedback(),
            current.promptProfile(), current.planningModel(), current.executionModel(),
            current.settingsOverrideJson(),
            current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
            current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
            current.createdAt(), current.updatedAt());
    }

    private String moveStep(String planId, int index, int direction) {
        try {
            PlanDefinition current = planService.getTask(planId);
            List<PlanStep> items = new ArrayList<>(current.steps());
            int target = index + direction;
            if (index < 0 || index >= items.size() || target < 0 || target >= items.size()) {
                return planStepsSection(current).render();
            }
            Collections.swap(items, index, target);
            PlanDefinition updated = new PlanDefinition(planId, current.kind(), current.status(),
                current.title(), current.summary(), current.goal(), current.notes(),
                current.deliverables(), current.inputs(), current.outputs(),
                current.assumptions(), renumberSteps(items), current.validationCriteria(),
                current.executionEvidence(), current.validationFeedback(),
                current.promptProfile(), current.planningModel(), current.executionModel(),
                current.settingsOverrideJson(),
                current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
                current.createdAt(), current.updatedAt());
            planService.saveTask(updated);
            return planStepsSection(planService.getTask(planId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private List<PlanStep> renumberSteps(List<PlanStep> steps) {
        List<PlanStep> reordered = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            reordered.add(new PlanStep(i + 1, steps.get(i).text()));
        }
        return reordered;
    }

    // ── Submit to agent ──

    @GetMapping("/plans/_submit-form/{planId}")
    @ResponseBody
    public String submitForm(@PathVariable String planId) {
        return submitToAgentPanel(planId).render();
    }

    @PostMapping("/plans/_submit/{planId}")
    @ResponseBody
    public String submitToAgent(@PathVariable String planId, @RequestParam Map<String, String> params) {
        try {
            PlanDefinition plan = planService.getTask(planId);
            String agentId = params.get("agentId");
            if (agentId == null || agentId.isBlank()) {
                agentId = agentProfileService.list().stream()
                    .filter(a -> a.status() != null && !"DISABLED".equals(a.status().name()))
                    .findFirst()
                    .map(a -> a.id())
                    .orElse(null);
            }
            if (agentId == null || agentId.isBlank()) {
                return new Div().withClass("orch-status").withInnerText("No active agents available. Create an agent first.").render();
            }
            int priority = 9;
            try { priority = Integer.parseInt(params.getOrDefault("priority", "9")); } catch (NumberFormatException ignored) {}
            Map<String, Object> inputValues = parsePlanInputValues(plan, params);
            WorkAssignment assignment = assignmentService.create(new AssignmentRequest(
                agentId, null, null, AssignmentType.TASK_RUN,
                priority,
                nn(params.get("modelOverride")),
                nn(params.get("workspaceId")),
                Map.of("taskId", planId, "inputValues", inputValues)
            ));
            return submitResultFragment(assignment, plan).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private Component submitResultFragment(WorkAssignment assignment, PlanDefinition plan) {
        return new Div().withClass("orch-panel")
            .withChild(Header.H2("Assignment Created"))
            .withChild(new Div().withClass("orch-form-stack")
                .withChild(new Paragraph("Task submitted to agent for execution."))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Assignment ID: "))
                    .withChild(new HtmlTag("code").withInnerText(assignment.id())))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Agent: "))
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "/agents/" + escapeAttr(assignment.agentId()))
                        .withInnerText(assignment.agentId())))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Plan: "))
                    .withInnerText(plan.title() != null ? plan.title() : plan.id()))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Status: "))
                    .withInnerText(assignment.status() != null ? assignment.status().name() : "QUEUED"))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Priority: "))
                    .withInnerText(String.valueOf(assignment.priority())))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "/agents/" + escapeAttr(assignment.agentId()))
                        .withInnerText("View agent status"))));
    }

    // ── Chat prompt ──

    @GetMapping("/plans/_editor/{planId}/chat-prompt-fragment")
    @ResponseBody
    public String chatPromptFragment(@PathVariable String planId) {
        try {
            PlanDefinition plan = planService.getTask(planId);
            StringBuilder sb = new StringBuilder();
            sb.append("Continue working on the following plan:\n\n");
            sb.append("Title: ").append(plan.title() != null ? plan.title() : "Untitled").append("\n");
            if (StringUtils.hasText(plan.goal())) sb.append("Goal: ").append(plan.goal()).append("\n");
            if (StringUtils.hasText(plan.summary())) sb.append("Summary: ").append(plan.summary()).append("\n");
            if (!plan.deliverables().isEmpty()) {
                sb.append("Deliverables:\n");
                for (String d : plan.deliverables()) sb.append("- ").append(d).append("\n");
            }
            if (!plan.steps().isEmpty()) {
                sb.append("Steps:\n");
                for (PlanStep s : plan.steps()) sb.append(s.order()).append(". ").append(s.text()).append("\n");
            }
            if (!plan.validationCriteria().isEmpty()) {
                sb.append("Validation Criteria:\n");
                for (String c : plan.validationCriteria()) sb.append("- ").append(c).append("\n");
            }
            sb.append("\n1. Grok the existing plan before asking questions.\n");
            sb.append("2. Continue questioning the user if the plan lacks context.\n");
            sb.append("3. Summarize and ask for guidance if the plan appears complete.\n");

            return new Div().withClass("orch-panel")
                .withChild(Header.H2("Continue in Chat"))
                .withChild(new Paragraph("Copy this prompt into chat to continue editing this plan:"))
                .withChild(new HtmlTag("pre").withClass("orch-meta")
                    .withAttribute("style", "white-space:pre-wrap;max-height:300px;overflow-y:auto;padding:0.5rem;background:var(--bg-tertiary);border-radius:4px")
                    .withInnerText(sb.toString().trim()))
                .withChild(Button.create("Copy & Open Chat")
                    .withAttribute("onclick", "navigator.clipboard.writeText(this.previousElementSibling.textContent);window.open('/chat','_blank')"))
                .render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Plan editor rendering helpers
    // ════════════════════════════════════════════════════════════════

    private Component planEditorFragment(PlanDefinition plan) {
        boolean isNew = plan == null;
        String planId = isNew ? null : plan.id();

        Div container = new Div().withClass("orch-panel plan-editor");
        container.withChild(Header.H2(isNew ? "New Plan" : "Plan Editor"));

        // Form for scalar fields - POST for new, PUT for existing
        Form form = Form.create();
        if (isNew) {
            form.withHxPost("/plans/_editor");
        } else {
            form.withHxPut("/plans/_editor/" + planId);
        }
        form.withHxTarget("#plan-editor-container");
        form.withHxSwap("innerHTML");

        // Advanced metadata (hidden kind/status)
        form.withChild(new HtmlTag("input")
            .withAttribute("type", "hidden")
            .withAttribute("name", "kind")
            .withAttribute("value", plan != null && plan.kind() != null ? plan.kind().name() : "TASK_TEMPLATE"));

        // Basic fields
        form.withChild(new Div().withClass("orch-form-stack")
            .withChild(label("Title", TextInput.create("title")
                .withId("plan-title")
                .withValue(isNew ? "" : nn(plan.title()))))
            .withChild(label("Summary", TextArea.create("summary")
                .withId("plan-summary").withRows(2)
                .withValue(isNew ? "" : nn(plan.summary()))))
            .withChild(label("Goal", TextArea.create("goal")
                .withId("plan-goal").withRows(3)
                .withValue(isNew ? "" : nn(plan.goal()))))
            .withChild(label("Notes", TextArea.create("notes")
                .withId("plan-notes").withRows(2)
                .withValue(isNew ? "" : nn(plan.notes())))));

        if (!isNew) {
            // Deliverables (ordered list editor)
            form.withChild(sectionHeader("Deliverables",
                "What the plan/task must produce. Distinct from structured outputs."));
            form.withChild(new Div().withId("plan-deliverables-section")
                .withChild(planDeliverablesSection(plan)));
            form.withChild(Button.create("Add deliverable")
                .withAttribute("hx-post", "/plans/_editor/" + planId + "/deliverables")
                .withAttribute("hx-target", "#plan-deliverables-section")
                .withAttribute("hx-swap", "innerHTML"));

            // Inputs (structured field editor)
            form.withChild(sectionHeader("Inputs",
                "Structured inputs the plan/task accepts at runtime."));
            form.withChild(new Div().withId("plan-inputs-section")
                .withChild(planInputsSection(plan)));
            form.withChild(Button.create("Add input field")
                .withAttribute("hx-post", "/plans/_editor/" + planId + "/inputs")
                .withAttribute("hx-target", "#plan-inputs-section")
                .withAttribute("hx-swap", "innerHTML"));

            // Outputs (structured field editor)
            form.withChild(sectionHeader("Outputs",
                "Structured outputs the plan/task must produce."));
            form.withChild(new Div().withId("plan-outputs-section")
                .withChild(planOutputsSection(plan)));
            form.withChild(Button.create("Add output field")
                .withAttribute("hx-post", "/plans/_editor/" + planId + "/outputs")
                .withAttribute("hx-target", "#plan-outputs-section")
                .withAttribute("hx-swap", "innerHTML"));

            // Steps (ordered list editor)
            form.withChild(sectionHeader("Steps",
                "Ordered execution steps using PlanStep(order, text)."));
            form.withChild(new Div().withId("plan-steps-section")
                .withChild(planStepsSection(plan)));
            form.withChild(Button.create("Add step")
                .withAttribute("hx-post", "/plans/_editor/" + planId + "/steps")
                .withAttribute("hx-target", "#plan-steps-section")
                .withAttribute("hx-swap", "innerHTML"));

            // Validation Criteria (ordered list editor)
            form.withChild(sectionHeader("Validation Criteria",
                "Criteria that must be met for the plan to be considered complete."));
            form.withChild(new Div().withId("plan-validation-section")
                .withChild(planValidationSection(plan)));
            form.withChild(Button.create("Add criterion")
                .withAttribute("hx-post", "/plans/_editor/" + planId + "/validation")
                .withAttribute("hx-target", "#plan-validation-section")
                .withAttribute("hx-swap", "innerHTML"));

            // Assumptions (ordered list editor)
            form.withChild(sectionHeader("Assumptions",
                "Explicit defaults or choices locked into the plan."));
            form.withChild(new Div().withId("plan-assumptions-section")
                .withChild(planAssumptionsSection(plan)));
            form.withChild(Button.create("Add assumption")
                .withAttribute("hx-post", "/plans/_editor/" + planId + "/assumptions")
                .withAttribute("hx-target", "#plan-assumptions-section")
                .withAttribute("hx-swap", "innerHTML"));
        }

        // Worktype, Planning Model, Execution Model
        String currentWorktype = plan != null ? plan.promptProfile() : null;
        Div modelGrid = new Div().withClass("orch-form-grid");
        modelGrid.withChild(label("Manager Type", worktypeSelect(currentWorktype)));
        modelGrid.withChild(label("Planning Model", modelSelectWithCurrent("planningModel", isNew ? null : plan.planningModel(), chatService.availableModels())
            .withId("plan-planning-model")));
        modelGrid.withChild(label("Execution Model", modelSelectWithCurrent("executionModel", isNew ? null : plan.executionModel(), chatService.availableModels())
            .withId("plan-execution-model")));
        form.withChild(modelGrid);

        // Advanced metadata (collapsible) - only shown for existing plans
        if (!isNew) {
            Div advanced = new Div().withId("plan-advanced").withClass("field-group");
            advanced.withChild(new HtmlTag("details")
                .withChild(new HtmlTag("summary").withInnerText("Advanced"))
                .withChild(new Div().withClass("orch-form-grid")
                    .withChild(label("Kind", new HtmlTag("span")
                        .withInnerText(plan.kind() != null ? plan.kind().name() : "TASK_TEMPLATE")))
                    .withChild(label("Status", new HtmlTag("span")
                        .withInnerText(plan.status() != null ? plan.status().name() : "UNKNOWN")))
                    .withChild(label("ID", new HtmlTag("code")
                        .withInnerText(nn(plan.id()))))
                    .withChild(label("Conversation", new HtmlTag("code")
                        .withInnerText(nn(plan.conversationId()))))
                    .withChild(label("Plan Start Message Order", new HtmlTag("span")
                        .withInnerText(String.valueOf(plan.planStartMessageOrder()))))
                    .withChild(label("Pending Question Index", new HtmlTag("span")
                        .withInnerText(String.valueOf(plan.pendingQuestionIndex())))))
                .withChild(new Div().withClass("orch-form-stack")
                    .withChild(label("Planning Task", TextArea.create("planningTask")
                        .withRows(2)
                        .withValue(nn(plan.planningTask()))))
                    .withChild(label("Final Message", TextArea.create("finalMessage")
                        .withRows(3)
                        .withValue(nn(plan.finalMessage()))))
                    .withChild(label("Settings Override JSON", TextArea.create("settingsOverrideJson")
                        .withRows(4)
                        .withValue(nn(plan.settingsOverrideJson()))))));
            form.withChild(advanced);
        }

        // Action buttons
        Div actions = new Div().withClass("tool-actions");
        actions.withChild(Button.create("Save").withClass("orch-primary").withAttribute("type", "submit"));
        if (!isNew) {
            actions.withChild(Button.create("Finalize Task")
                .withAttribute("hx-post", "/plans/_editor/" + planId + "/finalize")
                .withAttribute("hx-target", "#plan-editor-container")
                .withAttribute("hx-swap", "innerHTML"));
            actions.withChild(Button.create("Submit to Agent")
                .withClass("orch-primary")
                .withAttribute("hx-get", "/plans/_submit-form/" + planId)
                .withAttribute("hx-target", "#plan-submit-container")
                .withAttribute("hx-swap", "innerHTML"));
        }
        form.withChild(actions);
        container.withChild(form);
        if (!isNew) {
            container.withChild(new Div().withClass("plan-continue-row")
                .withChild(new HtmlTag("details")
                    .withChild(new HtmlTag("summary").withInnerText("Continue in Chat"))
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "/chat?continuePlanId=" + escapeAttr(planId))
                        .withClass("visually-hidden")
                        .withInnerText("Continue in chat without extra instruction"))
                    .withChild(Form.create().withAttribute("method", "get").withAttribute("action", "/chat")
                        .withChild(new HtmlTag("input", true).withAttribute("type", "hidden")
                            .withAttribute("name", "continuePlanId").withAttribute("value", planId))
                        .withChild(label("Optional instruction", TextArea.create("continuePlanMessage")
                            .withRows(2)
                            .withPlaceholder("What should the planning chat revisit or change?")))
                        .withChild(Button.create("Open planning chat").withAttribute("type", "submit")))));
        }

        if (!isNew) {
            // Submit form container
            container.withChild(new Div().withId("plan-submit-container"));
            container.withChild(sectionHeader("Recent Runs", "Latest saved plan/task executions."));
            container.withChild(new Div().withId("plan-runs-container")
                .hxGet("/plans/_runs/" + planId)
                .hxTrigger("load")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));
        }

        return container;
    }

    @GetMapping("/plans/_runs/{planId}")
    @ResponseBody
    public String planRunsFragment(@PathVariable String planId) {
        List<PlanRun> runs = planService.listRuns(planId);
        if (runs.isEmpty()) {
            return new Div().withClass("dashboard-empty").withInnerText("No runs yet.").render();
        }
        Table table = Table.create().withHeaders("Run", "Status", "Started", "Completed").withClass("dashboard-table");
        for (PlanRun run : runs) {
            table.addRow(
                new HtmlTag("code").withInnerText(run.id()),
                statusBadgeHtml(run.status() != null ? run.status().name() : "—"),
                new HtmlTag("span").withInnerText(run.startedAt() != null ? formatSince(run.startedAt()) : "—"),
                new HtmlTag("span").withInnerText(run.completedAt() != null ? formatSince(run.completedAt()) : "—")
            );
        }
        return table.render();
    }

    // ── Section renderers ──

    private Component planInputsSection(PlanDefinition plan) {
        return fieldListSection("inputs", plan.inputs(), plan.id());
    }

    private Component planOutputsSection(PlanDefinition plan) {
        return fieldListSection("outputs", plan.outputs(), plan.id());
    }

    private Component fieldListSection(String kind, List<PlanFieldDefinition> fields, String planId) {
        Div container = new Div();
        if (fields.isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty").withInnerText("None defined."));
            return container;
        }
        for (int i = 0; i < fields.size(); i++) {
            container.withChild(fieldRow(kind, fields.get(i), i, planId));
        }
        return container;
    }

    private Component fieldRow(String kind, PlanFieldDefinition field, int index, String planId) {
        Div row = new Div().withClass("field-row plan-field " + kind + "-field")
            .withAttribute("data-field-kind", kind)
            .withAttribute("data-field-index", String.valueOf(index));

        row.withChild(TextInput.create("").withAttribute("name", kind + "Name" + index)
            .withAttribute("placeholder", "name")
            .withAttribute("value", field.name() != null ? field.name() : "")
            .withAttribute("hx-trigger", "change")
            .withAttribute("hx-put", "/plans/_editor/" + planId + "/" + kind)
            .withAttribute("hx-target", "#plan-" + kind + "-section")
            .withAttribute("hx-swap", "innerHTML")
            .withAttribute("hx-include", "closest .field-row"));

        row.withChild(fieldTypeSelect(kind + "Type" + index, field.type()));

        row.withChild(new HtmlTag("label").withClass("inline-checkbox")
            .withChild(new HtmlTag("input").withAttribute("type", "checkbox")
                .withAttribute("name", kind + "Required" + index)
                .withAttribute(field.required() ? "checked" : "data-no-attr", field.required() ? "checked" : ""))
            .withChild(new TextNode(" req")));

        row.withChild(new HtmlTag("label").withClass("inline-checkbox")
            .withChild(new HtmlTag("input").withAttribute("type", "checkbox")
                .withAttribute("name", kind + "Array" + index)
                .withAttribute(field.array() ? "checked" : "data-no-attr", field.array() ? "checked" : ""))
            .withChild(new TextNode(" array")));

        row.withChild(TextArea.create("").withAttribute("name", kind + "Desc" + index)
            .withAttribute("placeholder", "description")
            .withAttribute("rows", "2")
            .withAttribute("value", field.description() != null ? field.description() : ""));

        row.withChild(TextArea.create("").withAttribute("name", kind + "Schema" + index)
            .withAttribute("placeholder", "schema (JSON)")
            .withAttribute("rows", "2")
            .withAttribute("value", field.schema() != null ? field.schema() : ""));

        row.withChild(new HtmlTag("button")
            .withClass("remove-field")
            .withAttribute("type", "button")
            .withAttribute("hx-delete", "/plans/_editor/" + planId + "/" + kind + "/" + index)
            .withAttribute("hx-target", "#plan-" + kind + "-section")
            .withAttribute("hx-swap", "innerHTML")
            .withInnerText("x"));

        return row;
    }

    private Component planDeliverablesSection(PlanDefinition plan) {
        return listSection("deliverables", plan.deliverables().stream().map(Item::new).toList(), plan.id());
    }

    private Component planStepsSection(PlanDefinition plan) {
        return listSection("steps", plan.steps().stream()
            .map(s -> new Item(s.order(), s.text())).toList(), plan.id());
    }

    private Component planValidationSection(PlanDefinition plan) {
        return listSection("validation", plan.validationCriteria().stream().map(Item::new).toList(), plan.id());
    }

    private Component planAssumptionsSection(PlanDefinition plan) {
        return listSection("assumptions", plan.assumptions().stream().map(Item::new).toList(), plan.id());
    }

    private Component listSectionHtml(PlanDefinition plan, String section) {
        return switch (section) {
            case "deliverables" -> planDeliverablesSection(plan);
            case "steps" -> planStepsSection(plan);
            case "validation" -> planValidationSection(plan);
            case "assumptions" -> planAssumptionsSection(plan);
            case "evidence" -> listSection("evidence", plan.executionEvidence().stream().map(Item::new).toList(), plan.id());
            case "feedback" -> listSection("feedback", plan.validationFeedback().stream().map(Item::new).toList(), plan.id());
            case "questions" -> listSection("questions", plan.pendingQuestions().stream().map(Item::new).toList(), plan.id());
            default -> new Div().withInnerText("Unknown section: " + section);
        };
    }

    private record Item(int order, String text) {
        Item(String text) { this(0, text); }
    }

    private Component listSection(String section, List<Item> items, String planId) {
        Div container = new Div().withClass("field-list");
        if (items.isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty").withInnerText("None defined."));
            return container;
        }
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Div row = new Div().withClass("field-row");
            String placeholder = section.equals("steps") ? "Step text" : "Item text";
            row.withChild(TextInput.create("")
                .withAttribute("name", section + "Value" + i)
                .withAttribute("placeholder", section.equals("steps") ? (item.order() + ". " + placeholder) : placeholder)
                .withAttribute("value", item.text() != null ? item.text() : "")
                .withAttribute("hx-trigger", "change")
                .withAttribute("hx-put", "/plans/_editor/" + planId + "/" + section)
                .withAttribute("hx-target", "#plan-" + section + "-section")
                .withAttribute("hx-swap", "innerHTML")
                .withAttribute("hx-include", "closest .field-row"));
            if (section.equals("steps")) {
                row.withChild(new HtmlTag("button")
                    .withClass("remove-field")
                    .withAttribute("type", "button")
                    .withAttribute("aria-label", "Move step up")
                    .withAttribute("hx-post", "/plans/_editor/" + planId + "/steps/" + i + "/move-up")
                    .withAttribute("hx-target", "#plan-steps-section")
                    .withAttribute("hx-swap", "innerHTML")
                    .withAttribute(i == 0 ? "disabled" : "data-no-attr", i == 0 ? "disabled" : "")
                    .withInnerText("↑"));
                row.withChild(new HtmlTag("button")
                    .withClass("remove-field")
                    .withAttribute("type", "button")
                    .withAttribute("aria-label", "Move step down")
                    .withAttribute("hx-post", "/plans/_editor/" + planId + "/steps/" + i + "/move-down")
                    .withAttribute("hx-target", "#plan-steps-section")
                    .withAttribute("hx-swap", "innerHTML")
                    .withAttribute(i == items.size() - 1 ? "disabled" : "data-no-attr", i == items.size() - 1 ? "disabled" : "")
                    .withInnerText("↓"));
            }
            row.withChild(new HtmlTag("button")
                .withClass("remove-field")
                .withAttribute("type", "button")
                .withAttribute("hx-delete", "/plans/_editor/" + planId + "/" + section + "/" + i)
                .withAttribute("hx-target", "#plan-" + section + "-section")
                .withAttribute("hx-swap", "innerHTML")
                .withInnerText("x"));
            container.withChild(row);
        }
        return container;
    }

    private Component submitToAgentPanel(String planId) {
        PlanDefinition plan;
        try {
            plan = planService.getTask(planId);
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Plan not found.");
        }

        Div panel = new Div().withClass("orch-panel");
        panel.withChild(Header.H2("Submit to Agent"));

        Form form = Form.create()
            .withHxPost("/plans/_submit/" + planId)
            .withHxTarget("#plan-submit-result")
            .withHxSwap("innerHTML");

        // Agent select
        List<AgentProfile> agents = agentProfileService.list();
        Select agentSelect = Select.create("agentId");
        for (var agent : agents) {
            if (agent.status() != null && !"DISABLED".equals(agent.status().name())) {
                agentSelect.addOption(agent.id(),
                    (agent.name() != null ? agent.name() : agent.id()) +
                    " (" + (agent.defaultModel() != null ? agent.defaultModel() : "no model") + ")",
                    false);
            }
        }
        form.withChild(new Div().withClass("orch-form-stack")
            .withChild(label("Agent", agentSelect))
            .withChild(label("Model Override", modelSelectWithCurrent(
                "modelOverride", null, chatService.availableModels())))
            .withChild(label("Priority", TextInput.number("priority")
                .withValue("9").withMin("0").withMax("100")))
            .withChild(label("Workspace ID", TextInput.create("workspaceId")
                .withPlaceholder("optional"))));

        // Generated input form from declared inputs
        if (!plan.inputs().isEmpty()) {
            Div inputsDiv = new Div().withClass("orch-form-stack");
            inputsDiv.withChild(new HtmlTag("h4").withInnerText("Runtime Inputs"));
            for (PlanFieldDefinition input : plan.inputs()) {
                String labelText = input.name() + " (" + input.type().wireName()
                    + (input.array() ? "[]" : "") + (input.required() ? ", required" : ", optional") + ")";
                String placeholder = input.description() != null ? input.description() : input.type().wireName();
                String fieldName = "input_" + input.name();
                if (input.type() == PlanFieldType.JSON || input.array() || input.type() == PlanFieldType.USER_MESSAGE) {
                    inputsDiv.withChild(label(labelText,
                        TextArea.create(fieldName)
                            .withRows(input.array() ? 3 : 2)
                            .withPlaceholder(placeholder)));
                } else {
                    inputsDiv.withChild(label(labelText,
                        TextInput.create(fieldName).withPlaceholder(placeholder)));
                }
            }
            form.withChild(inputsDiv);
        }

        form.withChild(Button.create("Submit").withClass("orch-primary").withAttribute("type", "submit"));
        panel.withChild(form);
        panel.withChild(new Div().withId("plan-submit-result"));

        return panel;
    }

    // ── Worktype select ──

    private Select worktypeSelect(String currentValue) {
        WorkTypeProfile current = WorkTypeProfile.fromString(currentValue);
        Select select = Select.create("workTypeProfile");
        select.addOption("CODING_CENTRIC", "Coding-centric",
            current == WorkTypeProfile.CODING_CENTRIC);
        select.addOption("DATA_CENTRIC", "Data-centric",
            current == WorkTypeProfile.DATA_CENTRIC);
        select.addOption("RESEARCH_CENTRIC", "Research-centric",
            current == WorkTypeProfile.RESEARCH_CENTRIC);
        return select;
    }

    private String resolveWorktype(Map<String, String> params) {
        String wt = params.get("workTypeProfile");
        return WorkTypeProfile.fromString(wt).name();
    }

    private Select agentSelect(String name, String currentValue) {
        Select select = Select.create(name);
        select.addOption("", "Select agent", !StringUtils.hasText(currentValue));
        for (AgentProfile agent : agentProfileService.list()) {
            String id = agent.id();
            String label = StringUtils.hasText(agent.name()) ? agent.name() + " (" + id + ")" : id;
            select.addOption(id, label, id.equals(currentValue));
        }
        return select;
    }

    private Select fieldTypeSelect(String name, PlanFieldType current) {
        String cw = current != null ? current.wireName() : "string";
        Select select = Select.create(name);
        for (PlanFieldType ft : PlanFieldType.values()) {
            select.addOption(ft.wireName(), ft.wireName(), ft.wireName().equals(cw));
        }
        return select;
    }

    // ════════════════════════════════════════════════════════════════
    //  Workflows (HTMX-first node/tree editor)
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/workflows")
    @ResponseBody
    public String workflows() {
        Component body = new Div()
            .withId("workflows-page")
            .withAttribute("data-orchestration-page", "workflows")
            .withChild(Header.H1("Workflows"))
            .withChild(new Paragraph("Compose task nodes into route-connected workflow graphs with gates, approvals, and submit-to-agent execution."))
            .withChild(new Div().withClass("browser-layout browser-layout-wide")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Button.create("New Workflow")
                            .withClass("orch-primary")
                            .hxPost("/workflows/_editor/_draft")
                            .hxTarget("#workflow-editor-container")
                            .hxSwap("innerHTML")))
                    .withChild(TextInput.search("workflowFilter")
                        .withId("workflow-filter")
                        .withPlaceholder("Filter workflows")
                        .withAttribute("hx-get", "/workflows/_list")
                        .withAttribute("hx-trigger", "keyup changed delay:300ms")
                        .withAttribute("hx-target", "#workflow-list")
                        .withAttribute("hx-swap", "innerHTML")
                        .withAttribute("hx-include", "#workflow-filter"))
                    .withChild(new Div().withId("workflow-list")
                        .withClass("entity-list")
                        .hxGet("/workflows/_list")
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withChild(workflowListContent(null))))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("workflow-editor-container")
                        .withChild(workflowEditorEmptyState()))))
            .withChild(moduleScript(WORKFLOWS_JS));
        return renderPage(body, "/workflows");
    }

    private Component workflowEditorEmptyState() {
        return new Div().withClass("orch-panel")
            .withChild(new Div().withClass("dashboard-empty")
                .withInnerText("Select a workflow from the list or create a new one."));
    }

    // ── Workflow list HTMX partial ──

    @GetMapping("/workflows/_list")
    @ResponseBody
    public String workflowListFragment(@RequestParam(value = "workflowFilter", required = false) String filter) {
        return workflowListContent(filter).render();
    }

    private Component workflowListContent(String filter) {
        List<WorkflowDefinition> workflows = workflowService.listDefinitions();
        if (filter != null && !filter.isBlank()) {
            String f = filter.toLowerCase();
            workflows = workflows.stream()
                .filter(w -> (w.title() != null && w.title().toLowerCase().contains(f)))
                .toList();
        }
        if (workflows.isEmpty()) {
            return new Div().withClass("tool-item").withInnerText("No workflows.");
        }
        Div list = new Div();
        for (var wf : workflows) {
            list.withChild(new HtmlTag("button")
                .withClass("tool-item")
                .hxGet("/workflows/_editor/" + escapeAttr(wf.id()))
                .hxTarget("#workflow-editor-container")
                .hxSwap("innerHTML")
                .withChild(new HtmlTag("strong").withInnerText(wf.title() != null ? wf.title() : "Untitled"))
                .withChild(new HtmlTag("br"))
                .withChild(new HtmlTag("span").withInnerText(
                    wf.nodes().size() + " nodes, " + wf.routes().size() + " routes")));
        }
        return list;
    }

    // ── Workflow editor HTMX partials ──

    @GetMapping("/workflows/_editor/_new")
    @ResponseBody
    public String newWorkflowEditor() {
        return workflowEditorFragment(null).render();
    }

    @PostMapping("/workflows/_editor/_draft")
    @ResponseBody
    public String createWorkflowDraftEditor() {
        WorkflowDefinition created = workflowService.saveDefinitionValidated(new WorkflowDefinition(
            null,
            "Untitled Workflow",
            "",
            List.of(),
            List.of(),
            null,
            null));
        return workflowEditorFragment(created).render();
    }

    @GetMapping("/workflows/_editor/{workflowId}")
    @ResponseBody
    public String workflowEditor(@PathVariable String workflowId) {
        try {
            WorkflowDefinition wf = workflowService.getDefinition(workflowId);
            return workflowEditorFragment(wf).render();
        } catch (IllegalArgumentException e) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("dashboard-empty")
                    .withInnerText("Workflow not found: " + escapeAttr(workflowId)))
                .render();
        }
    }

    @PostMapping("/workflows/_editor")
    @ResponseBody
    public String createWorkflowEditor(@RequestParam Map<String, String> params) {
        String title = params.getOrDefault("title", "").trim();
        if (title.isBlank()) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("orch-status")
                    .withInnerText("Title is required."))
                .render();
        }
        WorkflowDefinition created = workflowService.saveDefinitionValidated(new WorkflowDefinition(
            null, title,
            nn(params.get("summary")),
            List.of(), List.of(), null, null));
        return workflowEditorFragment(created).render();
    }

    @PutMapping("/workflows/_editor/{workflowId}")
    @ResponseBody
    public String updateWorkflowEditor(@PathVariable String workflowId, @RequestParam Map<String, String> params) {
        try {
            WorkflowDefinition current = workflowService.getDefinition(workflowId);
            WorkflowDefinition updated = new WorkflowDefinition(
                workflowId,
                params.containsKey("title") ? nn(params.get("title")) : current.title(),
                params.containsKey("summary") ? nn(params.get("summary")) : current.summary(),
                current.nodes(), current.routes(),
                current.createdAt(), current.updatedAt());
            workflowService.saveDefinitionValidated(updated);
            return workflowEditorFragment(workflowService.getDefinition(workflowId)).render();
        } catch (IllegalArgumentException e) {
            return workflowEditorValidationError(e.getMessage()).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @DeleteMapping("/workflows/{workflowId}")
    @ResponseBody
    public String deleteWorkflow(@PathVariable String workflowId) {
        try {
            workflowService.deleteDefinition(workflowId);
            return workflowEditorEmptyState().render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Node CRUD endpoints ──

    @PostMapping("/workflows/_editor/{workflowId}/nodes")
    @ResponseBody
    public String addWorkflowNode(@PathVariable String workflowId, @RequestParam Map<String, String> params) {
        try {
            WorkflowDefinition current = workflowService.getDefinition(workflowId);
            String nodeType = params.getOrDefault("nodeType", "TASK");
            List<WorkflowNode> nodes = new ArrayList<>(current.nodes());
            int maxIdx = nodes.stream()
                .map(n -> n.key().replace("node_", ""))
                .filter(s -> s.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max().orElse(0);
            String key = "node_" + (maxIdx + 1);
            nodes.add(new WorkflowNode(key, WorkflowNodeType.fromWireName(nodeType),
                nn(params.get("planId")), key, null, Map.of(),
                false, List.of(),
                nn(params.get("messageTemplate")),
                nn(params.get("resumePolicy"))));
            WorkflowDefinition updated = new WorkflowDefinition(
                workflowId, current.title(), current.summary(),
                nodes, current.routes(), current.createdAt(), current.updatedAt());
            workflowService.saveDefinition(updated);
            return workflowNodesSection(workflowService.getDefinition(workflowId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @DeleteMapping("/workflows/_editor/{workflowId}/nodes/{nodeKey}")
    @ResponseBody
    public String removeWorkflowNode(@PathVariable String workflowId, @PathVariable String nodeKey) {
        try {
            WorkflowDefinition current = workflowService.getDefinition(workflowId);
            List<WorkflowNode> nodes = new ArrayList<>(current.nodes());
            nodes.removeIf(n -> n.key().equals(nodeKey));
            // Also remove routes referencing this node
            List<WorkflowRoute> routes = current.routes().stream()
                .filter(r -> !r.fromNodeKey().equals(nodeKey) && !r.toNodeKey().equals(nodeKey))
                .toList();
            WorkflowDefinition updated = new WorkflowDefinition(
                workflowId, current.title(), current.summary(),
                nodes, routes, current.createdAt(), current.updatedAt());
            workflowService.saveDefinition(updated);
            // Refresh full editor
            return workflowEditorFragment(workflowService.getDefinition(workflowId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @PutMapping("/workflows/_editor/{workflowId}/nodes/{nodeKey}")
    @ResponseBody
    public String updateWorkflowNode(@PathVariable String workflowId, @PathVariable String nodeKey,
                                     @RequestParam Map<String, String> params) {
        try {
            WorkflowDefinition current = workflowService.getDefinition(workflowId);
            List<WorkflowNode> nodes = new ArrayList<>(current.nodes());
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).key().equals(nodeKey)) {
                    WorkflowNode old = nodes.get(i);
                    nodes.set(i, new WorkflowNode(
                        nodeKey,
                        params.containsKey("nodeType") ? WorkflowNodeType.fromWireName(params.get("nodeType")) : old.type(),
                        params.containsKey("planId") ? nn(params.get("planId")) : old.planId(),
                        params.containsKey("label") ? nn(params.get("label")) : old.label(),
                        params.containsKey("inputName") ? nn(params.get("inputName")) : old.inputName(),
                        old.config(),
                        params.containsKey("parallel") ? "true".equals(params.get("parallel")) : old.parallel(),
                        old.inputBindings(),
                        params.containsKey("messageTemplate") ? nn(params.get("messageTemplate")) : old.messageTemplate(),
                        params.containsKey("resumePolicy") ? nn(params.get("resumePolicy")) : old.resumePolicy()
                    ));
                    break;
                }
            }
            WorkflowDefinition updated = new WorkflowDefinition(
                workflowId, current.title(), current.summary(),
                nodes, current.routes(), current.createdAt(), current.updatedAt());
            workflowService.saveDefinition(updated);
            return workflowNodesSection(workflowService.getDefinition(workflowId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/workflows/_editor/{workflowId}/nodes/{nodeKey}/panel")
    @ResponseBody
    public String workflowNodePanel(@PathVariable String workflowId, @PathVariable String nodeKey) {
        try {
            WorkflowDefinition wf = workflowService.getDefinition(workflowId);
            WorkflowNode node = wf.nodes().stream()
                .filter(candidate -> candidate.key().equals(nodeKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeKey));
            return workflowSelectedNodePanel(wf, node).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Route CRUD endpoints ──

    @PostMapping("/workflows/_editor/{workflowId}/routes")
    @ResponseBody
    public String addWorkflowRoute(@PathVariable String workflowId, @RequestParam Map<String, String> params) {
        try {
            WorkflowDefinition current = workflowService.getDefinition(workflowId);
            List<WorkflowRoute> routes = new ArrayList<>(current.routes());
            String fromNodeKey = nn(params.get("fromNodeKey"));
            String routeType = params.getOrDefault("routeType", "MAP_OUTPUT");
            routes.add(new WorkflowRoute(
                "route_" + (routes.size() + 1),
                fromNodeKey.isBlank() ? null : fromNodeKey,
                nn(params.get("fromOutputName")),
                params.getOrDefault("toNodeKey", ""),
                nn(params.get("toInputName")),
                WorkflowRouteType.fromWireName(routeType),
                nn(params.get("condition"))
            ));
            WorkflowDefinition updated = new WorkflowDefinition(
                workflowId, current.title(), current.summary(),
                current.nodes(), routes, current.createdAt(), current.updatedAt());
            workflowService.saveDefinition(updated);
            return workflowRoutesSection(workflowService.getDefinition(workflowId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @DeleteMapping("/workflows/_editor/{workflowId}/routes/{routeId}")
    @ResponseBody
    public String removeWorkflowRoute(@PathVariable String workflowId, @PathVariable String routeId) {
        try {
            WorkflowDefinition current = workflowService.getDefinition(workflowId);
            List<WorkflowRoute> routes = current.routes().stream()
                .filter(r -> !r.id().equals(routeId))
                .toList();
            WorkflowDefinition updated = new WorkflowDefinition(
                workflowId, current.title(), current.summary(),
                current.nodes(), routes, current.createdAt(), current.updatedAt());
            workflowService.saveDefinition(updated);
            return workflowRoutesSection(workflowService.getDefinition(workflowId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Validation ──

    @GetMapping("/workflows/_editor/{workflowId}/validate")
    @ResponseBody
    public String validateWorkflow(@PathVariable String workflowId) {
        try {
            WorkflowDefinition wf = workflowService.getDefinition(workflowId);
            WorkflowValidator.ValidationResult result = workflowService.validateGraph(wf);
            Div container = new Div().withClass("warnings");
            for (String error : result.errors()) {
                container.withChild(new Div().withClass("warning-item")
                    .withAttribute("style", "background:#fff1f1;border-color:#e6b3b3")
                    .withInnerText("ERROR: " + error));
            }
            for (String warning : result.warnings()) {
                container.withChild(new Div().withClass("warning-item")
                    .withInnerText(warning));
            }
            if (result.valid() && result.warnings().isEmpty()) {
                container.withChild(new Div().withClass("warning-item")
                    .withAttribute("style", "background:#eef8f0;border-color:#6fa178")
                    .withInnerText("Valid: no errors found."));
            }
            return container.render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Submit to agent ──

    @GetMapping("/workflows/_submit-form/{workflowId}")
    @ResponseBody
    public String workflowSubmitForm(@PathVariable String workflowId) {
        return workflowSubmitToAgentPanel(workflowId).render();
    }

    @PostMapping("/workflows/_submit/{workflowId}")
    @ResponseBody
    public String workflowSubmitToAgent(@PathVariable String workflowId, @RequestParam Map<String, String> params) {
        try {
            WorkflowDefinition wf = workflowService.getDefinition(workflowId);
            WorkflowValidator.ValidationResult validation = workflowService.validateGraph(wf);
            if (!validation.valid()) {
                return workflowEditorValidationError(String.join("; ", validation.errors())).render();
            }
            String agentId = params.get("agentId");
            if (agentId == null || agentId.isBlank()) {
                agentId = agentProfileService.list().stream()
                    .filter(a -> a.status() != null && !"DISABLED".equals(a.status().name()))
                    .findFirst()
                    .map(a -> a.id())
                    .orElse(null);
            }
            if (agentId == null || agentId.isBlank()) {
                return new Div().withClass("orch-status").withInnerText("No active agents available. Create an agent first.").render();
            }
            int priority = 0;
            try { priority = Integer.parseInt(params.getOrDefault("priority", "0")); } catch (NumberFormatException ignored) {}
            WorkAssignment assignment = assignmentService.create(new AssignmentRequest(
                agentId, null, null, AssignmentType.WORKFLOW_RUN,
                priority,
                nn(params.get("modelOverride")),
                nn(params.get("workspaceId")),
                Map.of("workflowId", workflowId)
            ));
            return workflowSubmitResultFragment(assignment, wf).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Workflow editor rendering helpers
    // ════════════════════════════════════════════════════════════════

    private Component workflowEditorValidationError(String message) {
        Div container = new Div().withClass("orch-panel workflow-editor");
        container.withChild(new Div().withClass("orch-status orch-status-error")
            .withChild(new HtmlTag("strong").withInnerText("Validation failed"))
            .withChild(new HtmlTag("br"))
            .withChild(new HtmlTag("span").withInnerText(message)));
        return container;
    }

    private Component workflowEditorFragment(WorkflowDefinition wf) {
        boolean isNew = wf == null;
        String wfId = isNew ? null : wf.id();

        Div container = new Div().withClass("orch-panel workflow-editor");
        container.withChild(Header.H2(isNew ? "New Workflow" : "Workflow Editor"));

        // Scalar fields form
        Form form = Form.create();
        if (isNew) {
            form.withHxPost("/workflows/_editor");
        } else {
            form.withHxPut("/workflows/_editor/" + wfId);
        }
        form.withHxTarget("#workflow-editor-container");
        form.withHxSwap("innerHTML");

        form.withChild(new Div().withClass("orch-form-stack")
            .withChild(label("Title", TextInput.create("title")
                .withId("workflow-title")
                .withValue(isNew ? "" : nn(wf.title()))))
            .withChild(label("Summary", TextArea.create("summary")
                .withId("workflow-summary").withRows(2)
                .withValue(isNew ? "" : nn(wf.summary())))));

        if (!isNew) {
            // Nodes section
            form.withChild(sectionHeader("Nodes", "Task nodes connected by routes."));
            form.withChild(new Div().withId("workflow-nodes-section")
                .withChild(workflowNodesSection(wf)));
            form.withChild(addNodeForm(wfId));
            form.withChild(new Div().withId("workflow-node-panel")
                .withChild(new Div().withClass("dashboard-empty").withInnerText("Select a node to inspect adapters, routes, inputs, and outputs.")));

            // Routes section
            form.withChild(sectionHeader("Routes", "Connect node outputs to node inputs."));
            form.withChild(new Div().withId("workflow-routes-section")
                .withChild(workflowRoutesSection(wf)));
            form.withChild(addRouteForm(wfId));

            // Validation
            form.withChild(sectionHeader("Validation", "Graph structure and type compatibility."));
            form.withChild(new Div().withId("workflow-validation-result")
                .withClass("warnings"));
            form.withChild(Button.create("Validate")
                .hxGet("/workflows/_editor/" + wfId + "/validate")
                .hxTarget("#workflow-validation-result")
                .hxSwap("innerHTML"));
        }

        // Actions
        Div actions = new Div().withClass("tool-actions");
        actions.withChild(Button.create("Save").withClass("orch-primary").withAttribute("type", "submit"));
        if (!isNew) {
            actions.withChild(Button.create("Submit to Agent")
                .withClass("orch-primary")
                .hxGet("/workflows/_submit-form/" + wfId)
                .hxTarget("#workflow-submit-container")
                .hxSwap("innerHTML"));
            actions.withChild(Button.create("Delete")
                .hxDelete("/workflows/" + wfId)
                .hxTarget("#workflow-editor-container")
                .hxSwap("innerHTML")
                .withAttribute("hx-confirm", "Delete this workflow?"));
        }
        form.withChild(actions);
        container.withChild(form);

        if (!isNew) {
            container.withChild(new Div().withId("workflow-submit-container"));
            container.withChild(sectionHeader("Recent Runs", "Workflow execution history and approval-waiting states."));
            container.withChild(new Div().withId("workflow-runs-container")
                .hxGet("/workflows/_runs/" + wf.id())
                .hxTrigger("load")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));
        }

        return container;
    }

    @GetMapping("/workflows/_runs/{workflowId}")
    @ResponseBody
    public String workflowRunsFragment(@PathVariable String workflowId) {
        List<WorkflowRun> runs = workflowService.listRuns(workflowId);
        if (runs.isEmpty()) {
            return new Div().withClass("dashboard-empty").withInnerText("No runs yet.").render();
        }
        Table table = Table.create().withHeaders("Run", "Status", "Current Node", "Started", "Action").withClass("dashboard-table");
        for (WorkflowRun run : runs) {
            Component action = run.status() != null && "WAITING".equals(run.status().name())
                ? Button.create("Resume")
                    .withAttribute("hx-post", "/workflows/_runs/" + run.id() + "/resume")
                    .withAttribute("hx-target", "#workflow-runs-container")
                    .withAttribute("hx-swap", "innerHTML")
                : new HtmlTag("span").withInnerText("—");
            table.addRow(
                new HtmlTag("code").withInnerText(run.id()),
                statusBadgeHtml(run.status() != null ? run.status().name() : "—"),
                new HtmlTag("span").withInnerText(String.valueOf(run.currentNodeIndex())),
                new HtmlTag("span").withInnerText(run.startedAt() != null ? formatSince(run.startedAt()) : "—"),
                action
            );
        }
        return table.render();
    }

    @PostMapping("/workflows/_runs/{runId}/resume")
    @ResponseBody
    public String resumeWorkflowRun(@PathVariable String runId) {
        WorkflowRun run = workflowService.resumeRun(runId);
        return workflowRunsFragment(run.workflowId());
    }

    private Component addNodeForm(String wfId) {
        Div form = new Div().withClass("field-row");
        Select typeSelect = Select.create("nodeType");
        for (WorkflowNodeType nt : WorkflowNodeType.values()) {
            typeSelect.addOption(nt.wireName(), nt.wireName(), nt == WorkflowNodeType.TASK);
        }
        form.withChild(typeSelect);

        // Plan select
        List<PlanDefinition> tasks = planService.listTasks();
        Select planSelect = Select.create("planId");
        planSelect.addOption("", "-- plan --", true);
        for (var t : tasks) {
            planSelect.addOption(t.id(), t.title() != null ? t.title() : t.id(), false);
        }
        form.withChild(planSelect);
        form.withChild(TextInput.create("messageTemplate").withPlaceholder("msg template"));

        form.withChild(Button.create("Add")
            .hxPost("/workflows/_editor/" + wfId + "/nodes")
            .hxTarget("#workflow-nodes-section")
            .hxSwap("innerHTML")
            .hxInclude("closest .field-row"));
        return form;
    }

    private Component addRouteForm(String wfId) {
        try {
            WorkflowDefinition wf = workflowService.getDefinition(wfId);
            Div form = new Div().withClass("field-row");

            // From node select
            Select fromSelect = Select.create("fromNodeKey");
            fromSelect.addOption("", "-- from --", true);
            for (var n : wf.nodes()) {
                fromSelect.addOption(n.key(), n.displayLabel(), false);
            }
            form.withChild(fromSelect);

            form.withChild(workflowOutputSelect(wf, "fromOutputName"));
            form.withChild(routeTypeSelect());

            // To node select
            Select toSelect = Select.create("toNodeKey");
            toSelect.addOption("", "-- to --", true);
            for (var n : wf.nodes()) {
                toSelect.addOption(n.key(), n.displayLabel(), false);
            }
            form.withChild(toSelect);

            form.withChild(workflowInputSelect(wf, "toInputName"));

            form.withChild(Button.create("Add Route")
                .hxPost("/workflows/_editor/" + wfId + "/routes")
                .hxTarget("#workflow-routes-section")
                .hxSwap("innerHTML")
                .hxInclude("closest .field-row"));
            return form;
        } catch (Exception e) {
            return new Div().withInnerText("Error loading form: " + e.getMessage());
        }
    }

    private Select routeTypeSelect() {
        Select select = Select.create("routeType");
        for (WorkflowRouteType rt : WorkflowRouteType.values()) {
            select.addOption(rt.wireName(), rt.wireName(), rt == WorkflowRouteType.MAP_OUTPUT);
        }
        return select;
    }

    private Select workflowOutputSelect(WorkflowDefinition wf, String name) {
        Select select = Select.create(name);
        select.addOption("", "-- output --", true);
        for (WorkflowNode node : wf.nodes()) {
            if (node.type() == WorkflowNodeType.TASK && StringUtils.hasText(node.planId())) {
                try {
                    PlanDefinition task = planService.getTask(node.planId());
                    for (PlanFieldDefinition output : task.outputs()) {
                        select.addOption(output.name(),
                            node.displayLabel() + "." + output.name() + " (" + output.type().wireName() + ")",
                            false);
                    }
                } catch (Exception ignored) {
                    // Invalid task references are reported by validation.
                }
            } else {
                select.addOption("valid", node.displayLabel() + ".valid", false);
                select.addOption("messageId", node.displayLabel() + ".messageId", false);
            }
        }
        return select;
    }

    private Select workflowInputSelect(WorkflowDefinition wf, String name) {
        Select select = Select.create(name);
        select.addOption("", "-- input --", true);
        for (WorkflowNode node : wf.nodes()) {
            if (node.type() == WorkflowNodeType.TASK && StringUtils.hasText(node.planId())) {
                try {
                    PlanDefinition task = planService.getTask(node.planId());
                    for (PlanFieldDefinition input : task.inputs()) {
                        select.addOption(input.name(),
                            node.displayLabel() + "." + input.name()
                                + (input.required() ? " required" : " optional")
                                + " (" + input.type().wireName() + ")",
                            false);
                    }
                } catch (Exception ignored) {
                    // Invalid task references are reported by validation.
                }
            }
        }
        return select;
    }

    private Component workflowNodesSection(WorkflowDefinition wf) {
        Div container = new Div().withClass("field-list");
        if (wf.nodes().isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty").withInnerText("No nodes. Add a node above."));
            return container;
        }
        List<PlanDefinition> tasks = planService.listTasks();
        for (var node : wf.nodes()) {
            Div row = new Div().withClass("field-row workflow-node-row");
            row.withChild(TextInput.create("label")
                .withAttribute("value", node.displayLabel())
                .withAttribute("placeholder", "label")
                .withAttribute("hx-put", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key())
                .withAttribute("hx-trigger", "change")
                .withAttribute("hx-include", "closest .workflow-node-row")
                .withAttribute("hx-target", "#workflow-nodes-section")
                .withAttribute("hx-swap", "innerHTML"));

            Select nodeType = Select.create("nodeType");
            for (WorkflowNodeType value : WorkflowNodeType.values()) {
                nodeType.addOption(value.wireName(), value.wireName(), value == node.type());
            }
            nodeType.withAttribute("hx-put", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key())
                .withAttribute("hx-trigger", "change")
                .withAttribute("hx-include", "closest .workflow-node-row")
                .withAttribute("hx-target", "#workflow-nodes-section")
                .withAttribute("hx-swap", "innerHTML");
            row.withChild(nodeType);

            Select planSelect = Select.create("planId");
            planSelect.addOption("", "-- plan --", !StringUtils.hasText(node.planId()));
            for (PlanDefinition task : tasks) {
                boolean selected = StringUtils.hasText(node.planId()) && node.planId().equals(task.id());
                planSelect.addOption(task.id(), task.title() != null ? task.title() : task.id(), selected);
            }
            planSelect.withAttribute("hx-put", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key())
                .withAttribute("hx-trigger", "change")
                .withAttribute("hx-include", "closest .workflow-node-row")
                .withAttribute("hx-target", "#workflow-nodes-section")
                .withAttribute("hx-swap", "innerHTML");
            row.withChild(planSelect);

            row.withChild(TextInput.create("messageTemplate")
                .withAttribute("value", nn(node.messageTemplate()))
                .withAttribute("placeholder", "message template")
                .withAttribute("hx-put", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key())
                .withAttribute("hx-trigger", "change")
                .withAttribute("hx-include", "closest .workflow-node-row")
                .withAttribute("hx-target", "#workflow-nodes-section")
                .withAttribute("hx-swap", "innerHTML"));

            row.withChild(TextInput.create("resumePolicy")
                .withAttribute("value", nn(node.resumePolicy()))
                .withAttribute("placeholder", "resume policy")
                .withAttribute("hx-put", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key())
                .withAttribute("hx-trigger", "change")
                .withAttribute("hx-include", "closest .workflow-node-row")
                .withAttribute("hx-target", "#workflow-nodes-section")
                .withAttribute("hx-swap", "innerHTML"));

            row.withChild(new HtmlTag("code").withInnerText(node.key()));

            String outCount = String.valueOf(wf.outgoingRoutes(node.key()).size());
            row.withChild(new HtmlTag("span").withClass("orch-meta").withInnerText(outCount + " out"));
            row.withChild(workflowNodeSchemaSummary(node));
            row.withChild(Button.create("Select")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key() + "/panel")
                .withAttribute("hx-target", "#workflow-node-panel")
                .withAttribute("hx-swap", "innerHTML"));

            row.withChild(new HtmlTag("button")
                .withClass("remove-field")
                .withAttribute("type", "button")
                .withAttribute("hx-delete", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key())
                .withAttribute("hx-target", "#workflow-editor-container")
                .withAttribute("hx-swap", "innerHTML")
                .withInnerText("x"));

            container.withChild(row);
        }
        return container;
    }

    private Component workflowNodeSchemaSummary(WorkflowNode node) {
        Div summary = new Div().withClass("orch-meta");
        if (node.type() == WorkflowNodeType.TASK && StringUtils.hasText(node.planId())) {
            try {
                PlanDefinition task = planService.getTask(node.planId());
                String inputs = task.inputs().stream()
                    .map(input -> input.name() + (input.required() ? "*" : ""))
                    .collect(Collectors.joining(", "));
                String outputs = task.outputs().stream()
                    .map(PlanFieldDefinition::name)
                    .collect(Collectors.joining(", "));
                summary.withInnerText("in: " + (inputs.isBlank() ? "none" : inputs)
                    + " | out: " + (outputs.isBlank() ? "none" : outputs));
                return summary;
            } catch (Exception e) {
                summary.withInnerText("schema unavailable");
                return summary;
            }
        }
        summary.withInnerText(node.type().wireName());
        return summary;
    }

    private Component workflowRoutesSection(WorkflowDefinition wf) {
        Div container = new Div().withClass("field-list");
        if (wf.routes().isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty").withInnerText("No routes. Routes connect node outputs to downstream node inputs."));
            return container;
        }
        container.withChild(new Div().withClass("field-row workflow-route-header")
            .withChild(new HtmlTag("strong").withInnerText("Route ID"))
            .withChild(new HtmlTag("strong").withInnerText("Adapter/Route Type"))
            .withChild(new HtmlTag("strong").withInnerText("Source Node"))
            .withChild(new HtmlTag("strong").withInnerText("Source Output"))
            .withChild(new HtmlTag("strong").withInnerText("Destination Node"))
            .withChild(new HtmlTag("strong").withInnerText("Destination Input"))
            .withChild(new HtmlTag("strong").withInnerText("Condition"))
            .withChild(new HtmlTag("strong").withInnerText("Actions")));
        for (var route : wf.routes()) {
            Div row = new Div().withClass("field-row");

            row.withChild(new HtmlTag("code").withInnerText(route.id()));
            row.withChild(routeTypeBadge(route.routeType()));
            row.withChild(new HtmlTag("span").withInnerText(route.fromNodeKey() != null ? route.fromNodeKey() : "(root)"));
            row.withChild(new HtmlTag("span").withInnerText(nn(route.fromOutputName())));
            row.withChild(new HtmlTag("span").withInnerText(route.toNodeKey()));
            row.withChild(new HtmlTag("span").withInnerText(nn(route.toInputName())));
            row.withChild(new HtmlTag("span").withInnerText(nn(route.condition())));

            row.withChild(new HtmlTag("button")
                .withClass("remove-field")
                .withAttribute("type", "button")
                .withAttribute("hx-delete", "/workflows/_editor/" + wf.id() + "/routes/" + route.id())
                .withAttribute("hx-target", "#workflow-routes-section")
                .withAttribute("hx-swap", "innerHTML")
                .withInnerText("x"));

            container.withChild(row);
        }
        return container;
    }

    private Component workflowSelectedNodePanel(WorkflowDefinition wf, WorkflowNode node) {
        Div panel = new Div().withClass("orch-panel workflow-node-panel");
        panel.withChild(Header.H3("Selected Node: " + node.displayLabel()));
        panel.withChild(new Div().withClass("orch-form-grid")
            .withChild(label("Key", new HtmlTag("code").withInnerText(node.key())))
            .withChild(label("Type", nodeTypeBadge(node.type())))
            .withChild(label("Plan", new HtmlTag("span").withInnerText(nn(node.planId()))))
            .withChild(label("Input Name", new HtmlTag("span").withInnerText(nn(node.inputName())))));

        Div adapters = new Div().withClass("orch-form-stack");
        adapters.withChild(new HtmlTag("h4").withInnerText("Adapter Chain"));
        adapters.withChild(new Paragraph(switch (node.type()) {
            case LOG -> "Logging adapter: consumes routed values and materializes evidence/log output.";
            case COPY -> "Fan-out adapter: copy/pass-through routes can send one output to multiple receivers.";
            case REPORT -> "Report adapter: materializes declared outputs as report artifacts.";
            default -> "Use LOG, COPY, REPORT, MAP_OUTPUT, PASS_THROUGH, and LOG routes to build adapter chains.";
        }));
        panel.withChild(adapters);

        panel.withChild(routeSummary("Incoming Routes", wf.incomingRoutes(node.key())));
        panel.withChild(routeSummary("Outgoing Routes", wf.outgoingRoutes(node.key())));
        panel.withChild(workflowNodeSchemaSummary(node));
        return panel;
    }

    private Component routeSummary(String title, List<WorkflowRoute> routes) {
        Div container = new Div().withClass("orch-form-stack");
        container.withChild(new HtmlTag("h4").withInnerText(title));
        if (routes.isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty").withInnerText("None"));
            return container;
        }
        for (WorkflowRoute route : routes) {
            container.withChild(new Div().withClass("orch-meta")
                .withInnerText(route.id() + ": "
                    + nn(route.fromNodeKey()) + "." + nn(route.fromOutputName())
                    + " -> " + nn(route.toNodeKey()) + "." + nn(route.toInputName())
                    + " [" + route.routeType().wireName() + "]"));
        }
        return container;
    }

    private HtmlTag nodeTypeBadge(WorkflowNodeType type) {
        String css = type.isGate() ? "orch-status-chip active" : "orch-chip";
        return new HtmlTag("span").withClass(css).withInnerText(type.wireName());
    }

    private HtmlTag routeTypeBadge(WorkflowRouteType type) {
        return new HtmlTag("span").withClass("orch-chip").withInnerText(type.wireName());
    }

    private String truncateId(String id) {
        return id.length() > 12 ? id.substring(0, 12) + "..." : id;
    }

    private Component workflowSubmitToAgentPanel(String workflowId) {
        WorkflowDefinition wf;
        try {
            wf = workflowService.getDefinition(workflowId);
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Workflow not found.");
        }

        Div panel = new Div().withClass("orch-panel");
        panel.withChild(Header.H2("Submit to Agent"));
        WorkflowValidator.ValidationResult workflowValidation = workflowService.validateGraph(wf);
        if (!workflowValidation.valid()) {
            panel.withChild(new Div().withClass("orch-status orch-status-error")
                .withInnerText("Fix validation errors before submitting: "
                    + String.join("; ", workflowValidation.errors())));
            return panel;
        }

        Form form = Form.create()
            .withHxPost("/workflows/_submit/" + workflowId)
            .withHxTarget("#workflow-submit-result")
            .withHxSwap("innerHTML");

        List<AgentProfile> agents = agentProfileService.list();
        Select agentSelect = Select.create("agentId");
        for (var agent : agents) {
            if (agent.status() != null && !"DISABLED".equals(agent.status().name())) {
                agentSelect.addOption(agent.id(),
                    (agent.name() != null ? agent.name() : agent.id()) +
                    " (" + (agent.defaultModel() != null ? agent.defaultModel() : "no model") + ")",
                    false);
            }
        }
        form.withChild(new Div().withClass("orch-form-stack")
            .withChild(label("Agent", agentSelect))
            .withChild(label("Model Override", modelSelectWithCurrent(
                "modelOverride", null, chatService.availableModels())))
            .withChild(label("Priority", TextInput.number("priority")
                .withValue("9").withMin("0").withMax("100")))
            .withChild(label("Workspace ID", TextInput.create("workspaceId")
                .withPlaceholder("optional"))));

        form.withChild(Button.create("Submit").withClass("orch-primary").withAttribute("type", "submit"));
        panel.withChild(form);
        panel.withChild(new Div().withId("workflow-submit-result"));

        return panel;
    }

    private Component workflowSubmitResultFragment(WorkAssignment assignment, WorkflowDefinition wf) {
        return new Div().withClass("orch-panel")
            .withChild(Header.H2("Assignment Created"))
            .withChild(new Div().withClass("orch-form-stack")
                .withChild(new Paragraph("Workflow submitted to agent for execution."))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Assignment ID: "))
                    .withChild(new HtmlTag("code").withInnerText(assignment.id())))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Agent: "))
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "/agents/" + escapeAttr(assignment.agentId()))
                        .withInnerText(assignment.agentId())))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Workflow: "))
                    .withInnerText(wf.title() != null ? wf.title() : wf.id()))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Status: "))
                    .withInnerText(assignment.status() != null ? assignment.status().name() : "QUEUED"))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("strong").withInnerText("Priority: "))
                    .withInnerText(String.valueOf(assignment.priority())))
                .withChild(new HtmlTag("p")
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "/agents/" + escapeAttr(assignment.agentId()))
                        .withInnerText("View agent status"))));
    }

    // ════════════════════════════════════════════════════════════════
    //  Jobs (HTMX-first)
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/jobs")
    @ResponseBody
    public String jobs() {
        Component body = new Div()
            .withId("jobs-page")
            .withAttribute("data-orchestration-page", "jobs")
            .withChild(Header.H1("Jobs"))
            .withChild(new Paragraph("Ordered orchestration items coordinating plans and workflows with agent submission."))
            .withChild(new Div().withClass("browser-layout browser-layout-wide")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Button.create("New Job")
                            .withClass("orch-primary")
                            .hxGet("/jobs/_editor/_new")
                            .hxTarget("#job-editor-container")
                            .hxSwap("innerHTML")))
                    .withChild(jobsAgentFilter())
                    .withChild(new Div().withId("job-list")
                        .hxGet("/jobs/_list")
                        .hxTrigger("load")
                        .hxInclude("#jobs-agent-select")
                        .hxSwap("innerHTML")
                        .withClass("entity-list")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("job-editor-container")
                        .withChild(jobEditorEmptyState()))))
            .withChild(moduleScript(DASHBOARD_JS));
        return renderPage(body, "/jobs");
    }

    @GetMapping("/jobs/{jobId}")
    @ResponseBody
    public String jobDetail(@PathVariable String jobId) {
        Component body = new Div()
            .withId("jobs-page")
            .withAttribute("data-orchestration-page", "jobs")
            .withChild(Header.H1("Jobs"))
            .withChild(new Paragraph("Ordered orchestration items coordinating plans and workflows with agent submission."))
            .withChild(new Div().withClass("browser-layout browser-layout-wide")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Button.create("New Job")
                            .withClass("orch-primary")
                            .hxGet("/jobs/_editor/_new")
                            .hxTarget("#job-editor-container")
                            .hxSwap("innerHTML")))
                    .withChild(jobsAgentFilter())
                    .withChild(new Div().withId("job-list")
                        .hxGet("/jobs/_list")
                        .hxTrigger("load")
                        .hxInclude("#jobs-agent-select")
                        .hxSwap("innerHTML")
                        .withClass("entity-list")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("job-editor-container")
                        .hxGet("/jobs/_editor/" + escapeAttr(jobId))
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withChild(loadingPlaceholder()))))
            .withChild(moduleScript(DASHBOARD_JS));
        return renderPage(body, "/jobs/" + jobId);
    }

    private Component jobEditorEmptyState() {
        return new Div().withClass("orch-panel")
            .withChild(new Div().withClass("dashboard-empty")
                .withInnerText("Select a job from the list or create a new one."));
    }

    private Component jobsAgentFilter() {
        List<AgentProfile> agents = agentProfileService.list();
        Select select = Select.create("agentId").withId("jobs-agent-select");
        select.addOption("", "All agents", true);
        for (var agent : agents) {
            select.addOption(agent.id(), agent.name() != null ? agent.name() : agent.id(), false);
        }
        select.withAttribute("hx-get", "/jobs/_list");
        select.withAttribute("hx-trigger", "change");
        select.withAttribute("hx-target", "#job-list");
        select.withAttribute("hx-swap", "innerHTML");
        return new Div().withClass("entity-toolbar").withChild(select);
    }

    // ── Job HTMX partials ──

    @GetMapping("/jobs/_list")
    @ResponseBody
    public String jobListFragment(@RequestParam(value = "agentId", required = false) String agentId) {
        try {
            List<JobDefinition> jobs = jobService.listDefinitions(
                StringUtils.hasText(agentId) ? agentId : null, null, null);
            if (jobs.isEmpty()) {
                return new Div().withClass("tool-item").withInnerText("No jobs.").render();
            }
            Div list = new Div();
            for (var job : jobs) {
                list.withChild(new HtmlTag("button")
                    .withClass("tool-item")
                    .hxGet("/jobs/_editor/" + escapeAttr(job.id()))
                    .hxTarget("#job-editor-container")
                    .hxSwap("innerHTML")
                    .withChild(new HtmlTag("strong").withInnerText(
                        job.title() != null ? job.title() : "Untitled"))
                    .withChild(new HtmlTag("br"))
                    .withChild(statusBadgeHtml(job.status()))
                    .withChild(new HtmlTag("span").withInnerText(
                        " " + (job.ownerAgentId() != null ? job.ownerAgentId() : "no owner"))));
            }
            return list.render();
        } catch (Exception e) {
            return new Div().withClass("tool-item").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/jobs/_editor/_new")
    @ResponseBody
    public String newJobEditor() {
        return jobEditorFragment(null).render();
    }

    @GetMapping("/jobs/_editor/{jobId}")
    @ResponseBody
    public String jobEditor(@PathVariable String jobId) {
        try {
            JobDefinition job = jobService.getDefinition(jobId);
            return jobEditorFragment(job).render();
        } catch (Exception e) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("dashboard-empty")
                    .withInnerText("Job not found: " + escapeAttr(jobId)))
                .render();
        }
    }

    @PostMapping("/jobs/_editor")
    @ResponseBody
    public String createJob(@RequestParam Map<String, String> params) {
        try {
            String title = params.getOrDefault("title", "").trim();
            if (title.isBlank()) {
                return new Div().withClass("orch-panel")
                    .withChild(new Div().withClass("orch-status").withInnerText("Title is required."))
                    .render();
            }
            JobDefinition created = jobService.saveDefinition(new JobDefinition(
                null, // id - auto-generated
                nn(params.get("ownerAgentId")),
                nn(params.get("projectId")),
                null, // workspaceId - auto-managed
                "DRAFT",
                title,
                nn(params.get("summary")),
                List.of(), // items start empty
                nn(params.get("promptProfile")),
                nn(params.get("model")),
                null, // settingsOverrideJson
                null, null // timestamps
            ));
            return jobEditorFragment(created).render();
        } catch (Exception e) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()))
                .render();
        }
    }

    @PutMapping("/jobs/_editor/{jobId}")
    @ResponseBody
    public String updateJob(@PathVariable String jobId, @RequestParam Map<String, String> params) {
        try {
            JobDefinition current = jobService.getDefinition(jobId);
            JobDefinition updated = new JobDefinition(
                jobId,
                params.containsKey("ownerAgentId") ? nn(params.get("ownerAgentId")) : current.ownerAgentId(),
                params.containsKey("projectId") ? nn(params.get("projectId")) : current.projectId(),
                current.workspaceId(),
                params.containsKey("status") ? nn(params.get("status")) : current.status(),
                params.containsKey("title") ? nn(params.get("title")) : current.title(),
                params.containsKey("summary") ? nn(params.get("summary")) : current.summary(),
                current.items(),
                params.containsKey("promptProfile") ? nn(params.get("promptProfile")) : current.promptProfile(),
                params.containsKey("model") ? nn(params.get("model")) : current.model(),
                current.settingsOverrideJson(),
                current.createdAt(),
                current.updatedAt()
            );
            jobService.saveDefinition(updated);
            return jobEditorFragment(jobService.getDefinition(jobId)).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @DeleteMapping("/jobs/{jobId}")
    @ResponseBody
    public String deleteJob(@PathVariable String jobId) {
        try {
            jobService.deleteDefinition(jobId);
            // Return empty to trigger client-side removal
            return "";
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Job item CRUD ──

    @PostMapping("/jobs/_editor/{jobId}/items")
    @ResponseBody
    public String addJobItem(@PathVariable String jobId, @RequestParam Map<String, String> params) {
        try {
            JobWorkItem item = jobItemFromParams(params, 0);
            jobService.addItem(jobId, item);
            JobDefinition job = jobService.getDefinition(jobId);
            return jobEditorFragment(job).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @DeleteMapping("/jobs/_editor/{jobId}/items/{index}")
    @ResponseBody
    public String removeJobItem(@PathVariable String jobId, @PathVariable int index) {
        try {
            JobDefinition job = jobService.getDefinition(jobId);
            if (index >= 0 && index < job.items().size()) {
                String key = job.items().get(index).key();
                jobService.deleteItem(jobId, key);
            }
            JobDefinition updated = jobService.getDefinition(jobId);
            return jobEditorFragment(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @PutMapping("/jobs/_editor/{jobId}/items/{index}")
    @ResponseBody
    public String updateJobItem(@PathVariable String jobId, @PathVariable int index,
                                @RequestParam Map<String, String> params) {
        try {
            JobDefinition job = jobService.getDefinition(jobId);
            if (index >= 0 && index < job.items().size()) {
                String key = job.items().get(index).key();
                JobWorkItem item = jobItemFromParams(params, index);
                jobService.updateItem(jobId, key, item);
            }
            JobDefinition updated = jobService.getDefinition(jobId);
            return jobEditorFragment(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/jobs/_editor/_plan-inputs")
    @ResponseBody
    public String jobPlanInputsGuidance(@RequestParam(value = "planId", required = false) String planId) {
        if (!StringUtils.hasText(planId)) {
            return new Div().withId("plan-inputs-guidance")
                .withClass("orch-meta")
                .render();
        }
        try {
            PlanDefinition plan = planService.getTask(planId);
            List<PlanFieldDefinition> required = plan.inputs().stream()
                .filter(PlanFieldDefinition::required)
                .toList();
            if (required.isEmpty()) {
                return new Div().withId("plan-inputs-guidance")
                    .withClass("orch-meta")
                    .withInnerText("Plan has no required inputs.")
                    .render();
            }
            Div guidance = new Div().withId("plan-inputs-guidance").withClass("orch-meta");
            guidance.withChild(new HtmlTag("strong").withInnerText("Required bindings for " + plan.title() + ":"));
            for (PlanFieldDefinition input : required) {
                String desc = input.name() + " (" + input.type().wireName() + ")";
                if (StringUtils.hasText(input.description())) {
                    desc += " — " + input.description();
                }
                guidance.withChild(new HtmlTag("span").withInnerText(desc));
            }
            guidance.withChild(new HtmlTag("code").withInnerText(
                "Example bindingsJson: " + buildBindingExample(required)));
            return guidance.render();
        } catch (Exception e) {
            return new Div().withId("plan-inputs-guidance")
                .withClass("orch-meta orch-error")
                .withInnerText("Plan not found: " + planId)
                .render();
        }
    }

    private String buildBindingExample(List<PlanFieldDefinition> inputs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(inputs.get(i).name()).append("\": \"...\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private JobWorkItem jobItemFromParams(Map<String, String> params, int fallbackOrder) {
        String typeStr = params.getOrDefault("itemType", "PLAN").toUpperCase();
        JobWorkItemType type;
        try {
            type = JobWorkItemType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            type = JobWorkItemType.PLAN;
        }
        String planId = type == JobWorkItemType.PLAN ? nn(params.get("planId")) : null;
        Map<String, Object> bindings = parseJsonMap(params.get("bindingsJson"));

        // Validate required bindings if plan has required inputs
        if (StringUtils.hasText(planId)) {
            try {
                PlanDefinition plan = planService.getTask(planId);
                List<PlanFieldDefinition> required = plan.inputs().stream()
                    .filter(PlanFieldDefinition::required)
                    .toList();
                List<String> missing = required.stream()
                    .filter(f -> !bindings.containsKey(f.name()) || bindings.get(f.name()) == null
                        || (bindings.get(f.name()) instanceof String s && !StringUtils.hasText(s)))
                    .map(PlanFieldDefinition::name)
                    .toList();
                if (!missing.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Missing required bindings for plan '" + plan.title() + "': " + String.join(", ", missing));
                }
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
        }

        return new JobWorkItem(
            nn(params.get("key")),
            type,
            planId,
            type == JobWorkItemType.WORKFLOW ? nn(params.get("workflowId")) : null,
            bindings,
            fallbackOrder,
            nn(params.get("modelOverride")),
            parseIntOrNull(params.get("priority"))
        );
    }

    // ── Job submit to agent ──

    @GetMapping("/jobs/_submit-form/{jobId}")
    @ResponseBody
    public String jobSubmitForm(@PathVariable String jobId) {
        Div panel = new Div().withClass("orch-panel");
        panel.withChild(Header.H2("Submit to Agent"));

        Form form = Form.create()
            .withHxPost("/jobs/_submit/" + jobId)
            .withHxTarget("#job-submit-result")
            .withHxSwap("innerHTML");

        List<AgentProfile> agents = agentProfileService.list();
        Select agentSelect = Select.create("agentId");
        for (var agent : agents) {
            if (agent.status() != null && !"DISABLED".equals(agent.status().name())) {
                agentSelect.addOption(agent.id(),
                    (agent.name() != null ? agent.name() : agent.id()) +
                    " (" + (agent.defaultModel() != null ? agent.defaultModel() : "no model") + ")",
                    false);
            }
        }
        form.withChild(new Div().withClass("orch-form-stack")
            .withChild(label("Agent", agentSelect))
            .withChild(label("Model Override", TextInput.create("modelOverride")
                .withPlaceholder("optional")))
            .withChild(label("Priority", TextInput.number("priority")
                .withValue("0").withMin("0").withMax("100"))));

        form.withChild(Button.create("Submit").withClass("orch-primary")
            .withAttribute("type", "submit"));
        panel.withChild(form);
        panel.withChild(new Div().withId("job-submit-result"));

        return panel.render();
    }

    @PostMapping("/jobs/_submit/{jobId}")
    @ResponseBody
    public String submitJob(@PathVariable String jobId, @RequestParam Map<String, String> params) {
        try {
            JobDefinition job = jobService.getDefinition(jobId);
            String agentId = params.getOrDefault("agentId", "").trim();
            if (agentId.isBlank()) {
                return new Div().withClass("orch-status")
                    .withInnerText("Agent is required.").render();
            }

            AssignmentRequest request = new AssignmentRequest(
                agentId,
                jobId,
                null, // jobItemId
                AssignmentType.JOB_RUN,
                parseIntOrNull(params.get("priority")),
                nn(params.get("modelOverride")),
                job.workspaceId(),
                Map.of("jobId", jobId)
            );
            WorkAssignment assignment = assignmentService.create(request);

            return new Div().withClass("orch-panel")
                .withChild(Header.H3("Assignment Created"))
                .withChild(new Div().withClass("orch-meta")
                    .withChild(new HtmlTag("span").withInnerText("ID: " + assignment.id()))
                    .withChild(new HtmlTag("span").withInnerText("Agent: " +
                        (assignment.agentId() != null ? assignment.agentId() : "—")))
                    .withChild(new HtmlTag("span").withInnerText("Status: " +
                        (assignment.status() != null ? assignment.status().name() : "—")))
                    .withChild(new HtmlTag("span").withInnerText("Priority: " + assignment.priority())))
                .render();
        } catch (Exception e) {
            return new Div().withClass("orch-status")
                .withInnerText("Error submitting job: " + e.getMessage()).render();
        }
    }

    // ── Job detail fragments ──

    @GetMapping("/jobs/_detail/{jobId}/events")
    @ResponseBody
    public String jobEventsFragment(@PathVariable String jobId) {
        try {
            var events = jobService.listRuns(jobId).stream()
                .map(run -> "JOB_RUN_" + run.status().name() + " | " +
                    (run.createdAt() != null ? run.createdAt().toString() : ""))
                .toList();
            if (events.isEmpty()) {
                return new Div().withClass("dashboard-empty")
                    .withInnerText("No events.").render();
            }
            Div list = new Div();
            for (var event : events) {
                list.withChild(new Div().withClass("dashboard-side-item")
                    .withChild(new HtmlTag("span").withInnerText(event)));
            }
            return list.render();
        } catch (Exception e) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/jobs/_detail/{jobId}/outputs")
    @ResponseBody
    public String jobOutputsFragment(@PathVariable String jobId) {
        try {
            List<RunOutputArtifact> artifacts = queryOutputs(null, jobId, null, null, null, 40);
            if (artifacts.isEmpty()) {
                return new Div().withClass("dashboard-empty")
                    .withInnerText("No outputs.").render();
            }
            Div list = new Div();
            for (var o : artifacts) {
                list.withChild(new Div().withClass("dashboard-side-item")
                    .withChild(new HtmlTag("span").withClass("dashboard-side-item-name")
                        .withInnerText(o.outputName() != null ? o.outputName() :
                            o.artifactType() != null ? o.artifactType() : "output"))
                    .withChild(new HtmlTag("span").withClass("dashboard-side-item-meta")
                        .withInnerText(o.runId() != null ? o.runId() : "")));
            }
            return list.render();
        } catch (Exception e) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Job editor rendering ──

    private Component jobEditorFragment(JobDefinition job) {
        boolean isNew = job == null;
        String jobId = isNew ? null : job.id();

        Div container = new Div().withClass("orch-panel job-editor");
        container.withChild(Header.H2(isNew ? "New Job" : "Job: " +
            (job.title() != null ? job.title() : job.id())));

        Form form = Form.create();
        if (isNew) {
            form.withHxPost("/jobs/_editor");
        } else {
            form.withHxPut("/jobs/_editor/" + jobId);
        }
        form.withHxTarget("#job-editor-container");
        form.withHxSwap("innerHTML");

        // Scalar fields
        form.withChild(new Div().withClass("orch-form-stack")
            .withChild(label("Title", TextInput.create("title")
                .withId("job-title")
                .withValue(isNew ? "" : nn(job.title()))))
            .withChild(label("Summary", TextArea.create("summary")
                .withId("job-summary").withRows(3)
                .withValue(isNew ? "" : nn(job.summary()))))
            .withChild(label("Owner Agent", agentSelect("ownerAgentId",
                isNew ? "" : nn(job.ownerAgentId())).withId("job-owner-agent")))
            .withChild(label("Project ID", TextInput.create("projectId")
                .withId("job-project")
                .withValue(isNew ? "" : nn(job.projectId()))))
            .withChild(label("Status", TextInput.create("status")
                .withId("job-status")
                .withValue(isNew ? "DRAFT" : nn(job.status())))));

        // Worktype, Model
        String currentWorktype = job != null ? job.promptProfile() : null;
        form.withChild(new Div().withClass("orch-form-grid")
            .withChild(label("Manager Type", worktypeSelect(currentWorktype)))
            .withChild(label("Default Model", modelSelect("model")
                .withId("job-model"))));

        // Advanced metadata for existing jobs
        if (!isNew) {
            Div advanced = new Div().withId("job-advanced").withClass("field-group");
            advanced.withChild(new HtmlTag("details")
                .withChild(new HtmlTag("summary").withInnerText("Advanced"))
                .withChild(new Div().withClass("orch-form-grid")
                    .withChild(label("ID", new HtmlTag("code").withInnerText(job.id())))
                    .withChild(label("Status", statusBadgeHtml(job.status())))
                    .withChild(label("Workspace ID", new HtmlTag("code")
                        .withInnerText(job.workspaceId() != null ? job.workspaceId() : "—")))
                    .withChild(label("Created", new HtmlTag("span")
                        .withInnerText(job.createdAt() != null ? job.createdAt().toString() : "—")))));
            form.withChild(advanced);
        }

        // Item section (only for existing jobs)
        if (!isNew) {
            form.withChild(sectionHeader("Ordered Items",
                "Plans and workflows executed in sequence."));
            form.withChild(new Div().withId("job-items-section")
                .withChild(jobItemsSection(job)));
            form.withChild(Button.create("Add Item")
                .withAttribute("hx-post", "/jobs/_editor/" + jobId + "/items")
                .withAttribute("hx-target", "#job-editor-container")
                .withAttribute("hx-swap", "innerHTML")
                .withAttribute("hx-include", "#job-items-new-form"));

            // Plan input guidance (populated when planId is entered)
            form.withChild(new Div().withId("plan-inputs-guidance").withClass("orch-meta"));

            // Inline add-item form
            form.withChild(new Div().withId("job-items-new-form").withClass("field-row")
                .withChild(TextInput.create("key").withAttribute("placeholder", "item key")
                    .withAttribute("style", "max-width:100px"))
                .withChild(jobItemTypeSelect("itemType"))
                .withChild(TextInput.create("planId").withAttribute("placeholder", "plan ID")
                    .withAttribute("style", "max-width:120px")
                    .withAttribute("hx-get", "/jobs/_editor/_plan-inputs")
                    .withAttribute("hx-trigger", "change delay:500ms")
                    .withAttribute("hx-target", "#plan-inputs-guidance")
                    .withAttribute("hx-swap", "innerHTML")
                    .withAttribute("hx-include", "this"))
                .withChild(TextInput.create("workflowId").withAttribute("placeholder", "workflow ID")
                    .withAttribute("style", "max-width:120px"))
                .withChild(TextInput.create("bindingsJson").withAttribute("placeholder", "bindings JSON")
                    .withAttribute("style", "min-width:200px;max-width:240px"))
                .withChild(TextInput.create("modelOverride").withAttribute("placeholder", "model")
                    .withAttribute("style", "max-width:80px"))
                .withChild(TextInput.number("priority").withAttribute("placeholder", "pri")
                    .withAttribute("value", "0").withAttribute("style", "max-width:50px;min-width:50px")));

            form.withChild(sectionHeader("Recurrence", "Optional cron schedule for this job."));
            form.withChild(jobRecurrenceSection(jobId));
        }

        // Action buttons
        Div actions = new Div().withClass("tool-actions");
        actions.withChild(Button.create("Save").withClass("orch-primary")
            .withAttribute("type", "submit"));
        if (!isNew) {
            actions.withChild(Button.create("Start Run")
                .withClass("orch-primary")
                .withAttribute("hx-post", "/jobs/_runs/" + jobId + "/start")
                .withAttribute("hx-target", "#job-runs-panel")
                .withAttribute("hx-swap", "innerHTML"));
            actions.withChild(Button.create("Submit to Agent")
                .withClass("orch-primary")
                .withAttribute("hx-get", "/jobs/_submit-form/" + jobId)
                .withAttribute("hx-target", "#job-submit-container")
                .withAttribute("hx-swap", "innerHTML"));
            actions.withChild(Button.create("Delete")
                .withAttribute("hx-delete", "/jobs/" + jobId)
                .withAttribute("hx-confirm", "Delete this job?")
                .withAttribute("hx-target", "#job-editor-container")
                .withAttribute("hx-swap", "innerHTML"));
        }
        form.withChild(actions);
        container.withChild(form);

        if (!isNew) {
            // Submit form container
            container.withChild(new Div().withId("job-submit-container"));

            // Side panels: events and outputs
            container.withChild(new Div().withClass("entity-detail-side")
                .withChild(new Div().withClass("orch-panel")
                    .withChild(Header.H3("Runs"))
                    .withChild(new Div().withId("job-runs-panel")
                        .hxGet("/jobs/_runs/" + jobId)
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("orch-panel")
                    .withChild(Header.H3("Recent Outputs"))
                    .withChild(new Div().withId("job-outputs-panel")
                        .hxGet("/jobs/_detail/" + jobId + "/outputs")
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("orch-panel")
                    .withChild(Header.H3("Run Events"))
                    .withChild(new Div().withId("job-events-panel")
                        .hxGet("/jobs/_detail/" + jobId + "/events")
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withChild(loadingPlaceholder()))));
        }

        return container;
    }

    private Component jobRecurrenceSection(String jobId) {
        JobRecurrence recurrence;
        try {
            recurrence = jobService.getRecurrence(jobId).orElse(null);
        } catch (Exception ignored) {
            recurrence = null;
        }
        Form form = Form.create()
            .withHxPost("/jobs/_recurrence/" + jobId)
            .withHxTarget("#job-editor-container")
            .withHxSwap("innerHTML");
        form.withChild(new Div().withClass("orch-form-grid")
            .withChild(label("Cron Expression", TextInput.create("cronExpression")
                .withValue(recurrence != null ? nn(recurrence.cronExpression()) : "")))
            .withChild(label("Timezone", TextInput.create("timezone")
                .withValue(recurrence != null ? nn(recurrence.timezone()) : "UTC")))
            .withChild(label("Next Fire Time", TextInput.create("nextFireTime")
                .withValue(recurrence != null && recurrence.nextFireTime() != null ? recurrence.nextFireTime().toString() : ""))));
        form.withChild(Button.create("Save Recurrence").withAttribute("type", "submit"));
        return form;
    }

    @PostMapping("/jobs/_recurrence/{jobId}")
    @ResponseBody
    public String saveJobRecurrence(@PathVariable String jobId, @RequestParam Map<String, String> params) {
        jobService.setRecurrence(
            jobId,
            required(params.get("cronExpression"), "cronExpression is required"),
            params.getOrDefault("timezone", "UTC"),
            StringUtils.hasText(params.get("nextFireTime")) ? Instant.parse(params.get("nextFireTime")) : null
        );
        return jobEditorFragment(jobService.getDefinition(jobId)).render();
    }

    @GetMapping("/jobs/_runs/{jobId}")
    @ResponseBody
    public String jobRunsFragment(@PathVariable String jobId) {
        List<JobRun> runs = jobService.listRuns(jobId);
        if (runs.isEmpty()) {
            return new Div().withClass("dashboard-empty").withInnerText("No runs yet.").render();
        }
        Table table = Table.create().withHeaders("Run", "Status", "Created", "Action").withClass("dashboard-table");
        for (JobRun run : runs) {
            Component action = run.status() != null && !run.status().isTerminal()
                ? Button.create("Cancel")
                    .withAttribute("hx-post", "/jobs/_runs/" + run.id() + "/cancel")
                    .withAttribute("hx-target", "#job-runs-panel")
                    .withAttribute("hx-swap", "innerHTML")
                : new HtmlTag("span").withInnerText("—");
            table.addRow(
                new HtmlTag("code").withInnerText(run.id()),
                statusBadgeHtml(run.status() != null ? run.status().name() : "—"),
                new HtmlTag("span").withInnerText(run.createdAt() != null ? formatSince(run.createdAt()) : "—"),
                action
            );
        }
        return table.render();
    }

    @PostMapping("/jobs/_runs/{jobId}/start")
    @ResponseBody
    public String startJobRun(@PathVariable String jobId) {
        jobService.startRun(jobId);
        return jobRunsFragment(jobId);
    }

    @PostMapping("/jobs/_runs/{runId}/cancel")
    @ResponseBody
    public String cancelJobRun(@PathVariable String runId) {
        JobRun run = jobService.cancelRun(runId);
        return jobRunsFragment(run.jobId());
    }

    private Component jobItemsSection(JobDefinition job) {
        Div container = new Div().withClass("field-list");
        if (job.items().isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty")
                .withInnerText("No items defined."));
            return container;
        }
        for (int i = 0; i < job.items().size(); i++) {
            JobWorkItem item = job.items().get(i);
            Div row = new Div().withClass("field-row");
            row.withChild(new HtmlTag("span").withInnerText((i + 1) + "."));
            row.withChild(new HtmlTag("span").withClass("orch-chip")
                .withInnerText(item.type() != null ? item.type().name() : "—"));
            row.withChild(new HtmlTag("span").withInnerText(
                item.key() != null ? item.key() : ""));
            String refId = item.type() == JobWorkItemType.PLAN ? item.planId() :
                item.type() == JobWorkItemType.WORKFLOW ? item.workflowId() : "—";
            row.withChild(new HtmlTag("span").withInnerText(
                refId != null ? refId : "—"));
            row.withChild(new HtmlTag("span").withInnerText(
                item.modelOverride() != null ? item.modelOverride() : ""));
            row.withChild(new HtmlTag("span").withInnerText(
                "pri:" + (item.priority() != null ? item.priority() : 0)));
            row.withChild(new HtmlTag("button")
                .withClass("remove-field")
                .withAttribute("type", "button")
                .withAttribute("hx-delete", "/jobs/_editor/" + job.id() + "/items/" + i)
                .withAttribute("hx-target", "#job-editor-container")
                .withAttribute("hx-swap", "innerHTML")
                .withInnerText("x"));
            container.withChild(row);
        }
        return container;
    }

    private Select jobItemTypeSelect(String name) {
        Select select = Select.create(name);
        select.addOption("PLAN", "PLAN", true);
        select.addOption("WORKFLOW", "WORKFLOW", false);
        return select;
    }

    private int parseIntOrNull(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, Object> parsePlanInputValues(PlanDefinition plan, Map<String, String> params) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (PlanFieldDefinition input : plan.inputs()) {
            String raw = params.get("input_" + input.name());
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            values.put(input.name(), coercePlanInput(input, raw));
        }
        return values;
    }

    private Object coercePlanInput(PlanFieldDefinition field, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!field.array()) {
            return coerceScalar(field.type(), trimmed);
        }
        if (field.type() == PlanFieldType.JSON) {
            try {
                Object parsed = JSON.readValue(trimmed, Object.class);
                if (parsed instanceof List<?> list) {
                    return list;
                }
            } catch (Exception ignored) {
                // fall through to token split
            }
        }
        String[] parts = trimmed.split("\\r?\\n|\\s*,\\s*");
        List<Object> result = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            result.add(coerceScalar(field.type(), part.trim()));
        }
        return result;
    }

    private Object coerceScalar(PlanFieldType type, String value) {
        return switch (type) {
            case NUMBER -> {
                try {
                    yield value.contains(".") ? Double.parseDouble(value) : Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    yield value;
                }
            }
            case JSON -> {
                try {
                    yield JSON.readValue(value, Object.class);
                } catch (Exception ignored) {
                    yield value;
                }
            }
            case STRING, USER_MESSAGE, FILE_PATH -> value;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            Object parsed = JSON.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception ignored) {
            // return empty map below
        }
        return Map.of();
    }

    // ════════════════════════════════════════════════════════════════
    //  Projects (HTMX-first)
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/projects")
    @ResponseBody
    public String projects() {
        Component body = new Div()
            .withId("projects-page")
            .withAttribute("data-orchestration-page", "projects")
            .withChild(Header.H1("Projects"))
            .withChild(new Paragraph("Top-level tracking and data-space wrappers with owner agents."))
            .withChild(new Div().withClass("browser-layout browser-layout-wide")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Button.create("New Project")
                            .withClass("orch-primary")
                            .hxGet("/projects/_editor/_new")
                            .hxTarget("#project-editor-container")
                            .hxSwap("innerHTML")))
                    .withChild(new Div().withId("project-list")
                        .hxGet("/projects/_list")
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withClass("entity-list")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("project-editor-container")
                        .withChild(projectEditorEmptyState()))))
            .withChild(moduleScript(PROJECTS_JS));
        return renderPage(body, "/projects");
    }

    private Component projectEditorEmptyState() {
        return new Div().withClass("orch-panel")
            .withChild(new Div().withClass("dashboard-empty")
                .withInnerText("Select a project from the list or create a new one."));
    }

    @GetMapping("/projects/_list")
    @ResponseBody
    public String projectsListFragment() {
        List<Project> projects = projectService.listProjects();
        if (projects.isEmpty()) {
            return new Div().withClass("tool-item").withInnerText("No projects.").render();
        }
        Div list = new Div();
        for (var p : projects) {
            list.withChild(new HtmlTag("button")
                .withClass("tool-item")
                .hxGet("/projects/_editor/" + escapeAttr(p.id()))
                .hxTarget("#project-editor-container")
                .hxSwap("innerHTML")
                .withChild(new HtmlTag("strong").withInnerText(p.name() != null ? p.name() : "Untitled"))
                .withChild(new HtmlTag("br"))
                .withChild(new HtmlTag("span").withInnerText(
                    p.description() != null ? p.description() : "No description")));
        }
        return list.render();
    }

    // ── Project HTMX partials ──

    @GetMapping("/projects/_editor/_new")
    @ResponseBody
    public String newProjectEditor() {
        return projectEditorFragment(null).render();
    }

    @GetMapping("/projects/_editor/{projectId}")
    @ResponseBody
    public String projectEditor(@PathVariable String projectId) {
        try {
            Project project = projectService.getProject(projectId);
            return projectEditorFragment(project).render();
        } catch (Exception e) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("dashboard-empty")
                    .withInnerText("Project not found: " + escapeAttr(projectId)))
                .render();
        }
    }

    @PostMapping("/projects/_editor")
    @ResponseBody
    public String createProject(@RequestParam Map<String, String> params) {
        try {
            String name = params.getOrDefault("name", "").trim();
            if (name.isBlank()) {
                return new Div().withClass("orch-panel")
                    .withChild(new Div().withClass("orch-status").withInnerText("Name is required."))
                    .render();
            }
            String ownerAgentId = params.getOrDefault("ownerAgentId", "").trim();
            if (ownerAgentId.isBlank()) {
                return new Div().withClass("orch-panel")
                    .withChild(new Div().withClass("orch-status")
                        .withInnerText("Owner agent is required. " +
                            (agentProfileService.list().isEmpty() ?
                                "No agents exist — create an agent first." : "")))
                    .render();
            }
            Project created = projectService.createProject(
                name, nn(params.get("description")), ownerAgentId,
                nn(params.get("gitRepoUrl")));
            return projectEditorFragment(created).render();
        } catch (Exception e) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()))
                .render();
        }
    }

    @PutMapping("/projects/_editor/{projectId}")
    @ResponseBody
    public String updateProject(@PathVariable String projectId, @RequestParam Map<String, String> params) {
        try {
            Project updated = projectService.updateProject(
                projectId,
                params.containsKey("name") ? nn(params.get("name")) : null,
                params.containsKey("description") ? nn(params.get("description")) : null,
                params.containsKey("gitRepoUrl") ? nn(params.get("gitRepoUrl")) : null,
                params.containsKey("promptProfile") ? nn(params.get("promptProfile")) : null,
                params.containsKey("model") ? nn(params.get("model")) : null,
                null // settingsOverrideJson
            );
            return projectEditorFragment(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseBody
    public String deleteProject(@PathVariable String projectId) {
        try {
            projectService.deleteProject(projectId);
            return "";
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Project detail fragments ──

    @GetMapping("/projects/_detail/{projectId}")
    @ResponseBody
    public String projectDetailFragment(@PathVariable String projectId) {
        try {
            Project project = projectService.getProject(projectId);
            return projectEditorFragment(project).render();
        } catch (Exception e) {
            return new Div().withClass("orch-panel")
                .withChild(new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()))
                .render();
        }
    }

    @GetMapping("/projects/_detail/{projectId}/jobs")
    @ResponseBody
    public String projectJobsFragment(@PathVariable String projectId) {
        try {
            List<JobDefinition> jobs = jobService.listDefinitions(null, projectId, null);
            if (jobs.isEmpty()) {
                return new Div().withClass("dashboard-empty")
                    .withInnerText("No active jobs.").render();
            }
            Div list = new Div();
            for (var job : jobs) {
                list.withChild(new Div().withClass("orch-row")
                    .withChild(new HtmlTag("a")
                        .withAttribute("href", "#")
                        .withAttribute("hx-get", "/jobs/_editor/" + escapeAttr(job.id()))
                        .withAttribute("hx-target", "#job-editor-container")
                        .withAttribute("hx-swap", "innerHTML")
                        .withInnerText(job.title() != null ? job.title() : job.id()))
                    .withChild(statusBadgeHtml(job.status())));
            }
            return list.render();
        } catch (Exception e) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/projects/_detail/{projectId}/agents")
    @ResponseBody
    public String projectAgentsFragment(@PathVariable String projectId) {
        try {
            List<ProjectAgentMembership> members = projectService.listMembers(projectId);
            if (members.isEmpty()) {
                return new Div().withClass("dashboard-empty")
                    .withInnerText("No agents assigned.").render();
            }
            Div list = new Div();
            for (var m : members) {
                list.withChild(new Div().withClass("orch-row")
                    .withChild(new HtmlTag("strong").withInnerText(
                        m.agentId() != null ? m.agentId() : "unknown"))
                    .withChild(new HtmlTag("span").withInnerText(
                        m.role() != null ? m.role() : "member")));
            }
            return list.render();
        } catch (Exception e) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/projects/_detail/{projectId}/outputs")
    @ResponseBody
    public String projectOutputsFragment(@PathVariable String projectId) {
        try {
            List<RunOutputArtifact> artifacts = queryOutputs(null, null, projectId, null, null, 40);
            if (artifacts.isEmpty()) {
                return new Div().withClass("dashboard-empty")
                    .withInnerText("No recent outputs.").render();
            }
            Div list = new Div();
            for (var o : artifacts) {
                list.withChild(new Div().withClass("dashboard-side-item")
                    .withChild(new HtmlTag("span").withClass("dashboard-side-item-name")
                        .withInnerText(o.outputName() != null ? o.outputName() :
                            o.artifactType() != null ? o.artifactType() : "output"))
                    .withChild(new HtmlTag("span").withClass("dashboard-side-item-meta")
                        .withInnerText(o.runId() != null ? o.runId() : "")));
            }
            return list.render();
        } catch (Exception e) {
            return new Div().withClass("dashboard-empty")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Project editor rendering ──

    private Component projectEditorFragment(Project project) {
        boolean isNew = project == null;
        String projectId = isNew ? null : project.id();

        Div container = new Div().withClass("orch-panel project-editor");
        container.withChild(Header.H2(isNew ? "New Project" : "Project: " +
            (project.name() != null ? project.name() : project.id())));

        Form form = Form.create();
        if (isNew) {
            form.withHxPost("/projects/_editor");
        } else {
            form.withHxPut("/projects/_editor/" + projectId);
        }
        form.withHxTarget("#project-editor-container");
        form.withHxSwap("innerHTML");

        form.withChild(new Div().withClass("orch-form-stack")
            .withChild(label("Name", TextInput.create("name")
                .withId("project-name")
                .withValue(isNew ? "" : nn(project.name()))))
            .withChild(label("Description", TextArea.create("description")
                .withId("project-description").withRows(3)
                .withValue(isNew ? "" : nn(project.description()))))
            .withChild(label("Owner Agent", agentSelect("ownerAgentId",
                isNew ? "" : nn(project.ownerAgentId())).withId("project-owner-agent")))
            .withChild(label("Git Repo URL", TextInput.create("gitRepoUrl")
                .withId("project-git-url")
                .withValue(isNew ? "" : nn(project.gitRepoUrl())))));

        String currentWorktype = project != null ? project.promptProfile() : null;
        form.withChild(new Div().withClass("orch-form-grid")
            .withChild(label("Manager Type", worktypeSelect(currentWorktype)))
            .withChild(label("Default Model", modelSelect("model")
                .withId("project-model"))));

        // Agent membership section (only for existing)
        if (!isNew) {
            // Workspace info
            form.withChild(sectionHeader("Workspace", "Project workspace directory."));
            try {
                var ws = projectService.workspaceSummary(projectId);
                form.withChild(new Div().withId("project-workspace-section")
                    .withChild(new Div().withClass("orch-meta")
                        .withChild(new HtmlTag("span").withInnerText(
                            "Owner: " + (ws.ownerAgentId() != null ? ws.ownerAgentId() : "—")))
                        .withChild(new HtmlTag("span").withInnerText(
                            "Kind: " + (ws.rootKind() != null ? ws.rootKind() : "—")))
                        .withChild(new HtmlTag("span").withInnerText(
                            "Path: " + (ws.displayPath() != null ? ws.displayPath() : "—")))
                        .withChild(new HtmlTag("span").withInnerText(
                            "Members: " + ws.linkCount()))
                        .withChild(new HtmlTag("span").withInnerText(
                            "Lease: " + (ws.leaseId() != null ? ws.leaseId() : "—")))
                        .withChild(new HtmlTag("span").withInnerText(
                            "Mounted Agent: " + (ws.mountedAgentId() != null ? ws.mountedAgentId() : "—")))
                        .withChild(new HtmlTag("span").withInnerText(
                            "Release Requested: " + ws.releaseRequested()))));
                if (ws.leaseId() != null && !ws.releaseRequested()) {
                    form.withChild(Button.create("Release workspace after current turn")
                        .withAttribute("type", "button")
                        .withAttribute("hx-post", "/projects/_detail/" + projectId + "/workspace/release")
                        .withAttribute("hx-target", "#project-editor-container")
                        .withAttribute("hx-swap", "innerHTML"));
                }
            } catch (Exception e) {
                form.withChild(new Div().withId("project-workspace-section")
                    .withChild(new Div().withClass("dashboard-empty")
                        .withInnerText("Workspace: " + e.getMessage())));
            }

            form.withChild(sectionHeader("Project Network", "Owner and linked agents around this project."));
            form.withChild(new Div().withId("project-network-section")
                .hxGet("/projects/_detail/" + projectId + "/network")
                .hxTrigger("load")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));

            // Agent memberships
            form.withChild(sectionHeader("Agents", "Members assigned to this project."));
            form.withChild(new Div().withId("project-agents-section")
                .hxGet("/projects/_detail/" + projectId + "/agents")
                .hxTrigger("load")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));

            // Active jobs
            form.withChild(sectionHeader("Active Jobs", "Jobs associated with this project."));
            form.withChild(new Div().withId("project-jobs-section")
                .hxGet("/projects/_detail/" + projectId + "/jobs")
                .hxTrigger("load")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));

            // Recent outputs
            form.withChild(sectionHeader("Recent Outputs", "Output artifacts from project jobs."));
            form.withChild(new Div().withId("project-outputs-section")
                .hxGet("/projects/_detail/" + projectId + "/outputs")
                .hxTrigger("load")
                .hxSwap("innerHTML")
                .withChild(loadingPlaceholder()));

            // Advanced metadata
            Div advanced = new Div().withClass("field-group");
            advanced.withChild(new HtmlTag("details")
                .withChild(new HtmlTag("summary").withInnerText("Advanced"))
                .withChild(new Div().withClass("orch-form-grid")
                    .withChild(label("ID", new HtmlTag("code").withInnerText(project.id())))
                    .withChild(label("Created", new HtmlTag("span")
                        .withInnerText(project.createdAt() != null ? project.createdAt().toString() : "—")))
                    .withChild(label("Updated", new HtmlTag("span")
                        .withInnerText(project.updatedAt() != null ? project.updatedAt().toString() : "—")))));
            form.withChild(advanced);
        }

        Div actions = new Div().withClass("tool-actions");
        actions.withChild(Button.create("Save").withClass("orch-primary")
            .withAttribute("type", "submit"));
        if (!isNew) {
            actions.withChild(Button.create("Delete")
                .withAttribute("hx-delete", "/projects/" + projectId)
                .withAttribute("hx-confirm", "Delete this project?")
                .withAttribute("hx-target", "#project-editor-container")
                .withAttribute("hx-swap", "innerHTML"));
        }
        form.withChild(actions);
        container.withChild(form);

        return container;
    }

    @GetMapping("/projects/_detail/{projectId}/network")
    @ResponseBody
    public String projectNetworkFragment(@PathVariable String projectId) {
        Project project = projectService.getProject(projectId);
        List<ProjectAgentMembership> members = projectService.listMembers(projectId);
        Div panel = new Div().withClass("orch-meta");
        panel.withChild(new HtmlTag("span").withInnerText("Owner: " + nn(project.ownerAgentId())));
        panel.withChild(new HtmlTag("span").withInnerText("Members: " + members.size()));
        for (ProjectAgentMembership member : members) {
            panel.withChild(new HtmlTag("span").withInnerText(
                nn(member.agentId()) + " (" + nn(member.role()) + ")"));
        }
        return panel.render();
    }

    @PostMapping("/projects/_detail/{projectId}/workspace/release")
    @ResponseBody
    public String requestProjectWorkspaceRelease(@PathVariable String projectId) {
        projectService.requestWorkspaceRelease(projectId);
        return projectEditor(projectId);
    }

    // Deprecated: old project detail page, redirects to /projects
    // Kept for backward compatibility with existing links
    @GetMapping("/projects/{projectId}")
    @ResponseBody
    public String projectDetail(@PathVariable String projectId) {
        // Render the projects page shell with the editor pre-selected
        Component body = new Div()
            .withId("projects-page")
            .withAttribute("data-orchestration-page", "projects")
            .withChild(Header.H1("Projects"))
            .withChild(new Paragraph("Top-level tracking and data-space wrappers with owner agents."))
            .withChild(new Div().withClass("browser-layout browser-layout-wide")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Button.create("New Project")
                            .withClass("orch-primary")
                            .hxGet("/projects/_editor/_new")
                            .hxTarget("#project-editor-container")
                            .hxSwap("innerHTML")))
                    .withChild(new Div().withId("project-list")
                        .hxGet("/projects/_list")
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withClass("entity-list")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("project-editor-container")
                        .hxGet("/projects/_editor/" + escapeAttr(projectId))
                        .hxTrigger("load")
                        .hxSwap("innerHTML")
                        .withChild(loadingPlaceholder()))))
            .withChild(moduleScript(PROJECTS_JS));
        return renderPage(body, "/projects/" + projectId);
    }

    // ════════════════════════════════════════════════════════════════
    //  Inbox
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/inbox")
    @ResponseBody
    public String inbox() {
        Component body = new Div()
            .withId("inbox-page")
            .withAttribute("data-orchestration-page", "inbox")
            .withChild(Header.H1("Inbox"))
            .withChild(new Paragraph("User and agent inboxes with approval response controls."))
            .withChild(new Div().withClass("inbox-layout")
                .withChild(new Div().withClass("inbox-main")
                    .withChild(new Div().withClass("orch-panel")
                        .withChild(Header.H2("User Inbox"))
                        .withChild(new Div().withId("user-inbox-messages").withClass("inbox-message-list")
                            .withAttribute("hx-get", "/inbox/_user")
                            .withAttribute("hx-trigger", "load")
                            .withAttribute("hx-swap", "innerHTML")
                            .withChild(loadingPlaceholder())))
                    .withChild(new Div().withClass("orch-panel")
                        .withChild(Header.H2("Agent Inbox"))
                        .withChild(new Div().withId("inbox-agent-selector").withClass("entity-toolbar")
                            .withAttribute("hx-get", "/inbox/_agent-selector")
                            .withAttribute("hx-trigger", "load")
                            .withAttribute("hx-swap", "innerHTML")
                            .withChild(loadingPlaceholder()))
                        .withChild(new Div().withId("agent-inbox-messages").withClass("inbox-message-list")
                            .withAttribute("hx-get", "/inbox/_agent")
                            .withAttribute("hx-trigger", "load")
                            .withAttribute("hx-swap", "innerHTML")
                            .withChild(loadingPlaceholder())))));
        return renderPage(body, "/inbox");
    }

    @GetMapping("/inbox/_agent-selector")
    @ResponseBody
    public String inboxAgentSelector() {
        Select select = Select.create("agentId").withId("inbox-agent-select");
        select.addOption("", "-- select agent --", true);
        for (AgentProfile agent : agentProfileService.list()) {
            select.addOption(agent.id(), agent.name() != null ? agent.name() : agent.id(), false);
        }
        select.withAttribute("hx-get", "/inbox/_agent")
            .withAttribute("hx-trigger", "change")
            .withAttribute("hx-target", "#agent-inbox-messages")
            .withAttribute("hx-swap", "innerHTML")
            .withAttribute("hx-include", "#inbox-agent-select");
        return select.render();
    }

    @GetMapping("/inbox/_user")
    @ResponseBody
    public String userInboxFragment() {
        List<InboxMessage> messages = inboxService.userInbox();
        if (messages.isEmpty()) {
            return new Div().withClass("dashboard-empty").withInnerText("No user messages.").render();
        }
        Table table = Table.create().withClass("dashboard-table")
            .withHeaders("Type", "From", "Body", "State", "Actions");
        for (InboxMessage message : messages) {
            boolean responded = message.respondedAt() != null;
            table.addRow(
                new HtmlTag("span").withInnerText(message.messageType().wireName()),
                new HtmlTag("span").withInnerText(message.fromId() != null ? message.fromId() : "system"),
                new HtmlTag("span").withInnerText(message.body() != null ? message.body() : ""),
                statusBadgeHtml(responded ? "responded" : "pending"),
                responded
                    ? new HtmlTag("span").withInnerText("—")
                    : new Div().withClass("orch-actions")
                        .withChild(Button.create("Approve")
                            .withClass("orch-primary")
                            .withAttribute("hx-post", "/inbox/_user/" + escapeAttr(message.id()) + "/approve")
                            .withAttribute("hx-target", "#user-inbox-messages")
                            .withAttribute("hx-swap", "innerHTML"))
                        .withChild(Button.create("Reject")
                            .withAttribute("hx-post", "/inbox/_user/" + escapeAttr(message.id()) + "/reject")
                            .withAttribute("hx-target", "#user-inbox-messages")
                            .withAttribute("hx-swap", "innerHTML"))
            );
        }
        return table.render();
    }

    @PostMapping("/inbox/_user/{messageId}/approve")
    @ResponseBody
    public String approveUserInboxMessage(@PathVariable String messageId) {
        inboxService.respondUserApproval(messageId, true, null);
        return userInboxFragment();
    }

    @PostMapping("/inbox/_user/{messageId}/reject")
    @ResponseBody
    public String rejectUserInboxMessage(@PathVariable String messageId) {
        inboxService.respondUserApproval(messageId, false, null);
        return userInboxFragment();
    }

    @GetMapping("/inbox/_agent")
    @ResponseBody
    public String agentInboxFragment(@RequestParam(value = "agentId", required = false) String agentId) {
        if (!StringUtils.hasText(agentId)) {
            return new Div().withClass("dashboard-empty").withInnerText("Select an agent.").render();
        }
        List<io.mindspice.magenta2.ai.orchestration.runtime.InboxMessage> messages = runtimeInboxService.messages(agentId);
        if (messages.isEmpty()) {
            return new Div().withClass("dashboard-empty").withInnerText("No agent messages.").render();
        }
        Table table = Table.create().withClass("dashboard-table")
            .withHeaders("Type", "From", "Body", "State", "Actions");
        for (io.mindspice.magenta2.ai.orchestration.runtime.InboxMessage message : messages) {
            table.addRow(
                new HtmlTag("span").withInnerText(message.messageType() != null ? message.messageType() : "message"),
                new HtmlTag("span").withInnerText(message.fromId() != null ? message.fromId() : "system"),
                new HtmlTag("span").withInnerText(message.body() != null ? message.body() : ""),
                statusBadgeHtml(message.handled() ? "handled" : message.read() ? "read" : "unread"),
                message.handled()
                    ? new HtmlTag("span").withInnerText("—")
                    : new Div().withClass("orch-actions")
                        .withChild(Button.create("Read")
                            .withAttribute("hx-post", "/inbox/_agent/" + escapeAttr(agentId) + "/" + escapeAttr(message.id()) + "/read")
                            .withAttribute("hx-target", "#agent-inbox-messages")
                            .withAttribute("hx-swap", "innerHTML")
                            .withAttribute("hx-include", "#inbox-agent-select"))
                        .withChild(Button.create("Handled")
                            .withClass("orch-primary")
                            .withAttribute("hx-post", "/inbox/_agent/" + escapeAttr(agentId) + "/" + escapeAttr(message.id()) + "/handled")
                            .withAttribute("hx-target", "#agent-inbox-messages")
                            .withAttribute("hx-swap", "innerHTML")
                            .withAttribute("hx-include", "#inbox-agent-select"))
            );
        }
        return table.render();
    }

    @PostMapping("/inbox/_agent/{agentId}/{messageId}/read")
    @ResponseBody
    public String markAgentInboxRead(@PathVariable String agentId, @PathVariable String messageId) {
        runtimeInboxService.markRead(messageId);
        return agentInboxFragment(agentId);
    }

    @PostMapping("/inbox/_agent/{agentId}/{messageId}/handled")
    @ResponseBody
    public String markAgentInboxHandled(@PathVariable String agentId, @PathVariable String messageId) {
        runtimeInboxService.markHandled(messageId);
        return agentInboxFragment(agentId);
    }

    // ════════════════════════════════════════════════════════════════
    //  Outputs
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/outputs")
    @ResponseBody
    public String outputs() {
        Component body = new Div()
            .withId("outputs-page")
            .withAttribute("data-orchestration-page", "outputs")
            .withChild(Header.H1("Outputs"))
            .withChild(new Paragraph("Browse materialized output artifacts by agent, job, project, run ID, and artifact type. Click View to read text content inline, or Download for binary files."))
            .withChild(outputsFilterPanel())
            .withChild(new Div().withId("outputs-list").withClass("outputs-grid")
                .withAttribute("hx-get", "/outputs/_list")
                .withAttribute("hx-trigger", "load")
                .withAttribute("hx-swap", "innerHTML")
                .withChild(loadingPlaceholder()))
            .withChild(new Div().withId("outputs-content-pane").withClass("outputs-content-pane"));
        return renderPage(body, "/outputs");
    }

    @GetMapping("/outputs/_list")
    @ResponseBody
    public String outputsListFragment(
        @RequestParam(value = "agentId", required = false) String agentId,
        @RequestParam(value = "jobId", required = false) String jobId,
        @RequestParam(value = "projectId", required = false) String projectId,
        @RequestParam(value = "runId", required = false) String runId,
        @RequestParam(value = "type", required = false) String type
    ) {
        List<RunOutputArtifact> artifacts = queryOutputs(agentId, jobId, projectId, runId, type, 100);
        if (artifacts.isEmpty()) {
            return new Div().withClass("dashboard-empty").withInnerText("No outputs found.").render();
        }
        Table table = Table.create().withClass("dashboard-table")
            .withHeaders("Output", "Type", "Run", "Plan", "Created", "");
        for (RunOutputArtifact artifact : artifacts) {
            boolean canView = "text".equals(artifact.artifactType())
                || "json".equals(artifact.artifactType())
                || "user_message".equals(artifact.artifactType());
            table.addRow(
                new HtmlTag("span").withInnerText(artifact.outputName() != null ? artifact.outputName() : "output"),
                new HtmlTag("span").withInnerText(artifact.artifactType() != null ? artifact.artifactType() : "—"),
                new HtmlTag("span").withInnerText(artifact.runId() != null ? artifact.runId() : "—"),
                new HtmlTag("span").withInnerText(artifact.planId() != null ? artifact.planId() : "—"),
                new HtmlTag("span").withInnerText(artifact.createdAt() != null ? formatSince(artifact.createdAt()) : "—"),
                canView
                    ? new HtmlTag("a")
                        .withClass("orch-primary")
                        .withAttribute("href", "#")
                        .withAttribute("hx-get", "/outputs/_content/" + artifact.id())
                        .withAttribute("hx-target", "#outputs-content-pane")
                        .withAttribute("hx-swap", "innerHTML")
                        .withInnerText("View")
                    : new HtmlTag("a")
                        .withClass("orch-primary")
                        .withAttribute("href", "/api/outputs/" + artifact.id() + "/download")
                        .withInnerText("Download")
            );
        }
        return table.render();
    }

    @GetMapping("/outputs/_content/{artifactId}")
    @ResponseBody
    public String outputsContentFragment(@PathVariable String artifactId) {
        try {
            RunOutputArtifact artifact = outputArtifactService.getArtifact(artifactId);
            String content;
            try {
                content = outputArtifactService.loadContent(artifactId, 5 * 1024 * 1024);
            } catch (IOException e) {
                return new Div().withClass("orch-status orch-status-error")
                    .withInnerText("Failed to read artifact: " + e.getMessage()).render();
            }
            Div panel = new Div().withClass("output-content-fragment");
            panel.withChild(Header.H2(artifact.outputName() != null ? artifact.outputName() : "Output"));
            panel.withChild(new Div().withClass("orch-meta")
                .withChild(new HtmlTag("span").withInnerText("Type: " + nn(artifact.artifactType())))
                .withChild(new HtmlTag("span").withInnerText("File: " + nn(artifact.fileName())))
                .withChild(new HtmlTag("span").withInnerText("Created: " + (artifact.createdAt() != null ? formatSince(artifact.createdAt()) : "—"))));
            panel.withChild(new HtmlTag("a")
                .withAttribute("href", "/api/outputs/" + artifactId + "/download")
                .withClass("orch-primary")
                .withInnerText("Download"));
            String safeContent = escapeAttr(content);
            panel.withChild(new HtmlTag("pre").withClass("output-content-body").withInnerText(safeContent));
            return panel.render();
        } catch (IllegalArgumentException e) {
            return new Div().withClass("orch-status orch-status-error")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private Component outputsFilterPanel() {
        Form form = Form.create();
        form.withId("outputs-filter-form");
        form.withAttribute("hx-get", "/outputs/_list");
        form.withAttribute("hx-target", "#outputs-list");
        form.withAttribute("hx-swap", "innerHTML");

        Select agent = Select.create("agentId");
        agent.addOption("", "All agents", true);
        for (AgentProfile profile : agentProfileService.list()) {
            agent.addOption(profile.id(), profile.name() != null ? profile.name() : profile.id(), false);
        }
        Select job = Select.create("jobId");
        job.addOption("", "All jobs", true);
        for (JobDefinition definition : jobService.listDefinitions()) {
            job.addOption(definition.id(), definition.title() != null ? definition.title() : definition.id(), false);
        }
        Select project = Select.create("projectId");
        project.addOption("", "All projects", true);
        for (Project value : projectService.listProjects()) {
            project.addOption(value.id(), value.name() != null ? value.name() : value.id(), false);
        }
        Select type = Select.create("type");
        type.addOption("", "All types", true);
        type.addOption("file_path", "file_path", false);
        type.addOption("user_message", "user_message", false);
        type.addOption("json", "json", false);
        type.addOption("text", "text", false);

        Div toolbar = new Div().withClass("outputs-toolbar")
            .withChild(agent)
            .withChild(job)
            .withChild(project)
            .withChild(TextInput.create("runId").withPlaceholder("run ID"))
            .withChild(type)
            .withChild(Button.create("Browse").withClass("orch-primary").withAttribute("type", "submit"));
        form.withChild(toolbar);
        return form;
    }

    private List<RunOutputArtifact> queryOutputs(String agentId, String jobId, String projectId, String runId, String type, int limit) {
        OutputArtifactQuery query = OutputArtifactQuery.of(
            agentId,
            jobId,
            projectId,
            null,
            runId,
            null,
            type,
            limit
        );
        List<RunOutputArtifact> direct = outputArtifactService.query(query);
        if (!direct.isEmpty() || StringUtils.hasText(runId)) {
            return direct;
        }
        if (StringUtils.hasText(jobId)) {
            try {
                return artifactsForJobs(List.of(jobService.getDefinition(jobId)), type, limit);
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if (StringUtils.hasText(agentId) || StringUtils.hasText(projectId)) {
            return artifactsForJobs(jobService.listDefinitions(agentId, projectId, null), type, limit);
        }
        return direct;
    }

    private List<RunOutputArtifact> artifactsForJobs(List<JobDefinition> jobs, String type, int limit) {
        List<RunOutputArtifact> artifacts = new ArrayList<>();
        for (JobDefinition job : jobs) {
            for (String run : jobService.outputRunIds(job.id())) {
                artifacts.addAll(outputArtifactService.query(run, null, type, limit));
                if (artifacts.size() >= limit) {
                    return artifacts.subList(0, limit);
                }
            }
        }
        return artifacts;
    }

    // ════════════════════════════════════════════════════════════════
    //  Agents (HTMX-first)
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/agents")
    @ResponseBody
    public String agents() {
        Component body = new Div()
            .withId("agents-page")
            .withAttribute("data-orchestration-page", "agents")
            .withChild(Header.H1("Agents"))
            .withChild(new Paragraph("Manage agent profiles, queues, inboxes, assignments, and workspace."))
            .withChild(new Div().withClass("browser-layout browser-layout-wide")
                .withChild(new Div().withClass("browser-sidebar")
                    .withChild(new Div().withClass("browser-sidebar-header")
                        .withChild(Button.create("Create Agent").withClass("orch-primary")
                            .withAttribute("hx-post", "/agents/_create")
                            .withAttribute("hx-target", "#agent-list")
                            .withAttribute("hx-swap", "innerHTML"))
                        .withChild(Button.create("Reload")
                            .withAttribute("hx-get", "/agents/_list")
                            .withAttribute("hx-target", "#agent-list")
                            .withAttribute("hx-swap", "innerHTML")))
                    .withChild(TextInput.search("agentFilter").withId("agent-filter")
                        .withPlaceholder("Filter agents")
                        .withAttribute("hx-get", "/agents/_list")
                        .withAttribute("hx-trigger", "keyup changed delay:300ms")
                        .withAttribute("hx-target", "#agent-list")
                        .withAttribute("hx-swap", "innerHTML")
                        .withAttribute("hx-include", "#agent-filter"))
                    .withChild(new Div().withId("agent-list")
                        .withClass("entity-list")
                        .withAttribute("hx-get", "/agents/_list")
                        .withAttribute("hx-trigger", "load")
                        .withAttribute("hx-swap", "innerHTML")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("agent-detail-container")
                        .withChild(agentDetailEmptyState()))))
            .withChild(moduleScript(AGENTS_JS))
            .withChild(moduleScript(AGENT_CHAT_JS));
        return renderPage(body, "/agents");
    }

    private Component agentDetailEmptyState() {
        return new Div().withClass("dashboard-empty")
            .withInnerText("Select an agent from the list or create a new one.");
    }

    // ── Agent list HTMX partial ──

    @GetMapping("/agents/_list")
    @ResponseBody
    public String agentList(@RequestParam(value = "agentFilter", required = false) String filter) {
        List<AgentProfile> agents = agentProfileService.list();
        String f = filter != null ? filter.toLowerCase().trim() : "";
        if (!f.isEmpty()) {
            agents = agents.stream()
                .filter(a -> a.name() != null && a.name().toLowerCase().contains(f))
                .toList();
        }

        if (agents.isEmpty()) {
            return new Div().withClass("dashboard-empty")
                .withInnerText(f.isEmpty() ? "No agents. Create one to get started." : "No agents match filter.")
                .render();
        }

        Div cards = new Div().withId("agents-list-table").withClass("agent-card-list");
        for (var a : agents) {
            int queueCount = countAssignments(a.id());
            int inboxCount = countInboxMessages(a.id());
            String workspaceHealth = agentWorkspaceHealth(a);
            cards.withChild(new Div().withClass("agent-card")
                .withChild(new HtmlTag("a")
                    .withAttribute("href", "/agents/" + escapeAttr(a.id()))
                    .withAttribute("hx-get", "/agents/_detail/" + escapeAttr(a.id()))
                    .withAttribute("hx-target", "#agent-detail-container")
                    .withAttribute("hx-swap", "innerHTML")
                    .withClass("agent-card-name")
                    .withInnerText(a.name() != null ? a.name() : a.id()))
                .withChild(new Div().withClass("agent-card-meta")
                    .withChild(statusBadgeHtml(a.status() != null ? a.status().name() : "UNKNOWN"))
                    .withChild(statusBadgeHtml(workspaceHealth))
                    .withChild(new HtmlTag("span").withInnerText("Q " + queueCount))
                    .withChild(new HtmlTag("span").withInnerText("Inbox " + inboxCount)))
                .withChild(new Div().withClass("agent-card-model")
                    .withInnerText(a.defaultModel() != null ? a.defaultModel() : "unset"))
                .withChild(agentRowActions(a)));
        }
        return cards.render();
    }

    private int countAssignments(String agentId) {
        try {
            return assignmentService.assignments(agentId).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private int countInboxMessages(String agentId) {
        try {
            return runtimeInboxService.messages(agentId).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String agentWorkspaceHealth(AgentProfile agent) {
        try {
            workspaceService.agentWorkspace(agent.id(), null);
            return "READY";
        } catch (Exception e) {
            return "PENDING";
        }
    }

    private Component agentRowActions(AgentProfile agent) {
        Div actions = new Div().withClass("orch-actions");
        actions.withChild(Button.create("Refresh")
            .withAttribute("hx-get", "/agents/_list")
            .withAttribute("hx-target", "#agent-list")
            .withAttribute("hx-swap", "innerHTML"));
        actions.withChild(Button.create(agent.status() == AgentProfileStatus.ACTIVE ? "Disable" : "Enable")
            .withAttribute("hx-post", "/agents/_lifecycle/" + escapeAttr(agent.id())
                + (agent.status() == AgentProfileStatus.ACTIVE ? "/disable" : "/enable") + "?view=list")
            .withAttribute("hx-target", "#agent-list")
            .withAttribute("hx-swap", "innerHTML"));
        actions.withChild(Button.create("Delete")
            .withAttribute("hx-get", "/agents/_lifecycle/" + escapeAttr(agent.id()) + "/delete-confirm")
            .withAttribute("hx-target", "#agent-detail-container")
            .withAttribute("hx-swap", "innerHTML"));
        return actions;
    }

    // ── Create agent HTMX action ──

    @PostMapping("/agents/_create")
    @ResponseBody
    public String createAgent() {
        try {
            String name = "Agent " + System.currentTimeMillis() % 100000;
            AgentProfile created = agentProfileService.create(new AgentProfile(
                null, name, io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus.ACTIVE,
                "", "", java.util.List.of(), java.util.List.of(), false, null, null
            ));
            // Return the updated agent list with the new agent
            return agentList(null);
        } catch (Exception e) {
            return new Div().withClass("orch-error")
                .withInnerText("Error creating agent: " + e.getMessage()).render();
        }
    }

    // ── Agent detail page (standalone) ──

    @GetMapping("/agents/{agentId}")
    @ResponseBody
    public String agentDetail(@PathVariable String agentId) {
        AgentProfile agent;
        try {
            agent = agentProfileService.get(agentId);
        } catch (Exception e) {
            Component body = new Div()
                .withId("agents-page")
                .withChild(Header.H1("Agent Not Found"))
                .withChild(new Paragraph("Agent " + escapeAttr(agentId) + " does not exist."))
                .withChild(new HtmlTag("a").withAttribute("href", "/agents")
                    .withInnerText("Back to agents"));
            return renderPage(body, "/agents");
        }

        Component body = new Div()
            .withId("agents-page")
            .withAttribute("data-orchestration-page", "agents")
            .withAttribute("data-agent-id", agent.id())
            .withChild(Header.H1("Agent: " + (agent.name() != null ? agent.name() : agent.id())))
            .withChild(new Paragraph("Profile, queue, inbox, workspace, and history."))
            .withChild(agentDetailLayout(agent))
            .withChild(moduleScript(AGENTS_JS))
            .withChild(moduleScript(AGENT_CHAT_JS));
        return renderPage(body, "/agents/" + agentId);
    }

    // ── Agent detail HTMX partial ──

    @GetMapping("/agents/_detail/{agentId}")
    @ResponseBody
    public String agentDetailFragment(@PathVariable String agentId) {
        AgentProfile agent;
        try {
            agent = agentProfileService.get(agentId);
        } catch (Exception e) {
            return new Div().withClass("orch-error")
                .withInnerText("Agent not found: " + escapeAttr(agentId)).render();
        }
        return agentDetailLayout(agent).render();
    }

    private Component agentDetailLayout(AgentProfile agent) {
        return new Div()
            .withChild(new HtmlTag("details").withClass("agent-chat-accordion")
                .withChild(new HtmlTag("summary").withInnerText("Chat with Agent"))
                .withChild(agentChatPanel(agent.id())))
            .withChild(new Div().withClass("entity-detail-layout")
                .withChild(new Div().withClass("entity-detail-main")
                    .withChild(tabNav(agent.id(), "dashboard", "profile", "queue", "inbox", "jobs", "schedules", "reactions", "workspace", "outputs", "exec", "history", "submit"))
                    .withChild(new Div().withClass("agent-profile-loader-marker")
                        .withAttribute("hidden", "hidden")
                        .withAttribute("hx-get", "/agents/_editor/" + escapeAttr(agent.id())))
                    .withChild(new Div().withClass("agent-submit-loader-marker")
                        .withAttribute("hidden", "hidden")
                        .withAttribute("hx-get", "/agents/_submit-form/" + escapeAttr(agent.id())))
                    .withChild(new Div().withId("agent-tab-panel").withClass("orch-panel")
                        .withAttribute("hx-get", "/agents/_detail/" + escapeAttr(agent.id()) + "/dashboard")
                        .withAttribute("hx-trigger", "load")
                        .withAttribute("hx-swap", "innerHTML")
                        .withChild(loadingPlaceholder())))
                .withChild(agentEventLogPanel()));
    }

    private Component agentEventLogPanel() {
        Div panel = new Div().withClass("entity-detail-side agent-event-log");
        panel.withChild(Header.H2("Event Log"));
        panel.withChild(new Div().withClass("agent-event-log-list")
            .withChild(agentEventLogItem("Now", "Agent dashboard loaded"))
            .withChild(agentEventLogItem("Queue", "1 assignment waiting"))
            .withChild(agentEventLogItem("Workspace", "Workspace ready")));
        return panel;
    }

    private Component agentEventLogItem(String label, String message) {
        return new Div().withClass("agent-event-log-item")
            .withChild(new HtmlTag("span").withClass("agent-event-log-label").withInnerText(label))
            .withChild(new HtmlTag("span").withClass("agent-event-log-message").withInnerText(message));
    }

    private Component agentChatPanel(String agentId) {
        return new Div()
            .withAttribute("data-agent-chat-panel", "")
            .withAttribute("data-agent-id", agentId)
            .withAttribute("data-page-context", "agent detail")
            .withChild(new HtmlTag("section").withClass("agent-chat-panel").withId("agent-chat-panel")
                .withChild(new Div().withClass("agent-chat-body").withId("agent-chat-messages"))
                .withChild(Form.create().withClass("agent-chat-form").withId("agent-chat-form")
                    .withChild(TextInput.create("").withId("agent-chat-input").withPlaceholder("Ask this agent"))
                    .withChild(Button.create("Send").withAttribute("type", "submit"))));
    }

    // ── Agent detail tab partials ──

    @GetMapping("/agents/_detail/{agentId}/dashboard")
    @ResponseBody
    public String agentDashboardTab(@PathVariable String agentId) {
        AgentProfile agent = agentProfileService.get(agentId);

        int queueCount = countAssignments(agentId);
        int inboxCount = countInboxMessages(agentId);
        int jobCount = jobService.listDefinitions(agentId, null, null).size();

        WorkAssignment current = currentAssignment(agentId);

        Div panel = new Div().withClass("agent-dashboard");
        panel.withChild(Header.H2("Dashboard"));

        // Identity row
        Div identityGrid = new Div().withClass("orch-form-grid");
        identityGrid.withChild(agentMetaItem("Name", agent.name() != null ? agent.name() : agent.id()));
        identityGrid.withChild(agentMetaItem("Status", agent.status() != null ? agent.status().name() : "UNKNOWN"));
        identityGrid.withChild(agentMetaItem("Model", agent.defaultModel() != null ? agent.defaultModel() : "unset"));
        identityGrid.withChild(agentMetaItem("ID", agent.id()));
        identityGrid.withChild(agentMetaItem("Direct Line", agent.directLineEnabled() ? "Enabled" : "Disabled"));
        identityGrid.withChild(agentMetaItem("Created", agent.createdAt() != null ? formatSince(agent.createdAt()) : "—"));
        panel.withChild(identityGrid);

        // Counters row
        Div counters = new Div().withClass("agent-dashboard-counters");
        counters.withChild(agentCounterCard("Queue", String.valueOf(queueCount),
            "/agents/_detail/" + agentId + "/queue", "#agent-tab-panel"));
        counters.withChild(agentCounterCard("Inbox", String.valueOf(inboxCount),
            "/agents/_detail/" + agentId + "/inbox", "#agent-tab-panel"));
        counters.withChild(agentCounterCard("Jobs", String.valueOf(jobCount),
            "/agents/_detail/" + agentId + "/jobs", "#agent-tab-panel"));
        panel.withChild(counters);

        // Current assignment
        if (current != null) {
            Div running = new Div().withClass("orch-panel");
            running.withChild(Header.H2("Current Assignment"));
            running.withChild(new Div().withClass("orch-meta")
                .withChild(new HtmlTag("span").withInnerText("Type: " + current.assignmentType()))
                .withChild(new HtmlTag("span").withInnerText("Status: " + (current.status() != null ? current.status().name() : "unknown")))
                .withChild(new HtmlTag("span").withInnerText("Priority: " + current.priority()))
                .withChild(new HtmlTag("span").withInnerText("ID: " + current.id())));
            panel.withChild(running);
        }

        // Workspace status
        String health = agentWorkspaceHealth(agent);
        panel.withChild(new Div().withClass("orch-meta")
            .withChild(new HtmlTag("span").withInnerText("Workspace: " + health)));

        panel.withChild(new Div().withClass("orch-actions")
            .withChild(Button.create("Refresh")
                .withAttribute("hx-get", "/agents/_detail/" + escapeAttr(agentId) + "/dashboard")
                .withAttribute("hx-target", "#agent-tab-panel")
                .withAttribute("hx-swap", "innerHTML"))
            .withChild(Button.create(agent.status() == AgentProfileStatus.ACTIVE ? "Disable Agent" : "Enable Agent")
                .withAttribute("hx-post", "/agents/_lifecycle/" + escapeAttr(agentId)
                    + (agent.status() == AgentProfileStatus.ACTIVE ? "/disable" : "/enable"))
                .withAttribute("hx-target", "#agent-tab-panel")
                .withAttribute("hx-swap", "innerHTML"))
            .withChild(Button.create("Delete / Archive")
                .withAttribute("hx-get", "/agents/_lifecycle/" + escapeAttr(agentId) + "/delete-confirm")
                .withAttribute("hx-target", "#agent-docker-status-" + escapeAttr(agentId))
                .withAttribute("hx-swap", "innerHTML")));

        return panel.render();
    }

    private WorkAssignment currentAssignment(String agentId) {
        try {
            List<WorkAssignment> assignments = assignmentService.assignments(agentId);
            return assignments.stream()
                .filter(a -> a.status() == io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus.RUNNING)
                .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Component agentMetaItem(String label, String value) {
        return new Div().withClass("agent-meta-item")
            .withChild(new HtmlTag("span").withClass("agent-meta-label").withInnerText(label))
            .withChild(new HtmlTag("span").withClass("agent-meta-value").withInnerText(value));
    }

    private Component agentCounterCard(String label, String value, String hxUrl, String hxTarget) {
        Div card = new Div().withClass("agent-counter-card");
        card.withChild(new HtmlTag("span").withClass("agent-counter-value").withInnerText(value));
        card.withChild(new HtmlTag("span").withClass("agent-counter-label").withInnerText(label));
        card.withAttribute("hx-get", hxUrl);
        card.withAttribute("hx-target", hxTarget);
        card.withAttribute("hx-swap", "innerHTML");
        card.withAttribute("style", "cursor:pointer");
        return card;
    }

    @GetMapping("/agents/_detail/{agentId}/queue")
    @ResponseBody
    public String agentQueueTab(@PathVariable String agentId) {
        List<WorkAssignment> assignments = assignmentService.assignments(agentId);
        return renderAssignmentList(agentId, "Queue", assignments);
    }

    @GetMapping("/agents/_detail/{agentId}/profile")
    @ResponseBody
    public String agentProfileTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("Profile"));
        panel.withChild(new Div().withId("agent-editor-container")
            .withAttribute("hx-get", "/agents/_editor/" + escapeAttr(agentId))
            .withAttribute("hx-trigger", "load")
            .withAttribute("hx-swap", "innerHTML")
            .withChild(loadingPlaceholder()));
        return panel.render();
    }

    @PostMapping("/agents/_detail/{agentId}/queue/{assignmentId}/cancel")
    @ResponseBody
    public String cancelAgentAssignment(@PathVariable String agentId, @PathVariable String assignmentId) {
        assignmentService.cancel(assignmentId);
        return agentQueueTab(agentId);
    }

    @PostMapping("/agents/_detail/{agentId}/queue/{assignmentId}/pause")
    @ResponseBody
    public String pauseAgentAssignment(@PathVariable String agentId, @PathVariable String assignmentId) {
        assignmentService.pause(assignmentId);
        return agentQueueTab(agentId);
    }

    @PostMapping("/agents/_detail/{agentId}/queue/{assignmentId}/resume")
    @ResponseBody
    public String resumeAgentAssignment(@PathVariable String agentId, @PathVariable String assignmentId) {
        assignmentService.resume(assignmentId);
        return agentQueueTab(agentId);
    }

    @GetMapping("/agents/_detail/{agentId}/queue/{assignmentId}/diagnostics")
    @ResponseBody
    public String assignmentDiagnosticsFragment(@PathVariable String agentId, @PathVariable String assignmentId) {
        return assignmentDiagnosticsPanel(agentId, assignmentService.diagnostics(assignmentId)).render();
    }

    @PostMapping("/agents/_detail/{agentId}/queue/{assignmentId}/force-interrupt")
    @ResponseBody
    public String forceInterruptAgentAssignment(
        @PathVariable String agentId,
        @PathVariable String assignmentId,
        @RequestParam(value = "reason", required = false) String reason
    ) {
        assignmentService.forceInterrupt(assignmentId, reason);
        return agentQueueTab(agentId);
    }

    @GetMapping("/agents/_detail/{agentId}/inbox")
    @ResponseBody
    public String agentInboxTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("Inbox"));

        java.util.List<io.mindspice.magenta2.ai.orchestration.runtime.InboxMessage> messages;
        try {
            messages = runtimeInboxService.messages(agentId);
        } catch (Exception e) {
            panel.withChild(new Div().withClass("orch-error").withInnerText("Error: " + e.getMessage()));
            return panel.render();
        }

        if (messages.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty").withInnerText("No inbox messages."));
            return panel.render();
        }

        Table table = Table.create()
            .withHeaders("Type", "From", "Read", "Handled", "Created")
            .withClass("dashboard-table");
        for (var m : messages) {
            table.addRow(
                new HtmlTag("span").withInnerText(m.messageType() != null ? m.messageType() : "—"),
                new HtmlTag("span").withInnerText(m.fromId() != null ? m.fromId() : "—"),
                statusBadgeHtml(m.read() ? "read" : "unread"),
                statusBadgeHtml(m.handled() ? "handled" : "pending"),
                new HtmlTag("span").withInnerText(m.createdAt() != null ? formatSince(m.createdAt()) : "—")
            );
        }
        panel.withChild(table);
        return panel.render();
    }

    @GetMapping("/agents/_detail/{agentId}/jobs")
    @ResponseBody
    public String agentJobsTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("Jobs"));

        List<JobDefinition> jobs = jobService.listDefinitions(agentId, null, null);
        if (jobs.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty").withInnerText("No jobs."));
            return panel.render();
        }

        Table table = Table.create()
            .withHeaders("Title", "Status", "Project", "Updated")
            .withClass("dashboard-table");
        for (var j : jobs) {
            table.addRow(
                new HtmlTag("a").withAttribute("href", "/jobs")
                    .withInnerText(j.title() != null ? j.title() : j.id()),
                statusBadgeHtml(j.status()),
                new HtmlTag("span").withInnerText(j.projectId() != null ? j.projectId() : "—"),
                new HtmlTag("span").withInnerText(j.updatedAt() != null ? formatSince(j.updatedAt()) : "—")
            );
        }
        panel.withChild(table);
        return panel.render();
    }

    @GetMapping("/agents/_detail/{agentId}/schedules")
    @ResponseBody
    public String agentSchedulesTab(@PathVariable String agentId) {
        return schedulesPanel(agentId, null).render();
    }

    @PostMapping("/agents/_detail/{agentId}/schedules")
    @ResponseBody
    public String createAgentSchedule(@PathVariable String agentId, @RequestParam Map<String, String> params) {
        if (!schedulesEnabled) {
            return schedulesPanel(agentId, "Schedules are disabled.").render();
        }
        try {
            scheduleService.save(agentId, buildSchedule(agentId, null, null, params));
            return schedulesPanel(agentId, null).render();
        } catch (Exception exception) {
            return schedulesPanel(agentId, exception.getMessage()).render();
        }
    }

    @PutMapping("/agents/_detail/{agentId}/schedules/{scheduleId}")
    @ResponseBody
    public String updateAgentSchedule(
        @PathVariable String agentId,
        @PathVariable String scheduleId,
        @RequestParam Map<String, String> params
    ) {
        if (!schedulesEnabled) {
            return schedulesPanel(agentId, "Schedules are disabled.").render();
        }
        try {
            AgentSchedule existing = scheduleService.schedule(agentId, scheduleId);
            scheduleService.save(agentId, buildSchedule(agentId, scheduleId, existing, params));
            return schedulesPanel(agentId, null).render();
        } catch (Exception exception) {
            return schedulesPanel(agentId, exception.getMessage()).render();
        }
    }

    @PostMapping("/agents/_detail/{agentId}/schedules/{scheduleId}/toggle")
    @ResponseBody
    public String toggleAgentSchedule(@PathVariable String agentId, @PathVariable String scheduleId) {
        if (!schedulesEnabled) {
            return schedulesPanel(agentId, "Schedules are disabled.").render();
        }
        try {
            scheduleService.toggle(agentId, scheduleId);
            return schedulesPanel(agentId, null).render();
        } catch (Exception exception) {
            return schedulesPanel(agentId, exception.getMessage()).render();
        }
    }

    @DeleteMapping("/agents/_detail/{agentId}/schedules/{scheduleId}")
    @ResponseBody
    public String deleteAgentSchedule(@PathVariable String agentId, @PathVariable String scheduleId) {
        if (!schedulesEnabled) {
            return schedulesPanel(agentId, "Schedules are disabled.").render();
        }
        try {
            scheduleService.delete(agentId, scheduleId);
            return schedulesPanel(agentId, null).render();
        } catch (Exception exception) {
            return schedulesPanel(agentId, exception.getMessage()).render();
        }
    }

    @GetMapping("/agents/_detail/{agentId}/reactions")
    @ResponseBody
    public String agentReactionsTab(@PathVariable String agentId) {
        return reactionsPanel(agentId, null).render();
    }

    @PostMapping("/agents/_detail/{agentId}/reactions")
    @ResponseBody
    public String createAgentReaction(@PathVariable String agentId, @RequestParam Map<String, String> params) {
        if (!reactionsEnabled) {
            return reactionsPanel(agentId, "Event reactions are disabled.").render();
        }
        try {
            eventReactionService.save(agentId, buildReaction(agentId, null, null, params));
            return reactionsPanel(agentId, null).render();
        } catch (Exception exception) {
            return reactionsPanel(agentId, exception.getMessage()).render();
        }
    }

    @PutMapping("/agents/_detail/{agentId}/reactions/{reactionId}")
    @ResponseBody
    public String updateAgentReaction(
        @PathVariable String agentId,
        @PathVariable String reactionId,
        @RequestParam Map<String, String> params
    ) {
        if (!reactionsEnabled) {
            return reactionsPanel(agentId, "Event reactions are disabled.").render();
        }
        try {
            AgentEventReaction existing = eventReactionService.reaction(agentId, reactionId);
            eventReactionService.save(agentId, buildReaction(agentId, reactionId, existing, params));
            return reactionsPanel(agentId, null).render();
        } catch (Exception exception) {
            return reactionsPanel(agentId, exception.getMessage()).render();
        }
    }

    @PostMapping("/agents/_detail/{agentId}/reactions/{reactionId}/toggle")
    @ResponseBody
    public String toggleAgentReaction(@PathVariable String agentId, @PathVariable String reactionId) {
        if (!reactionsEnabled) {
            return reactionsPanel(agentId, "Event reactions are disabled.").render();
        }
        try {
            eventReactionService.toggle(agentId, reactionId);
            return reactionsPanel(agentId, null).render();
        } catch (Exception exception) {
            return reactionsPanel(agentId, exception.getMessage()).render();
        }
    }

    @DeleteMapping("/agents/_detail/{agentId}/reactions/{reactionId}")
    @ResponseBody
    public String deleteAgentReaction(@PathVariable String agentId, @PathVariable String reactionId) {
        if (!reactionsEnabled) {
            return reactionsPanel(agentId, "Event reactions are disabled.").render();
        }
        try {
            eventReactionService.delete(agentId, reactionId);
            return reactionsPanel(agentId, null).render();
        } catch (Exception exception) {
            return reactionsPanel(agentId, exception.getMessage()).render();
        }
    }

    private Component schedulesPanel(String agentId, String errorMessage) {
        Div panel = new Div();
        panel.withChild(Header.H2("Schedules"));
        if (!schedulesEnabled) {
            panel.withChild(featureDisabledState("Schedules are disabled.", "magenta.features.schedules-enabled=true"));
            return panel;
        }
        if (StringUtils.hasText(errorMessage)) {
            panel.withChild(new Div().withClass("orch-error").withInnerText(errorMessage));
        }
        panel.withChild(new Div().withClass("orch-panel")
            .withChild(Header.H3("Create Schedule"))
            .withChild(scheduleForm(agentId, null)));

        List<AgentSchedule> schedules = scheduleService.schedules(agentId);
        if (schedules.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty").withInnerText("No schedules configured."));
            return panel;
        }

        for (AgentSchedule schedule : schedules) {
            String assignmentType = templateString(schedule.assignmentTemplate(), "assignmentType", AssignmentType.JOB_RUN.name());
            Div item = new Div().withClass("orch-panel");
            item.withChild(new Div().withClass("orch-meta")
                .withChild(new HtmlTag("span").withInnerText("Cron: " + nn(schedule.cronExpression())))
                .withChild(new HtmlTag("span").withInnerText("Timezone: " + nn(schedule.timezone())))
                .withChild(new HtmlTag("span").withInnerText("Next Run: " + (schedule.nextRunAt() != null ? schedule.nextRunAt().toString() : "—")))
                .withChild(new HtmlTag("span").withInnerText("Job: " + nn(schedule.jobId())))
                .withChild(new HtmlTag("span").withInnerText("Enabled: " + schedule.enabled()))
                .withChild(new HtmlTag("span").withInnerText("Assignment Type: " + assignmentType)));
            item.withChild(scheduleForm(agentId, schedule));
            panel.withChild(item);
        }
        return panel;
    }

    private Component reactionsPanel(String agentId, String errorMessage) {
        Div panel = new Div();
        panel.withChild(Header.H2("Event Reactions"));
        if (!reactionsEnabled) {
            panel.withChild(featureDisabledState("Event reactions are disabled.", "magenta.features.reactions-enabled=true"));
            return panel;
        }
        if (StringUtils.hasText(errorMessage)) {
            panel.withChild(new Div().withClass("orch-error").withInnerText(errorMessage));
        }
        panel.withChild(new Div().withClass("orch-panel")
            .withChild(Header.H3("Create Reaction"))
            .withChild(reactionForm(agentId, null)));

        List<AgentEventReaction> reactions = eventReactionService.reactions(agentId);
        if (reactions.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty").withInnerText("No event reactions configured."));
            return panel;
        }

        for (AgentEventReaction reaction : reactions) {
            String assignmentType = templateString(reaction.assignmentTemplate(), "assignmentType", AssignmentType.JOB_RUN.name());
            Div item = new Div().withClass("orch-panel");
            item.withChild(new Div().withClass("orch-meta")
                .withChild(new HtmlTag("span").withInnerText("Event Type: " + reaction.eventType().name()))
                .withChild(new HtmlTag("span").withInnerText("Filter: " + summarizeJson(reaction.filter())))
                .withChild(new HtmlTag("span").withInnerText("Action: " + reaction.actionType().name()))
                .withChild(new HtmlTag("span").withInnerText("Enabled: " + reaction.enabled()))
                .withChild(new HtmlTag("span").withInnerText("Assignment Type: " + assignmentType)));
            item.withChild(reactionForm(agentId, reaction));
            panel.withChild(item);
        }
        return panel;
    }

    private Component scheduleForm(String agentId, AgentSchedule schedule) {
        boolean edit = schedule != null;
        String url = edit
            ? "/agents/_detail/" + escapeAttr(agentId) + "/schedules/" + escapeAttr(schedule.id())
            : "/agents/_detail/" + escapeAttr(agentId) + "/schedules";

        Map<String, Object> template = schedule == null || schedule.assignmentTemplate() == null
            ? Map.of() : schedule.assignmentTemplate();
        String assignmentType = templateString(template, "assignmentType", AssignmentType.JOB_RUN.name());
        int priority = templateInt(template, "priority", 0);
        String modelOverride = templateString(template, "modelOverride", "");
        String workspaceId = templateString(template, "workspaceId", "");
        String inputJson = toJsonText(templateInput(template));
        boolean enabled = schedule == null || schedule.enabled();

        Form form = Form.create();
        if (edit) {
            form.withHxPut(url);
        } else {
            form.withHxPost(url);
        }
        form.withHxTarget("#agent-tab-panel");
        form.withHxSwap("innerHTML");

        Div grid = new Div().withClass("orch-form-grid");
        grid.withChild(label("Job ID", TextInput.create("jobId").withValue(schedule != null ? nn(schedule.jobId()) : "")));
        grid.withChild(label("Cron Expression", TextInput.create("cronExpression").withValue(schedule != null ? nn(schedule.cronExpression()) : "")));
        grid.withChild(label("Timezone", TextInput.create("timezone").withValue(schedule != null ? nn(schedule.timezone()) : "UTC")));
        grid.withChild(label("Assignment Type", assignmentTypeSelect("assignmentType", assignmentType)));
        grid.withChild(label("Priority", TextInput.number("priority").withMin("0").withMax("9").withValue(String.valueOf(priority))));
        grid.withChild(label("Model Override", TextInput.create("modelOverride").withValue(modelOverride)));
        grid.withChild(label("Workspace ID", TextInput.create("workspaceId").withValue(workspaceId)));
        grid.withChild(label("Enabled", Select.create("enabled")
            .addOption("true", "Enabled", enabled)
            .addOption("false", "Disabled", !enabled)));
        form.withChild(grid);
        form.withChild(label("Input JSON", TextArea.create("inputJson").withInnerText(inputJson)));

        Div actions = new Div().withClass("orch-actions");
        actions.withChild(Button.create(edit ? "Save Schedule" : "Create Schedule")
            .withClass("orch-primary")
            .withAttribute("type", "submit"));

        if (edit) {
            actions.withChild(Button.create(schedule.enabled() ? "Disable" : "Enable")
                .withAttribute("type", "button")
                .withAttribute("hx-post", "/agents/_detail/" + escapeAttr(agentId) + "/schedules/" + escapeAttr(schedule.id()) + "/toggle")
                .withAttribute("hx-target", "#agent-tab-panel")
                .withAttribute("hx-swap", "innerHTML"));
            actions.withChild(Button.create("Delete")
                .withAttribute("type", "button")
                .withAttribute("hx-delete", "/agents/_detail/" + escapeAttr(agentId) + "/schedules/" + escapeAttr(schedule.id()))
                .withAttribute("hx-confirm", "Delete this schedule?")
                .withAttribute("hx-target", "#agent-tab-panel")
                .withAttribute("hx-swap", "innerHTML"));
        }
        form.withChild(actions);
        return form;
    }

    private Component reactionForm(String agentId, AgentEventReaction reaction) {
        boolean edit = reaction != null;
        String url = edit
            ? "/agents/_detail/" + escapeAttr(agentId) + "/reactions/" + escapeAttr(reaction.id())
            : "/agents/_detail/" + escapeAttr(agentId) + "/reactions";

        Map<String, Object> template = reaction == null || reaction.assignmentTemplate() == null
            ? Map.of() : reaction.assignmentTemplate();
        String assignmentType = templateString(template, "assignmentType", AssignmentType.JOB_RUN.name());
        int priority = templateInt(template, "priority", 0);
        String modelOverride = templateString(template, "modelOverride", "");
        String workspaceId = templateString(template, "workspaceId", "");
        String inputJson = toJsonText(templateInput(template));
        boolean enabled = reaction == null || reaction.enabled();
        String filterJson = toJsonText(reaction == null ? Map.of() : reaction.filter());

        Form form = Form.create();
        if (edit) {
            form.withHxPut(url);
        } else {
            form.withHxPost(url);
        }
        form.withHxTarget("#agent-tab-panel");
        form.withHxSwap("innerHTML");

        Div grid = new Div().withClass("orch-form-grid");
        grid.withChild(label("Event Type", eventTypeSelect("eventType", reaction == null ? EventType.MANUAL_USER_EVENT.name() : reaction.eventType().name())));
        grid.withChild(label("Assignment Type", assignmentTypeSelect("assignmentType", assignmentType)));
        grid.withChild(label("Priority", TextInput.number("priority").withMin("0").withMax("9").withValue(String.valueOf(priority))));
        grid.withChild(label("Model Override", TextInput.create("modelOverride").withValue(modelOverride)));
        grid.withChild(label("Workspace ID", TextInput.create("workspaceId").withValue(workspaceId)));
        grid.withChild(label("Enabled", Select.create("enabled")
            .addOption("true", "Enabled", enabled)
            .addOption("false", "Disabled", !enabled)));
        form.withChild(grid);
        form.withChild(label("Filter JSON", TextArea.create("filterJson").withInnerText(filterJson)));
        form.withChild(label("Input JSON", TextArea.create("inputJson").withInnerText(inputJson)));

        Div actions = new Div().withClass("orch-actions");
        actions.withChild(Button.create(edit ? "Save Reaction" : "Create Reaction")
            .withClass("orch-primary")
            .withAttribute("type", "submit"));

        if (edit) {
            actions.withChild(Button.create(reaction.enabled() ? "Disable" : "Enable")
                .withAttribute("type", "button")
                .withAttribute("hx-post", "/agents/_detail/" + escapeAttr(agentId) + "/reactions/" + escapeAttr(reaction.id()) + "/toggle")
                .withAttribute("hx-target", "#agent-tab-panel")
                .withAttribute("hx-swap", "innerHTML"));
            actions.withChild(Button.create("Delete")
                .withAttribute("type", "button")
                .withAttribute("hx-delete", "/agents/_detail/" + escapeAttr(agentId) + "/reactions/" + escapeAttr(reaction.id()))
                .withAttribute("hx-confirm", "Delete this reaction?")
                .withAttribute("hx-target", "#agent-tab-panel")
                .withAttribute("hx-swap", "innerHTML"));
        }
        form.withChild(actions);
        return form;
    }

    private AgentSchedule buildSchedule(String agentId, String scheduleId, AgentSchedule existing, Map<String, String> params) {
        Map<String, Object> assignmentTemplate = assignmentTemplate(
            agentId,
            normalize(params.get("jobId")),
            params.get("assignmentType"),
            params.get("priority"),
            params.get("modelOverride"),
            params.get("workspaceId"),
            params.get("inputJson")
        );
        return new AgentSchedule(
            scheduleId,
            agentId,
            normalize(params.get("jobId")),
            assignmentTemplate,
            required(params.get("cronExpression"), "cronExpression is required"),
            params.getOrDefault("timezone", "UTC"),
            Boolean.parseBoolean(params.getOrDefault("enabled", "true")),
            null,
            existing == null ? null : existing.createdAt(),
            existing == null ? null : existing.updatedAt()
        );
    }

    private AgentEventReaction buildReaction(
        String agentId,
        String reactionId,
        AgentEventReaction existing,
        Map<String, String> params
    ) {
        Map<String, Object> assignmentTemplate = assignmentTemplate(
            agentId,
            normalize(params.get("jobId")),
            params.get("assignmentType"),
            params.get("priority"),
            params.get("modelOverride"),
            params.get("workspaceId"),
            params.get("inputJson")
        );
        return new AgentEventReaction(
            reactionId,
            agentId,
            parseEventType(params.get("eventType")),
            parseJsonMap(params.get("filterJson"), "filterJson"),
            ReactionActionType.ENQUEUE_ASSIGNMENT,
            assignmentTemplate,
            Boolean.parseBoolean(params.getOrDefault("enabled", "true")),
            existing == null ? null : existing.createdAt(),
            existing == null ? null : existing.updatedAt()
        );
    }

    private Map<String, Object> assignmentTemplate(
        String agentId,
        String jobId,
        String assignmentType,
        String priority,
        String modelOverride,
        String workspaceId,
        String inputJson
    ) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("agentId", agentId);
        template.put("jobId", jobId);
        template.put("assignmentType", parseAssignmentType(assignmentType).name());
        template.put("priority", parsePriority(priority));
        template.put("modelOverride", normalize(modelOverride));
        template.put("workspaceId", normalize(workspaceId));
        template.put("input", parseJsonMap(inputJson, "inputJson"));
        return template;
    }

    private Component featureDisabledState(String message, String flag) {
        return new Div().withClass("dashboard-empty")
            .withChild(new HtmlTag("strong").withInnerText(message))
            .withChild(new HtmlTag("br"))
            .withChild(new HtmlTag("span").withInnerText("Enable with " + flag + "."));
    }

    private Select assignmentTypeSelect(String name, String selected) {
        Select select = Select.create(name);
        for (AssignmentType type : AssignmentType.values()) {
            select.addOption(type.name(), type.name(), type.name().equals(selected));
        }
        return select;
    }

    private Select eventTypeSelect(String name, String selected) {
        Select select = Select.create(name);
        for (EventType type : EventType.values()) {
            select.addOption(type.name(), type.name(), type.name().equals(selected));
        }
        return select;
    }

    private Map<String, Object> parseJsonMap(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Object parsed = JSON.readValue(json, Object.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(fieldName + " must be a JSON object");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                value.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON for " + fieldName);
        }
    }

    private EventType parseEventType(String value) {
        try {
            return EventType.valueOf(required(value, "eventType is required").trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid eventType");
        }
    }

    private AssignmentType parseAssignmentType(String value) {
        try {
            return AssignmentType.valueOf((value == null || value.isBlank() ? AssignmentType.JOB_RUN.name() : value.trim().toUpperCase()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid assignmentType");
        }
    }

    private int parsePriority(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("priority must be a number");
        }
    }

    private Map<String, Object> templateInput(Map<String, Object> template) {
        Object input = template.get("input");
        if (!(input instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String templateString(Map<String, Object> template, String key, String fallback) {
        Object value = template == null ? null : template.get(key);
        return value == null ? fallback : value.toString();
    }

    private int templateInt(Map<String, Object> template, String key, int fallback) {
        Object value = template == null ? null : template.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String toJsonText(Object value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String summarizeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        String json = toJsonText(value).replace('\n', ' ');
        return json.length() <= 80 ? json : json.substring(0, 77) + "...";
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @GetMapping("/agents/_detail/{agentId}/workspace")
    @ResponseBody
    public String agentWorkspaceTab(@PathVariable String agentId) {
        AgentProfile agent = agentProfileService.get(agentId);
        Div panel = new Div();
        panel.withChild(Header.H2("Workspace"));

        Workspace workspace = workspaceService.agentWorkspace(agent.id(), agent.name());
        List<WorkspaceLink> links = workspaceService.links(workspace.id());
        List<WorkspaceLease> activeLeases = workspaceService.activeLeases(workspace.id());
        String outputHint = workspaceOutputHint(workspace);

        panel.withChild(new Div().withClass("orch-meta")
            .withChild(new HtmlTag("span").withInnerText("Agent: " + (agent.name() != null ? agent.name() : agentId)))
            .withChild(new HtmlTag("span").withInnerText("Agent ID: " + agent.id()))
            .withChild(new HtmlTag("span").withInnerText("Workspace ID: " + workspace.id()))
            .withChild(new HtmlTag("span").withInnerText("Owner: "
                + (workspace.ownerType() != null ? workspace.ownerType().name() : "—")
                + ":" + nn(workspace.ownerId())))
            .withChild(new HtmlTag("span").withInnerText("Display Name: " + nn(workspace.displayName())))
            .withChild(new HtmlTag("span").withInnerText("Root Relative Path: " + nn(workspace.rootRelativePath())))
            .withChild(new HtmlTag("span").withInnerText("Output Directory Hint: " + outputHint))
            .withChild(new HtmlTag("span").withInnerText("Metadata: " + nn(workspace.metadataJson())))
            .withChild(new HtmlTag("span").withInnerText("Updated: "
                + (workspace.updatedAt() != null ? formatSince(workspace.updatedAt()) : "—"))));

        panel.withChild(Header.H3("Active Leases"));
        if (activeLeases.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty")
                .withInnerText("No active leases."));
        } else {
            Table leasesTable = Table.create()
                .withHeaders("Holder", "Mode", "Expires", "Created")
                .withClass("dashboard-table");
            for (WorkspaceLease lease : activeLeases) {
                leasesTable.addRow(
                    new HtmlTag("span").withInnerText(nn(lease.holderType()) + ":" + nn(lease.holderId())),
                    new HtmlTag("span").withInnerText(lease.mode() != null ? lease.mode().name() : "—"),
                    new HtmlTag("span").withInnerText(lease.expiresAt() != null ? lease.expiresAt().toString() : "—"),
                    new HtmlTag("span").withInnerText(lease.createdAt() != null ? formatSince(lease.createdAt()) : "—")
                );
            }
            panel.withChild(leasesTable);
        }

        panel.withChild(Header.H3("Workspace Links"));
        if (links.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty")
                .withInnerText("No workspace links configured."));
            return panel.render();
        }

        Table table = Table.create()
            .withHeaders("Label", "Type", "Target", "Access")
            .withClass("dashboard-table");
        for (WorkspaceLink link : links) {
            String access = (link.readable() ? "r" : "-") + (link.writable() ? "w" : "-");
            table.addRow(
                new HtmlTag("span").withInnerText(link.label() != null ? link.label() : "link"),
                new HtmlTag("span").withInnerText(link.linkType() != null ? link.linkType().name() : "PATH"),
                new HtmlTag("code").withInnerText(link.target() != null ? link.target() : "—"),
                new HtmlTag("span").withInnerText(access)
            );
        }
        panel.withChild(table);
        return panel.render();
    }

    private String workspaceOutputHint(Workspace workspace) {
        if (workspace == null || workspace.ownerType() == null || !StringUtils.hasText(workspace.ownerId())) {
            return "—";
        }
        return switch (workspace.ownerType()) {
            case AGENT -> "agents/" + workspace.ownerId() + "/workspace/outputs";
            case JOB -> "jobs/" + workspace.ownerId() + "/outputs";
            case PROJECT -> "projects/" + workspace.ownerId() + "/workspace";
        };
    }

    @GetMapping("/agents/_detail/{agentId}/outputs")
    @ResponseBody
    public String agentOutputsTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("Recent Outputs"));

        List<RunOutputArtifact> outputs = queryOutputs(agentId, null, null, null, null, 20);
        if (outputs.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty").withInnerText("No recent outputs."));
            return panel.render();
        }

        Table table = Table.create()
            .withHeaders("Name", "Type", "Plan", "Run", "Created")
            .withClass("dashboard-table");
        for (var o : outputs) {
            table.addRow(
                new HtmlTag("span").withInnerText(o.outputName() != null ? o.outputName() : "output"),
                new HtmlTag("span").withInnerText(o.artifactType() != null ? o.artifactType() : "—"),
                new HtmlTag("span").withInnerText(o.planId() != null ? o.planId() : "—"),
                new HtmlTag("span").withInnerText(o.runId() != null ? o.runId() : "—"),
                new HtmlTag("span").withInnerText(o.createdAt() != null ? formatSince(o.createdAt()) : "—")
            );
        }
        panel.withChild(table);
        return panel.render();
    }

    @GetMapping("/agents/_detail/{agentId}/exec")
    @ResponseBody
    public String agentExecTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("Shell Exec"));
        panel.withChild(new Paragraph("Run a bounded shell command in this agent workspace."));
        Form form = Form.create()
            .withHxPost("/agents/_detail/" + agentId + "/exec")
            .withHxTarget("#agent-exec-result")
            .withHxSwap("innerHTML");
        form.withChild(label("Command", TextInput.create("command").withPlaceholder("pwd")));
        form.withChild(label("Working Directory", TextInput.create("workingDirectory").withValue("workspace")));
        form.withChild(Button.create("Run").withClass("orch-primary").withAttribute("type", "submit"));
        panel.withChild(form);
        panel.withChild(new Div().withId("agent-exec-result"));
        return panel.render();
    }

    @PostMapping("/agents/_detail/{agentId}/exec")
    @ResponseBody
    public String execInAgent(@PathVariable String agentId, @RequestParam Map<String, String> params) {
        AgentProfile agent = agentProfileService.get(agentId);
        try {
            AgentShellToolService shellService = execShellService();
            if (shellService == null) {
                return new Div().withClass("orch-error").withInnerText("Shell execution service is unavailable.").render();
            }
            OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
                agentId, agent.name(), null, null, null, null, null, null));
            try {
                var result = shellService.exec(
                    required(params.get("command"), "command is required"),
                    params.getOrDefault("workingDirectory", "workspace"),
                    null);
                return new Div().withClass("orch-panel")
                    .withChild(new Div().withClass("orch-meta")
                        .withChild(new HtmlTag("span").withInnerText("Exit: " + (result.exitCode() != null ? result.exitCode() : "timeout"))))
                    .withChild(new HtmlTag("pre").withInnerText(nn(result.stdout())))
                    .withChild(new HtmlTag("pre").withInnerText(nn(result.stderr())))
                    .render();
            } finally {
                OrchestrationTaskContextHolder.clear();
            }
        } catch (Exception exception) {
            return new Div().withClass("orch-error").withInnerText("Error: " + exception.getMessage()).render();
        }
    }

    private AgentShellToolService execShellService() {
        return execShellServiceRef == null ? null : execShellServiceRef.getIfAvailable();
    }

    @GetMapping("/agents/_detail/{agentId}/history")
    @ResponseBody
    public String agentHistoryTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("History"));

        List<WorkAssignment> assignments = assignmentService.assignments(agentId);
        if (assignments.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty")
                .withInnerText("No assignment history for this agent."));
            return panel.render();
        }

        // Show terminal assignments (COMPLETED, FAILED, CANCELLED) in chronological order
        List<WorkAssignment> history = assignments.stream()
            .filter(a -> a.status() != null && a.status().isTerminal())
            .sorted(java.util.Comparator.comparing(
                a -> a.completedAt() != null ? a.completedAt() : a.createdAt(),
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .toList();

        if (history.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty")
                .withInnerText("No completed assignments yet. Active items appear in the Queue tab."));
            return panel.render();
        }

        Table table = Table.create()
            .withHeaders("Type", "Status", "Priority", "Job", "Run", "Completed")
            .withClass("dashboard-table");
        for (WorkAssignment a : history) {
            table.addRow(
                new HtmlTag("span").withInnerText(a.assignmentType() != null ? a.assignmentType().name() : "—"),
                statusBadgeHtml(a.status() != null ? a.status().name() : "unknown"),
                new HtmlTag("span").withInnerText(String.valueOf(a.priority())),
                new HtmlTag("span").withInnerText(a.jobId() != null ? a.jobId() : "—"),
                new HtmlTag("span").withInnerText(
                    a.output().containsKey("taskRunId") ? String.valueOf(a.output().get("taskRunId"))
                    : a.output().containsKey("workflowRunId") ? String.valueOf(a.output().get("workflowRunId"))
                    : a.output().containsKey("jobId") ? "job:" + a.output().get("jobId")
                    : "—"),
                new HtmlTag("span").withInnerText(a.completedAt() != null ? formatSince(a.completedAt()) : "—")
            );
        }
        panel.withChild(table);

        List<io.mindspice.magenta2.ai.chat.model.ChatSession> agentChats = chatService.listAgentSessions(agentId);
        panel.withChild(Header.H2("Agent Chats"));
        if (agentChats.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty")
                .withInnerText("No saved agent conversations yet."));
        } else {
            Div chats = new Div().withClass("agent-chat-history-list");
            for (var session : agentChats) {
                chats.withChild(new Div().withClass("agent-chat-history-item")
                    .withChild(new HtmlTag("strong").withInnerText(
                        session.title() != null ? session.title() : session.conversationId()))
                    .withChild(new HtmlTag("code").withInnerText(session.conversationId())));
            }
            panel.withChild(chats);
        }

        return panel.render();
    }

    @GetMapping("/agents/_detail/{agentId}/submit")
    @ResponseBody
    public String agentSubmitTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("Submit Work"));
        panel.withChild(new Div().withId("agent-submit-container")
            .withAttribute("hx-get", "/agents/_submit-form/" + escapeAttr(agentId))
            .withAttribute("hx-trigger", "load")
            .withAttribute("hx-swap", "innerHTML")
            .withChild(loadingPlaceholder()));
        return panel.render();
    }

    private String renderAssignmentList(String agentId, String title, List<WorkAssignment> assignments) {
        Div panel = new Div();
        panel.withChild(Header.H2(title));

        if (assignments.isEmpty()) {
            panel.withChild(new Div().withClass("dashboard-empty").withInnerText("No assignments."));
            return panel.render();
        }

        Table table = Table.create()
            .withHeaders("Type", "Status", "Priority", "Progress", "Lease", "Job", "Created", "Actions")
            .withClass("dashboard-table");
        for (var a : assignments) {
            Div actions = new Div().withClass("orch-actions");
            if (a.status() == OrchestrationStatus.QUEUED || a.status() == OrchestrationStatus.RUNNING) {
                actions.withChild(Button.create("Pause")
                    .withAttribute("hx-post", "/agents/_detail/" + agentId + "/queue/" + a.id() + "/pause")
                    .withAttribute("hx-target", "#agent-tab-panel")
                    .withAttribute("hx-swap", "innerHTML"));
            }
            if (a.status() == OrchestrationStatus.WAITING || a.status() == OrchestrationStatus.PAUSED) {
                actions.withChild(Button.create("Resume")
                    .withAttribute("hx-post", "/agents/_detail/" + agentId + "/queue/" + a.id() + "/resume")
                    .withAttribute("hx-target", "#agent-tab-panel")
                    .withAttribute("hx-swap", "innerHTML"));
            }
            if (a.status() != null && !a.status().isTerminal()) {
                actions.withChild(Button.create("Cancel")
                    .withAttribute("hx-post", "/agents/_detail/" + agentId + "/queue/" + a.id() + "/cancel")
                    .withAttribute("hx-target", "#agent-tab-panel")
                    .withAttribute("hx-swap", "innerHTML"));
            }
            actions.withChild(Button.create("Diagnostics")
                .withAttribute("hx-get", "/agents/_detail/" + agentId + "/queue/" + a.id() + "/diagnostics")
                .withAttribute("hx-target", "#assignment-diagnostics-panel")
                .withAttribute("hx-swap", "innerHTML"));
            if (a.status() == OrchestrationStatus.RUNNING) {
                actions.withChild(Button.create("Force Interrupt")
                    .withClass("orch-danger")
                    .withAttribute("hx-get", "/agents/_detail/" + agentId + "/queue/" + a.id() + "/diagnostics")
                    .withAttribute("hx-target", "#assignment-diagnostics-panel")
                    .withAttribute("hx-swap", "innerHTML"));
            }
            Component status = statusBadgeHtml(a.status() != null ? a.status().name() : "unknown");
            if (isSuspectedStuck(a)) {
                status = new Div()
                    .withChild(status)
                    .withChild(new HtmlTag("span").withClass("orch-status-chip disabled").withInnerText("suspected stuck"));
            }
            table.addRow(
                new HtmlTag("span").withInnerText(a.assignmentType() != null ? a.assignmentType().name() : "—"),
                status,
                new HtmlTag("span").withInnerText(String.valueOf(a.priority())),
                new HtmlTag("span").withInnerText(a.lastProgressAt() != null ? formatSince(a.lastProgressAt()) : "—"),
                new HtmlTag("span").withInnerText(a.leaseExpiresAt() != null ? "expires " + formatSinceFuture(a.leaseExpiresAt()) : "—"),
                new HtmlTag("span").withInnerText(a.jobId() != null ? a.jobId() : "—"),
                new HtmlTag("span").withInnerText(a.createdAt() != null ? formatSince(a.createdAt()) : "—"),
                actions
            );
        }
        panel.withChild(table);
        panel.withChild(new Div().withId("assignment-diagnostics-panel"));

        return panel.render();
    }

    private Component assignmentDiagnosticsPanel(String agentId, AssignmentDiagnostics diagnostics) {
        WorkAssignment a = diagnostics.assignment();
        Div panel = new Div().withClass("orch-panel assignment-diagnostics");
        panel.withChild(Header.H3("Assignment Diagnostics"));
        Div meta = new Div().withClass("orch-form-grid");
        meta.withChild(agentMetaItem("Assignment", a.id()));
        meta.withChild(agentMetaItem("Status", a.status() != null ? a.status().name() : "unknown"));
        meta.withChild(agentMetaItem("Last Progress", diagnostics.lastProgressAt() != null ? formatSince(diagnostics.lastProgressAt()) : "—"));
        meta.withChild(agentMetaItem("Last Heartbeat", diagnostics.lastHeartbeatAt() != null ? formatSince(diagnostics.lastHeartbeatAt()) : "—"));
        meta.withChild(agentMetaItem("Progress Age", formatDuration(diagnostics.progressAge())));
        meta.withChild(agentMetaItem("Heartbeat Age", formatDuration(diagnostics.heartbeatAge())));
        meta.withChild(agentMetaItem("Lease Owner", a.leaseOwner() != null ? a.leaseOwner() : "—"));
        meta.withChild(agentMetaItem("Lease Expiry", a.leaseExpiresAt() != null ? formatSinceFuture(a.leaseExpiresAt()) : "—"));
        meta.withChild(agentMetaItem("Conversation", diagnostics.conversationId() != null ? diagnostics.conversationId() : "—"));
        meta.withChild(agentMetaItem("Build Commit", diagnostics.buildCommit()));
        panel.withChild(meta);
        if (diagnostics.suspectedStuck()) {
            panel.withChild(new Div().withClass("orch-error")
                .withInnerText("Suspected stuck: heartbeat is recent but progress has not changed for at least 15 minutes."));
        }
        if (a.status() == OrchestrationStatus.RUNNING) {
            Form form = Form.create();
            form.withHxPost("/agents/_detail/" + escapeAttr(agentId) + "/queue/" + escapeAttr(a.id()) + "/force-interrupt");
            form.withAttribute("hx-target", "#agent-tab-panel");
            form.withAttribute("hx-swap", "innerHTML");
            form.withClass("orch-form-inline");
            form.withChild(TextInput.create("reason").withPlaceholder("Operator reason"));
            form.withChild(Button.create("Force Interrupt")
                .withAttribute("type", "submit")
                .withClass("orch-danger"));
            panel.withChild(form);
        }
        if (!diagnostics.linkedRuns().isEmpty()) {
            Table linked = Table.create().withHeaders("Run Type", "Run ID", "Parent", "Status", "Error").withClass("dashboard-table");
            for (var run : diagnostics.linkedRuns()) {
                linked.addRow(
                    new HtmlTag("span").withInnerText(run.type()),
                    new HtmlTag("span").withInnerText(run.id()),
                    new HtmlTag("span").withInnerText(run.parentId() != null ? run.parentId() : "—"),
                    statusBadgeHtml(run.status()),
                    new HtmlTag("span").withInnerText(run.errorText() != null ? run.errorText() : "—")
                );
            }
            panel.withChild(Header.H3("Linked Runs"));
            panel.withChild(linked);
        }
        if (!diagnostics.auditEvents().isEmpty()) {
            Table audit = Table.create().withHeaders("Seq", "Type", "Model", "Message").withClass("dashboard-table");
            for (var event : diagnostics.auditEvents()) {
                audit.addRow(
                    new HtmlTag("span").withInnerText(String.valueOf(event.sequence())),
                    new HtmlTag("span").withInnerText(event.eventType()),
                    new HtmlTag("span").withInnerText(event.model() != null ? event.model() : "—"),
                    new HtmlTag("span").withInnerText(firstNonBlank(event.messageText(), event.resultSummary(), event.errorType(), "—"))
                );
            }
            panel.withChild(Header.H3("Recent Audit Events"));
            panel.withChild(audit);
        }
        return panel;
    }

    @PostMapping("/agents/_lifecycle/{agentId}/enable")
    @ResponseBody
    public String enableAgentLifecycle(
        @PathVariable String agentId,
        @RequestParam(value = "view", required = false) String view
    ) {
        agentProfileService.enable(agentId, true);
        return "list".equalsIgnoreCase(view) ? agentList(null) : agentDetailFragment(agentId);
    }

    @PostMapping("/agents/_lifecycle/{agentId}/disable")
    @ResponseBody
    public String disableAgentLifecycle(
        @PathVariable String agentId,
        @RequestParam(value = "view", required = false) String view
    ) {
        agentProfileService.disable(agentId);
        return "list".equalsIgnoreCase(view) ? agentList(null) : agentDetailFragment(agentId);
    }

    @GetMapping("/agents/_lifecycle/{agentId}/delete-confirm")
    @ResponseBody
    public String deleteAgentLifecycleConfirm(@PathVariable String agentId) {
        AgentProfile agent = agentProfileService.get(agentId);
        Div panel = new Div().withClass("orch-panel")
            .withChild(Header.H3("Delete Agent: " + nn(agent.name())))
            .withChild(new Paragraph("Choose an explicit lifecycle action. No data is removed by default."));
        panel.withChild(new Div().withClass("orch-actions")
            .withChild(Button.create("Disable Only")
                .withAttribute("hx-post", "/agents/_lifecycle/" + escapeAttr(agentId) + "/disable")
                .withAttribute("hx-target", "#agent-docker-status-" + escapeAttr(agentId))
                .withAttribute("hx-swap", "innerHTML"))
            .withChild(Button.create("Archive + Disable")
                .withAttribute("hx-post", "/agents/_lifecycle/" + escapeAttr(agentId) + "/archive-and-disable")
                .withAttribute("hx-target", "#agent-docker-status-" + escapeAttr(agentId))
                .withAttribute("hx-swap", "innerHTML")));

        Form hardDeleteForm = Form.create();
        hardDeleteForm.withHxPost("/agents/_lifecycle/" + escapeAttr(agentId) + "/hard-delete");
        hardDeleteForm.withAttribute("hx-target", "#agent-docker-status-" + escapeAttr(agentId));
        hardDeleteForm.withAttribute("hx-swap", "innerHTML");
        hardDeleteForm.withChild(new HtmlTag("label").withInnerText("Type DELETE " + agentId + " to hard-delete"));
        hardDeleteForm.withChild(TextInput.create("confirmationText")
            .withPlaceholder("DELETE " + agentId));
        hardDeleteForm.withChild(Button.create("Hard Delete").withClass("orch-danger"));
        panel.withChild(hardDeleteForm);
        return panel.render();
    }

    @PostMapping("/agents/_lifecycle/{agentId}/archive-and-disable")
    @ResponseBody
    public String archiveAndDisableAgentLifecycle(@PathVariable String agentId) {
        try {
            agentProfileService.archiveAndDisable(agentId);
            return agentDetailFragment(agentId);
        } catch (Exception exception) {
            return new Div().withClass("orch-error").withInnerText("Error: " + exception.getMessage()).render();
        }
    }

    @PostMapping("/agents/_lifecycle/{agentId}/hard-delete")
    @ResponseBody
    public String hardDeleteAgentLifecycle(
        @PathVariable String agentId,
        @RequestParam("confirmationText") String confirmationText
    ) {
        try {
            agentProfileService.hardDelete(agentId, confirmationText);
            return new Div().withClass("orch-status")
                .withInnerText("Agent deleted. Refreshing agent list...")
                .withAttribute("hx-get", "/agents/_list")
                .withAttribute("hx-trigger", "load")
                .withAttribute("hx-target", "#agent-list")
                .withAttribute("hx-swap", "innerHTML")
                .render();
        } catch (Exception exception) {
            return new Div().withClass("orch-error").withInnerText("Error: " + exception.getMessage()).render();
        }
    }

    // ── Agent profile editor HTMX partials ──

    @GetMapping("/agents/_editor/{agentId}")
    @ResponseBody
    public String agentEditor(@PathVariable String agentId) {
        AgentProfile agent = agentProfileService.get(agentId);
        Div container = new Div();
        container.withChild(agentIdentitySection(agent));
        container.withChild(agentPromptSection(agent));
        container.withChild(agentToolsSection(agent));
        container.withChild(agentShellSection(agent));
        return container.render();
    }

    @GetMapping("/agents/_editor/{agentId}/profile")
    @ResponseBody
    public String agentProfileSection(@PathVariable String agentId) {
        return agentIdentitySection(agentProfileService.get(agentId)).render();
    }

    @PutMapping("/agents/_editor/{agentId}/profile")
    @ResponseBody
    public String saveAgentProfileSection(
        @PathVariable String agentId,
        @RequestParam("name") String name,
        @RequestParam("status") String status,
        @RequestParam("defaultModel") String defaultModel,
        @RequestParam(value = "directLineEnabled", defaultValue = "false") String directLineEnabled
    ) {
        try {
            AgentProfile current = agentProfileService.get(agentId);
            AgentProfile updated = agentProfileService.update(agentId, new AgentProfile(
                agentId, name,
                "ACTIVE".equalsIgnoreCase(status)
                    ? io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus.ACTIVE
                    : io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus.DISABLED,
                defaultModel, current.systemPrompt(),
                current.approvedTools(), current.allowedShellCommands(),
                "true".equalsIgnoreCase(directLineEnabled) || "on".equalsIgnoreCase(directLineEnabled),
                null, null
            ));
            return agentIdentitySection(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-error").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/agents/_editor/{agentId}/prompt")
    @ResponseBody
    public String agentPromptSection(@PathVariable String agentId) {
        return agentPromptSection(agentProfileService.get(agentId)).render();
    }

    @PutMapping("/agents/_editor/{agentId}/prompt")
    @ResponseBody
    public String saveAgentPrompt(
        @PathVariable String agentId,
        @RequestParam("systemPrompt") String systemPrompt
    ) {
        try {
            AgentProfile current = agentProfileService.get(agentId);
            AgentProfile updated = agentProfileService.update(agentId, new AgentProfile(
                agentId, current.name(), current.status(), current.defaultModel(),
                systemPrompt, current.approvedTools(), current.allowedShellCommands(),
                current.directLineEnabled(), null, null
            ));
            return agentPromptSection(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-error").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/agents/_editor/{agentId}/tools")
    @ResponseBody
    public String agentToolsSection(@PathVariable String agentId) {
        return agentToolsSection(agentProfileService.get(agentId)).render();
    }

    @PutMapping("/agents/_editor/{agentId}/tools")
    @ResponseBody
    public String saveAgentTools(
        @PathVariable String agentId,
        @RequestParam(value = "approvedTools", defaultValue = "") String approvedTools
    ) {
        try {
            java.util.List<String> tools = parseCsv(approvedTools);
            AgentProfile current = agentProfileService.get(agentId);
            AgentProfile updated = agentProfileService.update(agentId, new AgentProfile(
                agentId, current.name(), current.status(), current.defaultModel(),
                current.systemPrompt(), tools, current.allowedShellCommands(),
                current.directLineEnabled(), null, null
            ));
            return agentToolsSection(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-error").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    @GetMapping("/agents/_editor/{agentId}/shell")
    @ResponseBody
    public String agentShellSection(@PathVariable String agentId) {
        return agentShellSection(agentProfileService.get(agentId)).render();
    }

    @PutMapping("/agents/_editor/{agentId}/shell")
    @ResponseBody
    public String saveAgentShell(
        @PathVariable String agentId,
        @RequestParam(value = "allowedShellCommands", defaultValue = "") String allowedShellCommands
    ) {
        try {
            java.util.List<String> commands = parseCsv(allowedShellCommands);
            AgentProfile current = agentProfileService.get(agentId);
            AgentProfile updated = agentProfileService.update(agentId, new AgentProfile(
                agentId, current.name(), current.status(), current.defaultModel(),
                current.systemPrompt(), current.approvedTools(), commands,
                current.directLineEnabled(), null, null
            ));
            return agentShellSection(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-error").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    // ── Editor section helpers ──

    private Component agentIdentitySection(AgentProfile agent) {
        Form form = Form.create();
        form.withHxPut("/agents/_editor/" + agent.id() + "/profile");
        form.withHxTarget("this");
        form.withHxSwap("outerHTML");

        Div grid = new Div().withClass("orch-form-grid");
        grid.withChild(label("Name", TextInput.create("name")
            .withValue(nn(agent.name()))));
        grid.withChild(label("Status", Select.create("status")
            .addOption("ACTIVE", "Active", "ACTIVE".equals(agent.status() != null ? agent.status().name() : ""))
            .addOption("DISABLED", "Disabled", "DISABLED".equals(agent.status() != null ? agent.status().name() : ""))));
        grid.withChild(label("Default Model", modelSelectWithCurrent(
            "defaultModel", agent.defaultModel(), chatService.availableModels())));
        grid.withChild(label("Direct Line", Select.create("directLineEnabled")
            .addOption("true", "Enabled", agent.directLineEnabled())
            .addOption("false", "Disabled", !agent.directLineEnabled())));

        form.withChild(grid);
        form.withChild(new Div().withClass("orch-actions")
            .withChild(Button.create("Save").withClass("orch-primary").withAttribute("type", "submit")));
        form.withChild(new Div().withClass("orch-status")
            .withInnerText("Updated: " + (agent.updatedAt() != null ? formatSince(agent.updatedAt()) : "never")));

        Div section = new Div().withClass("agent-editor-section");
        section.withChild(Header.H3("Identity"));
        section.withChild(form);
        return section;
    }

    private Component agentPromptSection(AgentProfile agent) {
        Form form = Form.create();
        form.withHxPut("/agents/_editor/" + agent.id() + "/prompt");
        form.withHxTarget("this");
        form.withHxSwap("outerHTML");

        form.withChild(TextArea.create("systemPrompt")
            .withClass("agent-prompt-textarea")
            .withInnerText(nn(agent.systemPrompt())));

        form.withChild(new Div().withClass("orch-actions")
            .withChild(Button.create("Save").withClass("orch-primary").withAttribute("type", "submit")));

        Div section = new Div().withClass("agent-editor-section");
        section.withChild(Header.H3("System Prompt"));
        section.withChild(form);
        return section;
    }

    private Component agentToolsSection(AgentProfile agent) {
        Form form = Form.create();
        form.withHxPut("/agents/_editor/" + agent.id() + "/tools");
        form.withHxTarget("this");
        form.withHxSwap("outerHTML");

        String currentTools = agent.approvedTools() != null
            ? String.join(", ", agent.approvedTools()) : "";

        form.withChild(TextInput.create("approvedTools")
            .withValue(currentTools)
            .withPlaceholder("tool1, tool2, ..."));
        form.withChild(new Div().withClass("dashboard-muted")
            .withInnerText("Comma-separated list. Use * for all available tools."));
        form.withChild(new Div().withClass("agent-tool-chips")
            .withInnerText(agent.approvedTools() != null && !agent.approvedTools().isEmpty()
                ? agent.approvedTools().size() + " tools configured"
                : "No tools configured"));

        form.withChild(new Div().withClass("orch-actions")
            .withChild(Button.create("Save").withClass("orch-primary").withAttribute("type", "submit")));

        Div section = new Div().withClass("agent-editor-section");
        section.withChild(Header.H3("Approved Tools"));
        section.withChild(form);
        return section;
    }

    private Component agentShellSection(AgentProfile agent) {
        Form form = Form.create();
        form.withHxPut("/agents/_editor/" + agent.id() + "/shell");
        form.withHxTarget("this");
        form.withHxSwap("outerHTML");

        String currentCommands = agent.allowedShellCommands() != null
            ? String.join(", ", agent.allowedShellCommands()) : "";

        form.withChild(TextInput.create("allowedShellCommands")
            .withValue(currentCommands)
            .withPlaceholder("ls, cat, grep, ..."));
        form.withChild(new Div().withClass("dashboard-muted")
            .withInnerText("Comma-separated list. Use * for all commands. Bare executable names only."));

        form.withChild(new Div().withClass("orch-actions")
            .withChild(Button.create("Save").withClass("orch-primary").withAttribute("type", "submit")));

        Div section = new Div().withClass("agent-editor-section");
        section.withChild(Header.H3("Shell Allowlist"));
        section.withChild(form);
        return section;
    }

    // ── Submit work panel ──

    @GetMapping("/agents/_submit-form/{agentId}")
    @ResponseBody
    public String agentSubmitForm(@PathVariable String agentId) {
        AgentProfile agent = agentProfileService.get(agentId);

        Form form = Form.create();
        form.withId("agent-submit-form-" + agentId);
        form.withHxPost("/agents/_submit/" + agentId);
        form.withHxTarget("#agent-submit-result-" + agentId);
        form.withHxSwap("innerHTML");

        form.withChild(label("Assignment Type", Select.create("assignmentType")
            .addOption("TASK_RUN", "Task Run", true)
            .addOption("WORKFLOW_RUN", "Workflow Run", false)
            .addOption("JOB_RUN", "Job Run", false)));

        form.withChild(label("Plan/Workflow/Job ID", TextInput.create("targetId")
            .withPlaceholder("Enter plan, workflow, or job ID")));

        form.withChild(label("Priority (0-9)", TextInput.number("priority")
            .withMin("0").withMax("9").withValue("0")));

        form.withChild(label("Model Override", modelSelectWithCurrent(
            "modelOverride", null, chatService.availableModels())));

        form.withChild(new Div().withClass("orch-actions")
            .withChild(Button.create("Submit").withClass("orch-primary").withAttribute("type", "submit")));

        Div container = new Div();
        container.withChild(form);
        container.withChild(new Div().withId("agent-submit-result-" + agentId));
        return container.render();
    }

    @PostMapping("/agents/_submit/{agentId}")
    @ResponseBody
    public String submitToAgent(
        @PathVariable String agentId,
        @RequestParam("assignmentType") String assignmentType,
        @RequestParam("targetId") String targetId,
        @RequestParam(value = "priority", defaultValue = "0") int priority,
        @RequestParam(value = "modelOverride", defaultValue = "") String modelOverride
    ) {
        try {
            AgentProfile agent = agentProfileService.get(agentId);
            AssignmentType type = AssignmentType.valueOf(assignmentType);

            java.util.Map<String, Object> input = new java.util.LinkedHashMap<>();
            if (type == AssignmentType.TASK_RUN) {
                input.put("taskId", targetId);
            } else if (type == AssignmentType.WORKFLOW_RUN) {
                input.put("workflowId", targetId);
            } else if (type == AssignmentType.JOB_RUN) {
                input.put("jobId", targetId);
            }

            String jobId = type == AssignmentType.JOB_RUN ? targetId : null;
            WorkAssignment created = assignmentService.create(new AssignmentRequest(
                agentId, jobId, null, type, priority,
                modelOverride.isBlank() ? null : modelOverride.trim(),
                null, input
            ));

            return new Div().withClass("orch-status")
                .withChild(new HtmlTag("strong").withInnerText("Assignment created: " + created.id()))
                .withChild(new HtmlTag("br"))
                .withChild(new HtmlTag("span").withInnerText(
                    "Type: " + created.assignmentType() + " | Status: "
                    + (created.status() != null ? created.status().name() : "unknown")
                    + " | Priority: " + created.priority()))
                .withChild(new HtmlTag("br"))
                .withChild(new HtmlTag("a")
                    .withAttribute("href", "/agents/_detail/" + agentId + "/queue")
                    .withAttribute("hx-get", "/agents/_detail/" + agentId + "/queue")
                    .withAttribute("hx-target", "#agent-tab-panel")
                    .withAttribute("hx-swap", "innerHTML")
                    .withInnerText("View queue"))
                .render();
        } catch (Exception e) {
            return new Div().withClass("orch-error")
                .withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private java.util.List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    // ════════════════════════════════════════════════════════════════
    //  Settings (HTMX form-based)
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/settings")
    @ResponseBody
    public String settings() {
        RuntimeSettings s = runtimeSettingsService.get();

        Component body = new Div()
            .withId("settings-page")
            .withAttribute("data-orchestration-page", "settings")
            .withChild(Header.H1("Settings"))
            .withChild(new Paragraph("Runtime defaults, model routing, and context controls."))
            .withChild(settingsForm(s));
        return renderPage(body, "/settings");
    }

    @PutMapping("/settings")
    @ResponseBody
    public String saveSettings(@RequestParam Map<String, String> params) {
        try {
            RuntimeSettings current = runtimeSettingsService.get();
            RuntimeSettings updated = runtimeSettingsService.save(new RuntimeSettings(
                nn(params.getOrDefault("defaultAgentId", current.defaultAgentId())),
                nn(params.getOrDefault("defaultAgentName", current.defaultAgentName())),
                nn(params.getOrDefault("defaultModel", current.defaultModel())),
                nn(params.getOrDefault("planningModel", current.planningModel())),
                nn(params.getOrDefault("summaryModel", current.summaryModel())),
                nn(params.getOrDefault("compactionModel", current.compactionModel())),
                parseIntOrNull(params.getOrDefault("contextBufferPercent", String.valueOf(current.contextBufferPercent()))),
                nn(params.getOrDefault("systemChatModel", current.systemChatModel())),
                nn(params.getOrDefault("systemChatPrompt", current.systemChatPrompt())),
                nn(params.getOrDefault("systemChatApprovedTools", current.systemChatApprovedTools())),
                parseIntOrNull(params.getOrDefault("systemChatContextLimit", String.valueOf(current.systemChatContextLimit()))),
                "true".equalsIgnoreCase(params.getOrDefault("systemChatEnabled",
                    current.systemChatEnabled() == null || current.systemChatEnabled() ? "true" : "false"))
            ));
            return settingsForm(updated).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status orch-status-error").withInnerText("Error: " + e.getMessage()).render();
        }
    }

    private Component settingsForm(RuntimeSettings s) {
        Form form = Form.create();
        form.withHxPut("/settings");
        form.withHxTarget("#settings-form-container");
        form.withHxSwap("innerHTML");

        List<String> models = new ArrayList<>(chatService.availableModels());

        form.withChild(new Div().withClass("orch-layout")
            .withChild(new Div().withClass("orch-panel")
                .withChild(Header.H2("Model Routing"))
                .withChild(new Div().withClass("orch-form-grid")
                    .withChild(label("Default Agent ID", TextInput.create("defaultAgentId")
                        .withId("settings-default-agent-id")
                        .withValue(nn(s.defaultAgentId()))))
                    .withChild(label("Default Agent Name", TextInput.create("defaultAgentName")
                        .withId("settings-default-agent-name")
                        .withValue(nn(s.defaultAgentName()))))
                    .withChild(label("Default Model", modelSelectWithCurrent("defaultModel", s.defaultModel(), models)
                        .withId("settings-default-model")))
                    .withChild(label("Planning Model", modelSelectWithCurrent("planningModel", s.planningModel(), models)
                        .withId("settings-planning-model")))
                    .withChild(label("Summary Model", modelSelectWithCurrent("summaryModel", s.summaryModel(), models)
                        .withId("settings-summary-model")))
                    .withChild(label("Compaction Model", modelSelectWithCurrent("compactionModel", s.compactionModel(), models)
                        .withId("settings-compaction-model")))
                    .withChild(label("Context Buffer %", TextInput.number("contextBufferPercent")
                        .withId("settings-context-buffer")
                        .withMin("1").withMax("50")
                        .withValue(String.valueOf(s.contextBufferPercent())))))
                .withChild(new Div().withClass("orch-status").withInnerText("Use Save to persist changes.")))
            .withChild(new Div().withClass("orch-panel")
                .withChild(Header.H2("System Chat"))
                .withChild(new Paragraph("Bounded dashboard chat profile. The chat view remains the canonical conversation surface."))
                .withChild(new Div().withClass("orch-form-grid")
                    .withChild(label("Enabled", Select.create("systemChatEnabled")
                        .withId("settings-system-chat-enabled")
                        .addOption("true", "Enabled", s.systemChatEnabled() == null || s.systemChatEnabled())
                        .addOption("false", "Disabled", s.systemChatEnabled() != null && !s.systemChatEnabled())))
                    .withChild(label("Model", modelSelectWithCurrent("systemChatModel", s.systemChatModel(), models)
                        .withId("settings-system-chat-model")))
                    .withChild(label("Context Limit %", TextInput.number("systemChatContextLimit")
                        .withId("settings-system-chat-context-limit")
                        .withMin("1").withMax("100")
                        .withValue(s.systemChatContextLimit() == null ? "" : String.valueOf(s.systemChatContextLimit()))))
                    .withChild(label("Approved Tools", TextInput.create("systemChatApprovedTools")
                        .withId("settings-system-chat-tools")
                        .withValue(nn(s.systemChatApprovedTools()))
                        .withPlaceholder("tool1, tool2, *"))))
                .withChild(label("System Prompt", TextArea.create("systemChatPrompt")
                    .withId("settings-system-chat-prompt")
                    .withRows(6)
                    .withValue(nn(s.systemChatPrompt())))))
            .withChild(new Div().withClass("orch-panel")
                .withChild(Header.H2("Available Models"))
                .withChild(modelChipList(models))));

        form.withChild(Button.create("Save").withClass("orch-primary")
            .withAttribute("type", "submit"));

        return new Div().withId("settings-form-container").withChild(form);
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper Components
    // ════════════════════════════════════════════════════════════════

    private Component summaryCard(String title, String description, String href) {
        return new Div().withClass("dashboard-card")
            .withChild(Header.H2(title))
            .withChild(new Paragraph(description))
            .withChild(new HtmlTag("a").withAttribute("href", href).withInnerText("Open"));
    }

    private Component sectionHeader(String title, String subtitle) {
        return new Div().withClass("section-header")
            .withChild(new HtmlTag("h3").withInnerText(title))
            .withChild(new Paragraph(subtitle));
    }

    private Select modelSelect(String name) {
        Select select = Select.create(name).addOption("", "Default", true);
        for (var opt : chatService.availableModelOptions()) {
            select.addOption(opt.key(), opt.label(), false);
        }
        return select;
    }

    private Select modelSelectWithCurrent(String name, String current, List<String> models) {
        Select select = Select.create(name);
        select.addOption("", "Default", !StringUtils.hasText(current));
        List<io.mindspice.magenta2.ai.chat.service.ChatService.ModelOption> options = chatService.availableModelOptions();

        // If current model is not in available options, include it with a warning
        boolean currentFound = StringUtils.hasText(current)
            && options.stream().anyMatch(opt -> opt.key().equals(current));
        if (StringUtils.hasText(current) && !currentFound) {
            select.addOption(current, current + " (missing)", true);
        }

        for (var opt : options) {
            select.addOption(opt.key(), opt.label(), opt.key().equals(current));
        }
        return select;
    }

    private Component modelChipList(List<String> models) {
        Div chips = new Div().withClass("orch-chip-list");
        if (models.isEmpty()) {
            chips.withChild(new Div().withClass("dashboard-empty").withInnerText("No models detected."));
            return chips;
        }
        for (String model : models) {
            chips.withChild(new HtmlTag("span").withClass("orch-chip").withInnerText(model));
        }
        return chips;
    }

    private Component label(String text, Component input) {
        return new HtmlTag("label").withChild(new TextNode(text)).withChild(input);
    }

    private Component tabNav(String agentId, String... names) {
        HtmlTag nav = new HtmlTag("nav").withClass("orch-tabs").withAttribute("aria-label", "Detail views");
        for (String name : names) {
            Button button = Button.create(capitalize(name));
            if ("dashboard".equals(name)) {
                button.withClass("active");
            }
            button.withAttribute("data-tab", name)
                .withAttribute("data-tab-button", "true")
                .withAttribute("hx-get", "/agents/_detail/" + escapeAttr(agentId) + "/" + name)
                .withAttribute("hx-target", "#agent-tab-panel")
                .withAttribute("hx-swap", "innerHTML");
            nav.withChild(button);
        }
        return nav;
    }

    private Component moduleScript(String src) {
        return new HtmlTag("script").withAttribute("type", "module").withAttribute("src", src);
    }

    private HtmlTag statusBadgeHtml(String status) {
        String s = (status != null ? status : "").toLowerCase();
        String css;
        if (s.equals("active") || s.equals("running") || s.equals("completed")) {
            css = "orch-status-chip active";
        } else if (s.equals("disabled") || s.equals("failed") || s.equals("cancelled")) {
            css = "orch-status-chip disabled";
        } else {
            css = "orch-chip";
        }
        return new HtmlTag("span").withClass(css).withInnerText(status != null ? status : "unknown");
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private String escapeAttr(String value) {
        if (value == null) return "";
        return value.replace("\"", "&quot;").replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;");
    }

    private String nn(String value) {
        return value == null ? "" : value;
    }

    private String formatSince(Instant iso) {
        if (iso == null) return "—";
        long diff = Instant.now().toEpochMilli() - iso.toEpochMilli();
        long seconds = diff / 1000;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    private String formatSinceFuture(Instant instant) {
        if (instant == null) return "—";
        long seconds = (instant.toEpochMilli() - Instant.now().toEpochMilli()) / 1000;
        if (seconds < 0) return formatSince(instant);
        if (seconds < 60) return "in " + seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return "in " + minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return "in " + hours + "h";
        return "in " + (hours / 24) + "d";
    }

    private boolean isSuspectedStuck(WorkAssignment assignment) {
        if (assignment == null || assignment.status() != OrchestrationStatus.RUNNING) {
            return false;
        }
        Instant progressAt = assignment.lastProgressAt() != null ? assignment.lastProgressAt() : assignment.updatedAt();
        Instant heartbeatAt = assignment.lastHeartbeatAt() != null ? assignment.lastHeartbeatAt() : assignment.updatedAt();
        if (progressAt == null || heartbeatAt == null) {
            return false;
        }
        Duration progressAge = Duration.between(progressAt, Instant.now());
        Duration heartbeatAge = Duration.between(heartbeatAt, Instant.now());
        return progressAge.compareTo(Duration.ofMinutes(15)) >= 0
            && heartbeatAge.compareTo(Duration.ofMinutes(5)) < 0;
    }

    private String formatDuration(Duration duration) {
        if (duration == null) return "—";
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
