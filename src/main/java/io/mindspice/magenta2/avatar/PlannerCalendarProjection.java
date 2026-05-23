package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record PlannerCalendarProjection(
    String id,
    String taskId,
    Instant occurrenceStart,
    Instant occurrenceEnd,
    PlannerTaskStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
