package io.mindspice.magenta2.ai.chat.workflow;

import java.util.List;

public record WorkflowStep(
    String stepKey,
    String taskId,
    List<WorkflowInputBinding> inputBindings
) {
    public WorkflowStep {
        inputBindings = inputBindings == null ? List.of() : List.copyOf(inputBindings);
    }
}
