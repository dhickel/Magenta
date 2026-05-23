package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
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
import io.mindspice.magenta2.avatar.AvatarEvent;
import io.mindspice.magenta2.avatar.AvatarNote;
import io.mindspice.magenta2.avatar.AvatarPriority;
import io.mindspice.magenta2.avatar.AvatarService;
import io.mindspice.magenta2.avatar.AvatarTaskStatus;
import io.mindspice.magenta2.avatar.AvatarTodo;
import io.mindspice.magenta2.avatar.AvatarTodoStatus;
import io.mindspice.simplypages.builders.BannerBuilder;
import io.mindspice.simplypages.builders.ShellBuilder;
import io.mindspice.simplypages.builders.ShellTemplate;
import io.mindspice.simplypages.builders.TopNavBuilder;
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
    public String avatar() {
        return shell.renderWithContent(AvatarDashboardComponents.page(data()));
    }

    @GetMapping("/avatar/_widgets")
    @ResponseBody
    public String widgets() {
        return AvatarDashboardComponents.widgetGrid(data()).render();
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

    @GetMapping("/avatar/_edit")
    @ResponseBody
    public String edit(@RequestParam(value = "close", required = false) boolean close) {
        if (close) {
            return "";
        }
        return AvatarDashboardComponents.editModal(avatarService.dashboardRows()).render();
    }

    @PostMapping("/avatar/_layout/rows")
    @ResponseBody
    public String addLayoutRow() {
        avatarService.addDashboardRow();
        return AvatarDashboardComponents.layoutEditResponse(data()).render();
    }

    @PostMapping("/avatar/_layout/rows/{rowId}/move")
    @ResponseBody
    public String moveLayoutRow(@PathVariable String rowId, @RequestParam String direction) {
        try {
            avatarService.moveDashboardRow(rowId, directionValue(direction));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data()).render();
    }

    @GetMapping("/avatar/_layout/rows/{rowId}/catalog")
    @ResponseBody
    public String widgetCatalog(@PathVariable String rowId) {
        return AvatarDashboardComponents.widgetCatalogModal(avatarService.dashboardRows(), rowId).render();
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
        return AvatarDashboardComponents.layoutEditResponse(data()).render();
    }

    @PostMapping("/avatar/_layout/widgets/{widgetId}/move")
    @ResponseBody
    public String moveLayoutWidget(@PathVariable String widgetId, @RequestParam String direction) {
        try {
            avatarService.moveDashboardWidget(widgetId, direction);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data()).render();
    }

    @PutMapping("/avatar/_layout/widgets/{widgetId}/width")
    @ResponseBody
    public String resizeLayoutWidget(@PathVariable String widgetId, @RequestParam int columnWidth) {
        try {
            avatarService.resizeDashboardWidget(widgetId, columnWidth);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data()).render();
    }

    @DeleteMapping("/avatar/_layout/widgets/{widgetId}")
    @ResponseBody
    public String removeLayoutWidget(@PathVariable String widgetId) {
        try {
            avatarService.removeDashboardWidget(widgetId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return AvatarDashboardComponents.layoutEditResponse(data()).render();
    }

    @Deprecated
    @PutMapping("/avatar/_layout")
    @ResponseBody
    public String saveLayout() {
        return AvatarDashboardComponents.layoutEditResponse(data()).render();
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
        @RequestParam(value = "path", defaultValue = ".") String path
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return AvatarDashboardComponents.workAreaExplorer(explorer.list(workAreaId, path)).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/preview")
    @ResponseBody
    public String workAreaPreview(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return AvatarDashboardComponents.workAreaPreview(workAreaId, explorer.preview(workAreaId, path)).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/avatar/_work-areas/{workAreaId}/edit")
    @ResponseBody
    public String workAreaTextEditor(@PathVariable String workAreaId, @RequestParam String path) {
        WorkAreaExplorerService explorer = requireExplorerService();
        try {
            return AvatarDashboardComponents.workAreaTextEditor(workAreaId, explorer.preview(workAreaId, path)).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
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
            return AvatarDashboardComponents.workAreaExplorer(explorer.list(workAreaId, parentPath(path))).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
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
            return AvatarDashboardComponents.workAreaExplorer(explorer.list(workAreaId, path)).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/avatar/_work-areas/{workAreaId}/text")
    @ResponseBody
    public String createWorkAreaTextFile(
        @PathVariable String workAreaId,
        @RequestParam String path,
        @RequestParam String name
    ) {
        WorkAreaExplorerService explorer = requireExplorerService();
        String childPath = joinPath(path, name);
        try {
            explorer.saveText(workAreaId, childPath, "");
            return AvatarDashboardComponents.workAreaTextEditor(workAreaId, explorer.preview(workAreaId, childPath)).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
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
            return AvatarDashboardComponents.workAreaExplorer(explorer.list(workAreaId, parentPath(path))).render();
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
            return AvatarDashboardComponents.workAreaExplorer(explorer.list(workAreaId, parentPath(path))).render();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
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
}
