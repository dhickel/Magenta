package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.Map;

public record AvatarDashboardWidget(
    String widgetId,
    int position,
    String size,
    boolean enabled,
    boolean collapsed,
    Map<String, Object> settings,
    Instant updatedAt
) {
}
