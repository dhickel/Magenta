package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

public record WorkAssignment(
    String id,
    String agentId,
    String jobId,
    String jobItemId,
    AssignmentType assignmentType,
    int priority,
    OrchestrationStatus status,
    String modelOverride,
    String workspaceId,
    int currentItemIndex,
    Map<String, Object> checkpoint,
    Map<String, Object> input,
    Map<String, Object> output,
    Map<String, Object> evidence,
    String errorText,
    String leaseOwner,
    Instant leaseExpiresAt,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt
) {
}
