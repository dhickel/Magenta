package io.mindspice.magenta2.ai.orchestration.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.workspaces.EffectiveWorkspaceResolver;
import io.mindspice.magenta2.ai.orchestration.workspaces.Workspace;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLeaseService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentContextServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void migrationBackfillsProjectIdFromLegacyInputJson() {
        JdbcTemplate jdbc = jdbcTemplate();
        jdbc.execute("""
            create table work_assignments (
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
        Instant now = Instant.now();
        jdbc.update(
            """
                insert into work_assignments (
                    id, agent_id, assignment_type, priority, status, current_item_index,
                    checkpoint_json, input_json, output_json, evidence_json, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            "assignment-legacy", "agent-1", AssignmentType.TASK_RUN.name(), 1, OrchestrationStatus.QUEUED.name(), 0,
            "{}", "{\"projectId\":\"project-legacy\",\"taskId\":\"task-1\"}", "{}", "{}",
            now.toString(), now.toString()
        );

        OrchestrationRuntimeRepository repository = new OrchestrationRuntimeRepository(jdbc, new ObjectMapper());

        WorkAssignment assignment = repository.findAssignment("assignment-legacy").orElseThrow();
        assertThat(assignment.projectId()).isEqualTo("project-legacy");
        assertThat(assignment.effectiveWorkspaceId()).isNull();
    }

    @Test
    void assignmentCreationPersistsFirstClassProjectAndEffectiveWorkspaceContext() {
        Context context = context();

        WorkAssignment assignment = context.assignmentService().create(new AssignmentRequest(
            "agent-1", null, null, AssignmentType.TASK_RUN, 3, null,
            "project-1", null, Map.of("taskId", "task-1")
        ));

        assertThat(assignment.projectId()).isEqualTo("project-1");
        assertThat(assignment.input()).containsEntry("projectId", "project-1");
        assertThat(assignment.effectiveWorkspaceKind()).isEqualTo(WorkspaceOwnerType.PROJECT.name());
        assertThat(assignment.effectiveWorkspaceId()).isEqualTo(context.workspaceService()
            .projectWorkspace("project-1", "Project project-1").id());
        assertThat(context.leaseService().activeWritableLease(assignment.effectiveWorkspaceId())).isEmpty();
        assertThat(context.assignmentService().summary(assignment.id()).effectiveWorkspaceDisplayPath())
            .isEqualTo("projects/project-1/workspace");
    }

    @Test
    void workspaceIdOnlyRemainsCompatibilityMetadataAndDoesNotScopeProjectExecution() {
        Context context = context();
        Workspace projectWorkspace = context.workspaceService().projectWorkspace("project-compat", "Project compat");

        WorkAssignment assignment = context.assignmentService().create(new AssignmentRequest(
            "agent-1", null, null, AssignmentType.REPORT, 1, null,
            null, projectWorkspace.id(), Map.of("message", "hello")
        ));

        assertThat(assignment.projectId()).isNull();
        assertThat(assignment.workspaceId()).isEqualTo(projectWorkspace.id());
        assertThat(assignment.effectiveWorkspaceKind()).isEqualTo(WorkspaceOwnerType.AGENT.name());
        assertThat(context.workspaceService().get(assignment.effectiveWorkspaceId()).ownerType())
            .isEqualTo(WorkspaceOwnerType.AGENT);
    }

    @Test
    void mismatchedProjectOwnedWorkspaceIdIsRejected() {
        Context context = context();
        Workspace unrelatedProjectWorkspace = context.workspaceService().projectWorkspace("project-b", "Project B");

        assertThatThrownBy(() -> context.assignmentService().create(new AssignmentRequest(
            "agent-1", null, null, AssignmentType.REPORT, 1, null,
            "project-a", unrelatedProjectWorkspace.id(), Map.of("message", "hello")
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("projectId is the only project-scoping field");
    }

    @Test
    void workspaceBlockedAssignmentRequeuesAfterBlockingLeaseIsReleased() {
        Context context = context();
        Workspace workspace = context.workspaceService().projectWorkspace("project-blocked", "Project Blocked");
        var lease = context.leaseService().acquireWritable(workspace.id(), "ASSIGNMENT", "other-assignment", Duration.ofMinutes(5));
        WorkAssignment waiting = context.repository().saveAssignment(new WorkAssignment(
            "assignment-waiting", "agent-1", null, null, AssignmentType.REPORT, 1,
            OrchestrationStatus.WAITING, null, null, "project-blocked", workspace.id(), WorkspaceOwnerType.PROJECT.name(),
            0, Map.of("workspaceBlocker", "leased", "projectWorkspaceId", workspace.id()),
            Map.of("message", "hello", "projectId", "project-blocked"), Map.of(), Map.of(),
            "leased", null, null, null, null, null, null
        ));

        assertThatThrownBy(() -> context.assignmentService().requeueWorkspaceBlockedAssignment(waiting.id()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active writable lease");

        context.leaseService().release(lease.id(), "other-assignment");
        WorkAssignment requeued = context.assignmentService().requeueWorkspaceBlockedAssignment(waiting.id());

        assertThat(requeued.status()).isEqualTo(OrchestrationStatus.QUEUED);
        assertThat(requeued.checkpoint()).doesNotContainKey("workspaceBlocker");
        assertThat(requeued.checkpoint()).containsEntry("lastWorkspaceBlocker", "leased");
    }

    private Context context() {
        JdbcTemplate jdbc = jdbcTemplate();
        ObjectMapper mapper = new ObjectMapper();
        AiConfig config = new AiConfig(null, null, null, 10, tempDir, Map.of(
            "main", new ModelConfig("main-remote", "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null)
        ), Map.of());
        OrchestrationRuntimeRepository repository = new OrchestrationRuntimeRepository(jdbc, mapper);
        WorkspaceDirectoryService directoryService;
        try {
            directoryService = new WorkspaceDirectoryService(config);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbc);
        WorkspaceService workspaceService;
        try {
            workspaceService = new WorkspaceService(workspaceRepository, config);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        WorkspaceLeaseService leaseService = new WorkspaceLeaseService(workspaceRepository);
        AgentProfileService agentService = new AgentProfileService(new AgentProfileRepository(jdbc, mapper), config, null);
        agentService.create(new AgentProfile(
            "agent-1", "Agent 1", AgentProfileStatus.ACTIVE, "main", "Prompt",
            List.of(), List.of(), true, null, null
        ));
        AssignmentService assignmentService = new AssignmentService(
            repository, agentService, null, null, null, null, null,
            new EffectiveWorkspaceResolver(directoryService, workspaceService), workspaceService, leaseService
        );
        return new Context(repository, workspaceService, leaseService, assignmentService);
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true));
    }

    private record Context(
        OrchestrationRuntimeRepository repository,
        WorkspaceService workspaceService,
        WorkspaceLeaseService leaseService,
        AssignmentService assignmentService
    ) {
    }
}
