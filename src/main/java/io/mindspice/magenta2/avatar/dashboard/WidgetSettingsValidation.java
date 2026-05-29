package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;
import java.util.Map;

public record WidgetSettingsValidation(Map<String, Object> settings, List<String> errors) {
    public WidgetSettingsValidation {
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
