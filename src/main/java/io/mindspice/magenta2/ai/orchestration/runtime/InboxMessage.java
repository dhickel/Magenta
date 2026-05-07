package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

public record InboxMessage(
    String id,
    String toAgentId,
    String fromId,
    String messageType,
    String body,
    Map<String, Object> metadata,
    boolean read,
    boolean handled,
    Instant createdAt,
    Instant updatedAt
) {
}
