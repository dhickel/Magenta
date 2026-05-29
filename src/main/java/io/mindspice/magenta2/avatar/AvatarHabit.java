package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record AvatarHabit(
    String id,
    String title,
    String notes,
    String habitType,
    String period,
    double targetQuantity,
    String targetUnit,
    List<String> displayDays,
    LocalTime startTime,
    LocalTime endTime,
    boolean streakEnabled,
    boolean archived,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt
) {
}
