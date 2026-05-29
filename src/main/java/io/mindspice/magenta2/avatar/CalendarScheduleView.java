package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CalendarScheduleView(
    LocalDate startDate,
    LocalDate endDate,
    List<Entry> entries
) {
    public record Entry(
        String kind,
        String sourceId,
        String title,
        Instant startsAt,
        Instant endsAt,
        String status,
        String meta
    ) {
    }
}
