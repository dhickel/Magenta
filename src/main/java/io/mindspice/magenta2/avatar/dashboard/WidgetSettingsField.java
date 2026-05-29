package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

public record WidgetSettingsField(
    String name,
    String label,
    String defaultValue,
    List<String> allowedValues,
    boolean hidden
) {
    public WidgetSettingsField {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
