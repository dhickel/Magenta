package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record PlannerOccurrence(
    String id,
    String taskId,
    Instant occurrenceStart,
    Instant occurrenceEnd,
    String status,
    Instant skippedAt,
    Instant snoozedUntil,
    Instant restartedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
