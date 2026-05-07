package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

public record OrchestrationJobItem(
    String id,
    String jobId,
    int itemOrder,
    AssignmentType itemType,
    String taskId,
    String workflowId,
    String modelOverride,
    int priority,
    Map<String, Object> config,
    Instant createdAt,
    Instant updatedAt
) {
}
