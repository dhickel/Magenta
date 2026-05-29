package io.mindspice.magenta2.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessageToType;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessageType;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RootRelativePathService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileActionLogRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileMetadataRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileMetadataService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileLabelTargetType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import io.mindspice.magenta2.avatar.AvatarEvent;
import io.mindspice.magenta2.avatar.AvatarRepository;
import io.mindspice.magenta2.avatar.AvatarSchemaInitializer;
import io.mindspice.magenta2.avatar.AvatarService;
import io.mindspice.magenta2.avatar.AvatarTaskStatus;
import io.mindspice.magenta2.avatar.AvatarTodoStatus;
import io.mindspice.magenta2.avatar.AvatarPriority;
import io.mindspice.magenta2.avatar.PlannerRecurrence;
import io.mindspice.magenta2.avatar.PlannerRecurrenceMode;
import io.mindspice.magenta2.avatar.PlannerSubtodo;
import io.mindspice.magenta2.avatar.PlannerTask;
import io.mindspice.magenta2.avatar.PlannerTaskLink;
import io.mindspice.magenta2.avatar.PlannerTaskStatus;
import io.mindspice.magenta2.avatar.dashboard.DashboardWidgetDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;

class AvatarDashboardControllerTest {
    @TempDir
    Path tempDir;

    private AvatarService avatarService;
    private WorkAreaService workAreaService;
    private WorkAreaExplorerService workAreaExplorerService;
    private AvatarDashboardController controller;

    @BeforeEach
    void setUp() throws IOException {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        new AvatarSchemaInitializer(dataSource).initialize();
        avatarService = new AvatarService(new AvatarRepository(new JdbcTemplate(dataSource), new ObjectMapper()));
        avatarService.appendEvent(new AvatarEvent(
            "alert-1",
            "alert.manual",
            Map.of("body", "Check inbox"),
            Instant.parse("2026-05-22T10:00:00Z")
        ));
        JdbcTemplate runtimeJdbc = new JdbcTemplate(new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        ));
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(runtimeJdbc);
        WorkAreaRepository workAreaRepository = new WorkAreaRepository(runtimeJdbc);
        WorkspaceFileActionLogRepository actionLogRepository = new WorkspaceFileActionLogRepository(runtimeJdbc);
        WorkspaceFileMetadataRepository metadataRepository = new WorkspaceFileMetadataRepository(runtimeJdbc);
        WorkspaceFileMetadataService metadataService =
            new WorkspaceFileMetadataService(metadataRepository, actionLogRepository);
        AiConfig runtimeConfig = new AiConfig(
            null,
            null,
            null,
            10,
            tempDir.resolve("data"),
            Map.of(),
            Map.of()
        );
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(runtimeConfig);
        WorkspaceService workspaceService = new WorkspaceService(
            workspaceRepository,
            runtimeConfig,
            new RootRelativePathService(directoryService)
        );
        workAreaService = new WorkAreaService(workAreaRepository, workspaceService, directoryService);
        workAreaExplorerService = new WorkAreaExplorerService(workAreaService, metadataService, actionLogRepository);
        controller = new AvatarDashboardController(
            avatarService,
            new StubChatService(),
            new StubOutputArtifactService(tempDir),
            new StubAgentProfileService(),
            new StubJobService(),
            new EmptyAssignmentProvider(),
            new FixedProvider<>(workAreaService),
            new FixedProvider<>(workAreaExplorerService),
            new StubInboxService()
        );
    }

    @Test
    void homeRendersAssistantDashboardSelectorChatAndScopedAssets() {
        String html = controller.avatar(false);

        assertThat(html).contains("/css/avatar-dashboard.css?v=9");
        assertThat(html).contains("/js/avatar-chat.js?v=4");
        assertThat(html).contains("/js/avatar-layout-edit.js?v=1");
        assertThat(html).contains("/js/avatar-workarea-editor.js?v=2");
        assertThat(html).contains("/js/avatar-shell.js?v=6");
        assertThat(html).doesNotContain("/js/chat-client.js");
        assertThat(html).contains("id=\"content-area\" class=\"avatar-content-area\"");
        assertThat(html).contains("id=\"dashboard-selector\"");
        assertThat(html).contains(">Assistant</a>");
        assertThat(html).contains("hx-get=\"/dashboards/assistant/_page\"");
        assertThat(html).contains("hx-target=\"#dashboard-home\"");
        assertThat(html).contains("hx-swap=\"outerHTML\"");
        assertThat(html).contains("hx-push-url=\"/dashboards/assistant\"");
        assertThat(html).contains("aria-label=\"Create dashboard\"");
        assertThat(html).contains("id=\"avatar-chat\"");
        assertThat(html).contains("id=\"avatar-edit-container\"");
        assertThat(html).contains("hx-on::before-swap=\"if (event.detail.xhr.status === 400)");
        assertThat(html).contains("data-avatar-chat=\"true\"");
        assertThat(html).contains("data-avatar-chat-corner-resizer=\"true\"");
        assertThat(html).doesNotContain("data-avatar-chat-resizer=\"true\"");
        assertThat(html).contains("data-avatar-shell=\"true\"");
        assertThat(html).contains("data-dashboard-home=\"true\"");
        int railIndex = html.indexOf("class=\"avatar-shell-rail\"");
        int mainIndex = html.indexOf("class=\"avatar-shell-main\"");
        assertThat(railIndex).isGreaterThan(-1);
        assertThat(mainIndex).isGreaterThan(railIndex);
        assertThat(html).contains("id=\"dashboard-panel\"");
        assertThat(html).doesNotContain("data-avatar-tab=\"queue\"");
        assertThat(html).doesNotContain("data-avatar-tab=\"work-areas\"");
        assertThat(html).doesNotContain("/_dashboards/_tab-panel");
        assertThat(html).doesNotContain("Work Areas");
        assertPrimaryTopNav(html);
        assertThat(html).doesNotContain("Organizer");
        assertThat(html).doesNotContain("Refresh Widgets");
        assertThat(html).doesNotContain(">Avatar<");
        assertThat(html)
            .contains("data-avatar-widget-type=\"today-planner\"")
            .contains("data-avatar-widget-type=\"tasks-routines\"")
            .contains("data-avatar-widget-type=\"calendar-schedule\"");

        String editHtml = controller.avatar(true);
        assertThat(editHtml).contains("avatar-widget-grid-editing");
        assertThat(editHtml).contains("Dashboard edit mode");
        assertThat(editHtml).contains("avatar-icon-link");
        assertThat(editHtml).contains("hx-get=\"/dashboards/assistant/_page\"");
        assertThat(editHtml).contains("hx-push-url=\"/dashboards/assistant\"");

        String editRowsHtml = controller.avatar(true);
        assertThat(editRowsHtml).contains("editable-row-wrapper");
        assertThat(editRowsHtml).contains("add-module-section");
        assertThat(editRowsHtml).contains("insert-row-section");
        assertThat(editRowsHtml).contains("avatar-row-decoration");
        assertThat(editRowsHtml).contains("avatar-widget-corner-controls");
        assertThat(editRowsHtml).contains("avatar-chat-status");
        assertThat(editRowsHtml).contains("/width-picker");
        assertThat(editRowsHtml).doesNotContain("/avatar/_layout");
        assertThat(editRowsHtml).doesNotContain("Refresh Widgets");
        assertThat(editRowsHtml).doesNotContain("avatar-widget-decoration");
    }

    @Test
    void dashboardPageFragmentSwapsDashboardHomeWithoutFullShell() {
        var dashboard = avatarService.createDashboard("Research");
        var response = new org.springframework.mock.web.MockHttpServletResponse();

        String fragment = controller.dashboardPageFragment(dashboard.id(), false, response);

        assertThat(response.getHeader("HX-Push-Url")).isEqualTo("/dashboards/" + dashboard.id());
        assertThat(fragment).contains("id=\"dashboard-home\"");
        assertThat(fragment).contains("data-avatar-shell=\"true\"");
        assertThat(fragment).contains("Research is empty");
        assertThat(fragment).contains("hx-get=\"/dashboards/assistant/_page\"");
        assertThat(fragment).contains("hx-get=\"/dashboards/" + dashboard.id() + "/_page\"");
        assertThat(fragment).contains("hx-target=\"#dashboard-home\"");
        assertThat(fragment).doesNotContain("id=\"content-area\"");
        assertThat(fragment).doesNotContain("/js/avatar-shell.js");
    }

    private static void assertPrimaryTopNav(String html) {
        int home = html.indexOf("<a href=\"/\" class=\"navbar-item\">Home</a>");
        int chat = html.indexOf("<a href=\"/chat\" class=\"navbar-item\">Chat</a>");
        int agents = html.indexOf("<a href=\"/agents\" class=\"navbar-item\">Agents</a>");
        int manage = html.indexOf("<a href=\"/manage\" class=\"navbar-item\">Manage</a>");

        assertThat(home).isGreaterThanOrEqualTo(0);
        assertThat(chat).isGreaterThan(home);
        assertThat(agents).isGreaterThan(chat);
        assertThat(manage).isGreaterThan(agents);
        assertThat(html).doesNotContain("<a href=\"/avatar\" class=\"navbar-item\">Avatar</a>");
    }

    @Test
    void widgetFragmentsReturnStableTargets() {
        String grid = controller.widgets(false);
        String editGrid = controller.widgets(true);
        String todos = controller.widget("todos");

        assertThat(grid).contains("id=\"avatar-widget-grid\"");
        assertThat(editGrid).contains("avatar-widget-grid-editing");
        assertThat(todos).contains("data-avatar-widget-type=\"todos\"");
        assertThat(todos).contains("hx-post=\"/_dashboards/_todos\"");
        assertThat(controller.widgetDetail("todos")).contains("avatar-widget-detail-modal");
        assertThatThrownBy(() -> controller.widget("unknown"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tasksRoutinesDetailRendersAndAppliesHtmxFilters() {
        PlannerTask activeRecurring = avatarService.savePlannerTask(new PlannerTask(
            null,
            "Water plants",
            null,
            PlannerTaskStatus.ACTIVE,
            AvatarPriority.HIGH,
            Instant.now().plusSeconds(3600),
            null,
            "UTC",
            new PlannerRecurrence(PlannerRecurrenceMode.DAILY, 1, LocalDate.now(), null,
                LocalTime.of(13, 0), null, null, null),
            new PlannerTaskLink("project-1", null, null, null),
            null,
            null,
            null
        ));
        avatarService.savePlannerSubtodo(new PlannerSubtodo(
            null,
            activeRecurring.id(),
            "Check moisture",
            AvatarTodoStatus.OPEN,
            0,
            null,
            null
        ));
        avatarService.savePlannerTask(new PlannerTask(
            null,
            "Archive receipts",
            null,
            PlannerTaskStatus.DONE,
            AvatarPriority.NORMAL,
            Instant.now().plusSeconds(7200),
            null,
            "UTC",
            new PlannerRecurrence(PlannerRecurrenceMode.NONE, 1, null, null, null, null, null, null),
            new PlannerTaskLink(null, null, null, null),
            null,
            null,
            null
        ));

        String html = controller.widgetDetail("tasks-routines", "ACTIVE", "WEEK", "RECURRING");

        assertThat(html)
            .contains("name=\"status\"")
            .contains("name=\"range\"")
            .contains("name=\"recurrence\"")
            .contains("hx-get=\"/_dashboards/_widgets/tasks-routines/detail\"")
            .contains("Water plants")
            .contains("project project-1")
            .contains("Check moisture")
            .doesNotContain("Archive receipts");
        assertThat(avatarService.tasksRoutines("ACTIVE", "WEEK", "RECURRING").tasks())
            .extracting(PlannerTask::id)
            .containsExactly(activeRecurring.id());
    }

    @Test
    void todayPlannerDetailRendersOverdueAndUnscheduledWork() {
        avatarService.savePlannerTask(new PlannerTask(
            null,
            "File overdue invoice",
            null,
            PlannerTaskStatus.PLANNED,
            AvatarPriority.HIGH,
            LocalDate.now().minusDays(1).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(),
            null,
            "UTC",
            new PlannerRecurrence(PlannerRecurrenceMode.NONE, 1, null, null, null, null, null, null),
            new PlannerTaskLink(null, null, null, null),
            null,
            null,
            null
        ));
        avatarService.quickCapture("Sort unscheduled inbox", null);

        String html = controller.widgetDetail("today-planner");

        assertThat(html)
            .contains("Overdue")
            .contains("File overdue invoice")
            .contains("Unscheduled")
            .contains("Sort unscheduled inbox");
    }

    @Test
    void dashboardCreationCreatesEmptySelectedDashboard() {
        String modal = controller.createDashboardModal();
        assertThat(modal).contains("Create Dashboard");
        assertThat(modal).contains("hx-post=\"/dashboards\"");
        assertThat(modal).contains("name=\"name\"");
        assertThat(modal).contains("required=\"required\"");

        var response = new org.springframework.mock.web.MockHttpServletResponse();
        String created = controller.createDashboard("Research", response);
        assertThat(response.getHeader("HX-Push-Url")).contains("/dashboards/dashboard-");
        assertThat(created).contains("Research");
        assertThat(created).contains("Research is empty");
        assertThat(created).contains("hx-post=\"/dashboards/");
        assertThat(avatarService.dashboards()).extracting("name").contains("Assistant", "Research");

        var duplicateResponse = new org.springframework.mock.web.MockHttpServletResponse();
        String duplicate = controller.createDashboard("research", duplicateResponse);
        assertThat(duplicateResponse.getStatus()).isEqualTo(400);
        assertThat(duplicate).contains("dashboard already exists");

        var blankResponse = new org.springframework.mock.web.MockHttpServletResponse();
        String blank = controller.createDashboard(" ", blankResponse);
        assertThat(blankResponse.getStatus()).isEqualTo(400);
        assertThat(blank).contains("dashboard name is required");
        assertThat(blank).contains("aria-invalid=\"true\"");
    }

    @Test
    void assistantDoesNotExposeWorkAreasAsDashboardWidget() {
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "agent-1", "Other Agent Home");

        String html = controller.avatar(false);
        assertThat(html).doesNotContain("Work Areas");
        assertThat(html).doesNotContain("/avatar/_work-areas/");
        assertThatThrownBy(() -> controller.widget("files"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void workAreaExplorerFragmentsExposeStableRoutesTargetsAndOperationForms() throws Exception {
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = workAreaService.list(WorkspaceOwnerType.AGENT, "avatar", false).getFirst().id();
        workAreaExplorerService.createDirectory(workAreaId, "notes");
        workAreaExplorerService.createTextFile(workAreaId, "notes", "todo.txt");
        workAreaExplorerService.saveText(workAreaId, "notes/todo.txt", "hello");
        workAreaExplorerService.ensureTag("note", "Note");
        workAreaExplorerService.ensureTag("work-area", "Work Area");
        workAreaExplorerService.ensureTag("project-alpha", "Project Alpha");
        workAreaExplorerService.ensureTag("review", "Review");
        workAreaExplorerService.ensureTag(
            "directory-context",
            "Directory Context",
            WorkspaceFileLabelTargetType.DIRECTORY,
            "Directory-only operating context with a deliberately long LLM description for truncation checks."
        );
        workAreaExplorerService.ensureTag(
            "file-context",
            "File Context",
            WorkspaceFileLabelTargetType.FILE,
            "File-only operating context with a deliberately long LLM description for truncation checks."
        );
        workAreaExplorerService.addLabel(workAreaId, "notes/todo.txt", "note");
        workAreaExplorerService.addLabel(workAreaId, "notes/todo.txt", "work-area");
        workAreaExplorerService.addLabel(workAreaId, "notes/todo.txt", "project-alpha");
        workAreaExplorerService.addLabel(workAreaId, "notes/todo.txt", "review");

        String shell = controller.workAreaExplorer(workAreaId, "notes", "notes/todo.txt");
        assertThat(shell).contains("id=\"avatar-workarea-explorer-shell\"");
        assertThat(shell).contains("id=\"avatar-workarea-list-region\"");
        assertThat(shell).contains("id=\"avatar-workarea-inspector\"");
        assertThat(shell).contains("id=\"avatar-workarea-modal\"");
        assertThat(shell).contains("avatar-workarea-explorer-layout");
        assertThat(shell).contains("workspace-explorer-table-region");
        assertThat(shell).contains("file-explorer-inspector-pane");
        assertThat(shell).contains("data-workarea-path=\"notes/todo.txt\"");
        assertThat(shell).contains("data-workarea-open-url=\"/avatar/_work-areas/" + workAreaId + "/viewer?path=notes%2Ftodo.txt\"");
        assertThat(shell).contains("data-workarea-open-target=\"#avatar-workarea-modal\"");
        assertThat(shell).contains("data-workarea-open-swap=\"innerHTML\"");
        assertThat(shell).contains("hx-trigger=\"click[!event.target.closest('button,a,input,select,textarea,label,summary,details')]\"");
        assertThat(shell).contains(
            "hx-get=\"/avatar/_work-areas/" + workAreaId + "/explorer?path=notes&selected=notes%2Ftodo.txt&panel=expanded\""
        );
        assertThat(shell).contains("hx-get=\"/avatar/_work-areas/" + workAreaId + "/viewer?path=notes%2Ftodo.txt\"");
        assertThat(shell).contains(
            "hx-get=\"/avatar/_work-areas/" + workAreaId + "/modal/rename?path=notes%2Ftodo.txt&panel=expanded\""
        );
        assertThat(shell).contains(
            "hx-get=\"/avatar/_work-areas/" + workAreaId + "/modal/delete?path=notes%2Ftodo.txt&panel=expanded\""
        );
        assertThat(shell).contains("workspace-explorer-action-button");
        assertThat(shell).contains("aria-label=\"View file\"");
        assertThat(shell).contains("aria-label=\"Rename\"");
        assertThat(shell).contains("aria-label=\"Delete\"");
        assertThat(shell).contains("aria-label=\"Copy\"");
        assertThat(shell).contains("aria-label=\"Move\"");
        assertThat(shell).contains("+1");
        assertThat(shell).doesNotContain("file-explorer-cards");
        assertThat(shell).doesNotContain("file-explorer-entry");

        String list = controller.workAreaExplorerList(workAreaId, "notes", "notes/todo.txt");
        assertThat(list).contains("id=\"avatar-workarea-list-region\"");
        assertThat(list).contains("<th>Name</th>");
        assertThat(list).contains("<th>File Type</th>");
        assertThat(list).contains("<th>Size</th>");
        assertThat(list).contains("<th>Created</th>");
        assertThat(list).contains("<th>Last Modified</th>");
        assertThat(list).contains("<th>Tags</th>");
        assertThat(list).contains("<th>Actions</th>");
        assertThat(list).contains("selected");
        assertThat(list).contains("hx-get=\"/avatar/_work-areas/" + workAreaId + "/viewer?path=notes%2Ftodo.txt\"");
        assertThat(list).contains(
            "hx-get=\"/avatar/_work-areas/" + workAreaId + "/modal/rename?path=notes%2Ftodo.txt&panel=expanded\""
        );
        assertThat(list).contains(
            "hx-get=\"/avatar/_work-areas/" + workAreaId + "/modal/delete?path=notes%2Ftodo.txt&panel=expanded\""
        );
        assertThat(list).doesNotContain("file-explorer-cards");

        String inspect = controller.workAreaInspector(workAreaId, "notes/todo.txt");
        assertThat(inspect).contains("id=\"avatar-workarea-inspector\"");
        assertThat(inspect).contains("file-explorer-inspector-pane");
        assertThat(inspect).contains("Manage Tags");
        assertThat(inspect).contains("workspace-manage-tags-button");
        assertThat(inspect).contains(
            "hx-get=\"/avatar/_work-areas/" + workAreaId + "/modal/tag-editor?path=notes%2Ftodo.txt&panel=expanded\""
        );
        assertThat(inspect).contains("avatar-workarea-inspector-preview-text");
        assertThat(inspect).doesNotContain("Preview &amp; Details");
        assertThat(inspect).doesNotContain("Markdown file. Rendered and raw text views are available.");
        assertThat(inspect).doesNotContain("hx-get=\"/avatar/_work-areas/" + workAreaId + "/viewer?path=notes%2Ftodo.txt\"");
        assertThat(inspect).doesNotContain("/files/action/copy/picker?path=notes%2Ftodo.txt");
        assertThat(inspect).doesNotContain("workspace-tag-selector");
        assertThat(inspect).doesNotContain("workspace-tag-remove");

        String collapsed = controller.workAreaInspector(
            workAreaId,
            "notes/todo.txt",
            "notes",
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_COLLAPSED
        );
        assertThat(collapsed).contains("file-explorer-inspector-pane-collapsed");
        assertThat(collapsed).contains("title=\"Open details panel\"");
        assertThat(collapsed).contains("workspace-inspector-rail-toggle-label");
        assertThat(collapsed).contains(">Details<");
        assertThat(collapsed).doesNotContain("file-explorer-inspector-collapsed-label");
        assertThat(collapsed).doesNotContain(">todo.txt<");
        assertThat(collapsed).doesNotContain(">.<");
        assertThat(collapsed).contains(
            "hx-get=\"/avatar/_work-areas/" + workAreaId + "/explorer?path=notes&selected=notes%2Ftodo.txt&panel=expanded\""
        );
        assertThat(collapsed).doesNotContain("Preview &amp; Details");

        String rename = controller.workAreaActionModal(workAreaId, "rename", "notes/todo.txt");
        assertThat(rename).contains("hx-post=\"/avatar/_work-areas/" + workAreaId + "/files/rename\"");
        assertThat(rename).contains("hx-target=\"#avatar-workarea-modal\"");
        assertThat(rename).contains("hx-get=\"/avatar/_work-areas/modal/clear\"");
        assertThat(rename).contains("hx-swap=\"outerHTML\"");

        String copy = controller.workAreaActionModal(workAreaId, "copy", "notes/todo.txt");
        assertThat(copy).contains("hx-post=\"/avatar/_work-areas/" + workAreaId + "/files/action/copy\"");
        assertThat(copy).contains("workspace-directory-picker");
        assertThat(copy).contains("name=\"destination\"");

        String move = controller.workAreaActionModal(workAreaId, "move", "notes/todo.txt");
        assertThat(move).contains("hx-post=\"/avatar/_work-areas/" + workAreaId + "/files/action/move\"");

        String delete = controller.workAreaActionModal(workAreaId, "delete", "notes/todo.txt");
        assertThat(delete).contains("hx-post=\"/avatar/_work-areas/" + workAreaId + "/files/delete\"");
        assertThat(delete).contains("name=\"step\" value=\"FILE_CONFIRM\"");

        String tag = controller.workAreaActionModal(workAreaId, "tag", "notes/todo.txt");
        assertThat(tag).contains("Tag Editor");
        assertThat(tag).contains("avatar-modal-workarea");
        assertThat(tag).contains("workspace-tag-editor-header");
        assertThat(tag).contains("workspace-tag-editor-filters");
        assertThat(tag).contains("workspace-tag-filter-directory");
        assertThat(tag).contains("workspace-tag-filter-file");
        assertThat(tag).contains("workspace-tag-editor-table-header");
        assertThat(tag).contains("workspace-tag-editor-row");
        assertThat(tag).contains("workspace-tag-editor-summary");
        assertThat(tag).contains("workspace-tag-editor-detail");
        assertThat(tag).contains("data-tag-type=\"directory\"");
        assertThat(tag).contains("data-tag-type=\"file\"");
        assertThat(tag).contains("workspace-tag-editor-description");
        assertThat(tag).contains("workspace-tag-editor-edit-form");
        assertThat(tag).contains("workspace-tag-editor-assign-form");
        assertThat(tag).doesNotContain("Delete Tag");
        assertThat(tag).doesNotContain("hx-delete");
        assertThat(tag).contains("hx-post=\"/avatar/_work-areas/" + workAreaId + "/modal/tag-editor/tags\"");
        assertThat(tag).contains("hx-post=\"/avatar/_work-areas/" + workAreaId + "/modal/tag-editor/assign\"");
        assertThat(tag).contains("<option value=\"directory\"");
        assertThat(tag).contains("<option value=\"file\"");
    }

    @Test
    void workAreaMutationsReturnOobRefreshesForListInspectorAndModal() throws Exception {
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = workAreaService.list(WorkspaceOwnerType.AGENT, "avatar", false).getFirst().id();
        workAreaExplorerService.createDirectory(workAreaId, "notes");
        workAreaExplorerService.createDirectory(workAreaId, "archive");
        workAreaExplorerService.createDirectory(workAreaId, "notes/dest");
        workAreaExplorerService.createTextFile(workAreaId, "notes", "todo.txt");

        String renamed = controller.renameWorkAreaPath(workAreaId, "notes/todo.txt", "renamed.txt");
        assertOobRefresh(renamed);
        assertThat(renamed).contains("renamed.txt");

        String copied = controller.copyMoveWorkAreaPath(workAreaId, "copy", "notes/renamed.txt", "archive", "copy.txt");
        assertOobRefresh(copied);
        assertThat(copied).contains("copy.txt");

        String moved = controller.copyMoveWorkAreaPath(workAreaId, "move", "archive/copy.txt", "notes", "moved.txt");
        assertOobRefresh(moved);
        assertThat(moved).contains("moved.txt");

        String copiedToSiblingDestination = controller.copyMoveWorkAreaPath(
            workAreaId,
            "copy",
            "notes/renamed.txt",
            "dest",
            "sibling-copy.txt"
        );
        assertOobRefresh(copiedToSiblingDestination);
        assertThat(copiedToSiblingDestination).contains("sibling-copy.txt");
        assertThat(workAreaExplorerService.inspect(workAreaId, "notes/dest/sibling-copy.txt").regularFile()).isTrue();

        String missingDestination = controller.copyMoveWorkAreaPath(workAreaId, "copy", "notes/renamed.txt", "", "bad.txt");
        assertThat(missingDestination).contains("File action failed");
        assertThat(missingDestination).contains("destination directory is required");

        String deleted = controller.deleteWorkAreaPathStep(workAreaId, "notes/moved.txt", "FILE_CONFIRM");
        assertOobRefresh(deleted);
        assertThat(deleted).contains("Deleted notes/moved.txt");
    }

    @Test
    void workAreaViewerRejectsUnsupportedFilesAndTextSaveErrorsAreVisible() throws Exception {
        var home = workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = home.id();
        Path root = workAreaService.resolve(home);
        Files.write(root.resolve("blob.bin"), new byte[] {0, 1, 2, 3});

        String unsupported = controller.workAreaViewer(workAreaId, "blob.bin");
        assertThat(unsupported).contains("class=\"avatar-modal");
        assertThat(unsupported).doesNotContain("id=\"avatar-workarea-modal\"");
        assertThat(unsupported).contains("Viewer unavailable for this file type or size.");

        String save = controller.saveWorkAreaText(workAreaId, "blob.bin", "oops");
        assertThat(save).contains("class=\"avatar-modal");
        assertThat(save).doesNotContain("id=\"avatar-workarea-modal\"");
        assertThat(save).contains("Save failed");
        assertThat(save).contains("not safe for text editing");
    }

    @Test
    void workAreaViewerSupportsMarkdownTextImageAndFriendlyMarkdownFailure() throws Exception {
        var home = workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = home.id();
        Path root = workAreaService.resolve(home);
        Files.writeString(root.resolve("note.md"), "# Heading\n\nbody");
        Files.writeString(root.resolve("plain.txt"), "raw text");
        Files.write(root.resolve("pic.png"), new byte[] {1, 2, 3, 4});

        String markdown = controller.workAreaViewer(workAreaId, "note.md");
        assertThat(markdown).contains("class=\"avatar-modal");
        assertThat(markdown).doesNotContain("id=\"avatar-workarea-modal\"");
        assertThat(markdown).contains("data-viewer-kind=\"markdown\"");
        assertThat(markdown).contains("data-active-tab=\"preview\"");
        assertThat(markdown.indexOf("data-editor-mode=\"preview\""))
            .isLessThan(markdown.indexOf("data-editor-mode=\"edit\""));
        assertThat(markdown).contains("data-editor-mode=\"edit\"");
        assertThat(markdown).contains("data-editor-mode=\"preview\"");
        assertThat(markdown).contains("data-editor-mode=\"split\"");
        assertThat(markdown).contains("<h1>Heading</h1>");

        String markdownText = controller.workAreaTextViewer(workAreaId, "note.md", "text");
        assertThat(markdownText).contains("textarea");
        assertThat(markdownText).contains("data-viewer-kind=\"markdown\"");
        assertThat(markdownText).contains("data-active-tab=\"edit\"");
        assertThat(markdownText).contains("data-editor-mode=\"edit\"");
        assertThat(markdownText).contains("data-editor-mode=\"preview\"");
        assertThat(markdownText).contains("data-editor-mode=\"split\"");
        assertThat(markdownText).contains("data-editor-source=\"true\"");

        String plainText = controller.workAreaViewer(workAreaId, "plain.txt");
        assertThat(plainText).contains("textarea");
        assertThat(plainText).contains("data-viewer-kind=\"text\"");
        assertThat(plainText).contains("data-active-tab=\"preview\"");
        assertThat(plainText).contains("data-editor-mode=\"preview\"");
        assertThat(plainText).contains("data-editor-mode=\"edit\"");
        assertThat(plainText).contains("data-editor-plain-preview=\"true\"");
        assertThat(plainText).contains("aria-selected=\"true\"");
        assertThat(plainText).doesNotContain("data-editor-mode=\"split\"");

        String image = controller.workAreaViewer(workAreaId, "pic.png");
        assertThat(image).contains("avatar-workarea-image-frame");
        assertThat(image).contains("<img class=\"avatar-workarea-image\"");
        assertThat(image).contains("/api/work-areas/" + workAreaId + "/files/view?path=pic.png");
        assertThat(image).contains("/api/work-areas/" + workAreaId + "/files/download?path=pic.png");

        String failedMarkdown = WorkAreaExplorerFragments.renderedMarkdownForTest(
            "# broken",
            content -> {
                throw new RuntimeException("forced");
            }
        );
        assertThat(failedMarkdown).contains("avatar-workarea-render-fallback");
        assertThat(failedMarkdown).contains("# broken");
        assertThat(failedMarkdown).contains("avatar-workarea-render-error");
        assertThat(failedMarkdown).contains("Raw text is still available");
    }

    @Test
    void markdownPreviewRouteRendersUnsavedContentWithoutPersistingAndSanitizesHtml() throws Exception {
        var home = workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = home.id();
        Path root = workAreaService.resolve(home);
        Files.writeString(root.resolve("note.md"), "# Stored\n\nsafe");

        String unsavedPreview = controller.workAreaMarkdownPreview(
            workAreaId,
            "note.md",
            "# Unsaved\n\n<script>alert('x')</script>\n\n|A|B|\n|-|-|\n|1|2|"
        );
        assertThat(unsavedPreview).contains("magenta-rendered-markdown");
        assertThat(unsavedPreview).contains("<h1>Unsaved</h1>");
        assertThat(unsavedPreview).contains("<table>");
        assertThat(unsavedPreview).doesNotContain("<script>");

        String persisted = controller.workAreaViewer(workAreaId, "note.md");
        assertThat(persisted).contains("<h1>Stored</h1>");
        assertThat(persisted).doesNotContain("<h1>Unsaved</h1>");
    }

    @Test
    void workAreaTagRoutesCreateAssignAndRemoveCustomTags() throws Exception {
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = workAreaService.list(WorkspaceOwnerType.AGENT, "avatar", false).getFirst().id();
        workAreaExplorerService.createDirectory(workAreaId, "notes");

        String created = controller.createWorkAreaTag(workAreaId, "project-alpha", "Project Alpha");
        assertThat(created).contains("Tag is ready to assign: project-alpha");

        String added = controller.addWorkAreaTag(workAreaId, "notes", "project-alpha");
        assertOobRefresh(added);
        assertThat(added).contains("id=\"avatar-workarea-inspector\"");
        assertThat(added).contains("project-alpha");
        assertThat(added).contains("Manage Tags");
        assertThat(added).doesNotContain("workspace-tag-remove");

        String removed = controller.removeWorkAreaTag(workAreaId, "notes", "project-alpha");
        assertOobRefresh(removed);
        assertThat(removed).contains("Tag removed");
        assertThat(removed).contains("No tags");
    }

    @Test
    void workAreaTagAssignmentRejectsForgedTargetTypeAndDoesNotAssign() throws Exception {
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = workAreaService.list(WorkspaceOwnerType.AGENT, "avatar", false).getFirst().id();
        workAreaExplorerService.createDirectory(workAreaId, "notes");
        workAreaExplorerService.createTextFile(workAreaId, "notes", "todo.txt");

        String fileMismatch = controller.addWorkAreaTag(
            workAreaId,
            "notes/todo.txt",
            "pw-wrongtype-test-file",
            "directory"
        );
        assertThat(fileMismatch).contains("tag target type mismatch");
        assertThat(workAreaExplorerService.inspect(workAreaId, "notes/todo.txt").tags())
            .extracting(tag -> tag.slug())
            .doesNotContain("pw-wrongtype-test-file");

        String directoryMismatch = controller.addWorkAreaTag(
            workAreaId,
            "notes",
            "pw-wrongtype-test-dir",
            "file"
        );
        assertThat(directoryMismatch).contains("tag target type mismatch");
        assertThat(workAreaExplorerService.inspect(workAreaId, "notes").tags())
            .extracting(tag -> tag.slug())
            .doesNotContain("pw-wrongtype-test-dir");
    }

    @Test
    void tagEditorModalCreatesTypedTagsWithDescriptionAndAssignsSinglePathTarget() throws Exception {
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = workAreaService.list(WorkspaceOwnerType.AGENT, "avatar", false).getFirst().id();
        workAreaExplorerService.createDirectory(workAreaId, "notes");
        workAreaExplorerService.createTextFile(workAreaId, "notes", "todo.txt");
        workAreaExplorerService.ensureTag("dir-only", "Directory Only", WorkspaceFileLabelTargetType.DIRECTORY);

        LinkedMultiValueMap<String, String> createParams = new LinkedMultiValueMap<>();
        createParams.add("path", "notes/todo.txt");
        createParams.add("label", "file-review");
        createParams.add("displayName", "File Review");
        createParams.add("targetType", "file");
        createParams.add("description", "Use for files that should be reviewed by an LLM.");
        String created = controller.createWorkAreaTagFromEditor(workAreaId, createParams);
        assertThat(created).contains("Tag created: file-review");
        assertThat(created).contains("workspace-tag-editor-filters");
        assertThat(created).contains("workspace-tag-filter-directory");
        assertThat(created).contains("workspace-tag-filter-file");
        assertThat(created).contains("Use for files that should be reviewed by an LLM.");

        String modal = controller.workAreaActionModal(workAreaId, "tag", "notes/todo.txt");
        assertThat(modal).contains("dir-only");
        assertThat(modal).contains("file-review");
        assertThat(modal).contains("disabled aria-disabled=\"true\"");

        LinkedMultiValueMap<String, String> assignParams = new LinkedMultiValueMap<>();
        assignParams.add("path", "notes/todo.txt");
        assignParams.add("path", "notes/todo.txt");
        assignParams.add("label", "file-review");
        String assigned = controller.assignWorkAreaTagFromEditor(workAreaId, assignParams);
        assertOobRefresh(assigned);
        assertThat(assigned).contains("Tag added");
        assertThat(workAreaExplorerService.inspect(workAreaId, "notes/todo.txt").tags())
            .extracting(tag -> tag.slug())
            .contains("file-review");
    }

    @Test
    void workAreaFragmentValidationErrorsReturnVisibleFragments() {
        workAreaService.ensureHome(WorkspaceOwnerType.AGENT, "avatar", "Home");
        String workAreaId = workAreaService.list(WorkspaceOwnerType.AGENT, "avatar", false).getFirst().id();

        assertThat(controller.workAreaExplorerList(workAreaId, "../escape", null))
            .contains("id=\"avatar-workarea-list-region\"")
            .contains("path escapes Work Area");
        assertThat(controller.workAreaInspector(workAreaId, "../escape"))
            .contains("id=\"avatar-workarea-inspector\"")
            .contains("path escapes Work Area");
        assertThat(controller.workAreaActionModal(workAreaId, "delete", "../escape"))
            .contains("class=\"avatar-modal")
            .doesNotContain("id=\"avatar-workarea-modal\"")
            .contains("Action unavailable");
    }

    private void assertOobRefresh(String html) {
        assertThat(html).contains("id=\"avatar-workarea-modal\" hx-swap-oob=\"true\"");
        assertThat(html).contains("id=\"avatar-workarea-list-region\" class=\"workspace-explorer-table-region\" hx-swap-oob=\"true\"");
        assertThat(html).contains("id=\"avatar-workarea-inspector\" class=\"file-explorer-inspector-pane\" hx-swap-oob=\"true\"");
    }

    @Test
    void rowLayoutEditorAddsMovesResizesAndRemovesWidgets() {
        String emptyEditor = controller.edit(false);
        assertThat(emptyEditor).isEmpty();

        String emptyWorkAreaModal = controller.clearWorkAreaModal();
        assertThat(emptyWorkAreaModal).isEqualTo("<div id=\"avatar-workarea-modal\"></div>");
        assertThat(controller.clearDashboardModal()).isEmpty();

        String dashboardId = avatarService.createDashboard("Layout Test").id();
        String afterRow = controller.addLayoutRow(dashboardId);
        String rowId = avatarService.dashboardRows(dashboardId).getFirst().id();
        assertThat(afterRow).contains("hx-swap-oob=\"true\"");
        assertThat(afterRow).contains("/_dashboards/_layout/rows/" + rowId + "/catalog");
        assertThat(afterRow).contains("avatar-empty-row-insert");
        assertThat(afterRow).doesNotContain("/_dashboards/_layout/rows/" + rowId + "/insert-after");

        String catalog = controller.widgetCatalog(rowId);
        assertThat(catalog).contains("Add Widget");
        assertThat(catalog).contains("daily-tasks");
        assertThat(catalog).contains("avatar-modal avatar-widget-picker");
        assertThat(catalog).contains("avatar-widget-picker-modal");

        String inserted = controller.insertLayoutRowAfter(rowId);
        assertThat(inserted).contains("hx-swap-oob=\"true\"");
        assertThat(inserted).contains("avatar-widget-catalog");
        assertThat(inserted).contains("avatar-empty-row-insert");
        assertThat(avatarService.dashboardRows(dashboardId)).hasSize(2);

        assertThatThrownBy(() -> controller.addLayoutWidget(rowId, "unknown", 4))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        controller.addLayoutWidget(rowId, "todos", 4);
        String catalogAfterTodos = controller.widgetCatalog(rowId);
        assertThat(catalogAfterTodos)
            .contains("Todos")
            .contains("avatar-catalog-item-disabled")
            .contains("Already on this dashboard.")
            .contains("aria-disabled=\"true\"")
            .contains("Notes")
            .contains("hx-on::before-swap");

        var duplicateResponse = new org.springframework.mock.web.MockHttpServletResponse();
        String duplicateTodos = controller.addLayoutWidget(rowId, "todos", 4, duplicateResponse);
        assertThat(duplicateResponse.getStatus()).isEqualTo(400);
        assertThat(duplicateTodos)
            .contains("dashboard widget already exists: todos")
            .contains("avatar-widget-catalog")
            .contains("Todos")
            .contains("Already on this dashboard.")
            .doesNotContain("id=\"avatar-widget-grid\"");

        controller.addLayoutWidget(rowId, "notes", 4);
        controller.addLayoutWidget(rowId, "notes", 4);
        String todosId = avatarService.dashboardRows(dashboardId).getFirst().widgets().stream()
            .filter(widget -> widget.widgetKey().equals("todos"))
            .findFirst()
            .orElseThrow()
            .id();
        String notesId = avatarService.dashboardRows(dashboardId).getFirst().widgets().stream()
            .filter(widget -> widget.widgetKey().equals("notes"))
            .findFirst()
            .orElseThrow()
            .id();
        String extraNotesId = avatarService.dashboardRows(dashboardId).getFirst().widgets().stream()
            .filter(widget -> widget.widgetKey().equals("notes"))
            .skip(1)
            .findFirst()
            .orElseThrow()
            .id();
        assertThat(avatarService.dashboardRows(dashboardId).getFirst().widgets())
            .filteredOn(widget -> widget.widgetKey().equals("notes"))
            .hasSize(2);

        String settings = controller.widgetSettings(dashboardId, notesId);
        assertThat(settings).contains("Notes Settings");
        assertThat(settings).contains("hx-put=\"/dashboards/" + dashboardId + "/widgets/" + notesId + "/settings\"");
        assertThat(settings).contains("name=\"sourceMode\"");
        assertThat(settings).contains("value=\"dashboard\"");

        LinkedMultiValueMap<String, String> invalidSettings = new LinkedMultiValueMap<>();
        invalidSettings.add("sourceMode", "agent");
        var invalidSettingsResponse = new org.springframework.mock.web.MockHttpServletResponse();
        String invalidSettingsHtml = controller.saveWidgetSettings(
            dashboardId,
            notesId,
            invalidSettings,
            invalidSettingsResponse
        );
        assertThat(invalidSettingsResponse.getStatus()).isEqualTo(400);
        assertThat(invalidSettingsHtml).contains("Agent source mode requires an agent id.");
        assertThat(invalidSettingsHtml).contains("hx-on::before-swap");

        LinkedMultiValueMap<String, String> validSettings = new LinkedMultiValueMap<>();
        validSettings.add("sourceMode", "agent");
        validSettings.add("agentId", "agent-1");
        String savedSettings = controller.saveWidgetSettings(
            dashboardId,
            notesId,
            validSettings,
            new org.springframework.mock.web.MockHttpServletResponse()
        );
        assertThat(savedSettings).contains("hx-swap-oob=\"true\"");
        assertThat(savedSettings).contains("id=\"avatar-widget-" + notesId + "\"");
        controller.removeLayoutWidget(extraNotesId);

        String widthPicker = controller.widgetWidthPicker(todosId);
        assertThat(widthPicker).contains("Widget width");
        assertThat(widthPicker).contains("data-avatar-width-picker");
        assertThat(widthPicker).contains("1/12");
        assertThat(widthPicker).contains("12/12");

        String resized = controller.resizeLayoutWidget(todosId, 5);
        assertThat(resized).contains("id=\"avatar-widget-" + todosId + "\"");
        assertThat(avatarService.dashboardRows(dashboardId).getFirst().widgets()).anySatisfy(widget -> {
            assertThat(widget.widgetKey()).isEqualTo("todos");
            assertThat(widget.columnWidth()).isEqualTo(5);
        });

        controller.cycleLayoutWidgetWidth(todosId);
        assertThat(avatarService.dashboardRows(dashboardId).getFirst().widgets()).anySatisfy(widget -> {
            assertThat(widget.widgetKey()).isEqualTo("todos");
            assertThat(widget.columnWidth()).isEqualTo(3);
        });

        controller.moveLayoutWidget(notesId, "left");
        assertThat(avatarService.dashboardRows(dashboardId).getFirst().widgets())
            .extracting(widget -> widget.widgetKey())
            .containsExactly("notes", "todos");

        controller.removeLayoutWidget(notesId);
        assertThat(avatarService.dashboardRows(dashboardId).getFirst().widgets())
            .extracting(widget -> widget.widgetKey())
            .containsExactly("todos");

        assertThatThrownBy(() -> controller.removeLayoutRow(rowId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        controller.removeLayoutWidget(todosId);
        assertThat(controller.removeLayoutRow(rowId)).contains("avatar-widget-grid-editing");

        controller.addLayoutRow(dashboardId);
        controller.addLayoutRow(dashboardId);
        String secondRowId = avatarService.dashboardRows(dashboardId).get(1).id();
        controller.moveLayoutRow(secondRowId, "up");
        assertThat(avatarService.dashboardRows(dashboardId).getFirst().id()).isEqualTo(secondRowId);
    }

    @Test
    void noteCaptureRefreshesSubmittingWidgetInstanceWhenMultipleNotesWidgetsExist() {
        String dashboardId = avatarService.createDashboard("Notes Instances").id();
        controller.addLayoutRow(dashboardId);
        String rowId = avatarService.dashboardRows(dashboardId).getFirst().id();
        controller.addLayoutWidget(rowId, "notes", 4);
        controller.addLayoutWidget(rowId, "notes", 4);
        List<String> noteWidgetIds = avatarService.dashboardRows(dashboardId).getFirst().widgets().stream()
            .filter(widget -> widget.widgetKey().equals("notes"))
            .map(widget -> widget.id())
            .toList();
        String firstNotesId = noteWidgetIds.getFirst();
        String secondNotesId = noteWidgetIds.get(1);

        String secondBeforeSubmit = controller.widgetByInstance(dashboardId, secondNotesId);
        assertThat(secondBeforeSubmit)
            .contains("id=\"avatar-widget-" + secondNotesId + "\"")
            .contains("hx-post=\"/dashboards/" + dashboardId + "/widgets/" + secondNotesId + "/_notes\"")
            .contains("hx-target=\"#avatar-widget-" + secondNotesId + "\"")
            .doesNotContain("hx-post=\"/_dashboards/_notes\"");

        String submitted = controller.createNoteForWidget(dashboardId, secondNotesId, "Garden", "Water seedlings");

        assertThat(submitted)
            .contains("id=\"avatar-widget-" + secondNotesId + "\"")
            .contains("data-avatar-widget=\"" + secondNotesId + "\"")
            .contains("hx-target=\"#avatar-widget-" + secondNotesId + "\"")
            .contains("Garden")
            .doesNotContain("id=\"avatar-widget-" + firstNotesId + "\"")
            .doesNotContain("data-avatar-widget=\"" + firstNotesId + "\"");
        assertThat(submitted.split("id=\"avatar-widget-" + secondNotesId + "\"", -1)).hasSize(2);
        assertThat(avatarService.notes(false)).singleElement()
            .satisfies(note -> assertThat(note.body()).contains("Water seedlings"));
    }

    @Test
    void organizerEndpointsMutateAvatarServicesAndReturnWidgets() {
        String todoHtml = controller.createTodo("Pay bills", "checking", "HIGH");
        assertThat(todoHtml).contains("Pay bills");
        assertThat(avatarService.todos()).singleElement()
            .satisfies(todo -> assertThat(todo.priority().name()).isEqualTo("HIGH"));

        String dailyHtml = controller.createDailyTask("Review day", null);
        assertThat(dailyHtml).contains("Review day");
        assertThat(avatarService.dailyTasks(LocalDate.now())).singleElement()
            .satisfies(task -> assertThat(task.status()).isEqualTo(AvatarTaskStatus.PLANNED));

        String notesHtml = controller.createNote("Garden", "Water seedlings");
        assertThat(notesHtml).contains("Garden");
        assertThat(avatarService.notes(false)).singleElement()
            .satisfies(note -> assertThat(note.body()).contains("Water seedlings"));

        String organizer = controller.organizer("planner");
        assertThat(organizer).contains("Planner");
        assertThat(organizer).contains("/_dashboards/_planner-tasks");

        String plannerHtml = controller.createPlannerTask(
            "Review projects",
            "Look for blocked work",
            "HIGH",
            "2026-05-29T13:00:00",
            "2026-05-29T14:00:00",
            "DAILY",
            1,
            LocalDate.now().toString(),
            LocalDate.now().plusDays(2).toString(),
            "09:00",
            "",
            null,
            "",
            "project-1",
            "",
            "",
            ""
        );
        assertThat(plannerHtml).contains("Review projects");
        String taskId = avatarService.plannerTasks().getFirst().id();
        String subtodoHtml = controller.createPlannerSubtodo(taskId, "Check queue");
        assertThat(subtodoHtml).contains("Check queue");
        assertThat(controller.organizer("calendar")).contains("Planner projection");

        String captureHtml = controller.quickCapturePlannerTask("Sort seed trays", null);
        assertThat(captureHtml)
            .contains("data-avatar-widget-type=\"today-planner\"")
            .contains("Quick capture")
            .contains("Unscheduled")
            .contains("Sort seed trays")
            .contains("Restart")
            .contains("name=\"reviewNotes\"");
        assertThat(avatarService.plannerTasks()).extracting("title").contains("Sort seed trays");
        String reviewHtml = controller.reviewTodayPlanner("Good progress; move watering to tomorrow.");
        assertThat(reviewHtml)
            .contains("data-avatar-widget-type=\"today-planner\"")
            .contains("Good progress; move watering to tomorrow.");
        assertThat(avatarService.dayMap(LocalDate.now()).reviewNotes())
            .isEqualTo("Good progress; move watering to tomorrow.");

        String blockHtml = controller.createTimeBlock(
            "Planting block",
            "2026-05-29T15:00:00",
            "2026-05-29T16:00:00",
            "task",
            taskId
        );
        assertThat(blockHtml)
            .contains("data-calendar-structure=\"month\"")
            .contains("Planting block")
            .contains("hx-post=\"/_dashboards/_reminders\"")
            .contains("name=\"remindAt\"")
            .contains("Agenda");

        String reminderHtml = controller.createReminder(
            "Check planting block",
            "2026-05-29T14:30:00",
            null,
            "task",
            taskId
        );
        assertThat(reminderHtml)
            .contains("data-avatar-widget-type=\"calendar-schedule\"")
            .contains("Check planting block");

        String occurrenceHtml = controller.updatePlannerOccurrence(
            taskId,
            avatarService.plannerCalendarProjection(null, null).getFirst().occurrenceStart().toString(),
            "SKIPPED",
            null
        );
        assertThat(occurrenceHtml)
            .contains("data-avatar-widget-type=\"tasks-routines\"")
            .contains("Skipped");
    }

    @Test
    void outputPreviewUsesArtifactService() {
        String html = controller.outputPreview("artifact-1");

        assertThat(html).contains("summary");
        assertThat(html).contains("hello output");
        assertThat(html).contains("/api/outputs/artifact-1/download");
    }

    @Test
    void alertDismissAppendsInternalAvatarEventOnly() {
        String html = controller.dismissAlert("alert-1");

        assertThat(html).contains("data-avatar-widget-type=\"alerts\"");
        assertThat(avatarService.events()).anySatisfy(event -> {
            assertThat(event.eventType()).isEqualTo("alert.dismissed");
            assertThat(event.payload()).containsEntry("eventId", "alert-1");
        });
    }

    private static class StubChatService extends ChatService {
        StubChatService() {
            super(null, null, null, null, null);
        }

        @Override
        public String defaultModel() {
            return "qwen3";
        }
    }

    private static class StubOutputArtifactService extends OutputArtifactService {
        StubOutputArtifactService(Path tempDir) throws IOException {
            super(null, new WorkspaceDirectoryService(new AiConfig(
                null,
                null,
                null,
                10,
                tempDir,
                Map.of(),
                Map.of()
            )), new ObjectMapper());
        }

        @Override
        public List<RunOutputArtifact> query(String runId, String planId, String artifactType, Integer limit) {
            return List.of(artifact());
        }

        @Override
        public RunOutputArtifact getArtifact(String artifactId) {
            return artifact();
        }

        @Override
        public String loadContent(String artifactId, long maxBytes) {
            return "hello output";
        }

        private RunOutputArtifact artifact() {
            return new RunOutputArtifact(
                "artifact-1",
                "run-1",
                "plan-1",
                "avatar",
                "job-1",
                "project-1",
                "workspace-1",
                "PLAN",
                "summary",
                "text",
                "summary.txt",
                "outputs/summary.txt",
                null,
                Instant.parse("2026-05-22T10:00:00Z")
            );
        }
    }

    private static class StubAgentProfileService extends AgentProfileService {
        StubAgentProfileService() {
            super(null, null, null);
        }

        @Override
        public List<AgentProfile> list() {
            return List.of(new AgentProfile(
                "agent-1",
                "Research Agent",
                AgentProfileStatus.ACTIVE,
                "qwen3",
                null,
                List.of(),
                List.of(),
                false,
                Instant.parse("2026-05-22T10:00:00Z"),
                Instant.parse("2026-05-22T10:00:00Z")
            ));
        }
    }

    private static class StubJobService extends JobService {
        StubJobService() {
            super(null, null, null, null);
        }

        @Override
        public List<JobDefinition> listDefinitions() {
            return List.of();
        }
    }

    private static class StubInboxService extends InboxService {
        StubInboxService() {
            super(null, null);
        }

        @Override
        public List<InboxMessage> userInbox() {
            return List.of(new InboxMessage(
                "message-1",
                InboxMessageToType.USER,
                null,
                "agent-1",
                InboxMessageType.INFO,
                "Internal inbox message",
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-22T10:00:00Z"),
                Instant.parse("2026-05-22T10:00:00Z")
            ));
        }
    }

    private static class EmptyAssignmentProvider implements ObjectProvider<AssignmentService> {
        @Override
        public AssignmentService getObject(Object... args) {
            return null;
        }

        @Override
        public AssignmentService getIfAvailable() {
            return null;
        }

        @Override
        public AssignmentService getIfUnique() {
            return null;
        }

        @Override
        public AssignmentService getObject() {
            return null;
        }

        @Override
        public java.util.Iterator<AssignmentService> iterator() {
            return List.<AssignmentService>of().iterator();
        }

        @Override
        public java.util.stream.Stream<AssignmentService> stream() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public java.util.stream.Stream<AssignmentService> orderedStream() {
            return java.util.stream.Stream.empty();
        }
    }

    private record FixedProvider<T>(T value) implements ObjectProvider<T> {
        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return List.of(value).iterator();
        }

        @Override
        public java.util.stream.Stream<T> stream() {
            return java.util.stream.Stream.of(value);
        }

        @Override
        public java.util.stream.Stream<T> orderedStream() {
            return stream();
        }
    }
}
