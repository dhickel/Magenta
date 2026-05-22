package io.mindspice.magenta2.avatar;

import java.util.List;

public record AvatarSnapshot(
    AvatarProfile profile,
    List<AvatarPreference> preferences,
    List<AvatarDashboardWidget> dashboardLayout,
    List<AvatarTodo> todos,
    List<AvatarDailyTask> dailyTasks,
    List<AvatarCalendarItem> calendarItems,
    List<AvatarNote> notes,
    List<AvatarFact> facts,
    List<AvatarEvent> events
) {
}
