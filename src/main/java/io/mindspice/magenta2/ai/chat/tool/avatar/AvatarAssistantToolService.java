package io.mindspice.magenta2.ai.chat.tool.avatar;

import java.time.Instant;
import java.time.LocalDate;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.AssignmentRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.AssignmentResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.CalendarResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DailyTaskListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DailyTaskRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DailyTaskResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.DeletedResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.NoteListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.NoteRecord;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.NoteResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OutputContentResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OutputListResponse;
import io.mindspice.magenta2.ai.chat.tool.avatar.AvatarAssistantToolResponses.OutputRecord;
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
import io.mindspice.magenta2.avatar.AvatarCalendarItem;
import io.mindspice.magenta2.avatar.AvatarCalendarStatus;
import io.mindspice.magenta2.avatar.AvatarDailyTask;
import io.mindspice.magenta2.avatar.AvatarNote;
import io.mindspice.magenta2.avatar.AvatarPriority;
import io.mindspice.magenta2.avatar.AvatarService;
import io.mindspice.magenta2.avatar.AvatarTaskStatus;
import io.mindspice.magenta2.avatar.AvatarTodo;
import io.mindspice.magenta2.avatar.AvatarTodoStatus;
import org.springframework.context.annotation.Lazy;
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

    public AvatarAssistantToolService(
        AvatarService avatarService,
        AvatarAssistantToolAuthorizationService authorization,
        @Lazy TaskService taskService,
        @Lazy AssignmentService assignmentService,
        @Lazy OutputArtifactService outputArtifactService
    ) {
        this.avatarService = avatarService;
        this.authorization = authorization;
        this.taskService = taskService;
        this.assignmentService = assignmentService;
        this.outputArtifactService = outputArtifactService;
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
