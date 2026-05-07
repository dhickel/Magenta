package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

public record AgentEventReaction(
    String id,
    String agentId,
    EventType eventType,
    Map<String, Object> filter,
    ReactionActionType actionType,
    Map<String, Object> assignmentTemplate,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
