package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runtime state for a workflow v2 execution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowRun(
    String id,
    String workflowId,
    WorkflowRunStatus status,
    int currentNodeIndex,
    List<WorkflowNodeRun> nodeRuns,
    String workspacePath,
    String outputDir,
    String agentId,
    String jobId,
    String jobAssignmentId,
    String jobRunId,
    String projectId,
    String workspaceId,
    String runType,
    WorkflowDefinition workflowSnapshot,
    Map<String, Object> finalOutputs,
    List<String> artifactIds,
    String finalMessage,
    String errorText,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt
) {
    public WorkflowRun {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WorkflowRun id must not be blank");
        }
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("WorkflowRun workflowId must not be blank");
        }
        if (status == null) {
            status = WorkflowRunStatus.QUEUED;
        }
        currentNodeIndex = Math.max(0, currentNodeIndex);
        nodeRuns = nodeRuns == null ? List.of() : List.copyOf(nodeRuns);
        finalOutputs = finalOutputs == null ? Map.of() : Map.copyOf(finalOutputs);
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
    }

    public WorkflowRun(
        String id,
        String workflowId,
        WorkflowRunStatus status,
        int currentNodeIndex,
        List<WorkflowNodeRun> nodeRuns,
        String workspacePath,
        String outputDir,
        WorkflowDefinition workflowSnapshot,
        Map<String, Object> finalOutputs,
        List<String> artifactIds,
        String finalMessage,
        String errorText,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
        this(id, workflowId, status, currentNodeIndex, nodeRuns, workspacePath, outputDir,
            null, null, null, null, null, null, null,
            workflowSnapshot, finalOutputs, artifactIds, finalMessage, errorText,
            createdAt, updatedAt, startedAt, completedAt);
    }

    /**
     * Compatibility constructor for old run rows/callers.
     */
    @Deprecated
    public WorkflowRun(
        String id,
        String workflowId,
        WorkflowRunStatus status,
        int currentNodeIndex,
        List<WorkflowNodeRun> nodeRuns,
        String workspacePath,
        String outputDir,
        WorkflowDefinition workflowSnapshot,
        String finalMessage,
        String errorText,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
        this(id, workflowId, status, currentNodeIndex, nodeRuns,
            workspacePath, outputDir, null, null, null, null, null, null, null, workflowSnapshot,
            Map.of(), List.of(), finalMessage, errorText,
            createdAt, updatedAt, startedAt, completedAt);
    }

    public WorkflowNodeRun currentNodeRun() {
        if (currentNodeIndex < nodeRuns.size()) {
            return nodeRuns.get(currentNodeIndex);
        }
        return null;
    }

    public boolean isTerminal() {
        return status == WorkflowRunStatus.COMPLETED
            || status == WorkflowRunStatus.FAILED
            || status == WorkflowRunStatus.CANCELLED
            || status == WorkflowRunStatus.NEEDS_REVIEW;
    }
}
