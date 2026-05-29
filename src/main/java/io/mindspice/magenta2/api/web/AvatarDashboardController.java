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
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileLabelTargetType;
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
import io.mindspice.magenta2.avatar.CalendarScheduleView;
import io.mindspice.magenta2.avatar.PlannerOccurrence;
import io.mindspice.magenta2.avatar.PlannerRecurrence;
import io.mindspice.magenta2.avatar.PlannerRecurrenceMode;
import io.mindspice.magenta2.avatar.PlannerReminder;
import io.mindspice.magenta2.avatar.PlannerSubtodo;
import io.mindspice.magenta2.avatar.PlannerTask;
import io.mindspice.magenta2.avatar.PlannerTaskLink;
import io.mindspice.magenta2.avatar.PlannerTaskStatus;
import io.mindspice.magenta2.avatar.PlannerTimeBlock;
import io.mindspice.magenta2.avatar.TasksRoutinesView;
import io.mindspice.magenta2.avatar.TodayPlannerView;
import io.mindspice.magenta2.avatar.dashboard.WidgetSettingsValidation;
import io.mindspice.simplypages.builders.BannerBuilder;
import io.mindspice.simplypages.builders.ShellBuilder;
import io.mindspice.simplypages.builders.ShellTemplate;
import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.core.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.util.MultiValueMap;
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
    private static final String AVATAR_CSS = "/css/avatar-dashboard.css?v=9";
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
            .withPageTitle("Assistant Dashboard")
            .withCustomCss("/css/magenta.css?v=5")
            .addCustomCss(AVATAR_CSS)
            .withContentTargetClass("avatar-content-area")
            .withTopBanner(BannerBuilder.create()
                .withLayout(BannerBuilder.BannerLayout.CENTERED)
                .withTitle("Assistant")
                .withSubtitle("User dashboards, chat, and operational widgets")
                .build())
            .withTopNav(AppNavigation.primaryTopNav())
            .buildTemplate();
    }

    @GetMapping("/")
    @ResponseBody
    public String home(@RequestParam(value = "edit", required = false) boolean edit) {
        return dashboard(avatarService.assistantDashboard().id(), edit);
    }

    @GetMapping("/dashboards/{dashboardId}")
    @ResponseBody
    public String dashboard(@PathVariable String dashboardId,
                            @RequestParam(value = "edit", required = false) boolean edit) {
        return shell.renderWithContent(AvatarDashboardComponents.page(data(dashboardId), "dashboard", edit));
    }

    @GetMapping("/dashboards/{dashboardId}/_page")
    @ResponseBody
    public String dashboardPageFragment(@PathVariable String dashboardId,
                                        @RequestParam(value = "edit", required = false) boolean edit,
                                        HttpServletResponse response) {
        response.setHeader("HX-Push-Url", edit ? "/dashboards/" + dashboardId + "?edit=true" : "/dashboards/" + dashboardId);
        return AvatarDashboardComponents.pageFragment(data(dashboardId), edit).render();
    }

    public String avatar(@RequestParam(value = "tab", required = false) String tab,
                         @RequestParam(value = "edit", required = false) boolean edit) {
        return home(edit);
    }

    public String avatar(boolean edit) {
        return home(edit);
    }

    public String avatarTabPanel(@RequestParam(value = "tab", required = false) String tab,
                                 @RequestParam(value = "edit", required = false) boolean edit,
                                 HttpServletResponse response) {
        return avatarTabPanelResponse(tab, edit, response);
    }

    public String avatarTabPanelPath(@PathVariable String tab,
                                     @RequestParam(value = "edit", required = false) boolean edit,
                                     HttpServletResponse response) {
        return avatarTabPanelResponse(tab, edit, response);
    }

    public String avatarTabPanel(String tab, boolean edit) {
        return renderTabPanel(normalizeTabState(tab, edit));
    }

    private String avatarTabPanelResponse(String tab, boolean edit, HttpServletResponse response) {
        AssistantTabState state = normalizeTabState(tab, edit);
        response.setHeader("HX-Push-Url", avatarUrl(state));
        return renderTabPanel(state);
    }

    @GetMapping("/_dashboards/_widgets")
    @ResponseBody
    public String widgets(@RequestParam(value = "edit", required = false) boolean edit) {
        return AvatarDashboardComponents.widgetGrid(data(), edit).render();
    }

    @GetMapping("/_dashboards/_widgets/{widgetKey}")
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
                .filter(item -> widgetKey.equals(item.settings().get("widgetType")) || item.widgetId().equals(widgetKey))
                .findFirst()
                .orElseGet(() -> AvatarDashboardComponents.defaultWidget(
                    AvatarDashboardComponents.definition(widgetKey), 0
                )));
        return AvatarDashboardComponents.widget(data, widget).render();
    }

    @GetMapping("/dashboards/{dashboardId}/widgets/{widgetInstanceId}")
    @ResponseBody
    public String widgetByInstance(@PathVariable String dashboardId, @PathVariable String widgetInstanceId) {
        AvatarDashboardRowWidget rowWidget = requireDashboardWidget(dashboardId, widgetInstanceId);
        return AvatarDashboardComponents.widget(data(dashboardId), AvatarDashboardComponents.displayWidget(rowWidget)).render();
    }

    @GetMapping("/_dashboards/_widgets/{widgetKey}/detail")
    @ResponseBody
    public String widgetDetail(
        @PathVariable String widgetKey,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "range", required = false) String range,
        @RequestParam(value = "recurrence", required = false) String recurrence
    ) {
        requireWidget(widgetKey);
        AvatarDashboardComponents.AvatarDashboardData data = widgetDetailData(widgetKey, status, range, recurrence);
        AvatarDashboardWidget widget = data.rows().stream()
            .flatMap(row -> row.widgets().stream())
            .filter(item -> item.widgetKey().equals(widgetKey))
            .findFirst()
            .map(AvatarDashboardComponents::displayWidget)
            .orElseGet(() -> AvatarDashboardComponents.defaultWidget(AvatarDashboardComponents.definition(widgetKey), 0));
        return AvatarDashboardComponents.widgetDetailModal(data, widget).render();
    }

    public String widgetDetail(String widgetKey) {
        return widgetDetail(widgetKey, null, null, null);
    }

    @GetMapping("/dashboards/{dashboardId}/widgets/{widgetInstanceId}/detail")
    @ResponseBody
    public String widgetDetailByInstance(
        @PathVariable String dashboardId,
        @PathVariable String widgetInstanceId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "range", required = false) String range,
        @RequestParam(value = "recurrence", required = false) String recurrence
    ) {
        AvatarDashboardRowWidget rowWidget = requireDashboardWidget(dashboardId, widgetInstanceId);
        return AvatarDashboardComponents.widgetDetailModal(
            widgetDetailData(dashboardId, rowWidget.widgetKey(), status, range, recurrence),
            AvatarDashboardComponents.displayWidget(rowWidget)
        ).render();
    }

    public String widgetDetailByInstance(String dashboardId, String widgetInstanceId) {
        return widgetDetailByInstance(dashboardId, widgetInstanceId, null, null, null);
    }

    @GetMapping("/dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings")
    @ResponseBody
    public String widgetSettings(@PathVariable String dashboardId, @PathVariable String widgetInstanceId) {
        AvatarDashboardRowWidget rowWidget = requireDashboardWidget(dashboardId, widgetInstanceId);
        return AvatarDashboardComponents.widgetSettingsModal(data(dashboardId), rowWidget, null).render();
    }

    @GetMapping("/_dashboards/_layout/widgets/{widgetInstanceId}/settings")
    @ResponseBody
    public String widgetSettingsCompatibility(@PathVariable String widgetInstanceId) {
        String dashboardId = avatarService.dashboardIdForWidget(widgetInstanceId);
        return widgetSettings(dashboardId, widgetInstanceId);
    }

    @PutMapping("/dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings")
    @ResponseBody
    public String saveWidgetSettings(
        @PathVariable String dashboardId,
        @PathVariable String widgetInstanceId,
        @RequestParam MultiValueMap<String, String> params,
        HttpServletResponse response
    ) {
        AvatarDashboardRowWidget rowWidget = requireDashboardWidget(dashboardId, widgetInstanceId);
        Map<String, String> settings = firstValues(params);
        WidgetSettingsValidation validation = avatarService.validateDashboardWidgetSettings(rowWidget.widgetKey(), settings);
        if (!validation.valid()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return AvatarDashboardComponents.widgetSettingsModal(data(dashboardId), rowWidget, validation).render();
        }
        AvatarDashboardRowWidget saved = avatarService.updateDashboardWidgetSettings(dashboardId, widgetInstanceId, settings);
        return AvatarDashboardComponents.widgetSettingsSaveResponse(data(dashboardId), saved).render();
    }

    @GetMapping("/_dashboards/_edit")
    @ResponseBody
    public String edit(@RequestParam(value = "close", required = false) boolean close) {
        return "";
    }

    @GetMapping("/avatar/_work-areas/modal/clear")
    @ResponseBody
    public String clearWorkAreaModal() {
        return WorkAreaExplorerFragments.emptyModalHost();
    }

    @GetMapping("/dashboards/_create")
    @ResponseBody
    public String createDashboardModal() {
        return AvatarDashboardComponents.createDashboardModal(null, null).render();
    }

    @GetMapping("/dashboards/_modal/clear")
    @ResponseBody
    public String clearDashboardModal() {
        return "";
    }

    @PostMapping("/dashboards")
    @ResponseBody
    public String createDashboard(@RequestParam String name, HttpServletResponse response) {
        try {
            var dashboard = avatarService.createDashboard(name);
            response.setHeader("HX-Push-Url", "/dashboards/" + dashboard.id());
            return shell.renderWithContent(AvatarDashboardComponents.page(data(dashboard.id()), "dashboard", false));
        } catch (IllegalArgumentException exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return AvatarDashboardComponents.createDashboardModal(name, exception.getMessage()).render();
        }
    }

    @PostMapping("/_dashboards/_layout/rows")
    @ResponseBody
    public String addLayoutRow() {
        avatarService.addDashboardRow();
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @PostMapping("/dashboards/{dashboardId}/_layout/rows")
    @ResponseBody
    public String addLayoutRow(@PathVariable String dashboardId) {
        avatarService.addDashboardRow(dashboardId);
        return AvatarDashboardComponents.layoutEditResponse(data(dashboardId), true).render();
    }

    @PostMapping("/_dashboards/_layout/rows/{rowId}/insert-after")
    @ResponseBody
    public String insertLayoutRowAfter(@PathVariable String rowId) {
        String insertedId;
        try {
            insertedId = avatarService.insertDashboardRowAfter(rowId).id();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponseWithCatalog(data(avatarService.dashboardIdForRow(insertedId)), insertedId).render();
    }

    @PostMapping("/_dashboards/_layout/rows/{rowId}/move")
    @ResponseBody
    public String moveLayoutRow(@PathVariable String rowId, @RequestParam String direction) {
        try {
            avatarService.moveDashboardRow(rowId, directionValue(direction));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(avatarService.dashboardIdForRow(rowId)), true).render();
    }

    @GetMapping("/_dashboards/_layout/rows/{rowId}/catalog")
    @ResponseBody
    public String widgetCatalog(@PathVariable String rowId) {
        return AvatarDashboardComponents.widgetCatalogModal(
            avatarService.dashboardRows(avatarService.dashboardIdForRow(rowId)),
            rowId
        ).render();
    }

    @DeleteMapping("/_dashboards/_layout/rows/{rowId}")
    @ResponseBody
    public String removeLayoutRow(@PathVariable String rowId) {
        String dashboardId = avatarService.dashboardIdForRow(rowId);
        try {
            avatarService.removeDashboardRow(rowId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(dashboardId), true).render();
    }

    @PostMapping("/_dashboards/_layout/rows/{rowId}/widgets")
    @ResponseBody
    public String addLayoutWidget(
        @PathVariable String rowId,
        @RequestParam String widgetKey,
        @RequestParam(defaultValue = "4") int columnWidth,
        HttpServletResponse response
    ) {
        requireWidget(widgetKey);
        try {
            avatarService.addDashboardWidget(rowId, widgetKey, columnWidth);
        } catch (IllegalArgumentException exception) {
            if (response != null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            String dashboardId = avatarService.dashboardIdForRow(rowId);
            return AvatarDashboardComponents.widgetCatalogModal(
                avatarService.dashboardRows(dashboardId),
                rowId,
                exception.getMessage()
            ).render();
        }
        return AvatarDashboardComponents.layoutEditResponse(data(avatarService.dashboardIdForRow(rowId)), true).render();
    }

    public String addLayoutWidget(String rowId, String widgetKey, int columnWidth) {
        return addLayoutWidget(rowId, widgetKey, columnWidth, null);
    }

    @PostMapping("/_dashboards/_layout/widgets/{widgetId}/move")
    @ResponseBody
    public String moveLayoutWidget(@PathVariable String widgetId, @RequestParam String direction) {
        try {
            avatarService.moveDashboardWidget(widgetId, direction);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(avatarService.dashboardIdForWidget(widgetId)), true).render();
    }

    @PutMapping("/_dashboards/_layout/widgets/{widgetId}/width")
    @ResponseBody
    public String resizeLayoutWidget(@PathVariable String widgetId, @RequestParam int columnWidth) {
        try {
            avatarService.resizeDashboardWidget(widgetId, columnWidth);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(avatarService.dashboardIdForWidget(widgetId)), true).render();
    }

    @GetMapping("/_dashboards/_layout/widgets/{widgetId}/width-picker")
    @ResponseBody
    public String widgetWidthPicker(@PathVariable String widgetId) {
        String dashboardId = avatarService.dashboardIdForWidget(widgetId);
        AvatarDashboardRowWidget widget = avatarService.dashboardRows(dashboardId).stream()
            .flatMap(row -> row.widgets().stream())
            .filter(item -> item.id().equals(widgetId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "dashboard widget not found: " + widgetId
            ));
        return AvatarDashboardComponents.widgetWidthPicker(avatarService.dashboardRows(dashboardId), widget).render();
    }

    @PostMapping("/_dashboards/_layout/widgets/{widgetId}/width-cycle")
    @ResponseBody
    public String cycleLayoutWidgetWidth(@PathVariable String widgetId) {
        try {
            avatarService.cycleDashboardWidgetWidth(widgetId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(avatarService.dashboardIdForWidget(widgetId)), true).render();
    }

    @DeleteMapping("/_dashboards/_layout/widgets/{widgetId}")
    @ResponseBody
    public String removeLayoutWidget(@PathVariable String widgetId) {
        String dashboardId = avatarService.dashboardIdForWidget(widgetId);
        try {
            avatarService.removeDashboardWidget(widgetId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data(dashboardId), true).render();
    }

    @Deprecated
    @PutMapping("/_dashboards/_layout")
    @ResponseBody
    public String saveLayout() {
        return AvatarDashboardComponents.layoutEditResponse(data(), true).render();
    }

    @PostMapping("/_dashboards/_todos")
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

    @PostMapping("/_dashboards/_todos/{todoId}/complete")
    @ResponseBody
    public String completeTodo(@PathVariable String todoId) {
        avatarService.completeTodo(todoId);
        return widget("todos");
    }

    @DeleteMapping("/_dashboards/_todos/{todoId}")
    @ResponseBody
    public String deleteTodo(@PathVariable String todoId) {
        avatarService.deleteTodo(todoId);
        return widget("todos");
    }

    @PostMapping("/_dashboards/_daily-tasks")
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

    @PostMapping("/_dashboards/_daily-tasks/{taskId}/complete")
    @ResponseBody
    public String completeDailyTask(@PathVariable String taskId) {
        avatarService.completeDailyTask(taskId);
        return widget("daily-tasks");
    }

    @PostMapping("/_dashboards/_notes")
    @ResponseBody
    public String createNote(@RequestParam(value = "title", required = false) String title,
                             @RequestParam String body) {
        appendDashboardNote(title, body);
        return widget("notes");
    }

    @PostMapping("/dashboards/{dashboardId}/widgets/{widgetInstanceId}/_notes")
    @ResponseBody
    public String createNoteForWidget(@PathVariable String dashboardId,
                                      @PathVariable String widgetInstanceId,
                                      @RequestParam(value = "title", required = false) String title,
                                      @RequestParam String body) {
        AvatarDashboardRowWidget rowWidget = requireDashboardWidget(dashboardId, widgetInstanceId);
        if (!"notes".equals(rowWidget.widgetKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dashboard widget is not notes: " + widgetInstanceId);
        }
        appendDashboardNote(title, body);
        return widgetByInstance(dashboardId, widgetInstanceId);
    }

    private void appendDashboardNote(String title, String body) {
        requireText(body, "note body");
        avatarService.appendNote(null, title, body, List.of("avatar-dashboard"));
    }

    @PostMapping("/_dashboards/_calendar")
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

    @DeleteMapping("/_dashboards/_calendar/{calendarId}")
    @ResponseBody
    public String deleteCalendarItem(@PathVariable String calendarId) {
        avatarService.deleteCalendarItem(calendarId);
        return widget("calendar");
    }

    @GetMapping("/_dashboards/_organizer")
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

    @PostMapping("/_dashboards/_planner-tasks")
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

    @PostMapping("/_dashboards/_planner-tasks/{taskId}/subtodos")
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

    @PostMapping("/_dashboards/_today/quick-capture")
    @ResponseBody
    public String quickCapturePlannerTask(@RequestParam String title,
                                          @RequestParam(value = "notes", required = false) String notes) {
        avatarService.quickCapture(title, notes);
        return firstWidgetByType("today-planner");
    }

    @PostMapping("/_dashboards/_today/restart")
    @ResponseBody
    public String restartTodayPlanner() {
        avatarService.restartDay(LocalDate.now());
        return firstWidgetByType("today-planner");
    }

    @PostMapping("/_dashboards/_today/review")
    @ResponseBody
    public String reviewTodayPlanner(@RequestParam(value = "reviewNotes", required = false) String reviewNotes) {
        avatarService.reviewDay(LocalDate.now(), reviewNotes);
        return firstWidgetByType("today-planner");
    }

    @PostMapping("/_dashboards/_planner-tasks/{taskId}/occurrences")
    @ResponseBody
    public String updatePlannerOccurrence(@PathVariable String taskId,
                                          @RequestParam String occurrenceStart,
                                          @RequestParam String action,
                                          @RequestParam(value = "snoozedUntil", required = false) String snoozedUntil) {
        avatarService.updateOccurrence(taskId, Instant.parse(occurrenceStart), action, parseOptionalDateTime(snoozedUntil));
        return firstWidgetByType("tasks-routines");
    }

    @PostMapping("/_dashboards/_time-blocks")
    @ResponseBody
    public String createTimeBlock(@RequestParam String title,
                                  @RequestParam String startsAt,
                                  @RequestParam(value = "endsAt", required = false) String endsAt,
                                  @RequestParam(value = "sourceType", required = false) String sourceType,
                                  @RequestParam(value = "sourceId", required = false) String sourceId) {
        Instant start = parseDateTime(startsAt);
        avatarService.saveTimeBlock(new PlannerTimeBlock(
            null,
            start.atZone(ZoneId.systemDefault()).toLocalDate(),
            title.strip(),
            start,
            parseOptionalDateTime(endsAt),
            blankToNull(sourceType),
            blankToNull(sourceId),
            "PLANNED",
            null,
            null
        ));
        return firstWidgetByType("calendar-schedule");
    }

    @PostMapping("/_dashboards/_reminders")
    @ResponseBody
    public String createReminder(@RequestParam String title,
                                 @RequestParam String remindAt,
                                 @RequestParam(value = "notes", required = false) String notes,
                                 @RequestParam(value = "sourceType", required = false) String sourceType,
                                 @RequestParam(value = "sourceId", required = false) String sourceId) {
        avatarService.saveReminder(new PlannerReminder(
            null,
            title.strip(),
            notes,
            parseDateTime(remindAt),
            "OPEN",
            blankToNull(sourceType),
            blankToNull(sourceId),
            null,
            null,
            null
        ));
        return firstWidgetByType("calendar-schedule");
    }

    @GetMapping("/_dashboards/_outputs/{artifactId}")
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

    @PostMapping("/_dashboards/_alerts/{eventId}/dismiss")
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
        @RequestParam(value = "selected", required = false) String selected,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, path);
            String inspectPath = StringUtils.hasText(selected) ? selected : path;
            WorkAreaExplorerService.Entry inspected = explorer.inspect(workAreaId, inspectPath);
            return WorkAreaExplorerFragments.shell(
                listing,
                inspected,
                previewForInspector(explorer, workAreaId, inspected),
                selected,
                panelState(panel)
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Work Area unavailable", exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/placeholder")
    @ResponseBody
    public String workAreaPlaceholder() {
        return AvatarDashboardComponents.workAreaSurfacePlaceholder().render();
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/explorer/list")
    @ResponseBody
    public String workAreaExplorerList(
        @PathVariable String workAreaId,
        @RequestParam(value = "path", defaultValue = ".") String path,
        @RequestParam(value = "selected", required = false) String selected,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, path);
            return WorkAreaExplorerFragments.list(
                listing,
                selected,
                WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState(panel)),
                false
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.listError(exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/inspect")
    @ResponseBody
    public String workAreaInspector(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam(value = "listPath", defaultValue = ".") String listPath,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            boolean collapsed = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState(panel));
            return WorkAreaExplorerFragments.inspector(
                workAreaId,
                listPath,
                explorer.inspect(workAreaId, path),
                previewForInspector(explorer, workAreaId, path),
                null,
                collapsed,
                collapsed
                    ? WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
                    : WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_COLLAPSED,
                false
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/viewer")
    @ResponseBody
    public String workAreaViewer(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
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
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            return WorkAreaExplorerFragments.textViewer(workAreaId, explorer.preview(workAreaId, path), tab);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Viewer unavailable", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/viewer/markdown-preview")
    @ResponseBody
    public String workAreaMarkdownPreview(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam(required = false) String content
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            WorkAreaExplorerService.FilePreview preview = explorer.preview(workAreaId, path);
            if (!preview.text() || !"markdown".equals(preview.kind())) {
                return "<div class=\"avatar-status-error\">Preview unavailable for this file.</div>";
            }
            return WorkAreaExplorerFragments.markdownPreview(content);
        } catch (IllegalArgumentException exception) {
            return "<div class=\"avatar-status-error\">Preview unavailable for this file.</div>";
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/preview")
    @ResponseBody
    public String workAreaPreview(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
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
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
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
        @RequestParam String content,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.saveText(workAreaId, path, content);
            String listPath = parentPath(path);
            WorkAreaExplorerService.FilePreview preview = explorer.preview(workAreaId, path);
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, listPath);
            WorkAreaExplorerService.Entry inspected = explorer.inspect(workAreaId, path);
            return WorkAreaExplorerFragments.textSaveResponse(
                workAreaId,
                preview,
                listing,
                inspected,
                previewForInspector(explorer, workAreaId, inspected),
                path,
                panelState(panel),
                "Saved " + path
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Save failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/directories")
    @ResponseBody
    public String createWorkAreaDirectory(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String name,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        String childPath = joinPath(path, name);
        try {
            WorkAreaExplorerService.Entry entry = explorer.createDirectory(workAreaId, childPath);
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                path,
                entry.path(),
                panelState(panel),
                "Created " + entry.name()
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Create folder failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/text")
    @ResponseBody
    public String createWorkAreaTextFile(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String name,
        @RequestParam(value = "kind", defaultValue = "text") String kind,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            WorkAreaExplorerService.Entry entry = "markdown".equalsIgnoreCase(kind)
                ? explorer.createMarkdownFile(workAreaId, path, name)
                : explorer.createTextFile(workAreaId, path, name);
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, path);
            WorkAreaExplorerService.Entry inspected = explorer.inspect(workAreaId, entry.path());
            return WorkAreaExplorerFragments.textCreateResponse(
                workAreaId,
                explorer.preview(workAreaId, entry.path()),
                listing,
                inspected,
                previewForInspector(explorer, workAreaId, inspected),
                entry.path(),
                panelState(panel),
                "Created " + entry.name()
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Create file failed", exception.getMessage());
        }
    }

    String createWorkAreaTextFile(String workAreaId, String path, String name) {
        return createWorkAreaTextFile(
            workAreaId,
            path,
            name,
            "text",
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/mark")
    @ResponseBody
    public String markNestedWorkArea(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam(value = "displayName", required = false) String displayName,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.mark(workAreaId, path, displayName);
            return refreshedExplorer(explorer, workAreaId, parentPath(path), panelState(panel));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/avatar/_work-areas/{workAreaId}/files")
    @ResponseBody
    public String deleteWorkAreaPath(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String confirm,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.deleteRecursive(workAreaId, path, confirm);
            return refreshedExplorer(explorer, workAreaId, parentPath(path), panelState(panel));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/modal/{action}")
    @ResponseBody
    public String workAreaActionModal(
        @PathVariable String workAreaId,
        @PathVariable String action,
        @RequestParam String path,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            if ("tag".equals(action)) {
                return renderTagEditorModal(explorer, workAreaId, path, panelState(panel), null);
            }
            WorkAreaExplorerService.DeletePreflight preflight = "delete".equals(action)
                || "delete-recursive".equals(action)
                ? explorer.deletePreflight(workAreaId, path, WorkAreaExplorerService.DeleteStep.INTENT)
                : null;
            return WorkAreaExplorerFragments.actionModal(workAreaId, action, path, panelState(panel), preflight);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Action unavailable", exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/files/action/{action}/picker")
    @ResponseBody
    public String copyMovePicker(
        @PathVariable String workAreaId,
        @PathVariable String action,
        @RequestParam String path,
        @RequestParam(value = "browse", defaultValue = ".") String browse,
        @RequestParam(value = "destination", defaultValue = ".") String destination,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel,
        @RequestParam(value = "x", defaultValue = "48") int x,
        @RequestParam(value = "y", defaultValue = "96") int y
    ) {
        if (!"copy".equals(action) && !"move".equals(action)) {
            return WorkAreaExplorerFragments.modalError("File action failed", "Unknown file action: " + action);
        }
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.inspect(workAreaId, path);
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, browse);
            explorer.inspect(workAreaId, destination);
            return WorkAreaExplorerFragments.copyMovePicker(
                workAreaId,
                action,
                path,
                listing.path(),
                destination,
                panelState(panel),
                x,
                y
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("File action failed", exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/files/directories")
    @ResponseBody
    public String directoryPickerOptions(
        @PathVariable String workAreaId,
        @RequestParam(value = "path", defaultValue = ".") String browse,
        @RequestParam String source,
        @RequestParam String action,
        @RequestParam(value = "destination", defaultValue = ".") String destination,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel,
        @RequestParam(value = "x", defaultValue = "48") int x,
        @RequestParam(value = "y", defaultValue = "96") int y
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, browse);
            return WorkAreaExplorerFragments.directoryPickerOptions(
                listing,
                action,
                source,
                destination,
                panelState(panel),
                x,
                y
            );
        } catch (IllegalArgumentException exception) {
            return "<div class=\"avatar-status-error\">"
                + org.springframework.web.util.HtmlUtils.htmlEscape(exception.getMessage())
                + "</div>";
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/files/delete")
    @ResponseBody
    public String deleteWorkAreaPathStep(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String step,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.delete(workAreaId, path, WorkAreaExplorerService.DeleteStep.valueOf(step));
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(path),
                parentPath(path),
                panelState(panel),
                "Deleted " + path
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Delete failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/files/rename")
    @ResponseBody
    public String renameWorkAreaPath(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String name,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            WorkAreaExplorerService.Entry renamed = explorer.rename(workAreaId, path, name);
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(renamed.path()),
                renamed.path(),
                panelState(panel),
                "Renamed to " + renamed.name()
            );
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
        @RequestParam(value = "name", required = false) String name,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            String effectiveDestination = effectiveOperationDestination(explorer, workAreaId, path, destination);
            WorkAreaExplorerService.Entry result;
            if ("copy".equals(action)) {
                result = explorer.copy(workAreaId, path, effectiveDestination, name);
            } else if ("move".equals(action)) {
                result = explorer.move(workAreaId, path, effectiveDestination, name);
            } else {
                return WorkAreaExplorerFragments.modalError("File action failed", "Unknown file action: " + action);
            }
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(result.path()),
                result.path(),
                panelState(panel),
                action + " completed"
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("File action failed", exception.getMessage());
        }
    }

    private String effectiveOperationDestination(
        WorkAreaExplorerService explorer,
        String workAreaId,
        String sourcePath,
        String destination
    ) {
        if (!StringUtils.hasText(destination)) {
            throw new IllegalArgumentException("destination directory is required");
        }
        String cleaned = destination.strip().replace('\\', '/');
        if (destinationDirectoryExists(explorer, workAreaId, cleaned)) {
            return cleaned;
        }
        if (!cleaned.contains("/") && !".".equals(cleaned)) {
            String siblingDestination = joinPath(parentPath(sourcePath), cleaned);
            if (destinationDirectoryExists(explorer, workAreaId, siblingDestination)) {
                return siblingDestination;
            }
        }
        return cleaned;
    }

    private boolean destinationDirectoryExists(WorkAreaExplorerService explorer, String workAreaId, String destination) {
        try {
            return explorer.inspect(workAreaId, destination).directory();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/tags")
    @ResponseBody
    public String createWorkAreaTag(
        @PathVariable String workAreaId,
        @RequestParam String label,
        @RequestParam(value = "targetType", required = false) String targetType,
        @RequestParam(value = "displayName", required = false) String displayName,
        @RequestParam(value = "description", required = false) String description
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            if (StringUtils.hasText(targetType)) {
                explorer.ensureTag(
                    label,
                    displayName,
                    WorkspaceFileLabelTargetType.fromWireName(targetType),
                    description
                );
            } else {
                explorer.ensureTag(label, displayName);
            }
            return WorkAreaExplorerFragments.modalMessage("Tag created", "Tag is ready to assign: " + label);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Tag failed", exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/modal/tag-editor")
    @ResponseBody
    public String workAreaTagEditorModal(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam(value = "panel", defaultValue = WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED) String panel
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            return renderTagEditorModal(explorer, workAreaId, path, panelState(panel), null);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Tag editor unavailable", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/modal/tag-editor/tags")
    @ResponseBody
    public String createWorkAreaTagFromEditor(
        @PathVariable String workAreaId,
        @RequestParam MultiValueMap<String, String> params
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            String path = requiredSingleValue(params, "path");
            String panel = optionalSingleValue(params, "panel");
            String label = requiredSingleValue(params, "label");
            String displayName = optionalSingleValue(params, "displayName");
            String description = optionalSingleValue(params, "description");
            String targetType = optionalSingleValue(params, "targetType");
            WorkspaceFileLabelTargetType normalizedTargetType = StringUtils.hasText(targetType)
                ? WorkspaceFileLabelTargetType.fromWireName(targetType)
                : WorkspaceFileLabelTargetType.fromWireName(inferPathTargetType(explorer, workAreaId, path));
            explorer.ensureTag(label, displayName, normalizedTargetType, description);
            return renderTagEditorModal(
                explorer,
                workAreaId,
                path,
                panelState(panel),
                "Tag created: " + label
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.modalError("Tag failed", exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/modal/tag-editor/assign")
    @ResponseBody
    public String assignWorkAreaTagFromEditor(
        @PathVariable String workAreaId,
        @RequestParam MultiValueMap<String, String> params
    ) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            String path = requiredSingleValue(params, "path");
            String label = requiredSingleValue(params, "label");
            String panel = optionalSingleValue(params, "panel");
            explorer.addLabel(workAreaId, path, label);
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(path),
                path,
                panelState(panel),
                "Tag added"
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/files/tags")
    @ResponseBody
    public String addWorkAreaTag(
        @PathVariable String workAreaId,
        @RequestParam MultiValueMap<String, String> params
    ) {
        try {
            String path = requiredSingleValue(params, "path");
            String label = requiredSingleValue(params, "label");
            String targetType = optionalSingleValue(params, "targetType");
            String panel = optionalSingleValue(params, "panel");
            return addWorkAreaTag(workAreaId, path, label, targetType, panel);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/tags/options")
    @ResponseBody
    public String workAreaTagOptions(
        @PathVariable String workAreaId,
        @RequestParam MultiValueMap<String, String> params
    ) {
        try {
            String path = requiredSingleValue(params, "path");
            String label = optionalSingleValue(params, "label");
            WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
            return WorkAreaExplorerFragments.tagOptions(
                workAreaId,
                path,
                label,
                explorer.availableTags(workAreaId, path, label, 12)
            );
        } catch (IllegalArgumentException exception) {
            return "<div class=\"entity-selector-empty\">Unable to load tags: " + exception.getMessage() + "</div>";
        }
    }

    @DeleteMapping("/avatar/_work-areas/{workAreaId}/files/tags")
    @ResponseBody
    public String removeWorkAreaTag(
        @PathVariable String workAreaId,
        @RequestParam MultiValueMap<String, String> params
    ) {
        try {
            String path = requiredSingleValue(params, "path");
            String label = requiredSingleValue(params, "label");
            String panel = optionalSingleValue(params, "panel");
            return removeWorkAreaTag(workAreaId, path, label, panel);
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    String workAreaExplorer(String workAreaId, String path, String selected) {
        return workAreaExplorer(
            workAreaId,
            path,
            selected,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String workAreaExplorerList(String workAreaId, String path, String selected) {
        return workAreaExplorerList(
            workAreaId,
            path,
            selected,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String workAreaInspector(String workAreaId, String path) {
        return workAreaInspector(
            workAreaId,
            path,
            parentPath(path),
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String saveWorkAreaText(String workAreaId, String path, String content) {
        return saveWorkAreaText(
            workAreaId,
            path,
            content,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String createWorkAreaDirectory(String workAreaId, String path, String name) {
        return createWorkAreaDirectory(
            workAreaId,
            path,
            name,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String createWorkAreaTextFile(String workAreaId, String path, String name, String kind) {
        return createWorkAreaTextFile(
            workAreaId,
            path,
            name,
            kind,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String markNestedWorkArea(String workAreaId, String path, String displayName) {
        return markNestedWorkArea(
            workAreaId,
            path,
            displayName,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String workAreaActionModal(String workAreaId, String action, String path) {
        return workAreaActionModal(
            workAreaId,
            action,
            path,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String deleteWorkAreaPathStep(String workAreaId, String path, String step) {
        return deleteWorkAreaPathStep(
            workAreaId,
            path,
            step,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String renameWorkAreaPath(String workAreaId, String path, String name) {
        return renameWorkAreaPath(
            workAreaId,
            path,
            name,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String copyMoveWorkAreaPath(String workAreaId, String action, String path, String destination, String name) {
        return copyMoveWorkAreaPath(
            workAreaId,
            action,
            path,
            destination,
            name,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String createWorkAreaTag(String workAreaId, String label, String displayName) {
        return createWorkAreaTag(workAreaId, label, null, displayName, null);
    }

    String addWorkAreaTag(String workAreaId, String path, String label) {
        return addWorkAreaTag(workAreaId, path, label, null);
    }

    String addWorkAreaTag(String workAreaId, String path, String label, String targetType) {
        return addWorkAreaTag(
            workAreaId,
            path,
            label,
            targetType,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String addWorkAreaTag(String workAreaId, String path, String label, String targetType, String panel) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            WorkspaceFileLabelTargetType requestedType = StringUtils.hasText(targetType)
                ? WorkspaceFileLabelTargetType.fromWireName(targetType)
                : null;
            explorer.addLabel(workAreaId, path, label, requestedType);
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(path),
                path,
                panelState(panel),
                "Tag added"
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    String removeWorkAreaTag(String workAreaId, String path, String label) {
        return removeWorkAreaTag(
            workAreaId,
            path,
            label,
            WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED
        );
    }

    String removeWorkAreaTag(String workAreaId, String path, String label, String panel) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.removeLabel(workAreaId, path, label);
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(path),
                path,
                panelState(panel),
                "Tag removed"
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/labels/note")
    @ResponseBody
    public String addWorkAreaNoteLabel(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.addLabel(workAreaId, path, "note");
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(path),
                path,
                WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED,
                "Tag added"
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    @DeleteMapping("/avatar/_work-areas/{workAreaId}/labels/note")
    @ResponseBody
    public String removeWorkAreaNoteLabel(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireAssistantExplorerService(workAreaId);
        try {
            explorer.removeLabel(workAreaId, path, "note");
            return refreshedExplorerTargets(
                explorer,
                workAreaId,
                parentPath(path),
                path,
                WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED,
                "Tag removed"
            );
        } catch (IllegalArgumentException exception) {
            return WorkAreaExplorerFragments.inspectorError(exception.getMessage());
        }
    }

    private AvatarDashboardComponents.AvatarDashboardData data() {
        return data(avatarService.assistantDashboard().id());
    }

    private AvatarDashboardComponents.AvatarDashboardData data(String dashboardId) {
        List<AgentProfile> agents = safeList(agentProfileService::list);
        return new AvatarDashboardComponents.AvatarDashboardData(
            avatarService.dashboard(dashboardId),
            avatarService.dashboards(),
            avatarService.profile(),
            avatarService.dashboardLayout(),
            avatarService.dashboardRows(dashboardId),
            avatarService.dailyTasks(LocalDate.now()),
            avatarService.todos(),
            avatarService.calendarItems(),
            avatarService.todayPlanner(LocalDate.now()),
            avatarService.tasksRoutines(),
            avatarService.calendarSchedule(LocalDate.now(), LocalDate.now().plusDays(30)),
            avatarService.notes(false),
            avatarService.events(),
            safeList(() -> outputArtifactService.query(null, null, null, 20)),
            agents,
            avatarWorkAreas(),
            safeList(jobService::listDefinitions),
            assignments(agents),
            safeList(inboxService::userInbox),
            chatService.defaultModel()
        );
    }

    private AvatarDashboardComponents.AvatarDashboardData widgetDetailData(
        String widgetKey,
        String status,
        String range,
        String recurrence
    ) {
        return widgetDetailData(avatarService.assistantDashboard().id(), widgetKey, status, range, recurrence);
    }

    private AvatarDashboardComponents.AvatarDashboardData widgetDetailData(
        String dashboardId,
        String widgetKey,
        String status,
        String range,
        String recurrence
    ) {
        AvatarDashboardComponents.AvatarDashboardData base = data(dashboardId);
        if (!"tasks-routines".equals(widgetKey)) {
            return base;
        }
        return new AvatarDashboardComponents.AvatarDashboardData(
            base.dashboard(),
            base.dashboards(),
            base.profile(),
            base.layout(),
            base.rows(),
            base.dailyTasks(),
            base.todos(),
            base.calendarItems(),
            base.todayPlanner(),
            avatarService.tasksRoutines(status, range, recurrence),
            base.calendarSchedule(),
            base.notes(),
            base.events(),
            base.outputs(),
            base.agents(),
            base.workAreas(),
            base.jobs(),
            base.assignments(),
            base.userInbox(),
            base.defaultModel()
        );
    }

    private String renderTabPanel(AssistantTabState state) {
        return AvatarDashboardComponents.tabPanelResponse(data(), state.activeTab(), state.editMode()).render();
    }

    private Component wrapTabPanel(AssistantTabState state, Component content) {
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
        // TODO(avatar-shell-baseline): Replace this fallback summary with a dedicated Assistant history component once
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
            ? "Assistant"
            : avatarService.profile().displayName().strip();
        String agentSummary = avatarAgent == null
            ? "Reserved Assistant agent profile is not currently available."
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
            base.workAreas(),
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
            base.dashboard(),
            base.dashboards(),
            base.profile(),
            base.layout(),
            base.rows(),
            base.dailyTasks(),
            base.todos(),
            base.calendarItems(),
            base.todayPlanner(),
            base.tasksRoutines(),
            base.calendarSchedule(),
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

    private String firstWidgetByType(String widgetType) {
        AvatarDashboardComponents.AvatarDashboardData current = data();
        return current.rows().stream()
            .flatMap(row -> row.widgets().stream())
            .filter(widget -> widgetType.equals(widget.widgetKey()))
            .findFirst()
            .map(widget -> AvatarDashboardComponents.widget(current, AvatarDashboardComponents.displayWidget(widget)).render())
            .orElseGet(() -> AvatarDashboardComponents.widget(
                current,
                AvatarDashboardComponents.defaultWidget(AvatarDashboardComponents.definition(widgetType), 0)
            ).render());
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

    private List<WorkArea> avatarWorkAreas() {
        WorkAreaService service = workAreaService.getIfAvailable();
        if (service == null) {
            return List.of();
        }
        Map<String, WorkArea> byId = new LinkedHashMap<>();
        safeList(() -> service.list(WorkspaceOwnerType.AGENT, AVATAR_AGENT_ID, false))
            .forEach(workArea -> byId.put(workArea.id(), workArea));
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

    private AvatarDashboardRowWidget requireDashboardWidget(String dashboardId, String widgetInstanceId) {
        try {
            AvatarDashboardRowWidget widget = avatarService.dashboardWidget(widgetInstanceId);
            if (!avatarService.dashboardIdForWidget(widgetInstanceId).equals(dashboardId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "dashboard widget not found: " + widgetInstanceId);
            }
            return widget;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    private static Map<String, String> firstValues(MultiValueMap<String, String> params) {
        Map<String, String> values = new LinkedHashMap<>();
        if (params == null) {
            return values;
        }
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            List<String> entryValues = entry.getValue();
            values.put(entry.getKey(), entryValues == null || entryValues.isEmpty() ? "" : entryValues.getFirst());
        }
        return values;
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

    private WorkAreaExplorerService requireAssistantExplorerService(String workAreaId) {
        WorkAreaExplorerService explorer = requireExplorerService();
        WorkAreaService service = workAreaService.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Work Area service is unavailable");
        }
        WorkArea workArea;
        try {
            workArea = service.get(workAreaId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work Area unavailable");
        }
        if (workArea.ownerType() != WorkspaceOwnerType.AGENT || !AVATAR_AGENT_ID.equals(workArea.ownerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work Area unavailable");
        }
        return explorer;
    }

    private String renderTagEditorModal(
        WorkAreaExplorerService explorer,
        String workAreaId,
        String path,
        String panelState,
        String message
    ) {
        WorkAreaExplorerService.Entry entry = explorer.inspect(workAreaId, path);
        return WorkAreaExplorerFragments.tagEditorModal(
            workAreaId,
            entry,
            explorer.listAllTags(null, 200),
            panelState(panelState),
            message
        );
    }

    private String inferPathTargetType(WorkAreaExplorerService explorer, String workAreaId, String path) {
        return explorer.inspect(workAreaId, path).directory()
            ? WorkspaceFileLabelTargetType.DIRECTORY.wireName()
            : WorkspaceFileLabelTargetType.FILE.wireName();
    }

    private String requiredSingleValue(MultiValueMap<String, String> params, String field) {
        List<String> values = params.get(field);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        List<String> normalized = values.stream()
            .filter(StringUtils::hasText)
            .map(String::strip)
            .distinct()
            .toList();
        if (normalized.size() != 1) {
            throw new IllegalArgumentException(field + " must target exactly one value");
        }
        return normalized.getFirst();
    }

    private String optionalSingleValue(MultiValueMap<String, String> params, String field) {
        List<String> values = params.get(field);
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = values.stream()
            .filter(StringUtils::hasText)
            .map(String::strip)
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() != 1) {
            throw new IllegalArgumentException(field + " must target exactly one value");
        }
        return normalized.getFirst();
    }

    private String refreshedExplorer(
        WorkAreaExplorerService explorer,
        String workAreaId,
        String path,
        String panelState
    ) {
        WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, path);
        WorkAreaExplorerService.Entry inspected = selectedOrCurrentEntry(explorer, workAreaId, listing, path);
        return WorkAreaExplorerFragments.shell(
            listing,
            inspected,
            previewForInspector(explorer, workAreaId, inspected),
            inspected == null ? null : inspected.path(),
            panelState(panelState)
        );
    }

    private String refreshedExplorerTargets(
        WorkAreaExplorerService explorer,
        String workAreaId,
        String listPath,
        String selectedPath,
        String panelState,
        String message
    ) {
        WorkAreaExplorerService.DirectoryListing listing = explorer.list(workAreaId, listPath);
        WorkAreaExplorerService.Entry inspected = explorer.inspect(workAreaId, selectedPath);
        return WorkAreaExplorerFragments.mutationResponse(
            listing,
            inspected,
            previewForInspector(explorer, workAreaId, inspected),
            selectedPath,
            panelState(panelState),
            message
        );
    }

    private WorkAreaExplorerService.FilePreview previewForInspector(
        WorkAreaExplorerService explorer,
        String workAreaId,
        String path
    ) {
        try {
            WorkAreaExplorerService.Entry entry = explorer.inspect(workAreaId, path);
            return previewForInspector(explorer, workAreaId, entry);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private WorkAreaExplorerService.FilePreview previewForInspector(
        WorkAreaExplorerService explorer,
        String workAreaId,
        WorkAreaExplorerService.Entry entry
    ) {
        if (entry == null || entry.directory() || !entry.canView()) {
            return null;
        }
        try {
            return explorer.preview(workAreaId, entry.path());
        } catch (RuntimeException exception) {
            return null;
        }
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

    private String panelState(String panel) {
        return WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panel)
            ? WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_COLLAPSED
            : WorkAreaExplorerFragments.INSPECTOR_PANEL_STATE_EXPANDED;
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

    private AssistantTabState normalizeTabState(String tab, boolean edit) {
        return new AssistantTabState(DEFAULT_AVATAR_TAB, edit);
    }

    private String normalizeTab(String value) {
        return DEFAULT_AVATAR_TAB;
    }

    private String avatarUrl(AssistantTabState state) {
        StringBuilder url = new StringBuilder("/dashboards/assistant");
        if (state.editMode()) {
            url.append("?edit=true");
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

    private record AssistantTabState(String activeTab, boolean editMode) {
    }
}
