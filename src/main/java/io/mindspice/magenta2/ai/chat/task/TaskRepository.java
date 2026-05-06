package io.mindspice.magenta2.ai.chat.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
public class TaskRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<List<TaskFieldDefinition>> FIELD_LIST = new TypeReference<>() { };
    private static final TypeReference<List<TaskStep>> STEP_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> VALUE_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TaskRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    public List<TaskDefinition> findAll() {
        return jdbcTemplate.query(
            """
                select id, title, summary, goal, notes, input_description, inputs_json,
                       output_description, outputs_json, assumptions_json, steps_json, validation_criteria_json,
                       created_at, updated_at
                from ai_task_definitions
                order by updated_at desc, title asc
                """,
            (rs, rowNum) -> definitionFromRow(rs)
        );
    }

    public Optional<TaskDefinition> find(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select id, title, summary, goal, notes, input_description, inputs_json,
                       output_description, outputs_json, assumptions_json, steps_json, validation_criteria_json,
                       created_at, updated_at
                from ai_task_definitions
                where id = ?
                """,
            rs -> rs.next() ? Optional.of(definitionFromRow(rs)) : Optional.empty(),
            id
        );
    }

    @Transactional
    public TaskDefinition save(TaskDefinition task) {
        Instant createdAt = task.createdAt() == null ? Instant.now() : task.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into ai_task_definitions (
                    id, title, summary, goal, notes, input_description, inputs_json,
                    output_description, outputs_json, assumptions_json, steps_json, validation_criteria_json,
                    created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    summary = excluded.summary,
                    goal = excluded.goal,
                    notes = excluded.notes,
                    input_description = excluded.input_description,
                    inputs_json = excluded.inputs_json,
                    output_description = excluded.output_description,
                    outputs_json = excluded.outputs_json,
                    assumptions_json = excluded.assumptions_json,
                    steps_json = excluded.steps_json,
                    validation_criteria_json = excluded.validation_criteria_json,
                    updated_at = excluded.updated_at
                """,
            task.id(),
            task.title(),
            task.summary(),
            task.goal(),
            task.notes(),
            task.inputDescription(),
            jsonOrNull(task.inputs()),
            task.outputDescription(),
            jsonOrNull(task.outputs()),
            jsonOrNull(task.assumptions()),
            jsonOrNull(task.steps()),
            jsonOrNull(task.validationCriteria()),
            createdAt.toString(),
            updatedAt.toString()
        );
        return new TaskDefinition(
            task.id(), task.title(), task.summary(), task.goal(), task.notes(), task.inputDescription(),
            task.inputs(), task.outputDescription(), task.outputs(), task.assumptions(),
            task.steps(), task.validationCriteria(), createdAt, updatedAt
        );
    }

    public void delete(String id) {
        if (StringUtils.hasText(id)) {
            jdbcTemplate.update("delete from ai_task_definitions where id = ?", id);
        }
    }

    public Optional<TaskDraft> findDraft(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select conversation_id, status, planning_task, title, summary, goal, notes,
                       input_description, inputs_json, output_description, outputs_json,
                       assumptions_json, steps_json, validation_criteria_json, pending_questions_json,
                       pending_question_index, pre_planning_model, execution_model, created_task_id,
                       created_at, updated_at
                from ai_task_drafts
                where conversation_id = ?
                """,
            rs -> rs.next() ? Optional.of(draftFromRow(rs)) : Optional.empty(),
            conversationId
        );
    }

    public List<String> findDraftConversationIds() {
        return jdbcTemplate.queryForList(
            "select conversation_id from ai_task_drafts order by updated_at desc",
            String.class
        );
    }

    @Transactional
    public TaskDraft saveDraft(TaskDraft draft) {
        Instant createdAt = draft.createdAt() == null ? Instant.now() : draft.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into ai_task_drafts (
                    conversation_id, status, planning_task, title, summary, goal, notes, input_description,
                    inputs_json, output_description, outputs_json, assumptions_json,
                    steps_json, validation_criteria_json, pending_questions_json, pending_question_index,
                    pre_planning_model, execution_model, created_task_id, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(conversation_id) do update set
                    status = excluded.status,
                    planning_task = excluded.planning_task,
                    title = excluded.title,
                    summary = excluded.summary,
                    goal = excluded.goal,
                    notes = excluded.notes,
                    input_description = excluded.input_description,
                    inputs_json = excluded.inputs_json,
                    output_description = excluded.output_description,
                    outputs_json = excluded.outputs_json,
                    assumptions_json = excluded.assumptions_json,
                    steps_json = excluded.steps_json,
                    validation_criteria_json = excluded.validation_criteria_json,
                    pending_questions_json = excluded.pending_questions_json,
                    pending_question_index = excluded.pending_question_index,
                    pre_planning_model = excluded.pre_planning_model,
                    execution_model = excluded.execution_model,
                    created_task_id = excluded.created_task_id,
                    updated_at = excluded.updated_at
                """,
            draft.conversationId(),
            draft.status().name(),
            draft.planningTask(),
            draft.title(),
            draft.summary(),
            draft.goal(),
            draft.notes(),
            draft.inputDescription(),
            jsonOrNull(draft.inputs()),
            draft.outputDescription(),
            jsonOrNull(draft.outputs()),
            jsonOrNull(draft.assumptions()),
            jsonOrNull(draft.steps()),
            jsonOrNull(draft.validationCriteria()),
            jsonOrNull(draft.pendingQuestions()),
            draft.pendingQuestionIndex(),
            draft.prePlanningModel(),
            draft.executionModel(),
            draft.createdTaskId(),
            createdAt.toString(),
            updatedAt.toString()
        );
        return new TaskDraft(
            draft.conversationId(), draft.status(), draft.planningTask(), draft.title(), draft.summary(),
            draft.goal(), draft.notes(), draft.inputDescription(), draft.inputs(),
            draft.outputDescription(), draft.outputs(), draft.assumptions(), draft.steps(),
            draft.validationCriteria(), draft.pendingQuestions(), draft.pendingQuestionIndex(),
            draft.prePlanningModel(), draft.executionModel(), draft.createdTaskId(), createdAt, updatedAt
        );
    }

    public void deleteDraft(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            jdbcTemplate.update("delete from ai_task_drafts where conversation_id = ?", conversationId);
        }
    }

    public Optional<TaskRun> findRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select id, task_id, status, input_values_json, output_values_json, task_snapshot_json,
                       execution_evidence_json, validation_feedback_json, final_message, error_text,
                       created_at, updated_at, started_at, completed_at
                from ai_task_runs
                where id = ?
                """,
            rs -> rs.next() ? Optional.of(runFromRow(rs)) : Optional.empty(),
            runId
        );
    }

    public List<TaskRun> findRunsForTask(String taskId) {
        return jdbcTemplate.query(
            """
                select id, task_id, status, input_values_json, output_values_json, task_snapshot_json,
                       execution_evidence_json, validation_feedback_json, final_message, error_text,
                       created_at, updated_at, started_at, completed_at
                from ai_task_runs
                where task_id = ?
                order by created_at desc
                """,
            (rs, rowNum) -> runFromRow(rs),
            taskId
        );
    }

    @Transactional
    public TaskRun saveRun(TaskRun run) {
        Instant createdAt = run.createdAt() == null ? Instant.now() : run.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into ai_task_runs (
                    id, task_id, status, input_values_json, output_values_json, task_snapshot_json,
                    execution_evidence_json, validation_feedback_json, final_message, error_text,
                    created_at, updated_at, started_at, completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    status = excluded.status,
                    input_values_json = excluded.input_values_json,
                    output_values_json = excluded.output_values_json,
                    task_snapshot_json = excluded.task_snapshot_json,
                    execution_evidence_json = excluded.execution_evidence_json,
                    validation_feedback_json = excluded.validation_feedback_json,
                    final_message = excluded.final_message,
                    error_text = excluded.error_text,
                    updated_at = excluded.updated_at,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at
                """,
            run.id(),
            run.taskId(),
            run.status().name(),
            jsonOrNull(run.inputValues()),
            jsonOrNull(run.outputValues()),
            json(run.taskSnapshot()),
            jsonOrNull(run.executionEvidence()),
            jsonOrNull(run.validationFeedback()),
            run.finalMessage(),
            run.errorText(),
            createdAt.toString(),
            updatedAt.toString(),
            instant(run.startedAt()),
            instant(run.completedAt())
        );
        return new TaskRun(
            run.id(), run.taskId(), run.status(), run.inputValues(), run.outputValues(), run.taskSnapshot(),
            run.executionEvidence(), run.validationFeedback(), run.finalMessage(), run.errorText(),
            createdAt, updatedAt, run.startedAt(), run.completedAt()
        );
    }

    TaskDefinition definitionFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskDefinition(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("goal"),
            rs.getString("notes"),
            rs.getString("input_description"),
            read(rs.getString("inputs_json"), FIELD_LIST, List.of()),
            rs.getString("output_description"),
            read(rs.getString("outputs_json"), FIELD_LIST, List.of()),
            read(rs.getString("assumptions_json"), STRING_LIST, List.of()),
            read(rs.getString("steps_json"), STEP_LIST, List.of()),
            read(rs.getString("validation_criteria_json"), STRING_LIST, List.of()),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    private TaskDraft draftFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskDraft(
            rs.getString("conversation_id"),
            TaskDraftStatus.valueOf(rs.getString("status")),
            normalizePlanningTask(rs.getString("planning_task")),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("goal"),
            rs.getString("notes"),
            rs.getString("input_description"),
            read(rs.getString("inputs_json"), FIELD_LIST, List.of()),
            rs.getString("output_description"),
            read(rs.getString("outputs_json"), FIELD_LIST, List.of()),
            read(rs.getString("assumptions_json"), STRING_LIST, List.of()),
            read(rs.getString("steps_json"), STEP_LIST, List.of()),
            read(rs.getString("validation_criteria_json"), STRING_LIST, List.of()),
            read(rs.getString("pending_questions_json"), STRING_LIST, List.of()),
            rs.getInt("pending_question_index"),
            rs.getString("pre_planning_model"),
            rs.getString("execution_model"),
            rs.getString("created_task_id"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    private TaskRun runFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskRun(
            rs.getString("id"),
            rs.getString("task_id"),
            TaskRunStatus.valueOf(rs.getString("status")),
            read(rs.getString("input_values_json"), VALUE_MAP, Map.of()),
            read(rs.getString("output_values_json"), VALUE_MAP, Map.of()),
            readRequired(rs.getString("task_snapshot_json"), TaskDefinition.class),
            read(rs.getString("execution_evidence_json"), STRING_LIST, List.of()),
            read(rs.getString("validation_feedback_json"), STRING_LIST, List.of()),
            rs.getString("final_message"),
            rs.getString("error_text"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at")),
            parseInstant(rs.getString("started_at")),
            parseInstant(rs.getString("completed_at"))
        );
    }

    private <T> T read(String json, TypeReference<T> type, T defaultValue) {
        if (!StringUtils.hasText(json)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse task JSON", exception);
        }
    }

    private <T> T readRequired(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse task JSON", exception);
        }
    }

    private String jsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        return json(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize task JSON", exception);
        }
    }

    private String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Instant parseInstant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private String normalizePlanningTask(String value) {
        return "define_deliverables".equals(value) ? "define_outputs" : value;
    }
}
