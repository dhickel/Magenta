package io.mindspice.magenta2.ai.chat.tool.avatar;

import java.time.Instant;
import java.time.LocalDate;
import java.io.IOException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.AssignmentRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.AssignmentResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarEntryRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarScheduleResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DailyTaskListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DailyTaskRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DailyTaskResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DeletedResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.NoteListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.NoteRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.NoteResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OccurrenceRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OccurrenceResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OutputContentResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OutputListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OutputRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.ReminderRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.ReminderResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TaskRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TaskResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TasksRoutinesResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TimeBlockRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TimeBlockResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TodayPlanResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TodoListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TodoRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.TodoResponse;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.avatar.dashboard.DashboardProjectArtifact;
import io.mindspice.magenta2.avatar.dashboard.DashboardProjectContextView;
import io.mindspice.magenta2.avatar.dashboard.ProjectArtifactService;
import io.mindspice.magenta2.avatar.AvatarCalendarItem;
import io.mindspice.magenta2.avatar.AvatarCalendarStatus;
import io.mindspice.magenta2.avatar.AvatarDailyTask;
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
import io.mindspice.magenta2.avatar.PlannerTask;
import io.mindspice.magenta2.avatar.PlannerTaskLink;
import io.mindspice.magenta2.avatar.PlannerTaskStatus;
import io.mindspice.magenta2.avatar.PlannerTimeBlock;
import io.mindspice.magenta2.avatar.TodayPlannerView;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AvatarAssistantToolService {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final AvatarService avatarService;
    private final AvatarAssistantToolAuthorizationService authorization;
    private final TaskService taskService;
    private final AssignmentService assignmentService;
    private final OutputArtifactService outputArtifactService;
    private final ProjectArtifactService projectArtifactService;
    private final WorkAreaExplorerService workAreaExplorerService;

    public AvatarAssistantToolService(
        AvatarService avatarService,
        AvatarAssistantToolAuthorizationService authorization,
        @Lazy TaskService taskService,
        @Lazy AssignmentService assignmentService,
        @Lazy OutputArtifactService outputArtifactService
    ) {
        this(avatarService, authorization, taskService, assignmentService, outputArtifactService, null, null);
    }

    @Autowired
    public AvatarAssistantToolService(
        AvatarService avatarService,
        AvatarAssistantToolAuthorizationService authorization,
        @Lazy TaskService taskService,
        @Lazy AssignmentService assignmentService,
        @Lazy OutputArtifactService outputArtifactService,
        @Lazy ProjectArtifactService projectArtifactService,
        @Lazy WorkAreaExplorerService workAreaExplorerService
    ) {
        this.avatarService = avatarService;
        this.authorization = authorization;
        this.taskService = taskService;
        this.assignmentService = assignmentService;
        this.outputArtifactService = outputArtifactService;
        this.projectArtifactService = projectArtifactService;
        this.workAreaExplorerService = workAreaExplorerService;
    }

    public TodoListResponse todoList(String status, Boolean includeDone, Integer limit) {
        authorization.requireAvatarSupervisor("avatar_todo_list");
        AvatarTodoStatus requestedStatus = enumValue(AvatarTodoStatus.class, status, null);
        boolean showDone = Boolean.TRUE.equals(includeDone);
        List<TodoRecord> todos = avatarService.todos().stream()
            .filter(todo -> requestedStatus == null || todo.status() == requestedStatus)
            .filter(todo -> showDone || (todo.status() != AvatarTodoStatus.DONE && todo.status() != AvatarTodoStatus.CANCELED))
            .limit(boundLimit(limit))
            .map(this::todoRecord)
            .toList();
        return new TodoListResponse(todos);
    }

    public TodoResponse todoUpsert(
        String id,
        String title,
        String notes,
        String status,
        String priority,
        String dueAt,
        String linkedProjectId,
        String linkedTaskId,
        String linkedOutputId
    ) {
        authorization.requireAvatarSupervisor("avatar_todo_upsert");
        AvatarTodo current = StringUtils.hasText(id) ? avatarService.todo(id) : null;
        String effectiveTitle = requireText(firstText(title, current == null ? null : current.title()), "title");
        AvatarTodo saved = avatarService.saveTodo(new AvatarTodo(
            current == null ? trimToNull(id) : current.id(),
            effectiveTitle,
            firstValue(notes, current == null ? null : current.notes()),
            enumValue(AvatarTodoStatus.class, status, current == null ? AvatarTodoStatus.OPEN : current.status()),
            enumValue(AvatarPriority.class, priority, current == null ? AvatarPriority.NORMAL : current.priority()),
            instant(firstValue(dueAt, current == null ? null : string(current.dueAt()))),
            firstValue(linkedProjectId, current == null ? null : current.linkedProjectId()),
            firstValue(linkedTaskId, current == null ? null : current.linkedTaskId()),
            firstValue(linkedOutputId, current == null ? null : current.linkedOutputId()),
            current == null ? null : current.createdAt(),
            current == null ? null : current.updatedAt(),
            current == null ? null : current.completedAt()
        ));
        return new TodoResponse(todoRecord(saved));
    }

    public TodoResponse todoComplete(String id) {
        authorization.requireAvatarSupervisor("avatar_todo_complete");
        return new TodoResponse(todoRecord(avatarService.completeTodo(id)));
    }

    public DailyTaskListResponse dailyTaskList(String date, Boolean includeDone, Integer limit) {
        authorization.requireAvatarSupervisor("avatar_daily_task_list");
        boolean showDone = Boolean.TRUE.equals(includeDone);
        List<DailyTaskRecord> tasks = avatarService.dailyTasks(localDate(date)).stream()
            .filter(task -> showDone || task.status() != AvatarTaskStatus.DONE)
            .limit(boundLimit(limit))
            .map(this::dailyTaskRecord)
            .toList();
        return new DailyTaskListResponse(tasks);
    }

    public DailyTaskResponse dailyTaskUpsert(
        String id,
        String date,
        String title,
        String notes,
        String status,
        Integer position
    ) {
        authorization.requireAvatarSupervisor("avatar_daily_task_upsert");
        AvatarDailyTask current = StringUtils.hasText(id) ? avatarService.dailyTask(id) : null;
        LocalDate effectiveDate = current == null ? requireDate(date) : firstValue(localDate(date), current.taskDate());
        String effectiveTitle = requireText(firstText(title, current == null ? null : current.title()), "title");
        AvatarDailyTask saved = avatarService.saveDailyTask(new AvatarDailyTask(
            current == null ? trimToNull(id) : current.id(),
            effectiveDate,
            effectiveTitle,
            firstValue(notes, current == null ? null : current.notes()),
            enumValue(AvatarTaskStatus.class, status, current == null ? AvatarTaskStatus.PLANNED : current.status()),
            position == null ? (current == null ? 0 : current.position()) : position,
            current == null ? null : current.createdAt(),
            current == null ? null : current.updatedAt()
        ));
        return new DailyTaskResponse(dailyTaskRecord(saved));
    }

    public DailyTaskResponse dailyTaskComplete(String id) {
        authorization.requireAvatarSupervisor("avatar_daily_task_complete");
        return new DailyTaskResponse(dailyTaskRecord(avatarService.completeDailyTask(id)));
    }

    public CalendarListResponse calendarList(String startsAfter, String startsBefore, Boolean includeCanceled, Integer limit) {
        authorization.requireAvatarSupervisor("avatar_calendar_list");
        Instant after = instant(startsAfter);
        Instant before = instant(startsBefore);
        boolean showCanceled = Boolean.TRUE.equals(includeCanceled);
        List<CalendarRecord> items = avatarService.calendarItems().stream()
            .filter(item -> after == null || !item.startsAt().isBefore(after))
            .filter(item -> before == null || item.startsAt().isBefore(before))
            .filter(item -> showCanceled || item.status() != AvatarCalendarStatus.CANCELED)
            .limit(boundLimit(limit))
            .map(this::calendarRecord)
            .toList();
        return new CalendarListResponse(items);
    }

    public CalendarResponse calendarUpsert(
        String id,
        String title,
        String notes,
        String startsAt,
        String endsAt,
        String timezone,
        String location,
        String status
    ) {
        authorization.requireAvatarSupervisor("avatar_calendar_upsert");
        AvatarCalendarItem current = StringUtils.hasText(id) ? avatarService.calendarItem(id) : null;
        Instant effectiveStart = current == null ? requireInstant(startsAt, "startsAt") : firstValue(instant(startsAt), current.startsAt());
        String effectiveTitle = requireText(firstText(title, current == null ? null : current.title()), "title");
        AvatarCalendarItem saved = avatarService.saveCalendarItem(new AvatarCalendarItem(
            current == null ? trimToNull(id) : current.id(),
            effectiveTitle,
            firstValue(notes, current == null ? null : current.notes()),
            effectiveStart,
            firstValue(instant(endsAt), current == null ? null : current.endsAt()),
            firstValue(timezone, current == null ? null : current.timezone()),
            firstValue(location, current == null ? null : current.location()),
            enumValue(AvatarCalendarStatus.class, status, current == null ? AvatarCalendarStatus.SCHEDULED : current.status()),
            current == null ? null : current.createdAt(),
            current == null ? null : current.updatedAt()
        ));
        return new CalendarResponse(calendarRecord(saved));
    }

    public DeletedResponse calendarDelete(String id) {
        authorization.requireAvatarSupervisor("avatar_calendar_delete");
        avatarService.deleteCalendarItem(id);
        return new DeletedResponse(id, true);
    }

    public TodayPlanResponse todayPlanGet(String date) {
        authorization.requireAvatarSupervisor("avatar_today_plan_get");
        TodayPlannerView view = avatarService.todayPlanner(firstValue(localDate(date), LocalDate.now()));
        return new TodayPlanResponse(
            view.date().toString(),
            view.topPriorities().stream().map(this::taskRecord).toList(),
            view.now().stream().map(this::taskRecord).toList(),
            view.next().stream().map(this::taskRecord).toList(),
            view.later().stream().map(this::taskRecord).toList(),
            view.overdue().stream().map(this::taskRecord).toList(),
            view.unscheduled().stream().map(this::taskRecord).toList(),
            view.timeBlocks().stream().map(this::timeBlockRecord).toList(),
            view.reminders().stream().map(this::reminderRecord).toList()
        );
    }

    public TodayPlanResponse todayPlanUpdate(String date, String reviewNotes, Boolean restart) {
        authorization.requireAvatarSupervisor("avatar_today_plan_update");
        LocalDate day = firstValue(localDate(date), LocalDate.now());
        if (Boolean.TRUE.equals(restart)) {
            avatarService.restartDay(day);
        }
        if (StringUtils.hasText(reviewNotes)) {
            avatarService.reviewDay(day, reviewNotes);
        }
        return todayPlanGet(day.toString());
    }

    public TaskResponse quickCapture(String title, String notes) {
        authorization.requireAvatarSupervisor("avatar_quick_capture");
        return new TaskResponse(taskRecord(avatarService.quickCapture(title, notes)));
    }

    public TodayPlanResponse dayRestart(String date) {
        authorization.requireAvatarSupervisor("avatar_day_restart");
        LocalDate day = firstValue(localDate(date), LocalDate.now());
        avatarService.restartDay(day);
        return todayPlanGet(day.toString());
    }

    public TasksRoutinesResponse tasksRoutinesGet(Integer limit) {
        authorization.requireAvatarSupervisor("avatar_tasks_routines_get");
        int bounded = boundLimit(limit);
        return new TasksRoutinesResponse(
            avatarService.tasksRoutines().tasks().stream().limit(bounded).map(this::taskRecord).toList(),
            avatarService.tasksRoutines().occurrences().stream().limit(bounded).map(this::occurrenceRecord).toList(),
            avatarService.tasksRoutines().reminders().stream().limit(bounded).map(this::reminderRecord).toList()
        );
    }

    public TaskResponse taskUpsert(
        String id,
        String title,
        String notes,
        String status,
        String priority,
        String startsAt,
        String dueAt,
        String recurrenceMode,
        String projectId
    ) {
        authorization.requireAvatarSupervisor("avatar_task_upsert");
        PlannerTask current = StringUtils.hasText(id) ? avatarService.plannerTask(id) : null;
        PlannerRecurrence recurrence = new PlannerRecurrence(
            enumValue(PlannerRecurrenceMode.class, recurrenceMode, current == null || current.recurrence() == null
                ? PlannerRecurrenceMode.NONE
                : current.recurrence().mode()),
            current == null || current.recurrence() == null ? 1 : current.recurrence().interval(),
            current == null || current.recurrence() == null ? null : current.recurrence().startDate(),
            current == null || current.recurrence() == null ? null : current.recurrence().endDate(),
            current == null || current.recurrence() == null ? null : current.recurrence().time(),
            current == null || current.recurrence() == null ? null : current.recurrence().weekday(),
            current == null || current.recurrence() == null ? null : current.recurrence().monthDay(),
            current == null || current.recurrence() == null ? null : current.recurrence().cron()
        );
        PlannerTask saved = avatarService.savePlannerTask(new PlannerTask(
            current == null ? trimToNull(id) : current.id(),
            requireText(firstText(title, current == null ? null : current.title()), "title"),
            firstValue(notes, current == null ? null : current.notes()),
            enumValue(PlannerTaskStatus.class, status, current == null ? PlannerTaskStatus.PLANNED : current.status()),
            enumValue(AvatarPriority.class, priority, current == null ? AvatarPriority.NORMAL : current.priority()),
            firstValue(instant(startsAt), current == null ? null : current.startsAt()),
            firstValue(instant(dueAt), current == null ? null : current.dueAt()),
            current == null ? ZoneId.systemDefault().getId() : current.timezone(),
            recurrence,
            new PlannerTaskLink(firstValue(projectId, current == null || current.link() == null ? null : current.link().projectId()),
                current == null || current.link() == null ? null : current.link().assignmentId(),
                current == null || current.link() == null ? null : current.link().jobId(),
                current == null || current.link() == null ? null : current.link().outputId()),
            current == null ? null : current.createdAt(),
            current == null ? null : current.updatedAt(),
            current == null ? null : current.completedAt()
        ));
        return new TaskResponse(taskRecord(saved));
    }

    public OccurrenceResponse taskOccurrenceUpdate(String taskId, String occurrenceStart, String action, String snoozedUntil) {
        authorization.requireAvatarSupervisor("avatar_task_occurrence_update");
        return new OccurrenceResponse(occurrenceRecord(avatarService.updateOccurrence(
            taskId,
            requireInstant(occurrenceStart, "occurrenceStart"),
            action,
            instant(snoozedUntil)
        )));
    }

    public CalendarScheduleResponse calendarScheduleGet(String startDate, String endDate) {
        authorization.requireAvatarSupervisor("avatar_calendar_schedule_get");
        CalendarScheduleView view = avatarService.calendarSchedule(localDate(startDate), localDate(endDate));
        return new CalendarScheduleResponse(
            view.startDate().toString(),
            view.endDate().toString(),
            view.entries().stream().map(this::calendarEntryRecord).toList()
        );
    }

    public TimeBlockResponse timeblockUpsert(
        String id,
        String date,
        String title,
        String startsAt,
        String endsAt,
        String sourceType,
        String sourceId
    ) {
        authorization.requireAvatarSupervisor("avatar_timeblock_upsert");
        PlannerTimeBlock saved = avatarService.saveTimeBlock(new PlannerTimeBlock(
            trimToNull(id),
            requireDate(date),
            requireText(title, "title"),
            requireInstant(startsAt, "startsAt"),
            instant(endsAt),
            trimToNull(sourceType),
            trimToNull(sourceId),
            "PLANNED",
            null,
            null
        ));
        return new TimeBlockResponse(timeBlockRecord(saved));
    }

    public ReminderResponse reminderUpsert(
        String id,
        String title,
        String notes,
        String remindAt,
        String status,
        String sourceType,
        String sourceId,
        String snoozedUntil
    ) {
        authorization.requireAvatarSupervisor("avatar_reminder_upsert");
        PlannerReminder saved = avatarService.saveReminder(new PlannerReminder(
            trimToNull(id),
            requireText(title, "title"),
            notes,
            requireInstant(remindAt, "remindAt"),
            StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "OPEN",
            trimToNull(sourceType),
            trimToNull(sourceId),
            instant(snoozedUntil),
            null,
            null
        ));
        return new ReminderResponse(reminderRecord(saved));
    }

    public NoteResponse noteAppend(String id, String title, String body, List<String> tags) {
        authorization.requireAvatarSupervisor("avatar_note_append");
        return new NoteResponse(noteRecord(avatarService.appendNote(id, title, body, cleanTags(tags))));
    }

    public NoteListResponse noteSearch(String query, Boolean includeArchived, Integer limit) {
        authorization.requireAvatarSupervisor("avatar_note_search");
        List<NoteRecord> notes = avatarService.searchNotes(
                query,
                Boolean.TRUE.equals(includeArchived),
                boundLimit(limit)
            ).stream()
            .map(this::noteRecord)
            .toList();
        return new NoteListResponse(notes);
    }

    public Map<String, Object> fileNoteRead(String source, String bindingId, String path) {
        authorization.requireAvatarSupervisor("avatar_file_note_read");
        WorkAreaExplorerService.FilePreview preview = filePreview(source, bindingId, path, false, null);
        return Map.of(
            "source", normalizeSource(source),
            "bindingId", bindingId,
            "path", preview.path(),
            "kind", preview.kind(),
            "text", preview.text(),
            "content", preview.content() == null ? "" : preview.content(),
            "size", preview.size()
        );
    }

    public Map<String, Object> fileNoteUpdate(String source, String bindingId, String path, String content) {
        authorization.requireAvatarSupervisor("avatar_file_note_update");
        WorkAreaExplorerService.FilePreview preview = filePreview(source, bindingId, path, true, content);
        return Map.of(
            "source", normalizeSource(source),
            "bindingId", bindingId,
            "path", preview.path(),
            "kind", preview.kind(),
            "saved", true,
            "size", preview.size()
        );
    }

    public Map<String, Object> projectContextGet(String projectId) {
        authorization.requireAvatarSupervisor("avatar_project_context_get");
        ProjectArtifactService service = requireProjectArtifactService();
        DashboardProjectContextView context = service.context(requireText(projectId, "projectId"));
        if (context.missingBinding()) {
            return Map.of("projectId", projectId, "available", false, "message", context.missingBindingMessage());
        }
        return Map.of(
            "projectId", context.project().id(),
            "name", context.project().name(),
            "codeProject", context.codeProject(),
            "storageRoot", context.storageRootLabel(),
            "artifacts", context.artifacts().stream().map(this::projectArtifactMap).toList(),
            "notes", context.notes().stream().map(note -> Map.of(
                "source", note.sourceMode(),
                "path", note.path(),
                "title", note.title(),
                "tags", note.tags()
            )).toList(),
            "outputs", context.outputs().stream().limit(10).map(output -> Map.of(
                "id", output.id(),
                "name", output.outputName() == null ? "" : output.outputName(),
                "type", output.artifactType() == null ? "" : output.artifactType()
            )).toList()
        );
    }

    public Map<String, Object> projectArtifactUpdate(String projectId, String artifactType, String content) {
        authorization.requireAvatarSupervisor("avatar_project_artifact_update");
        DashboardProjectArtifact artifact = requireProjectArtifactService()
            .updateArtifact(requireText(projectId, "projectId"), requireText(artifactType, "artifactType"), content);
        return Map.of("projectId", projectId, "artifact", projectArtifactMap(artifact), "saved", true);
    }

    public AssignmentResponse submitTask(
        String taskId,
        String agentId,
        Map<String, Object> inputValues,
        String conversationId,
        String projectId,
        String workspaceId,
        String modelOverride,
        Integer priority
    ) {
        authorization.requireAvatarSupervisor("avatar_submit_task");
        return new AssignmentResponse(assignmentRecord(createTaskAssignment(
            taskId, agentId, inputValues, conversationId, projectId, workspaceId, modelOverride, priority)));
    }

    public AssignmentResponse submitResearchAssignment(
        String taskId,
        String agentId,
        String researchQuestion,
        String instructions,
        List<String> sourceHints,
        String projectId,
        String workspaceId,
        String modelOverride,
        Integer priority
    ) {
        authorization.requireAvatarSupervisor("avatar_submit_research_assignment");
        requireText(researchQuestion, "researchQuestion");
        Map<String, Object> inputValues = new LinkedHashMap<>();
        inputValues.put("researchQuestion", researchQuestion.trim());
        if (StringUtils.hasText(instructions)) {
            inputValues.put("instructions", instructions.trim());
        }
        List<String> hints = cleanTags(sourceHints);
        if (!hints.isEmpty()) {
            inputValues.put("sourceHints", hints);
        }
        inputValues.put("assignmentKind", "research");
        return new AssignmentResponse(assignmentRecord(createTaskAssignment(
            taskId, agentId, inputValues, null, projectId, workspaceId, modelOverride, priority)));
    }

    public OutputListResponse listOutputs(
        String agentId,
        String jobId,
        String projectId,
        String workspaceId,
        String runId,
        String planId,
        String runType,
        String artifactType,
        Integer limit
    ) {
        authorization.requireAvatarSupervisor("avatar_list_outputs");
        int bounded = Math.min(Math.max(limit == null ? 50 : limit, 1), 200);
        List<OutputRecord> outputs = outputArtifactService.query(OutputArtifactQuery.of(
                agentId,
                jobId,
                null,
                null,
                projectId,
                workspaceId,
                runId,
                planId,
                runType,
                artifactType,
                bounded
            )).stream()
            .limit(bounded)
            .map(this::outputRecord)
            .toList();
        return new OutputListResponse(outputs.size(), bounded, outputs);
    }

    public OutputContentResponse readOutput(String artifactId, Long maxBytes) throws IOException {
        authorization.requireAvatarSupervisor("avatar_read_output");
        RunOutputArtifact artifact = outputArtifactService.getArtifact(artifactId);
        long bounded = boundReadBytes(maxBytes);
        String content = outputArtifactService.loadContent(artifactId, bounded);
        return new OutputContentResponse(outputRecord(artifact), content.length(), content);
    }

    private TodoRecord todoRecord(AvatarTodo todo) {
        return new TodoRecord(
            todo.id(),
            todo.title(),
            todo.notes(),
            todo.status().name(),
            todo.priority().name(),
            string(todo.dueAt()),
            todo.linkedProjectId(),
            todo.linkedTaskId(),
            todo.linkedOutputId(),
            string(todo.updatedAt()),
            string(todo.completedAt())
        );
    }

    private DailyTaskRecord dailyTaskRecord(AvatarDailyTask task) {
        return new DailyTaskRecord(
            task.id(),
            task.taskDate().toString(),
            task.title(),
            task.notes(),
            task.status().name(),
            task.position(),
            string(task.updatedAt())
        );
    }

    private CalendarRecord calendarRecord(AvatarCalendarItem item) {
        return new CalendarRecord(
            item.id(),
            item.title(),
            item.notes(),
            string(item.startsAt()),
            string(item.endsAt()),
            item.timezone(),
            item.location(),
            item.status().name(),
            string(item.updatedAt())
        );
    }

    private TaskRecord taskRecord(PlannerTask task) {
        return new TaskRecord(
            task.id(),
            task.title(),
            task.notes(),
            task.status() == null ? null : task.status().name(),
            task.priority() == null ? null : task.priority().name(),
            string(task.startsAt()),
            string(task.dueAt()),
            task.recurrence() == null || task.recurrence().mode() == null ? "NONE" : task.recurrence().mode().name(),
            task.link() == null ? null : task.link().projectId(),
            string(task.updatedAt()),
            string(task.completedAt())
        );
    }

    private OccurrenceRecord occurrenceRecord(PlannerOccurrence occurrence) {
        return new OccurrenceRecord(
            occurrence.id(),
            occurrence.taskId(),
            string(occurrence.occurrenceStart()),
            string(occurrence.occurrenceEnd()),
            occurrence.status(),
            string(occurrence.skippedAt()),
            string(occurrence.snoozedUntil()),
            string(occurrence.restartedAt())
        );
    }

    private ReminderRecord reminderRecord(PlannerReminder reminder) {
        return new ReminderRecord(
            reminder.id(),
            reminder.title(),
            reminder.notes(),
            string(reminder.remindAt()),
            reminder.status(),
            reminder.sourceType(),
            reminder.sourceId(),
            string(reminder.snoozedUntil())
        );
    }

    private TimeBlockRecord timeBlockRecord(PlannerTimeBlock block) {
        return new TimeBlockRecord(
            block.id(),
            block.blockDate().toString(),
            block.title(),
            string(block.startsAt()),
            string(block.endsAt()),
            block.sourceType(),
            block.sourceId(),
            block.status()
        );
    }

    private CalendarEntryRecord calendarEntryRecord(CalendarScheduleView.Entry entry) {
        return new CalendarEntryRecord(
            entry.kind(),
            entry.sourceId(),
            entry.title(),
            string(entry.startsAt()),
            string(entry.endsAt()),
            entry.status(),
            entry.meta()
        );
    }

    private NoteRecord noteRecord(AvatarNote note) {
        return new NoteRecord(
            note.id(),
            note.title(),
            snippet(note.body()),
            note.tags() == null ? List.of() : note.tags(),
            note.archived(),
            string(note.updatedAt())
        );
    }

    private WorkAssignment createTaskAssignment(
        String taskId,
        String agentId,
        Map<String, Object> inputValues,
        String conversationId,
        String projectId,
        String workspaceId,
        String modelOverride,
        Integer priority
    ) {
        requireText(taskId, "taskId");
        requireText(agentId, "agentId");
        taskService.getTask(taskId);
        return assignmentService.create(new AssignmentRequest(
            agentId,
            null,
            null,
            AssignmentType.TASK_RUN,
            priority,
            modelOverride,
            projectId,
            workspaceId,
            taskInput(taskId, inputValues, conversationId)
        ));
    }

    private AssignmentRecord assignmentRecord(WorkAssignment assignment) {
        return new AssignmentRecord(
            assignment.id(),
            assignment.agentId(),
            assignment.assignmentType() == null ? null : assignment.assignmentType().name(),
            assignment.status() == null ? null : assignment.status().name(),
            assignment.priority(),
            assignment.modelOverride(),
            assignment.projectId(),
            assignment.workspaceId(),
            assignment.effectiveWorkspaceId(),
            assignment.effectiveWorkspaceKind(),
            string(assignment.updatedAt())
        );
    }

    private OutputRecord outputRecord(RunOutputArtifact artifact) {
        return new OutputRecord(
            artifact.id(),
            artifact.runId(),
            artifact.planId(),
            artifact.agentId(),
            artifact.jobId(),
            artifact.jobAssignmentId(),
            artifact.jobRunId(),
            artifact.projectId(),
            artifact.workspaceId(),
            artifact.runType(),
            artifact.outputName(),
            artifact.artifactType(),
            artifact.fileName(),
            string(artifact.createdAt())
        );
    }

    private WorkAreaExplorerService.FilePreview filePreview(
        String source,
        String bindingId,
        String path,
        boolean save,
        String content
    ) {
        String normalized = normalizeSource(source);
        requireText(bindingId, "bindingId");
        requireText(path, "path");
        if ("project".equals(normalized)) {
            ProjectArtifactService service = requireProjectArtifactService();
            return save
                ? service.saveProjectFile(bindingId, path, content)
                : service.readProjectFile(bindingId, path);
        }
        if ("work_area".equals(normalized)) {
            WorkAreaExplorerService service = requireWorkAreaExplorerService();
            return save
                ? service.saveText(bindingId, path, content)
                : service.preview(bindingId, path);
        }
        throw new IllegalArgumentException("file note source must be project or work_area");
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException("source is required");
        }
        return switch (source.trim().toLowerCase(Locale.ROOT)) {
            case "project" -> "project";
            case "work_area", "work-area" -> "work_area";
            default -> throw new IllegalArgumentException("unsupported file note source: " + source);
        };
    }

    private ProjectArtifactService requireProjectArtifactService() {
        if (projectArtifactService == null) {
            throw new IllegalStateException("project artifact service is unavailable");
        }
        return projectArtifactService;
    }

    private WorkAreaExplorerService requireWorkAreaExplorerService() {
        if (workAreaExplorerService == null) {
            throw new IllegalStateException("Work Area explorer service is unavailable");
        }
        return workAreaExplorerService;
    }

    private Map<String, Object> projectArtifactMap(DashboardProjectArtifact artifact) {
        return Map.of(
            "type", artifact.type(),
            "title", artifact.title(),
            "path", artifact.path(),
            "items", artifact.items(),
            "status", artifact.status() == null ? "" : artifact.status(),
            "error", artifact.error() == null ? "" : artifact.error()
        );
    }

    private Map<String, Object> taskInput(String taskId, Map<String, Object> inputValues, String conversationId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("taskId", taskId.trim());
        input.put("inputValues", inputValues == null ? Map.of() : new LinkedHashMap<>(inputValues));
        if (StringUtils.hasText(conversationId)) {
            input.put("conversationId", conversationId.trim());
        }
        return input;
    }

    private int boundLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(1, limit), MAX_LIMIT);
    }

    private long boundReadBytes(Long maxBytes) {
        if (maxBytes == null || maxBytes <= 0) {
            return 65_536L;
        }
        return Math.min(maxBytes, 1_048_576L);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }

    private Instant instant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value.trim()) : null;
    }

    private Instant requireInstant(String value, String name) {
        Instant parsed = instant(value);
        if (parsed == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return parsed;
    }

    private LocalDate localDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : null;
    }

    private LocalDate requireDate(String value) {
        LocalDate parsed = localDate(value);
        if (parsed == null) {
            throw new IllegalArgumentException("date is required");
        }
        return parsed;
    }

    private <T> T firstValue(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstValue(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240);
    }

    private String string(Instant value) {
        return value == null ? null : value.toString();
    }
}
