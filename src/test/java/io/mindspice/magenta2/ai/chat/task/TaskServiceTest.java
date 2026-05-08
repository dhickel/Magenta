package io.mindspice.magenta2.ai.chat.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanToolContext;
import io.mindspice.magenta2.ai.chat.plan.PlanToolExecutionContext;
import io.mindspice.magenta2.ai.chat.tool.InteractionQuestionTools;
import io.mindspice.magenta2.ai.chat.tool.task.TaskTools;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskServiceTest {

    @Test
    void taskCrudDraftQuestionsSnapshotsAndOutputValidationWork() throws Exception {
        TaskService service = taskService();

        TaskDefinition task = service.saveTask(sampleTask(null));

        assertThat(service.getTask(task.id()).inputs()).extracting(TaskFieldDefinition::name).containsExactly("topic");
        assertThatThrownBy(() -> service.startRun(task.id(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("topic");

        TaskRun run = service.startRun(task.id(), Map.of("topic", "SQLite"));
        assertThat(run.taskSnapshot().title()).isEqualTo("Research task");
        service.registerExecutionContext("task-run-conversation", run.id());
        assertThat(service.mode("task-run-conversation")).isEqualTo(PlanMode.EXECUTE_TASK);
        assertThat(service.runtimeInstructions("task-run-conversation"))
            .contains("executing a reusable task")
            .contains("research_notes");
        assertThatThrownBy(() -> service.completeRun(run.id(), Map.of(), "done", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("research_notes");

        TaskRun completed = service.completeRun(
            run.id(),
            Map.of("research_notes", "SQLite notes"),
            "done",
            List.of("Named output stored.")
        );
        assertThat(completed.status()).isEqualTo(TaskRunStatus.COMPLETED);
        assertThat(service.getRun(run.id()).outputValues()).containsEntry("research_notes", "SQLite notes");
        service.clearExecutionContext("task-run-conversation");
        assertThat(service.mode("task-run-conversation")).isEqualTo(PlanMode.NORMAL);

        service.beginDraft("conversation-1", "model-a", "model-b");
        PlanToolExecutionContext.set(new PlanToolContext("conversation-1", PlanMode.TASK));
        try {
            new InteractionQuestionTools(null, service).askQuestions(List.of("Which inputs should this task accept?"));
        } finally {
            PlanToolExecutionContext.clear();
        }
        assertThat(service.activeDraft("conversation-1").orElseThrow().currentQuestion())
            .isEqualTo("Which inputs should this task accept?");
        service.recordPromptAnswer("conversation-1", "A topic input.", null, 1);
        assertThat(service.activeDraft("conversation-1").orElseThrow().hasPendingQuestion()).isFalse();
    }

    @Test
    void taskToolsAreModeGatedAndTaskCompleteRequiresDeclaredOutputs() throws Exception {
        TaskService service = taskService();
        TaskDefinition task = service.saveTask(sampleTask(null));
        TaskRun run = service.startRun(task.id(), Map.of("topic", "routing"));
        TaskTools tools = new TaskTools(service);

        assertThatThrownBy(() -> tools.complete(Map.of("research_notes", "notes"), "done", List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("execute_task mode");

        PlanToolExecutionContext.set(new PlanToolContext("run-conversation", PlanMode.EXECUTE_TASK, run.id()));
        try {
            assertThatThrownBy(() -> tools.complete(Map.of(), "done", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("research_notes");
            assertThat(tools.complete(Map.of("research_notes", "notes"), "done", List.of("evidence")))
                .isEqualTo("Task completed: " + run.id());
        } finally {
            PlanToolExecutionContext.clear();
        }
    }

    @Test
    void draftApprovalRequiresNamedOutputsButNotLegacyDeliverables() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        TaskRepository repository = new TaskRepository(jdbcTemplate, new ObjectMapper());
        TaskService service = new TaskService(repository);
        service.beginDraft("conversation-approval", "model-a", "model-b");
        TaskDraft draft = service.activeDraft("conversation-approval").orElseThrow();
        repository.saveDraft(new TaskDraft(
            draft.conversationId(),
            TaskDraftStatus.DRAFT,
            "approval_readiness",
            "Reusable research",
            "Research a topic.",
            "Collect reusable research notes.",
            null,
            "Runtime topic.",
            List.of(new TaskFieldDefinition("topic", TaskValueType.STRING, "Topic to research.", true, null, "SQLite")),
            "Named outputs.",
            List.of(new TaskFieldDefinition("research_notes", TaskValueType.LONG_TEXT, "Research notes.", true, null, null)),
            List.of(),
            List.of(new TaskStep(1, "Collect notes for <topic>.")),
            List.of("research_notes is present."),
            List.of(),
            0,
            draft.prePlanningModel(),
            draft.executionModel(),
            null,
            draft.createdAt(),
            draft.updatedAt()
        ));

        TaskDefinition task = service.approveDraft("conversation-approval");

        assertThat(task.title()).isEqualTo("Reusable research");
        assertThat(task.outputs()).extracting(TaskFieldDefinition::name).containsExactly("research_notes");
    }

    @Test
    void legacyRunSnapshotsWithDeliverablesStillLoad() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        TaskRepository repository = new TaskRepository(jdbcTemplate, new ObjectMapper());
        String now = "2026-05-06T00:00:00Z";
        String snapshot = """
            {
              "id": "legacy-task",
              "title": "Legacy task",
              "summary": null,
              "goal": "Load legacy snapshot.",
              "notes": null,
              "deliverables": ["Legacy deliverable"],
              "inputDescription": null,
              "inputs": [],
              "outputDescription": null,
              "outputs": [{"name":"result","type":"STRING","description":"Result.","required":true,"schema":null,"example":null}],
              "assumptions": [],
              "steps": [{"order":1,"text":"Do it."}],
              "validationCriteria": ["result is present."],
              "createdAt": "2026-05-06T00:00:00Z",
              "updatedAt": "2026-05-06T00:00:00Z"
            }
            """;
        jdbcTemplate.update(
            """
                insert into ai_task_runs (
                    id, task_id, status, input_values_json, output_values_json, task_snapshot_json,
                    execution_evidence_json, validation_feedback_json, final_message, error_text,
                    created_at, updated_at, started_at, completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            "legacy-run",
            "legacy-task",
            TaskRunStatus.COMPLETED.name(),
            "{}",
            "{\"result\":\"ok\"}",
            snapshot,
            "[]",
            "[]",
            "done",
            null,
            now,
            now,
            now,
            now
        );

        TaskRun run = repository.findRun("legacy-run").orElseThrow();

        assertThat(run.taskSnapshot().title()).isEqualTo("Legacy task");
        assertThat(run.taskSnapshot().outputs()).extracting(TaskFieldDefinition::name).containsExactly("result");
    }

    @Test
    void legacyDefinitionDraftColumnsAreIgnoredAndPlanningTaskIsNormalized() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("alter table ai_task_definitions add column deliverables_json text");
        jdbcTemplate.execute("alter table ai_task_drafts add column deliverables_json text");
        TaskRepository repository = new TaskRepository(jdbcTemplate, new ObjectMapper());
        String now = "2026-05-06T00:00:00Z";
        jdbcTemplate.update(
            """
                insert into ai_task_definitions (
                    id, title, summary, goal, notes, deliverables_json, input_description, inputs_json,
                    output_description, outputs_json, assumptions_json, steps_json, validation_criteria_json,
                    created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            "legacy-definition",
            "Legacy definition",
            null,
            "Goal",
            null,
            "[\"Legacy deliverable\"]",
            null,
            "[]",
            null,
            "[]",
            "[]",
            "[]",
            "[]",
            now,
            now
        );
        jdbcTemplate.update(
            """
                insert into ai_task_drafts (
                    conversation_id, status, planning_task, title, summary, goal, notes, deliverables_json,
                    input_description, inputs_json, output_description, outputs_json, assumptions_json,
                    steps_json, validation_criteria_json, pending_questions_json, pending_question_index,
                    pre_planning_model, execution_model, created_task_id, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            "legacy-draft",
            TaskDraftStatus.DRAFT.name(),
            "define_deliverables",
            "Legacy draft",
            null,
            "Goal",
            null,
            "[\"Legacy deliverable\"]",
            null,
            "[]",
            null,
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            0,
            null,
            null,
            null,
            now,
            now
        );

        assertThat(repository.find("legacy-definition").orElseThrow().title()).isEqualTo("Legacy definition");
        assertThat(repository.findDraft("legacy-draft").orElseThrow().planningTask()).isEqualTo("define_outputs");
    }

    private TaskDefinition sampleTask(String id) {
        return new TaskDefinition(
            id,
            "Research task",
            "Research a topic.",
            "Collect notes.",
            null,
            "Runtime topic.",
            List.of(new TaskFieldDefinition("topic", TaskValueType.STRING, "Topic to research.", true, null, "SQLite")),
            "Named outputs.",
            List.of(new TaskFieldDefinition("research_notes", TaskValueType.LONG_TEXT, "Research notes.", true, null, null)),
            List.of("Use available context."),
            List.of(new TaskStep(1, "Collect notes for <topic>.")),
            List.of("research_notes is present."),
            null,
            null
        );
    }

    private TaskService taskService() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        return new TaskService(new TaskRepository(jdbcTemplate, new ObjectMapper()));
    }

    private JdbcTemplate jdbcTemplate() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String statement : Files.readString(Path.of("src/main/resources/schema.sql")).split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
        return jdbcTemplate;
    }
}
