package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record UserDashboard(
    String id,
    String name,
    int position,
    boolean defaultDashboard,
    Instant createdAt,
    Instant updatedAt
) {
}
