package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanKind;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanStatus;
import io.mindspice.magenta2.ai.chat.plan.WorkTypeProfile;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.Project;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectAgentMembership;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

class OrchestrationControllerTest {

    private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyProvider() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public T getObject() { return null; }
            @Override public T getIfAvailable() { return null; }
        };
    }

    private static OrchestrationController controller() {
        return new OrchestrationController(
            new StubChatService(),
            new StubProjectService(),
            new StubJobService(),
            new StubAgentProfileService(),
            new StubInboxService(),
            new StubRuntimeInboxService(),
            new StubOutputArtifactService(),
            new StubRuntimeSettingsService(),
            new StubPlanService(),
            new StubAssignmentService(),
            new StubWorkflowService(),
            emptyProvider(),
            emptyProvider()
        );
    }

    @Test
    void dashboardRendersFullShellWithSidebar() {
        String html = controller().dashboard(null, null);

        assertThat(html).contains("/css/orchestration.css?v=6");
        assertThat(html).contains("Magenta Operations");
        assertThat(html).contains("Dashboard");
        assertThat(html).contains("/dashboard");
        assertThat(html).contains("main-sidebar");
        assertThat(html).contains("sidenav");
        assertThat(html).contains("collapsible");
        assertThat(html).contains("/webjars/htmx.org/dist/htmx.min.js");
        assertThat(html).contains("/js/orchestration/dashboard.js?v=5");
        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    @Test
    void planPageRendersHtmxFirstEditor() {
        String html = controller().plans();

        // Title is "Plans" (not "Plans & Tasks")
        assertThat(html).contains("Plans");
        assertThat(html).contains("/js/orchestration/plans.js?v=2");

        // HTMX containers for plan list
        assertThat(html).contains("hx-get=\"/plans/_list\"");
        assertThat(html).contains("hx-get=\"/plans/_editor/_new\"");

        // No Run button, No Run panel, No run-plan
        assertThat(html).doesNotContain("run-plan");
        assertThat(html).doesNotContain("plan-run-agent-id");
        assertThat(html).doesNotContain("plan-run-log");

        // No "Plans & Tasks" heading
        assertThat(html).doesNotContain("Plans &amp; Tasks");

        // No plan-prompt-profile text field (replaced by worktype dropdown)
        assertThat(html).doesNotContain("plan-prompt-profile");

        // No data-action based handlers for plan editing
        assertThat(html).doesNotContain("data-action=\"save-plan\"");
        assertThat(html).doesNotContain("data-action=\"new-plan\"");

        // Editor container exists
        assertThat(html).contains("plan-editor-container");

        // Filter input has HTMX attributes
        assertThat(html).contains("plan-filter");
        assertThat(html).contains("hx-get=\"/plans/_list\"");
        assertThat(html).contains("hx-trigger=\"keyup changed delay:300ms\"");
    }

    @Test
    void planEditorFragmentForNewPlanRendersWorktypeDropdownAndForm() {
        String html = controller().newPlanEditor();

        // New plan editor has form with POST
        assertThat(html).contains("hx-post=\"/plans/_editor\"");
        assertThat(html).contains("New Plan");

        // Has worktype dropdown (not promptProfile text field)
        assertThat(html).contains("workTypeProfile");
        assertThat(html).contains("CODING_CENTRIC");
        assertThat(html).contains("DATA_CENTRIC");
        assertThat(html).contains("RESEARCH_CENTRIC");

        // No Run button
        assertThat(html).doesNotContain("run-plan");
        assertThat(html).doesNotContain("Plan/Task Editor");
    }

    @Test
    void planEditorFragmentForExistingPlanRendersAllSections() {
        String planId = "plan-abc";
        String html = controller().planEditor(planId);

        // Has PUT form for update
        assertThat(html).contains("hx-put=\"/plans/_editor/" + planId + "\"");

        // Has all list/field section headers
        assertThat(html).contains("Deliverables");
        assertThat(html).contains("Inputs");
        assertThat(html).contains("Outputs");
        assertThat(html).contains("Steps");
        assertThat(html).contains("Validation Criteria");
        assertThat(html).contains("Assumptions");

        // Has worktype dropdown
        assertThat(html).contains("workTypeProfile");

        // Has action buttons
        assertThat(html).contains("Save");
        assertThat(html).contains("Finalize Task");
        assertThat(html).contains("Submit to Agent");
        assertThat(html).contains("Continue in Chat");

        // Has Advanced section (collapsible)
        assertThat(html).contains("Advanced");

        // No Run button or run panel
        assertThat(html).doesNotContain("run-plan");
        assertThat(html).doesNotContain("plan-run-agent-id");
        assertThat(html).doesNotContain("plan-run-log");

        // No example field in input editor
        assertThat(html).doesNotContain("example");
    }

    @Test
    void planListFragmentRendersHtmxEnabledButtons() {
        String html = controller().planListFragment(null);

        // Has at least one plan item from stub
        assertThat(html).contains("tool-item");
        assertThat(html).contains("hx-get=\"/plans/_editor/");
        assertThat(html).contains("hx-target=\"#plan-editor-container\"");
    }

    @Test
    void submitFormRendersAgentSelectAndInputs() {
        String html = controller().submitForm("plan-abc");

        assertThat(html).contains("Submit to Agent");
        assertThat(html).contains("agentId");
        assertThat(html).contains("modelOverride");
        assertThat(html).contains("priority");
    }

    @Test
    void chatPromptFragmentRendersPromptText() {
        String html = controller().chatPromptFragment("plan-abc");

        assertThat(html).contains("Continue in Chat");
        assertThat(html).contains("plan title");
        assertThat(html).contains("Grok the existing plan");
    }

    @Test
    void workflowPageRendersHtmxFirstEditor() {
        String html = controller().workflows();

        assertThat(html).contains("Workflows");
        assertThat(html).contains("/js/orchestration/workflows.js?v=2");
        assertThat(html).contains("/workflows/_list");
        assertThat(html).contains("/workflows/_editor/_new");

        // No Run button, No Run panel
        assertThat(html).doesNotContain("run-workflow");
        assertThat(html).doesNotContain("workflow-run-agent-id");
        assertThat(html).doesNotContain("workflow-run-log");

        // No data-action based handlers
        assertThat(html).doesNotContain("data-action=\"save-workflow\"");
        assertThat(html).doesNotContain("data-action=\"validate-workflow\"");
        assertThat(html).doesNotContain("data-action=\"add-workflow-node\"");

        // Editor container exists
        assertThat(html).contains("workflow-editor-container");

        // Filter input has HTMX attributes
        assertThat(html).contains("workflow-filter");
        assertThat(html).contains("hx-get=\"/workflows/_list\"");
        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    @Test
    void jobPageRendersHtmxFirstWithListAndEditor() {
        String html = controller().jobs();

        // HTMX-first layout with list and editor
        assertThat(html).contains("Ordered orchestration items");
        assertThat(html).contains("hx-get=\"/jobs/_list\"");
        assertThat(html).contains("hx-get=\"/jobs/_editor/_new\"");
        assertThat(html).contains("job-editor-container");
        assertThat(html).contains("job-list");

        // No JS data-action handlers for job editing
        assertThat(html).doesNotContain("data-action=\"create-job\"");
        assertThat(html).doesNotContain("data-action=\"reload-jobs\"");
        assertThat(html).doesNotContain("data-action=\"run-job\"");
        assertThat(html).doesNotContain("data-action=\"save-job\"");
        assertThat(html).doesNotContain("data-action=\"add-job-item\"");
        assertThat(html).doesNotContain("data-action=\"cancel-job\"");

        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    @Test
    void jobEditorFragmentRendersFormWithItemsAndSubmitButtons() {
        // Create a job first, then test the editor
        String html = controller().jobEditor("job-abc");

        // Job editor is populated from stub
        assertThat(html).contains("hx-put=\"/jobs/_editor/job-abc\"");
        assertThat(html).contains("job-editor");

        // Has items section
        assertThat(html).contains("Ordered Items");

        // Has submit and delete buttons
        assertThat(html).contains("Submit to Agent");
        assertThat(html).contains("Delete");

        // No Run button
        assertThat(html).doesNotContain("run-job");

        // Events and outputs load via HTMX
        assertThat(html).contains("/jobs/_detail/job-abc/events");
        assertThat(html).contains("/jobs/_detail/job-abc/outputs");
    }

    @Test
    void projectPageRendersHtmxFirstWithListAndEditor() {
        String html = controller().projects();

        // HTMX-first layout
        assertThat(html).contains("/js/orchestration/projects.js?v=3");
        assertThat(html).contains("hx-get=\"/projects/_list\"");
        assertThat(html).contains("hx-get=\"/projects/_editor/_new\"");
        assertThat(html).contains("project-editor-container");

        // No JS data-action handlers
        assertThat(html).doesNotContain("data-action=\"create-project\"");
        assertThat(html).doesNotContain("data-action=\"save-project\"");
        assertThat(html).doesNotContain("data-action=\"delete-project\"");
        assertThat(html).doesNotContain("data-action=\"add-project-agent\"");

        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    @Test
    void projectEditorFragmentRendersFormWithDetailSections() {
        String html = controller().projectEditor("proj-xyz");

        // Editor form with PUT
        assertThat(html).contains("hx-put=\"/projects/_editor/proj-xyz\"");
        assertThat(html).contains("project-editor");

        // Has detail sections
        assertThat(html).contains("Name");
        assertThat(html).contains("Description");
        assertThat(html).contains("Owner Agent");
        assertThat(html).contains("Git Repo URL");
        assertThat(html).contains("Worktype");

        // Has workspace, agents, jobs, outputs sections
        assertThat(html).contains("Workspace");
        assertThat(html).contains("Agents");
        assertThat(html).contains("Active Jobs");
        assertThat(html).contains("Recent Outputs");

        // Has delete button
        assertThat(html).contains("Delete");
    }

    @Test
    void projectDetailPagePreloadsEditorViaHtmx() {
        String html = controller().projectDetail("proj-xyz");

        // Should have shell with sidebar and editor pre-loading via HTMX
        assertThat(html).contains("hx-get=\"/projects/_editor/proj-xyz\"");
        assertThat(html).contains("hx-get=\"/projects/_list\"");
        assertThat(html).contains("project-editor-container");

        // No JS data-action handlers
        assertThat(html).doesNotContain("data-action=\"save-project\"");
        assertThat(html).doesNotContain("data-action=\"delete-project\"");
    }

    @Test
    void inboxPageRendersWithUserAndAgentInboxControls() {
        String html = controller().inbox();

        assertThat(html).contains("/js/orchestration/inbox.js?v=1");
        assertThat(html).contains("User Inbox");
        assertThat(html).contains("Agent Inbox");
        assertThat(html).contains("user-inbox-messages");
        assertThat(html).contains("agent-inbox-messages");
        assertThat(html).contains("inbox-agent-select");
        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    @Test
    void outputsPageRendersWithFilterControls() {
        String html = controller().outputs();

        assertThat(html).contains("/js/orchestration/outputs.js?v=1");
        assertThat(html).contains("outputs-agent-select");
        assertThat(html).contains("outputs-job-select");
        assertThat(html).contains("outputs-project-select");
        assertThat(html).contains("outputs-run-id");
        assertThat(html).contains("outputs-type-select");
        assertThat(html).contains("browse-outputs");
        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    // ── Agent page tests (HTMX-first for Phase 06) ──

    @Test
    void agentsPageRendersHtmxFirstWithListAndDetailContainers() {
        String html = controller().agents();

        assertThat(html).contains("Manage agent profiles");
        assertThat(html).contains("browser-layout");
        assertThat(html).contains("browser-sidebar");
        assertThat(html).contains("browser-detail");
        assertThat(html).contains("agent-list");
        assertThat(html).contains("agent-detail-container");
        assertThat(html).contains("/js/orchestration/agents.js?v=1");
        assertThat(html).doesNotContain("/js/chat-client.js");

        // HTMX containers for list loading
        assertThat(html).contains("hx-get=\"/agents/_list\"");
        assertThat(html).contains("hx-trigger=\"load\"");
        assertThat(html).contains("hx-swap=\"innerHTML\"");

        // Filter
        assertThat(html).contains("agent-filter");
        assertThat(html).contains("hx-get=\"/agents/_list\"");
        assertThat(html).contains("hx-trigger=\"keyup changed delay:300ms\"");

        // Create button via HTMX
        assertThat(html).contains("hx-post=\"/agents/_create\"");

        // No old JS-based agent form IDs (now HTMX-driven editor + detail)
        assertThat(html).doesNotContain("data-action=\"save-agent\"");
        assertThat(html).doesNotContain("data-action=\"clone-agent\"");
        assertThat(html).doesNotContain("data-action=\"delete-agent\"");
        assertThat(html).doesNotContain("data-action=\"reload-agents\"");
    }

    @Test
    void agentDetailPageRendersHtmxTabsAndEditorContainers() {
        String html = controller().agentDetail("agent-1");

        assertThat(html).contains("Agent: Test Agent");
        assertThat(html).contains("entity-detail-layout");
        assertThat(html).contains("orch-tabs");
        assertThat(html).contains("/js/orchestration/agents.js?v=1");

        // Tab buttons
        assertThat(html).contains("dashboard");
        assertThat(html).contains("queue");
        assertThat(html).contains("inbox");
        assertThat(html).contains("jobs");
        assertThat(html).contains("workspace");
        assertThat(html).contains("outputs");
        assertThat(html).contains("history");

        // HTMX lazy-load containers for tabs, editor, and submit
        assertThat(html).contains("hx-get=\"/agents/_detail/agent-1/dashboard\"");
        assertThat(html).contains("hx-get=\"/agents/_editor/agent-1\"");
        assertThat(html).contains("hx-get=\"/agents/_submit-form/agent-1\"");

        // No old JS-dependent markers
        assertThat(html).doesNotContain("data-agent-id");
        assertThat(html).doesNotContain("agent-assignment-form");
        assertThat(html).doesNotContain("agent-profile-form");
        assertThat(html).doesNotContain("data-action=\"save-agent\"");
        assertThat(html).doesNotContain("open-agent-chat");
    }

    @Test
    void agentListFragmentRendersTable() {
        String html = controller().agentList(null);

        assertThat(html).contains("agents-list-table");
        assertThat(html).contains("dashboard-table");
        assertThat(html).contains("Test Agent");
        assertThat(html).contains("ACTIVE");
    }

    @Test
    void agentDashboardTabRendersCountersAndDockerStatus() {
        String html = controller().agentDashboardTab("agent-1");

        assertThat(html).contains("Dashboard");
        assertThat(html).contains("agent-dashboard");
        assertThat(html).contains("agent-dashboard-counters");
        assertThat(html).contains("agent-counter-card");
        assertThat(html).contains("Queue");
        assertThat(html).contains("Inbox");
        assertThat(html).contains("Jobs");

        // Docker status section loaded via HTMX
        assertThat(html).contains("agent-docker-status-agent-1");
        assertThat(html).contains("hx-get=\"/agents/_detail/agent-1/docker-status\"");
    }

    @Test
    void agentQueueTabRendersAssignmentTable() {
        String html = controller().agentQueueTab("agent-1");

        assertThat(html).contains("Queue");
        assertThat(html).contains("No assignments");
    }

    @Test
    void agentInboxTabRendersInboxTable() {
        String html = controller().agentInboxTab("agent-1");

        assertThat(html).contains("Inbox");
        assertThat(html).contains("No inbox messages");
    }

    @Test
    void agentEditorRendersIdentityPromptToolsShellSections() {
        String html = controller().agentEditor("agent-1");

        assertThat(html).contains("Identity");
        assertThat(html).contains("System Prompt");
        assertThat(html).contains("Approved Tools");
        assertThat(html).contains("Shell Allowlist");

        // Each section has its own HTMX form
        assertThat(html).contains("hx-put=\"/agents/_editor/agent-1/profile\"");
        assertThat(html).contains("hx-put=\"/agents/_editor/agent-1/prompt\"");
        assertThat(html).contains("hx-put=\"/agents/_editor/agent-1/tools\"");
        assertThat(html).contains("hx-put=\"/agents/_editor/agent-1/shell\"");
    }

    @Test
    void agentSubmitFormRendersStructuredFields() {
        String html = controller().agentSubmitForm("agent-1");

        assertThat(html).contains("agent-submit-form-agent-1");
        assertThat(html).contains("hx-post=\"/agents/_submit/agent-1\"");
        assertThat(html).contains("assignmentType");
        assertThat(html).contains("TASK_RUN");
        assertThat(html).contains("WORKFLOW_RUN");
        assertThat(html).contains("JOB_RUN");
        assertThat(html).contains("targetId");
        assertThat(html).contains("priority");
        assertThat(html).contains("modelOverride");
    }

    @Test
    void settingsPageRendersWithModelRoutingAndContextControls() {
        String html = controller().settings();

        assertThat(html).contains("Model Routing");
        assertThat(html).contains("settings-default-model");
        assertThat(html).contains("settings-planning-model");
        assertThat(html).contains("settings-compaction-model");
        assertThat(html).contains("contextBufferPercent");
        assertThat(html).contains("save-settings");
        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    @Test
    void allOrchestrationPagesUseDashboardShellWithSidebar() {
        OrchestrationController controller = controller();

        List<String> pages = List.of(
            controller.dashboard(null, null),
            controller.plans(),
            controller.workflows(),
            controller.jobs(),
            controller.projects(),
            controller.inbox(),
            controller.outputs(),
            controller.agents(),
            controller.settings()
        );

        for (String html : pages) {
            assertThat(html).contains("main-sidebar");
            assertThat(html).contains("sidenav");
            assertThat(html).contains("/css/orchestration.css?v=6");
            assertThat(html).doesNotContain("/js/chat-client.js");
        }
    }

    @Test
    void sidebarContainsAllRequiredNavigationLinks() {
        String html = controller().dashboard(null, null);

        assertThat(html).contains("/dashboard");
        assertThat(html).contains("/plans");
        assertThat(html).contains("/workflows");
        assertThat(html).contains("/jobs");
        assertThat(html).contains("/projects");
        assertThat(html).contains("/inbox");
        assertThat(html).contains("/agents");
        assertThat(html).contains("/outputs");
        assertThat(html).contains("/settings");
    }

    @Test
    void dashboardRendersHxContainersForPartialLoading() {
        String html = controller().dashboard(null, null);

        // Dashboard should have HTMX containers for lazy-loaded sections
        assertThat(html).contains("hx-get=\"/dashboard/_stats\"");
        assertThat(html).contains("hx-get=\"/dashboard/_active-work\"");
        assertThat(html).contains("hx-get=\"/dashboard/_open-projects\"");
        assertThat(html).contains("hx-get=\"/dashboard/_agents\"");
        assertThat(html).contains("hx-get=\"/dashboard/_side-inbox\"");
        assertThat(html).contains("hx-get=\"/dashboard/_side-outputs\"");
        // Should trigger on load and refresh
        assertThat(html).contains("hx-trigger=\"load, every 30s\"");
        assertThat(html).contains("hx-swap=\"innerHTML\"");
        // Freshness ticker stays (JS-driven)
        assertThat(html).contains("stat-freshness");
    }

    @Test
    void projectPageRendersHxListContainer() {
        String html = controller().projects();

        // Project list should load via HTMX
        assertThat(html).contains("hx-get=\"/projects/_list\"");
        assertThat(html).contains("hx-trigger=\"load\"");
    }

    // ── JS module content tests ──

    @Test
    void planJsIsMinimalHtmxFirstSkeleton() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/plans.js"));

        // Stripped of rendering, save, run, new plan handlers
        assertThat(js).contains("data-orchestration-page='plans'");
        assertThat(js).doesNotContain("renderPlanEdit");
        assertThat(js).doesNotContain("renderRunInputForm");
        assertThat(js).doesNotContain("readFields");
        assertThat(js).doesNotContain("readDeliverables");
        assertThat(js).doesNotContain("fieldRow");
        assertThat(js).doesNotContain("deliverableRow");
        assertThat(js).doesNotContain("save-plan");
        assertThat(js).doesNotContain("run-plan");
        assertThat(js).doesNotContain("new-plan");
        assertThat(js).doesNotContain("data-action=\"add-deliverable\"");
        assertThat(js).doesNotContain("data-action=\"add-plan-input\"");
        assertThat(js).doesNotContain("data-action=\"add-plan-output\"");
        assertThat(js).doesNotContain("payload");
        assertThat(js).doesNotContain("jsonFetch");
        assertThat(js).doesNotContain("/api/plans");
    }

    @Test
    void workflowJsIsMinimalHtmxFirstSkeleton() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/workflows.js"));

        // Stripped of rendering, save, run, node/route CRUD, validation handlers
        assertThat(js).contains("data-orchestration-page='workflows'");
        assertThat(js).doesNotContain("renderWorkflowEdit");
        assertThat(js).doesNotContain("readNodes");
        assertThat(js).doesNotContain("nodeRow");
        assertThat(js).doesNotContain("save-workflow");
        assertThat(js).doesNotContain("run-workflow");
        assertThat(js).doesNotContain("validate-workflow");
        assertThat(js).doesNotContain("add-workflow-node");
        assertThat(js).doesNotContain("payload");
        assertThat(js).doesNotContain("jsonFetch");
        assertThat(js).doesNotContain("/api/workflows");
    }

    @Test
    void inboxJsSupportsApprovalResponseFlow() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/inbox.js"));

        assertThat(js).contains("data-orchestration-page='inbox'");
        assertThat(js).contains("/api/users/inbox");
        assertThat(js).contains("/api/agents/");
        assertThat(js).contains("inbox");
        assertThat(js).contains("data-action=\"approve\"");
        assertThat(js).contains("data-action=\"reject\"");
        assertThat(js).contains("approved");
        assertThat(js).contains("/respond");
        assertThat(js).contains("refreshRunState");
    }

    @Test
    void outputsJsBrowsesByMultipleFilters() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/outputs.js"));

        assertThat(js).contains("data-orchestration-page='outputs'");
        assertThat(js).contains("outputs-agent-select");
        assertThat(js).contains("outputs-job-select");
        assertThat(js).contains("outputs-project-select");
        assertThat(js).contains("browse-outputs");
        assertThat(js).contains("/api/outputs");
    }

    @Test
    void projectsJsIsMinimalHtmxFirstSkeleton() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/projects.js"));

        // Stripped of CRUD handlers, now HTMX-first skeleton
        assertThat(js).contains("data-orchestration-page='projects'");
        assertThat(js).doesNotContain("jsonFetch");
        assertThat(js).doesNotContain("innerHTML");
        assertThat(js).doesNotContain("save-project");
        assertThat(js).doesNotContain("delete-project");
        assertThat(js).doesNotContain("add-project-agent");
        assertThat(js).doesNotContain("create-project");
        assertThat(js).doesNotContain("/api/projects");
    }

    @Test
    void dashboardJsRemovesAgentRenderingAfterPhase06() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/dashboard.js"));

        // Keeps settings and dashboard ticker
        assertThat(js).contains("data-orchestration-page");
        assertThat(js).contains("/api/settings/runtime");
        assertThat(js).contains("initSettings");
        assertThat(js).contains("save-settings");
        assertThat(js).contains("initDashboardTicker");

        // Agent rendering functions removed (moved to HTMX in Phase 06)
        assertThat(js).doesNotContain("initAgents");
        assertThat(js).doesNotContain("initAgentDetail");
        assertThat(js).doesNotContain("renderAgentProfile");
        assertThat(js).doesNotContain("renderAgentTab");
        assertThat(js).doesNotContain("renderAssignmentForm");
        assertThat(js).doesNotContain("agentCard");
        assertThat(js).doesNotContain("save-agent");
        assertThat(js).doesNotContain("loadAgentDetail");

        // Job functions removed (HTMX-driven since Phase 05)
        assertThat(js).doesNotContain("initJobs");
        assertThat(js).doesNotContain("initJobDetail");
        assertThat(js).doesNotContain("create-job");
        assertThat(js).doesNotContain("add-job-item");
        assertThat(js).doesNotContain("run-job");
    }

    @Test
    void dashboardJsHasHtmxFirstArchitecture() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/dashboard.js"));

        // Dashboard should use HTMX ticker, not client-side rendering functions
        assertThat(js).contains("initDashboardTicker");
        assertThat(js).doesNotContain("renderDashboardStats");
        assertThat(js).doesNotContain("renderActiveWork");
        assertThat(js).doesNotContain("renderOpenProjects");
        assertThat(js).doesNotContain("renderAgents");
        assertThat(js).doesNotContain("renderSideInbox");
        assertThat(js).doesNotContain("renderSideOutputs");
        assertThat(js).doesNotContain("initDashboard(");
    }

    @Test
    void agentsJsIsMinimalHtmxFirstSkeleton() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/agents.js"));

        assertThat(js).contains("data-orchestration-page='agents'");
        assertThat(js).contains("HTMX handles all agent CRUD");

        // No JSON fetch, no innerHTML manipulation
        assertThat(js).doesNotContain("jsonFetch");
        assertThat(js).doesNotContain("innerHTML");
        assertThat(js).doesNotContain("save-agent");
        assertThat(js).doesNotContain("clone-agent");
        assertThat(js).doesNotContain("delete-agent");
    }

    // ── Stubs ──

    private static class StubChatService extends ChatService {
        StubChatService() {
            super(null, null, null, null, null);
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

    private static class StubProjectService extends ProjectService {
        private static final Project STUB_PROJECT = new Project(
            "proj-xyz", "Test Project", "A test project",
            "agent-1", null, null, null, null, null, null
        );

        StubProjectService() { super(null, null); }
        @Override public java.util.List<Project> listProjects() { return List.of(STUB_PROJECT); }
        @Override public Project getProject(String id) {
            if ("proj-xyz".equals(id)) return STUB_PROJECT;
            throw new IllegalArgumentException("Project not found: " + id);
        }
        @Override public Project createProject(String name, String desc, String owner, String git) {
            return new Project("new-proj", name, desc, owner, git, null, null, null, null, null);
        }
        @Override public Project updateProject(String id, String name, String desc, String git,
                                                String pp, String model, String soj) {
            return new Project(id,
                name != null ? name : STUB_PROJECT.name(),
                desc != null ? desc : STUB_PROJECT.description(),
                STUB_PROJECT.ownerAgentId(),
                git != null ? git : STUB_PROJECT.gitRepoUrl(),
                pp != null ? pp : STUB_PROJECT.promptProfile(),
                model != null ? model : STUB_PROJECT.model(),
                soj != null ? soj : STUB_PROJECT.settingsOverrideJson(),
                STUB_PROJECT.createdAt(), STUB_PROJECT.updatedAt());
        }
        @Override public java.util.List<ProjectAgentMembership> listMembers(String projectId) { return List.of(); }
        @Override public ProjectWorkspaceSummary workspaceSummary(String projectId) {
            return new ProjectWorkspaceSummary(projectId, "agent-1", "PROJECT", "projects/" + projectId + "/workspace", 1);
        }
    }

    private static class StubJobService extends JobService {
        private static final JobDefinition STUB_JOB = new JobDefinition(
            "job-abc", "agent-1", null, null, "DRAFT",
            "Test Job", "A test job", List.of(),
            "CODING_CENTRIC", null, null, null, null
        );

        StubJobService() { super(null, null, null, null); }
        @Override public java.util.List<JobDefinition> listDefinitions() { return List.of(STUB_JOB); }
        @Override public java.util.List<JobDefinition> listDefinitions(String a, String p, String s) {
            if (a != null && !a.isBlank()) return List.of();
            return List.of(STUB_JOB);
        }
        @Override public JobDefinition getDefinition(String id) {
            if ("job-abc".equals(id)) return STUB_JOB;
            throw new IllegalArgumentException("Job not found: " + id);
        }
        @Override public JobDefinition saveDefinition(JobDefinition def) { return def; }
        @Override public java.util.List<String> outputRunIds(String jobId) { return List.of(); }
    }

    private static class StubAgentProfileService extends AgentProfileService {
        StubAgentProfileService() { super(null, null, null); }
        @Override public java.util.List<AgentProfile> list() {
            return List.of(new AgentProfile("agent-1", "Test Agent", AgentProfileStatus.ACTIVE,
                "qwen3", "You are helpful.", List.of(), List.of(), false, null, null));
        }
        @Override public AgentProfile get(String id) {
            if ("agent-1".equals(id)) {
                return new AgentProfile("agent-1", "Test Agent", AgentProfileStatus.ACTIVE,
                    "qwen3", "You are helpful.", List.of(), List.of(), false,
                    java.time.Instant.ofEpochSecond(1000000), java.time.Instant.ofEpochSecond(2000000));
            }
            throw new IllegalStateException("Agent not found: " + id);
        }
        @Override public AgentProfile update(String id, AgentProfile profile) { return profile; }
    }

    private static class StubInboxService extends InboxService {
        StubInboxService() { super(null, null); }
        @Override public java.util.List<io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage> userInbox() { return List.of(); }
    }

    private static class StubRuntimeInboxService extends io.mindspice.magenta2.ai.orchestration.runtime.InboxService {
        StubRuntimeInboxService() { super(null, null, null); }
        @Override public java.util.List<io.mindspice.magenta2.ai.orchestration.runtime.InboxMessage> messages(String agentId) { return List.of(); }
    }

    private static class StubOutputArtifactService extends OutputArtifactService {
        StubOutputArtifactService() { super(null, null, null); }
        @Override public java.util.List<io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact> query(
            String r, String p, String t, Integer l) { return List.of(); }
    }

    private static class StubRuntimeSettingsService extends RuntimeSettingsService {
        StubRuntimeSettingsService() { super(null, null, null); }
        @Override public RuntimeSettings get() {
            return new RuntimeSettings("", "", "", "", "", "", 20);
        }
    }

    private static class StubPlanService extends PlanService {
        private static final PlanDefinition STUB_PLAN = new PlanDefinition(
            "plan-abc", PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "plan title", "plan summary", "plan goal", null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(),
            WorkTypeProfile.CODING_CENTRIC.name(), null, null,
            null, null, List.of(), 0, 0, null, null, null, null
        );

        StubPlanService() { super(null, null); }

        @Override public java.util.List<PlanDefinition> listTasks() {
            return List.of(STUB_PLAN);
        }

        @Override public PlanDefinition getTask(String id) {
            if ("plan-abc".equals(id)) return STUB_PLAN;
            throw new IllegalStateException("Task not found: " + id);
        }

        @Override public PlanDefinition saveTask(PlanDefinition task) {
            return task;
        }
    }

    private static class StubAssignmentService extends AssignmentService {
        StubAssignmentService() { super(null, null, null, null); }

        @Override public WorkAssignment create(AssignmentRequest request) {
            return new WorkAssignment("asgn-1", request.agentId(), null, null,
                request.assignmentType(), request.priority() != null ? request.priority() : 0,
                io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus.QUEUED,
                request.modelOverride(), request.workspaceId(),
                0, Map.of(), request.input() != null ? request.input() : Map.of(),
                Map.of(), Map.of(), null, null, null,
                Instant.now(), Instant.now(), null, null);
        }

        @Override public java.util.List<WorkAssignment> assignments(String agentId) { return List.of(); }
    }

    private static class StubWorkflowService extends WorkflowService {
        StubWorkflowService() { super(null, null, null); }

        @Override public java.util.List<WorkflowDefinition> listDefinitions() { return List.of(); }

        @Override public WorkflowDefinition getDefinition(String id) {
            return new WorkflowDefinition(id, "Test WF", "summary",
                List.of(), List.of(), null, null);
        }
    }

}
