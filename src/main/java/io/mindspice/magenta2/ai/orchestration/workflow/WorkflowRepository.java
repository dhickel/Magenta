package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Owns persistence for {@link WorkflowDefinition}, {@link WorkflowRun},
 * {@link WorkflowNodeRun}, and {@link InboxMessage}.
 *
 * <p>JSON columns store nested node lists, route lists, bindings, and value maps.
 * Uses Jackson with Java time modules for consistent serialization.
 */
@Repository("orchestrationWorkflowRepository")
public class WorkflowRepository {
    private static final TypeReference<List<WorkflowNode>> NODE_LIST = new TypeReference<>() { };
    private static final TypeReference<List<WorkflowRoute>> ROUTE_LIST = new TypeReference<>() { };
    private static final TypeReference<List<WorkflowNodeRun>> NODE_RUN_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> VALUE_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.findAndRegisterModules();
        ensureTables();
    }

    private void ensureTables() {
        // Tables are created via schema.sql bootstrap.
        // This is a no-op safety net; idempotent create-if-not-exists.
        jdbcTemplate.execute("""
            create table if not exists workflow_definitions (
                id text primary key,
                title text not null,
                summary text,
                nodes_json text not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        // Phase 04: add graph columns if not present (migration for existing DBs).
        // Early workflow_definitions tables used steps_json; keep those rows readable
        // as empty graph drafts instead of failing the whole UI.
        try {
            jdbcTemplate.execute("alter table workflow_definitions add column nodes_json text not null default '[]'");
        } catch (Exception ignored) {
            // Column already exists
        }
        try {
            jdbcTemplate.execute("alter table workflow_definitions add column routes_json text not null default '[]'");
        } catch (Exception ignored) {
            // Column already exists
        }
        jdbcTemplate.execute("""
            create table if not exists workflow_runs (
                id text primary key,
                workflow_id text not null,
                status text not null,
                current_node_index integer not null default 0,
                node_runs_json text not null,
                workspace_path text,
                output_dir text,
                workflow_snapshot_json text not null,
                final_message text,
                error_text text,
                created_at text not null,
                updated_at text not null,
                started_at text,
                completed_at text,
                foreign key (workflow_id) references workflow_definitions(id) on delete cascade
            )
            """);
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
                completed_at text
            )
            """);
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
    }

    // ════════════════════════════════════════════════════════════════
    //  WorkflowDefinition
    // ════════════════════════════════════════════════════════════════

    public Optional<WorkflowDefinition> findDefinition(String id) {
        if (!StringUtils.hasText(id)) return Optional.empty();
        return jdbcTemplate.query(
            "select id, title, summary, nodes_json, routes_json, created_at, updated_at from workflow_definitions where id = ?",
            rs -> rs.next() ? Optional.of(definitionFromRow(rs)) : Optional.empty(),
            id
        );
    }

    public List<WorkflowDefinition> findAllDefinitions() {
        return jdbcTemplate.query(
            "select id, title, summary, nodes_json, routes_json, created_at, updated_at from workflow_definitions order by updated_at desc, title asc",
            (rs, rowNum) -> definitionFromRow(rs)
        );
    }

    @Transactional
    public WorkflowDefinition saveDefinition(WorkflowDefinition definition) {
        Instant createdAt = definition.createdAt() == null ? Instant.now() : definition.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into workflow_definitions (id, title, summary, nodes_json, routes_json, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    summary = excluded.summary,
                    nodes_json = excluded.nodes_json,
                    routes_json = excluded.routes_json,
                    updated_at = excluded.updated_at
                """,
            definition.id(), definition.title(), definition.summary(),
            json(definition.nodes()), json(definition.routes()),
            createdAt.toString(), updatedAt.toString()
        );
        return new WorkflowDefinition(definition.id(), definition.title(), definition.summary(),
            definition.nodes(), definition.routes(), createdAt, updatedAt);
    }

    @Transactional
    public void deleteDefinition(String id) {
        if (StringUtils.hasText(id)) {
            jdbcTemplate.update("delete from workflow_runs where workflow_id = ?", id);
            jdbcTemplate.update("delete from workflow_definitions where id = ?", id);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  WorkflowRun
    // ════════════════════════════════════════════════════════════════

    public Optional<WorkflowRun> findRun(String runId) {
        if (!StringUtils.hasText(runId)) return Optional.empty();
        return jdbcTemplate.query(
            """
                select id, workflow_id, status, current_node_index, node_runs_json,
                       workspace_path, output_dir, workflow_snapshot_json,
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
                select id, workflow_id, status, current_node_index, node_runs_json,
                       workspace_path, output_dir, workflow_snapshot_json,
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
                    id, workflow_id, status, current_node_index, node_runs_json,
                    workspace_path, output_dir, workflow_snapshot_json,
                    final_message, error_text,
                    created_at, updated_at, started_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    status = excluded.status,
                    current_node_index = excluded.current_node_index,
                    node_runs_json = excluded.node_runs_json,
                    workspace_path = excluded.workspace_path,
                    output_dir = excluded.output_dir,
                    workflow_snapshot_json = excluded.workflow_snapshot_json,
                    final_message = excluded.final_message,
                    error_text = excluded.error_text,
                    updated_at = excluded.updated_at,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at
                """,
            run.id(), run.workflowId(), run.status().wireName(), run.currentNodeIndex(),
            json(run.nodeRuns()), run.workspacePath(), run.outputDir(),
            json(run.workflowSnapshot()), run.finalMessage(), run.errorText(),
            createdAt.toString(), updatedAt.toString(),
            instant(run.startedAt()), instant(run.completedAt())
        );
        // Also save individual node runs
        saveNodeRuns(run.id(), run.nodeRuns());
        return new WorkflowRun(run.id(), run.workflowId(), run.status(), run.currentNodeIndex(),
            run.nodeRuns(), run.workspacePath(), run.outputDir(), run.workflowSnapshot(),
            run.finalMessage(), run.errorText(), createdAt, updatedAt, run.startedAt(), run.completedAt());
    }

    // ════════════════════════════════════════════════════════════════
    //  WorkflowNodeRun
    // ════════════════════════════════════════════════════════════════

    private void saveNodeRuns(String workflowRunId, List<WorkflowNodeRun> nodeRuns) {
        // Delete existing and re-insert
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

    // ════════════════════════════════════════════════════════════════
    //  InboxMessage
    // ════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════
    //  Row mapping helpers
    // ════════════════════════════════════════════════════════════════

    private WorkflowDefinition definitionFromRow(ResultSet rs) throws SQLException {
        return new WorkflowDefinition(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("summary"),
            parseNodeList(rs.getString("nodes_json")),
            parseRouteList(rs.getString("routes_json")),
            parseInstant(rs.getString("created_at")),
            parseInstant(rs.getString("updated_at"))
        );
    }

    private WorkflowRun runFromRow(ResultSet rs) throws SQLException {
        return new WorkflowRun(
            rs.getString("id"),
            rs.getString("workflow_id"),
            WorkflowRunStatus.fromWireName(rs.getString("status")),
            rs.getInt("current_node_index"),
            parseNodeRunList(rs.getString("node_runs_json")),
            rs.getString("workspace_path"),
            rs.getString("output_dir"),
            parseDefinition(rs.getString("workflow_snapshot_json")),
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

    // ════════════════════════════════════════════════════════════════
    //  JSON helpers
    // ════════════════════════════════════════════════════════════════

    private String json(Object value) {
        if (value == null) return "[]";
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
