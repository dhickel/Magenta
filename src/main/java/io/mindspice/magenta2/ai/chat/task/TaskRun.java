package io.mindspice.magenta2.ai.chat.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TaskRun(
    String id,
    String taskId,
    TaskRunStatus status,
    Map<String, Object> inputValues,
    Map<String, Object> outputValues,
    TaskDefinition taskSnapshot,
    List<String> executionEvidence,
    List<String> validationFeedback,
    String finalMessage,
    String errorText,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt
) {
    public TaskRun {
        inputValues = inputValues == null ? Map.of() : Map.copyOf(inputValues);
        outputValues = outputValues == null ? Map.of() : Map.copyOf(outputValues);
        executionEvidence = executionEvidence == null ? List.of() : List.copyOf(executionEvidence);
        validationFeedback = validationFeedback == null ? List.of() : List.copyOf(validationFeedback);
    }
}
