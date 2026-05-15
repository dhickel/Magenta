package io.mindspice.magenta2.ai.orchestration.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Manages job definitions, runs, and recurrence scheduling.
 * Jobs own persistent workspaces and coordinate multiple plan/workflow work items.
 */
@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final WorkspaceDirectoryService workspaceDirectoryService;
    private final PlanService planService;
    private final WorkflowService workflowService;

    public JobService(JobRepository jobRepository,
                      WorkspaceDirectoryService workspaceDirectoryService,
                      PlanService planService,
                      WorkflowService workflowService) {
        this.jobRepository = jobRepository;
        this.workspaceDirectoryService = workspaceDirectoryService;
        this.planService = planService;
        this.workflowService = workflowService;
    }

    // ════════════════════════════════════════════════════════════════
    //  Definition CRUD
    // ════════════════════════════════════════════════════════════════

    public List<JobDefinition> listDefinitions() {
        return jobRepository.findAllDefinitions();
    }

    public List<JobDefinition> listDefinitions(String agentId, String projectId, String status) {
        if (!StringUtils.hasText(agentId) && !StringUtils.hasText(projectId) && !StringUtils.hasText(status)) {
            return listDefinitions();
        }
        return jobRepository.findDefinitions(agentId, projectId, status);
    }

    public JobDefinition getDefinition(String id) {
        return jobRepository.findDefinition(id)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    public JobDefinition saveDefinition(JobDefinition def) {
        String id = StringUtils.hasText(def.id()) ? def.id() : UUID.randomUUID().toString();
        if (!StringUtils.hasText(def.title())) {
            throw new IllegalArgumentException("Job title is required");
        }
        List<JobWorkItem> items = def.items() == null ? List.of() : def.items();
        // Validate items reference valid plan or workflow ids
        for (JobWorkItem item : items) {
            if (item.type() == JobWorkItemType.PLAN && !StringUtils.hasText(item.planId())) {
                throw new IllegalArgumentException(
                    "Work item '" + item.key() + "' has type PLAN but no planId");
            }
            if (item.type() == JobWorkItemType.WORKFLOW && !StringUtils.hasText(item.workflowId())) {
                throw new IllegalArgumentException(
                    "Work item '" + item.key() + "' has type WORKFLOW but no workflowId");
            }
        }
        Instant now = Instant.now();
        Instant createdAt = def.createdAt() == null ? now : def.createdAt();
        return jobRepository.saveDefinition(new JobDefinition(
            id,
            normalize(def.ownerAgentId()),
            normalize(def.projectId()),
            normalize(def.workspaceId()),
            StringUtils.hasText(def.status()) ? def.status().trim() : "DRAFT",
            def.title(), def.summary(),
            orderedItems(items),
            def.promptProfile(), def.model(), def.settingsOverrideJson(),
            createdAt, def.updatedAt()
        ));
    }

    public List<JobWorkItem> listItems(String jobId) {
        return getDefinition(jobId).items();
    }

    public JobWorkItem addItem(String jobId, JobWorkItem item) {
        JobDefinition definition = getDefinition(jobId);
        JobWorkItem normalized = normalizeItem(item, definition.items().size());
        List<JobWorkItem> items = new ArrayList<>(definition.items());
        items.add(normalized);
        saveDefinition(withItems(definition, items));
        return normalized;
    }

    public JobWorkItem updateItem(String jobId, String itemKey, JobWorkItem item) {
        JobDefinition definition = getDefinition(jobId);
        List<JobWorkItem> items = new ArrayList<>(definition.items());
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key().equals(itemKey)) {
                JobWorkItem normalized = normalizeItem(item, i);
                items.set(i, normalized);
                saveDefinition(withItems(definition, items));
                return normalized;
            }
        }
        throw new IllegalArgumentException("Work item not found: " + itemKey);
    }

    public void deleteItem(String jobId, String itemKey) {
        JobDefinition definition = getDefinition(jobId);
        List<JobWorkItem> items = new ArrayList<>(definition.items());
        boolean removed = items.removeIf(item -> item.key().equals(itemKey));
        if (!removed) {
            throw new IllegalArgumentException("Work item not found: " + itemKey);
        }
        saveDefinition(withItems(definition, items));
    }

    /**
     * Update a job definition's status. Used by the runner to synchronize
     * definition status with run lifecycle.
     */
    public void updateDefinitionStatus(String jobId, String status) {
        JobDefinition def = getDefinition(jobId);
        saveDefinition(new JobDefinition(
            def.id(), def.ownerAgentId(), def.projectId(), def.workspaceId(),
            status, def.title(), def.summary(), def.items(),
            def.promptProfile(), def.model(), def.settingsOverrideJson(),
            def.createdAt(), def.updatedAt()
        ));
    }

    public void deleteDefinition(String id) {
        jobRepository.deleteRecurrence(id);
        jobRepository.deleteDefinition(id);
    }

    // ════════════════════════════════════════════════════════════════
    //  Runs
    // ════════════════════════════════════════════════════════════════

    /**
     * Creates and starts a new job run. Allocates persistent job workspace
     * and output directory.
     */
    public JobRun startRun(String jobId) {
        JobDefinition def = getDefinition(jobId);
        Instant now = Instant.now();
        String runId = UUID.randomUUID().toString();

        // Allocate job workspace and output dir
        String workspacePath = null;
        String outputDir = null;
        try {
            Path wsPath = workspaceDirectoryService.jobWorkspace(jobId);
            workspacePath = wsPath.toString();
            Path outPath = workspaceDirectoryService.jobOutput(jobId, slug(def.title()), runId);
            outputDir = outPath.toString();
            log.info("Allocated workspace={} output={} for job run={}", workspacePath, outputDir, runId);
        } catch (Exception e) {
            log.error("Failed to allocate workspace for job run={}: {}", runId, e.getMessage());
        }

        // Initialize work item runs
        List<JobWorkItemRun> itemRuns = new ArrayList<>();
        for (JobWorkItem item : def.items()) {
            itemRuns.add(new JobWorkItemRun(
                item.key(),
                item.type().name(),
                item.planId(),
                item.workflowId(),
                "QUEUED",
                null, // runId — assigned when executed
                Map.of(), Map.of(), null, null, null
            ));
        }

        return jobRepository.saveRun(new JobRun(
            runId, jobId, JobRunStatus.QUEUED,
            itemRuns, workspacePath, outputDir,
            null, null, now, now, null, null
        ));
    }

    /**
     * Transitions a job run from QUEUED to RUNNING.
     */
    public JobRun markRunning(String runId) {
        JobRun run = getRun(runId);
        if (run.status() != JobRunStatus.QUEUED) {
            throw new IllegalStateException("Job run must be QUEUED to start: " + runId);
        }
        return jobRepository.saveRun(new JobRun(
            run.id(), run.jobId(), JobRunStatus.RUNNING,
            run.workItemRuns(), run.workspacePath(), run.outputDir(),
            run.finalMessage(), run.errorText(),
            run.createdAt(), Instant.now(), Instant.now(), run.completedAt()
        ));
    }

    /**
     * Updates the status of a single work item within the job run.
     */
    public JobRun updateWorkItemRun(String jobRunId, String itemKey,
                                     String status, String itemRunId,
                                     Map<String, Object> outputValues, String errorText) {
        JobRun run = getRun(jobRunId);
        List<JobWorkItemRun> updated = new ArrayList<>(run.workItemRuns());
        boolean found = false;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).key().equals(itemKey)) {
                JobWorkItemRun existing = updated.get(i);
                updated.set(i, new JobWorkItemRun(
                    existing.key(), existing.type(),
                    existing.planId(), existing.workflowId(),
                    status, itemRunId != null ? itemRunId : existing.runId(),
                    existing.inputValues(),
                    outputValues != null ? outputValues : existing.outputValues(),
                    errorText != null ? errorText : existing.errorText(),
                    "RUNNING".equals(status) && existing.startedAt() == null ? Instant.now() : existing.startedAt(),
                    isTerminalStatus(status) ? Instant.now() : existing.completedAt()
                ));
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Work item not found in job run: " + itemKey);
        }

        // Check if all items are terminal → complete the job run
        JobRunStatus newStatus = run.status();
        if (allTerminal(updated)) {
            boolean anyFailed = updated.stream().anyMatch(wi -> "FAILED".equals(wi.status()));
            newStatus = anyFailed ? JobRunStatus.FAILED : JobRunStatus.COMPLETED;
        }

        return jobRepository.saveRun(new JobRun(
            run.id(), run.jobId(), newStatus,
            updated, run.workspacePath(), run.outputDir(),
            newStatus.isTerminal() ? "Job run " + newStatus.name().toLowerCase() : run.finalMessage(),
            run.errorText(),
            run.createdAt(), Instant.now(),
            run.startedAt(),
            newStatus.isTerminal() ? Instant.now() : run.completedAt()
        ));
    }

    public JobRun failRun(String runId, String errorText) {
        JobRun run = getRun(runId);
        return jobRepository.saveRun(new JobRun(
            run.id(), run.jobId(), JobRunStatus.FAILED,
            run.workItemRuns(), run.workspacePath(), run.outputDir(),
            run.finalMessage(), errorText,
            run.createdAt(), Instant.now(),
            run.startedAt(), Instant.now()
        ));
    }

    public JobRun cancelRun(String runId) {
        JobRun run = getRun(runId);
        if (run.status().isTerminal()) {
            throw new IllegalStateException("Cannot cancel terminal job run: " + runId);
        }
        return jobRepository.saveRun(new JobRun(
            run.id(), run.jobId(), JobRunStatus.CANCELLED,
            run.workItemRuns(), run.workspacePath(), run.outputDir(),
            "Cancelled", run.errorText(),
            run.createdAt(), Instant.now(),
            run.startedAt(), Instant.now()
        ));
    }

    public JobRun getRun(String runId) {
        return jobRepository.findRun(runId)
            .orElseThrow(() -> new IllegalArgumentException("Job run not found: " + runId));
    }

    public List<JobRun> listRuns(String jobId) {
        return jobRepository.findRunsByJobId(jobId);
    }

    public List<String> outputRunIds(String jobId) {
        return listRuns(jobId).stream()
            .flatMap(run -> run.workItemRuns().stream())
            .map(JobWorkItemRun::runId)
            .filter(runId -> runId != null && !runId.isBlank())
            .toList();
    }

    // ════════════════════════════════════════════════════════════════
    //  Recurrence
    // ════════════════════════════════════════════════════════════════

    /**
     * Sets or updates a cron-based recurrence for a job.
     * A new run is created each time the cron fires.
     */
    public JobRecurrence setRecurrence(String jobId, String cronExpression,
                                        String timezone, Instant nextFireTime) {
        getDefinition(jobId); // validate exists
        if (!StringUtils.hasText(cronExpression)) {
            throw new IllegalArgumentException("cronExpression is required");
        }
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        JobRecurrence existing = jobRepository.findRecurrence(jobId).orElse(null);
        if (existing != null) {
            id = existing.id();
            now = existing.createdAt();
        }
        return jobRepository.saveRecurrence(new JobRecurrence(
            id, jobId, cronExpression,
            StringUtils.hasText(timezone) ? timezone : "UTC",
            nextFireTime, true, now, Instant.now()
        ));
    }

    public Optional<JobRecurrence> getRecurrence(String jobId) {
        return jobRepository.findRecurrence(jobId);
    }

    public void disableRecurrence(String jobId) {
        JobRecurrence existing = jobRepository.findRecurrence(jobId)
            .orElseThrow(() -> new IllegalArgumentException("No recurrence for job: " + jobId));
        jobRepository.saveRecurrence(new JobRecurrence(
            existing.id(), existing.jobId(),
            existing.cronExpression(), existing.timezone(),
            existing.nextFireTime(), false,
            existing.createdAt(), Instant.now()
        ));
    }

    /**
     * Finds recurrences due by the given time and fires a new run for each.
     */
    public List<JobRun> fireDueRecurrences(Instant before) {
        List<JobRecurrence> dueList = jobRepository.findDueRecurrences(before);
        List<JobRun> newRuns = new ArrayList<>();
        for (JobRecurrence rec : dueList) {
            try {
                JobRun run = startRun(rec.jobId());
                newRuns.add(run);
            } catch (Exception e) {
                log.error("Failed to fire recurrence for job={}: {}", rec.jobId(), e.getMessage());
            }
        }
        return newRuns;
    }

    // ── Helpers ──

    private List<JobWorkItem> orderedItems(List<JobWorkItem> items) {
        List<JobWorkItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return List.copyOf(sorted);
    }

    private JobDefinition withItems(JobDefinition definition, List<JobWorkItem> items) {
        return new JobDefinition(
            definition.id(), definition.ownerAgentId(), definition.projectId(),
            definition.workspaceId(), definition.status(), definition.title(),
            definition.summary(), items, definition.promptProfile(), definition.model(),
            definition.settingsOverrideJson(), definition.createdAt(), definition.updatedAt()
        );
    }

    private JobWorkItem normalizeItem(JobWorkItem item, int fallbackOrder) {
        if (item == null) {
            throw new IllegalArgumentException("Job item is required");
        }
        JobWorkItemType type = item.type();
        if (type == null) {
            throw new IllegalArgumentException("Job item type is required");
        }
        if (type == JobWorkItemType.PLAN && !StringUtils.hasText(item.planId())) {
            throw new IllegalArgumentException("Job PLAN item requires planId");
        }
        if (type == JobWorkItemType.WORKFLOW && !StringUtils.hasText(item.workflowId())) {
            throw new IllegalArgumentException("Job WORKFLOW item requires workflowId");
        }
        // Validate referenced plan/workflow exists
        if (type == JobWorkItemType.PLAN && planService != null) {
            planService.getTask(item.planId());
        }
        if (type == JobWorkItemType.WORKFLOW && workflowService != null) {
            workflowService.getWorkflow(item.workflowId());
        }
        String key = StringUtils.hasText(item.key()) ? item.key().trim() : UUID.randomUUID().toString();
        int order = item.order() > 0 ? item.order() : fallbackOrder + 1;
        return new JobWorkItem(
            key, type, normalize(item.planId()), normalize(item.workflowId()),
            item.inputBindings() == null ? Map.of() : item.inputBindings(),
            order, normalize(item.modelOverride()), item.priority()
        );
    }

    private boolean allTerminal(List<JobWorkItemRun> items) {
        return items.stream().allMatch(wi -> isTerminalStatus(wi.status()));
    }

    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status)
            || "CANCELLED".equals(status);
    }

    private String slug(String title) {
        if (!StringUtils.hasText(title)) return "run";
        String s = title.toLowerCase().replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (s.length() > 48) s = s.substring(0, 48).replaceAll("-$", "");
        return s.isEmpty() ? "run" : s;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
