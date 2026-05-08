package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

public record OrchestrationJob(
    String id,
    @NotBlank String ownerAgentId,
    @NotBlank String title,
    String summary,
    String defaultModel,
    String workspaceId,
    OrchestrationStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
