package io.mindspice.magenta2.ai.orchestration.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
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
public class OrchestrationRuntimeRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OrchestrationRuntimeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.findAndRegisterModules();
        ensureSchema();
    }

    public OrchestrationJob saveJob(OrchestrationJob job) {
        Instant now = Instant.now();
        Instant createdAt = job.createdAt() == null ? now : job.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into orchestration_jobs (
                    id, owner_agent_id, title, summary, default_model, workspace_id, status, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    owner_agent_id = excluded.owner_agent_id,
                    title = excluded.title,
                    summary = excluded.summary,
                    default_model = excluded.default_model,
                    workspace_id = excluded.workspace_id,
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """,
            job.id(), job.ownerAgentId(), job.title(), job.summary(), job.defaultModel(), job.workspaceId(),
            job.status().name(), createdAt.toString(), updatedAt.toString()
        );
        return findJob(job.id()).orElseThrow();
    }

    public Optional<OrchestrationJob> findJob(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select * from orchestration_jobs where id = ?",
            rs -> rs.next() ? Optional.of(toJob(rs)) : Optional.empty(),
            id
        );
    }

    public List<OrchestrationJob> findJobsForAgent(String agentId) {
        return jdbcTemplate.query(
            "select * from orchestration_jobs where owner_agent_id = ? order by updated_at desc",
            (rs, rowNum) -> toJob(rs),
            agentId
        );
    }

    public OrchestrationJobItem saveJobItem(OrchestrationJobItem item) {
        Instant now = Instant.now();
        Instant createdAt = item.createdAt() == null ? now : item.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into orchestration_job_items (
                    id, job_id, item_order, item_type, task_id, workflow_id, model_override, priority,
                    retry_count, continue_on_failure, config_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    item_order = excluded.item_order,
                    item_type = excluded.item_type,
                    task_id = excluded.task_id,
                    workflow_id = excluded.workflow_id,
                    model_override = excluded.model_override,
                    priority = excluded.priority,
                    retry_count = excluded.retry_count,
                    continue_on_failure = excluded.continue_on_failure,
                    config_json = excluded.config_json,
                    updated_at = excluded.updated_at
                """,
            item.id(), item.jobId(), item.itemOrder(), item.itemType().name(), item.taskId(), item.workflowId(),
            item.modelOverride(), item.priority(), item.retryCount(), item.continueOnFailure() ? 1 : 0,
            jsonOrNull(item.config()), createdAt.toString(), updatedAt.toString()
        );
        return findJobItem(item.id()).orElseThrow();
    }

    public Optional<OrchestrationJobItem> findJobItem(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select * from orchestration_job_items where id = ?",
            rs -> rs.next() ? Optional.of(toJobItem(rs)) : Optional.empty(),
            id
        );
    }

    public List<OrchestrationJobItem> findJobItems(String jobId) {
        return jdbcTemplate.query(
            "select * from orchestration_job_items where job_id = ? order by item_order asc, created_at asc",
            (rs, rowNum) -> toJobItem(rs),
            jobId
        );
    }

    public WorkAssignment saveAssignment(WorkAssignment assignment) {
        Instant now = Instant.now();
        Instant createdAt = assignment.createdAt() == null ? now : assignment.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into work_assignments (
                    id, agent_id, job_id, job_item_id, assignment_type, priority, status, model_override,
                    workspace_id, current_item_index, checkpoint_json, input_json, output_json, evidence_json,
                    error_text, lease_owner, lease_expires_at, created_at, updated_at, started_at, completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    agent_id = excluded.agent_id,
                    job_id = excluded.job_id,
                    job_item_id = excluded.job_item_id,
                    assignment_type = excluded.assignment_type,
                    priority = excluded.priority,
                    status = excluded.status,
                    model_override = excluded.model_override,
                    workspace_id = excluded.workspace_id,
                    current_item_index = excluded.current_item_index,
                    checkpoint_json = excluded.checkpoint_json,
                    input_json = excluded.input_json,
                    output_json = excluded.output_json,
                    evidence_json = excluded.evidence_json,
                    error_text = excluded.error_text,
                    lease_owner = excluded.lease_owner,
                    lease_expires_at = excluded.lease_expires_at,
                    updated_at = excluded.updated_at,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at
                """,
            assignment.id(), assignment.agentId(), assignment.jobId(), assignment.jobItemId(),
            assignment.assignmentType().name(), assignment.priority(), assignment.status().name(),
            assignment.modelOverride(), assignment.workspaceId(), assignment.currentItemIndex(),
            jsonOrNull(assignment.checkpoint()), jsonOrNull(assignment.input()), jsonOrNull(assignment.output()),
            jsonOrNull(assignment.evidence()), assignment.errorText(), assignment.leaseOwner(),
            instant(assignment.leaseExpiresAt()), createdAt.toString(), updatedAt.toString(),
            instant(assignment.startedAt()), instant(assignment.completedAt())
        );
        return findAssignment(assignment.id()).orElseThrow();
    }

    public Optional<WorkAssignment> findAssignment(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select * from work_assignments where id = ?",
            rs -> rs.next() ? Optional.of(toAssignment(rs)) : Optional.empty(),
            id
        );
    }

    public List<WorkAssignment> findAssignmentsForAgent(String agentId) {
        return jdbcTemplate.query(
            "select * from work_assignments where agent_id = ? order by created_at desc",
            (rs, rowNum) -> toAssignment(rs),
            agentId
        );
    }

    public List<WorkAssignment> findAssignmentsForJob(String jobId) {
        return jdbcTemplate.query(
            "select * from work_assignments where job_id = ? order by created_at desc",
            (rs, rowNum) -> toAssignment(rs),
            jobId
        );
    }

    public List<WorkAssignment> findQueuedAssignments(int limit) {
        return jdbcTemplate.query(
            """
                select * from work_assignments
                where status = ?
                order by priority desc, created_at asc
                limit ?
                """,
            (rs, rowNum) -> toAssignment(rs),
            OrchestrationStatus.QUEUED.name(),
            limit
        );
    }

    @Transactional
    public Optional<WorkAssignment> acquireLease(String assignmentId, String leaseOwner, Instant leaseExpiresAt) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
            """
                update work_assignments
                set status = ?, lease_owner = ?, lease_expires_at = ?, started_at = coalesce(started_at, ?), updated_at = ?
                where id = ? and status in (?, ?) and (lease_expires_at is null or lease_expires_at <= ?)
                """,
            OrchestrationStatus.RUNNING.name(), leaseOwner, leaseExpiresAt.toString(), now.toString(), now.toString(),
            assignmentId, OrchestrationStatus.QUEUED.name(), OrchestrationStatus.INTERRUPTED.name(), now.toString()
        );
        return updated == 0 ? Optional.empty() : findAssignment(assignmentId);
    }

    public int markStaleRunningLeases(Instant now) {
        return jdbcTemplate.update(
            """
                update work_assignments
                set status = ?, lease_owner = null, lease_expires_at = null, updated_at = ?
                where status = ? and lease_expires_at is not null and lease_expires_at <= ?
                """,
            OrchestrationStatus.INTERRUPTED.name(), now.toString(), OrchestrationStatus.RUNNING.name(), now.toString()
        );
    }

    public boolean revertToQueued(String assignmentId, String leaseOwner) {
        int updated = jdbcTemplate.update(
            """
                update work_assignments
                set status = ?, lease_owner = null, lease_expires_at = null, updated_at = ?
                where id = ? and status = ? and lease_owner = ?
                """,
            OrchestrationStatus.QUEUED.name(), Instant.now().toString(),
            assignmentId, OrchestrationStatus.RUNNING.name(), leaseOwner
        );
        return updated == 1;
    }

    public int extendRunningLease(String assignmentId, String leaseOwner, Instant leaseExpiresAt) {
        Instant now = Instant.now();
        return jdbcTemplate.update(
            """
                update work_assignments
                set lease_expires_at = ?, updated_at = ?
                where id = ? and status = ? and lease_owner = ?
                """,
            leaseExpiresAt.toString(), now.toString(), assignmentId, OrchestrationStatus.RUNNING.name(), leaseOwner
        );
    }

    public InboxMessage saveInboxMessage(InboxMessage message) {
        Instant now = Instant.now();
        Instant createdAt = message.createdAt() == null ? now : message.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into agent_inbox_messages (
                    id, to_agent_id, from_id, message_type, body, metadata_json, read_flag,
                    handled_flag, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    read_flag = excluded.read_flag,
                    handled_flag = excluded.handled_flag,
                    updated_at = excluded.updated_at
                """,
            message.id(), message.toAgentId(), message.fromId(), message.messageType(), message.body(),
            jsonOrNull(message.metadata()), message.read() ? 1 : 0, message.handled() ? 1 : 0,
            createdAt.toString(), updatedAt.toString()
        );
        return findInboxMessage(message.id()).orElseThrow();
    }

    public Optional<InboxMessage> findInboxMessage(String id) {
        return jdbcTemplate.query(
            "select * from agent_inbox_messages where id = ?",
            rs -> rs.next() ? Optional.of(toInboxMessage(rs)) : Optional.empty(),
            id
        );
    }

    public List<InboxMessage> findInboxMessages(String agentId) {
        return jdbcTemplate.query(
            "select * from agent_inbox_messages where to_agent_id = ? order by created_at desc",
            (rs, rowNum) -> toInboxMessage(rs),
            agentId
        );
    }

    public AgentSchedule saveSchedule(AgentSchedule schedule) {
        Instant now = Instant.now();
        Instant createdAt = schedule.createdAt() == null ? now : schedule.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into agent_schedules (
                    id, agent_id, job_id, assignment_template_json, cron_expression, timezone,
                    enabled_flag, next_run_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    agent_id = excluded.agent_id,
                    job_id = excluded.job_id,
                    assignment_template_json = excluded.assignment_template_json,
                    cron_expression = excluded.cron_expression,
                    timezone = excluded.timezone,
                    enabled_flag = excluded.enabled_flag,
                    next_run_at = excluded.next_run_at,
                    updated_at = excluded.updated_at
                """,
            schedule.id(), schedule.agentId(), schedule.jobId(), jsonOrNull(schedule.assignmentTemplate()),
            schedule.cronExpression(), schedule.timezone(), schedule.enabled() ? 1 : 0,
            instant(schedule.nextRunAt()), createdAt.toString(), updatedAt.toString()
        );
        return findSchedule(schedule.id()).orElseThrow();
    }

    public Optional<AgentSchedule> findSchedule(String id) {
        return jdbcTemplate.query(
            "select * from agent_schedules where id = ?",
            rs -> rs.next() ? Optional.of(toSchedule(rs)) : Optional.empty(),
            id
        );
    }

    public List<AgentSchedule> findSchedulesForAgent(String agentId) {
        return jdbcTemplate.query(
            "select * from agent_schedules where agent_id = ? order by created_at desc",
            (rs, rowNum) -> toSchedule(rs),
            agentId
        );
    }

    public List<AgentSchedule> findDueSchedules(Instant now) {
        return jdbcTemplate.query(
            "select * from agent_schedules where enabled_flag = 1 and next_run_at is not null and next_run_at <= ? order by next_run_at asc",
            (rs, rowNum) -> toSchedule(rs),
            now.toString()
        );
    }

    public boolean createScheduleFiring(String id, String scheduleId, Instant dueAt, String assignmentId) {
        Instant now = Instant.now();
        int inserted = jdbcTemplate.update(
            """
                insert or ignore into schedule_firings (
                    id, schedule_id, due_at, assignment_id, created_at
                )
                values (?, ?, ?, ?, ?)
                """,
            id, scheduleId, dueAt.toString(), assignmentId, now.toString()
        );
        return inserted == 1;
    }

    public AgentEventReaction saveReaction(AgentEventReaction reaction) {
        Instant now = Instant.now();
        Instant createdAt = reaction.createdAt() == null ? now : reaction.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into agent_event_reactions (
                    id, agent_id, event_type, filter_json, action_type, assignment_template_json,
                    enabled_flag, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    agent_id = excluded.agent_id,
                    event_type = excluded.event_type,
                    filter_json = excluded.filter_json,
                    action_type = excluded.action_type,
                    assignment_template_json = excluded.assignment_template_json,
                    enabled_flag = excluded.enabled_flag,
                    updated_at = excluded.updated_at
                """,
            reaction.id(), reaction.agentId(), reaction.eventType().name(), jsonOrNull(reaction.filter()),
            reaction.actionType().name(), jsonOrNull(reaction.assignmentTemplate()), reaction.enabled() ? 1 : 0,
            createdAt.toString(), updatedAt.toString()
        );
        return findReaction(reaction.id()).orElseThrow();
    }

    public Optional<AgentEventReaction> findReaction(String id) {
        return jdbcTemplate.query(
            "select * from agent_event_reactions where id = ?",
            rs -> rs.next() ? Optional.of(toReaction(rs)) : Optional.empty(),
            id
        );
    }

    public List<AgentEventReaction> findReactionsForAgent(String agentId) {
        return jdbcTemplate.query(
            "select * from agent_event_reactions where agent_id = ? order by created_at desc",
            (rs, rowNum) -> toReaction(rs),
            agentId
        );
    }

    public List<AgentEventReaction> findEnabledReactions(EventType eventType) {
        return jdbcTemplate.query(
            "select * from agent_event_reactions where enabled_flag = 1 and event_type = ? order by created_at asc",
            (rs, rowNum) -> toReaction(rs),
            eventType.name()
        );
    }

    public OrchestrationEvent saveEvent(OrchestrationEvent event) {
        Instant createdAt = event.createdAt() == null ? Instant.now() : event.createdAt();
        jdbcTemplate.update(
            """
                insert into orchestration_events (
                    id, event_type, source_type, source_id, payload_json, created_at, handled_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    handled_at = excluded.handled_at
                """,
            event.id(), event.eventType().name(), event.sourceType(), event.sourceId(), jsonOrNull(event.payload()),
            createdAt.toString(), instant(event.handledAt())
        );
        return findEvent(event.id()).orElseThrow();
    }

    public Optional<OrchestrationEvent> findEvent(String id) {
        return jdbcTemplate.query(
            "select * from orchestration_events where id = ?",
            rs -> rs.next() ? Optional.of(toEvent(rs)) : Optional.empty(),
            id
        );
    }

    public List<OrchestrationEvent> findEventsForSource(String sourceType, String sourceId) {
        return jdbcTemplate.query(
            "select * from orchestration_events where source_type = ? and source_id = ? order by created_at desc",
            (rs, rowNum) -> toEvent(rs),
            sourceType,
            sourceId
        );
    }

    private OrchestrationJob toJob(ResultSet rs) throws SQLException {
        return new OrchestrationJob(
            rs.getString("id"), rs.getString("owner_agent_id"), rs.getString("title"), rs.getString("summary"),
            rs.getString("default_model"), rs.getString("workspace_id"),
            OrchestrationStatus.valueOf(rs.getString("status")), instantValue(rs.getString("created_at")),
            instantValue(rs.getString("updated_at"))
        );
    }

    private OrchestrationJobItem toJobItem(ResultSet rs) throws SQLException {
        return new OrchestrationJobItem(
            rs.getString("id"), rs.getString("job_id"), rs.getInt("item_order"),
            AssignmentType.valueOf(rs.getString("item_type")), rs.getString("task_id"), rs.getString("workflow_id"),
            rs.getString("model_override"), rs.getInt("priority"), rs.getInt("retry_count"),
            rs.getInt("continue_on_failure") == 1, map(rs.getString("config_json")),
            instantValue(rs.getString("created_at")), instantValue(rs.getString("updated_at"))
        );
    }

    private WorkAssignment toAssignment(ResultSet rs) throws SQLException {
        return new WorkAssignment(
            rs.getString("id"), rs.getString("agent_id"), rs.getString("job_id"), rs.getString("job_item_id"),
            AssignmentType.valueOf(rs.getString("assignment_type")), rs.getInt("priority"),
            OrchestrationStatus.valueOf(rs.getString("status")), rs.getString("model_override"),
            rs.getString("workspace_id"), rs.getInt("current_item_index"), map(rs.getString("checkpoint_json")),
            map(rs.getString("input_json")), map(rs.getString("output_json")), map(rs.getString("evidence_json")),
            rs.getString("error_text"), rs.getString("lease_owner"), instantValue(rs.getString("lease_expires_at")),
            instantValue(rs.getString("created_at")), instantValue(rs.getString("updated_at")),
            instantValue(rs.getString("started_at")), instantValue(rs.getString("completed_at"))
        );
    }

    private InboxMessage toInboxMessage(ResultSet rs) throws SQLException {
        return new InboxMessage(
            rs.getString("id"), rs.getString("to_agent_id"), rs.getString("from_id"),
            rs.getString("message_type"), rs.getString("body"), map(rs.getString("metadata_json")),
            rs.getInt("read_flag") == 1, rs.getInt("handled_flag") == 1,
            instantValue(rs.getString("created_at")), instantValue(rs.getString("updated_at"))
        );
    }

    private AgentSchedule toSchedule(ResultSet rs) throws SQLException {
        return new AgentSchedule(
            rs.getString("id"), rs.getString("agent_id"), rs.getString("job_id"),
            map(rs.getString("assignment_template_json")), rs.getString("cron_expression"), rs.getString("timezone"),
            rs.getInt("enabled_flag") == 1, instantValue(rs.getString("next_run_at")),
            instantValue(rs.getString("created_at")), instantValue(rs.getString("updated_at"))
        );
    }

    private AgentEventReaction toReaction(ResultSet rs) throws SQLException {
        return new AgentEventReaction(
            rs.getString("id"), rs.getString("agent_id"), EventType.valueOf(rs.getString("event_type")),
            map(rs.getString("filter_json")), ReactionActionType.valueOf(rs.getString("action_type")),
            map(rs.getString("assignment_template_json")), rs.getInt("enabled_flag") == 1,
            instantValue(rs.getString("created_at")), instantValue(rs.getString("updated_at"))
        );
    }

    private OrchestrationEvent toEvent(ResultSet rs) throws SQLException {
        return new OrchestrationEvent(
            rs.getString("id"), EventType.valueOf(rs.getString("event_type")), rs.getString("source_type"),
            rs.getString("source_id"), map(rs.getString("payload_json")), instantValue(rs.getString("created_at")),
            instantValue(rs.getString("handled_at"))
        );
    }

    private Map<String, Object> map(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse orchestration JSON", exception);
        }
    }

    private String jsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize orchestration JSON", exception);
        }
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private Instant instantValue(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists orchestration_jobs (
                id text primary key,
                owner_agent_id text not null,
                title text not null,
                summary text,
                default_model text,
                workspace_id text,
                status text not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists orchestration_job_items (
                id text primary key,
                job_id text not null,
                item_order integer not null,
                item_type text not null,
                task_id text,
                workflow_id text,
                model_override text,
                priority integer not null,
                retry_count integer not null default 0,
                continue_on_failure integer not null default 0,
                config_json text,
                created_at text not null,
                updated_at text not null,
                foreign key(job_id) references orchestration_jobs(id)
            )
            """);
        java.util.List<String> jobItemColumns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('orchestration_job_items')",
            String.class
        );
        if (!jobItemColumns.contains("retry_count")) {
            jdbcTemplate.execute("alter table orchestration_job_items add column retry_count integer not null default 0");
        }
        if (!jobItemColumns.contains("continue_on_failure")) {
            jdbcTemplate.execute("alter table orchestration_job_items add column continue_on_failure integer not null default 0");
        }
        jdbcTemplate.execute("""
            create table if not exists work_assignments (
                id text primary key,
                agent_id text not null,
                job_id text,
                job_item_id text,
                assignment_type text not null,
                priority integer not null,
                status text not null,
                model_override text,
                workspace_id text,
                current_item_index integer not null,
                checkpoint_json text,
                input_json text,
                output_json text,
                evidence_json text,
                error_text text,
                lease_owner text,
                lease_expires_at text,
                created_at text not null,
                updated_at text not null,
                started_at text,
                completed_at text
            )
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_work_assignments_queue
                on work_assignments(status, priority, created_at)
            """);
        jdbcTemplate.execute("""
            create table if not exists agent_inbox_messages (
                id text primary key,
                to_agent_id text not null,
                from_id text,
                message_type text not null,
                body text,
                metadata_json text,
                read_flag integer not null,
                handled_flag integer not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists agent_schedules (
                id text primary key,
                agent_id text not null,
                job_id text,
                assignment_template_json text,
                cron_expression text not null,
                timezone text not null,
                enabled_flag integer not null,
                next_run_at text,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists schedule_firings (
                id text primary key,
                schedule_id text not null,
                due_at text not null,
                assignment_id text not null,
                created_at text not null,
                unique(schedule_id, due_at),
                foreign key(schedule_id) references agent_schedules(id)
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists agent_event_reactions (
                id text primary key,
                agent_id text not null,
                event_type text not null,
                filter_json text,
                action_type text not null,
                assignment_template_json text,
                enabled_flag integer not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists orchestration_events (
                id text primary key,
                event_type text not null,
                source_type text,
                source_id text,
                payload_json text,
                created_at text not null,
                handled_at text
            )
            """);
    }
}
