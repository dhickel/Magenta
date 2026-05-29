package io.mindspice.magenta2.avatar;

import java.time.LocalDate;
import java.util.List;

public record TodayPlannerView(
    LocalDate date,
    PlannerDayMap dayMap,
    List<PlannerTask> topPriorities,
    List<PlannerTask> now,
    List<PlannerTask> next,
    List<PlannerTask> later,
    List<PlannerTask> overdue,
    List<PlannerTask> unscheduled,
    List<PlannerTimeBlock> timeBlocks,
    List<PlannerReminder> reminders
) {
}
