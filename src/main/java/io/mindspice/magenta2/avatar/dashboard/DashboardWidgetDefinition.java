package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

public record DashboardWidgetDefinition(
    String type,
    String title,
    String description,
    String category,
    String dataOwner,
    int defaultWidth,
    List<Integer> supportedWidths,
    WidgetInstancePolicy instancePolicy,
    WidgetBindingMode bindingMode,
    WidgetSettingsSchema settingsSchema,
    String summaryRenderer,
    String detailRenderer,
    String settingsRenderer,
    WidgetRefreshPolicy refreshPolicy,
    WidgetEmptyStatePolicy emptyStatePolicy,
    WidgetToolDescriptor toolDescriptor
) {
    public DashboardWidgetDefinition {
        supportedWidths = supportedWidths == null ? List.of(defaultWidth) : List.copyOf(supportedWidths);
        settingsSchema = settingsSchema == null ? WidgetSettingsSchema.basic("dashboard") : settingsSchema;
        toolDescriptor = toolDescriptor == null ? WidgetToolDescriptor.none() : toolDescriptor;
    }

    public boolean supportsWidth(int width) {
        return supportedWidths.contains(width);
    }

    public boolean singleInstance() {
        return instancePolicy == WidgetInstancePolicy.SINGLE_PER_DASHBOARD
            || instancePolicy == WidgetInstancePolicy.SINGLE_SYSTEM;
    }
}
