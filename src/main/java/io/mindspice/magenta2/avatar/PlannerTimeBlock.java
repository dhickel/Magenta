package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.time.LocalDate;

public record PlannerTimeBlock(
    String id,
    LocalDate blockDate,
    String title,
    Instant startsAt,
    Instant endsAt,
    String sourceType,
    String sourceId,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
