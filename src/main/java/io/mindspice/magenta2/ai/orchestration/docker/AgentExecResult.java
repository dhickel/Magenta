package io.mindspice.magenta2.ai.orchestration.docker;

public record AgentExecResult(
    int exitCode,
    String stdout,
    String stderr,
    boolean timedOut,
    String containerId
) {
    public boolean success() {
        return !timedOut && exitCode == 0;
    }
}
