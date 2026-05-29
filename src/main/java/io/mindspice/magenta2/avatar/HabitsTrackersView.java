package io.mindspice.magenta2.avatar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record HabitsTrackersView(
    LocalDate today,
    List<AvatarHabit> activeHabits,
    List<AvatarHabit> archivedHabits,
    Map<String, List<AvatarHabitLog>> recentLogs,
    Map<String, Progress> progress
) {
    public record Progress(
        double loggedQuantity,
        double targetQuantity,
        String targetUnit,
        String status,
        int trendDays,
        int streakDays
    ) {
    }
}
