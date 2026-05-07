package io.mindspice.magenta2.ai.orchestration.runtime;

import java.util.Map;

public record AssignmentRequest(
    String agentId,
    String jobId,
    String jobItemId,
    AssignmentType assignmentType,
    Integer priority,
    String modelOverride,
    String workspaceId,
    Map<String, Object> input
) {
}
