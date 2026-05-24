package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import io.mindspice.magenta2.ai.chat.model.ChatSession;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkArea;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import io.mindspice.magenta2.avatar.AvatarCalendarItem;
import io.mindspice.magenta2.avatar.AvatarCalendarStatus;
import io.mindspice.magenta2.avatar.AvatarDailyTask;
import io.mindspice.magenta2.avatar.AvatarDashboardWidget;
import io.mindspice.magenta2.avatar.AvatarDashboardRowWidget;
import io.mindspice.magenta2.avatar.AvatarEvent;
import io.mindspice.magenta2.avatar.AvatarNote;
import io.mindspice.magenta2.avatar.AvatarPriority;
import io.mindspice.magenta2.avatar.AvatarService;
import io.mindspice.magenta2.avatar.AvatarTaskStatus;
import io.mindspice.magenta2.avatar.AvatarTodo;
import io.mindspice.magenta2.avatar.AvatarTodoStatus;
import io.mindspice.magenta2.avatar.PlannerRecurrence;
import io.mindspice.magenta2.avatar.PlannerRecurrenceMode;
import io.mindspice.magenta2.avatar.PlannerSubtodo;
import io.mindspice.magenta2.avatar.PlannerTask;
import io.mindspice.magenta2.avatar.PlannerTaskLink;
import io.mindspice.magenta2.avatar.PlannerTaskStatus;
import io.mindspice.simplypages.builders.BannerBuilder;
import io.mindspice.simplypages.builders.ShellBuilder;
import io.mindspice.simplypages.builders.ShellTemplate;
import io.mindspice.simplypages.builders.TopNavBuilder;
import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.core.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class AvatarDashboardController {
    private static final String AVATAR_CSS = "/css/avatar-dashboard.css?v=1";
    private static final String AVATAR_AGENT_ID = "avatar";
    private static final String DEFAULT_AVATAR_TAB = "dashboard";

    private final AvatarService avatarService;
    private final ChatService chatService;
    private final OutputArtifactService outputArtifactService;
    private final AgentProfileService agentProfileService;
    private final JobService jobService;
    private final ObjectProvider<AssignmentService> assignmentService;
    private final ObjectProvider<WorkAreaService> workAreaService;
    private final ObjectProvider<WorkAreaExplorerService> workAreaExplorerService;
    private final InboxService inboxService;
    private final ShellTemplate shell;

    public AvatarDashboardController(AvatarService avatarService,
                                     ChatService chatService,
                                     OutputArtifactService outputArtifactService,
                                     AgentProfileService agentProfileService,
                                     JobService jobService,
                                     ObjectProvider<AssignmentService> assignmentService,
                                     ObjectProvider<WorkAreaService> workAreaService,
                                     ObjectProvider<WorkAreaExplorerService> workAreaExplorerService,
                                     InboxService inboxService) {
        this.avatarService = avatarService;
        this.chatService = chatService;
        this.outputArtifactService = outputArtifactService;
        this.agentProfileService = agentProfileService;
        this.jobService = jobService;
        this.assignmentService = assignmentService;
        this.workAreaService = workAreaService;
        this.workAreaExplorerService = workAreaExplorerService;
        this.inboxService = inboxService;
        this.shell = ShellBuilder.create()
            .withPageTitle("Avatar Dashboard")
            .withCustomCss("/css/magenta.css?v=5")
            .addCustomCss(AVATAR_CSS)
            .withTopBanner(BannerBuilder.create()
                .withLayout(BannerBuilder.BannerLayout.CENTERED)
                .withTitle("Avatar")
                .withSubtitle("Personal assistant dashboard")
                .build())
            .withTopNav(TopNavBuilder.create()
                .withHtmxNavigation(false)
                .addPrimaryLink("Home", "/")
                .addPrimaryLink("Chat", "/chat")
                .addPrimaryLink("Avatar", "/avatar")
                .addPrimaryLink("Dashboard", "/dashboard")
                .build())
            .buildTemplate();
    }

    @GetMapping("/avatar")
    @ResponseBody
    public String avatar(@RequestParam(value = "tab", required = false) String tab,
                         @RequestParam(value = "edit", required = false) boolean edit) {
        AvatarTabState state = normalizeTabState(tab, edit);
        return shell.renderWithContent(AvatarDashboardComponents.page(data(), state.activeTab(), state.editMode()));
    }

    public String avatar(boolean edit) {
        return avatar(DEFAULT_AVATAR_TAB, edit);
    }

    @GetMapping("/avatar/_tab-panel")
    @ResponseBody
    public String avatarTabPanel(@RequestParam(value = "tab", required = false) String tab,
                                 @RequestParam(value = "edit", required = false) boolean edit,
                                 HttpServletResponse response) {
        return avatarTabPanelResponse(tab, edit, response);
    }

    @GetMapping("/avatar/_tab-panel/{tab}")
    @ResponseBody
    public String avatarTabPanelPath(@PathVariable String tab,
                                     @RequestParam(value = "edit", required = false) boolean edit,
                                     HttpServletResponse response) {
        return avatarTabPanelResponse(tab, edit, response);
    }

    public String avatarTabPanel(String tab, boolean edit) {
        return renderTabPanel(normalizeTabState(tab, edit));
    }

    private String avatarTabPanelResponse(String tab, boolean edit, HttpServletResponse response) {
        AvatarTabState state = normalizeTabState(tab, edit);
        response.setHeader("HX-Push-Url", avatarUrl(state));
        return renderTabPanel(state);
    }

    @GetMapping("/avatar/_widgets")
    @ResponseBody
    public String widgets(@RequestParam(value = "edit", required = false) boolean edit) {
        return AvatarDashboardComponents.widgetGrid(data(), edit).render();
    }

    @GetMapping("/avatar/_widgets/{widgetKey}")
    @ResponseBody
    public String widget(@PathVariable String widgetKey) {
        requireWidget(widgetKey);
        AvatarDashboardComponents.AvatarDashboardData data = data();
        AvatarDashboardWidget widget = data.rows().stream()
            .flatMap(row -> row.widgets().stream())
            .filter(item -> item.widgetKey().equals(widgetKey))
            .findFirst()
            .map(AvatarDashboardComponents::displayWidget)
            .orElseGet(() -> AvatarDashboardComponents.normalizedLayout(data.layout()).stream()
                .filter(item -> item.widgetId().equals(widgetKey))
                .findFirst()
                .orElseGet(() -> AvatarDashboardComponents.defaultWidget(
                    AvatarDashboardComponents.definition(widgetKey), 0
                )));
        return AvatarDashboardComponents.widget(data, widget).render();
    }

    @GetMapping("/avatar/_widgets/{widgetKey}/detail")
    @ResponseBody
    public String widgetDetail(@PathVariable String widgetKey) {
        requireWidget(widgetKey);
        return AvatarDashboardComponents.widgetDetailModal(data(), widgetKey).render();
    }

    @GetMapping("/avatar/_edit")
    @ResponseBody
    public String edit(@RequestParam(value = "close", required = false) boolean close) {
        return "";
    }

    @PostMapping("/avatar/_layout/rows")
    @ResponseBody
    public String addLayoutRow() {
        avatarService.addDashboardRow();
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @PostMapping("/avatar/_layout/rows/{rowId}/insert-after")
    @ResponseBody
    public String insertLayoutRowAfter(@PathVariable String rowId) {
        String insertedId;
        try {
            insertedId = avatarService.insertDashboardRowAfter(rowId).id();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponseWithCatalog(data(), insertedId).render();
    }

    @PostMapping("/avatar/_layout/rows/{rowId}/move")
    @ResponseBody
    public String moveLayoutRow(@PathVariable String rowId, @RequestParam String direction) {
        try {
            avatarService.moveDashboardRow(rowId, directionValue(direction));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @GetMapping("/avatar/_layout/rows/{rowId}/catalog")
    @ResponseBody
    public String widgetCatalog(@PathVariable String rowId) {
        return AvatarDashboardComponents.widgetCatalogModal(avatarService.dashboardRows(), rowId).render();
    }

    @DeleteMapping("/avatar/_layout/rows/{rowId}")
    @ResponseBody
    public String removeLayoutRow(@PathVariable String rowId) {
        try {
            avatarService.removeDashboardRow(rowId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @PostMapping("/avatar/_layout/rows/{rowId}/widgets")
    @ResponseBody
    public String addLayoutWidget(
        @PathVariable String rowId,
        @RequestParam String widgetKey,
        @RequestParam(defaultValue = "4") int columnWidth
    ) {
        requireWidget(widgetKey);
        try {
            avatarService.addDashboardWidget(rowId, widgetKey, columnWidth);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @PostMapping("/avatar/_layout/widgets/{widgetId}/move")
    @ResponseBody
    public String moveLayoutWidget(@PathVariable String widgetId, @RequestParam String direction) {
        try {
            avatarService.moveDashboardWidget(widgetId, direction);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @PutMapping("/avatar/_layout/widgets/{widgetId}/width")
    @ResponseBody
    public String resizeLayoutWidget(@PathVariable String widgetId, @RequestParam int columnWidth) {
        try {
            avatarService.resizeDashboardWidget(widgetId, columnWidth);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @GetMapping("/avatar/_layout/widgets/{widgetId}/width-picker")
    @ResponseBody
    public String widgetWidthPicker(@PathVariable String widgetId) {
        AvatarDashboardRowWidget widget = avatarService.dashboardRows().stream()
            .flatMap(row -> row.widgets().stream())
            .filter(item -> item.id().equals(widgetId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "dashboard widget not found: " + widgetId
            ));
        return AvatarDashboardComponents.widgetWidthPicker(avatarService.dashboardRows(), widget).render();
    }

    @PostMapping("/avatar/_layout/widgets/{widgetId}/width-cycle")
    @ResponseBody
    public String cycleLayoutWidgetWidth(@PathVariable String widgetId) {
        try {
            avatarService.cycleDashboardWidgetWidth(widgetId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @DeleteMapping("/avatar/_layout/widgets/{widgetId}")
    @ResponseBody
    public String removeLayoutWidget(@PathVariable String widgetId) {
        try {
            avatarService.removeDashboardWidget(widgetId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @Deprecated
    @PutMapping("/avatar/_layout")
    @ResponseBody
    public String saveLayout() {
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @PostMapping("/avatar/_todos")
    @ResponseBody
    public String createTodo(@RequestParam String title,
                             @RequestParam(value = "notes", required = false) String notes,
                             @RequestParam(value = "priority", required = false) String priority) {
        requireText(title, "todo title");
        avatarService.saveTodo(new AvatarTodo(
            null,
            title.strip(),
            notes,
            AvatarTodoStatus.OPEN,
            parsePriority(priority),
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));
        return widget("todos");
    }

    @PostMapping("/avatar/_todos/{todoId}/complete")
    @ResponseBody
    public String completeTodo(@PathVariable String todoId) {
        avatarService.completeTodo(todoId);
        return widget("todos");
    }

    @DeleteMapping("/avatar/_todos/{todoId}")
    @ResponseBody
    public String deleteTodo(@PathVariable String todoId) {
        avatarService.deleteTodo(todoId);
        return widget("todos");
    }

    @PostMapping("/avatar/_daily-tasks")
    @ResponseBody
    public String createDailyTask(@RequestParam String title,
                                  @RequestParam(value = "notes", required = false) String notes) {
        requireText(title, "daily task title");
        avatarService.saveDailyTask(new AvatarDailyTask(
            null,
            LocalDate.now(),
            title.strip(),
            notes,
            AvatarTaskStatus.PLANNED,
            avatarService.dailyTasks(LocalDate.now()).size(),
            null,
            null
        ));
        return widget("daily-tasks");
    }

    @PostMapping("/avatar/_daily-tasks/{taskId}/complete")
    @ResponseBody
    public String completeDailyTask(@PathVariable String taskId) {
        avatarService.completeDailyTask(taskId);
        return widget("daily-tasks");
    }

    @PostMapping("/avatar/_notes")
    @ResponseBody
    public String createNote(@RequestParam(value = "title", required = false) String title,
                             @RequestParam String body) {
        requireText(body, "note body");
        avatarService.appendNote(null, title, body, List.of("avatar-dashboard"));
        return widget("notes");
    }

    @PostMapping("/avatar/_calendar")
    @ResponseBody
    public String createCalendarItem(@RequestParam String title,
                                     @RequestParam(value = "startsAt", required = false) String startsAt,
                                     @RequestParam(value = "notes", required = false) String notes) {
        requireText(title, "calendar title");
        avatarService.saveCalendarItem(new AvatarCalendarItem(
            null,
            title.strip(),
            notes,
            parseDateTime(startsAt),
            null,
            ZoneId.systemDefault().getId(),
            null,
            AvatarCalendarStatus.SCHEDULED,
            null,
            null
        ));
        return widget("calendar");
    }

    @DeleteMapping("/avatar/_calendar/{calendarId}")
    @ResponseBody
    public String deleteCalendarItem(@PathVariable String calendarId) {
        avatarService.deleteCalendarItem(calendarId);
        return widget("calendar");
    }

    @GetMapping("/avatar/_organizer")
    @ResponseBody
    public String organizer(@RequestParam(value = "tab", defaultValue = "planner") String tab) {
        return AvatarDashboardComponents.organizerModal(
            normalizeOrganizerTab(tab),
            avatarService.plannerTasks(),
            plannerSubtodos(),
            avatarService.plannerCalendarProjection(null, null),
            avatarService.todos(),
            avatarService.calendarItems(),
            avatarService.notes(false)
        ).render();
    }

    @PostMapping("/avatar/_planner-tasks")
    @ResponseBody
    public String createPlannerTask(
        @RequestParam String title,
        @RequestParam(value = "notes", required = false) String notes,
        @RequestParam(value = "priority", required = false) String priority,
        @RequestParam(value = "startsAt", required = false) String startsAt,
        @RequestParam(value = "dueAt", required = false) String dueAt,
        @RequestParam(value = "recurrenceMode", required = false) String recurrenceMode,
        @RequestParam(value = "recurrenceInterval", defaultValue = "1") int recurrenceInterval,
        @RequestParam(value = "recurrenceStartDate", required = false) String recurrenceStartDate,
        @RequestParam(value = "recurrenceEndDate", required = false) String recurrenceEndDate,
        @RequestParam(value = "recurrenceTime", required = false) String recurrenceTime,
        @RequestParam(value = "recurrenceWeekday", required = false) String recurrenceWeekday,
        @RequestParam(value = "recurrenceMonthDay", required = false) Integer recurrenceMonthDay,
        @RequestParam(value = "recurrenceCron", required = false) String recurrenceCron,
        @RequestParam(value = "linkedProjectId", required = false) String linkedProjectId,
        @RequestParam(value = "linkedAssignmentId", required = false) String linkedAssignmentId,
        @RequestParam(value = "linkedJobId", required = false) String linkedJobId,
        @RequestParam(value = "linkedOutputId", required = false) String linkedOutputId
    ) {
        requireText(title, "planner task title");
        avatarService.savePlannerTask(new PlannerTask(
            null,
            title.strip(),
            notes,
            PlannerTaskStatus.PLANNED,
            parsePriority(priority),
            parseOptionalDateTime(startsAt),
            parseOptionalDateTime(dueAt),
            ZoneId.systemDefault().getId(),
            new PlannerRecurrence(
                parseRecurrenceMode(recurrenceMode),
                recurrenceInterval,
                parseOptionalDate(recurrenceStartDate),
                parseOptionalDate(recurrenceEndDate),
                parseOptionalTime(recurrenceTime),
                parseOptionalWeekday(recurrenceWeekday),
                recurrenceMonthDay,
                recurrenceCron
            ),
            new PlannerTaskLink(blankToNull(linkedProjectId), blankToNull(linkedAssignmentId),
                blankToNull(linkedJobId), blankToNull(linkedOutputId)),
            null,
            null,
            null
        ));
        return organizer("planner");
    }

    @PostMapping("/avatar/_planner-tasks/{taskId}/subtodos")
    @ResponseBody
    public String createPlannerSubtodo(@PathVariable String taskId, @RequestParam String title) {
        requireText(title, "planner subtodo title");
        avatarService.savePlannerSubtodo(new PlannerSubtodo(
            null,
            taskId,
            title.strip(),
            AvatarTodoStatus.OPEN,
            avatarService.plannerSubtodos(taskId).size(),
            null,
            null
        ));
        return organizer("planner");
    }

    @GetMapping("/avatar/_outputs/{artifactId}")
    @ResponseBody
    public String outputPreview(@PathVariable String artifactId) {
        try {
            RunOutputArtifact artifact = outputArtifactService.getArtifact(artifactId);
            String content = outputArtifactService.loadContent(artifactId, 1024 * 1024);
            return AvatarDashboardComponents.outputPreview(artifact, content).render();
        } catch (IOException | IllegalArgumentException exception) {
            return AvatarDashboardComponents.statusFragment(
                "Unable to preview output: " + exception.getMessage(),
                true
            ).render();
        }
    }

    @PostMapping("/avatar/_alerts/{eventId}/dismiss")
    @ResponseBody
    public String dismissAlert(@PathVariable String eventId) {
        requireText(eventId, "event id");
        avatarService.appendEvent(new AvatarEvent(
            null,
            "alert.dismissed",
            Map.of("eventId", eventId),
            Instant.now()
        ));
        return widget("alerts");
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/explorer")
    @ResponseBody
    public String workAreaExplorer(
        @PathVariable String workAreaId,
        @RequestParam(value = "path", defaultValue = ".") String path,
        @RequestParam(value = "selected", required = false) String selected
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, path);
            String inspectPath = StringUtils.hasText(selected) ? selected : path;
            WorkAreaExplorerService.Entry inspected = explorer.inspect(workAreaId, inspectPath);
            return WorkAreaExplorerFragments.shell(listing, inspected, selected);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Work Area unavailable", exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/explorer/list")
    @ResponseBody
    public String workAreaExplorerList(
        @PathVariable String workAreaId,
        @RequestParam(value = "path", defaultValue = ".") String path,
        @RequestParam(value = "selected", required = false) String selected
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, path);
            return WorkAreaExplorerFragments.list(listing, selected, false);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.listError(exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/inspect")
    @ResponseBody
    public String workAreaInspector(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return WorkAreaExplorerFragments.inspector(workAreaId, explorer.inspect(workAreaId, path), null, false);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/viewer")
    @ResponseBody
    public String workAreaViewer(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return WorkAreaExplorerFragments.viewer(workAreaId, explorer.preview(workAreaId, path));
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Viewer unavailable", exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/viewer/text")
    @ResponseBody
    public String workAreaTextViewer(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam(value = "tab", defaultValue = "rendered") String tab
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return WorkAreaExplorerFragments.textViewer(workAreaId, explorer.preview(workAreaId, path), tab);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Viewer unavailable", exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/preview")
    @ResponseBody
    public String workAreaPreview(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return AvatarDashboardComponents.workAreaPreview(workAreaId, explorer.preview(workAreaId, path)).render();
        } catch (IllegalArgumentException exception) {
            return AvatarDashboardComponents.statusFragment(
                "Preview unavailable: " + exception.getMessage(),
                true
            ).render();
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/edit")
    @ResponseBody
    public String workAreaTextEditor(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return WorkAreaExplorerFragments.textViewer(workAreaId, explorer.preview(workAreaId, path), "text");
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Editor unavailable", exception.getMessage());
        }
    }

    @PutMapping("/avatar/_work-areas/{workAreaId}/text")
    @ResponseBody
    public String saveWorkAreaText(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String content
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.saveText(workAreaId, path, content);
            String listPath = parentPath(path);
            return refreshedExplorerTargets(explorer, workAreaId, listPath, path, "Saved " + path);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Save failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/directories")
    @ResponseBody
    public String createWorkAreaDirectory(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String name
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        String childPath = joinPath(path, name);
        try {
            explorer.createDirectory(workAreaId, childPath);
            return refreshedExplorer(explorer, workAreaId, path);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/text")
    @ResponseBody
    public String createWorkAreaTextFile(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String name,
        @RequestParam(value = "kind", defaultValue = "text") String kind
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            WorkAreaExplorerService.Entry entry = "markdown".equalsIgnoreCase(kind)
                ? explorer.createMarkdownFile(workAreaId, path, name)
                : explorer.createTextFile(workAreaId, path, name);
            return AvatarDashboardComponents.workAreaTextEditor(workAreaId, explorer.preview(workAreaId, entry.path()), false).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    String createWorkAreaTextFile(String workAreaId, String path, String name) {
        return createWorkAreaTextFile(workAreaId, path, name, "text");
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/mark")
    @ResponseBody
    public String markNestedWorkArea(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam(value = "displayName", required = false) String displayName
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.mark(workAreaId, path, displayName);
            return refreshedExplorer(explorer, workAreaId, parentPath(path));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/avatar/_work-areas/{workAreaId}/files")
    @ResponseBody
    public String deleteWorkAreaPath(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String confirm
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.deleteRecursive(workAreaId, path, confirm);
            return refreshedExplorer(explorer, workAreaId, parentPath(path));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/modal/{action}")
    @ResponseBody
    public String workAreaActionModal(
        @PathVariable String workAreaId,
        @PathVariable String action,
        @RequestParam String path
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            WorkAreaExplorerService.DeletePreflight preflight = "delete".equals(action)
                || "delete-recursive".equals(action)
                ? explorer.deletePreflight(workAreaId, path, WorkAreaExplorerService.DeleteStep.INTENT)
                : null;
            return WorkAreaExplorerFragments.actionModal(workAreaId, action, path, preflight);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Action unavailable", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/files/delete")
    @ResponseBody
    public String deleteWorkAreaPathStep(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String step
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.delete(workAreaId, path, WorkAreaExplorerService.DeleteStep.valueOf(step));
            return refreshedExplorerTargets(explorer, workAreaId, parentPath(path), parentPath(path), "Deleted " + path);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Delete failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/files/rename")
    @ResponseBody
    public String renameWorkAreaPath(@PathVariable String workAreaId, @RequestParam String path, @RequestParam String name) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            WorkAreaExplorerService.Entry renamed = explorer.rename(workAreaId, path, name);
            return refreshedExplorerTargets(explorer, workAreaId, parentPath(renamed.path()), renamed.path(), "Renamed to " + renamed.name());
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Rename failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/files/action/{action}")
    @ResponseBody
    public String copyMoveWorkAreaPath(
        @PathVariable String workAreaId,
        @PathVariable String action,
        @RequestParam String path,
        @RequestParam String destination,
        @RequestParam(value = "name", required = false) String name
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            WorkAreaExplorerService.Entry result;
            if ("copy".equals(action)) {
                result = explorer.copy(workAreaId, path, destination, name);
            } else if ("move".equals(action)) {
                result = explorer.move(workAreaId, path, destination, name);
            } else {
                return WorkAreaExplorerFragments.modalError("File action failed", "Unknown file action: " + action);
            }
            return refreshedExplorerTargets(explorer, workAreaId, parentPath(result.path()), result.path(), action + " completed");
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("File action failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/tags")
    @ResponseBody
    public String createWorkAreaTag(
        @PathVariable String workAreaId,
        @RequestParam String label,
        @RequestParam(value = "displayName", required = false) String displayName
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.ensureTag(label, displayName);
            return WorkAreaExplorerFragments.modalMessage("Tag created", "Tag is ready to assign: " + label);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Tag failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/files/tags")
    @ResponseBody
    public String addWorkAreaTag(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String label
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.addLabel(workAreaId, path, label);
            return refreshedExplorerTargets(explorer, workAreaId, parentPath(path), path, "Tag added");
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @DeleteMapping("/avatar/_work-areas/{workAreaId}/files/tags")
    @ResponseBody
    public String removeWorkAreaTag(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String label
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.removeLabel(workAreaId, path, label);
            return refreshedExplorerTargets(explorer, workAreaId, parentPath(path), path, "Tag removed");
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/labels/note")
    @ResponseBody
    public String addWorkAreaNoteLabel(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.addLabel(workAreaId, path, "note");
            return refreshedExplorerTargets(explorer, workAreaId, parentPath(path), path, "Tag added");
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @DeleteMapping("/avatar/_work-areas/{workAreaId}/labels/note")
    @ResponseBody
    public String removeWorkAreaNoteLabel(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            explorer.removeLabel(workAreaId, path, "note");
            return refreshedExplorerTargets(explorer, workAreaId, parentPath(path), path, "Tag removed");
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    private AvatarDashboardComponents.AvatarDashboardData data() {
        List<AgentProfile> agents = safeList(agentProfileService::list);
        return new AvatarDashboardComponents.AvatarDashboardData(
            avatarService.profile(),
            avatarService.dashboardLayout(),
            avatarService.dashboardRows(),
            avatarService.dailyTasks(LocalDate.now()),
            avatarService.todos(),
            avatarService.calendarItems(),
            avatarService.notes(false),
            avatarService.events(),
            safeList(() -> outputArtifactService.query(null, null, null, 20)),
            agents,
            workAreas(agents),
            safeList(jobService::listDefinitions),
            assignments(agents),
            safeList(inboxService::userInbox),
            chatService.defaultModel()
        );
    }

    private String renderTabPanel(AvatarTabState state) {
        return AvatarDashboardComponents.tabPanelResponse(data(), state.activeTab(), state.editMode()).render();
    }

    private Component wrapTabPanel(AvatarTabState state, Component content) {
        return new Div()
            .withId("avatar-tab-panel")
            .withClass("avatar-tab-panel avatar-tab-panel-" + state.activeTab())
            .withAttribute("data-avatar-active-tab", state.activeTab())
            .withAttribute("data-avatar-edit-mode", Boolean.toString(state.editMode()))
            .withChild(content);
    }

    private Component queueTab() {
        List<WorkAssignment> queueAssignments = avatarQueueAssignments();
        AvatarDashboardComponents.AvatarDashboardData base = data();
        AvatarDashboardComponents.AvatarDashboardData queueData = copyData(
            base,
            base.outputs(),
            base.workAreas(),
            queueAssignments
        );
        return new Div()
            .withClass("avatar-tab-panel-content avatar-tab-panel-queue")
            .withChild(AvatarDashboardComponents.widget(
                queueData,
                AvatarDashboardComponents.defaultWidget(AvatarDashboardComponents.definition("system"), 0)
            ))
            .withChild(AvatarDashboardComponents.widget(
                queueData,
                AvatarDashboardComponents.defaultWidget(AvatarDashboardComponents.definition("recent-work"), 1)
            ));
    }

    private Component historyTab() {
        List<WorkAssignment> historyAssignments = avatarHistoryAssignments();
        List<ChatSession> historySessions = avatarHistorySessions();
        AvatarDashboardComponents.AvatarDashboardData base = data();
        AvatarDashboardComponents.AvatarDashboardData historyData = copyData(
            base,
            avatarOutputs(),
            base.workAreas(),
            historyAssignments
        );
        // TODO(avatar-shell-baseline): Replace this fallback summary with a dedicated Avatar history component once
        // the user-surface transcript read path is available without widening scope beyond this controller.
        return new Div()
            .withClass("avatar-tab-panel-content avatar-tab-panel-history")
            .withChild(AvatarDashboardComponents.statusFragment(
                "History fallback: "
                    + historyAssignments.size() + " retained assignments and "
                    + historySessions.size() + " chat sessions are currently visible.",
                false
            ))
            .withChild(AvatarDashboardComponents.widget(
                historyData,
                AvatarDashboardComponents.defaultWidget(AvatarDashboardComponents.definition("recent-work"), 0)
            ));
    }

    private Component profileTab() {
        AgentProfile avatarAgent = avatarAgentProfile();
        String displayName = avatarService.profile() == null || !StringUtils.hasText(avatarService.profile().displayName())
            ? "Avatar"
            : avatarService.profile().displayName().strip();
        String agentSummary = avatarAgent == null
            ? "Reserved Avatar agent profile is not currently available."
            : "Reserved agent status: " + avatarAgent.status()
                + (StringUtils.hasText(avatarAgent.defaultModel())
                    ? "; model " + avatarAgent.defaultModel()
                    : "; model unset");
        return new Div()
            .withClass("avatar-tab-panel-content avatar-tab-panel-profile")
            .withChild(AvatarDashboardComponents.statusFragment(displayName + ". " + agentSummary, false));
    }

    private Component outputsTab() {
        AvatarDashboardComponents.AvatarDashboardData base = data();
        AvatarDashboardComponents.AvatarDashboardData outputsData = copyData(
            base,
            avatarOutputs(),
            base.workAreas(),
            base.assignments()
        );
        return AvatarDashboardComponents.widget(
            outputsData,
            AvatarDashboardComponents.defaultWidget(AvatarDashboardComponents.definition("outputs"), 0)
        );
    }

    private Component workAreasTab() {
        AvatarDashboardComponents.AvatarDashboardData base = data();
        AvatarDashboardComponents.AvatarDashboardData workAreasData = copyData(
            base,
            base.outputs(),
            avatarWorkAreas(base.agents()),
            base.assignments()
        );
        return AvatarDashboardComponents.widget(
            workAreasData,
            AvatarDashboardComponents.defaultWidget(AvatarDashboardComponents.definition("files"), 0)
        );
    }

    private AvatarDashboardComponents.AvatarDashboardData copyData(
        AvatarDashboardComponents.AvatarDashboardData base,
        List<RunOutputArtifact> outputs,
        List<WorkArea> workAreas,
        List<WorkAssignment> assignments
    ) {
        return new AvatarDashboardComponents.AvatarDashboardData(
            base.profile(),
            base.layout(),
            base.rows(),
            base.dailyTasks(),
            base.todos(),
            base.calendarItems(),
            base.notes(),
            base.events(),
            outputs,
            base.agents(),
            workAreas,
            base.jobs(),
            assignments,
            base.userInbox(),
            base.defaultModel()
        );
    }

    private List<WorkAssignment> assignments(List<AgentProfile> agents) {
        AssignmentService service = assignmentService.getIfAvailable();
        if (service == null || agents == null || agents.isEmpty()) {
            return List.of();
        }
        return agents.stream()
            .flatMap(agent -> safeList(() -> service.queueAssignments(agent.id())).stream())
            .toList();
    }

    private List<WorkArea> workAreas(List<AgentProfile> agents) {
        WorkAreaService service = workAreaService.getIfAvailable();
        if (service == null || agents == null || agents.isEmpty()) {
            return List.of();
        }
        return agents.stream()
            .flatMap(agent -> safeList(() -> service.list(WorkspaceOwnerType.AGENT, agent.id(), false)).stream())
            .toList();
    }

    private List<WorkAssignment> avatarQueueAssignments() {
        AssignmentService service = assignmentService.getIfAvailable();
        if (service == null) {
            return List.of();
        }
        Map<String, WorkAssignment> byId = new LinkedHashMap<>();
        safeList(() -> service.queueAssignments(AVATAR_AGENT_ID)).forEach(assignment -> byId.put(assignment.id(), assignment));
        safeList(agentProfileService::list).stream()
            .flatMap(agent -> safeList(() -> service.queueAssignments(agent.id())).stream())
            .forEach(assignment -> byId.putIfAbsent(assignment.id(), assignment));
        return List.copyOf(byId.values());
    }

    private List<WorkAssignment> avatarHistoryAssignments() {
        AssignmentService service = assignmentService.getIfAvailable();
        if (service == null) {
            return List.of();
        }
        Map<String, WorkAssignment> byId = new LinkedHashMap<>();
        safeList(() -> service.historyAssignments(AVATAR_AGENT_ID)).forEach(assignment -> byId.put(assignment.id(), assignment));
        return List.copyOf(byId.values());
    }

    private List<ChatSession> avatarHistorySessions() {
        Map<String, ChatSession> byId = new LinkedHashMap<>();
        safeList(chatService::listSessions).forEach(session -> byId.put(session.conversationId(), session));
        safeList(() -> chatService.listAgentSessions(AVATAR_AGENT_ID)).forEach(session -> byId.put(session.conversationId(), session));
        return List.copyOf(byId.values());
    }

    private AgentProfile avatarAgentProfile() {
        try {
            return agentProfileService.get(AVATAR_AGENT_ID);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<RunOutputArtifact> avatarOutputs() {
        List<RunOutputArtifact> avatarOutputs = safeList(() -> outputArtifactService.query(
            OutputArtifactQuery.of(AVATAR_AGENT_ID, null, null, null, null, null, null, 20)
        ));
        if (!avatarOutputs.isEmpty()) {
            return avatarOutputs;
        }
        return safeList(() -> outputArtifactService.query(null, null, null, 20));
    }

    private List<WorkArea> avatarWorkAreas(List<AgentProfile> agents) {
        WorkAreaService service = workAreaService.getIfAvailable();
        if (service == null) {
            return List.of();
        }
        Map<String, WorkArea> byId = new LinkedHashMap<>();
        safeList(() -> service.list(WorkspaceOwnerType.AGENT, AVATAR_AGENT_ID, false))
            .forEach(workArea -> byId.put(workArea.id(), workArea));
        workAreas(agents).forEach(workArea -> byId.putIfAbsent(workArea.id(), workArea));
        return List.copyOf(byId.values());
    }

    private Map<String, List<PlannerSubtodo>> plannerSubtodos() {
        return avatarService.plannerTasks().stream()
            .collect(java.util.stream.Collectors.toMap(
                PlannerTask::id,
                task -> avatarService.plannerSubtodos(task.id()),
                (left, right) -> left,
                java.util.LinkedHashMap::new
            ));
    }

    private <T> List<T> safeList(ListSupplier<T> supplier) {
        try {
            List<T> result = supplier.get();
            return result == null ? List.of() : result;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private void requireWidget(String widgetKey) {
        if (!AvatarDashboardComponents.isKnownWidget(widgetKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown widget key: " + widgetKey);
        }
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
        }
    }

    private WorkAreaExplorerService requireExplorerService() {
        WorkAreaExplorerService service = workAreaExplorerService.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Work Area explorer is unavailable");
        }
        return service;
    }

    private String refreshedExplorer(WorkAreaExplorerService explorer, String workAreaId, String path) {
        WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, path);
        WorkAreaExplorerService.Entry inspected = selectedOrCurrentEntry(explorer, workAreaId, listing, path);
        return WorkAreaExplorerFragments.shell(listing, inspected, inspected == null ? null : inspected.path());
    }

    private String refreshedExplorerTargets(
        WorkAreaExplorerService explorer,
        String workAreaId,
        String listPath,
        String selectedPath,
        String message
    ) {
        WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, listPath);
        WorkAreaExplorerService.Entry inspected = explorer.inspect(workAreaId, selectedPath);
        return WorkAreaExplorerFragments.mutationResponse(listing, inspected, selectedPath, message);
    }

    private WorkAreaExplorerService.Entry selectedOrCurrentEntry(
        WorkAreaExplorerService explorer,
        String workAreaId,
        WorkAreaExplorerService.DirectoryListing listing,
        String path
    ) {
        try {
            return explorer.inspect(workAreaId, path);
        } catch (RuntimeException exception) {
            return listing.entries().isEmpty() ? null : listing.entries().getFirst();
        }
    }

    private String parentPath(String path) {
        if (!StringUtils.hasText(path) || ".".equals(path.strip())) {
            return ".";
        }
        String normalized = path.strip().replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash <= 0) {
            return ".";
        }
        return normalized.substring(0, lastSlash);
    }

    private String joinPath(String base, String name) {
        requireText(name, "directory name");
        String child = name.strip().replace('\\', '/');
        String prefix = StringUtils.hasText(base) && !".".equals(base.strip()) ? base.strip().replace('\\', '/') + "/" : "";
        return prefix + child;
    }

    private int directionValue(String direction) {
        String normalized = StringUtils.hasText(direction) ? direction.strip().toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "up" -> -1;
            case "down" -> 1;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown row direction: " + direction);
        };
    }

    private AvatarPriority parsePriority(String value) {
        if (!StringUtils.hasText(value)) {
            return AvatarPriority.NORMAL;
        }
        try {
            return AvatarPriority.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown priority: " + value);
        }
    }

    private AvatarTabState normalizeTabState(String tab, boolean edit) {
        String normalizedTab = normalizeTab(tab);
        return new AvatarTabState(normalizedTab, "dashboard".equals(normalizedTab) && edit);
    }

    private String normalizeTab(String value) {
        String normalized = StringUtils.hasText(value) ? value.strip().toLowerCase(Locale.ROOT) : DEFAULT_AVATAR_TAB;
        return switch (normalized) {
            case "dashboard", "queue", "history", "profile", "outputs", "work-areas" -> normalized;
            default -> DEFAULT_AVATAR_TAB;
        };
    }

    private String avatarUrl(AvatarTabState state) {
        StringBuilder url = new StringBuilder("/avatar?tab=").append(state.activeTab());
        if (state.editMode()) {
            url.append("&edit=true");
        }
        return url.toString();
    }

    private String normalizeOrganizerTab(String value) {
        String normalized = StringUtils.hasText(value) ? value.strip().toLowerCase(Locale.ROOT) : "planner";
        return switch (normalized) {
            case "planner", "todos", "calendar", "notes" -> normalized;
            default -> "planner";
        };
    }

    private PlannerRecurrenceMode parseRecurrenceMode(String value) {
        if (!StringUtils.hasText(value)) {
            return PlannerRecurrenceMode.NONE;
        }
        try {
            return PlannerRecurrenceMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown recurrence mode: " + value);
        }
    }

    private Instant parseOptionalDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return parseDateTime(value);
    }

    private LocalDate parseOptionalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date value.");
        }
    }

    private LocalTime parseOptionalTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time value.");
        }
    }

    private DayOfWeek parseOptionalWeekday(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid weekday value.");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private Instant parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(value.strip()).atZone(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid startsAt value.");
        }
    }

    @FunctionalInterface
    private interface ListSupplier<T> {
        List<T> get();
    }

    private record AvatarTabState(String activeTab, boolean editMode) {
    }
}
