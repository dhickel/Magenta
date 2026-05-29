package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record PlannerReminder(
    String id,
    String title,
    String notes,
    Instant remindAt,
    String status,
    String sourceType,
    String sourceId,
    Instant snoozedUntil,
    Instant createdAt,
    Instant updatedAt
) {
}
