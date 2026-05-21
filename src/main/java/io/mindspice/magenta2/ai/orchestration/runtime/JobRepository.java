package io.mindspice.magenta2.ai.orchestration.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<JobWorkItem>> ITEM_LIST = new TypeReference<>() {};
    private static final TypeReference<List<JobWorkItemRun>> ITEM_RUN_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> VALUE_MAP = new TypeReference<>() {};

    public JobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        ensureTables();
    }

    // ── JobDefinition ──

    public Optional<JobDefinition> findDefinition(String id) {
        if (!StringUtils.hasText(id)) return Optional.empty();
        return jdbcTemplate.query(
            "select * from job_definitions where id = ?",
            rs -> rs.next() ? Optional.of(definitionFromRow(rs)) : Optional.empty(),
            id
        );
    }

    public List<JobDefinition> findAllDefinitions() {
        return jdbcTemplate.query(
            "select * from job_definitions order by updated_at desc, title asc",
            (rs, rowNum) -> definitionFromRow(rs)
        );
    }

    public List<JobDefinition> findDefinitions(String agentId, String projectId, String status) {
        StringBuilder sql = new StringBuilder("select * from job_definitions where 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(agentId)) {
            sql.append(" and owner_agent_id = ?");
            args.add(agentId);
        }
        if (StringUtils.hasText(projectId)) {
            sql.append(" and project_id = ?");
            args.add(projectId);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and status = ?");
            args.add(status);
        }
        sql.append(" order by updated_at desc, title asc");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> definitionFromRow(rs), args.toArray());
    }

    public JobDefinition saveDefinition(JobDefinition def) {
        Instant now = Instant.now();
        Instant createdAt = def.createdAt() == null ? now : def.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into job_definitions (
                    id, owner_agent_id, project_id, workspace_id, persistent_workspace_enabled, status,
                    title, summary, items_json, prompt_profile,
                    model, settings_override_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    owner_agent_id = excluded.owner_agent_id,
                    project_id = excluded.project_id,
                    workspace_id = excluded.workspace_id,
                    persistent_workspace_enabled = excluded.persistent_workspace_enabled,
                    status = excluded.status,
                    title = excluded.title,
                    summary = excluded.summary,
                    items_json = excluded.items_json,
                    prompt_profile = excluded.prompt_profile,
                    model = excluded.model,
                    settings_override_json = excluded.settings_override_json,
                    updated_at = excluded.updated_at
                """,
            def.id(), def.ownerAgentId(), def.projectId(), def.workspaceId(),
            Boolean.TRUE.equals(def.persistentWorkspaceEnabled()) ? 1 : 0,
            def.status(), def.title(), def.summary(),
            json(def.items()), def.promptProfile(),
            def.model(), def.settingsOverrideJson(),
            createdAt.toString(), updatedAt.toString()
        );
        return findDefinition(def.id()).orElseThrow();
    }

    public void deleteDefinition(String id) {
        if (StringUtils.hasText(id)) {
            jdbcTemplate.update("delete from job_recurrences where job_id = ?", id);
            jdbcTemplate.update("delete from job_runs where job_id = ?", id);
            jdbcTemplate.update("delete from job_definitions where id = ?", id);
        }
    }

    @Transactional
    public void purgeDefinitionsOwnedByAgent(String agentId) {
        if (!StringUtils.hasText(agentId)) {
            return;
        }
        jdbcTemplate.update(
            "delete from job_recurrences where job_id in (select id from job_definitions where owner_agent_id = ?)",
            agentId
        );
        jdbcTemplate.update(
            "delete from job_runs where job_id in (select id from job_definitions where owner_agent_id = ?)",
            agentId
        );
        jdbcTemplate.update("delete from job_definitions where owner_agent_id = ?", agentId);
    }

    // ── JobRun ──

    public Optional<JobRun> findRun(String id) {
        if (!StringUtils.hasText(id)) return Optional.empty();
        return jdbcTemplate.query(
            "select * from job_runs where id = ?",
            rs -> rs.next() ? Optional.of(runFromRow(rs)) : Optional.empty(),
            id
        );
    }

    public List<JobRun> findRunsByJobId(String jobId) {
        return jdbcTemplate.query(
            "select * from job_runs where job_id = ? order by created_at desc",
            (rs, rowNum) -> runFromRow(rs),
            jobId
        );
    }

    public long countActiveRunsByJobId(String jobId) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from job_runs
                where job_id = ?
                  and status not in (?, ?, ?)
                """,
            Long.class,
            jobId,
            JobRunStatus.COMPLETED.name(),
            JobRunStatus.FAILED.name(),
            JobRunStatus.CANCELLED.name()
        );
        return count == null ? 0 : count;
    }

    public long countActiveRunsByProject(String projectId) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from job_runs r
                join job_definitions d on d.id = r.job_id
                where d.project_id = ?
                  and r.status not in (?, ?, ?)
                """,
            Long.class,
            projectId,
            JobRunStatus.COMPLETED.name(),
            JobRunStatus.FAILED.name(),
            JobRunStatus.CANCELLED.name()
        );
        return count == null ? 0 : count;
    }

    public JobRun saveRun(JobRun run) {
        Instant now = Instant.now();
        Instant createdAt = run.createdAt() == null ? now : run.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into job_runs (
                    id, job_id, job_assignment_id, workspace_id, status, work_item_runs_json,
                    workspace_path, output_dir, final_message, error_text,
                    created_at, updated_at, started_at, completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    job_assignment_id = excluded.job_assignment_id,
                    workspace_id = excluded.workspace_id,
                    status = excluded.status,
                    work_item_runs_json = excluded.work_item_runs_json,
                    workspace_path = excluded.workspace_path,
                    output_dir = excluded.output_dir,
                    final_message = excluded.final_message,
                    error_text = excluded.error_text,
                    updated_at = excluded.updated_at,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at
                """,
            run.id(), run.jobId(), run.jobAssignmentId(), run.workspaceId(), run.status().name(),
            json(run.workItemRuns()),
            run.workspacePath(), run.outputDir(),
            run.finalMessage(), run.errorText(),
            createdAt.toString(), updatedAt.toString(),
            instant(run.startedAt()), instant(run.completedAt())
        );
        return findRun(run.id()).orElseThrow();
    }

    // ── JobRecurrence ──

    public Optional<JobRecurrence> findRecurrence(String jobId) {
        return jdbcTemplate.query(
            "select * from job_recurrences where job_id = ?",
            rs -> rs.next() ? Optional.of(recurrenceFromRow(rs)) : Optional.empty(),
            jobId
        );
    }

    public JobRecurrence saveRecurrence(JobRecurrence rec) {
        Instant now = Instant.now();
        Instant createdAt = rec.createdAt() == null ? now : rec.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into job_recurrences (
                    id, job_id, cron_expression, timezone,
                    next_fire_time, enabled, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(job_id) do update set
                    cron_expression = excluded.cron_expression,
                    timezone = excluded.timezone,
                    next_fire_time = excluded.next_fire_time,
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at
                """,
            rec.id(), rec.jobId(), rec.cronExpression(), rec.timezone(),
            instant(rec.nextFireTime()), rec.enabled() ? 1 : 0,
            createdAt.toString(), updatedAt.toString()
        );
        return findRecurrence(rec.jobId()).orElseThrow();
    }

    public void deleteRecurrence(String jobId) {
        jdbcTemplate.update("delete from job_recurrences where job_id = ?", jobId);
    }

    public List<JobRecurrence> findDueRecurrences(Instant before) {
        return jdbcTemplate.query(
            """
                select * from job_recurrences
                where enabled = 1 and next_fire_time <= ?
                order by next_fire_time asc
                """,
            (rs, rowNum) -> recurrenceFromRow(rs),
            before.toString()
        );
    }

    // ── Row mapping ──

    private JobDefinition definitionFromRow(ResultSet rs) throws SQLException {
        return new JobDefinition(
            rs.getString("id"),
            getNullable(rs, "owner_agent_id"),
            getNullable(rs, "project_id"),
            getNullable(rs, "workspace_id"),
            getBoolean(rs, "persistent_workspace_enabled"),
            getNullable(rs, "status"),
            rs.getString("title"),
            rs.getString("summary"),
            read(rs.getString("items_json"), ITEM_LIST, List.of()),
            rs.getString("prompt_profile"),
            rs.getString("model"),
            rs.getString("settings_override_json"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    private JobRun runFromRow(ResultSet rs) throws SQLException {
        return new JobRun(
            rs.getString("id"),
            rs.getString("job_id"),
            getNullable(rs, "job_assignment_id"),
            getNullable(rs, "workspace_id"),
            JobRunStatus.valueOf(rs.getString("status")),
            read(rs.getString("work_item_runs_json"), ITEM_RUN_LIST, List.of()),
            rs.getString("workspace_path"),
            rs.getString("output_dir"),
            rs.getString("final_message"),
            rs.getString("error_text"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at")),
            parseInstant(rs.getString("started_at")),
            parseInstant(rs.getString("completed_at"))
        );
    }

    private JobRecurrence recurrenceFromRow(ResultSet rs) throws SQLException {
        return new JobRecurrence(
            rs.getString("id"),
            rs.getString("job_id"),
            rs.getString("cron_expression"),
            rs.getString("timezone"),
            parseInstant(rs.getString("next_fire_time")),
            rs.getInt("enabled") == 1,
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    // ── JSON helpers ──

    private <T> T read(String json, TypeReference<T> type, T defaultValue) {
        if (!StringUtils.hasText(json)) return defaultValue;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse job JSON", e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize job JSON", e);
        }
    }

    private String instant(Instant i) {
        return i == null ? null : i.toString();
    }

    private Instant parseInstant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private String getNullable(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private boolean getBoolean(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getInt(column) != 0;
        } catch (SQLException ignored) {
            return false;
        }
    }

    // ── Schema bootstrapping ──

    private void ensureTables() {
        jdbcTemplate.execute("""
            create table if not exists job_definitions (
                id text primary key,
                owner_agent_id text,
                project_id text,
                workspace_id text,
                persistent_workspace_enabled integer not null default 0,
                status text,
                title text not null,
                summary text,
                items_json text not null,
                prompt_profile text,
                model text,
                settings_override_json text,
                created_at text not null,
                updated_at text not null
            )
            """);
        List<String> definitionColumns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('job_definitions')",
            String.class
        );
        if (!definitionColumns.contains("owner_agent_id")) {
            jdbcTemplate.execute("alter table job_definitions add column owner_agent_id text");
        }
        if (!definitionColumns.contains("project_id")) {
            jdbcTemplate.execute("alter table job_definitions add column project_id text");
        }
        if (!definitionColumns.contains("workspace_id")) {
            jdbcTemplate.execute("alter table job_definitions add column workspace_id text");
        }
        if (!definitionColumns.contains("persistent_workspace_enabled")) {
            jdbcTemplate.execute("alter table job_definitions add column persistent_workspace_enabled integer not null default 0");
        }
        if (!definitionColumns.contains("status")) {
            jdbcTemplate.execute("alter table job_definitions add column status text");
        }
        jdbcTemplate.execute("""
            create table if not exists job_runs (
                id text primary key,
                job_id text not null,
                job_assignment_id text,
                workspace_id text,
                status text not null,
                work_item_runs_json text not null,
                workspace_path text,
                output_dir text,
                final_message text,
                error_text text,
                created_at text not null,
                updated_at text not null,
                started_at text,
                completed_at text,
                foreign key (job_id) references job_definitions(id) on delete cascade
            )
            """);
        List<String> runColumns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('job_runs')",
            String.class
        );
        if (!runColumns.contains("job_assignment_id")) {
            jdbcTemplate.execute("alter table job_runs add column job_assignment_id text");
        }
        if (!runColumns.contains("workspace_id")) {
            jdbcTemplate.execute("alter table job_runs add column workspace_id text");
        }
        jdbcTemplate.execute("""
            create table if not exists job_recurrences (
                id text primary key,
                job_id text not null unique,
                cron_expression text not null,
                timezone text not null,
                next_fire_time text,
                enabled integer not null default 1,
                created_at text not null,
                updated_at text not null,
                foreign key (job_id) references job_definitions(id) on delete cascade
            )
            """);
    }
}
