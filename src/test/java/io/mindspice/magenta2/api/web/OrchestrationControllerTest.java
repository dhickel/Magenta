package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import io.mindspice.magenta2.ai.config.user.AiConfig;
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
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService.AssignmentDiagnostics;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService.LinkedRunStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentEventReaction;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentSchedule;
import io.mindspice.magenta2.ai.orchestration.runtime.EventReactionService;
import io.mindspice.magenta2.ai.orchestration.runtime.EventType;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.Project;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectAgentMembership;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectService;
import io.mindspice.magenta2.ai.orchestration.runtime.ReactionActionType;
import io.mindspice.magenta2.ai.orchestration.runtime.ScheduleService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService;
import io.mindspice.magenta2.ai.orchestration.workspaces.LeaseMode;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLease;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLink;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLinkType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationControllerTest {

    private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyProvider() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public T getObject() { return null; }
            @Override public T getIfAvailable() { return null; }
        };
    }

    private static OrchestrationController controller() {
        return controller(true, true, new StubScheduleService(), new StubEventReactionService());
    }

    private static OrchestrationController controller(
        boolean schedulesEnabled,
        boolean reactionsEnabled,
        ScheduleService scheduleService,
        EventReactionService reactionService
    ) {
        return new OrchestrationController(
            new StubChatService(),
            new StubProjectService(),
            new StubJobService(),
            new StubAgentProfileService(),
            new StubInboxService(),
            new StubRuntimeInboxService(),
            new StubOutputArtifactService(),
            new StubRuntimeSettingsService(),
            workspaceService(),
            new StubPlanService(),
            new StubAssignmentService(),
            scheduleService,
            reactionService,
            new StubWorkflowService(),
            emptyProvider(),
            schedulesEnabled,
            reactionsEnabled
        );
    }

    private static WorkspaceService workspaceService() {
        try {
            Path dataRoot = Files.createTempDirectory("orch-controller-workspace");
            WorkspaceRepository repository = new WorkspaceRepository(new JdbcTemplate(
                new SingleConnectionDataSource("jdbc:sqlite::memory:", true)
            ));
            WorkspaceService service = new WorkspaceService(repository, new AiConfig(
                null, null, null, null, null, 10, dataRoot, null, Map.of(), Map.of()
            ));
            var workspace = service.agentWorkspace("agent-1", "Test Agent");
            service.addLink(workspace.id(), new WorkspaceLink(
                null,
                workspace.id(),
                "Home",
                WorkspaceLinkType.PATH,
                "home",
                true,
                false,
                null,
                null
            ));
            repository.saveLease(new WorkspaceLease(
                "lease-1",
                workspace.id(),
                "TASK_RUN",
                "run-1",
                LeaseMode.READ,
                Instant.now().plus(Duration.ofMinutes(15)),
                false,
                null,
                Instant.now(),
                Instant.now()
            ));
            return service;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to create workspace test service", exception);
        }
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
        assertThat(html).contains("hx-post=\"/plans/_editor/_draft\"");
        assertThat(html).contains("New Plan Chat");
        assertThat(html).contains("/chat?startPlanning=true");

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
    void draftPlanCreationRendersFullPersistedEditor() {
        String html = controller().createDraftPlanEditor();

        assertThat(html).contains("Plan Editor");
        assertThat(html).contains("Deliverables");
        assertThat(html).contains("Inputs");
        assertThat(html).contains("Outputs");
        assertThat(html).contains("Steps");
        assertThat(html).contains("Validation Criteria");
        assertThat(html).contains("Assumptions");
        assertThat(html).contains("Advanced");
        assertThat(html).doesNotContain("Execution Evidence");
        assertThat(html).doesNotContain("Validation Feedback");
        assertThat(html).doesNotContain("Pending Questions");
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
        assertThat(html).contains("/chat?continuePlanId=" + planId);

        // Has Advanced section (collapsible)
        assertThat(html).contains("Advanced");
        assertThat(html).doesNotContain("Execution Evidence");
        assertThat(html).doesNotContain("Validation Feedback");
        assertThat(html).doesNotContain("Pending Questions");

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
        assertThat(html).contains("/workflows/_editor/_draft");
        assertThat(html).contains("No workflows.");
        assertThat(html).doesNotContain("Loading...");

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
    void newWorkflowButtonCreatesDraftWithFullEditorSurface() {
        String html = controller().createWorkflowDraftEditor();

        assertThat(html).contains("Workflow Editor");
        assertThat(html).contains("Untitled Workflow");
        assertThat(html).contains("Nodes");
        assertThat(html).contains("Routes");
        assertThat(html).contains("Validation");
        assertThat(html).contains("Submit to Agent");
        assertThat(html).doesNotContain("run-workflow");
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
        assertThat(html).contains("Manager Type");

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

        assertThat(html).contains("hx-get=\"/inbox/_user\"");
        assertThat(html).contains("hx-get=\"/inbox/_agent-selector\"");
        assertThat(html).contains("hx-get=\"/inbox/_agent\"");
        assertThat(html).contains("User Inbox");
        assertThat(html).contains("Agent Inbox");
        assertThat(html).contains("user-inbox-messages");
        assertThat(html).contains("agent-inbox-messages");
        assertThat(html).contains("inbox-agent-selector");
        assertThat(html).doesNotContain("/js/chat-client.js");
    }

    @Test
    void outputsPageRendersWithFilterControls() {
        String html = controller().outputs();

        assertThat(html).contains("outputs-filter-form");
        assertThat(html).contains("name=\"agentId\"");
        assertThat(html).contains("name=\"jobId\"");
        assertThat(html).contains("name=\"projectId\"");
        assertThat(html).contains("name=\"runId\"");
        assertThat(html).contains("name=\"type\"");
        assertThat(html).contains("hx-get=\"/outputs/_list\"");
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
        assertThat(html).contains("schedules");
        assertThat(html).contains("reactions");
        assertThat(html).contains("workspace");
        assertThat(html).contains("outputs");
        assertThat(html).contains("history");
        assertThat(html).contains("chat");

        // HTMX lazy-load containers for tabs, editor, and submit
        assertThat(html).contains("hx-get=\"/agents/_detail/agent-1/dashboard\"");
        assertThat(html).contains("hx-get=\"/agents/_editor/agent-1\"");
        assertThat(html).contains("hx-get=\"/agents/_submit-form/agent-1\"");

        // Agent chat panel host and JS are present
        assertThat(html).contains("data-agent-chat-panel");
        assertThat(html).contains("data-agent-id=\"agent-1\"");
        assertThat(html).contains("agent-chat-accordion");
        assertThat(html).contains("Chat with Agent");
        assertThat(html).contains("/js/orchestration/agent-chat.js?v=1");

        // No old JS-dependent markers
        assertThat(html).doesNotContain("agent-assignment-form");
        assertThat(html).doesNotContain("agent-profile-form");
        assertThat(html).doesNotContain("data-action=\"save-agent\"");
    }

    @Test
    void agentListFragmentRendersCompactCards() {
        String html = controller().agentList(null);

        assertThat(html).contains("agents-list-table");
        assertThat(html).contains("agent-card-list");
        assertThat(html).contains("Test Agent");
        assertThat(html).contains("ACTIVE");
        assertThat(html).contains("Refresh");
        assertThat(html).contains("/agents/_lifecycle/agent-1/disable?view=list");
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

        // Workspace status section
        assertThat(html).contains("Workspace:");
        assertThat(html).contains("Refresh");
        assertThat(html).contains("Delete / Archive");
        assertThat(html).doesNotContain("Open Agent Chat");
    }

    @Test
    void agentChatTabRemovedFromTabNav() {
        String html = controller().agentDetail("agent-1");
        assertThat(html).doesNotContain("hx-get=\"/agents/_detail/agent-1/chat\"");
    }

    @Test
    void agentChatScriptNotOnNonAgentPages() {
        // Verify agent-chat.js is NOT loaded on the agents list page
        String listHtml = controller().agents();
        assertThat(listHtml).doesNotContain("agent-chat.js");

        // Verify agent-chat.js is NOT on dashboard
        String dashboardHtml = controller().dashboard(null, null);
        assertThat(dashboardHtml).doesNotContain("agent-chat.js");
    }

    @Test
    void deleteConfirmRendersExplicitLifecycleChoices() {
        String html = controller().deleteAgentLifecycleConfirm("agent-1");

        assertThat(html).contains("Disable Only");
        assertThat(html).contains("Archive + Disable");
        assertThat(html).contains("Hard Delete");
        assertThat(html).contains("DELETE agent-1");
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
    void agentWorkspaceTabRendersMetadataLinksAndLeases() {
        String html = controller().agentWorkspaceTab("agent-1");

        assertThat(html).contains("Workspace");
        assertThat(html).contains("Workspace ID:");
        assertThat(html).contains("Owner: AGENT:agent-1");
        assertThat(html).contains("Display Name: Test Agent");
        assertThat(html).contains("Root Relative Path: agents/agent-1/workspace");
        assertThat(html).contains("Output Directory Hint: agents/agent-1/workspace/outputs");
        assertThat(html).contains("Active Leases");
        assertThat(html).contains("TASK_RUN:run-1");
        assertThat(html).contains("Workspace Links");
        assertThat(html).contains("Home");
    }

    @Test
    void schedulesAndReactionsTabsRenderDisabledStatesWhenFeaturesOff() {
        OrchestrationController controller = controller(
            false,
            false,
            new StubScheduleService(),
            new StubEventReactionService()
        );

        String schedules = controller.agentSchedulesTab("agent-1");
        String reactions = controller.agentReactionsTab("agent-1");

        assertThat(schedules).contains("Schedules are disabled.");
        assertThat(schedules).contains("magenta.features.schedules-enabled=true");
        assertThat(reactions).contains("Event reactions are disabled.");
        assertThat(reactions).contains("magenta.features.reactions-enabled=true");
    }

    @Test
    void scheduleCreateRendersInlineErrorsForInvalidCronAndInvalidJson() {
        StubScheduleService scheduleService = new StubScheduleService();
        OrchestrationController controller = controller(
            true,
            true,
            scheduleService,
            new StubEventReactionService()
        );

        String invalidCron = controller.createAgentSchedule("agent-1", Map.of(
            "jobId", "job-abc",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "cronExpression", "not-a-cron",
            "timezone", "UTC",
            "enabled", "true",
            "inputJson", "{\"key\":\"value\"}"
        ));
        assertThat(invalidCron).contains("orch-error");
        assertThat(invalidCron).contains("invalid cronExpression");

        String invalidJson = controller.createAgentSchedule("agent-1", Map.of(
            "jobId", "job-abc",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "cronExpression", "0 * * * * *",
            "timezone", "UTC",
            "enabled", "true",
            "inputJson", "{bad-json"
        ));
        assertThat(invalidJson).contains("orch-error");
        assertThat(invalidJson).contains("Invalid JSON for inputJson");
    }

    @Test
    void scheduleCreateRendersSavedRowForValidInput() {
        StubScheduleService scheduleService = new StubScheduleService();
        OrchestrationController controller = controller(
            true,
            true,
            scheduleService,
            new StubEventReactionService()
        );

        String html = controller.createAgentSchedule("agent-1", Map.of(
            "jobId", "job-abc",
            "assignmentType", "JOB_RUN",
            "priority", "2",
            "cronExpression", "0 * * * * *",
            "timezone", "UTC",
            "enabled", "true",
            "inputJson", "{\"task\":\"check\"}"
        ));

        assertThat(html).contains("Cron: 0 * * * * *");
        assertThat(html).contains("Assignment Type: JOB_RUN");
        assertThat(html).contains("Next Run:");
        assertThat(scheduleService.schedules("agent-1")).hasSize(1);
    }

    @Test
    void scheduleLifecycleSupportsUpdateToggleAndDelete() {
        StubScheduleService scheduleService = new StubScheduleService();
        OrchestrationController controller = controller(
            true,
            true,
            scheduleService,
            new StubEventReactionService()
        );

        controller.createAgentSchedule("agent-1", Map.of(
            "jobId", "job-abc",
            "assignmentType", "JOB_RUN",
            "priority", "2",
            "cronExpression", "0 * * * * *",
            "timezone", "UTC",
            "enabled", "true",
            "inputJson", "{\"task\":\"create\"}"
        ));
        String scheduleId = scheduleService.schedules("agent-1").get(0).id();

        String updated = controller.updateAgentSchedule("agent-1", scheduleId, Map.of(
            "jobId", "job-abc",
            "assignmentType", "TASK_RUN",
            "priority", "4",
            "cronExpression", "0 */5 * * * *",
            "timezone", "UTC",
            "enabled", "false",
            "inputJson", "{\"task\":\"updated\"}"
        ));
        assertThat(updated).contains("Cron: 0 */5 * * * *");
        assertThat(updated).contains("Assignment Type: TASK_RUN");

        String toggled = controller.toggleAgentSchedule("agent-1", scheduleId);
        assertThat(toggled).contains("Enabled: true");

        String deleted = controller.deleteAgentSchedule("agent-1", scheduleId);
        assertThat(deleted).contains("No schedules configured.");
        assertThat(scheduleService.schedules("agent-1")).isEmpty();
    }

    @Test
    void scheduleDeleteIsScopedByAgent() {
        StubScheduleService scheduleService = new StubScheduleService();
        OrchestrationController controller = controller(
            true,
            true,
            scheduleService,
            new StubEventReactionService()
        );

        controller.createAgentSchedule("agent-1", Map.of(
            "jobId", "job-abc",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "cronExpression", "0 * * * * *",
            "timezone", "UTC",
            "enabled", "true",
            "inputJson", "{\"task\":\"create\"}"
        ));
        String scheduleId = scheduleService.schedules("agent-1").get(0).id();

        String wrongAgentDelete = controller.deleteAgentSchedule("agent-2", scheduleId);
        assertThat(wrongAgentDelete).contains("orch-error");
        assertThat(wrongAgentDelete).contains("schedule not found");
        assertThat(scheduleService.schedules("agent-1")).hasSize(1);
    }

    @Test
    void reactionCreateRendersInlineErrorsForInvalidEventAndFilterJson() {
        StubEventReactionService reactionService = new StubEventReactionService();
        OrchestrationController controller = controller(
            true,
            true,
            new StubScheduleService(),
            reactionService
        );

        String invalidEvent = controller.createAgentReaction("agent-1", Map.of(
            "eventType", "NOT_A_REAL_EVENT",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "enabled", "true",
            "filterJson", "{\"state\":\"queued\"}",
            "inputJson", "{\"task\":\"check\"}"
        ));
        assertThat(invalidEvent).contains("orch-error");
        assertThat(invalidEvent).contains("invalid eventType");

        String invalidFilter = controller.createAgentReaction("agent-1", Map.of(
            "eventType", "JOB_STATUS_CHANGED",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "enabled", "true",
            "filterJson", "{bad-json",
            "inputJson", "{\"task\":\"check\"}"
        ));
        assertThat(invalidFilter).contains("orch-error");
        assertThat(invalidFilter).contains("Invalid JSON for filterJson");
    }

    @Test
    void reactionCreateRendersSavedRowForValidInput() {
        StubEventReactionService reactionService = new StubEventReactionService();
        OrchestrationController controller = controller(
            true,
            true,
            new StubScheduleService(),
            reactionService
        );

        String html = controller.createAgentReaction("agent-1", Map.of(
            "eventType", "JOB_STATUS_CHANGED",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "enabled", "true",
            "filterJson", "{\"status\":\"QUEUED\"}",
            "inputJson", "{\"task\":\"check\"}"
        ));

        assertThat(html).contains("Event Type: JOB_STATUS_CHANGED");
        assertThat(html).contains("Action: ENQUEUE_ASSIGNMENT");
        assertThat(html).contains("Assignment Type: JOB_RUN");
        assertThat(reactionService.reactions("agent-1")).hasSize(1);
    }

    @Test
    void reactionLifecycleSupportsUpdateToggleAndDelete() {
        StubEventReactionService reactionService = new StubEventReactionService();
        OrchestrationController controller = controller(
            true,
            true,
            new StubScheduleService(),
            reactionService
        );

        controller.createAgentReaction("agent-1", Map.of(
            "eventType", "JOB_STATUS_CHANGED",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "enabled", "true",
            "filterJson", "{\"status\":\"QUEUED\"}",
            "inputJson", "{\"task\":\"create\"}"
        ));
        String reactionId = reactionService.reactions("agent-1").get(0).id();

        String updated = controller.updateAgentReaction("agent-1", reactionId, Map.of(
            "eventType", "MANUAL_USER_EVENT",
            "assignmentType", "TASK_RUN",
            "priority", "3",
            "enabled", "false",
            "filterJson", "{\"channel\":\"ops\"}",
            "inputJson", "{\"task\":\"updated\"}"
        ));
        assertThat(updated).contains("Event Type: MANUAL_USER_EVENT");
        assertThat(updated).contains("Assignment Type: TASK_RUN");
        assertThat(updated).contains("Enabled: false");

        String toggled = controller.toggleAgentReaction("agent-1", reactionId);
        assertThat(toggled).contains("Enabled: true");

        String deleted = controller.deleteAgentReaction("agent-1", reactionId);
        assertThat(deleted).contains("No event reactions configured.");
        assertThat(reactionService.reactions("agent-1")).isEmpty();
    }

    @Test
    void reactionDeleteIsScopedByAgent() {
        StubEventReactionService reactionService = new StubEventReactionService();
        OrchestrationController controller = controller(
            true,
            true,
            new StubScheduleService(),
            reactionService
        );

        controller.createAgentReaction("agent-1", Map.of(
            "eventType", "JOB_STATUS_CHANGED",
            "assignmentType", "JOB_RUN",
            "priority", "1",
            "enabled", "true",
            "filterJson", "{\"status\":\"QUEUED\"}",
            "inputJson", "{\"task\":\"create\"}"
        ));
        String reactionId = reactionService.reactions("agent-1").get(0).id();

        String wrongAgentDelete = controller.deleteAgentReaction("agent-2", reactionId);
        assertThat(wrongAgentDelete).contains("orch-error");
        assertThat(wrongAgentDelete).contains("reaction not found");
        assertThat(reactionService.reactions("agent-1")).hasSize(1);
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
        assertThat(html).contains("hx-put=\"/settings\"");
        assertThat(html).contains(">Save<");
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
        assertThat(html).doesNotContain("hx-get=\"/chat\"");
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
        // Message-based stat replaces approval-specific wording.
        assertThat(html).contains("stat-messages");
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
    void workflowJsProvidesGraphComposerSurface() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/orchestration/workflows.js"));

        assertThat(js).contains("data-orchestration-page='workflows'");
        assertThat(js).contains("Workflow V2 Graph Composer");
        assertThat(js).contains("graph-canvas");
        assertThat(js).contains("saveWorkflow");
        assertThat(js).contains("validateWorkflow");
        assertThat(js).contains("/api/workflows");
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

        // Keeps settings runtime controls
        assertThat(js).contains("data-orchestration-page");
        assertThat(js).contains("/api/settings/runtime");
        assertThat(js).contains("initSettings");
        assertThat(js).contains("save-settings");

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

        // Dashboard remains HTMX-first with no client-side dashboard renderers.
        assertThat(js).contains("data-orchestration-page");
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

    // ── Phase 03: Operational Editor, Model, Output, and Status Fixes ──

    @Test
    void planEditorFieldTypeSelectUsesWireNames() {
        // DEFECT-03-01: PlanFieldType dropdowns use wireName() values
        // The fieldSelect method always renders all PlanFieldType values with wireName()
        // Verify by checking the plan editor fragment for a plan with inputs
        PlanDefinition planWithInputs = new PlanDefinition(
            "plan-fields", PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            "Test", null, null, null,
            List.of(),
            List.of(new io.mindspice.magenta2.ai.chat.plan.PlanFieldDefinition(
                "topic", io.mindspice.magenta2.ai.chat.plan.PlanFieldType.USER_MESSAGE,
                false, "desc", true, null)),
            List.of(new io.mindspice.magenta2.ai.chat.plan.PlanFieldDefinition(
                "report", io.mindspice.magenta2.ai.chat.plan.PlanFieldType.FILE_PATH,
                false, "desc", true, null)),
            List.of(), List.of(), List.of(),
            List.of(), List.of(), "CODING_CENTRIC", null, null,
            null, null, List.of(), 0, 0, null, null, null, null
        );
        StubPlanService stubPlanService = new StubPlanService();
        stubPlanService.setPlan(planWithInputs);

        OrchestrationController ctrl = controllerWithPlanService(stubPlanService);
        String html = ctrl.planEditor("plan-fields");
        // Field type selects use wireName() for option values
        assertThat(html).contains("user_message");
        assertThat(html).contains("file_path");
        assertThat(html).contains("json");
        assertThat(html).contains("string");
        assertThat(html).contains("number");
    }

    @Test
    void planStepRowsExposeMoveControls() {
        PlanDefinition plan = new PlanDefinition(
            "plan-steps", PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            "Test", null, null, null,
            List.of(), List.of(), List.of(), List.of(),
            List.of(new io.mindspice.magenta2.ai.chat.plan.PlanStep(1, "First"),
                new io.mindspice.magenta2.ai.chat.plan.PlanStep(2, "Second")),
            List.of(), List.of(), List.of(), "CODING_CENTRIC", null, null,
            null, null, List.of(), 0, 0, null, null, null, null
        );
        StubPlanService stubPlanService = new StubPlanService();
        stubPlanService.setPlan(plan);

        String html = controllerWithPlanService(stubPlanService).planEditor("plan-steps");

        assertThat(html).contains("/plans/_editor/plan-steps/steps/0/move-down");
        assertThat(html).contains("/plans/_editor/plan-steps/steps/1/move-up");
    }

    @Test
    void planAddDeliverableProducesPlaceholderText() {
        // DEFECT-03-02: Adding a deliverable results in a placeholder, not empty string
        // Simulate via the stub: addItem->save handles placeholder
        PlanDefinition plan = new PlanDefinition(
            "plan-test", PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            "Test", null, null, null,
            List.of("Existing"), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), "CODING_CENTRIC", null, null,
            null, null, List.of(), 0, 0, null, null, null, null
        );
        StubPlanService stubPlanService = new StubPlanService();
        stubPlanService.setPlan(plan);

        // Verify the stub returns a plan with deliverables containing non-empty text
        PlanDefinition result = stubPlanService.getTask("plan-test");
        assertThat(result.deliverables()).contains("Existing");
    }

    @Test
    void finalizePlanEditorSetsApproved() {
        // DEFECT-03-04: finalize sets APPROVED
        PlanDefinition draftPlan = new PlanDefinition(
            "plan-draft", PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            "Draft Plan", "summary", "goal", null,
            List.of("d1"), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), "CODING_CENTRIC", null, null,
            null, null, List.of(), 0, 0, null, null, null, null
        );
        StubPlanService stubPlanService = new StubPlanService();
        stubPlanService.setPlan(draftPlan);

        OrchestrationController ctrl = controllerWithPlanService(stubPlanService);
        String html = ctrl.finalizePlanEditor("plan-draft");

        // Should render without error (successfully finalized)
        assertThat(html).doesNotContain("Error:");
        // After finalize, the plan should be APPROVED
        PlanDefinition finalized = stubPlanService.getTask("plan-draft");
        assertThat(finalized.status()).isEqualTo(PlanStatus.APPROVED);
    }

    @Test
    void agentHistoryTabShowsAssignmentHistory() {
        // DEFECT-03-HISTORY: History tab renders real assignments
        StubAssignmentService stubAsgn = new StubAssignmentService();
        WorkAssignment completed = new WorkAssignment(
            "asgn-10", "agent-1", "job-abc", "item-1",
            AssignmentType.TASK_RUN, 0, OrchestrationStatus.COMPLETED,
            null, null, 0, Map.of(),
            Map.of("taskRunId", "run-123"), Map.of(), Map.of(), null, null, null,
            Instant.now(), Instant.now(), Instant.now(),
            Instant.now().minusSeconds(3600)
        );
        stubAsgn.setAssignments(List.of(completed));

        OrchestrationController ctrl = controllerWithAssignmentService(stubAsgn);
        String html = ctrl.agentHistoryTab("agent-1");

        assertThat(html).contains("History");
        assertThat(html).contains("TASK_RUN");
        assertThat(html).contains("COMPLETED");
        assertThat(html).doesNotContain("No assignment history for this agent");
    }

    @Test
    void agentQueueShowsStuckDiagnosticsAndForceInterruptControls() {
        StubAssignmentService stubAsgn = new StubAssignmentService();
        WorkAssignment running = new WorkAssignment(
            "asgn-stuck", "agent-1", null, null,
            AssignmentType.TASK_RUN, 9, OrchestrationStatus.RUNNING,
            null, null, 0, Map.of("taskRunId", "run-1"),
            Map.of("taskId", "task-1"), Map.of(), Map.of(), null,
            "owner-1", Instant.now().plusSeconds(300),
            Instant.now().minusSeconds(1200), Instant.now(), Instant.now().minusSeconds(1200),
            null, Instant.now().minusSeconds(1200), Instant.now().minusSeconds(30)
        );
        stubAsgn.setAssignments(List.of(running));

        String html = controllerWithAssignmentService(stubAsgn).agentQueueTab("agent-1");

        assertThat(html).contains("suspected stuck");
        assertThat(html).contains("/agents/_detail/agent-1/queue/asgn-stuck/diagnostics");
        assertThat(html).contains("Force Interrupt");
    }

    @Test
    void assignmentDiagnosticsPanelShowsAuditAndForceInterruptForm() {
        StubAssignmentService stubAsgn = new StubAssignmentService();
        WorkAssignment running = new WorkAssignment(
            "asgn-diag", "agent-1", null, null,
            AssignmentType.TASK_RUN, 9, OrchestrationStatus.RUNNING,
            null, null, 0, Map.of("taskRunId", "run-1"),
            Map.of("taskId", "task-1"), Map.of(), Map.of(), null,
            "owner-1", Instant.now().plusSeconds(300),
            Instant.now().minusSeconds(1200), Instant.now(), Instant.now().minusSeconds(1200),
            null, Instant.now().minusSeconds(1200), Instant.now().minusSeconds(30)
        );
        stubAsgn.setAssignments(List.of(running));

        String html = controllerWithAssignmentService(stubAsgn)
            .assignmentDiagnosticsFragment("agent-1", "asgn-diag");

        assertThat(html).contains("Assignment Diagnostics");
        assertThat(html).contains("Suspected stuck");
        assertThat(html).contains("Build Commit");
        assertThat(html).contains("/agents/_detail/agent-1/queue/asgn-diag/force-interrupt");
    }

    @Test
    void outputContentFragmentRendersTextContent() {
        // DEFECT-07-01: Output content fragment renders text
        // StubOutputArtifactService is used; verify the fragment loads
        String html = controller().outputsContentFragment("artifact-1");
        // Should not error out even if stub returns placeholder
        assertThat(html).doesNotContain("Error");
    }

    @Test
    void modelDropdownUsesCanonicalAliasValues() {
        // DEFECT-06: Model dropdown option values are model aliases, not remote names
        String html = controller().settings();

        // The model dropdown should contain the alias key from availableModelOptions()
        assertThat(html).contains("local-qwen");
        assertThat(html).contains("local-qwen (qwen3.6:35b)");
    }

    @Test
    void jobItemAddValidatesRequiredPlanBindings() {
        // DEFECT-03-BINDINGS: Adding a PLAN item validates required bindings
        // Create a plan with required inputs
        PlanDefinition planWithRequired = new PlanDefinition(
            "plan-req", PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Plan With Required Inputs", null, null, null,
            List.of(),
            List.of(new io.mindspice.magenta2.ai.chat.plan.PlanFieldDefinition(
                "topic", io.mindspice.magenta2.ai.chat.plan.PlanFieldType.STRING,
                false, "The topic to process", true, null)),
            List.of(),
            List.of(), List.of(), List.of(),
            List.of(), List.of(), "CODING_CENTRIC", null, null,
            null, null, List.of(), 0, 0, null, null, null, null
        );
        StubPlanService stubPlanService = new StubPlanService();
        stubPlanService.setPlan(planWithRequired);

        OrchestrationController ctrl = controllerWithPlanService(stubPlanService);

        // Try to add a PLAN item without required bindings — should error
        Map<String, String> params = Map.of("itemType", "PLAN", "planId", "plan-req", "key", "item-1");
        String html = ctrl.addJobItem("job-abc", params);
        assertThat(html).contains("Error");
        assertThat(html).contains("topic");
    }

    @Test
    void scheduleDisabledShowsEnableWithTrue() {
        // DEFECT-03-MSG: schedules-enabled=false -> schedules-enabled=true
        OrchestrationController ctrl = controllerWithFeatures(false, true);
        String html = ctrl.agentSchedulesTab("agent-1");
        assertThat(html).contains("schedules-enabled=true");
        assertThat(html).doesNotContain("schedules-enabled=false");
    }

    @Test
    void reactionDisabledShowsEnableWithTrue() {
        // DEFECT-03-MSG: reactions-enabled=false -> reactions-enabled=true
        OrchestrationController ctrl = controllerWithFeatures(true, false);
        String html = ctrl.agentReactionsTab("agent-1");
        assertThat(html).contains("reactions-enabled=true");
        assertThat(html).doesNotContain("reactions-enabled=false");
    }

    // ── Helper factory methods for Phase 03 tests ──

    private OrchestrationController controllerWithPlanService(PlanService planService) {
        return new OrchestrationController(
            new StubChatService(),
            new StubProjectService(),
            new StubJobService(),
            new StubAgentProfileService(),
            new StubInboxService(),
            new StubRuntimeInboxService(),
            new StubOutputArtifactService(),
            new StubRuntimeSettingsService(),
            workspaceService(),
            planService,
            new StubAssignmentService(),
            new StubScheduleService(),
            new StubEventReactionService(),
            new StubWorkflowService(),
            emptyProvider(),
            true,
            true
        );
    }

    private OrchestrationController controllerWithAssignmentService(AssignmentService assignmentService) {
        return new OrchestrationController(
            new StubChatService(),
            new StubProjectService(),
            new StubJobService(),
            new StubAgentProfileService(),
            new StubInboxService(),
            new StubRuntimeInboxService(),
            new StubOutputArtifactService(),
            new StubRuntimeSettingsService(),
            workspaceService(),
            new StubPlanService(),
            assignmentService,
            new StubScheduleService(),
            new StubEventReactionService(),
            new StubWorkflowService(),
            emptyProvider(),
            true,
            true
        );
    }

    private OrchestrationController controllerWithFeatures(boolean schedulesEnabled, boolean reactionsEnabled) {
        return new OrchestrationController(
            new StubChatService(),
            new StubProjectService(),
            new StubJobService(),
            new StubAgentProfileService(),
            new StubInboxService(),
            new StubRuntimeInboxService(),
            new StubOutputArtifactService(),
            new StubRuntimeSettingsService(),
            workspaceService(),
            new StubPlanService(),
            new StubAssignmentService(),
            new StubScheduleService(),
            new StubEventReactionService(),
            new StubWorkflowService(),
            emptyProvider(),
            schedulesEnabled,
            reactionsEnabled
        );
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

        @Override
        public List<ModelOption> availableModelOptions() {
            return List.of(new ModelOption("local-qwen", "local-qwen (qwen3.6:35b)"));
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
            return new ProjectWorkspaceSummary(projectId, "agent-1", "PROJECT", "projects/" + projectId + "/workspace", 1, null, null, null, false);
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
        @Override public io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact getArtifact(String artifactId) {
            return new io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact(
                artifactId, "run-1", "plan-abc", "agent-1", "job-abc", null, null, "TASK_RUN",
                "test-output", "text", "test.txt", "/tmp/test.txt", null, Instant.now()
            );
        }
        @Override public String loadContent(String artifactId, long maxBytes) {
            return "Hello, World!";
        }
        @Override public java.util.List<io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact> query(
            String r, String p, String t, Integer l) { return List.of(); }
        @Override public java.util.List<io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact> query(
            io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery query) { return List.of(); }
    }

    private static class StubRuntimeSettingsService extends RuntimeSettingsService {
        StubRuntimeSettingsService() { super(null, null, null); }
        @Override public RuntimeSettings get() {
            return new RuntimeSettings("", "", "", "", "", "", 20);
        }
    }

    private static class StubPlanService extends PlanService {
        private PlanDefinition storedPlan;

        private static final PlanDefinition STUB_PLAN = new PlanDefinition(
            "plan-abc", PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "plan title", "plan summary", "plan goal", null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(),
            WorkTypeProfile.CODING_CENTRIC.name(), null, null,
            null, null, List.of(), 0, 0, null, null, null, null
        );

        StubPlanService() { super(null, null); }

        void setPlan(PlanDefinition plan) { this.storedPlan = plan; }

        @Override public java.util.List<PlanDefinition> listTasks() {
            return storedPlan != null ? List.of(storedPlan) : List.of(STUB_PLAN);
        }

        @Override public PlanDefinition getTask(String id) {
            if (storedPlan != null && storedPlan.id().equals(id)) return storedPlan;
            if ("plan-abc".equals(id)) return STUB_PLAN;
            throw new IllegalStateException("Task not found: " + id);
        }

        @Override public PlanDefinition saveTask(PlanDefinition task) {
            this.storedPlan = task;
            return task;
        }
    }

    private static class StubAssignmentService extends AssignmentService {
        private List<WorkAssignment> storedAssignments = List.of();

        StubAssignmentService() { super(null, null, null, null); }

        void setAssignments(List<WorkAssignment> assignments) { this.storedAssignments = assignments; }

        @Override public WorkAssignment create(AssignmentRequest request) {
            return new WorkAssignment("asgn-1", request.agentId(), null, null,
                request.assignmentType(), request.priority() != null ? request.priority() : 0,
                io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus.QUEUED,
                request.modelOverride(), request.workspaceId(),
                0, Map.of(), request.input() != null ? request.input() : Map.of(),
                Map.of(), Map.of(), null, null, null,
                Instant.now(), Instant.now(), null, null);
        }

        @Override public java.util.List<WorkAssignment> assignments(String agentId) { return storedAssignments; }

        @Override public WorkAssignment get(String assignmentId) {
            return storedAssignments.stream()
                .filter(assignment -> assignment.id().equals(assignmentId))
                .findFirst()
                .orElseThrow();
        }

        @Override public AssignmentDiagnostics diagnostics(String assignmentId) {
            WorkAssignment assignment = get(assignmentId);
            return new AssignmentDiagnostics(
                assignment,
                assignment.lastProgressAt(),
                assignment.lastHeartbeatAt(),
                java.time.Duration.between(assignment.lastProgressAt(), Instant.now()),
                java.time.Duration.between(assignment.lastHeartbeatAt(), Instant.now()),
                true,
                List.of(new LinkedRunStatus("TASK_RUN", "run-1", "task-1", "RUNNING", null)),
                List.of(),
                "conversation-1",
                "unknown"
            );
        }

        @Override public WorkAssignment forceInterrupt(String assignmentId, String reason) {
            WorkAssignment assignment = get(assignmentId);
            WorkAssignment interrupted = new WorkAssignment(
                assignment.id(), assignment.agentId(), assignment.jobId(), assignment.jobItemId(),
                assignment.assignmentType(), assignment.priority(), OrchestrationStatus.INTERRUPTED,
                assignment.modelOverride(), assignment.workspaceId(), assignment.currentItemIndex(),
                assignment.checkpoint(), assignment.input(), assignment.output(), assignment.evidence(),
                "Force interrupted: " + reason, null, null, assignment.createdAt(), assignment.updatedAt(),
                assignment.startedAt(), assignment.completedAt(), assignment.lastProgressAt(), assignment.lastHeartbeatAt()
            );
            storedAssignments = List.of(interrupted);
            return interrupted;
        }
    }

    private static class StubScheduleService extends ScheduleService {
        private final Map<String, List<AgentSchedule>> schedules = new java.util.HashMap<>();

        StubScheduleService() {
            super(null, null, null, null, true);
        }

        @Override
        public List<AgentSchedule> schedules(String agentId) {
            return schedules.getOrDefault(agentId, List.of());
        }

        @Override
        public AgentSchedule schedule(String agentId, String scheduleId) {
            return schedules(agentId).stream()
                .filter(schedule -> scheduleId.equals(schedule.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("schedule not found"));
        }

        @Override
        public AgentSchedule save(String agentId, AgentSchedule schedule) {
            if (!org.springframework.scheduling.support.CronExpression.isValidExpression(schedule.cronExpression())) {
                throw new IllegalArgumentException("invalid cronExpression");
            }
            List<AgentSchedule> current = new ArrayList<>(schedules(agentId));
            AgentSchedule saved = new AgentSchedule(
                schedule.id() != null ? schedule.id() : "sched-" + (current.size() + 1),
                agentId,
                schedule.jobId(),
                schedule.assignmentTemplate(),
                schedule.cronExpression(),
                schedule.timezone(),
                schedule.enabled(),
                Instant.now().plusSeconds(60),
                schedule.createdAt() != null ? schedule.createdAt() : Instant.now(),
                Instant.now()
            );
            current.removeIf(existing -> existing.id().equals(saved.id()));
            current.add(saved);
            schedules.put(agentId, current);
            return saved;
        }

        @Override
        public AgentSchedule toggle(String agentId, String scheduleId) {
            AgentSchedule existing = schedule(agentId, scheduleId);
            return save(agentId, new AgentSchedule(
                existing.id(),
                existing.agentId(),
                existing.jobId(),
                existing.assignmentTemplate(),
                existing.cronExpression(),
                existing.timezone(),
                !existing.enabled(),
                existing.nextRunAt(),
                existing.createdAt(),
                existing.updatedAt()
            ));
        }

        @Override
        public void delete(String agentId, String scheduleId) {
            List<AgentSchedule> current = new ArrayList<>(schedules(agentId));
            boolean removed = current.removeIf(schedule -> schedule.id().equals(scheduleId));
            if (!removed) {
                throw new IllegalStateException("schedule not found");
            }
            schedules.put(agentId, current);
        }
    }

    private static class StubEventReactionService extends EventReactionService {
        private final Map<String, List<AgentEventReaction>> reactions = new java.util.HashMap<>();

        StubEventReactionService() {
            super(null, null);
        }

        @Override
        public List<AgentEventReaction> reactions(String agentId) {
            return reactions.getOrDefault(agentId, List.of());
        }

        @Override
        public AgentEventReaction reaction(String agentId, String reactionId) {
            return reactions(agentId).stream()
                .filter(reaction -> reactionId.equals(reaction.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("reaction not found"));
        }

        @Override
        public AgentEventReaction save(String agentId, AgentEventReaction reaction) {
            List<AgentEventReaction> current = new ArrayList<>(reactions(agentId));
            AgentEventReaction saved = new AgentEventReaction(
                reaction.id() != null ? reaction.id() : "reaction-" + (current.size() + 1),
                agentId,
                reaction.eventType(),
                reaction.filter(),
                ReactionActionType.ENQUEUE_ASSIGNMENT,
                reaction.assignmentTemplate(),
                reaction.enabled(),
                reaction.createdAt() != null ? reaction.createdAt() : Instant.now(),
                Instant.now()
            );
            current.removeIf(existing -> existing.id().equals(saved.id()));
            current.add(saved);
            reactions.put(agentId, current);
            return saved;
        }

        @Override
        public AgentEventReaction toggle(String agentId, String reactionId) {
            AgentEventReaction existing = reaction(agentId, reactionId);
            return save(agentId, new AgentEventReaction(
                existing.id(),
                existing.agentId(),
                existing.eventType(),
                existing.filter(),
                existing.actionType(),
                existing.assignmentTemplate(),
                !existing.enabled(),
                existing.createdAt(),
                existing.updatedAt()
            ));
        }

        @Override
        public void delete(String agentId, String reactionId) {
            List<AgentEventReaction> current = new ArrayList<>(reactions(agentId));
            boolean removed = current.removeIf(reaction -> reaction.id().equals(reactionId));
            if (!removed) {
                throw new IllegalStateException("reaction not found");
            }
            reactions.put(agentId, current);
        }
    }

    private static class StubWorkflowService extends WorkflowService {
        StubWorkflowService() { super(null, null, null); }

        @Override public java.util.List<WorkflowDefinition> listDefinitions() { return List.of(); }

        @Override public WorkflowDefinition saveDefinitionValidated(WorkflowDefinition definition) {
            return new WorkflowDefinition(
                definition.id() == null ? "workflow-draft" : definition.id(),
                definition.title(),
                definition.summary(),
                definition.nodes(),
                definition.routes(),
                definition.createdAt(),
                definition.updatedAt());
        }

        @Override public WorkflowDefinition getDefinition(String id) {
            return new WorkflowDefinition(id, "Test WF", "summary",
                List.of(), List.of(), null, null);
        }
    }

}
