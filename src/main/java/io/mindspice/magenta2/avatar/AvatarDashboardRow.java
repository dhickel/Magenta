package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AvatarDashboardRow(
    String id,
    int position,
    boolean collapsed,
    Map<String, Object> settings,
    Instant updatedAt,
    List<AvatarDashboardRowWidget> widgets
) {
}
