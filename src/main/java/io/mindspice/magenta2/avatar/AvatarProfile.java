package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record AvatarProfile(
    String id,
    String displayName,
    String timezone,
    String locale,
    String summary,
    Instant createdAt,
    Instant updatedAt
) {
}
