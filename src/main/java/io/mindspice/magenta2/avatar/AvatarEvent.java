package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.Map;

public record AvatarEvent(
    String id,
    String eventType,
    Map<String, Object> payload,
    Instant occurredAt
) {
}
