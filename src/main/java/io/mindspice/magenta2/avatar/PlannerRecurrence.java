package io.mindspice.magenta2.avatar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record PlannerRecurrence(
    PlannerRecurrenceMode mode,
    int interval,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime time,
    DayOfWeek weekday,
    Integer monthDay,
    String cron
) {
    public PlannerRecurrence normalized() {
        int normalizedInterval = interval <= 0 ? 1 : interval;
        return new PlannerRecurrence(mode == null ? PlannerRecurrenceMode.NONE : mode,
            normalizedInterval, startDate, endDate, time, weekday, monthDay, cron);
    }
}
