package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PlannerDayMap(
    String id,
    LocalDate mapDate,
    List<String> topPriorityIds,
    String nowItemId,
    String nextItemId,
    List<String> laterItemIds,
    String reviewNotes,
    Instant restartedAt,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public PlannerDayMap {
        topPriorityIds = topPriorityIds == null ? List.of() : List.copyOf(topPriorityIds);
        laterItemIds = laterItemIds == null ? List.of() : List.copyOf(laterItemIds);
    }
}
