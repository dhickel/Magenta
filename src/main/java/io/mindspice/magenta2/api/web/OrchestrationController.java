package io.mindspice.magenta2.api.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldType;
import io.mindspice.magenta2.ai.chat.plan.PlanKind;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanStatus;
import io.mindspice.magenta2.ai.chat.plan.PlanStep;
import io.mindspice.magenta2.ai.chat.plan.WorkTypeProfile;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItem;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItemType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.Project;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectAgentMembership;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectService;
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
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowValidator;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
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
    private static final String DASHBOARD_CSS = "/css/orchestration.css?v=6";
    private static final String DASHBOARD_JS = "/js/orchestration/dashboard.js?v=5";
    private static final String AGENTS_JS = "/js/orchestration/agents.js?v=1";
    private static final String PLANS_JS = "/js/orchestration/plans.js?v=2";
    private static final String WORKFLOWS_JS = "/js/orchestration/workflows.js?v=2";
    private static final String PROJECTS_JS = "/js/orchestration/projects.js?v=3";
    private static final String INBOX_JS = "/js/orchestration/inbox.js?v=1";
    private static final String OUTPUTS_JS = "/js/orchestration/outputs.js?v=1";

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

    // ── Plan editor services ──
    private final PlanService planService;
    private final AssignmentService assignmentService;

    // ── Workflow editor services ──
    private final WorkflowService workflowService;

    // ── Runtime services ──
    private final org.springframework.beans.factory.ObjectProvider<io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeClient> dockerClient;
    private final org.springframework.beans.factory.ObjectProvider<io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeConfig> dockerConfig;

    public OrchestrationController(ChatService chatService,
                                   ProjectService projectService,
                                   JobService jobService,
                                   AgentProfileService agentProfileService,
                                   InboxService inboxService,
                                   io.mindspice.magenta2.ai.orchestration.runtime.InboxService runtimeInboxService,
                                   OutputArtifactService outputArtifactService,
                                   RuntimeSettingsService runtimeSettingsService,
                                   PlanService planService,
                                   AssignmentService assignmentService,
                                   WorkflowService workflowService,
                                   org.springframework.beans.factory.ObjectProvider<io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeClient> dockerClient,
                                   org.springframework.beans.factory.ObjectProvider<io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeConfig> dockerConfig) {
        this.chatService = chatService;
        this.projectService = projectService;
        this.jobService = jobService;
        this.agentProfileService = agentProfileService;
        this.inboxService = inboxService;
        this.runtimeInboxService = runtimeInboxService;
        this.outputArtifactService = outputArtifactService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.planService = planService;
        this.assignmentService = assignmentService;
        this.workflowService = workflowService;
        this.dockerClient = dockerClient;
        this.dockerConfig = dockerConfig;
        this.dashboardShell = createDashboardShell();
    }

    private ShellTemplate createDashboardShell() {
        SideNav sideNav = buildSideNav();

        return ShellBuilder.create()
            .withPageTitle("Magenta Dashboard")
            .withCustomCss(DASHBOARD_CSS)
            .withTopBanner(BannerBuilder.create()
                .withLayout(BannerBuilder.BannerLayout.CENTERED)
                .withTitle("Magenta Operations")
                .withSubtitle("Orchestration dashboard")
                .build())
            .withTopNav(TopNavBuilder.create()
                .addPrimaryLink("Chat", "/chat")
                .build())
            .withSideNav(sideNav, true)
            .buildTemplate();
    }

    private SideNav buildSideNav() {
        SideNav nav = SideNav.create();
        nav.addSection("Orchestration");
        nav.addItem("Dashboard", "/dashboard", false);
        nav.addItem("Plans", "/plans", false);
        nav.addItem("Workflows", "/workflows", false);
        nav.addItem("Jobs", "/jobs", false);
        nav.addItem("Projects", "/projects", false);
        nav.addSection("Communication");
        nav.addItem("Inbox", "/inbox", false);
        nav.addItem("Agents", "/agents", false);
        nav.addSection("Tools");
        nav.addItem("Outputs", "/outputs", false);
        nav.addItem("Settings", "/settings", false);
        return nav;
    }

    private String renderPage(Component content) {
        return dashboardShell.renderWithContent(content);
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
        return renderPage(body);
    }

    private Component dashboardChatBand() {
        return new Div().withClass("dashboard-chat-band")
            .withChild(new Div().withClass("dashboard-chat-band-inner")
                .withChild(new HtmlTag("span").withClass("dashboard-chat-label")
                    .withInnerText("System chat"))
                .withChild(TextInput.create("dashboard-chat-input")
                    .withId("dashboard-chat-input")
                    .withPlaceholder("System-level chat coming in a future update"))
                .withChild(Button.create("Send").withAttribute("disabled", "")));
    }

    private Component dashboardStatusStrip() {
        Div container = new Div().withClass("dashboard-stats-wrapper");

        // Stats loaded via HTMX
        container.withChild(new Div().withId("dashboard-stats-container")
            .hxGet("/dashboard/_stats")
            .hxTrigger("load, every 30s")
            .hxSwap("innerHTML")
            .withChild(statsStripPlaceholder()));

        // Freshness ticker – updated by JS (plan explicitly allows lightweight JS ticker)
        container.withChild(new Div().withClass("dashboard-stat dashboard-stat-freshness")
            .withChild(new HtmlTag("span").withClass("dashboard-stat-value")
                .withId("stat-freshness").withInnerText("loading"))
            .withChild(new HtmlTag("span").withClass("dashboard-stat-label")
                .withInnerText("Data freshness")));

        return container;
    }

    private Component statsStripPlaceholder() {
        return new Div().withClass("dashboard-status-strip")
            .withChild(dashboardStat("stat-running", "Running", "—"))
            .withChild(dashboardStat("stat-pending", "Pending", "—"))
            .withChild(dashboardStat("stat-waiting", "Waiting Approval", "—"))
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
                    null, null)));
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
        long waitingApprovals = inboxService.userInbox().stream()
            .filter(m -> m.respondedAt() == null).count();

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
            .withChild(dashboardStat("stat-waiting", "Waiting Approval", String.valueOf(waitingApprovals)))
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
        long waitingApprovals = inboxService.userInbox().stream()
            .filter(m -> m.respondedAt() == null).count();

        return new Div().withClass("dashboard-side-stat")
            .withChild(new HtmlTag("span").withClass("dashboard-side-value")
                .withInnerText(String.valueOf(waitingApprovals)))
            .withChild(new HtmlTag("span").withClass("dashboard-side-label")
                .withInnerText("waiting approvals"))
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
                            .hxGet("/plans/_editor/_new")
                            .hxTarget("#plan-editor-container")
                            .hxSwap("innerHTML")))
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
        return renderPage(body);
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
        Div list = new Div();
        for (var plan : plans) {
            list.withChild(new HtmlTag("button")
                .withClass("tool-item")
                .hxGet("/plans/_editor/" + escapeAttr(plan.id()))
                .hxTarget("#plan-editor-container")
                .hxSwap("innerHTML")
                .withChild(new HtmlTag("strong").withInnerText(plan.title() != null ? plan.title() : "Untitled"))
                .withChild(new HtmlTag("br"))
                .withChild(new HtmlTag("span").withInnerText(
                    (plan.kind() != null ? plan.kind().name() : "") + " - " +
                    (plan.status() != null ? plan.status().name() : ""))));
        }
        return list.render();
    }

    @GetMapping("/plans/_editor/_new")
    @ResponseBody
    public String newPlanEditor() {
        return planEditorFragment(null).render();
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
            null, null, List.of(), 0, 0, null, null, null, null
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
                current.settingsOverrideJson(),
                current.planningTask(), current.pendingQuestions(), current.pendingQuestionIndex(),
                current.planStartMessageOrder(), current.finalMessage(), current.conversationId(),
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
            PlanDefinition existing = planService.getTask(planId);
            planService.saveTask(existing);
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
                false, null, false, null, null));
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
            String typeStr = params.getOrDefault(kind + "Type" + index, existing.type().name());
            boolean required = params.containsKey(kind + "Required" + index);
            boolean array = params.containsKey(kind + "Array" + index);
            String desc = params.getOrDefault(kind + "Desc" + index, existing.description());
            String schema = params.getOrDefault(kind + "Schema" + index, existing.schema());

            PlanFieldType type;
            try {
                type = PlanFieldType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                type = existing.type();
            }

            if (name == null || name.isBlank()) {
                name = "field_" + (index + 1);
            }

            fields.set(index, new PlanFieldDefinition(
                name.trim(), type, array, nn(desc), required,
                nn(schema), existing.example()));

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

    @DeleteMapping("/plans/_editor/{planId}/assumptions/{index}")
    @ResponseBody
    public String removeAssumption(@PathVariable String planId, @PathVariable int index) {
        return removeListItem(planId, index, "assumptions");
    }

    private String addListItem(String planId, String section) {
        try {
            PlanDefinition current = planService.getTask(planId);
            PlanDefinition updated = switch (section) {
                case "deliverables" -> {
                    List<String> items = new ArrayList<>(current.deliverables());
                    items.add("");
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
                    items.add(new PlanStep(items.size() + 1, ""));
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
                    items.add("");
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
                    items.add("");
                    yield new PlanDefinition(planId, current.kind(), current.status(),
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
                    yield new PlanDefinition(planId, current.kind(), current.status(),
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
                default -> throw new IllegalArgumentException("Unknown section: " + section);
            };
            planService.saveTask(updated);
            return listSectionHtml(planService.getTask(planId), section).render();
        } catch (Exception e) {
            return new Div().withClass("orch-status").withInnerText("Error: " + e.getMessage()).render();
        }
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
            int priority = 0;
            try { priority = Integer.parseInt(params.getOrDefault("priority", "0")); } catch (NumberFormatException ignored) {}
            WorkAssignment assignment = assignmentService.create(new AssignmentRequest(
                agentId, null, null, AssignmentType.TASK_RUN,
                priority,
                nn(params.get("modelOverride")),
                nn(params.get("workspaceId")),
                Map.of("taskId", planId)
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
        modelGrid.withChild(label("Worktype", worktypeSelect(currentWorktype)));
        modelGrid.withChild(label("Planning Model", modelSelect("planningModel")
            .withId("plan-planning-model")));
        modelGrid.withChild(label("Execution Model", modelSelect("executionModel")
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
                        .withInnerText(plan.id())))));
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
            actions.withChild(Button.create("Continue in Chat")
                .withAttribute("hx-get", "/plans/_editor/" + planId + "/chat-prompt-fragment")
                .withAttribute("hx-target", "#plan-chat-prompt-container")
                .withAttribute("hx-swap", "innerHTML"));
        }
        form.withChild(actions);
        container.withChild(form);

        if (!isNew) {
            // Submit form container
            container.withChild(new Div().withId("plan-submit-container"));
            // Chat prompt container
            container.withChild(new Div().withId("plan-chat-prompt-container"));
        }

        return container;
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
                .withAttribute("placeholder", section.equals("steps") ? (item.order() + ". " + placeholder) : placeholder)
                .withAttribute("value", item.text() != null ? item.text() : ""));
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
            .withChild(label("Model Override", TextInput.create("modelOverride")
                .withPlaceholder("optional")))
            .withChild(label("Priority", TextInput.number("priority")
                .withValue("0").withMin("0").withMax("100")))
            .withChild(label("Workspace ID", TextInput.create("workspaceId")
                .withPlaceholder("optional"))));

        // Generated input form from required inputs
        if (!plan.inputs().isEmpty()) {
            Div inputsDiv = new Div().withClass("orch-form-stack");
            inputsDiv.withChild(new HtmlTag("h4").withInnerText("Required Inputs"));
            for (PlanFieldDefinition input : plan.inputs()) {
                if (input.required()) {
                    inputsDiv.withChild(label(input.name() + " (" + input.type().wireName() + ")",
                        TextInput.create("input_" + input.name())
                            .withPlaceholder(input.description() != null ? input.description() : input.type().wireName())));
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
                            .hxGet("/workflows/_editor/_new")
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
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("browser-detail")
                    .withChild(new Div().withId("workflow-editor-container")
                        .withChild(workflowEditorEmptyState()))))
            .withChild(moduleScript(WORKFLOWS_JS));
        return renderPage(body);
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
        List<WorkflowDefinition> workflows = workflowService.listDefinitions();
        if (filter != null && !filter.isBlank()) {
            String f = filter.toLowerCase();
            workflows = workflows.stream()
                .filter(w -> (w.title() != null && w.title().toLowerCase().contains(f)))
                .toList();
        }
        if (workflows.isEmpty()) {
            return new Div().withClass("tool-item").withInnerText("No workflows.").render();
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
        return list.render();
    }

    // ── Workflow editor HTMX partials ──

    @GetMapping("/workflows/_editor/_new")
    @ResponseBody
    public String newWorkflowEditor() {
        return workflowEditorFragment(null).render();
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
        }

        return container;
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

            form.withChild(TextInput.create("fromOutputName").withPlaceholder("output name"));
            form.withChild(routeTypeSelect());

            // To node select
            Select toSelect = Select.create("toNodeKey");
            toSelect.addOption("", "-- to --", true);
            for (var n : wf.nodes()) {
                toSelect.addOption(n.key(), n.displayLabel(), false);
            }
            form.withChild(toSelect);

            form.withChild(TextInput.create("toInputName").withPlaceholder("input name"));

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

    private Component workflowNodesSection(WorkflowDefinition wf) {
        Div container = new Div().withClass("field-list");
        if (wf.nodes().isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty").withInnerText("No nodes. Add a node above."));
            return container;
        }
        for (var node : wf.nodes()) {
            Div row = new Div().withClass("field-row workflow-node-row");
            // Inline editable fields with auto-save on change
            row.withChild(TextInput.create("label_" + node.key())
                .withAttribute("value", node.displayLabel())
                .withAttribute("placeholder", "label")
                .withAttribute("hx-put", "/workflows/_editor/" + wf.id() + "/nodes/" + node.key())
                .withAttribute("hx-trigger", "change")
                .withAttribute("hx-include", "closest .workflow-node-row")
                .withAttribute("hx-target", "#workflow-nodes-section")
                .withAttribute("hx-swap", "innerHTML"));

            row.withChild(nodeTypeBadge(node.type()));
            row.withChild(new HtmlTag("code")
                .withInnerText(node.key()));

            if (node.planId() != null && !node.planId().isBlank()) {
                row.withChild(new HtmlTag("span").withClass("orch-chip")
                    .withInnerText("plan:" + truncateId(node.planId())));
            }

            String outCount = String.valueOf(wf.outgoingRoutes(node.key()).size());
            row.withChild(new HtmlTag("span").withClass("orch-meta").withInnerText(outCount + " out"));

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

    private Component workflowRoutesSection(WorkflowDefinition wf) {
        Div container = new Div().withClass("field-list");
        if (wf.routes().isEmpty()) {
            container.withChild(new Div().withClass("dashboard-empty").withInnerText("No routes. Routes connect node outputs to downstream node inputs."));
            return container;
        }
        for (var route : wf.routes()) {
            Div row = new Div().withClass("field-row");

            String fromDesc = route.fromNodeKey() != null
                ? route.fromNodeKey() + (route.fromOutputName() != null && !route.fromOutputName().isBlank()
                    ? "." + route.fromOutputName() : "")
                : "(root)";
            String toDesc = route.toNodeKey()
                + (route.toInputName() != null && !route.toInputName().isBlank()
                    ? "." + route.toInputName() : "");

            row.withChild(new HtmlTag("code").withInnerText(route.id()));
            row.withChild(routeTypeBadge(route.routeType()));
            row.withChild(new HtmlTag("span").withInnerText(fromDesc));
            row.withChild(new HtmlTag("span").withInnerText("→"));
            row.withChild(new HtmlTag("span").withInnerText(toDesc));

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
            .withChild(label("Model Override", TextInput.create("modelOverride")
                .withPlaceholder("optional")))
            .withChild(label("Priority", TextInput.number("priority")
                .withValue("0").withMin("0").withMax("100")))
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
        return renderPage(body);
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

    private JobWorkItem jobItemFromParams(Map<String, String> params, int fallbackOrder) {
        String typeStr = params.getOrDefault("itemType", "PLAN").toUpperCase();
        JobWorkItemType type;
        try {
            type = JobWorkItemType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            type = JobWorkItemType.PLAN;
        }
        return new JobWorkItem(
            nn(params.get("key")),
            type,
            type == JobWorkItemType.PLAN ? nn(params.get("planId")) : null,
            type == JobWorkItemType.WORKFLOW ? nn(params.get("workflowId")) : null,
            Map.of(), // inputBindings - deferred for future
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
            List<RunOutputArtifact> artifacts = new ArrayList<>();
            for (String runId : jobService.outputRunIds(jobId)) {
                artifacts.addAll(outputArtifactService.artifactsForRun(runId));
            }
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
            .withChild(label("Owner Agent ID", TextInput.create("ownerAgentId")
                .withId("job-owner-agent")
                .withValue(isNew ? "" : nn(job.ownerAgentId()))))
            .withChild(label("Project ID", TextInput.create("projectId")
                .withId("job-project")
                .withValue(isNew ? "" : nn(job.projectId()))))
            .withChild(label("Status", TextInput.create("status")
                .withId("job-status")
                .withValue(isNew ? "DRAFT" : nn(job.status())))));

        // Worktype, Model
        String currentWorktype = job != null ? job.promptProfile() : null;
        form.withChild(new Div().withClass("orch-form-grid")
            .withChild(label("Worktype", worktypeSelect(currentWorktype)))
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

            // Inline add-item form
            form.withChild(new Div().withId("job-items-new-form").withClass("field-row")
                .withChild(TextInput.create("key").withAttribute("placeholder", "item key")
                    .withAttribute("style", "max-width:100px"))
                .withChild(jobItemTypeSelect("itemType"))
                .withChild(TextInput.create("planId").withAttribute("placeholder", "plan ID")
                    .withAttribute("style", "max-width:120px"))
                .withChild(TextInput.create("workflowId").withAttribute("placeholder", "workflow ID")
                    .withAttribute("style", "max-width:120px"))
                .withChild(TextInput.create("modelOverride").withAttribute("placeholder", "model")
                    .withAttribute("style", "max-width:80px"))
                .withChild(TextInput.number("priority").withAttribute("placeholder", "pri")
                    .withAttribute("value", "0").withAttribute("style", "max-width:50px;min-width:50px")));
        }

        // Action buttons
        Div actions = new Div().withClass("tool-actions");
        actions.withChild(Button.create("Save").withClass("orch-primary")
            .withAttribute("type", "submit"));
        if (!isNew) {
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
        return renderPage(body);
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
            // Find jobs for this project, then their output run ids
            List<JobDefinition> jobs = jobService.listDefinitions(null, projectId, null);
            List<RunOutputArtifact> artifacts = new ArrayList<>();
            for (var job : jobs) {
                for (String runId : jobService.outputRunIds(job.id())) {
                    artifacts.addAll(outputArtifactService.artifactsForRun(runId));
                }
            }
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
            .withChild(label("Owner Agent ID", TextInput.create("ownerAgentId")
                .withId("project-owner-agent")
                .withValue(isNew ? "" : nn(project.ownerAgentId()))))
            .withChild(label("Git Repo URL", TextInput.create("gitRepoUrl")
                .withId("project-git-url")
                .withValue(isNew ? "" : nn(project.gitRepoUrl())))));

        String currentWorktype = project != null ? project.promptProfile() : null;
        form.withChild(new Div().withClass("orch-form-grid")
            .withChild(label("Worktype", worktypeSelect(currentWorktype)))
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
                            "Members: " + ws.linkCount()))));
            } catch (Exception e) {
                form.withChild(new Div().withId("project-workspace-section")
                    .withChild(new Div().withClass("dashboard-empty")
                        .withInnerText("Workspace: " + e.getMessage())));
            }

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
        return renderPage(body);
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
                        .withChild(new Div().withId("user-inbox-messages").withClass("inbox-message-list")))
                    .withChild(new Div().withClass("orch-panel")
                        .withChild(Header.H2("Agent Inbox"))
                        .withChild(new Div().withId("inbox-agent-selector").withClass("entity-toolbar")
                            .withChild(Select.create("inboxAgentId").withId("inbox-agent-select")))
                        .withChild(new Div().withId("agent-inbox-messages").withClass("inbox-message-list"))))
                .withChild(new Div().withClass("inbox-side")
                    .withChild(new Div().withClass("orch-panel")
                        .withChild(Header.H2("Run State"))
                        .withChild(new Div().withId("inbox-run-state")))))
            .withChild(moduleScript(INBOX_JS));
        return renderPage(body);
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
            .withChild(new Paragraph("Browse materialized output artifacts by agent, job, project, run ID, and artifact type."))
            .withChild(new Div().withClass("outputs-toolbar")
                .withChild(Select.create("outputAgentId").withId("outputs-agent-select"))
                .withChild(Select.create("outputJobId").withId("outputs-job-select"))
                .withChild(Select.create("outputProjectId").withId("outputs-project-select"))
                .withChild(TextInput.create("runId").withId("outputs-run-id").withPlaceholder("run ID"))
                .withChild(Select.create("outputArtifactType").withId("outputs-type-select")
                    .addOption("all", "All types", true)
                    .addOption("file", "Files", false)
                    .addOption("message", "Messages", false)
                    .addOption("evidence", "Evidence", false))
                .withChild(Button.create("Browse").withClass("orch-primary").withAttribute("data-action", "browse-outputs")))
            .withChild(new Div().withId("outputs-list").withClass("outputs-grid"))
            .withChild(moduleScript(OUTPUTS_JS));
        return renderPage(body);
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
            .withChild(moduleScript(AGENTS_JS));
        return renderPage(body);
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

        Table table = Table.create()
            .withHeaders("Name", "Status", "Model", "Queue", "Inbox", "Jobs")
            .withClass("dashboard-table");
        table.withId("agents-list-table");

        for (var a : agents) {
            int queueCount = countAssignments(a.id());
            int inboxCount = countInboxMessages(a.id());
            table.addRow(
                new HtmlTag("a")
                    .withAttribute("href", "/agents/" + escapeAttr(a.id()))
                    .withAttribute("hx-get", "/agents/_detail/" + escapeAttr(a.id()))
                    .withAttribute("hx-target", "#agent-detail-container")
                    .withAttribute("hx-swap", "innerHTML")
                    .withInnerText(a.name() != null ? a.name() : a.id()),
                statusBadgeHtml(a.status() != null ? a.status().name() : "UNKNOWN"),
                new HtmlTag("span").withInnerText(
                    a.defaultModel() != null ? a.defaultModel() : "unset"),
                new HtmlTag("span").withInnerText(String.valueOf(queueCount)),
                new HtmlTag("span").withInnerText(String.valueOf(inboxCount)),
                new HtmlTag("span").withInnerText("—")
            );
        }
        return table.render();
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
            return renderPage(body);
        }

        Component body = new Div()
            .withId("agents-page")
            .withAttribute("data-orchestration-page", "agents")
            .withChild(Header.H1("Agent: " + (agent.name() != null ? agent.name() : agent.id())))
            .withChild(new Paragraph("Profile, queue, inbox, workspace, and history."))
            .withChild(agentDetailLayout(agent))
            .withChild(moduleScript(AGENTS_JS));
        return renderPage(body);
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
        return new Div().withClass("entity-detail-layout")
            .withChild(new Div().withClass("entity-detail-main")
                .withChild(tabNav("dashboard", "queue", "inbox", "jobs", "workspace", "outputs", "history"))
                .withChild(new Div().withId("agent-tab-panel").withClass("orch-panel")
                    .withAttribute("hx-get", "/agents/_detail/" + escapeAttr(agent.id()) + "/dashboard")
                    .withAttribute("hx-trigger", "load")
                    .withAttribute("hx-swap", "innerHTML")
                    .withChild(loadingPlaceholder())))
            .withChild(new Div().withClass("entity-detail-side")
                .withChild(new Div().withClass("orch-panel")
                    .withChild(Header.H2("Profile"))
                    .withChild(new Div().withId("agent-editor-container")
                        .withAttribute("hx-get", "/agents/_editor/" + escapeAttr(agent.id()))
                        .withAttribute("hx-trigger", "load")
                        .withAttribute("hx-swap", "innerHTML")
                        .withChild(loadingPlaceholder())))
                .withChild(new Div().withClass("orch-panel")
                    .withChild(Header.H2("Submit Work"))
                    .withChild(new Div().withId("agent-submit-container")
                        .withAttribute("hx-get", "/agents/_submit-form/" + escapeAttr(agent.id()))
                        .withAttribute("hx-trigger", "load")
                        .withAttribute("hx-swap", "innerHTML")
                        .withChild(loadingPlaceholder()))));
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

        // Docker status
        panel.withChild(new Div().withId("agent-docker-status-" + agentId)
            .withAttribute("hx-get", "/agents/_detail/" + agentId + "/docker-status")
            .withAttribute("hx-trigger", "load")
            .withAttribute("hx-swap", "innerHTML")
            .withChild(new Div().withClass("dashboard-empty").withInnerText("Loading Docker status...")));

        // Quick chat button
        panel.withChild(new Div().withClass("orch-actions")
            .withChild(new HtmlTag("a")
                .withAttribute("href", "/chat?agent=" + escapeAttr(agentId))
                .withClass("orch-primary")
                .withInnerText("Chat with Agent")));

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

    @GetMapping("/agents/_detail/{agentId}/workspace")
    @ResponseBody
    public String agentWorkspaceTab(@PathVariable String agentId) {
        AgentProfile agent = agentProfileService.get(agentId);
        Div panel = new Div();
        panel.withChild(Header.H2("Workspace"));

        panel.withChild(new Div().withClass("orch-meta")
            .withChild(new HtmlTag("span").withInnerText("Agent: " + (agent.name() != null ? agent.name() : agentId)))
            .withChild(new HtmlTag("span").withInnerText("Agent ID: " + agent.id())));

        panel.withChild(new Div().withClass("dashboard-empty")
            .withInnerText("Workspace details available via the API.")
            .withChild(new HtmlTag("br"))
            .withChild(new HtmlTag("code").withInnerText("GET /api/agents/" + agentId + "/workspace")));
        return panel.render();
    }

    @GetMapping("/agents/_detail/{agentId}/outputs")
    @ResponseBody
    public String agentOutputsTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("Recent Outputs"));

        // Agent-specific outputs are tracked through job/assignment/run relationships.
        // Show the global recent outputs list; agent filtering will improve when RunOutputArtifact
        // carries agent identity metadata.
        List<RunOutputArtifact> outputs = outputArtifactService.query(null, null, null, 20);
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

    @GetMapping("/agents/_detail/{agentId}/history")
    @ResponseBody
    public String agentHistoryTab(@PathVariable String agentId) {
        Div panel = new Div();
        panel.withChild(Header.H2("History"));
        panel.withChild(new Div().withClass("dashboard-empty")
            .withInnerText("Run history appears as assignments and job events are persisted."));
        panel.withChild(new HtmlTag("br"));
        panel.withChild(new HtmlTag("a")
            .withAttribute("href", "/agents/_detail/" + agentId + "/queue")
            .withAttribute("hx-get", "/agents/_detail/" + agentId + "/queue")
            .withAttribute("hx-target", "#agent-tab-panel")
            .withAttribute("hx-swap", "innerHTML")
            .withInnerText("View assignments queue"));
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
            .withHeaders("Type", "Status", "Priority", "Job", "Created")
            .withClass("dashboard-table");
        for (var a : assignments) {
            table.addRow(
                new HtmlTag("span").withInnerText(a.assignmentType() != null ? a.assignmentType().name() : "—"),
                statusBadgeHtml(a.status() != null ? a.status().name() : "unknown"),
                new HtmlTag("span").withInnerText(String.valueOf(a.priority())),
                new HtmlTag("span").withInnerText(a.jobId() != null ? a.jobId() : "—"),
                new HtmlTag("span").withInnerText(a.createdAt() != null ? formatSince(a.createdAt()) : "—")
            );
        }
        panel.withChild(table);

        return panel.render();
    }

    @GetMapping("/agents/_detail/{agentId}/docker-status")
    @ResponseBody
    public String agentDockerStatusTab(@PathVariable String agentId) {
        io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeClient client = dockerClient.getIfAvailable();
        io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeConfig config = dockerConfig.getIfAvailable();

        Div panel = new Div().withClass("docker-status-fragment");

        if (client == null) {
            panel.withChild(new HtmlTag("span").withClass("orch-status")
                .withInnerText("Docker runtime is disabled (magenta.docker.enabled=false)."));
            return panel.render();
        }

        boolean reachable = client.ping();
        if (!reachable) {
            String error = client.getDaemonError() != null ? client.getDaemonError() : "daemon unreachable";
            panel.withChild(new HtmlTag("span").withClass("orch-status orch-status-error")
                .withInnerText("Docker daemon unreachable: " + error));
            return panel.render();
        }

        String health = client.healthCheck();
        boolean healthy = health != null && health.contains("ready");
        panel.withChild(new HtmlTag("span").withClass(healthy ? "orch-status" : "orch-status-error")
            .withInnerText("Docker: " + (healthy ? "Ready — " : "") + health));
        panel.withChild(new HtmlTag("br"));
        panel.withChild(new HtmlTag("small").withInnerText(
            "Host: " + client.getDockerHost() + " | Image: " + client.getAgentImage()));
        return panel.render();
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
        grid.withChild(label("Default Model", TextInput.create("defaultModel")
            .withValue(nn(agent.defaultModel()))));
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

        form.withChild(label("Model Override", TextInput.create("modelOverride")
            .withPlaceholder("Optional; leave blank for default")));

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
            .withChild(settingsForm(s))
            .withChild(moduleScript(DASHBOARD_JS));
        return renderPage(body);
    }

    private Component settingsForm(RuntimeSettings s) {
        Div container = new Div();

        container.withChild(new Div().withClass("orch-layout")
            .withChild(new Div().withClass("orch-panel")
                .withChild(Header.H2("Model Routing"))
                .withChild(new Div().withClass("orch-form-grid")
                    .withChild(label("Default Agent ID", TextInput.create("defaultAgentId")
                        .withId("settings-default-agent-id")
                        .withValue(nn(s.defaultAgentId()))))
                    .withChild(label("Default Agent Name", TextInput.create("defaultAgentName")
                        .withId("settings-default-agent-name")
                        .withValue(nn(s.defaultAgentName()))))
                    .withChild(label("Default Model", Select.create("defaultModel")
                        .withId("settings-default-model")
                        .addOption(nn(s.defaultModel()), nn(s.defaultModel()), true)))
                    .withChild(label("Planning Model", Select.create("planningModel")
                        .withId("settings-planning-model")
                        .addOption(nn(s.planningModel()), nn(s.planningModel()), true)))
                    .withChild(label("Summary Model", Select.create("summaryModel")
                        .withId("settings-summary-model")
                        .addOption(nn(s.summaryModel()), nn(s.summaryModel()), true)))
                    .withChild(label("Compaction Model", Select.create("compactionModel")
                        .withId("settings-compaction-model")
                        .addOption(nn(s.compactionModel()), nn(s.compactionModel()), true)))
                    .withChild(label("Context Buffer %", TextInput.number("contextBufferPercent")
                        .withId("settings-context-buffer")
                        .withMin("0").withMax("90")
                        .withValue(String.valueOf(s.contextBufferPercent())))))
                .withChild(new Div().withId("settings-status").withClass("orch-status")))
            .withChild(new Div().withClass("orch-panel")
                .withChild(Header.H2("Available Models"))
                .withChild(new Div().withId("settings-model-list").withClass("orch-chip-list"))));

        container.withChild(Button.create("Save").withClass("orch-primary")
            .withAttribute("data-action", "save-settings"));

        return container;
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
        return Select.create(name).addOption("", "Default", true);
    }

    private Component label(String text, Component input) {
        return new HtmlTag("label").withChild(new TextNode(text)).withChild(input);
    }

    private Component tabNav(String... names) {
        HtmlTag nav = new HtmlTag("nav").withClass("orch-tabs").withAttribute("aria-label", "Detail views");
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
}
