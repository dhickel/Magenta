package io.mindspice.magenta2.avatar.dashboard;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DashboardWidgetRegistry {
    private static final List<Integer> STANDARD_WIDTHS = List.of(3, 4, 6, 8, 12);
    private static final DashboardWidgetRegistry DEFAULT = new DashboardWidgetRegistry(List.of(
        planner("today-planner", "Today Planner", "Top priorities, day map, time blocks, and review.", 6,
            List.of("avatar_today_plan_get"),
            List.of("avatar_today_plan_update", "avatar_quick_capture", "avatar_day_restart")),
        planner("tasks-routines", "Tasks/Routines", "Planner tasks, routines, recurrence, subtasks, and status.", 6,
            List.of("avatar_tasks_routines_get"),
            List.of("avatar_task_upsert", "avatar_task_occurrence_update", "avatar_reminder_upsert")),
        planner("calendar-schedule", "Calendar/Schedule", "Calendar grid with events, time blocks, recurrence, and reminders.", 6,
            List.of("avatar_calendar_schedule_get"),
            List.of("avatar_calendar_upsert", "avatar_timeblock_upsert", "avatar_reminder_upsert")),
        personal("daily-tasks", "Daily Tasks", "Today-focused task capture.", 6, WidgetInstancePolicy.SINGLE_PER_DASHBOARD),
        personal("todos", "Todos", "Priority queue and quick completion.", 4, WidgetInstancePolicy.SINGLE_PER_DASHBOARD),
        personal("calendar", "Calendar", "Upcoming dated work.", 4, WidgetInstancePolicy.SINGLE_PER_DASHBOARD),
        personal("notes", "Notes", "Short personal notes.", 6, WidgetInstancePolicy.MULTI_INSTANCE),
        operational("outputs", "Outputs", "Recent generated artifacts.", 6, WidgetInstancePolicy.MULTI_INSTANCE, WidgetBindingMode.OUTPUT_SOURCE),
        operational("system", "System", "Agent and queue counters.", 4, WidgetInstancePolicy.SINGLE_SYSTEM, WidgetBindingMode.SYSTEM),
        operational("alerts", "Alerts", "Inbox and system alerts.", 4, WidgetInstancePolicy.SINGLE_PER_DASHBOARD, WidgetBindingMode.SYSTEM),
        operational("recent-work", "Recent Work", "Recent jobs, assignments, and outputs.", 4, WidgetInstancePolicy.MULTI_INSTANCE, WidgetBindingMode.OUTPUT_SOURCE)
    ));

    private final Map<String, DashboardWidgetDefinition> definitions;

    public DashboardWidgetRegistry(Collection<DashboardWidgetDefinition> definitions) {
        Map<String, DashboardWidgetDefinition> byType = new LinkedHashMap<>();
        for (DashboardWidgetDefinition definition : definitions == null ? List.<DashboardWidgetDefinition>of() : definitions) {
            byType.put(definition.type(), definition);
        }
        this.definitions = Collections.unmodifiableMap(byType);
    }

    public static DashboardWidgetRegistry defaultRegistry() {
        return DEFAULT;
    }

    public List<DashboardWidgetDefinition> definitions() {
        return definitions.values().stream().toList();
    }

    public Optional<DashboardWidgetDefinition> find(String type) {
        return Optional.ofNullable(definitions.get(type));
    }

    public DashboardWidgetDefinition require(String type) {
        return find(type).orElseThrow(() -> new IllegalArgumentException("unknown dashboard widget type: " + type));
    }

    public boolean contains(String type) {
        return definitions.containsKey(type);
    }

    public String singleInstanceKey(String type) {
        return find(type).filter(DashboardWidgetDefinition::singleInstance).map(DashboardWidgetDefinition::type).orElse(null);
    }

    private static DashboardWidgetDefinition personal(
        String type,
        String title,
        String description,
        int defaultWidth,
        WidgetInstancePolicy policy
    ) {
        return definition(type, title, description, "personal", "avatar.sqlite", defaultWidth, policy, WidgetBindingMode.NONE);
    }

    private static DashboardWidgetDefinition planner(
        String type,
        String title,
        String description,
        int defaultWidth,
        List<String> readTools,
        List<String> mutationTools
    ) {
        return new DashboardWidgetDefinition(
            type,
            title,
            description,
            "planner",
            "avatar.sqlite",
            defaultWidth,
            STANDARD_WIDTHS,
            WidgetInstancePolicy.SINGLE_PER_DASHBOARD,
            WidgetBindingMode.NONE,
            WidgetSettingsSchema.basic("dashboard"),
            type,
            type,
            "generic",
            WidgetRefreshPolicy.MANUAL,
            WidgetEmptyStatePolicy.NO_DATA,
            new WidgetToolDescriptor(readTools, mutationTools, "AVATAR_SUPERVISOR", false, 50)
        );
    }

    private static DashboardWidgetDefinition operational(
        String type,
        String title,
        String description,
        int defaultWidth,
        WidgetInstancePolicy policy,
        WidgetBindingMode bindingMode
    ) {
        return definition(type, title, description, "operations", "magenta services", defaultWidth, policy, bindingMode);
    }

    private static DashboardWidgetDefinition definition(
        String type,
        String title,
        String description,
        String category,
        String dataOwner,
        int defaultWidth,
        WidgetInstancePolicy policy,
        WidgetBindingMode bindingMode
    ) {
        return new DashboardWidgetDefinition(
            type,
            title,
            description,
            category,
            dataOwner,
            defaultWidth,
            STANDARD_WIDTHS,
            policy,
            bindingMode,
            WidgetSettingsSchema.basic(bindingMode == WidgetBindingMode.NONE ? "dashboard" : "dashboard"),
            type,
            type,
            "generic",
            WidgetRefreshPolicy.MANUAL,
            WidgetEmptyStatePolicy.NO_DATA,
            WidgetToolDescriptor.none()
        );
    }
}
