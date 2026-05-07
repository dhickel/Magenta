package io.mindspice.magenta2.ai.orchestration.runtime;

public enum OrchestrationStatus {
    QUEUED,
    RUNNING,
    WAITING,
    PAUSED,
    INTERRUPTED,
    CANCEL_REQUESTED,
    CANCELLED,
    FAILED,
    COMPLETED,
    NEEDS_REVIEW
}
