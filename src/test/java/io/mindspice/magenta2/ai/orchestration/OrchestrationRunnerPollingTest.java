package io.mindspice.magenta2.ai.orchestration;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.task.TaskRepository;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRepository;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.execution.MagentaWorkExecutor;
import io.mindspice.magenta2.ai.execution.MagentaWorkKind;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.InboxService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEventService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJobService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunnerService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRuntimeRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsRepository;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationRunnerPollingTest {
    @TempDir
    Path tempDir;

    @Test
    void backloggedExecutorLeasesAssignmentOnlyOnce() throws Exception {
        MagentaWorkExecutor executor = new MagentaWorkExecutor(Map.of(
            MagentaWorkKind.BACKGROUND_JOB, new MagentaWorkExecutor.LaneSettings("test-bg-", 1, 100)
        ));
        Services svc = services(executor);
        AgentProfile agent = svc.agentService().create(profile("agent-poll", "main"));

        // Fill the single thread with a blocker so the executor is backlogged
        CountDownLatch blockStart = new CountDownLatch(1);
        CountDownLatch blockRelease = new CountDownLatch(1);
        executor.submitBackground("blocker", 0, "blocker", () -> {
            blockStart.countDown();
            blockRelease.await(5, TimeUnit.SECONDS);
            return null;
        });
        assertThat(blockStart.await(2, TimeUnit.SECONDS)).isTrue();

        // Create a QUEUED assignment (REPORT type requires no model execution)
        WorkAssignment assignment = svc.assignmentService().create(new AssignmentRequest(
            agent.id(), null, null, AssignmentType.REPORT, 0, null, null,
            Map.of("message", "poll-test")
        ));
        assertThat(assignment.status()).isEqualTo(OrchestrationStatus.QUEUED);

        // First poll: should acquire lease and submit to executor (queued behind blocker)
        svc.runnerService().pollQueuedWork();

        // Assignment is now RUNNING with a lease — not QUEUED, so cannot be re-found
        WorkAssignment afterFirst = svc.assignmentService().get(assignment.id());
        assertThat(afterFirst.status()).isEqualTo(OrchestrationStatus.RUNNING);
        assertThat(afterFirst.leaseOwner()).isNotNull();
        assertThat(svc.repository().findQueuedAssignments(10)).isEmpty();

        // Second poll: should find nothing QUEUED and NOT resubmit
        svc.runnerService().pollQueuedWork();
        WorkAssignment afterSecond = svc.assignmentService().get(assignment.id());
        assertThat(afterSecond.status()).isEqualTo(OrchestrationStatus.RUNNING);

        // Cleanup so the executor thread terminates
        blockRelease.countDown();
    }

    @Test
    void saturatedExecutorRevertsLeaseAndPollingSurvives() throws Exception {
        MagentaWorkExecutor executor = new MagentaWorkExecutor(Map.of(
            MagentaWorkKind.BACKGROUND_JOB, new MagentaWorkExecutor.LaneSettings("test-bg-", 1, 0)
        ));
        Services svc = services(executor);
        AgentProfile agent = svc.agentService().create(profile("agent-sat", "main"));

        // Fill the single capacity slot (1 thread + 0 queue = total capacity 1)
        CountDownLatch blockStart = new CountDownLatch(1);
        CountDownLatch blockRelease = new CountDownLatch(1);
        executor.submitBackground("blocker", 0, "blocker", () -> {
            blockStart.countDown();
            blockRelease.await(5, TimeUnit.SECONDS);
            return null;
        });
        assertThat(blockStart.await(2, TimeUnit.SECONDS)).isTrue();

        // Create a QUEUED assignment
        WorkAssignment assignment = svc.assignmentService().create(new AssignmentRequest(
            agent.id(), null, null, AssignmentType.REPORT, 0, null, null,
            Map.of("message", "rejection-test")
        ));

        // First poll: lease acquired, submission rejected (executor full), lease reverted
        svc.runnerService().pollQueuedWork();

        // Assignment is back to QUEUED with lease cleared
        WorkAssignment afterFirst = svc.assignmentService().get(assignment.id());
        assertThat(afterFirst.status()).isEqualTo(OrchestrationStatus.QUEUED);
        assertThat(afterFirst.leaseOwner()).isNull();
        // Still visible in the queued list
        assertThat(svc.repository().findQueuedAssignments(10))
            .extracting(WorkAssignment::id)
            .contains(assignment.id());

        // Second poll: same cycle occurs — polling survives, work stays eligible
        svc.runnerService().pollQueuedWork();
        WorkAssignment afterSecond = svc.assignmentService().get(assignment.id());
        assertThat(afterSecond.status()).isEqualTo(OrchestrationStatus.QUEUED);
        assertThat(afterSecond.leaseOwner()).isNull();

        // Cleanup
        blockRelease.countDown();
    }

    @Test
    void revertToQueuedOnlySucceedsForCorrectLeaseOwner() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        OrchestrationRuntimeRepository repository = new OrchestrationRuntimeRepository(jdbcTemplate, objectMapper);

        // Insert a RUNNING assignment with a known lease owner
        WorkAssignment running = repository.saveAssignment(new WorkAssignment(
            "lease-owner-test", "agent-x", null, null, AssignmentType.REPORT, 0,
            OrchestrationStatus.RUNNING, null, null, 0, Map.of(), Map.of("message", "test"),
            Map.of(), Map.of(), null, "correct-owner", Instant.now().plusSeconds(300),
            Instant.now(), Instant.now(), Instant.now(), null
        ));

        // Wrong owner: should not revert
        assertThat(repository.revertToQueued(running.id(), "wrong-owner")).isFalse();
        assertThat(repository.findAssignment(running.id()).orElseThrow().status())
            .isEqualTo(OrchestrationStatus.RUNNING);

        // Correct owner: should revert
        assertThat(repository.revertToQueued(running.id(), "correct-owner")).isTrue();
        assertThat(repository.findAssignment(running.id()).orElseThrow().status())
            .isEqualTo(OrchestrationStatus.QUEUED);
    }

    private Services services(MagentaWorkExecutor executor) throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        AiConfig aiConfig = aiConfig();

        AgentProfileService agentService = new AgentProfileService(
            new AgentProfileRepository(jdbcTemplate, objectMapper), aiConfig, null
        );
        RuntimeSettingsService settingsService = new RuntimeSettingsService(
            new RuntimeSettingsRepository(jdbcTemplate), aiConfig, agentService
        );
        WorkspaceService workspaceService = new WorkspaceService(new WorkspaceRepository(jdbcTemplate), aiConfig);
        OrchestrationRuntimeRepository repository = new OrchestrationRuntimeRepository(jdbcTemplate, objectMapper);
        OrchestrationJobService jobService = new OrchestrationJobService(repository, agentService, workspaceService);
        AssignmentService assignmentService = new AssignmentService(repository, agentService, settingsService, jobService);
        OrchestrationEventService eventService = new OrchestrationEventService(repository, assignmentService);
        InboxService inboxService = new InboxService(repository, agentService, eventService);
        TaskService taskService = new TaskService(new TaskRepository(jdbcTemplate, objectMapper));
        WorkflowService workflowService = new WorkflowService(
            new WorkflowRepository(jdbcTemplate, objectMapper), taskService, null
        );
        OrchestrationRunnerService runnerService = new OrchestrationRunnerService(
            repository, assignmentService, jobService, taskService, workflowService, inboxService,
            eventService, executor
        );
        return new Services(repository, agentService, assignmentService, runnerService);
    }

    private AgentProfile profile(String name, String model) {
        return new AgentProfile(
            null, name, AgentProfileStatus.ACTIVE, model, "Prompt", List.of(), List.of(), true, null, null
        );
    }

    private AiConfig aiConfig() {
        Map<String, ModelConfig> models = Map.of(
            "main", model("main-remote"),
            "workflow", model("workflow-remote")
        );
        return new AiConfig(
            "legacy", "main", "workflow", "workflow", "main", 10, tempDir, null, models,
            Map.of("legacy", new AgentConfig("main", "Legacy prompt", List.of(), List.of()))
        );
    }

    private ModelConfig model(String remoteName) {
        return new ModelConfig(remoteName, "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null);
    }

    private JdbcTemplate jdbcTemplate() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }

    private record Services(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentService,
        AssignmentService assignmentService,
        OrchestrationRunnerService runnerService
    ) {
    }
}
