package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.List;

public record ReminderInboxView(
    Instant now,
    List<PlannerReminder> due,
    List<PlannerReminder> upcoming,
    List<PlannerReminder> snoozed,
    List<PlannerReminder> closed
) {
}
