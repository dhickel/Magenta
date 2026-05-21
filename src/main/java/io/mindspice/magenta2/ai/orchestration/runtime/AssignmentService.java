package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import io.mindspice.magenta2.ai.orchestration.workspaces.EffectiveWorkspace;
import io.mindspice.magenta2.ai.orchestration.workspaces.EffectiveWorkspaceResolver;
import io.mindspice.magenta2.ai.orchestration.workspaces.Workspace;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLeaseService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final EffectiveWorkspaceResolver effectiveWorkspaceResolver;
    private final WorkspaceService workspaceService;
    private final WorkspaceLeaseService workspaceLeaseService;
    private volatile Consumer<String> localInterruptHandler = ignored -> { };

    public AssignmentService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        RuntimeSettingsService runtimeSettingsService,
        JobService jobService
    ) {
        this(repository, agentProfileService, runtimeSettingsService, jobService, null, null, null);
    }

    public AssignmentService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        RuntimeSettingsService runtimeSettingsService,
        JobService jobService,
        @Autowired(required = false) AuditRepository auditRepository,
        @Autowired(required = false) PlanService planService,
        @Autowired(required = false) WorkflowService workflowService
    ) {
        this(repository, agentProfileService, runtimeSettingsService, jobService, auditRepository, planService,
            workflowService, null, null, null);
    }

    @Autowired
    public AssignmentService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        RuntimeSettingsService runtimeSettingsService,
        JobService jobService,
        @Autowired(required = false) AuditRepository auditRepository,
        @Autowired(required = false) PlanService planService,
        @Autowired(required = false) WorkflowService workflowService,
        @Autowired(required = false) EffectiveWorkspaceResolver effectiveWorkspaceResolver,
        @Autowired(required = false) WorkspaceService workspaceService,
        @Autowired(required = false) WorkspaceLeaseService workspaceLeaseService
    ) {
        this.repository = repository;
        this.agentProfileService = agentProfileService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.jobService = jobService;
        this.auditRepository = auditRepository;
        this.planService = planService;
        this.workflowService = workflowService;
        this.effectiveWorkspaceResolver = effectiveWorkspaceResolver;
        this.workspaceService = workspaceService;
        this.workspaceLeaseService = workspaceLeaseService;
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
        Map<String, Object> input = inputWithProjectContext(request);
        String projectId = normalize(firstText(request.projectId(), text(input.get("projectId"))));
        validateWorkspaceCompatibility(projectId, request.workspaceId());
        EffectiveWorkspace effectiveWorkspace = resolveEffectiveWorkspace(request.agentId(), projectId);
        AssignmentTemplateParser.validate(new AssignmentRequest(
            request.agentId(),
            request.jobId(),
            request.jobItemId(),
            request.assignmentType(),
            request.priority(),
            request.modelOverride(),
            projectId,
            request.workspaceId(),
            input
        ));
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
            projectId,
            effectiveWorkspace == null ? null : effectiveWorkspace.workspaceId(),
            effectiveWorkspace == null ? null : effectiveWorkspace.ownerType().name(),
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

    public java.util.List<WorkAssignment> queueAssignments(String agentId) {
        agentProfileService.get(agentId);
        return repository.findQueueAssignmentsForAgent(agentId);
    }

    public java.util.List<WorkAssignment> historyAssignments(String agentId) {
        agentProfileService.get(agentId);
        return repository.findTerminalAssignmentsForAgent(agentId);
    }

    public void delete(String agentId, String assignmentId) {
        agentProfileService.get(agentId);
        WorkAssignment assignment = get(assignmentId);
        if (!agentId.equals(assignment.agentId())) {
            throw new IllegalArgumentException("Assignment does not belong to agent: " + assignmentId);
        }
        if (assignment.status() != null && assignment.status().isTerminal()) {
            throw new IllegalStateException("Terminal assignments are retained in History; use history purge to remove them: " + assignmentId);
        }
        if (!deletable(assignment.status())) {
            throw new IllegalStateException("Running assignments cannot be deleted: " + assignmentId);
        }
        if (!repository.deleteAssignment(agentId, assignmentId)) {
            throw new IllegalStateException("Assignment not found: " + assignmentId);
        }
    }

    public int purgeHistory(String agentId, int olderThanDays) {
        agentProfileService.get(agentId);
        if (olderThanDays < 1) {
            throw new IllegalArgumentException("olderThanDays must be at least 1");
        }
        return repository.purgeTerminalAssignmentHistory(agentId, Instant.now().minus(Duration.ofDays(olderThanDays)));
    }

    @Scheduled(fixedDelayString = "${magenta.orchestration.assignment-history-purge-delay-ms:3600000}")
    public void autoPurgeHistory() {
        RuntimeSettingsSnapshot settings = RuntimeSettingsSnapshot.from(runtimeSettingsService.get());
        if (settings.assignmentHistoryAutoPurgeDays() == -1) {
            return;
        }
        repository.purgeTerminalAssignmentHistory(Instant.now().minus(Duration.ofDays(settings.assignmentHistoryAutoPurgeDays())));
    }

    WorkAssignment cancel(String assignmentId) {
        return cancel(get(assignmentId));
    }

    public WorkAssignment cancel(String agentId, String assignmentId) {
        return cancel(requireAgentAssignment(agentId, assignmentId));
    }

    private WorkAssignment cancel(WorkAssignment current) {
        if (isTerminal(current.status())) {
            return current;
        }
        if (current.status() == OrchestrationStatus.RUNNING) {
            String assignmentId = current.id();
            WorkAssignment cancelRequested = repository.requestCancel(assignmentId).orElseGet(() -> get(assignmentId));
            localInterruptHandler.accept(assignmentId);
            return cancelRequested;
        }
        if (current.status() == OrchestrationStatus.CANCEL_REQUESTED) {
            return current;
        }
        return saveStatus(current, OrchestrationStatus.CANCELLED);
    }

    WorkAssignment pause(String assignmentId) {
        return pause(get(assignmentId));
    }

    public WorkAssignment pause(String agentId, String assignmentId) {
        return pause(requireAgentAssignment(agentId, assignmentId));
    }

    private WorkAssignment pause(WorkAssignment current) {
        if (isTerminal(current.status())) {
            return current;
        }
        return saveStatus(current, OrchestrationStatus.PAUSED);
    }

    WorkAssignment resume(String assignmentId) {
        return resume(get(assignmentId));
    }

    public WorkAssignment resume(String agentId, String assignmentId) {
        return resume(requireAgentAssignment(agentId, assignmentId));
    }

    private WorkAssignment resume(WorkAssignment current) {
        if (current.status() != OrchestrationStatus.PAUSED && current.status() != OrchestrationStatus.INTERRUPTED
            && current.status() != OrchestrationStatus.WAITING) {
            throw new IllegalStateException("Assignment is not resumable: " + current.id());
        }
        return saveStatus(current, OrchestrationStatus.QUEUED);
    }

    WorkAssignment forceInterrupt(String assignmentId, String reason) {
        return forceInterrupt(get(assignmentId), reason);
    }

    public WorkAssignment forceInterrupt(String agentId, String assignmentId, String reason) {
        return forceInterrupt(requireAgentAssignment(agentId, assignmentId), reason);
    }

    private WorkAssignment forceInterrupt(WorkAssignment current, String reason) {
        if (isTerminal(current.status()) || current.status() == OrchestrationStatus.INTERRUPTED) {
            return current;
        }
        if (current.status() != OrchestrationStatus.RUNNING && current.status() != OrchestrationStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException("Assignment is not force-interruptible: " + current.id());
        }
        String operatorReason = StringUtils.hasText(reason) ? reason.trim() : "operator requested force interrupt";
        boolean updated = repository.forceInterruptAssignment(current.id(), "Force interrupted: " + operatorReason);
        localInterruptHandler.accept(current.id());
        return updated ? get(current.id()) : get(current.id());
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
        List<String> conversationIds = conversationIds(assignment);
        String conversationId = conversationIds.isEmpty() ? null : String.join(", ", conversationIds);
        List<AuditRepository.AuditEvent> auditEvents = auditRepository == null || conversationIds.isEmpty()
            ? List.of()
            : auditRepository.findByConversationIds(conversationIds).stream()
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

    private WorkAssignment requireAgentAssignment(String agentId, String assignmentId) {
        agentProfileService.get(agentId);
        WorkAssignment assignment = get(assignmentId);
        if (!agentId.equals(assignment.agentId())) {
            throw new IllegalArgumentException("Assignment does not belong to agent: " + assignmentId);
        }
        return assignment;
    }

    public AssignmentTranscript transcript(String agentId, String assignmentId) {
        agentProfileService.get(agentId);
        WorkAssignment assignment = get(assignmentId);
        if (!agentId.equals(assignment.agentId())) {
            throw new IllegalArgumentException("Assignment does not belong to agent: " + assignmentId);
        }
        List<String> conversationIds = conversationIds(assignment);
        List<AuditRepository.AuditEvent> auditEvents = auditRepository == null || conversationIds.isEmpty()
            ? List.of()
            : auditRepository.findByConversationIds(conversationIds);
        return new AssignmentTranscript(assignment, conversationIds, auditEvents);
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

    public WorkAssignment repairEffectiveWorkspaceContext(WorkAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        String projectId = normalize(firstText(assignment.projectId(), text(assignment.input().get("projectId"))));
        if (StringUtils.hasText(projectId)) {
            validateWorkspaceCompatibility(projectId, assignment.workspaceId());
        }
        if (StringUtils.hasText(assignment.effectiveWorkspaceId())
            && StringUtils.hasText(assignment.effectiveWorkspaceKind())
            && safeEquals(projectId, assignment.projectId())) {
            return assignment;
        }
        EffectiveWorkspace effectiveWorkspace = resolveEffectiveWorkspace(assignment.agentId(), projectId);
        if (effectiveWorkspace == null) {
            return assignment;
        }
        return repository.saveAssignment(new WorkAssignment(
            assignment.id(), assignment.agentId(), assignment.jobId(), assignment.jobItemId(),
            assignment.assignmentType(), assignment.priority(), assignment.status(), assignment.modelOverride(),
            assignment.workspaceId(), projectId, effectiveWorkspace.workspaceId(), effectiveWorkspace.ownerType().name(),
            assignment.currentItemIndex(), assignment.checkpoint(), assignment.input(), assignment.output(), assignment.evidence(),
            assignment.errorText(), assignment.leaseOwner(), assignment.leaseExpiresAt(), assignment.createdAt(),
            assignment.updatedAt(), assignment.startedAt(), assignment.completedAt(), assignment.lastProgressAt(),
            assignment.lastHeartbeatAt()
        ));
    }

    public AssignmentSummary summary(String assignmentId) {
        return summary(get(assignmentId));
    }

    public List<AssignmentSummary> summariesForAgent(String agentId) {
        return assignments(agentId).stream().map(this::summary).toList();
    }

    public List<AssignmentSummary> activeSummariesForProject(String projectId) {
        return repository.findActiveAssignmentsForProject(projectId).stream().map(this::summary).toList();
    }

    public List<AssignmentSummary> activeSummariesForEffectiveWorkspace(String workspaceId) {
        return repository.findActiveAssignmentsForEffectiveWorkspace(workspaceId).stream().map(this::summary).toList();
    }

    public boolean hasActiveAssignmentsForProject(String projectId) {
        return repository.countActiveAssignmentsForProject(projectId) > 0;
    }

    public boolean hasActiveAssignmentsForJob(String jobId) {
        return repository.countActiveAssignmentsForJob(jobId) > 0;
    }

    public boolean hasActiveAssignmentsForEffectiveWorkspace(String workspaceId) {
        return repository.countActiveAssignmentsForEffectiveWorkspace(workspaceId) > 0;
    }

    public WorkAssignment requeueWorkspaceBlockedAssignment(String assignmentId) {
        WorkAssignment assignment = get(assignmentId);
        if (assignment.status() != OrchestrationStatus.WAITING || !assignment.checkpoint().containsKey("workspaceBlocker")) {
            throw new IllegalStateException("Assignment is not waiting on a workspace lease: " + assignmentId);
        }
        String workspaceId = firstText(
            assignment.effectiveWorkspaceId(),
            text(assignment.checkpoint().get("projectWorkspaceId")),
            text(assignment.checkpoint().get("effectiveWorkspaceId"))
        );
        if (!StringUtils.hasText(workspaceId)) {
            throw new IllegalStateException("Workspace-blocked assignment has no effective workspace id: " + assignmentId);
        }
        if (workspaceLeaseService == null) {
            throw new IllegalStateException("Workspace lease service is unavailable");
        }
        if (workspaceLeaseService.activeWritableLease(workspaceId).isPresent()) {
            throw new IllegalStateException("Workspace still has an active writable lease: " + workspaceId);
        }
        Map<String, Object> checkpoint = new LinkedHashMap<>(assignment.checkpoint());
        Object blocker = checkpoint.remove("workspaceBlocker");
        if (blocker != null) {
            checkpoint.put("lastWorkspaceBlocker", blocker);
        }
        return repository.saveAssignment(copy(
            assignment, OrchestrationStatus.QUEUED, assignment.currentItemIndex(), checkpoint,
            assignment.output(), assignment.evidence(), null, null, null, assignment.completedAt()
        ));
    }

    public int requeueWorkspaceBlockedAssignments(int limit) {
        int boundedLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        int requeued = 0;
        for (WorkAssignment assignment : repository.findWaitingAssignments(boundedLimit)) {
            if (!assignment.checkpoint().containsKey("workspaceBlocker")) {
                continue;
            }
            try {
                requeueWorkspaceBlockedAssignment(assignment.id());
                requeued++;
            } catch (IllegalStateException ignored) {
                // Still blocked or not enough context; leave it WAITING for a later operator pass.
            }
        }
        return requeued;
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
            assignment.projectId(), assignment.effectiveWorkspaceId(), assignment.effectiveWorkspaceKind(),
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

    private boolean isTerminal(OrchestrationStatus status) {
        return status == OrchestrationStatus.COMPLETED || status == OrchestrationStatus.CANCELLED
            || status == OrchestrationStatus.FAILED || status == OrchestrationStatus.NEEDS_REVIEW;
    }

    private AssignmentSummary summary(WorkAssignment assignment) {
        return new AssignmentSummary(
            assignment.id(),
            assignment.agentId(),
            assignment.jobId(),
            assignment.jobItemId(),
            assignment.assignmentType(),
            assignment.priority(),
            assignment.status(),
            assignment.projectId(),
            assignment.workspaceId(),
            assignment.effectiveWorkspaceId(),
            assignment.effectiveWorkspaceKind(),
            effectiveWorkspaceDisplayPath(assignment.effectiveWorkspaceId()),
            text(assignment.checkpoint().get("workspaceBlocker")),
            text(assignment.checkpoint().get("jobRunId")),
            text(assignment.checkpoint().get("workflowRunId")),
            firstText(text(assignment.checkpoint().get("taskRunId")), text(assignment.checkpoint().get("planRunId"))),
            assignment.createdAt(),
            assignment.updatedAt(),
            assignment.startedAt(),
            assignment.completedAt()
        );
    }

    private String effectiveWorkspaceDisplayPath(String workspaceId) {
        if (!StringUtils.hasText(workspaceId) || workspaceService == null) {
            return null;
        }
        try {
            Workspace workspace = workspaceService.get(workspaceId);
            return workspace.rootRelativePath();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public boolean deletable(OrchestrationStatus status) {
        return status != null
            && !status.isTerminal()
            && status != OrchestrationStatus.RUNNING
            && status != OrchestrationStatus.CANCEL_REQUESTED;
    }

    private record RuntimeSettingsSnapshot(int assignmentHistoryAutoPurgeDays) {
        private static RuntimeSettingsSnapshot from(io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings settings) {
            Integer days = settings.assignmentHistoryAutoPurgeDays();
            return new RuntimeSettingsSnapshot(days == null ? -1 : days);
        }
    }

    private List<String> conversationIds(WorkAssignment assignment) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectConversationIds(ids, assignment.checkpoint());
        collectConversationIds(ids, assignment.output());
        repository.findAssignmentConversationIds(assignment.id()).forEach(ids::add);
        collectConversationIds(ids, assignment.evidence());
        collectConversationIds(ids, assignment.input());
        String taskId = text(assignment.input().get("taskId"));
        Instant windowStart = assignment.startedAt() != null ? assignment.startedAt() : assignment.createdAt();
        Instant windowEnd = firstInstant(assignment.completedAt(), assignment.updatedAt(), Instant.now());
        repository.findLegacyTaskConversationIds(taskId, windowStart, windowEnd).forEach(ids::add);
        return List.copyOf(ids);
    }

    @SuppressWarnings("unchecked")
    private void collectConversationIds(LinkedHashSet<String> ids, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().toString();
                Object child = entry.getValue();
                if ("conversationId".equals(key) || "activeConversationId".equals(key)) {
                    addConversationId(ids, child);
                } else if ("conversationIds".equals(key)) {
                    collectConversationIds(ids, child);
                } else if (child instanceof Map<?, ?> || child instanceof Iterable<?>) {
                    collectConversationIds(ids, child);
                }
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                if (child instanceof Map<?, ?> || child instanceof Iterable<?>) {
                    collectConversationIds(ids, child);
                } else {
                    addConversationId(ids, child);
                }
            }
        }
    }

    private void addConversationId(LinkedHashSet<String> ids, Object value) {
        String text = text(value);
        if (StringUtils.hasText(text)) {
            ids.add(text);
        }
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

    private Instant firstInstant(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private boolean safeEquals(String left, String right) {
        return java.util.Objects.equals(normalize(left), normalize(right));
    }

    private EffectiveWorkspace resolveEffectiveWorkspace(String agentId, String projectId) {
        if (effectiveWorkspaceResolver == null) {
            return null;
        }
        return effectiveWorkspaceResolver.resolve(agentId, projectId);
    }

    private void validateWorkspaceCompatibility(String projectId, String workspaceId) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(workspaceId) || workspaceService == null) {
            return;
        }
        try {
            Workspace workspace = workspaceService.get(workspaceId.trim());
            if (workspace.ownerType() == WorkspaceOwnerType.PROJECT
                && !projectId.trim().equals(workspace.ownerId())) {
                throw new IllegalArgumentException(
                    "workspaceId belongs to project " + workspace.ownerId()
                    + " but projectId is " + projectId.trim()
                    + "; projectId is the only project-scoping field"
                );
            }
        } catch (IllegalStateException ignored) {
            // Unknown compatibility workspace ids remain metadata and do not select project execution.
        }
    }

    private Map<String, Object> inputWithProjectContext(AssignmentRequest request) {
        Map<String, Object> input = request.input() == null ? Map.of() : request.input();
        String projectId = firstText(request.projectId(), text(input.get("projectId")));
        if (!StringUtils.hasText(projectId)) {
            return input;
        }
        Map<String, Object> merged = new LinkedHashMap<>(input);
        merged.put("projectId", projectId.trim());
        return merged;
    }

    public record LinkedRunStatus(String type, String id, String parentId, String status, String errorText) {
    }

    public record AssignmentSummary(
        String id,
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        int priority,
        OrchestrationStatus status,
        String projectId,
        String workspaceId,
        String effectiveWorkspaceId,
        String effectiveWorkspaceKind,
        String effectiveWorkspaceDisplayPath,
        String workspaceBlocker,
        String jobRunId,
        String workflowRunId,
        String taskRunId,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
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

    public record AssignmentTranscript(
        WorkAssignment assignment,
        List<String> conversationIds,
        List<AuditRepository.AuditEvent> auditEvents
    ) {
    }
}
