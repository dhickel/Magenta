package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

public record AgentSchedule(
    String id,
    String agentId,
    String jobId,
    Map<String, Object> assignmentTemplate,
    String cronExpression,
    String timezone,
    boolean enabled,
    Instant nextRunAt,
    Instant createdAt,
    Instant updatedAt
) {
}
