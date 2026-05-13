package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single execution of a workflow definition. Tracks overall status,
 * all node runs, and workspace paths.
 *
 * @param id               unique run identifier
 * @param workflowId       the definition id being executed
 * @param status           overall run status
 * @param currentNodeIndex index of the node currently executing (or next to resume)
 * @param nodeRuns         per-node execution state
 * @param workspacePath    temp workspace path surviving across nodes
 * @param outputDir        output directory for materialized artifacts
 * @param workflowSnapshot snapshot of the definition at start time
 * @param finalMessage     human-readable result message
 * @param errorText        error message for failed runs
 * @param createdAt        creation timestamp
 * @param updatedAt        last-update timestamp
 * @param startedAt        when the run started
 * @param completedAt      when the run reached a terminal state
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
    WorkflowDefinition workflowSnapshot,
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
        nodeRuns = nodeRuns == null ? List.of() : List.copyOf(nodeRuns);
        currentNodeIndex = Math.max(0, currentNodeIndex);
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
