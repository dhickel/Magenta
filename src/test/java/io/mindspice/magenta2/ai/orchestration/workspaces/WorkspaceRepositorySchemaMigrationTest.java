package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import io.mindspice.magenta2.ai.chat.plan.PlanRepository;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceRepositorySchemaMigrationTest {

    @Test
    void schemaSqlCreatesCurrentLeaseTableWithoutWorkspaceRoots() throws Exception {
        String schema = Files.readString(schemaPath());

        assertThat(schema).doesNotContain("create table if not exists workspace_roots");
        assertThat(schema).doesNotContain("references workspace_roots");
        assertThat(schema).contains("create table if not exists workspace_leases");
        assertThat(schema).contains("foreign key(workspace_id) references workspaces(id)");
    }

    @Test
    void schemaSqlCreatesCurrentPlanAndOutputArtifactShape() throws Exception {
        JdbcTemplate jdbc = jdbc();

        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }

        List<String> planRunColumns = columns(jdbc, "plan_runs");
        List<String> artifactColumns = columns(jdbc, "run_output_artifacts");

        assertThat(planRunColumns).contains("temp_workspace_path");
        assertThat(artifactColumns).contains("agent_id", "job_id", "project_id", "workspace_id", "run_type");
        assertThat(indexes(jdbc, "run_output_artifacts"))
            .contains(
                "idx_run_output_artifacts_run",
                "idx_run_output_artifacts_agent",
                "idx_run_output_artifacts_job",
                "idx_run_output_artifacts_project",
                "idx_run_output_artifacts_workspace"
            );

        new PlanRepository(jdbc, new ObjectMapper());
        new WorkspaceRepository(jdbc);

        assertThat(columns(jdbc, "plan_runs")).isEqualTo(planRunColumns);
        assertThat(columns(jdbc, "run_output_artifacts")).isEqualTo(artifactColumns);
    }

    @Test
    void legacyWorkspaceRootsMigrationPreservesWarmLeaseRows() {
        JdbcTemplate jdbc = jdbc();
        String now = Instant.parse("2026-05-18T12:00:00Z").toString();
        String expires = Instant.parse("2026-05-18T13:00:00Z").toString();
        createLegacyWorkspaceTables(jdbc);
        insertLegacyWorkspaceRoot(jdbc, "ws-active", "PROJECT", "project-1", "projects/project-1", now);
        insertLegacyWorkspaceRoot(jdbc, "ws-requested", "PROJECT", "project-2", "projects/project-2", now);
        insertLegacyWorkspaceRoot(jdbc, "ws-released", "JOB", "job-1", "jobs/job-1", now);
        insertLegacyLease(jdbc, "lease-active", "ws-active", "ASSIGNMENT", "run-active", "WRITE", expires, 0, null, now);
        insertLegacyLease(jdbc, "lease-requested", "ws-requested", "ASSIGNMENT", "run-requested", "WRITE", expires, 1, null, now);
        insertLegacyLease(jdbc, "lease-released", "ws-released", "ASSIGNMENT", "run-released", "READ", expires, 0, now, now);

        WorkspaceRepository repository = new WorkspaceRepository(jdbc);

        assertThat(tableExists(jdbc, "workspace_roots")).isFalse();
        assertThat(foreignKeyTargets(jdbc, "workspace_leases")).contains("workspaces");
        assertThat(jdbc.queryForObject("select count(*) from workspace_leases", Integer.class)).isEqualTo(3);
        assertThat(repository.findLeaseById("lease-active").orElseThrow().releasedAt()).isNull();
        assertThat(repository.findLeaseById("lease-requested").orElseThrow().releaseRequested()).isTrue();
        assertThat(repository.findLeaseById("lease-released").orElseThrow().releasedAt()).isNotNull();
        assertThat(repository.findActiveWritableLease("ws-active").orElseThrow().holderId()).isEqualTo("run-active");
        assertThat(repository.findById("ws-requested").orElseThrow().ownerId()).isEqualTo("project-2");
    }

    private Path schemaPath() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("schema.sql").toURI());
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true));
    }

    private void createLegacyWorkspaceTables(JdbcTemplate jdbc) {
        jdbc.execute("""
            create table workspace_roots (
                id text primary key,
                owner_type text not null,
                owner_id text not null,
                root_relative_path text not null,
                display_name text not null,
                metadata_json text,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbc.execute("""
            create table workspace_leases (
                id text primary key,
                workspace_id text not null,
                holder_type text not null,
                holder_id text not null,
                mode text not null,
                expires_at text,
                release_requested integer not null default 0,
                released_at text,
                created_at text not null,
                updated_at text not null,
                foreign key(workspace_id) references workspace_roots(id)
            )
            """);
        jdbc.execute("""
            create unique index idx_workspace_leases_active_write
                on workspace_leases(workspace_id)
                where mode = 'WRITE' and released_at is null
            """);
    }

    private void insertLegacyWorkspaceRoot(
        JdbcTemplate jdbc,
        String id,
        String ownerType,
        String ownerId,
        String rootRelativePath,
        String now
    ) {
        jdbc.update("""
            insert into workspace_roots (
                id, owner_type, owner_id, root_relative_path, display_name,
                metadata_json, created_at, updated_at
            )
            values (?, ?, ?, ?, ?, '{}', ?, ?)
            """, id, ownerType, ownerId, rootRelativePath, id, now, now);
    }

    private void insertLegacyLease(
        JdbcTemplate jdbc,
        String id,
        String workspaceId,
        String holderType,
        String holderId,
        String mode,
        String expiresAt,
        int releaseRequested,
        String releasedAt,
        String now
    ) {
        jdbc.update("""
            insert into workspace_leases (
                id, workspace_id, holder_type, holder_id, mode,
                expires_at, release_requested, released_at, created_at, updated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, workspaceId, holderType, holderId, mode, expiresAt, releaseRequested, releasedAt, now, now);
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
            "select count(*) from sqlite_master where type = 'table' and name = ?",
            Integer.class,
            table
        );
        return count != null && count > 0;
    }

    private List<String> foreignKeyTargets(JdbcTemplate jdbc, String table) {
        return jdbc.query(
            "select \"table\" from pragma_foreign_key_list('" + table + "')",
            (rs, rowNum) -> rs.getString(1)
        );
    }

    private List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("select name from pragma_table_info('" + table + "')", String.class);
    }

    private List<String> indexes(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("select name from pragma_index_list('" + table + "')", String.class);
    }
}
