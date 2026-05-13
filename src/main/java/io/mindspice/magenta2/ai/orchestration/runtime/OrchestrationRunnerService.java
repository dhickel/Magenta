package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskRunStatus;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.execution.MagentaWorkExecutor;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrchestrationRunnerService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationRunnerService.class);
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMinutes(1);

    private final OrchestrationRuntimeRepository repository;
    private final AssignmentService assignmentService;
    private final JobService jobService;
    private final TaskService taskService;
    private final WorkflowService workflowService;
    private final ChatService chatService;
    private final InboxService inboxService;
    private final OrchestrationEventService eventService;
    private final MagentaWorkExecutor executor;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "orchestration-lease-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final String leaseOwner = UUID.randomUUID().toString();

    public OrchestrationRunnerService(
        OrchestrationRuntimeRepository repository,
        AssignmentService assignmentService,
        JobService jobService,
        TaskService taskService,
        WorkflowService workflowService,
        InboxService inboxService,
        OrchestrationEventService eventService,
        MagentaWorkExecutor executor
    ) {
        this(repository, assignmentService, jobService, taskService, workflowService, null, inboxService, eventService, executor);
    }

    @Autowired
    public OrchestrationRunnerService(
        OrchestrationRuntimeRepository repository,
        AssignmentService assignmentService,
        JobService jobService,
        TaskService taskService,
        WorkflowService workflowService,
        ChatService chatService,
        InboxService inboxService,
        OrchestrationEventService eventService,
        MagentaWorkExecutor executor,
        @Value("${magenta.orchestration.lease-seconds:300}") long leaseSeconds,
        @Value("${magenta.orchestration.heartbeat-seconds:60}") long heartbeatSeconds
    ) {
        this.repository = repository;
        this.assignmentService = assignmentService;
        this.jobService = jobService;
        this.taskService = taskService;
        this.workflowService = workflowService;
        this.chatService = chatService;
        this.inboxService = inboxService;
        this.eventService = eventService;
        this.executor = executor;
        this.leaseDuration = secondsOrDefault(leaseSeconds, DEFAULT_LEASE_DURATION);
        this.heartbeatInterval = secondsOrDefault(heartbeatSeconds, DEFAULT_HEARTBEAT_INTERVAL);
    }

    public OrchestrationRunnerService(
        OrchestrationRuntimeRepository repository,
        AssignmentService assignmentService,
        JobService jobService,
        TaskService taskService,
        WorkflowService workflowService,
        ChatService chatService,
        InboxService inboxService,
        OrchestrationEventService eventService,
        MagentaWorkExecutor executor
    ) {
        this(
            repository, assignmentService, jobService, taskService, workflowService, chatService, inboxService,
            eventService, executor, DEFAULT_LEASE_DURATION.toSeconds(), DEFAULT_HEARTBEAT_INTERVAL.toSeconds()
        );
    }

    @Scheduled(fixedDelayString = "${magenta.orchestration.runner-delay-ms:2000}")
    public void pollQueuedWork() {
        recoverStaleLeases();
        for (WorkAssignment queued : repository.findRecoverableAssignments(4)) {
            var leased = repository.acquireLease(queued.id(), leaseOwner, Instant.now().plus(leaseDuration));
            if (leased.isEmpty()) {
                continue;
            }
            WorkAssignment assignment = leased.get();
            try {
                executor.submitBackground(assignment.id(), assignment.priority(),
                    "orchestration assignment " + assignment.id(), () -> {
                    executeWithLease(assignment);
                    return null;
                });
            } catch (RejectedExecutionException e) {
                repository.revertToQueued(assignment.id(), leaseOwner);
            }
        }
    }

    public int recoverStaleLeases() {
        return repository.markStaleRunningLeases(Instant.now());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        int interrupted = recoverStaleLeases();
        logger.info("Startup recovery: marked {} stale RUNNING assignments as INTERRUPTED", interrupted);
        if (interrupted > 0) {
            logger.info("INTERRUPTED assignments will be picked up by the normal polling loop via findRecoverableAssignments");
        }
    }

    public WorkAssignment runNextSynchronously() {
        recoverStaleLeases();
        return repository.findRecoverableAssignments(1).stream()
            .findFirst()
            .map(assignment -> runAssignment(assignment.id()))
            .orElse(null);
    }

    public WorkAssignment runAssignment(String assignmentId) {
        return repository.acquireLease(assignmentId, leaseOwner, Instant.now().plus(leaseDuration))
            .map(this::executeWithLease)
            .orElseGet(() -> assignmentService.get(assignmentId));
    }

    private WorkAssignment executeWithLease(WorkAssignment leased) {
        ScheduledFuture<?> heartbeat = startLeaseHeartbeat(leased.id());
        try {
            return switch (leased.assignmentType()) {
                case TASK_RUN -> runTask(leased);
                case WORKFLOW_RUN -> runWorkflow(leased);
                case JOB_RUN -> runJob(leased);
                case AGENT_MESSAGE -> runAgentMessage(leased);
                case WAIT_FOR_MESSAGE -> assignmentService.saveStatus(leased, OrchestrationStatus.WAITING);
                case REPORT -> complete(leased,
                    Map.of("message", text(leased.input().get("message"), "Report completed.")),
                    Map.of("reports", List.of(text(leased.input().get("message"), "Report completed."))));
            };
        } catch (RuntimeException exception) {
            return fail(assignmentService.get(leased.id()), exception.getMessage());
        } finally {
            heartbeat.cancel(false);
        }
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    private ScheduledFuture<?> startLeaseHeartbeat(String assignmentId) {
        long heartbeatMillis = Math.max(1, heartbeatInterval.toMillis());
        return heartbeatExecutor.scheduleAtFixedRate(
            () -> repository.extendRunningLease(assignmentId, leaseOwner, Instant.now().plus(leaseDuration)),
            heartbeatMillis,
            heartbeatMillis,
            TimeUnit.MILLISECONDS
        );
    }

    private WorkAssignment runTask(WorkAssignment assignment) {
        String taskId = text(assignment.input().get("taskId"), null);
        TaskRun taskRun = runTaskThroughModel(taskId, mapValue(assignment.input().get("inputValues")), assignment,
            assignmentService.resolveModel(assignment, null));
        Map<String, Object> checkpoint = Map.of("taskRunId", taskRun.id(), "status", taskRun.status().name());
        Map<String, Object> output = Map.of("taskRunId", taskRun.id(), "outputValues", taskRun.outputValues());
        return complete(checkpointed(assignment, assignment.currentItemIndex(), checkpoint, output, evidence(taskRun)), output, evidence(taskRun));
    }

    private WorkAssignment runWorkflow(WorkAssignment assignment) {
        String workflowId = text(assignment.input().get("workflowId"), null);
        WorkflowRun workflowRun = workflowService.runSynchronously(workflowId, assignmentService.resolveModel(assignment, null));
        Map<String, Object> checkpoint = Map.of("workflowRunId", workflowRun.id(), "status", workflowRun.status().name());
        Map<String, Object> output = Map.of("workflowRunId", workflowRun.id(), "finalOutputs", workflowRun.finalOutputs());
        if (workflowRun.status().name().equals("COMPLETED")) {
            return complete(checkpointed(assignment, assignment.currentItemIndex(), checkpoint, output, output), output, output);
        }
        return fail(checkpointed(assignment, assignment.currentItemIndex(), checkpoint, output, output), workflowRun.errorText());
    }

    private WorkAssignment runJob(WorkAssignment assignment) {
        String jobId = StringUtils.hasText(assignment.jobId()) ? assignment.jobId() : text(assignment.input().get("jobId"), null);
        JobDefinition job = jobService.getDefinition(jobId);
        List<JobWorkItem> items = job.items();
        Map<String, Object> outputs = new LinkedHashMap<>(assignment.output());
        Map<String, Object> evidence = new LinkedHashMap<>(assignment.evidence());
        int start = Math.max(assignment.currentItemIndex(), integer(assignment.checkpoint().get("nextItemIndex"), 0));
        WorkAssignment current = assignment;
        for (int i = start; i < items.size(); i++) {
            current = assignmentService.get(current.id());
            if (current.status() == OrchestrationStatus.CANCEL_REQUESTED) {
                return assignmentService.saveStatus(current, OrchestrationStatus.CANCELLED);
            }
            JobWorkItem item = items.get(i);
            JobItemResult itemResult = runJobItem(current, assignment, item);
            outputs.put(item.key(), itemResult.output());
            evidence.put(item.key(), itemResult.evidence());
            if (!itemResult.succeeded()) {
                String errorText = itemResult.errorText();
                current = checkpointed(current, i + 1, Map.of(
                    "jobId", job.id(),
                    "nextItemIndex", i + 1,
                    "failedItemKey", item.key(),
                    "model", assignmentService.resolveModel(current, item)
                ), outputs, evidence);
                return fail(current, errorText);
            }
            current = checkpointed(current, i + 1, Map.of(
                "jobId", job.id(),
                "nextItemIndex", i + 1,
                "completedItemKey", item.key(),
                "model", assignmentService.resolveModel(current, item)
            ), outputs, evidence);
            current = assignmentService.save(current);
        }
        eventService.publish(EventType.JOB_STATUS_CHANGED, "JOB", job.id(), Map.of("jobId", job.id(), "status", "COMPLETED"));
        return complete(current, outputs, evidence);
    }

    private JobItemResult runJobItem(WorkAssignment assignment, WorkAssignment current, JobWorkItem item) {
        try {
            Object output = switch (item.type()) {
                case PLAN -> {
                    if (!StringUtils.hasText(item.planId())) {
                        throw new IllegalArgumentException("PLAN work item '" + item.key() + "' has no planId");
                    }
                    TaskRun run = runTaskThroughModel(item.planId(), item.inputBindings(), assignment,
                        assignmentService.resolveModel(current, item));
                    yield Map.of("planRunId", run.id(), "outputValues", run.outputValues());
                }
                case WORKFLOW -> {
                    if (!StringUtils.hasText(item.workflowId())) {
                        throw new IllegalArgumentException("WORKFLOW work item '" + item.key() + "' has no workflowId");
                    }
                    WorkflowRun run = workflowService.runSynchronously(item.workflowId(),
                        assignmentService.resolveModel(current, item));
                    if (!run.status().name().equals("COMPLETED")) {
                        throw new IllegalStateException("Workflow job item failed: " + run.errorText());
                    }
                    yield Map.of("workflowRunId", run.id(), "finalOutputs", run.finalOutputs());
                }
            };
            return new JobItemResult(true, output, Map.of(
                "itemType", item.type().name(),
                "itemKey", item.key(),
                "completedAt", Instant.now().toString()
            ), null);
        } catch (RuntimeException exception) {
            String error = exception.getMessage();
            return new JobItemResult(false, Map.of("failed", true, "error", error), Map.of(
                "itemType", item.type().name(),
                "itemKey", item.key(),
                "failedAt", Instant.now().toString(),
                "error", error
            ), error);
        }
    }

    private TaskRun runTaskThroughModel(
        String taskId,
        Map<String, Object> inputValues,
        WorkAssignment assignment,
        String modelOverride
    ) {
        if (chatService == null) {
            throw new IllegalStateException("Task execution requires model-backed chat execution");
        }
        TaskRun run = chatService.executeTaskBlocking(
            taskId,
            inputValues,
            UUID.randomUUID().toString(),
            modelOverride
        ).run();
        if (run.status() != TaskRunStatus.COMPLETED) {
            throw new IllegalStateException("Task run did not complete: "
                + (run.errorText() == null ? run.status().name() : run.errorText()));
        }
        return run;
    }

    private WorkAssignment runAgentMessage(WorkAssignment assignment) {
        String toAgentId = text(assignment.input().get("toAgentId"), assignment.agentId());
        InboxMessage message = inboxService.send(toAgentId, new InboxMessage(
            null, toAgentId, assignment.agentId(), text(assignment.input().get("messageType"), "assignment_message"),
            text(assignment.input().get("body"), ""), mapValue(assignment.input().get("metadata")),
            false, false, null, null
        ));
        return complete(assignment, Map.of("messageId", message.id()), Map.of("messageId", message.id()));
    }

    private WorkAssignment checkpointed(
        WorkAssignment assignment,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> output,
        Map<String, Object> evidence
    ) {
        return assignmentService.copy(
            assignment, OrchestrationStatus.RUNNING, currentItemIndex, checkpoint, output, evidence, null,
            leaseOwner, Instant.now().plus(leaseDuration), null
        );
    }

    private WorkAssignment complete(WorkAssignment assignment, Map<String, Object> output, Map<String, Object> evidence) {
        return assignmentService.save(assignmentService.copy(
            assignment, OrchestrationStatus.COMPLETED, assignment.currentItemIndex(), assignment.checkpoint(), output,
            evidence, null, null, null, Instant.now()
        ));
    }

    private WorkAssignment fail(WorkAssignment assignment, String errorText) {
        return assignmentService.save(assignmentService.copy(
            assignment, OrchestrationStatus.FAILED, assignment.currentItemIndex(), assignment.checkpoint(),
            assignment.output(), assignment.evidence(), errorText, null, null, Instant.now()
        ));
    }

    private Map<String, Object> evidence(TaskRun taskRun) {
        return Map.of("taskRunId", taskRun.id(), "evidence", taskRun.executionEvidence());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private int integer(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private Duration secondsOrDefault(long seconds, Duration fallback) {
        return seconds > 0 ? Duration.ofSeconds(seconds) : fallback;
    }

    private record JobItemResult(boolean succeeded, Object output, Map<String, Object> evidence, String errorText) {
    }
}
