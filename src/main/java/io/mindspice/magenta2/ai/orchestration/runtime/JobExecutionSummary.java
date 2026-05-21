package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.List;

/**
 * Stable read model that bridges a job definition, assignment-owned execution,
 * persistent job workspace state, and currently indexed outputs.
 */
public record JobExecutionSummary(
    String jobId,
    String jobTitle,
    String jobStatus,
    String assignmentId,
    OrchestrationStatus assignmentStatus,
    AssignmentType assignmentType,
    int assignmentPriority,
    String modelOverride,
    String agentId,
    String agentName,
    String agentStatus,
    String projectId,
    String projectName,
    String compatibilityWorkspaceId,
    String effectiveWorkspaceId,
    String effectiveWorkspaceKind,
    String effectiveWorkspaceDisplayPath,
    boolean persistentWorkspaceEnabled,
    String persistentJobWorkspaceId,
    String persistentJobWorkspacePath,
    boolean persistentJobWorkspacePresent,
    String jobRunId,
    JobRunStatus jobRunStatus,
    String outputDirectory,
    List<String> childRunIds,
    int outputCount,
    Instant latestOutputAt,
    Instant queuedAt,
    Instant startedAt,
    Instant completedAt,
    Instant updatedAt
) {
}
