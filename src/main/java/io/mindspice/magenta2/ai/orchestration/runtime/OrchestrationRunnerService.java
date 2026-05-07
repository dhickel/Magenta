package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.execution.MagentaWorkExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrchestrationRunnerService {
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final OrchestrationRuntimeRepository repository;
    private final AssignmentService assignmentService;
    private final OrchestrationJobService jobService;
    private final TaskService taskService;
    private final WorkflowService workflowService;
    private final InboxService inboxService;
    private final OrchestrationEventService eventService;
    private final MagentaWorkExecutor executor;
    private final String leaseOwner = UUID.randomUUID().toString();

    public OrchestrationRunnerService(
        OrchestrationRuntimeRepository repository,
        AssignmentService assignmentService,
        OrchestrationJobService jobService,
        TaskService taskService,
        WorkflowService workflowService,
        InboxService inboxService,
        OrchestrationEventService eventService,
        MagentaWorkExecutor executor
    ) {
        this.repository = repository;
        this.assignmentService = assignmentService;
        this.jobService = jobService;
        this.taskService = taskService;
        this.workflowService = workflowService;
        this.inboxService = inboxService;
        this.eventService = eventService;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${magenta.orchestration.runner-delay-ms:2000}")
    public void pollQueuedWork() {
        recoverStaleLeases();
        for (WorkAssignment queued : repository.findQueuedAssignments(4)) {
            executor.submitBackground(queued.id(), queued.priority(), "orchestration assignment " + queued.id(), () -> {
                runAssignment(queued.id());
                return null;
            });
        }
    }

    public int recoverStaleLeases() {
        return repository.markStaleRunningLeases(Instant.now());
    }

    public WorkAssignment runNextSynchronously() {
        recoverStaleLeases();
        return repository.findQueuedAssignments(1).stream()
            .findFirst()
            .map(assignment -> runAssignment(assignment.id()))
            .orElse(null);
    }

    public WorkAssignment runAssignment(String assignmentId) {
        WorkAssignment leased = repository.acquireLease(assignmentId, leaseOwner, Instant.now().plus(LEASE_DURATION))
            .orElse(null);
        if (leased == null) {
            return assignmentService.get(assignmentId);
        }
        try {
            return switch (leased.assignmentType()) {
                case TASK_RUN -> runTask(leased);
                case WORKFLOW_RUN -> runWorkflow(leased);
                case JOB_RUN -> runJob(leased);
                case AGENT_MESSAGE -> runAgentMessage(leased);
                case WAIT_FOR_MESSAGE -> assignmentService.saveStatus(leased, OrchestrationStatus.WAITING);
                case REPORT -> complete(leased, Map.of("message", text(leased.input().get("message"), "Report completed.")),
                    Map.of("reports", List.of(text(leased.input().get("message"), "Report completed."))));
            };
        } catch (RuntimeException exception) {
            return fail(assignmentService.get(assignmentId), exception.getMessage());
        }
    }

    private WorkAssignment runTask(WorkAssignment assignment) {
        String taskId = text(assignment.input().get("taskId"), null);
        TaskRun taskRun = taskService.runSynchronously(taskId, mapValue(assignment.input().get("inputValues")));
        Map<String, Object> checkpoint = Map.of("taskRunId", taskRun.id(), "status", taskRun.status().name());
        Map<String, Object> output = Map.of("taskRunId", taskRun.id(), "outputValues", taskRun.outputValues());
        return complete(checkpointed(assignment, assignment.currentItemIndex(), checkpoint, output, evidence(taskRun)), output, evidence(taskRun));
    }

    private WorkAssignment runWorkflow(WorkAssignment assignment) {
        String workflowId = text(assignment.input().get("workflowId"), null);
        WorkflowRun workflowRun = workflowService.runSynchronously(workflowId);
        Map<String, Object> checkpoint = Map.of("workflowRunId", workflowRun.id(), "status", workflowRun.status().name());
        Map<String, Object> output = Map.of("workflowRunId", workflowRun.id(), "finalOutputs", workflowRun.finalOutputs());
        if (workflowRun.status().name().equals("COMPLETED")) {
            return complete(checkpointed(assignment, assignment.currentItemIndex(), checkpoint, output, output), output, output);
        }
        return fail(checkpointed(assignment, assignment.currentItemIndex(), checkpoint, output, output), workflowRun.errorText());
    }

    private WorkAssignment runJob(WorkAssignment assignment) {
        String jobId = StringUtils.hasText(assignment.jobId()) ? assignment.jobId() : text(assignment.input().get("jobId"), null);
        OrchestrationJob job = jobService.get(jobId);
        List<OrchestrationJobItem> items = jobService.items(job.id());
        Map<String, Object> outputs = new LinkedHashMap<>(assignment.output());
        Map<String, Object> evidence = new LinkedHashMap<>(assignment.evidence());
        int start = Math.max(assignment.currentItemIndex(), integer(assignment.checkpoint().get("nextItemIndex"), 0));
        WorkAssignment current = assignment;
        for (int i = start; i < items.size(); i++) {
            current = assignmentService.get(current.id());
            if (current.status() == OrchestrationStatus.CANCEL_REQUESTED) {
                return assignmentService.saveStatus(current, OrchestrationStatus.CANCELLED);
            }
            OrchestrationJobItem item = items.get(i);
            if (item.itemType() == AssignmentType.WAIT_FOR_MESSAGE) {
                Map<String, Object> checkpoint = Map.of(
                    "jobId", job.id(),
                    "nextItemIndex", i,
                    "waitingItemId", item.id(),
                    "model", assignmentService.resolveModel(current, item)
                );
                Map<String, Object> waitingEvidence = new LinkedHashMap<>(evidence);
                waitingEvidence.put(item.id(), Map.of("itemType", item.itemType().name(), "waitingSince", Instant.now().toString()));
                return assignmentService.save(assignmentService.copy(
                    current, OrchestrationStatus.WAITING, i, checkpoint, outputs, waitingEvidence,
                    null, null, null, null
                ));
            }
            Object itemOutput = runJobItem(current, item);
            outputs.put(item.id(), itemOutput);
            evidence.put(item.id(), Map.of("itemType", item.itemType().name(), "completedAt", Instant.now().toString()));
            current = checkpointed(current, i + 1, Map.of(
                "jobId", job.id(),
                "nextItemIndex", i + 1,
                "completedItemId", item.id(),
                "model", assignmentService.resolveModel(current, item)
            ), outputs, evidence);
            current = assignmentService.save(current);
        }
        eventService.publish(EventType.JOB_STATUS_CHANGED, "JOB", job.id(), Map.of("jobId", job.id(), "status", "COMPLETED"));
        return complete(current, outputs, evidence);
    }

    private Object runJobItem(WorkAssignment assignment, OrchestrationJobItem item) {
        return switch (item.itemType()) {
            case TASK_RUN -> {
                TaskRun run = taskService.runSynchronously(item.taskId(), mapValue(item.config().get("inputValues")));
                yield Map.of("taskRunId", run.id(), "outputValues", run.outputValues());
            }
            case WORKFLOW_RUN -> {
                WorkflowRun run = workflowService.runSynchronously(item.workflowId());
                if (!run.status().name().equals("COMPLETED")) {
                    throw new IllegalStateException("Workflow job item failed: " + run.errorText());
                }
                yield Map.of("workflowRunId", run.id(), "finalOutputs", run.finalOutputs());
            }
            case AGENT_MESSAGE -> {
                String toAgentId = text(item.config().get("toAgentId"), assignment.agentId());
                InboxMessage message = inboxService.send(toAgentId, new InboxMessage(
                    null, toAgentId, assignment.agentId(), text(item.config().get("messageType"), "job_message"),
                    text(item.config().get("body"), ""), mapValue(item.config().get("metadata")),
                    false, false, null, null
                ));
                yield Map.of("messageId", message.id());
            }
            case WAIT_FOR_MESSAGE -> throw new IllegalStateException("WAIT_FOR_MESSAGE pauses job execution");
            case REPORT -> Map.of("message", text(item.config().get("message"), "Report completed."));
            case JOB_RUN -> throw new IllegalArgumentException("Nested JOB_RUN items are not supported");
        };
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
            leaseOwner, Instant.now().plus(LEASE_DURATION), null
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
}
