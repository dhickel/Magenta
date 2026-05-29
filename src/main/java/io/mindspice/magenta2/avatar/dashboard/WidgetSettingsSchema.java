package io.mindspice.magenta2.avatar.dashboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WidgetSettingsSchema(List<WidgetSettingsField> fields) {
    public WidgetSettingsSchema {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (WidgetSettingsField field : fields) {
            if (field.defaultValue() != null) {
                defaults.put(field.name(), field.defaultValue());
            }
        }
        return defaults;
    }

    public static WidgetSettingsSchema basic(String defaultSourceMode) {
        return new WidgetSettingsSchema(List.of(
            new WidgetSettingsField("sourceMode", "Source", defaultSourceMode, List.of("dashboard", "agent", "project", "work_area"), false),
            new WidgetSettingsField("agentId", "Agent", "", List.of(), false),
            new WidgetSettingsField("projectId", "Project", "", List.of(), false),
            new WidgetSettingsField("workAreaId", "Work Area", "", List.of(), false),
            new WidgetSettingsField("density", "Density", "compact", List.of("compact", "comfortable"), false)
        ));
    }
}
