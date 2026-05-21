package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single execution run of a {@link JobDefinition}. Tracks per-item progress,
 * workspace paths, and terminal output.
 *
 * @param id             unique run identifier
 * @param jobId          parent job definition id
 * @param jobAssignmentId assignment/run owner used for persistent job workspace isolation
 * @param workspaceId    effective durable workspace id
 * @param status         current run status
 * @param workItemRuns   ordered list of per-item run state
 * @param workspacePath  job workspace path on disk
 * @param outputDir      job output directory on disk
 * @param finalMessage   human-readable completion message
 * @param errorText      error description if failed
 * @param createdAt      creation timestamp
 * @param updatedAt      last update timestamp
 * @param startedAt      when the run began executing
 * @param completedAt    when the run reached a terminal state
 */
public record JobRun(
    String id,
    String jobId,
    String jobAssignmentId,
    String workspaceId,
    JobRunStatus status,
    List<JobWorkItemRun> workItemRuns,
    String workspacePath,
    String outputDir,
    String finalMessage,
    String errorText,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt
) {
    public JobRun(
        String id,
        String jobId,
        JobRunStatus status,
        List<JobWorkItemRun> workItemRuns,
        String workspacePath,
        String outputDir,
        String finalMessage,
        String errorText,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
        this(id, jobId, null, null, status, workItemRuns, workspacePath, outputDir,
            finalMessage, errorText, createdAt, updatedAt, startedAt, completedAt);
    }

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * Returns the fraction of work items completed (0.0 to 1.0).
     */
    public double progress() {
        if (workItemRuns == null || workItemRuns.isEmpty()) return 0.0;
        long completed = workItemRuns.stream()
            .filter(wi -> "COMPLETED".equals(wi.status()) || "FAILED".equals(wi.status()))
            .count();
        return (double) completed / workItemRuns.size();
    }
}

/**
 * Run state for a single work item within a job run.
 */
record JobWorkItemRun(
    String key,
    String type,
    String planId,
    String workflowId,
    String status,
    String runId,
    Map<String, Object> inputValues,
    Map<String, Object> outputValues,
    String errorText,
    Instant startedAt,
    Instant completedAt
) {
    public JobWorkItemRun {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type is required");
    }
}
