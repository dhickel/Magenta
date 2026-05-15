package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRepositoryTest {

    @Test
    void migratesLegacyStepWorkflowDefinitionsToGraphColumns() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:", true)
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
}
