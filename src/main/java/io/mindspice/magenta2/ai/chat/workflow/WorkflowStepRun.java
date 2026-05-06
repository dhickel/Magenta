package io.mindspice.magenta2.ai.chat.workflow;

import java.util.Map;

public record WorkflowStepRun(
    String stepKey,
    String taskId,
    String taskRunId,
    WorkflowStepRunStatus status,
    Map<String, Object> inputValues,
    Map<String, Object> outputValues,
    String errorText
) {
    public WorkflowStepRun {
        status = status == null ? WorkflowStepRunStatus.PENDING : status;
        inputValues = inputValues == null ? Map.of() : Map.copyOf(inputValues);
        outputValues = outputValues == null ? Map.of() : Map.copyOf(outputValues);
    }
}
