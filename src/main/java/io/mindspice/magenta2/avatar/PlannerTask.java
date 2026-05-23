package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record PlannerTask(
    String id,
    String title,
    String notes,
    PlannerTaskStatus status,
    AvatarPriority priority,
    Instant startsAt,
    Instant dueAt,
    String timezone,
    PlannerRecurrence recurrence,
    PlannerTaskLink link,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
}
