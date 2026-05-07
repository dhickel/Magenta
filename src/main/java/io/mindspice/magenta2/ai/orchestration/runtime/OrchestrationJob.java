package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;

public record OrchestrationJob(
    String id,
    String ownerAgentId,
    String title,
    String summary,
    String defaultModel,
    String workspaceId,
    OrchestrationStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
