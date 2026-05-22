package io.mindspice.magenta2.ai.chat.tool.avatar;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.avatar.AvatarRepository;
import io.mindspice.magenta2.avatar.AvatarSchemaInitializer;
import io.mindspice.magenta2.avatar.AvatarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarToolsTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    private static final List<String> ORGANIZER_TOOLS = List.of(
        "avatar_todo_list",
        "avatar_todo_upsert",
        "avatar_todo_complete",
        "avatar_daily_task_list",
        "avatar_daily_task_upsert",
        "avatar_daily_task_complete",
        "avatar_calendar_list",
        "avatar_calendar_upsert",
        "avatar_calendar_delete",
        "avatar_note_append",
        "avatar_note_search",
        "avatar_submit_task",
        "avatar_submit_research_assignment",
        "avatar_list_outputs",
        "avatar_read_output"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TaskService taskService;
    private AssignmentService assignmentService;
    private OutputArtifactService outputArtifactService;
    private AvatarAssistantTools tools;

    @BeforeEach
    void setUp() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        new AvatarSchemaInitializer(dataSource).initialize();
        AvatarService avatarService = new AvatarService(new AvatarRepository(new JdbcTemplate(dataSource), objectMapper));
        AgentProfileService profileService = mock(AgentProfileService.class);
        when(profileService.get("avatar")).thenReturn(new AgentProfile(
            "avatar",
            "Avatar",
            AgentProfileStatus.ACTIVE,
            "model",
            "prompt",
            ORGANIZER_TOOLS,
            List.of(),
            true,
            Instant.now(),
            Instant.now()
        ));
        AvatarAssistantToolAuthorizationService authorization = new AvatarAssistantToolAuthorizationService(profileService, "avatar");
        taskService = mock(TaskService.class);
        assignmentService = mock(AssignmentService.class);
        outputArtifactService = mock(OutputArtifactService.class);
        tools = new AvatarAssistantTools(
            new AvatarAssistantToolService(avatarService, authorization, taskService, assignmentService, outputArtifactService),
            objectMapper
        );
        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "avatar", "Avatar", null, null, null, "AGENT_CHAT", null, null
        ));
    }

    @AfterEach
    void clearContext() {
        OrchestrationTaskContextHolder.clear();
    }

    @Test
    void organizerToolsCrudTodosDailyTasksCalendarAndNotes() throws Exception {
        JsonNode todo = json(tools.avatarTodoUpsert(
            null,
            "Pay bills",
            "Use checking",
            "OPEN",
            "HIGH",
            "2026-05-23T12:00:00Z",
            "project-1",
            "task-1",
            "output-1"
        )).path("todo");
        String todoId = todo.path("id").asText();

        assertThat(todo.path("title").asText()).isEqualTo("Pay bills");
        assertThat(todo.path("priority").asText()).isEqualTo("HIGH");

        JsonNode completedTodo = json(tools.avatarTodoComplete(todoId)).path("todo");
        JsonNode todoList = json(tools.avatarTodoList("DONE", true, 10)).path("todos");

        assertThat(completedTodo.path("status").asText()).isEqualTo("DONE");
        assertThat(todoList.size()).isEqualTo(1);
        assertThat(todoList.get(0).path("id").asText()).isEqualTo(todoId);

        JsonNode dailyTask = json(tools.avatarDailyTaskUpsert(
            null,
            "2026-05-22",
            "Review day",
            "Check open loops",
            "ACTIVE",
            2
        )).path("task");
        String dailyTaskId = dailyTask.path("id").asText();

        assertThat(dailyTask.path("taskDate").asText()).isEqualTo("2026-05-22");
        assertThat(json(tools.avatarDailyTaskComplete(dailyTaskId)).path("task").path("status").asText())
            .isEqualTo("DONE");
        assertThat(json(tools.avatarDailyTaskList("2026-05-22", true, 10)).path("tasks").size())
            .isEqualTo(1);

        JsonNode calendarItem = json(tools.avatarCalendarUpsert(
            null,
            "Dentist",
            "Bring card",
            "2026-05-24T15:00:00Z",
            "2026-05-24T16:00:00Z",
            "America/New_York",
            "Clinic",
            "SCHEDULED"
        )).path("item");
        String calendarId = calendarItem.path("id").asText();

        assertThat(json(tools.avatarCalendarList("2026-05-24T00:00:00Z", "2026-05-25T00:00:00Z", false, 10)).path("items").size())
            .isEqualTo(1);
        assertThat(json(tools.avatarCalendarDelete(calendarId)).path("deleted").asBoolean()).isTrue();
        assertThat(json(tools.avatarCalendarList(null, null, true, 10)).path("items").size()).isZero();

        JsonNode note = json(tools.avatarNoteAppend(
            null,
            "Garden",
            "Water seedlings",
            List.of("home", "plants")
        )).path("note");
        String noteId = note.path("id").asText();

        tools.avatarNoteAppend(noteId, null, "Move tray outside", List.of());
        JsonNode notes = json(tools.avatarNoteSearch("seedlings", false, 10)).path("notes");

        assertThat(notes.size()).isEqualTo(1);
        assertThat(notes.get(0).path("id").asText()).isEqualTo(noteId);
        assertThat(notes.get(0).path("snippet").asText()).contains("Water seedlings");
    }

    @Test
    void registersAvatarOrganizerToolsWithChatToolRegistry() {
        ToolCallbackProvider provider = new AvatarAssistantToolConfiguration()
            .avatarAssistantToolCallbackProvider(tools);
        List<String> names = Arrays.stream(provider.getToolCallbacks())
            .map(callback -> callback.getToolDefinition().name())
            .collect(Collectors.toList());

        assertThat(names).containsExactlyInAnyOrderElementsOf(ORGANIZER_TOOLS);

        ChatToolRegistry registry = new ChatToolRegistry(List.of(), List.of(provider));
        assertThat(registry.resolveApprovedTools(List.of("avatar_todo_list", "avatar_note_search")))
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactly("avatar_todo_list", "avatar_note_search");
    }

    @Test
    void submitsTaskAndResearchAssignmentsThroughAssignmentService() throws Exception {
        when(taskService.getTask("task-1")).thenReturn(task("task-1"));
        when(assignmentService.create(any())).thenReturn(assignment("assignment-1", "agent-1"));

        JsonNode taskAssignment = json(tools.avatarSubmitTask(
            "task-1",
            "agent-1",
            Map.of("topic", "avatars"),
            "conversation-1",
            "project-1",
            "workspace-1",
            "local-model",
            7
        )).path("assignment");

        assertThat(taskAssignment.path("id").asText()).isEqualTo("assignment-1");
        verify(assignmentService).create(argThat(request ->
            request.assignmentType() == AssignmentType.TASK_RUN
                && "agent-1".equals(request.agentId())
                && "project-1".equals(request.projectId())
                && "task-1".equals(request.input().get("taskId"))
                && "conversation-1".equals(request.input().get("conversationId"))
        ));

        when(assignmentService.create(any())).thenReturn(assignment("assignment-2", "agent-2"));
        JsonNode researchAssignment = json(tools.avatarSubmitResearchAssignment(
            "task-1",
            "agent-2",
            "Find current Avatar gaps",
            "Prefer internal docs",
            List.of("docs", "plans"),
            null,
            null,
            null,
            4
        )).path("assignment");

        assertThat(researchAssignment.path("id").asText()).isEqualTo("assignment-2");
        verify(assignmentService).create(argThat(request -> {
            Object inputValues = request.input().get("inputValues");
            return request.assignmentType() == AssignmentType.TASK_RUN
                && "agent-2".equals(request.agentId())
                && inputValues instanceof Map<?, ?> map
                && "research".equals(map.get("assignmentKind"))
                && "Find current Avatar gaps".equals(map.get("researchQuestion"));
        }));
    }

    @Test
    void listsAndReadsOutputsThroughOutputArtifactService() throws Exception {
        RunOutputArtifact artifact = artifact("artifact-1");
        when(outputArtifactService.query(any(OutputArtifactQuery.class))).thenReturn(List.of(artifact));
        when(outputArtifactService.getArtifact("artifact-1")).thenReturn(artifact);
        when(outputArtifactService.loadContent("artifact-1", 128L)).thenReturn("output text");

        JsonNode outputs = json(tools.avatarListOutputs(
            "agent-1",
            null,
            "project-1",
            null,
            "run-1",
            "task-1",
            "TASK_RUN",
            "text",
            10
        ));
        JsonNode content = json(tools.avatarReadOutput("artifact-1", 128L));

        assertThat(outputs.path("outputs").size()).isEqualTo(1);
        assertThat(outputs.path("outputs").get(0).path("id").asText()).isEqualTo("artifact-1");
        assertThat(content.path("content").asText()).isEqualTo("output text");
        verify(outputArtifactService).loadContent("artifact-1", 128L);
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private TaskDefinition task(String id) {
        return new TaskDefinition(id, "Task", null, "Goal", null, null, List.of(), null, List.of(), List.of(), List.of(), List.of(), null, null);
    }

    private WorkAssignment assignment(String id, String agentId) {
        return new WorkAssignment(
            id,
            agentId,
            null,
            null,
            AssignmentType.TASK_RUN,
            0,
            OrchestrationStatus.QUEUED,
            null,
            null,
            null,
            "workspace-1",
            "AGENT",
            0,
            Map.of(),
            Map.of("taskId", "task-1"),
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null,
            null
        );
    }

    private RunOutputArtifact artifact(String id) {
        return new RunOutputArtifact(
            id,
            "run-1",
            "task-1",
            "agent-1",
            null,
            null,
            null,
            "project-1",
            "workspace-1",
            "TASK_RUN",
            "result",
            "text",
            "result.txt",
            "outputs/result.txt",
            null,
            Instant.now()
        );
    }
}
