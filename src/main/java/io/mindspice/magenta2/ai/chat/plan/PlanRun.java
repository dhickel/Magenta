package io.mindspice.magenta2.ai.chat.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single execution run of a {@link PlanDefinition}. Snapshots the full definition
 * at start time so later edits do not mutate historical run meaning.
 */
public record PlanRun(
    String id,
    String planId,
    PlanRunStatus status,
    Map<String, Object> inputValues,
    Map<String, Object> outputValues,
    PlanDefinition planSnapshot,
    String workspaceId,
    String outputDirectory,
    List<String> executionEvidence,
    List<String> validationFeedback,
    List<String> deliverableEvidence,
    String finalMessage,
    String errorText,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt
) {
    public PlanRun {
        inputValues = inputValues == null ? Map.of() : Map.copyOf(inputValues);
        outputValues = outputValues == null ? Map.of() : Map.copyOf(outputValues);
        executionEvidence = executionEvidence == null ? List.of() : List.copyOf(executionEvidence);
        validationFeedback = validationFeedback == null ? List.of() : List.copyOf(validationFeedback);
        deliverableEvidence = deliverableEvidence == null ? List.of() : List.copyOf(deliverableEvidence);
    }

    // --- Wither helpers ---

    public PlanRun withStatus(PlanRunStatus newStatus) {
        return new PlanRun(id, planId, newStatus, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, validationFeedback,
            deliverableEvidence, finalMessage, errorText,
            createdAt, Instant.now(), startedAt, completedAt);
    }

    public PlanRun withOutputValues(Map<String, Object> newOutputValues) {
        return new PlanRun(id, planId, status, inputValues, newOutputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, validationFeedback,
            deliverableEvidence, finalMessage, errorText,
            createdAt, Instant.now(), startedAt, completedAt);
    }

    public PlanRun withExecutionEvidence(List<String> newEvidence) {
        return new PlanRun(id, planId, status, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, newEvidence, validationFeedback,
            deliverableEvidence, finalMessage, errorText,
            createdAt, Instant.now(), startedAt, completedAt);
    }

    public PlanRun withValidationFeedback(List<String> newFeedback) {
        return new PlanRun(id, planId, status, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, newFeedback,
            deliverableEvidence, finalMessage, errorText,
            createdAt, Instant.now(), startedAt, completedAt);
    }

    public PlanRun withFinalMessage(String newFinalMessage) {
        return new PlanRun(id, planId, status, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, validationFeedback,
            deliverableEvidence, newFinalMessage, errorText,
            createdAt, Instant.now(), startedAt, completedAt);
    }

    public PlanRun withErrorText(String newErrorText) {
        return new PlanRun(id, planId, status, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, validationFeedback,
            deliverableEvidence, finalMessage, newErrorText,
            createdAt, Instant.now(), startedAt, completedAt);
    }

    public PlanRun withStartedAt(Instant newStartedAt) {
        return new PlanRun(id, planId, status, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, validationFeedback,
            deliverableEvidence, finalMessage, errorText,
            createdAt, Instant.now(), newStartedAt, completedAt);
    }

    public PlanRun withCompletedAt(Instant newCompletedAt) {
        return new PlanRun(id, planId, status, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, validationFeedback,
            deliverableEvidence, finalMessage, errorText,
            createdAt, Instant.now(), startedAt, newCompletedAt);
    }

    public PlanRun withDeliverableEvidence(List<String> newDeliverableEvidence) {
        return new PlanRun(id, planId, status, inputValues, outputValues, planSnapshot,
            workspaceId, outputDirectory, executionEvidence, validationFeedback,
            newDeliverableEvidence, finalMessage, errorText,
            createdAt, Instant.now(), startedAt, completedAt);
    }
}
