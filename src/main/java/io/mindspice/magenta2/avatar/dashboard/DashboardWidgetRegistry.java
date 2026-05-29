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
        notes(),
        project("projects", "Projects", "Goals, materials, contacts, blockers, next actions, outputs, notes, and progress.", 6),
        project("contacts-materials", "Contacts/Materials", "Project contacts and materials with source binding.", 6),
        agentStatusQueue(),
        agentOutputs(),
        agentFilesNotes(),
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

    private static DashboardWidgetDefinition notes() {
        return new DashboardWidgetDefinition(
            "notes",
            "Notes",
            "Personal and file-backed notes.",
            "context",
            "avatar.sqlite and confined project/work area files",
            6,
            STANDARD_WIDTHS,
            WidgetInstancePolicy.MULTI_INSTANCE,
            WidgetBindingMode.OPTIONAL_WORK_AREA,
            new WidgetSettingsSchema(List.of(
                new WidgetSettingsField("noteSourceMode", "Note Source", "personal",
                    List.of("personal", "agent", "project", "work_area", "mixed"), false),
                new WidgetSettingsField("sourceMode", "Source", "dashboard",
                    List.of("dashboard", "agent", "project", "work_area"), true),
                new WidgetSettingsField("agentId", "Agent", "", List.of(), false),
                new WidgetSettingsField("projectId", "Project", "", List.of(), false),
                new WidgetSettingsField("workAreaId", "Work Area", "", List.of(), false),
                new WidgetSettingsField("noteQuery", "Search", "", List.of(), false),
                new WidgetSettingsField("lastOpenedNoteId", "Last Personal Note", "", List.of(), true),
                new WidgetSettingsField("lastOpenedFilePath", "Last File Note", "", List.of(), true),
                new WidgetSettingsField("density", "Density", "compact", List.of("compact", "comfortable"), false)
            )),
            "notes",
            "notes",
            "generic",
            WidgetRefreshPolicy.MANUAL,
            WidgetEmptyStatePolicy.NO_DATA,
            new WidgetToolDescriptor(
                List.of("avatar_note_search", "avatar_file_note_read"),
                List.of("avatar_note_append", "avatar_file_note_update"),
                "AVATAR_SUPERVISOR",
                false,
                50
            )
        );
    }

    private static DashboardWidgetDefinition project(
        String type,
        String title,
        String description,
        int defaultWidth
    ) {
        return new DashboardWidgetDefinition(
            type,
            title,
            description,
            "context",
            "magenta project workspace",
            defaultWidth,
            STANDARD_WIDTHS,
            WidgetInstancePolicy.MULTI_INSTANCE,
            WidgetBindingMode.REQUIRED_PROJECT,
            new WidgetSettingsSchema(List.of(
                new WidgetSettingsField("sourceMode", "Source", "project", List.of("project"), true),
                new WidgetSettingsField("projectId", "Project", "", List.of(), false),
                new WidgetSettingsField("density", "Density", "compact", List.of("compact", "comfortable"), false)
            )),
            type,
            type,
            "generic",
            WidgetRefreshPolicy.MANUAL,
            WidgetEmptyStatePolicy.MISSING_BINDING,
            new WidgetToolDescriptor(
                List.of("avatar_project_context_get"),
                List.of("avatar_project_artifact_update"),
                "AVATAR_SUPERVISOR",
                false,
                50
            )
        );
    }

    private static DashboardWidgetDefinition agentStatusQueue() {
        return new DashboardWidgetDefinition(
            "agent-status-queue",
            "Agent Status/Queue",
            "Selected agent profile, model, queue, inbox, running, waiting, and health.",
            "operations",
            "magenta services",
            6,
            STANDARD_WIDTHS,
            WidgetInstancePolicy.MULTI_INSTANCE,
            WidgetBindingMode.REQUIRED_AGENT,
            new WidgetSettingsSchema(List.of(
                new WidgetSettingsField("sourceMode", "Source", "agent", List.of("agent"), true),
                new WidgetSettingsField("agentId", "Agent", "", List.of(), false),
                new WidgetSettingsField("projectId", "Project", "", List.of(), false),
                new WidgetSettingsField("workAreaId", "Work Area", "", List.of(), false),
                new WidgetSettingsField("density", "Density", "compact", List.of("compact", "comfortable"), false)
            )),
            "agent-status-queue",
            "agent-status-queue",
            "generic",
            WidgetRefreshPolicy.MANUAL,
            WidgetEmptyStatePolicy.MISSING_BINDING,
            new WidgetToolDescriptor(
                List.of("agent_workspace_status", "agent_queue_list", "agent_inbox_list"),
                List.of("agent_assignment_cancel", "agent_assignment_pause", "agent_assignment_resume"),
                "CURRENT_AGENT_CONTEXT",
                false,
                50
            )
        );
    }

    private static DashboardWidgetDefinition agentOutputs() {
        return new DashboardWidgetDefinition(
            "agent-outputs",
            "Agent Outputs",
            "Scoped output artifacts for dashboard, agent, project, job, or Work Area sources.",
            "operations",
            "magenta output services",
            6,
            STANDARD_WIDTHS,
            WidgetInstancePolicy.MULTI_INSTANCE,
            WidgetBindingMode.OUTPUT_SOURCE,
            new WidgetSettingsSchema(List.of(
                new WidgetSettingsField("sourceMode", "Source", "agent",
                    List.of("dashboard", "agent", "project", "job", "work_area"), false),
                new WidgetSettingsField("agentId", "Agent", "", List.of(), false),
                new WidgetSettingsField("projectId", "Project", "", List.of(), false),
                new WidgetSettingsField("jobId", "Job", "", List.of(), false),
                new WidgetSettingsField("workAreaId", "Work Area", "", List.of(), false),
                new WidgetSettingsField("artifactType", "Artifact Type", "", List.of(), false),
                new WidgetSettingsField("density", "Density", "compact", List.of("compact", "comfortable"), false)
            )),
            "agent-outputs",
            "agent-outputs",
            "generic",
            WidgetRefreshPolicy.MANUAL,
            WidgetEmptyStatePolicy.MISSING_BINDING,
            new WidgetToolDescriptor(
                List.of("agent_output_list", "agent_output_read", "agent_job_outputs"),
                List.of(),
                "CURRENT_AGENT_CONTEXT",
                false,
                50
            )
        );
    }

    private static DashboardWidgetDefinition agentFilesNotes() {
        return new DashboardWidgetDefinition(
            "agent-files-notes",
            "Agent Files/Notes",
            "Selected Work Area mini-browser and tagged notes.",
            "operations",
            "confined Work Area services",
            6,
            STANDARD_WIDTHS,
            WidgetInstancePolicy.MULTI_INSTANCE,
            WidgetBindingMode.REQUIRED_WORK_AREA,
            new WidgetSettingsSchema(List.of(
                new WidgetSettingsField("sourceMode", "Source", "work_area", List.of("work_area"), true),
                new WidgetSettingsField("agentId", "Agent", "", List.of(), false),
                new WidgetSettingsField("workAreaId", "Work Area", "", List.of(), false),
                new WidgetSettingsField("filePath", "Path", ".", List.of(), false),
                new WidgetSettingsField("density", "Density", "compact", List.of("compact", "comfortable"), false)
            )),
            "agent-files-notes",
            "agent-files-notes",
            "generic",
            WidgetRefreshPolicy.MANUAL,
            WidgetEmptyStatePolicy.MISSING_BINDING,
            new WidgetToolDescriptor(
                List.of("agent_workspace_status"),
                List.of(),
                "CURRENT_AGENT_CONTEXT",
                false,
                25
            )
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
