package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRepositoryTest {

    @Test
    void createsFreshWorkflowSchema() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true)
        );

        new WorkflowRepository(jdbcTemplate, new ObjectMapper());

        assertThat(columns(jdbcTemplate, "workflow_definitions")).contains(
            "schema_version", "max_concurrency", "nodes_json", "routes_json", "ui_layout_json");
        assertThat(columns(jdbcTemplate, "workflow_runs")).contains(
            "current_node_index", "node_runs_json", "workflow_snapshot_json",
            "final_outputs_json", "artifact_ids_json", "updated_at", "run_display_name");
        assertThat(columns(jdbcTemplate, "workflow_node_runs")).contains("workflow_run_id", "node_key", "status");
        assertThat(columns(jdbcTemplate, "inbox_messages")).contains("message_type", "metadata_json", "updated_at");
    }

    @Test
    void currentWorkflowSchemaWarmStartIsIdempotent() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true)
        );
        new WorkflowRepository(jdbcTemplate, new ObjectMapper());

        WorkflowRepository repository = new WorkflowRepository(jdbcTemplate, new ObjectMapper());

        WorkflowDefinition definition = repository.saveDefinition(new WorkflowDefinition(
            "warm-workflow", 2, "Warm Workflow", "", 1,
            java.util.List.of(), java.util.List.of(), java.util.Map.of(), null, null
        ));
        assertThat(repository.findDefinition(definition.id())).isPresent();
    }

    @Test
    void migratesLegacyStepWorkflowDefinitionsToGraphColumns() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true)
        );
        jdbcTemplate.execute("""
            create table workflow_definitions (
                id text primary key,
                title text not null,
                summary text,
                steps_json text not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.update("""
                insert into workflow_definitions (id, title, summary, steps_json, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
            "legacy-workflow", "Legacy Workflow", "old shape", "[]",
            "2026-05-14T00:00:00Z", "2026-05-14T00:00:00Z");

        WorkflowRepository repository = new WorkflowRepository(jdbcTemplate, new ObjectMapper());

        WorkflowDefinition migrated = repository.findDefinition("legacy-workflow").orElseThrow();
        assertThat(migrated.title()).isEqualTo("Legacy Workflow");
        assertThat(migrated.nodes()).isEmpty();
        assertThat(migrated.routes()).isEmpty();
    }

    @Test
    void unexpectedDefinitionMigrationFailureIsVisible() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        JdbcTemplate setup = new JdbcTemplate(dataSource);
        setup.execute("""
            create table workflow_definitions (
                id text primary key,
                title text not null,
                summary text,
                steps_json text not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        JdbcTemplate jdbcTemplate = new FailingMigrationJdbcTemplate(
            dataSource,
            "alter table workflow_definitions add column routes_json"
        );

        assertThatThrownBy(() -> new WorkflowRepository(jdbcTemplate, new ObjectMapper()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to migrate workflow schema: add column workflow_definitions.routes_json")
            .hasRootCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void migratesLegacyWorkflowRunsToGraphRunColumns() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true)
        );
        jdbcTemplate.execute("""
            create table workflow_definitions (
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
        jdbcTemplate.execute("""
            create table workflow_runs (
                id text primary key,
                workflow_id text not null,
                status text not null,
                workflow_snapshot_json text,
                step_runs_json text,
                final_output_values_json text,
                final_message text,
                error_text text,
                created_at text not null,
                started_at text,
                completed_at text
            )
            """);
        jdbcTemplate.update("""
                insert into workflow_definitions (
                    id, title, summary, nodes_json, routes_json, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
            "legacy-workflow", "Legacy Workflow", "old shape", "[]", "[]",
            "2026-05-14T00:00:00Z", "2026-05-14T00:00:00Z");
        jdbcTemplate.update("""
                insert into workflow_runs (
                    id, workflow_id, status, workflow_snapshot_json, step_runs_json,
                    final_output_values_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
            "legacy-run", "legacy-workflow", "COMPLETED", null, "[]", "{}",
            "2026-05-14T00:00:00Z");

        WorkflowRepository repository = new WorkflowRepository(jdbcTemplate, new ObjectMapper());

        WorkflowRun migrated = repository.findRunsByWorkflowId("legacy-workflow").getFirst();
        assertThat(migrated.id()).isEqualTo("legacy-run");
        assertThat(migrated.currentNodeIndex()).isZero();
        assertThat(migrated.nodeRuns()).isEmpty();
        assertThat(migrated.updatedAt()).isEqualTo(migrated.createdAt());
        assertThat(columns(jdbcTemplate, "workflow_runs")).contains(
            "agent_id", "job_id", "job_assignment_id", "job_run_id", "project_id", "workspace_id", "run_type");
    }

    @Test
    void persistsWorkflowRunAttribution() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true)
        );
        WorkflowRepository repository = new WorkflowRepository(jdbcTemplate, new ObjectMapper());
        WorkflowDefinition definition = repository.saveDefinition(new WorkflowDefinition(
            "workflow-1", 2, "Workflow", "", 1, java.util.List.of(), java.util.List.of(), java.util.Map.of(), null, null
        ));

        repository.saveRun(new WorkflowRun(
            "run-1",
            definition.id(),
            "Workflow display run",
            WorkflowRunStatus.COMPLETED,
            0,
            java.util.List.of(),
            "/tmp/work",
            "/tmp/out",
            "agent-1",
            "job-1",
            "assignment-1",
            "job-run-1",
            "project-1",
            "workspace-1",
            "JOB_WORKFLOW_ITEM",
            definition,
            java.util.Map.of("result", "ok"),
            java.util.List.of("artifact-1"),
            "done",
            null,
            null,
            null,
            null,
            null
        ));

        WorkflowRun run = repository.findRun("run-1").orElseThrow();
        assertThat(run.runDisplayName()).isEqualTo("Workflow display run");
        assertThat(run.agentId()).isEqualTo("agent-1");
        assertThat(run.jobId()).isEqualTo("job-1");
        assertThat(run.jobAssignmentId()).isEqualTo("assignment-1");
        assertThat(run.jobRunId()).isEqualTo("job-run-1");
        assertThat(run.projectId()).isEqualTo("project-1");
        assertThat(run.workspaceId()).isEqualTo("workspace-1");
        assertThat(run.runType()).isEqualTo("JOB_WORKFLOW_ITEM");
    }

    private java.util.List<String> columns(JdbcTemplate jdbcTemplate, String table) {
        return jdbcTemplate.queryForList("select name from pragma_table_info('" + table + "')", String.class);
    }

    private static class FailingMigrationJdbcTemplate extends JdbcTemplate {
        private final String failedSqlFragment;

        private FailingMigrationJdbcTemplate(DataSource dataSource, String failedSqlFragment) {
            super(dataSource);
            this.failedSqlFragment = failedSqlFragment;
        }

        @Override
        public void execute(String sql) throws DataAccessException {
            if (sql.startsWith(failedSqlFragment)) {
                throw new DataAccessResourceFailureException("forced migration failure");
            }
            super.execute(sql);
        }
    }
}
