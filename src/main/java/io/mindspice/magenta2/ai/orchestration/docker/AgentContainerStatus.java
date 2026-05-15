package io.mindspice.magenta2.ai.orchestration.docker;

public enum AgentContainerStatus {
    DISABLED,
    UNAVAILABLE,
    IMAGE_MISSING,
    STOPPED,
    STARTING,
    RUNNING,
    IDLE,
    STOPPING,
    ERROR
}
