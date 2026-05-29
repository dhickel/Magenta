package io.mindspice.magenta2.avatar.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

public final class WidgetSettingsValidator {
    private WidgetSettingsValidator() {
    }

    public static WidgetSettingsValidation validate(DashboardWidgetDefinition definition, Map<String, ?> submitted) {
        Map<String, Object> merged = new LinkedHashMap<>(definition.settingsSchema().defaults());
        if (submitted != null) {
            for (Map.Entry<String, ?> entry : submitted.entrySet()) {
                if (entry.getValue() != null) {
                    merged.put(entry.getKey(), entry.getValue().toString().strip());
                }
            }
        }

        ArrayList<String> errors = new ArrayList<>();
        for (WidgetSettingsField field : definition.settingsSchema().fields()) {
            Object value = merged.get(field.name());
            if (!field.allowedValues().isEmpty()
                && StringUtils.hasText(value == null ? null : value.toString())
                && !field.allowedValues().contains(value.toString())) {
                errors.add(field.label() + " must be one of " + String.join(", ", field.allowedValues()));
            }
        }
        validateBinding(definition, merged, errors);
        return new WidgetSettingsValidation(merged, errors);
    }

    private static void validateBinding(
        DashboardWidgetDefinition definition,
        Map<String, Object> settings,
        ArrayList<String> errors
    ) {
        if (definition.bindingMode() == WidgetBindingMode.REQUIRED_AGENT && !hasText(settings.get("agentId"))) {
            errors.add("Agent binding is required for " + definition.title());
        }
        if (definition.bindingMode() == WidgetBindingMode.REQUIRED_PROJECT && !hasText(settings.get("projectId"))) {
            errors.add("Project binding is required for " + definition.title());
        }
        if (definition.bindingMode() == WidgetBindingMode.REQUIRED_WORK_AREA && !hasText(settings.get("workAreaId"))) {
            errors.add("Work Area binding is required for " + definition.title());
        }
        Object sourceMode = settings.get("sourceMode");
        if ("agent".equals(sourceMode) && !hasText(settings.get("agentId"))) {
            errors.add("Agent source mode requires an agent id.");
        }
        if ("project".equals(sourceMode) && !hasText(settings.get("projectId"))) {
            errors.add("Project source mode requires a project id.");
        }
        if ("job".equals(sourceMode) && !hasText(settings.get("jobId"))) {
            errors.add("Job source mode requires a job id.");
        }
        if ("work_area".equals(sourceMode) && !hasText(settings.get("workAreaId"))) {
            errors.add("Work Area source mode requires a Work Area id.");
        }
        Object noteSourceMode = settings.get("noteSourceMode");
        if ("agent".equals(noteSourceMode) && !hasText(settings.get("agentId"))) {
            errors.add("Agent note source requires an agent id.");
        }
        if ("project".equals(noteSourceMode) && !hasText(settings.get("projectId"))) {
            errors.add("Project note source requires a project id.");
        }
        if ("work_area".equals(noteSourceMode) && !hasText(settings.get("workAreaId"))) {
            errors.add("Work Area note source requires a Work Area id.");
        }
    }

    private static boolean hasText(Object value) {
        return value != null && StringUtils.hasText(value.toString());
    }
}
