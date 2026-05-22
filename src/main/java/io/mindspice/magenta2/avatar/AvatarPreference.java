package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.Map;

public record AvatarPreference(
    String namespace,
    String key,
    Map<String, Object> value,
    Instant updatedAt
) {
}
