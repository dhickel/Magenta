package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AvatarNote(
    String id,
    String title,
    String body,
    List<String> tags,
    Map<String, Object> sourceRef,
    boolean archived,
    Instant createdAt,
    Instant updatedAt
) {
}
