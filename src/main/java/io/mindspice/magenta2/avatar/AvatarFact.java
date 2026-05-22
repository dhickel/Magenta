package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.Map;

public record AvatarFact(
    String namespace,
    String key,
    Map<String, Object> value,
    AvatarFactStatus status,
    Instant updatedAt
) {
}
