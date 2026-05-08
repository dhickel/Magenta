package io.mindspice.magenta2;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.plan.ChatPlanRepository;
import io.mindspice.magenta2.ai.chat.repository.AgentJobRepository;
import io.mindspice.magenta2.ai.chat.repository.AuditRepository;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskRepository;
import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskRunStatus;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRepository;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRunStatus;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStep;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRuntimeRepository;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaOwnershipTest {

    private static final List<String> ALL_TABLES = List.of(
        "ai_chat_memory",
        "ai_chat_session_metadata",
        "agent_jobs",
        "ai_chat_plans",
        "ai_chat_plan_steps",
        "audit_event",
        "ai_task_definitions",
        "ai_task_drafts",
        "ai_task_runs",
        "ai_workflow_definitions",
        "ai_workflow_runs",
        "agent_profiles",
        "orchestration_jobs",
        "orchestration_job_items",
        "work_assignments",
        "agent_inbox_messages",
        "agent_schedules",
        "schedule_firings",
        "agent_event_reactions",
        "orchestration_events",
        "runtime_settings",
        "workspaces",
        "workspace_links"
    );

    @Test
    void cleanDatabaseHasAllExpectedTables() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);

        List<String> tables = jt.queryForList(
            "select name from sqlite_master where type = 'table' order by name",
            String.class
        );
        assertThat(tables).describedAs("all expected tables must exist").containsAll(ALL_TABLES);
    }

    @Test
    void cleanDatabaseHasRequiredIndexes() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);

        List<String> indexes = jt.queryForList(
            "select name from sqlite_master where type = 'index' and name is not null order by name",
            String.class
        );
        assertThat(indexes).describedAs("all required indexes must exist")
            .contains(
                "idx_ai_chat_memory_conversation",
                "idx_audit_event_conversation",
                "idx_agent_jobs_conversation",
                "idx_agent_jobs_conversation_title_active",
                "idx_ai_task_runs_task",
                "idx_ai_workflow_runs_workflow",
                "idx_work_assignments_queue",
                "idx_workspaces_owner"
            );
    }

    @Test
    void foreignKeysAreEnabled() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);

        boolean enabled = Boolean.TRUE.equals(jt.queryForObject(
            "pragma foreign_keys",
            Boolean.class
        ));
        assertThat(enabled).describedAs("foreign keys must be enabled on the connection").isTrue();
    }

    @Test
    void taskDeleteRemovesRuns() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TaskRepository taskRepo = new TaskRepository(jt, mapper);

        Instant now = Instant.now();
        TaskDefinition task = taskRepo.save(new TaskDefinition(
            "task-1", "Test Task", "summary", "goal", "notes",
            "input desc", List.of(), "output desc", List.of(),
            List.of(), List.of(), List.of(), now, now
        ));

        taskRepo.saveRun(new TaskRun(
            "run-1", "task-1", TaskRunStatus.QUEUED,
            Map.of(), Map.of(), task, List.of(), List.of(),
            null, null, now, now, null, null
        ));
        taskRepo.saveRun(new TaskRun(
            "run-2", "task-1", TaskRunStatus.RUNNING,
            Map.of(), Map.of(), task, List.of(), List.of(),
            null, null, now, now, null, null
        ));

        assertThat(taskRepo.findRunsForTask("task-1")).hasSize(2);

        taskRepo.delete("task-1");

        assertThat(taskRepo.find("task-1")).describedAs("task definition must be deleted").isEmpty();
        assertThat(taskRepo.findRun("run-1")).describedAs("run must be cascade-deleted").isEmpty();
        assertThat(taskRepo.findRun("run-2")).describedAs("run must be cascade-deleted").isEmpty();
    }

    @Test
    void workflowDeleteRemovesRuns() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WorkflowRepository workflowRepo = new WorkflowRepository(jt, mapper);

        Instant now = Instant.now();
        WorkflowDefinition wf = workflowRepo.save(new WorkflowDefinition(
            "wf-1", "Test Workflow", "summary", List.of(), now, now
        ));

        workflowRepo.saveRun(new WorkflowRun(
            "run-1", "wf-1", WorkflowRunStatus.RUNNING,
            wf, List.of(), Map.of(), null, null,
            now, now, null, null
        ));
        workflowRepo.saveRun(new WorkflowRun(
            "run-2", "wf-1", WorkflowRunStatus.COMPLETED,
            wf, List.of(), Map.of(), "done", null,
            now, now, null, null
        ));

        assertThat(workflowRepo.findRunsForWorkflow("wf-1")).hasSize(2);

        workflowRepo.delete("wf-1");

        assertThat(workflowRepo.find("wf-1")).describedAs("workflow definition must be deleted").isEmpty();
        assertThat(workflowRepo.findRun("run-1")).describedAs("run must be cascade-deleted").isEmpty();
        assertThat(workflowRepo.findRun("run-2")).describedAs("run must be cascade-deleted").isEmpty();
    }

    @Test
    void chatPlanDeleteRemovesSteps() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);
        ChatPlanRepository repo = new ChatPlanRepository(jt, new ObjectMapper());

        Instant now = Instant.now();
        repo.save(new io.mindspice.magenta2.ai.chat.plan.ExecutionPlan(
            "conv-1",
            io.mindspice.magenta2.ai.chat.plan.PlanMode.PLAN,
            io.mindspice.magenta2.ai.chat.plan.PlanStatus.DRAFT,
            "planning task",
            "goal",
            "title",
            "summary",
            "notes",
            List.of("deliverable1"),
            List.of("input1"),
            List.of("output1"),
            List.of("assumption1"),
            List.of(new io.mindspice.magenta2.ai.chat.plan.PlanStep(1, "Step 1")),
            List.of("criteria1"),
            List.of("evidence1"),
            List.of("feedback1"),
            "pre_model",
            null,
            List.of(),
            0,
            0,
            null,
            now,
            now
        ));

        List<String> steps = jt.queryForList(
            "select step_text from ai_chat_plan_steps where conversation_id = ?",
            String.class, "conv-1"
        );
        assertThat(steps).describedAs("plan steps must exist").isNotEmpty();

        repo.delete("conv-1");

        steps = jt.queryForList(
            "select step_text from ai_chat_plan_steps where conversation_id = ?",
            String.class, "conv-1"
        );
        assertThat(steps).describedAs("plan steps must be deleted with plan").isEmpty();
    }

    @Test
    void upgradedChatSessionMetadataGetsMissingColumns() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        // Create an older version of the table (only basic columns)
        jt.execute("""
            create table if not exists ai_chat_session_metadata (
                conversation_id text primary key,
                model text
            )
            """);
        List<String> before = jt.queryForList(
            "select name from pragma_table_info('ai_chat_session_metadata')", String.class
        );
        assertThat(before).describedAs("old table must lack newer columns")
            .containsExactlyInAnyOrder("conversation_id", "model")
            .doesNotContain("planning_model", "title", "favorite", "archived", "updated_at", "active_task_run_id");

        // Repository constructor calls ensureSchema, which adds missing columns
        new ChatSessionMetadataRepository(jt);

        List<String> after = jt.queryForList(
            "select name from pragma_table_info('ai_chat_session_metadata')", String.class
        );
        assertThat(after).describedAs("ensureSchema must add all expected columns")
            .contains("conversation_id", "model", "planning_model", "title", "favorite", "archived", "updated_at", "active_task_run_id");
    }

    @Test
    void upgradedChatMemoryGetsMetadataColumn() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        // Create old table without message_metadata_json
        jt.execute("""
            create table if not exists ai_chat_memory (
                conversation_id text not null,
                message_order integer not null,
                message_type text not null,
                message_text text,
                primary key (conversation_id, message_order)
            )
            """);
        List<String> before = jt.queryForList(
            "select name from pragma_table_info('ai_chat_memory')", String.class
        );
        assertThat(before).describedAs("old table must lack message_metadata_json")
            .doesNotContain("message_metadata_json");

        new ChatMemoryRepository(jt, new ObjectMapper());

        List<String> after = jt.queryForList(
            "select name from pragma_table_info('ai_chat_memory')", String.class
        );
        assertThat(after).describedAs("ensureSchema must add message_metadata_json")
            .contains("message_metadata_json");
    }

    @Test
    void upgradedAuditEventGetsAllColumns() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        // Create minimal audit_event table
        jt.execute("""
            create table if not exists audit_event (
                id integer primary key autoincrement,
                conversation_id text not null,
                sequence integer not null,
                event_type text not null,
                recorded_at text not null
            )
            """);

        new AuditRepository(jt);

        List<String> columns = jt.queryForList(
            "select name from pragma_table_info('audit_event')", String.class
        );
        assertThat(columns).describedAs("audit ensureSchema must add message_text")
            .contains("message_text", "message_metadata_json", "model",
                "tool_call_id", "tool_name", "result_text",
                "compaction_method", "used_tokens", "percent_used");
    }

    @Test
    void upgradedChatPlansGetsExtraColumns() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        // Create old ai_chat_plans without extra columns
        jt.execute("""
            create table if not exists ai_chat_plans (
                conversation_id text primary key,
                mode text not null,
                status text not null,
                goal text,
                title text,
                summary text
            )
            """);

        new ChatPlanRepository(jt, new ObjectMapper());

        List<String> columns = jt.queryForList(
            "select name from pragma_table_info('ai_chat_plans')", String.class
        );
        assertThat(columns).describedAs("ensureTables must add all expected columns")
            .contains("planning_task", "notes", "deliverables_json", "inputs_json",
                "outputs_json", "assumptions_json", "pre_planning_model",
                "execution_model", "pending_questions_json",
                "final_message");
    }

    @Test
    void upgradedOrchestrationJobItemsGetsExtraColumns() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        // Create old orchestration_job_items without retry_count or continue_on_failure
        jt.execute("""
            create table if not exists orchestration_jobs (
                id text primary key,
                owner_agent_id text not null,
                title text not null,
                status text not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        jt.execute("""
            create table if not exists orchestration_job_items (
                id text primary key,
                job_id text not null,
                item_order integer not null,
                item_type text not null,
                priority integer not null,
                config_json text,
                created_at text not null,
                updated_at text not null
            )
            """);

        new OrchestrationRuntimeRepository(jt, new ObjectMapper());

        List<String> columns = jt.queryForList(
            "select name from pragma_table_info('orchestration_job_items')", String.class
        );
        assertThat(columns).describedAs("ensureSchema must add retry_count and continue_on_failure")
            .contains("retry_count", "continue_on_failure");
    }

    @Test
    void foreignKeyConstraintPreventsOrphanedRunsOnDirectDelete() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);

        // Insert directly (bypassing repository) to that FK cascade is the only
        // mechanism removing child rows.
        jt.update("""
            insert into ai_task_definitions (id, title, created_at, updated_at)
            values ('fk-test-task', 'FK Test', ?, ?)
            """, now(), now());
        jt.update("""
            insert into ai_task_runs (id, task_id, status, task_snapshot_json, created_at, updated_at)
            values ('fk-test-run', 'fk-test-task', 'QUEUED', '{}', ?, ?)
            """, now(), now());

        assertThat(jt.queryForList("select id from ai_task_runs where task_id = 'fk-test-task'", String.class))
            .describedAs("run must exist before delete").hasSize(1);

        // Delete parent directly via SQL (bypassing repository's explicit cascade)
        jt.update("delete from ai_task_definitions where id = 'fk-test-task'");

        assertThat(jt.queryForList("select id from ai_task_runs where task_id = 'fk-test-task'", String.class))
            .describedAs("run must be cascade-deleted by FK when foreign keys are enabled")
            .isEmpty();
    }

    @Test
    void ensureSchemaIsIdempotent() {
        JdbcTemplate jt = jdbcTemplateWithForeignKeys();
        runSchema(jt);

        // Run all repository ensureSchema methods again (they should all be no-ops)
        new ChatMemoryRepository(jt, new ObjectMapper());
        new ChatSessionMetadataRepository(jt);
        new AgentJobRepository(jt);
        new AuditRepository(jt);
        new ChatPlanRepository(jt, new ObjectMapper());
        new AgentProfileRepository(jt, new ObjectMapper());
        new OrchestrationRuntimeRepository(jt, new ObjectMapper());
        new RuntimeSettingsRepository(jt);
        new WorkspaceRepository(jt);

        // Verify all tables still exist
        List<String> tables = jt.queryForList(
            "select name from sqlite_master where type = 'table' order by name",
            String.class
        );
        assertThat(tables).containsAll(ALL_TABLES);
    }

    private void runSchema(JdbcTemplate jt) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema.sql"));
            populator.populate(jt.getDataSource().getConnection());
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to run schema.sql", e);
        }
    }

    private JdbcTemplate jdbcTemplateWithForeignKeys() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true", true);
        return new JdbcTemplate(dataSource);
    }

    private String now() {
        return Instant.now().toString();
    }
}
