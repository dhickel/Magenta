package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

public record OrchestrationEvent(
    String id,
    EventType eventType,
    String sourceType,
    String sourceId,
    Map<String, Object> payload,
    Instant createdAt,
    Instant handledAt
) {
}
