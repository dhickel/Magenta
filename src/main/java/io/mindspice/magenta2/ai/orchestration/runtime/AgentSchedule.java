package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record AgentSchedule(
    String id,
    String agentId,
    String jobId,
    Map<String, Object> assignmentTemplate,
    @NotBlank String cronExpression,
    String timezone,
    boolean enabled,
    Instant nextRunAt,
    Instant createdAt,
    Instant updatedAt
) {
}
