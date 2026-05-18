package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;
import java.util.List;

/**
 * Read model for agent workspace health and activity, derived from
 * workspace state, leases, assignments, and outputs.
 */
public record AgentWorkspaceStatus(
    String agentId,
    String workspaceRelativePath,
    WorkspaceHealth health,
    boolean exists,
    boolean writable,
    int activeRunCount,
    int activeLeaseCount,
    List<String> linkedProjectIds,
    long outputArtifactCount,
    long outputBytes,
    Instant lastActivityAt,
    String message
) {
    public enum WorkspaceHealth {
        READY,
        MISSING,
        READ_ONLY,
        BUSY,
        ERROR
    }

    public static AgentWorkspaceStatus missing(String agentId) {
        return new AgentWorkspaceStatus(
            agentId, "agents/" + agentId + "/workspace", WorkspaceHealth.MISSING,
            false, false, 0, 0, List.of(), 0, 0, null,
            "Workspace directory does not exist"
        );
    }

    public static AgentWorkspaceStatus error(String agentId, String message) {
        return new AgentWorkspaceStatus(
            agentId, "agents/" + agentId + "/workspace", WorkspaceHealth.ERROR,
            false, false, 0, 0, List.of(), 0, 0, null,
            message
        );
    }
}
