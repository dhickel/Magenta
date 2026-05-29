package io.mindspice.magenta2.avatar;

import java.util.List;
import java.util.Map;

public record TasksRoutinesView(
    List<PlannerTask> tasks,
    Map<String, List<PlannerSubtodo>> subtodos,
    List<PlannerOccurrence> occurrences,
    List<PlannerReminder> reminders,
    String statusFilter,
    String rangeFilter,
    String recurrenceFilter
) {
    public TasksRoutinesView(
        List<PlannerTask> tasks,
        Map<String, List<PlannerSubtodo>> subtodos,
        List<PlannerOccurrence> occurrences,
        List<PlannerReminder> reminders
    ) {
        this(tasks, subtodos, occurrences, reminders, "ALL", "ALL", "ALL");
    }
}
