package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import io.mindspice.magenta2.ai.chat.plan.PlanRun;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.repository.AuditRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssignmentService {
    private final OrchestrationRuntimeRepository repository;
    private final AgentProfileService agentProfileService;
    private final RuntimeSettingsService runtimeSettingsService;
    private final JobService jobService;
    private final AuditRepository auditRepository;
    private final PlanService planService;
    private final WorkflowService workflowService;
    private volatile Consumer<String> localInterruptHandler = ignored -> { };

    public AssignmentService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        RuntimeSettingsService runtimeSettingsService,
        JobService jobService
    ) {
        this(repository, agentProfileService, runtimeSettingsService, jobService, null, null, null);
    }

    @Autowired
    public AssignmentService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        RuntimeSettingsService runtimeSettingsService,
        JobService jobService,
        @Autowired(required = false) AuditRepository auditRepository,
        @Autowired(required = false) PlanService planService,
        @Autowired(required = false) WorkflowService workflowService
    ) {
        this.repository = repository;
        this.agentProfileService = agentProfileService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.jobService = jobService;
        this.auditRepository = auditRepository;
        this.planService = planService;
        this.workflowService = workflowService;
    }

    public WorkAssignment create(AssignmentRequest request) {
        return create(UUID.randomUUID().toString(), request);
    }

    WorkAssignment create(String assignmentId, AssignmentRequest request) {
        if (request.assignmentType() == null) {
            throw new IllegalArgumentException("assignmentType is required");
        }
        if (!StringUtils.hasText(request.agentId())) {
            throw new IllegalArgumentException("agentId is required");
        }
        AgentProfile agent = agentProfileService.get(request.agentId());
        if (agent.status() == io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus.DISABLED) {
            throw new IllegalStateException("Agent is disabled and cannot accept new assignments: " + request.agentId());
        }
        if (StringUtils.hasText(request.jobId())) {
            jobService.getDefinition(request.jobId());
        }
        Map<String, Object> input = request.input() == null ? Map.of() : request.input();
        validateInput(request.assignmentType(), input, request.jobId());
        return repository.saveAssignment(new WorkAssignment(
            assignmentId,
            request.agentId(),
            normalize(request.jobId()),
            normalize(request.jobItemId()),
            request.assignmentType(),
            request.priority() == null ? 0 : request.priority(),
            OrchestrationStatus.QUEUED,
            normalize(request.modelOverride()),
            normalize(request.workspaceId()),
            0,
            Map.of(),
            input,
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));
    }

    public WorkAssignment get(String assignmentId) {
        return repository.findAssignment(assignmentId)
            .orElseThrow(() -> new IllegalStateException("Assignment not found: " + assignmentId));
    }

    public java.util.List<WorkAssignment> assignments(String agentId) {
        agentProfileService.get(agentId);
        return repository.findAssignmentsForAgent(agentId);
    }

    public WorkAssignment cancel(String assignmentId) {
        WorkAssignment current = get(assignmentId);
        if (isTerminal(current.status())) {
            return current;
        }
        return saveStatus(current, current.status() == OrchestrationStatus.QUEUED
            ? OrchestrationStatus.CANCELLED
            : OrchestrationStatus.CANCEL_REQUESTED);
    }

    public WorkAssignment pause(String assignmentId) {
        WorkAssignment current = get(assignmentId);
        if (isTerminal(current.status())) {
            return current;
        }
        return saveStatus(current, OrchestrationStatus.PAUSED);
    }

    public WorkAssignment resume(String assignmentId) {
        WorkAssignment current = get(assignmentId);
        if (current.status() != OrchestrationStatus.PAUSED && current.status() != OrchestrationStatus.INTERRUPTED
            && current.status() != OrchestrationStatus.WAITING) {
            throw new IllegalStateException("Assignment is not resumable: " + assignmentId);
        }
        return saveStatus(current, OrchestrationStatus.QUEUED);
    }

    public WorkAssignment forceInterrupt(String assignmentId, String reason) {
        WorkAssignment current = get(assignmentId);
        if (isTerminal(current.status()) || current.status() == OrchestrationStatus.INTERRUPTED) {
            return current;
        }
        if (current.status() != OrchestrationStatus.RUNNING && current.status() != OrchestrationStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException("Assignment is not force-interruptible: " + assignmentId);
        }
        String operatorReason = StringUtils.hasText(reason) ? reason.trim() : "operator requested force interrupt";
        boolean updated = repository.forceInterruptAssignment(assignmentId, "Force interrupted: " + operatorReason);
        localInterruptHandler.accept(assignmentId);
        return updated ? get(assignmentId) : get(assignmentId);
    }

    public void registerLocalInterruptHandler(Consumer<String> handler) {
        this.localInterruptHandler = handler == null ? ignored -> { } : handler;
    }

    public AssignmentDiagnostics diagnostics(String assignmentId) {
        WorkAssignment assignment = get(assignmentId);
        Instant now = Instant.now();
        Instant progressAt = assignment.lastProgressAt() != null ? assignment.lastProgressAt() : assignment.updatedAt();
        Instant heartbeatAt = assignment.lastHeartbeatAt() != null ? assignment.lastHeartbeatAt() : assignment.updatedAt();
        Duration progressAge = age(now, progressAt);
        Duration heartbeatAge = age(now, heartbeatAt);
        boolean suspectedStuck = assignment.status() == OrchestrationStatus.RUNNING
            && progressAge != null
            && progressAge.compareTo(Duration.ofMinutes(15)) >= 0
            && heartbeatAge != null
            && heartbeatAge.compareTo(Duration.ofMinutes(5)) < 0;

        List<LinkedRunStatus> linkedRuns = linkedRuns(assignment);
        String conversationId = firstText(
            text(assignment.checkpoint().get("conversationId")),
            text(assignment.output().get("conversationId")),
            text(assignment.input().get("conversationId"))
        );
        List<AuditRepository.AuditEvent> auditEvents = auditRepository == null || !StringUtils.hasText(conversationId)
            ? List.of()
            : auditRepository.findByConversationId(conversationId).stream()
                .sorted(Comparator.comparingInt(AuditRepository.AuditEvent::sequence).reversed())
                .limit(12)
                .toList();
        return new AssignmentDiagnostics(
            assignment,
            progressAt,
            heartbeatAt,
            progressAge,
            heartbeatAge,
            suspectedStuck,
            linkedRuns,
            auditEvents,
            conversationId,
            buildCommit()
        );
    }

    public String resolveModel(WorkAssignment assignment, JobWorkItem item) {
        String explicit = assignment.modelOverride();
        String itemOverride = item == null ? null : item.modelOverride();
        JobDefinition job = StringUtils.hasText(assignment.jobId()) ? jobService.getDefinition(assignment.jobId()) : null;
        AgentProfile agent = agentProfileService.get(assignment.agentId());
        String key = firstText(explicit, itemOverride, job == null ? null : job.model(), agent.defaultModel());
        return runtimeSettingsService.resolveModel(key, null);
    }

    WorkAssignment save(WorkAssignment assignment) {
        return repository.saveAssignment(assignment);
    }

    WorkAssignment saveIfLeaseOwner(WorkAssignment assignment, String leaseOwner) {
        return repository.saveAssignmentIfLeaseOwner(assignment, leaseOwner)
            .orElseGet(() -> repository.findAssignment(assignment.id()).orElse(assignment));
    }

    WorkAssignment saveStatus(WorkAssignment assignment, OrchestrationStatus status) {
        Instant completedAt = isTerminal(status) ? Instant.now() : assignment.completedAt();
        return repository.saveAssignment(copy(
            assignment,
            status,
            assignment.currentItemIndex(),
            assignment.checkpoint(),
            assignment.output(),
            assignment.evidence(),
            assignment.errorText(),
            null,
            null,
            completedAt
        ));
    }

    WorkAssignment copy(
        WorkAssignment assignment,
        OrchestrationStatus status,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> output,
        Map<String, Object> evidence,
        String errorText,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant completedAt
    ) {
        return new WorkAssignment(
            assignment.id(), assignment.agentId(), assignment.jobId(), assignment.jobItemId(),
            assignment.assignmentType(), assignment.priority(), status, assignment.modelOverride(), assignment.workspaceId(),
            currentItemIndex, checkpoint == null ? Map.of() : checkpoint, assignment.input(),
            output == null ? Map.of() : output, evidence == null ? Map.of() : evidence, errorText,
            leaseOwner, leaseExpiresAt, assignment.createdAt(), assignment.updatedAt(), assignment.startedAt(), completedAt,
            assignment.lastProgressAt(), assignment.lastHeartbeatAt()
        );
    }

    private List<LinkedRunStatus> linkedRuns(WorkAssignment assignment) {
        List<LinkedRunStatus> runs = new ArrayList<>();
        addTaskRun(runs, firstText(
            text(assignment.checkpoint().get("taskRunId")),
            text(assignment.output().get("taskRunId")),
            text(assignment.checkpoint().get("planRunId")),
            text(assignment.output().get("planRunId"))
        ));
        addWorkflowRun(runs, firstText(
            text(assignment.checkpoint().get("workflowRunId")),
            text(assignment.output().get("workflowRunId"))
        ));
        addJobRun(runs, firstText(
            text(assignment.checkpoint().get("jobRunId")),
            text(assignment.output().get("jobRunId"))
        ));
        return runs;
    }

    private void addTaskRun(List<LinkedRunStatus> runs, String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        try {
            PlanRun planRun = planService == null ? null : planService.getRun(runId);
            if (planRun != null) {
                runs.add(new LinkedRunStatus("PLAN_RUN", planRun.id(), planRun.planId(), planRun.status().name(), planRun.errorText()));
            }
        } catch (RuntimeException ignored) {
            runs.add(new LinkedRunStatus("TASK_RUN", runId, null, "missing", null));
        }
    }

    private void addWorkflowRun(List<LinkedRunStatus> runs, String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        try {
            WorkflowRun run = workflowService == null ? null : workflowService.getRun(runId);
            if (run != null) {
                runs.add(new LinkedRunStatus("WORKFLOW_RUN", run.id(), run.workflowId(), run.status().name(), run.errorText()));
            }
        } catch (RuntimeException ignored) {
            runs.add(new LinkedRunStatus("WORKFLOW_RUN", runId, null, "missing", null));
        }
    }

    private void addJobRun(List<LinkedRunStatus> runs, String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        try {
            JobRun run = jobService == null ? null : jobService.getRun(runId);
            if (run != null) {
                runs.add(new LinkedRunStatus("JOB_RUN", run.id(), run.jobId(), run.status().name(), run.errorText()));
            }
        } catch (RuntimeException ignored) {
            runs.add(new LinkedRunStatus("JOB_RUN", runId, null, "missing", null));
        }
    }

    private void validateInput(AssignmentType type, Map<String, Object> input, String jobId) {
        if (type == AssignmentType.TASK_RUN && !StringUtils.hasText(text(input.get("taskId")))) {
            throw new IllegalArgumentException("TASK_RUN assignments require input.taskId");
        }
        if (type == AssignmentType.WORKFLOW_RUN && !StringUtils.hasText(text(input.get("workflowId")))) {
            throw new IllegalArgumentException("WORKFLOW_RUN assignments require input.workflowId");
        }
        if (type == AssignmentType.JOB_RUN && !StringUtils.hasText(jobId) && !StringUtils.hasText(text(input.get("jobId")))) {
            throw new IllegalArgumentException("JOB_RUN assignments require jobId");
        }
    }

    private boolean isTerminal(OrchestrationStatus status) {
        return status == OrchestrationStatus.COMPLETED || status == OrchestrationStatus.CANCELLED
            || status == OrchestrationStatus.FAILED || status == OrchestrationStatus.NEEDS_REVIEW;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Duration age(Instant now, Instant instant) {
        return instant == null ? null : Duration.between(instant, now);
    }

    private String buildCommit() {
        String commit = firstText(
            System.getenv("MAGENTA_BUILD_COMMIT"),
            System.getenv("GIT_COMMIT"),
            System.getProperty("magenta.build.commit"),
            System.getProperty("git.commit")
        );
        return commit == null ? "unknown" : commit;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record LinkedRunStatus(String type, String id, String parentId, String status, String errorText) {
    }

    public record AssignmentDiagnostics(
        WorkAssignment assignment,
        Instant lastProgressAt,
        Instant lastHeartbeatAt,
        Duration progressAge,
        Duration heartbeatAge,
        boolean suspectedStuck,
        List<LinkedRunStatus> linkedRuns,
        List<AuditRepository.AuditEvent> auditEvents,
        String conversationId,
        String buildCommit
    ) {
    }
}
