package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.time.LocalDate;

public record AvatarHabitLog(
    String id,
    String habitId,
    LocalDate logDate,
    double quantity,
    String status,
    String notes,
    Instant skippedAt,
    Instant restartedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
