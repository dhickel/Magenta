package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssignmentService {
    private final OrchestrationRuntimeRepository repository;
    private final AgentProfileService agentProfileService;
    private final RuntimeSettingsService runtimeSettingsService;
    private final JobService jobService;

    public AssignmentService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        RuntimeSettingsService runtimeSettingsService,
        JobService jobService
    ) {
        this.repository = repository;
        this.agentProfileService = agentProfileService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.jobService = jobService;
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
        agentProfileService.get(request.agentId());
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
            leaseOwner, leaseExpiresAt, assignment.createdAt(), assignment.updatedAt(), assignment.startedAt(), completedAt
        );
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
