package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * Runtime state for one node execution within a workflow run.
 *
 * @param nodeKey      the node key matching the definition
 * @param type         the node type
 * @param status       current execution status
 * @param inputValues  resolved input values for this node
 * @param outputValues output values produced by this node
 * @param startedAt    when node execution started
 * @param completedAt  when node execution completed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowNodeRun(
    String nodeKey,
    WorkflowNodeType type,
    WorkflowNodeRunStatus status,
    Map<String, Object> inputValues,
    Map<String, Object> outputValues,
    Instant startedAt,
    Instant completedAt
) {
    public WorkflowNodeRun {
        if (nodeKey == null || nodeKey.isBlank()) {
            throw new IllegalArgumentException("WorkflowNodeRun nodeKey must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("WorkflowNodeRun type must not be null");
        }
        if (status == null) {
            status = WorkflowNodeRunStatus.PENDING;
        }
        inputValues = inputValues == null ? Map.of() : Map.copyOf(inputValues);
        outputValues = outputValues == null ? Map.of() : Map.copyOf(outputValues);
    }
}
