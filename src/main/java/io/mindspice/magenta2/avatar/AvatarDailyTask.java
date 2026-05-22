package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.time.LocalDate;

public record AvatarDailyTask(
    String id,
    LocalDate taskDate,
    String title,
    String notes,
    AvatarTaskStatus status,
    int position,
    Instant createdAt,
    Instant updatedAt
) {
}
