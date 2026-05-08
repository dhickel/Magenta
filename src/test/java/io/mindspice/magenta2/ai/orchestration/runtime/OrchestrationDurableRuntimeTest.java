package io.mindspice.magenta2.ai.orchestration.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.service.TaskExecutionResult;
import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskFieldDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskRepository;
import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskRunStatus;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.task.TaskStep;
import io.mindspice.magenta2.ai.chat.task.TaskValueType;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRepository;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowBindingKind;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowInputBinding;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStep;
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
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsRepository;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationDurableRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void jobItemsAssignmentsInboxSchedulesAndReactionsPersist() throws Exception {
        Services services = services();
        AgentProfile agent = services.agentService().create(profile("agent-a", "main"));
        TaskDefinition task = services.taskService().saveTask(task());
        OrchestrationJob job = services.jobService().save(new OrchestrationJob(
            null, agent.id(), "Daily research", null, "summary", null, OrchestrationStatus.QUEUED, null, null
        ));

        OrchestrationJobItem item = services.jobService().saveItem(job.id(), new OrchestrationJobItem(
            null, job.id(), 1, AssignmentType.TASK_RUN, task.id(), null, "planning", 7,
            Map.of("inputValues", Map.of("topic", "SQLite")), null, null
        ));
        WorkAssignment high = services.assignmentService().create(new AssignmentRequest(
            agent.id(), null, null, AssignmentType.REPORT, 10, "main", null, Map.of("message", "high")
        ));
        WorkAssignment low = services.assignmentService().create(new AssignmentRequest(
            agent.id(), null, null, AssignmentType.REPORT, 1, null, null, Map.of("message", "low")
        ));

        assertThat(services.jobService().items(job.id())).extracting(OrchestrationJobItem::id).containsExactly(item.id());
        assertThat(services.repository().findQueuedAssignments(2)).extracting(WorkAssignment::id)
            .containsExactly(high.id(), low.id());
        assertThat(services.assignmentService().resolveModel(high, item)).isEqualTo("main-remote");

        InboxMessage message = services.inboxService().send(agent.id(), new InboxMessage(
            null, agent.id(), "user", "note", "hello", Map.of("topic", "SQLite"), false, false, null, null
        ));
        assertThat(services.inboxService().markHandled(message.id()).handled()).isTrue();

        AgentSchedule schedule = services.scheduleService().save(agent.id(), new AgentSchedule(
            null, agent.id(), job.id(), Map.of("assignmentType", "JOB_RUN", "input", Map.of("jobId", job.id())),
            "0 0 * * * *", "UTC", true, Instant.now().minusSeconds(1), null, null
        ));
        services.scheduleService().pollDueSchedules();
        assertThat(services.repository().findSchedule(schedule.id()).orElseThrow().nextRunAt()).isAfter(Instant.now());
        assertThat(services.repository().findQueuedAssignments(10)).extracting(WorkAssignment::assignmentType)
            .contains(AssignmentType.JOB_RUN);
    }

    @Test
    void runnerExecutesJobItemsAndResumesFromCheckpoint() throws Exception {
        Services services = services();
        AgentProfile agent = services.agentService().create(profile("agent-b", "main"));
        TaskDefinition task = services.taskService().saveTask(task());
        OrchestrationJob job = services.jobService().save(new OrchestrationJob(
            null, agent.id(), "Two item job", null, null, null, OrchestrationStatus.QUEUED, null, null
        ));
        services.jobService().saveItem(job.id(), new OrchestrationJobItem(
            null, job.id(), 1, AssignmentType.REPORT, null, null, null, 0, Map.of("message", "already done"), null, null
        ));
        OrchestrationJobItem taskItem = services.jobService().saveItem(job.id(), new OrchestrationJobItem(
            null, job.id(), 2, AssignmentType.TASK_RUN, task.id(), null, null, 0,
            Map.of("inputValues", Map.of("topic", "resume")), null, null
        ));
        WorkAssignment interrupted = services.repository().saveAssignment(new WorkAssignment(
            "resume-assignment", agent.id(), job.id(), null, AssignmentType.JOB_RUN, 0, OrchestrationStatus.INTERRUPTED,
            null, job.workspaceId(), 1, Map.of("nextItemIndex", 1), Map.of("jobId", job.id()),
            Map.of("done", "report"), Map.of(), null, null, null, null, null, null, null
        ));

        WorkAssignment queued = services.assignmentService().resume(interrupted.id());
        assertThat(queued.status()).isEqualTo(OrchestrationStatus.QUEUED);

        WorkAssignment completed = services.runnerService().runNextSynchronously();

        assertThat(completed.status()).isEqualTo(OrchestrationStatus.COMPLETED);
        assertThat(completed.output()).containsKey(taskItem.id());
        assertThat(completed.currentItemIndex()).isEqualTo(2);
    }

    @Test
    void waitForMessageJobItemPersistsWaitingCheckpointInsteadOfFailing() throws Exception {
        Services services = services();
        AgentProfile agent = services.agentService().create(profile("agent-wait", "main"));
        TaskDefinition task = services.taskService().saveTask(task());
        OrchestrationJob job = services.jobService().save(new OrchestrationJob(
            null, agent.id(), "Wait boundary job", null, null, null, OrchestrationStatus.QUEUED, null, null
        ));
        services.jobService().saveItem(job.id(), new OrchestrationJobItem(
            null, job.id(), 1, AssignmentType.TASK_RUN, task.id(), null, null, 0,
            Map.of("inputValues", Map.of("topic", "wait")), null, null
        ));
        services.jobService().saveItem(job.id(), new OrchestrationJobItem(
            null, job.id(), 2, AssignmentType.REPORT, null, null, null, 0,
            Map.of("message", "checkpointed report"), null, null
        ));
        OrchestrationJobItem waitItem = services.jobService().saveItem(job.id(), new OrchestrationJobItem(
            null, job.id(), 3, AssignmentType.WAIT_FOR_MESSAGE, null, null, null, 0, Map.of(), null, null
        ));
        services.assignmentService().create(new AssignmentRequest(
            agent.id(), job.id(), null, AssignmentType.JOB_RUN, 0, null, job.workspaceId(), Map.of("jobId", job.id())
        ));

        WorkAssignment waiting = services.runnerService().runNextSynchronously();

        assertThat(waiting.status()).isEqualTo(OrchestrationStatus.WAITING);
        assertThat(waiting.currentItemIndex()).isEqualTo(2);
        assertThat(waiting.checkpoint()).containsEntry("waitingItemId", waitItem.id());
        assertThat(waiting.errorText()).isNull();
        assertThat(waiting.output()).hasSize(2);
    }

    @Test
    void leaseRecoveryAndEventReactionAssignmentCreationWork() throws Exception {
        Services services = services();
        AgentProfile agent = services.agentService().create(profile("agent-c", "main"));
        WorkAssignment running = services.repository().saveAssignment(new WorkAssignment(
            "stale", agent.id(), null, null, AssignmentType.REPORT, 0, OrchestrationStatus.RUNNING,
            null, null, 0, Map.of(), Map.of("message", "stale"), Map.of(), Map.of(), null,
            "old-owner", Instant.now().minusSeconds(30), null, null, Instant.now().minusSeconds(60), null
        ));
        services.reactionService().save(agent.id(), new AgentEventReaction(
            null, agent.id(), EventType.INBOX_MESSAGE_RECEIVED, Map.of("messageType", "ping"),
            ReactionActionType.ENQUEUE_ASSIGNMENT,
            Map.of("assignmentType", "REPORT", "input", Map.of("message", "reacted")), true, null, null
        ));

        assertThat(services.runnerService().recoverStaleLeases()).isEqualTo(1);
        assertThat(services.assignmentService().get(running.id()).status()).isEqualTo(OrchestrationStatus.INTERRUPTED);

        services.inboxService().send(agent.id(), new InboxMessage(
            null, agent.id(), "user", "ping", "wake", Map.of(), false, false, null, null
        ));

        assertThat(services.repository().findQueuedAssignments(10)).extracting(WorkAssignment::assignmentType)
            .contains(AssignmentType.REPORT);
    }

    @Test
    void runningAssignmentHeartbeatExtendsLease() throws Exception {
        SlowTaskChatService chatService = new SlowTaskChatService();
        Services services = services(chatService, 2, 1);
        AgentProfile agent = services.agentService().create(profile("agent-heartbeat", "main"));
        TaskDefinition task = services.taskService().saveTask(task());
        WorkAssignment assignment = services.assignmentService().create(new AssignmentRequest(
            agent.id(), null, null, AssignmentType.TASK_RUN, 0, null, null,
            Map.of("taskId", task.id(), "inputValues", Map.of("topic", "lease"))
        ));

        CompletableFuture<WorkAssignment> result = CompletableFuture.supplyAsync(() ->
            services.runnerService().runAssignment(assignment.id())
        );

        assertThat(chatService.started.await(1, TimeUnit.SECONDS)).isTrue();
        Instant firstLeaseExpiry = services.assignmentService().get(assignment.id()).leaseExpiresAt();
        Thread.sleep(1300);
        Instant extendedLeaseExpiry = services.assignmentService().get(assignment.id()).leaseExpiresAt();
        chatService.release.countDown();

        assertThat(result.get(2, TimeUnit.SECONDS).status()).isEqualTo(OrchestrationStatus.COMPLETED);
        assertThat(extendedLeaseExpiry).isAfter(firstLeaseExpiry);
    }

    @Test
    void heartbeatDoesNotExtendLeaseOwnedByAnotherRunner() throws Exception {
        Services services = services();
        AgentProfile agent = services.agentService().create(profile("agent-heartbeat-negative", "main"));
        Instant originalExpiry = Instant.now().plusSeconds(30);
        WorkAssignment assignment = services.repository().saveAssignment(new WorkAssignment(
            "owned-by-other", agent.id(), null, null, AssignmentType.REPORT, 0, OrchestrationStatus.RUNNING,
            null, null, 0, Map.of(), Map.of("message", "owned"), Map.of(), Map.of(), null,
            "other-runner", originalExpiry, null, null, Instant.now(), null
        ));

        int updated = services.repository().extendRunningLease(
            assignment.id(), "current-runner", originalExpiry.plusSeconds(60)
        );

        assertThat(updated).isZero();
        assertThat(services.assignmentService().get(assignment.id()).leaseExpiresAt()).isEqualTo(originalExpiry);
    }

    @Test
    void contextBearingTaskAndWorkflowRunsCreateDurableAssignments() throws Exception {
        Services services = services();
        AgentProfile agent = services.agentService().create(profile("agent-d", "main"));
        TaskDefinition task = services.taskService().saveTask(task());
        OrchestrationJob job = services.jobService().save(new OrchestrationJob(
            null, agent.id(), "Context job", null, "summary", null, OrchestrationStatus.QUEUED, null, null
        ));

        OrchestrationRunResult taskResult = services.orchestrationRunService().runTask(
            task.id(),
            Map.of("topic", "context"),
            new OrchestrationRunContext(null, job.id(), null, "planning", 9)
        );

        assertThat(taskResult.assignment().status()).isEqualTo(OrchestrationStatus.COMPLETED);
        assertThat(taskResult.assignment().agentId()).isEqualTo(agent.id());
        assertThat(taskResult.assignment().jobId()).isEqualTo(job.id());
        assertThat(taskResult.assignment().workspaceId()).isEqualTo(job.workspaceId());
        assertThat(taskResult.assignment().modelOverride()).isEqualTo("planning");
        assertThat(taskResult.assignment().priority()).isEqualTo(9);
        assertThat(taskResult.assignment().input()).containsEntry("taskId", task.id());
        assertThat(taskResult.runId()).isNotBlank();

        WorkflowDefinition workflow = services.workflowService().saveWorkflow(new WorkflowDefinition(
            null,
            "Context workflow",
            null,
            List.of(
                new WorkflowStep("first", task.id(), List.of(
                    new WorkflowInputBinding("topic", WorkflowBindingKind.LITERAL, "first", null, null)
                )),
                new WorkflowStep("second", task.id(), List.of(
                    new WorkflowInputBinding("topic", WorkflowBindingKind.LITERAL, "second", null, null)
                ))
            ),
            null,
            null
        ));

        OrchestrationRunResult workflowResult = services.orchestrationRunService().runWorkflow(
            workflow.id(),
            new OrchestrationRunContext(agent.id(), null, job.workspaceId(), "planning", 4)
        );

        assertThat(workflowResult.assignment().status()).isEqualTo(OrchestrationStatus.COMPLETED);
        assertThat(workflowResult.assignment().agentId()).isEqualTo(agent.id());
        assertThat(workflowResult.assignment().workspaceId()).isEqualTo(job.workspaceId());
        assertThat(workflowResult.assignment().modelOverride()).isEqualTo("planning");
        assertThat(workflowResult.assignment().priority()).isEqualTo(4);
        assertThat(workflowResult.assignment().input()).containsEntry("workflowId", workflow.id());
        assertThat(workflowResult.runId()).isNotBlank();
    }

    @Test
    void scheduleDueProcessingIsIdempotentPerDueInstant() throws Exception {
        Services services = services();
        AgentProfile agent = services.agentService().create(profile("agent-e", "main"));
        OrchestrationJob job = services.jobService().save(new OrchestrationJob(
            null, agent.id(), "Scheduled job", null, null, null, OrchestrationStatus.QUEUED, null, null
        ));
        Instant dueAt = Instant.now().minusSeconds(5);
        AgentSchedule schedule = services.scheduleService().save(agent.id(), new AgentSchedule(
            null, agent.id(), job.id(), Map.of("assignmentType", "JOB_RUN", "input", Map.of("jobId", job.id())),
            "0 0 * * * *", "UTC", true, dueAt, null, null
        ));

        services.scheduleService().pollDueSchedules();
        services.scheduleService().save(agent.id(), new AgentSchedule(
            schedule.id(), agent.id(), job.id(), schedule.assignmentTemplate(), schedule.cronExpression(),
            schedule.timezone(), true, dueAt, schedule.createdAt(), schedule.updatedAt()
        ));
        services.scheduleService().pollDueSchedules();

        assertThat(services.repository().findAssignmentsForAgent(agent.id()))
            .filteredOn(assignment -> assignment.assignmentType() == AssignmentType.JOB_RUN)
            .hasSize(1);
    }

    private Services services() throws Exception {
        return services(null, 300, 60);
    }

    private Services services(ChatService chatServiceOverride, long leaseSeconds, long heartbeatSeconds) throws Exception {
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
        ScheduleService scheduleService = new ScheduleService(repository, agentService, assignmentService, eventService);
        EventReactionService reactionService = new EventReactionService(repository, agentService);
        TaskService taskService = new TaskService(new TaskRepository(jdbcTemplate, objectMapper));
        ChatService chatService = chatServiceOverride == null ? new FakeTaskChatService(taskService) : chatServiceOverride;
        WorkflowService workflowService = new WorkflowService(new WorkflowRepository(jdbcTemplate, objectMapper), taskService, chatService);
        MagentaWorkExecutor executor = new MagentaWorkExecutor(Map.of(
            MagentaWorkKind.BACKGROUND_JOB, new MagentaWorkExecutor.LaneSettings("test-bg-", 1, 10)
        ));
        OrchestrationRunnerService runnerService = new OrchestrationRunnerService(
            repository, assignmentService, jobService, taskService, workflowService, chatService, inboxService,
            eventService, executor, leaseSeconds, heartbeatSeconds
        );
        OrchestrationRunService orchestrationRunService = new OrchestrationRunService(
            assignmentService, jobService, runnerService
        );
        return new Services(
            repository, agentService, taskService, jobService, assignmentService, inboxService, scheduleService,
            reactionService, runnerService, workflowService, orchestrationRunService
        );
    }

    private AgentProfile profile(String name, String model) {
        return new AgentProfile(null, name, AgentProfileStatus.ACTIVE, model, "Prompt", List.of(), List.of(), true, null, null);
    }

    private TaskDefinition task() {
        return new TaskDefinition(
            null, "Research", null, "Research a topic.", null, null,
            List.of(new TaskFieldDefinition("topic", TaskValueType.STRING, "Topic.", true, null, "SQLite")),
            null,
            List.of(new TaskFieldDefinition("notes", TaskValueType.LONG_TEXT, "Notes.", true, null, "notes")),
            List.of(),
            List.of(new TaskStep(1, "Research <topic>.")),
            List.of("notes exists"),
            null,
            null
        );
    }

    private AiConfig aiConfig() {
        Map<String, ModelConfig> models = Map.of(
            "main", model("main-remote"),
            "planning", model("planning-remote"),
            "summary", model("summary-remote")
        );
        return new AiConfig(
            "legacy", "main", "summary", "planning", "main", 10, tempDir, null, models,
            Map.of("legacy", new AgentConfig("main", "Legacy prompt", List.of(), List.of()))
        );
    }

    private ModelConfig model(String remoteName) {
        return new ModelConfig(remoteName, "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null);
    }

    private JdbcTemplate jdbcTemplate() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String statement : Files.readString(Path.of("src/main/resources/schema.sql")).split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
        return jdbcTemplate;
    }

    private record Services(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentService,
        TaskService taskService,
        OrchestrationJobService jobService,
        AssignmentService assignmentService,
        InboxService inboxService,
        ScheduleService scheduleService,
        EventReactionService reactionService,
        OrchestrationRunnerService runnerService,
        WorkflowService workflowService,
        OrchestrationRunService orchestrationRunService
    ) {
    }

    private static final class FakeTaskChatService extends ChatService {
        private final TaskService taskService;

        FakeTaskChatService(TaskService taskService) {
            super(null, null, null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(), null);
            this.taskService = taskService;
        }

        @Override
        public TaskExecutionResult executeTaskBlocking(
            String taskId,
            Map<String, Object> inputValues,
            String conversationId,
            String modelOverride
        ) {
            var run = taskService.startChatExecution(conversationId, taskId, inputValues);
            java.util.LinkedHashMap<String, Object> outputs = new java.util.LinkedHashMap<>();
            for (var output : run.taskSnapshot().outputs()) {
                outputs.put(output.name(), output.example() == null ? "completed " + output.name() : output.example());
            }
            var completed = taskService.completeRun(run.id(), outputs, "done", List.of("fake task_complete"));
            taskService.clearExecutionContext(conversationId);
            return new TaskExecutionResult(conversationId, completed, null);
        }
    }

    private static final class SlowTaskChatService extends ChatService {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        SlowTaskChatService() {
            super(null, null, null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(), null);
        }

        @Override
        public TaskExecutionResult executeTaskBlocking(
            String taskId,
            Map<String, Object> inputValues,
            String conversationId,
            String modelOverride
        ) {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for slow task release", exception);
            }
            return new TaskExecutionResult(conversationId, new TaskRun(
                "slow-run", taskId, TaskRunStatus.COMPLETED, inputValues, Map.of("notes", "done"),
                null, List.of("slow evidence"), List.of(), "done", null,
                Instant.now(), Instant.now(), Instant.now(), Instant.now()
            ), null);
        }
    }
}
