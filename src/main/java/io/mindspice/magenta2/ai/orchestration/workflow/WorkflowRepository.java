package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence for workflow definitions, runs, node-runs, and inbox messages.
 */
@Repository("orchestrationWorkflowRepository")
public class WorkflowRepository {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRepository.class);
    private static final TypeReference<List<WorkflowNode>> NODE_LIST = new TypeReference<>() { };
    private static final TypeReference<List<WorkflowRoute>> ROUTE_LIST = new TypeReference<>() { };
    private static final TypeReference<List<WorkflowNodeRun>> NODE_RUN_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> VALUE_MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.findAndRegisterModules();
        ensureTables();
    }

    private void ensureTables() {
        jdbcTemplate.execute("""
            create table if not exists workflow_definitions (
                id text primary key,
                schema_version integer not null default 2,
                title text not null,
                summary text,
                max_concurrency integer not null default 4,
                nodes_json text not null,
                routes_json text not null default '[]',
                ui_layout_json text not null default '{}',
                created_at text not null,
                updated_at text not null
            )
            """);

        // Migration safety for older definitions table
        addColumnIfMissing("workflow_definitions", "schema_version", "integer not null default 2");
        addColumnIfMissing("workflow_definitions", "max_concurrency", "integer not null default 4");
        addColumnIfMissing("workflow_definitions", "ui_layout_json", "text not null default '{}'");
        addColumnIfMissing("workflow_definitions", "nodes_json", "text not null default '[]'");
        addColumnIfMissing("workflow_definitions", "routes_json", "text not null default '[]'");

        jdbcTemplate.execute("""
            create table if not exists workflow_runs (
                id text primary key,
                workflow_id text not null,
                run_display_name text,
                status text not null,
                current_node_index integer not null default 0,
                node_runs_json text not null,
                workspace_path text,
                output_dir text,
                agent_id text,
                job_id text,
                job_assignment_id text,
                job_run_id text,
                project_id text,
                workspace_id text,
                run_type text,
                workflow_snapshot_json text not null,
                final_outputs_json text not null default '{}',
                artifact_ids_json text not null default '[]',
                final_message text,
                error_text text,
                created_at text not null,
                updated_at text not null,
                started_at text,
                completed_at text,
                foreign key (workflow_id) references workflow_definitions(id) on delete cascade
            )
            """);
        addColumnIfMissing("workflow_runs", "final_outputs_json", "text not null default '{}'");
        addColumnIfMissing("workflow_runs", "artifact_ids_json", "text not null default '[]'");
        addColumnIfMissing("workflow_runs", "current_node_index", "integer not null default 0");
        addColumnIfMissing("workflow_runs", "node_runs_json", "text not null default '[]'");
        addColumnIfMissing("workflow_runs", "workspace_path", "text");
        addColumnIfMissing("workflow_runs", "output_dir", "text");
        addColumnIfMissing("workflow_runs", "agent_id", "text");
        addColumnIfMissing("workflow_runs", "job_id", "text");
        addColumnIfMissing("workflow_runs", "job_assignment_id", "text");
        addColumnIfMissing("workflow_runs", "job_run_id", "text");
        addColumnIfMissing("workflow_runs", "project_id", "text");
        addColumnIfMissing("workflow_runs", "workspace_id", "text");
        addColumnIfMissing("workflow_runs", "run_type", "text");
        addColumnIfMissing("workflow_runs", "workflow_snapshot_json", "text");
        addColumnIfMissing("workflow_runs", "final_message", "text");
        addColumnIfMissing("workflow_runs", "error_text", "text");
        addColumnIfMissing("workflow_runs", "updated_at", "text");
        addColumnIfMissing("workflow_runs", "started_at", "text");
        addColumnIfMissing("workflow_runs", "completed_at", "text");
        addColumnIfMissing("workflow_runs", "run_display_name", "text");
        jdbcTemplate.update("update workflow_runs set updated_at = created_at where updated_at is null");

        jdbcTemplate.execute("""
            create table if not exists workflow_node_runs (
                id text primary key,
                workflow_run_id text not null,
                node_key text not null,
                node_type text not null,
                node_index integer not null,
                status text not null,
                input_values_json text not null,
                output_values_json text not null,
                started_at text,
                completed_at text,
                foreign key (workflow_run_id) references workflow_runs(id) on delete cascade
            )
            """);

        // Workflow-owned inbox table for workflow/user approvals and run-output messages.
        jdbcTemplate.execute("""
            create table if not exists inbox_messages (
                id text primary key,
                to_type text not null,
                to_id text,
                from_id text,
                message_type text not null,
                body text,
                metadata_json text,
                response_json text,
                responded_at text,
                handled_at text,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_inbox_messages_to
                on inbox_messages (to_type, to_id, created_at desc)
            """);
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        if (hasColumn(tableName, columnName)) {
            return;
        }
        String sql = "alter table " + tableName + " add column " + columnName + " " + columnDefinition;
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException e) {
            if (hasColumn(tableName, columnName)) {
                log.warn("Workflow schema migration already applied while adding {}.{}", tableName, columnName, e);
                return;
            }
            throw new IllegalStateException(
                "Failed to migrate workflow schema: add column " + tableName + "." + columnName,
                e
            );
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "select count(*) from pragma_table_info('" + tableName + "') where name = ?",
                Integer.class,
                columnName
            );
            return count != null && count > 0;
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                "Failed to inspect workflow schema column " + tableName + "." + columnName,
                e
            );
        }
    }

    public Optional<WorkflowDefinition> findDefinition(String id) {
        if (!StringUtils.hasText(id)) return Optional.empty();
        return jdbcTemplate.query(
            "select id, schema_version, title, summary, max_concurrency, nodes_json, routes_json, ui_layout_json, created_at, updated_at from workflow_definitions where id = ?",
            rs -> rs.next() ? Optional.of(definitionFromRow(rs)) : Optional.empty(),
            id
        );
    }

    public List<WorkflowDefinition> findAllDefinitions() {
        return jdbcTemplate.query(
            "select id, schema_version, title, summary, max_concurrency, nodes_json, routes_json, ui_layout_json, created_at, updated_at from workflow_definitions order by updated_at desc, title asc",
            (rs, rowNum) -> definitionFromRow(rs)
        );
    }

    @Transactional
    public WorkflowDefinition saveDefinition(WorkflowDefinition definition) {
        Instant createdAt = definition.createdAt() == null ? Instant.now() : definition.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into workflow_definitions (
                    id, schema_version, title, summary, max_concurrency,
                    nodes_json, routes_json, ui_layout_json,
                    created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    schema_version = excluded.schema_version,
                    title = excluded.title,
                    summary = excluded.summary,
                    max_concurrency = excluded.max_concurrency,
                    nodes_json = excluded.nodes_json,
                    routes_json = excluded.routes_json,
                    ui_layout_json = excluded.ui_layout_json,
                    updated_at = excluded.updated_at
                """,
            definition.id(), definition.schemaVersion(), definition.title(), definition.summary(),
            definition.maxConcurrency(), json(definition.nodes()), json(definition.routes()),
            json(definition.uiLayout()), createdAt.toString(), updatedAt.toString()
        );
        return new WorkflowDefinition(definition.id(), definition.schemaVersion(), definition.title(),
            definition.summary(), definition.maxConcurrency(), definition.nodes(), definition.routes(),
            definition.uiLayout(), createdAt, updatedAt);
    }

    @Transactional
    public void deleteDefinition(String id) {
        if (StringUtils.hasText(id)) {
            jdbcTemplate.update("delete from workflow_runs where workflow_id = ?", id);
            jdbcTemplate.update("delete from workflow_definitions where id = ?", id);
        }
    }

    public Optional<WorkflowRun> findRun(String runId) {
        if (!StringUtils.hasText(runId)) return Optional.empty();
        return jdbcTemplate.query(
            """
                select id, workflow_id, run_display_name, status, current_node_index, node_runs_json,
                       workspace_path, output_dir,
                       agent_id, job_id, job_assignment_id, job_run_id, project_id, workspace_id, run_type,
                       workflow_snapshot_json,
                       final_outputs_json, artifact_ids_json,
                       final_message, error_text,
                       created_at, updated_at, started_at, completed_at
                from workflow_runs where id = ?
                """,
            rs -> rs.next() ? Optional.of(runFromRow(rs)) : Optional.empty(),
            runId
        );
    }

    public List<WorkflowRun> findRunsByWorkflowId(String workflowId) {
        return jdbcTemplate.query(
            """
                select id, workflow_id, run_display_name, status, current_node_index, node_runs_json,
                       workspace_path, output_dir,
                       agent_id, job_id, job_assignment_id, job_run_id, project_id, workspace_id, run_type,
                       workflow_snapshot_json,
                       final_outputs_json, artifact_ids_json,
                       final_message, error_text,
                       created_at, updated_at, started_at, completed_at
                from workflow_runs where workflow_id = ? order by created_at desc
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
                insert into workflow_runs (
                    id, workflow_id, run_display_name, status, current_node_index, node_runs_json,
                    workspace_path, output_dir,
                    agent_id, job_id, job_assignment_id, job_run_id, project_id, workspace_id, run_type,
                    workflow_snapshot_json,
                    final_outputs_json, artifact_ids_json,
                    final_message, error_text,
                    created_at, updated_at, started_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    run_display_name = excluded.run_display_name,
                    status = excluded.status,
                    current_node_index = excluded.current_node_index,
                    node_runs_json = excluded.node_runs_json,
                    workspace_path = excluded.workspace_path,
                    output_dir = excluded.output_dir,
                    agent_id = excluded.agent_id,
                    job_id = excluded.job_id,
                    job_assignment_id = excluded.job_assignment_id,
                    job_run_id = excluded.job_run_id,
                    project_id = excluded.project_id,
                    workspace_id = excluded.workspace_id,
                    run_type = excluded.run_type,
                    workflow_snapshot_json = excluded.workflow_snapshot_json,
                    final_outputs_json = excluded.final_outputs_json,
                    artifact_ids_json = excluded.artifact_ids_json,
                    final_message = excluded.final_message,
                    error_text = excluded.error_text,
                    updated_at = excluded.updated_at,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at
                """,
            run.id(), run.workflowId(), run.runDisplayName(), run.status().wireName(), run.currentNodeIndex(),
            json(run.nodeRuns()), run.workspacePath(), run.outputDir(),
            run.agentId(), run.jobId(), run.jobAssignmentId(), run.jobRunId(), run.projectId(), run.workspaceId(), run.runType(),
            json(run.workflowSnapshot()),
            json(run.finalOutputs()), json(run.artifactIds()),
            run.finalMessage(), run.errorText(),
            createdAt.toString(), updatedAt.toString(),
            instant(run.startedAt()), instant(run.completedAt())
        );
        saveNodeRuns(run.id(), run.nodeRuns());
        return new WorkflowRun(run.id(), run.workflowId(), run.runDisplayName(), run.status(), run.currentNodeIndex(),
            run.nodeRuns(), run.workspacePath(), run.outputDir(),
            run.agentId(), run.jobId(), run.jobAssignmentId(), run.jobRunId(), run.projectId(), run.workspaceId(), run.runType(),
            run.workflowSnapshot(),
            run.finalOutputs(), run.artifactIds(), run.finalMessage(), run.errorText(),
            createdAt, updatedAt, run.startedAt(), run.completedAt());
    }

    private void saveNodeRuns(String workflowRunId, List<WorkflowNodeRun> nodeRuns) {
        jdbcTemplate.update("delete from workflow_node_runs where workflow_run_id = ?", workflowRunId);
        for (int i = 0; i < nodeRuns.size(); i++) {
            WorkflowNodeRun nr = nodeRuns.get(i);
            jdbcTemplate.update(
                """
                    insert into workflow_node_runs (
                        id, workflow_run_id, node_key, node_type, node_index,
                        status, input_values_json, output_values_json,
                        started_at, completed_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                workflowRunId + "-" + i,
                workflowRunId,
                nr.nodeKey(),
                nr.type().wireName(),
                i,
                nr.status().name(),
                json(nr.inputValues()),
                json(nr.outputValues()),
                instant(nr.startedAt()),
                instant(nr.completedAt())
            );
        }
    }

    public List<WorkflowNodeRun> findNodeRuns(String workflowRunId) {
        return jdbcTemplate.query(
            """
                select node_key, node_type, status, input_values_json, output_values_json,
                       started_at, completed_at
                from workflow_node_runs
                where workflow_run_id = ? order by node_index
                """,
            (rs, rowNum) -> nodeRunFromRow(rs),
            workflowRunId
        );
    }

    public Optional<InboxMessage> findInboxMessage(String messageId) {
        if (!StringUtils.hasText(messageId)) return Optional.empty();
        return jdbcTemplate.query(
            """
                select id, to_type, to_id, from_id, message_type, body, metadata_json,
                       response_json, responded_at, handled_at, created_at, updated_at
                from inbox_messages where id = ?
                """,
            rs -> rs.next() ? Optional.of(inboxFromRow(rs)) : Optional.empty(),
            messageId
        );
    }

    public List<InboxMessage> findInboxByRecipient(InboxMessageToType toType, String toId) {
        String sql;
        Object[] args;
        if (toType == InboxMessageToType.USER) {
            sql = """
                select id, to_type, to_id, from_id, message_type, body, metadata_json,
                       response_json, responded_at, handled_at, created_at, updated_at
                from inbox_messages where to_type = ? order by created_at desc
                """;
            args = new Object[]{toType.wireName()};
        } else {
            sql = """
                select id, to_type, to_id, from_id, message_type, body, metadata_json,
                       response_json, responded_at, handled_at, created_at, updated_at
                from inbox_messages where to_type = ? and to_id = ? order by created_at desc
                """;
            args = new Object[]{toType.wireName(), toId};
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> inboxFromRow(rs), args);
    }

    public Optional<InboxMessage> findWaitingApprovalMessage(String workflowRunId, int nodeIndex) {
        return jdbcTemplate.query(
            """
                select id, to_type, to_id, from_id, message_type, body, metadata_json,
                       response_json, responded_at, handled_at, created_at, updated_at
                from inbox_messages
                where metadata_json like ?
                order by created_at desc
                limit 1
                """,
            rs -> rs.next() ? Optional.of(inboxFromRow(rs)) : Optional.empty(),
            "%\"workflowRunId\":\"" + workflowRunId + "\"%"
        );
    }

    @Transactional
    public InboxMessage saveInboxMessage(InboxMessage message) {
        Instant createdAt = message.createdAt() == null ? Instant.now() : message.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into inbox_messages (
                    id, to_type, to_id, from_id, message_type, body, metadata_json,
                    response_json, responded_at, handled_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    response_json = excluded.response_json,
                    responded_at = excluded.responded_at,
                    handled_at = excluded.handled_at,
                    updated_at = excluded.updated_at
                """,
            message.id(), message.toType().wireName(), message.toId(), message.fromId(),
            message.messageType().wireName(), message.body(), message.metadataJson(),
            message.responseJson(), instant(message.respondedAt()), instant(message.handledAt()),
            createdAt.toString(), updatedAt.toString()
        );
        return new InboxMessage(message.id(), message.toType(), message.toId(), message.fromId(),
            message.messageType(), message.body(), message.metadataJson(),
            message.responseJson(), message.respondedAt(), message.handledAt(),
            createdAt, updatedAt);
    }

    private WorkflowDefinition definitionFromRow(ResultSet rs) throws SQLException {
        return new WorkflowDefinition(
            rs.getString("id"),
            rs.getInt("schema_version"),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getInt("max_concurrency"),
            parseNodeList(rs.getString("nodes_json")),
            parseRouteList(rs.getString("routes_json")),
            parseValueMap(rs.getString("ui_layout_json")),
            parseInstant(rs.getString("created_at")),
            parseInstant(rs.getString("updated_at"))
        );
    }

    private WorkflowRun runFromRow(ResultSet rs) throws SQLException {
        return new WorkflowRun(
            rs.getString("id"),
            rs.getString("workflow_id"),
            rs.getString("run_display_name"),
            WorkflowRunStatus.fromWireName(rs.getString("status")),
            rs.getInt("current_node_index"),
            parseNodeRunList(rs.getString("node_runs_json")),
            rs.getString("workspace_path"),
            rs.getString("output_dir"),
            rs.getString("agent_id"),
            rs.getString("job_id"),
            rs.getString("job_assignment_id"),
            rs.getString("job_run_id"),
            rs.getString("project_id"),
            rs.getString("workspace_id"),
            rs.getString("run_type"),
            parseDefinition(rs.getString("workflow_snapshot_json")),
            parseValueMap(rs.getString("final_outputs_json")),
            parseStringList(rs.getString("artifact_ids_json")),
            rs.getString("final_message"),
            rs.getString("error_text"),
            parseInstant(rs.getString("created_at")),
            parseInstant(rs.getString("updated_at")),
            parseInstant(rs.getString("started_at")),
            parseInstant(rs.getString("completed_at"))
        );
    }

    private WorkflowNodeRun nodeRunFromRow(ResultSet rs) throws SQLException {
        return new WorkflowNodeRun(
            rs.getString("node_key"),
            WorkflowNodeType.fromWireName(rs.getString("node_type")),
            WorkflowNodeRunStatus.valueOf(rs.getString("status")),
            parseValueMap(rs.getString("input_values_json")),
            parseValueMap(rs.getString("output_values_json")),
            List.of(),
            parseInstant(rs.getString("started_at")),
            parseInstant(rs.getString("completed_at"))
        );
    }

    private InboxMessage inboxFromRow(ResultSet rs) throws SQLException {
        return new InboxMessage(
            rs.getString("id"),
            InboxMessageToType.fromWireName(rs.getString("to_type")),
            rs.getString("to_id"),
            rs.getString("from_id"),
            InboxMessageType.fromWireName(rs.getString("message_type")),
            rs.getString("body"),
            rs.getString("metadata_json"),
            rs.getString("response_json"),
            parseInstant(rs.getString("responded_at")),
            parseInstant(rs.getString("handled_at")),
            parseInstant(rs.getString("created_at")),
            parseInstant(rs.getString("updated_at"))
        );
    }

    private String json(Object value) {
        if (value == null) return "null";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON", e);
        }
    }

    private List<WorkflowNode> parseNodeList(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) return List.of();
        try {
            return objectMapper.readValue(json, NODE_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse node list JSON", e);
        }
    }

    private List<WorkflowRoute> parseRouteList(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) return List.of();
        try {
            return objectMapper.readValue(json, ROUTE_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse route list JSON", e);
        }
    }

    private List<WorkflowNodeRun> parseNodeRunList(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) return List.of();
        try {
            return objectMapper.readValue(json, NODE_RUN_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse node run list JSON", e);
        }
    }

    private WorkflowDefinition parseDefinition(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) return null;
        try {
            return objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse definition JSON", e);
        }
    }

    private Map<String, Object> parseValueMap(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) return Map.of();
        try {
            return objectMapper.readValue(json, VALUE_MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse value map JSON", e);
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json) || "null".equals(json)) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse string list JSON", e);
        }
    }

    private Instant parseInstant(String text) {
        if (!StringUtils.hasText(text)) return null;
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
