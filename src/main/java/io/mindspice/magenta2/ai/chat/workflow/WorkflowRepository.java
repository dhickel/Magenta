package io.mindspice.magenta2.ai.chat.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Legacy repository for the retired ai_workflow_* tables. It is intentionally
 * not a Spring bean; production workflow persistence uses workflow_definitions
 * and workflow_runs in the orchestration workflow package.
 */
@Deprecated(forRemoval = true)
public class WorkflowRepository {
    private static final TypeReference<List<WorkflowStep>> STEP_LIST = new TypeReference<>() { };
    private static final TypeReference<List<WorkflowStepRun>> STEP_RUN_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> VALUE_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    public List<WorkflowDefinition> findAll() {
        return jdbcTemplate.query(
            "select id, title, summary, steps_json, created_at, updated_at from ai_workflow_definitions order by updated_at desc, title asc",
            (rs, rowNum) -> definitionFromRow(rs)
        );
    }

    public Optional<WorkflowDefinition> find(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select id, title, summary, steps_json, created_at, updated_at from ai_workflow_definitions where id = ?",
            rs -> rs.next() ? Optional.of(definitionFromRow(rs)) : Optional.empty(),
            id
        );
    }

    @Transactional
    public WorkflowDefinition save(WorkflowDefinition workflow) {
        Instant createdAt = workflow.createdAt() == null ? Instant.now() : workflow.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into ai_workflow_definitions (id, title, summary, steps_json, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    summary = excluded.summary,
                    steps_json = excluded.steps_json,
                    updated_at = excluded.updated_at
                """,
            workflow.id(), workflow.title(), workflow.summary(), json(workflow.steps()),
            createdAt.toString(), updatedAt.toString()
        );
        return new WorkflowDefinition(workflow.id(), workflow.title(), workflow.summary(), workflow.steps(), createdAt, updatedAt);
    }

    @Transactional
    public void delete(String id) {
        if (StringUtils.hasText(id)) {
            jdbcTemplate.update("delete from ai_workflow_runs where workflow_id = ?", id);
            jdbcTemplate.update("delete from ai_workflow_definitions where id = ?", id);
        }
    }

    public Optional<WorkflowRun> findRun(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select id, workflow_id, status, workflow_snapshot_json, step_runs_json, final_outputs_json,
                       final_message, error_text, created_at, updated_at, started_at, completed_at
                from ai_workflow_runs
                where id = ?
                """,
            rs -> rs.next() ? Optional.of(runFromRow(rs)) : Optional.empty(),
            id
        );
    }

    public List<WorkflowRun> findRunsForWorkflow(String workflowId) {
        return jdbcTemplate.query(
            """
                select id, workflow_id, status, workflow_snapshot_json, step_runs_json, final_outputs_json,
                       final_message, error_text, created_at, updated_at, started_at, completed_at
                from ai_workflow_runs
                where workflow_id = ?
                order by created_at desc
                """,
            (rs, rowNum) -> runFromRow(rs),
            workflowId
        );
    }

    @Transactional
    public WorkflowRun saveRun(WorkflowRun run) {
        Instant createdAt = run.createdAt() == null ? Instant.now() : run.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into ai_workflow_runs (
                    id, workflow_id, status, workflow_snapshot_json, step_runs_json, final_outputs_json,
                    final_message, error_text, created_at, updated_at, started_at, completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    status = excluded.status,
                    workflow_snapshot_json = excluded.workflow_snapshot_json,
                    step_runs_json = excluded.step_runs_json,
                    final_outputs_json = excluded.final_outputs_json,
                    final_message = excluded.final_message,
                    error_text = excluded.error_text,
                    updated_at = excluded.updated_at,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at
                """,
            run.id(), run.workflowId(), run.status().name(), json(run.workflowSnapshot()),
            jsonOrNull(run.stepRuns()), jsonOrNull(run.finalOutputs()), run.finalMessage(), run.errorText(),
            createdAt.toString(), updatedAt.toString(), instant(run.startedAt()), instant(run.completedAt())
        );
        return new WorkflowRun(run.id(), run.workflowId(), run.status(), run.workflowSnapshot(), run.stepRuns(),
            run.finalOutputs(), run.finalMessage(), run.errorText(), createdAt, updatedAt, run.startedAt(), run.completedAt());
    }

    private WorkflowDefinition definitionFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WorkflowDefinition(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("summary"),
            read(rs.getString("steps_json"), STEP_LIST, List.of()),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    private WorkflowRun runFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WorkflowRun(
            rs.getString("id"),
            rs.getString("workflow_id"),
            WorkflowRunStatus.valueOf(rs.getString("status")),
            readRequired(rs.getString("workflow_snapshot_json"), WorkflowDefinition.class),
            read(rs.getString("step_runs_json"), STEP_RUN_LIST, List.of()),
            read(rs.getString("final_outputs_json"), VALUE_MAP, Map.of()),
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
            throw new IllegalStateException("Failed to parse workflow JSON", exception);
        }
    }

    private <T> T readRequired(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse workflow JSON", exception);
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
            throw new IllegalStateException("Failed to serialize workflow JSON", exception);
        }
    }

    private String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Instant parseInstant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }
}
