package io.mindspice.magenta2.ai.chat.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkflowRun(
    String id,
    String workflowId,
    WorkflowRunStatus status,
    WorkflowDefinition workflowSnapshot,
    List<WorkflowStepRun> stepRuns,
    Map<String, Object> finalOutputs,
    String finalMessage,
    String errorText,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt
) {
    public WorkflowRun {
        stepRuns = stepRuns == null ? List.of() : List.copyOf(stepRuns);
        finalOutputs = finalOutputs == null ? Map.of() : Map.copyOf(finalOutputs);
    }
}
