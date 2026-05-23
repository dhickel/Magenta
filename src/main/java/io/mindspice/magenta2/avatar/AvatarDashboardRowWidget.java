package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.Map;

public record AvatarDashboardRowWidget(
    String id,
    String rowId,
    String widgetKey,
    int columnPosition,
    int columnWidth,
    boolean enabled,
    boolean collapsed,
    Map<String, Object> settings,
    Instant updatedAt
) {
}
