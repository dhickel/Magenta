package io.mindspice.magenta2.ai.orchestration.agents;

import java.time.Instant;
import java.util.List;

public record AgentProfile(
    String id,
    String name,
    AgentProfileStatus status,
    String defaultModel,
    String systemPrompt,
    List<String> approvedTools,
    List<String> allowedShellCommands,
    boolean directLineEnabled,
    Instant createdAt,
    Instant updatedAt
) {
}
