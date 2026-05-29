package io.mindspice.magenta2.api.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkArea;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.avatar.AvatarCalendarItem;
import io.mindspice.magenta2.avatar.AvatarDailyTask;
import io.mindspice.magenta2.avatar.AvatarDashboardRow;
import io.mindspice.magenta2.avatar.AvatarDashboardRowWidget;
import io.mindspice.magenta2.avatar.AvatarDashboardWidget;
import io.mindspice.magenta2.avatar.AvatarEvent;
import io.mindspice.magenta2.avatar.AvatarNote;
import io.mindspice.magenta2.avatar.AvatarProfile;
import io.mindspice.magenta2.avatar.AvatarTodo;
import io.mindspice.magenta2.avatar.CalendarScheduleView;
import io.mindspice.magenta2.avatar.PlannerOccurrence;
import io.mindspice.magenta2.avatar.UserDashboard;
import io.mindspice.magenta2.avatar.dashboard.DashboardWidgetDefinition;
import io.mindspice.magenta2.avatar.dashboard.DashboardWidgetRegistry;
import io.mindspice.magenta2.avatar.dashboard.DashboardFileNote;
import io.mindspice.magenta2.avatar.dashboard.DashboardNotesView;
import io.mindspice.magenta2.avatar.dashboard.DashboardProjectArtifact;
import io.mindspice.magenta2.avatar.dashboard.DashboardProjectContextView;
import io.mindspice.magenta2.avatar.dashboard.WidgetInstancePolicy;
import io.mindspice.magenta2.avatar.dashboard.WidgetSettingsField;
import io.mindspice.magenta2.avatar.dashboard.WidgetSettingsValidation;
import io.mindspice.magenta2.avatar.PlannerCalendarProjection;
import io.mindspice.magenta2.avatar.PlannerReminder;
import io.mindspice.magenta2.avatar.PlannerSubtodo;
import io.mindspice.magenta2.avatar.PlannerTask;
import io.mindspice.magenta2.avatar.PlannerTimeBlock;
import io.mindspice.magenta2.avatar.TasksRoutinesView;
import io.mindspice.magenta2.avatar.TodayPlannerView;
import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.components.Header;
import io.mindspice.simplypages.components.Markdown;
import io.mindspice.simplypages.components.Paragraph;
import io.mindspice.simplypages.components.display.Modal;
import io.mindspice.simplypages.components.forms.Button;
import io.mindspice.simplypages.components.forms.Form;
import io.mindspice.simplypages.components.forms.Select;
import io.mindspice.simplypages.components.forms.TextArea;
import io.mindspice.simplypages.components.forms.TextInput;
import io.mindspice.simplypages.core.Component;
import io.mindspice.simplypages.core.HtmlTag;
import io.mindspice.simplypages.layout.Column;
import io.mindspice.simplypages.layout.Row;

final class AvatarDashboardComponents {
    static final DashboardWidgetRegistry WIDGET_REGISTRY = DashboardWidgetRegistry.defaultRegistry();
    static final List<DashboardWidgetDefinition> WIDGETS = WIDGET_REGISTRY.definitions();

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private AvatarDashboardComponents() {
    }

    static Component page(AvatarDashboardData data, boolean editMode) {
        return page(data, "dashboard", editMode);
    }

    static Component page(AvatarDashboardData data, String activeTab, boolean editMode) {
        return page(data, activeTab, editMode, true);
    }

    static Component pageFragment(AvatarDashboardData data, boolean editMode) {
        return page(data, "dashboard", editMode, false);
    }

    private static Component page(AvatarDashboardData data, String activeTab, boolean editMode, boolean includeAssets) {
        String normalizedTab = normalizeTab(activeTab);
        boolean dashboardEditMode = editMode && "dashboard".equals(normalizedTab);
        Div root = new Div()
            .withId("dashboard-home")
            .withClass(dashboardEditMode ? "avatar-page avatar-page-editing" : "avatar-page")
            .withAttribute("data-avatar-shell", "true")
            .withAttribute("data-dashboard-home", "true")
            .withAttribute("data-dashboard-id", data.dashboard().id())
            .withChild(new Div().withClass("avatar-shell")
                .withChild(dashboardSelector(data))
                .withChild(new Div().withClass("avatar-shell-grid")
                    .withChild(new Div().withClass("avatar-shell-rail")
                        .withChild(compactChat(data.defaultModel())))
                    .withChild(new Div().withClass("avatar-shell-main")
                        .withChild(dashboardPanel(data, dashboardEditMode)))))
            .withChild(editContainer())
            .withChild(new Div().withId("avatar-output-preview").withClass("avatar-output-preview"));
        if (includeAssets) {
            root.withChild(moduleScript("/js/avatar-chat.js?v=4"))
                .withChild(moduleScript("/js/avatar-layout-edit.js?v=1"))
                .withChild(moduleScript("/js/avatar-workarea-editor.js?v=2"))
                .withChild(moduleScript("/js/avatar-shell.js?v=6"));
        }
        return root;
    }

    private static Component dashboardSelector(AvatarDashboardData data) {
        Div selector = new Div()
            .withId("dashboard-selector")
            .withClass("dashboard-selector")
            .withAttribute("data-dashboard-selector", "true");
        for (UserDashboard dashboard : safeDashboards(data.dashboards())) {
            HtmlTag link = new HtmlTag("a")
                .withClass("dashboard-selector-item"
                    + (dashboard.id().equals(data.dashboard().id()) ? " active" : ""))
                .withAttribute("href", "/dashboards/" + url(dashboard.id()))
                .withAttribute("hx-get", "/dashboards/" + url(dashboard.id()) + "/_page")
                .withAttribute("hx-target", "#dashboard-home")
                .withAttribute("hx-swap", "outerHTML")
                .withAttribute("hx-push-url", "/dashboards/" + url(dashboard.id()))
                .withInnerText(dashboard.name());
            selector.withChild(link);
        }
        selector.withChild(Button.create("+")
            .withClass("dashboard-create-button")
            .withAttribute("type", "button")
            .withAttribute("title", "Create dashboard")
            .withAttribute("aria-label", "Create dashboard")
            .withAttribute("hx-get", "/dashboards/_create")
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML"));
        return selector;
    }

    static Component createDashboardModal(String name, String error) {
        Div body = new Div().withClass("avatar-stack-form");
        if (error != null && !error.isBlank()) {
            body.withChild(new Div().withClass("avatar-status-error").withInnerText(error));
        }
        Form form = Form.create().withClass("avatar-stack-form");
        form.withAttribute("hx-post", "/dashboards");
        form.withAttribute("hx-target", "body");
        form.withAttribute("hx-swap", "outerHTML");
        form.withChild(TextInput.create("name")
            .withValue(name == null ? "" : name)
            .withPlaceholder("Dashboard name")
            .withAttribute("required", "required")
            .withAttribute("aria-invalid", error != null && !error.isBlank() ? "true" : "false"));
        form.withChild(Button.submit("Create Dashboard"));
        body.withChild(form);
        return new Div().withId("dashboard-create-modal").withClass("avatar-modal")
            .withChild(new Div().withClass("avatar-edit-panel")
                .withChild(new Div().withClass("avatar-edit-header")
                    .withChild(Header.H2("Create Dashboard"))
                    .withChild(Button.create("Close")
                        .withAttribute("type", "button")
                        .withAttribute("hx-get", "/dashboards/_modal/clear")
                        .withAttribute("hx-target", "#avatar-edit-container")
                        .withAttribute("hx-swap", "innerHTML")))
                .withChild(body));
    }

    private static List<UserDashboard> safeDashboards(List<UserDashboard> dashboards) {
        return dashboards == null ? List.of() : dashboards;
    }

    private static Component dashboardPanel(AvatarDashboardData data, boolean editMode) {
        String dashboardUrl = editMode
            ? "/dashboards/" + url(data.dashboard().id())
            : "/dashboards/" + url(data.dashboard().id()) + "?edit=true";
        HtmlTag editLink = iconLink(
            editMode ? "close" : "settings",
            editMode ? "Exit dashboard layout edit" : "Edit dashboard layout",
            dashboardUrl
        );
        editLink.withAttribute("hx-get", editMode
                ? "/dashboards/" + url(data.dashboard().id()) + "/_page"
                : "/dashboards/" + url(data.dashboard().id()) + "/_page?edit=true")
            .withAttribute("hx-target", "#dashboard-home")
            .withAttribute("hx-swap", "outerHTML")
            .withAttribute("hx-push-url", dashboardUrl);
        return new Div()
            .withId("dashboard-panel")
            .withClass("avatar-tab-panel avatar-tab-panel-dashboard")
            .withAttribute("data-dashboard-panel", data.dashboard().id())
            .withChild(new Div().withClass("avatar-shell-strip")
                .withChild(new HtmlTag("span").withClass("avatar-shell-note")
                    .withInnerText(editMode ? "Dashboard edit mode" : "Dashboard"))
                .withChild(editLink))
            .withChild(new Div().withClass("avatar-dashboard-panel")
                .withChild(widgetGrid(data, editMode)));
    }

    static Component widgetGrid(AvatarDashboardData data) {
        return widgetGrid(data, false);
    }

    static Component widgetGrid(AvatarDashboardData data, boolean editMode) {
        List<AvatarDashboardRow> rows = data.rows() == null ? List.of() : data.rows();
        Div grid = new Div()
            .withId("avatar-widget-grid")
            .withClass((rows.isEmpty() ? "avatar-widget-grid" : "avatar-widget-grid avatar-row-widget-grid")
                + (editMode ? " avatar-widget-grid-editing" : ""))
            .withAttribute("data-avatar-widget-grid", "true");
        if (rows.isEmpty()) {
            return grid.withChild(emptyDashboard(data.dashboard(), editMode));
        }
        for (int i = 0; i < rows.size(); i++) {
            AvatarDashboardRow row = rows.get(i);
            if (!editMode && row.widgets().isEmpty()) {
                continue;
            }
            grid.withChild(rowShell(data, row, i, rows.size(), editMode));
            if (editMode && !row.widgets().isEmpty()) {
                grid.withChild(insertRowSection(row));
            }
        }
        return grid;
    }

    private static Component emptyDashboard(UserDashboard dashboard, boolean editMode) {
        Div empty = new Div().withClass("dashboard-empty-state");
        empty.withChild(Header.H2((dashboard == null ? "Dashboard" : dashboard.name()) + " is empty"));
        empty.withChild(small("Add a row to start placing widgets."));
        empty.withChild(Button.create("Add Row")
            .withAttribute("type", "button")
            .withAttribute("hx-post", "/dashboards/" + url(dashboard.id()) + "/_layout/rows")
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML"));
        if (!editMode) {
            empty.withChild(new HtmlTag("a")
                .withClass("button button-secondary small")
                .withAttribute("href", "/dashboards/" + url(dashboard.id()) + "?edit=true")
                .withAttribute("hx-get", "/dashboards/" + url(dashboard.id()) + "/_page?edit=true")
                .withAttribute("hx-target", "#dashboard-home")
                .withAttribute("hx-swap", "outerHTML")
                .withAttribute("hx-push-url", "/dashboards/" + url(dashboard.id()) + "?edit=true")
                .withInnerText("Edit"));
        }
        return empty;
    }

    static Component widget(AvatarDashboardData data, AvatarDashboardWidget widget) {
        return widget(data, widget, null, false);
    }

    private static Component widget(
        AvatarDashboardData data,
        AvatarDashboardWidget widget,
        AvatarDashboardRowWidget layoutWidget,
        boolean editMode
    ) {
        String type = widgetType(widget);
        DashboardWidgetDefinition definition = definition(type);
        Div frame = new Div()
            .withId(rootId(widget.widgetId()))
            .withClass("avatar-widget")
            .withClass("avatar-widget-" + size(widget))
            .withAttribute("data-avatar-widget", widget.widgetId())
            .withAttribute("data-avatar-widget-type", type)
            .withAttribute("data-avatar-widget-enabled", Boolean.toString(widget.enabled()));
        if (editMode && layoutWidget != null) {
            frame.withClass("avatar-widget-editing");
        }
        if (!widget.enabled()) {
            frame.withClass("avatar-widget-disabled");
        }
        frame.withChild(widgetCornerControls(data.dashboard().id(), widget.widgetId(), type, layoutWidget, editMode));
        frame.withChild(new Div().withClass("avatar-widget-header")
            .withChild(Header.H2(definition.title())));
        if (!widget.enabled()) {
            return frame.withChild(empty("Disabled in layout."));
        }
        return frame.withChild(widgetBody(data, widget));
    }

    static Component editModal(List<AvatarDashboardRow> rows) {
        Div panel = new Div()
            .withId("avatar-layout-editor")
            .withClass("avatar-edit-panel")
            .withAttribute("data-avatar-layout-editor", "true");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(new Div()
                .withChild(Header.H2("Edit Dashboard"))
                .withChild(small("Rows use 12-column widths. Each first-party widget can appear once.")))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/dashboards/_modal/clear")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));

        Div list = new Div().withClass("avatar-edit-list");
        List<AvatarDashboardRow> safeRows = rows == null ? List.of() : rows;
        for (int i = 0; i < safeRows.size(); i++) {
            list.withChild(editRow(safeRows.get(i), i, safeRows.size()));
        }
        if (safeRows.isEmpty()) {
            list.withChild(empty("No layout rows yet."));
        }
        panel.withChild(list);
        panel.withChild(new Div().withClass("avatar-edit-actions")
            .withChild(Button.create("Add Row")
                .withAttribute("type", "button")
                .withAttribute("hx-post", "/_dashboards/_layout/rows")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        return new Div().withId("avatar-edit-modal").withClass("avatar-modal").withChild(panel);
    }

    static Component layoutSavedResponse(AvatarDashboardData data) {
        return layoutEditResponse(data, true);
    }

    static Component layoutEditResponse(AvatarDashboardData data, boolean editMode) {
        Div grid = (Div) widgetGrid(data, editMode);
        grid.withAttribute("hx-swap-oob", "true");
        return new Div()
            .withChild(editContainer().withAttribute("hx-swap-oob", "true"))
            .withChild(grid);
    }

    static Component layoutEditResponseWithCatalog(AvatarDashboardData data, String rowId) {
        Div grid = (Div) widgetGrid(data, true);
        grid.withAttribute("hx-swap-oob", "true");
        return new Div()
            .withChild(widgetCatalogModal(data.rows(), rowId))
            .withChild(grid);
    }

    static Component widgetDetailModal(AvatarDashboardData data, AvatarDashboardWidget widget) {
        String type = widgetType(widget);
        Div panel = new Div().withClass("avatar-edit-panel avatar-widget-detail-panel");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(Header.H2(definition(type).title()))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/dashboards/_modal/clear")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        panel.withChild(new Div().withClass("avatar-widget avatar-widget-detail")
            .withChild(widgetBody(data, widget)));
        return new Div().withId("avatar-widget-detail-modal").withClass("avatar-modal").withChild(panel);
    }

    static Component widgetSettingsModal(
        AvatarDashboardData data,
        AvatarDashboardRowWidget widget,
        WidgetSettingsValidation validation
    ) {
        DashboardWidgetDefinition definition = definition(widget.widgetKey());
        Map<String, Object> settings = validation == null || validation.settings().isEmpty()
            ? mergedSettings(definition, widget.settings())
            : validation.settings();
        Div panel = new Div().withClass("avatar-edit-panel avatar-widget-settings-panel");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(new Div()
                .withChild(Header.H2(definition.title() + " Settings"))
                .withChild(small("Widget instance " + widget.id())))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/dashboards/_modal/clear")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        if (validation != null && !validation.valid()) {
            Div errors = new Div().withClass("avatar-status-error");
            for (String error : validation.errors()) {
                errors.withChild(new Div().withInnerText(error));
            }
            panel.withChild(errors);
        }
        Form form = Form.create().withClass("avatar-stack-form avatar-widget-settings-form");
        form.withAttribute("hx-put", "/dashboards/" + url(data.dashboard().id()) + "/widgets/" + widget.id() + "/settings");
        form.withAttribute("hx-target", "#avatar-edit-container");
        form.withAttribute("hx-swap", "innerHTML");
        allowHtmxErrorSwap(form);
        form.withChild(new Div().withClass("avatar-settings-meta")
            .withChild(metaLine("Type", definition.type()))
            .withChild(metaLine("Policy", policyLabel(definition.instancePolicy())))
            .withChild(metaLine("Binding", definition.bindingMode().name().toLowerCase(Locale.ROOT))));
        for (WidgetSettingsField field : definition.settingsSchema().fields()) {
            if (field.hidden()) {
                form.withChild(hiddenInput(field.name(), value(settings, field.name())));
            } else if (!field.allowedValues().isEmpty()) {
                Select select = Select.create(field.name());
                for (String option : field.allowedValues()) {
                    select.addOption(option, optionLabel(option), option.equals(value(settings, field.name())));
                }
                form.withChild(new Div().withClass("avatar-settings-field")
                    .withChild(new HtmlTag("label").withInnerText(field.label()))
                    .withChild(select));
            } else {
                form.withChild(new Div().withClass("avatar-settings-field")
                    .withChild(new HtmlTag("label").withInnerText(field.label()))
                    .withChild(TextInput.create(field.name()).withValue(value(settings, field.name()))));
            }
        }
        form.withChild(Button.submit("Save Settings"));
        panel.withChild(form);
        return new Div().withId("avatar-widget-settings-modal").withClass("avatar-modal").withChild(panel);
    }

    static Component widgetSettingsSaveResponse(AvatarDashboardData data, AvatarDashboardRowWidget rowWidget) {
        AvatarDashboardWidget widget = displayWidget(rowWidget);
        Div refreshed = (Div) widget(data, widget, rowWidget, false);
        refreshed.withAttribute("hx-swap-oob", "true");
        return new Div()
            .withChild(editContainer().withAttribute("hx-swap-oob", "true"))
            .withChild(refreshed);
    }

    static Component widgetWidthPicker(List<AvatarDashboardRow> rows, AvatarDashboardRowWidget widget) {
        DashboardWidgetDefinition definition = definition(widget.widgetKey());
        int maxWidth = maxWidthForWidget(rows, widget);
        Div panel = new Div()
            .withClass("avatar-width-picker-popover")
            .withAttribute("data-avatar-width-picker", "true")
            .withAttribute("data-avatar-widget-id", widget.id());
        panel.withChild(new Div().withClass("avatar-width-picker-header")
            .withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText("Widget width"))
                .withChild(small(definition.title() + " is " + widget.columnWidth() + "/12. Up to " + maxWidth + "/12 fits in this row.")))
            .withChild(iconButton("close", "Close width picker", "Close width picker")
                .withAttribute("data-avatar-width-picker-dismiss", "true")));

        Div presets = new Div().withClass("avatar-width-preset-grid");
        for (int width : List.of(3, 4, 6, 8, 12)) {
            boolean available = width <= maxWidth;
            Form preset = Form.create().withClass("avatar-width-preset-form");
            preset.withAttribute("hx-put", "/_dashboards/_layout/widgets/" + widget.id() + "/width");
            preset.withAttribute("hx-target", "#avatar-edit-container");
            preset.withAttribute("hx-swap", "innerHTML");
            preset.withChild(hiddenInput("columnWidth", Integer.toString(width)));
            Button button = Button.submit(width + "/12");
            button.withClass("avatar-width-preset-button");
            if (width == widget.columnWidth()) {
                button.withClass("active");
            }
            if (!available) {
                button.withAttribute("disabled", "disabled");
                button.withAttribute("title", "This row does not have room for " + width + "/12");
            }
            preset.withChild(button);
            presets.withChild(preset);
        }
        panel.withChild(new Div().withClass("avatar-width-picker-section")
            .withChild(new HtmlTag("strong").withInnerText("Presets"))
            .withChild(presets));

        Form custom = Form.create().withClass("avatar-width-custom-form");
        custom.withAttribute("hx-put", "/_dashboards/_layout/widgets/" + widget.id() + "/width");
        custom.withAttribute("hx-target", "#avatar-edit-container");
        custom.withAttribute("hx-swap", "innerHTML");
        custom.withChild(new Div().withClass("avatar-width-custom-grid")
            .withChild(TextInput.number("columnWidth")
                .withMin("1")
                .withMax(Integer.toString(maxWidth))
                .withValue(Integer.toString(widget.columnWidth()))
                .withAttribute("step", "1")
                .withAttribute("inputmode", "numeric")
                .withAttribute("aria-label", "Custom widget width in twelfths"))
            .withChild(Button.submit("Apply")));
        custom.withChild(small("Enter any width from 1/12 to " + maxWidth + "/12 that still fits this row."));
        panel.withChild(new Div().withClass("avatar-width-picker-section")
            .withChild(new HtmlTag("strong").withInnerText("Custom"))
            .withChild(custom));

        return new Div().withId("avatar-width-picker-root")
            .withClass("avatar-inline-popover-root")
            .withChild(new Div()
                .withClass("avatar-inline-popover-backdrop")
                .withAttribute("data-avatar-width-picker-dismiss", "true"))
            .withChild(panel);
    }

    private static Component legacyLayoutEditResponse(AvatarDashboardData data) {
        List<AvatarDashboardRow> rows = data.rows() == null ? List.of() : data.rows();
        Div grid = new Div().withId("avatar-widget-grid")
            .withAttribute("hx-swap-oob", "true")
            .withClass(rows.isEmpty() ? "avatar-widget-grid" : "avatar-widget-grid avatar-row-widget-grid")
            .withAttribute("data-avatar-widget-grid", "true");
        for (Component child : widgetGridChildren(data)) {
            grid.withChild(child);
        }
        return new Div()
            .withChild(editModal(data.rows()))
            .withChild(grid);
    }

    static Component widgetCatalogModal(List<AvatarDashboardRow> rows, String rowId) {
        return widgetCatalogModal(rows, rowId, null);
    }

    static Component widgetCatalogModal(List<AvatarDashboardRow> rows, String rowId, String error) {
        java.util.Map<String, Long> used = rows == null ? java.util.Map.of() : rows.stream()
            .flatMap(row -> row.widgets().stream())
            .map(AvatarDashboardRowWidget::widgetKey)
            .collect(java.util.stream.Collectors.groupingBy(key -> key, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        Div panel = new Div().withClass("avatar-edit-panel avatar-widget-catalog avatar-widget-picker-modal");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(new Div()
                .withChild(Header.H2("Add Widget"))
                .withChild(small("Pick one module for this row. Widths follow the 12-column dashboard grid.")))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/dashboards/_modal/clear")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        if (error != null && !error.isBlank()) {
            panel.withChild(new Div().withClass("avatar-status-error").withInnerText(error));
        }
        Div catalog = new Div().withClass("avatar-catalog-grid");
        for (DashboardWidgetDefinition definition : WIDGETS) {
            boolean disabled = definition.singleInstance() && used.containsKey(definition.type());
            Form form = Form.create().withClass("avatar-catalog-item");
            form.withAttribute("hx-post", "/_dashboards/_layout/rows/" + rowId + "/widgets");
            form.withAttribute("hx-target", "#avatar-edit-container");
            form.withAttribute("hx-swap", "innerHTML");
            allowHtmxErrorSwap(form);
            if (disabled) {
                form.withClass("avatar-catalog-item-disabled");
                form.withAttribute("aria-disabled", "true");
            }
            form.withChild(new HtmlTag("input", true)
                .withAttribute("type", "hidden")
                .withAttribute("name", "widgetKey")
                .withAttribute("value", definition.type()));
            form.withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText(definition.title()))
                .withChild(small(definition.description())));
            if (disabled) {
                form.withChild(small("Already on this dashboard."));
            }
            form.withChild(widthSelect("columnWidth", defaultWidth(definition), false));
            Button button = Button.submit(disabled ? "Added" : "Add");
            if (disabled) {
                button.withAttribute("disabled", "disabled");
            }
            form.withChild(button);
            catalog.withChild(form);
        }
        panel.withChild(catalog);
        return new Div().withId("avatar-edit-modal").withClass("avatar-modal avatar-widget-picker").withChild(panel);
    }

    static Component organizerModal(
        String activeTab,
        List<PlannerTask> plannerTasks,
        Map<String, List<PlannerSubtodo>> subtodos,
        List<PlannerCalendarProjection> projections,
        List<AvatarTodo> todos,
        List<AvatarCalendarItem> calendarItems,
        List<AvatarNote> notes
    ) {
        String tab = activeTab == null ? "planner" : activeTab;
        Div panel = new Div().withClass("avatar-edit-panel avatar-organizer-panel");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(Header.H2("Organizer"))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/dashboards/_modal/clear")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        panel.withChild(organizerTabs(tab));
        panel.withChild(switch (tab) {
            case "todos" -> organizerTodoTab(todos);
            case "calendar" -> organizerCalendarTab(calendarItems, projections);
            case "notes" -> organizerNotesTab(notes);
            default -> organizerPlannerTab(plannerTasks, subtodos);
        });
        return new Div().withId("avatar-organizer-modal").withClass("avatar-modal").withChild(panel);
    }

    private static Component organizerTabs(String activeTab) {
        Div tabs = new Div().withClass("avatar-tabs");
        tabs.withChild(organizerTab("planner", "Planner", activeTab));
        tabs.withChild(organizerTab("todos", "Todos", activeTab));
        tabs.withChild(organizerTab("calendar", "Calendar", activeTab));
        tabs.withChild(organizerTab("notes", "Notes", activeTab));
        return tabs;
    }

    private static Component organizerTab(String tab, String label, String activeTab) {
        HtmlTag button = new HtmlTag("button")
            .withAttribute("type", "button")
            .withAttribute("hx-get", "/_dashboards/_organizer?tab=" + tab)
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML")
            .withInnerText(label);
        if (tab.equals(activeTab)) {
            button.withClass("active");
        }
        return button;
    }

    private static Component organizerPlannerTab(
        List<PlannerTask> plannerTasks,
        Map<String, List<PlannerSubtodo>> subtodos
    ) {
        Div body = new Div().withClass("avatar-organizer-body");
        Form form = Form.create().withClass("avatar-stack-form avatar-planner-form");
        form.withAttribute("hx-post", "/_dashboards/_planner-tasks");
        form.withAttribute("hx-target", "#avatar-edit-container");
        form.withAttribute("hx-swap", "innerHTML");
        form.withChild(TextInput.create("title").withPlaceholder("Planner task title"));
        form.withChild(TextArea.create("notes").withRows(3).withPlaceholder("Notes"));
        form.withChild(new Div().withClass("avatar-form-grid")
            .withChild(prioritySelect())
            .withChild(TextInput.create("startsAt").withAttribute("type", "datetime-local"))
            .withChild(TextInput.create("dueAt").withAttribute("type", "datetime-local")));
        form.withChild(recurrenceFields());
        form.withChild(new Div().withClass("avatar-form-grid")
            .withChild(TextInput.create("linkedProjectId").withPlaceholder("Project link"))
            .withChild(TextInput.create("linkedAssignmentId").withPlaceholder("Assignment link"))
            .withChild(TextInput.create("linkedJobId").withPlaceholder("Job link"))
            .withChild(TextInput.create("linkedOutputId").withPlaceholder("Output link")));
        form.withChild(Button.submit("Create Planner Task"));
        body.withChild(form);

        Div list = new Div().withClass("avatar-list");
        List<PlannerTask> safeTasks = plannerTasks == null ? List.of() : plannerTasks;
        if (safeTasks.isEmpty()) {
            list.withChild(empty("No planner tasks yet."));
        }
        for (PlannerTask task : safeTasks.stream().limit(12).toList()) {
            Div item = new Div().withClass("avatar-list-row avatar-planner-task");
            item.withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText(task.title()))
                .withChild(small(taskMeta(task))));
            Div taskBody = new Div().withClass("avatar-planner-task-body");
            List<PlannerSubtodo> taskSubtodos = subtodos == null
                ? List.of()
                : subtodos.getOrDefault(task.id(), List.of());
            for (PlannerSubtodo subtodo : taskSubtodos.stream().limit(4).toList()) {
                taskBody.withChild(small("- " + subtodo.title()));
            }
            Form subtodoForm = Form.create().withClass("avatar-inline-form");
            subtodoForm.withAttribute("hx-post", "/_dashboards/_planner-tasks/" + task.id() + "/subtodos");
            subtodoForm.withAttribute("hx-target", "#avatar-edit-container");
            subtodoForm.withAttribute("hx-swap", "innerHTML");
            subtodoForm.withChild(TextInput.create("title").withPlaceholder("Add subtodo"));
            subtodoForm.withChild(Button.submit("Add Subtodo"));
            taskBody.withChild(subtodoForm);
            item.withChild(taskBody);
            list.withChild(item);
        }
        return body.withChild(list);
    }

    private static Component organizerTodoTab(List<AvatarTodo> todos) {
        Div body = new Div().withClass("avatar-organizer-body");
        return body.withChild(todos(todos, rootId("todos")));
    }

    private static Component organizerCalendarTab(
        List<AvatarCalendarItem> items,
        List<PlannerCalendarProjection> projections
    ) {
        Div body = new Div().withClass("avatar-organizer-body");
        body.withChild(calendar(items, rootId("calendar")));
        Div projected = new Div().withClass("avatar-list");
        List<PlannerCalendarProjection> safeProjections = projections == null ? List.of() : projections;
        if (safeProjections.isEmpty()) {
            projected.withChild(empty("No planner projections."));
        }
        for (PlannerCalendarProjection projection : safeProjections.stream().limit(8).toList()) {
            projected.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText("Planner occurrence"))
                    .withChild(small(formatInstant(projection.occurrenceStart())))));
        }
        body.withChild(new Div().withClass("avatar-section-heading").withInnerText("Planner projection"));
        return body.withChild(projected);
    }

    private static Component organizerNotesTab(List<AvatarNote> notes) {
        Div body = new Div().withClass("avatar-organizer-body");
        DashboardNotesView view = new DashboardNotesView(
            "personal",
            "Personal notes",
            null,
            "",
            "",
            "",
            notes,
            List.of()
        );
        return body.withChild(notes(view, rootId("notes"), "/_dashboards/_notes", null, defaultWidget(definition("notes"), 0)));
    }

    private static Component recurrenceFields() {
        Div group = new Div().withClass("avatar-recurrence-fields");
        group.withChild(new Div().withClass("avatar-form-grid")
            .withChild(Select.create("recurrenceMode")
                .addOption("NONE", "No repeat", true)
                .addOption("DAILY", "Daily", false)
                .addOption("WEEKLY", "Weekly", false)
                .addOption("MONTHLY", "Monthly", false)
                .addOption("CRON", "Cron", false))
            .withChild(TextInput.create("recurrenceInterval")
                .withAttribute("type", "number")
                .withAttribute("min", "1")
                .withAttribute("value", "1"))
            .withChild(TextInput.create("recurrenceStartDate").withAttribute("type", "date"))
            .withChild(TextInput.create("recurrenceEndDate").withAttribute("type", "date"))
            .withChild(TextInput.create("recurrenceTime").withAttribute("type", "time")));
        group.withChild(new Div().withClass("avatar-form-grid")
            .withChild(Select.create("recurrenceWeekday")
                .addOption("", "Any weekday", true)
                .addOption("MONDAY", "Monday", false)
                .addOption("TUESDAY", "Tuesday", false)
                .addOption("WEDNESDAY", "Wednesday", false)
                .addOption("THURSDAY", "Thursday", false)
                .addOption("FRIDAY", "Friday", false)
                .addOption("SATURDAY", "Saturday", false)
                .addOption("SUNDAY", "Sunday", false))
            .withChild(TextInput.create("recurrenceMonthDay")
                .withPlaceholder("Month day")
                .withAttribute("type", "number")
                .withAttribute("min", "1")
                .withAttribute("max", "31"))
            .withChild(TextInput.create("recurrenceCron").withPlaceholder("Advanced cron")));
        return group;
    }

    private static Component prioritySelect() {
        return Select.create("priority")
            .addOption("NORMAL", "Normal", true)
            .addOption("HIGH", "High", false)
            .addOption("URGENT", "Urgent", false)
            .addOption("LOW", "Low", false);
    }

    private static String taskMeta(PlannerTask task) {
        String status = task.status() == null ? "planned" : task.status().name().toLowerCase(Locale.ROOT);
        String due = task.dueAt() == null ? "unscheduled" : formatInstant(task.dueAt());
        String recurrence = task.recurrence() == null || task.recurrence().mode() == null
            ? "none"
            : task.recurrence().mode().name().toLowerCase(Locale.ROOT);
        return status + " / due " + due + " / repeat " + recurrence;
    }

    private static boolean recurring(PlannerTask task) {
        return task.recurrence() != null
            && task.recurrence().mode() != null
            && !"NONE".equals(task.recurrence().mode().name());
    }

    private static String projectLinkText(PlannerTask task) {
        if (task.link() == null || task.link().projectId() == null || task.link().projectId().isBlank()) {
            return "";
        }
        return " / project " + task.link().projectId();
    }

    private static String actionLabel(String action) {
        return action == null ? "Update" : action.charAt(0) + action.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String timeLabel(Instant instant) {
        if (instant == null) {
            return "unscheduled";
        }
        return DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US).format(instant.atZone(ZoneId.systemDefault()));
    }

    static Component outputPreview(RunOutputArtifact artifact, String content) {
        return new Div()
            .withId("avatar-output-preview")
            .withClass("avatar-output-preview")
            .withChild(new Div().withClass("avatar-output-preview-header")
                .withChild(Header.H2(artifact.outputName() == null ? "Output" : artifact.outputName()))
                .withChild(new HtmlTag("a")
                    .withClass("orch-primary")
                    .withAttribute("href", "/api/outputs/" + artifact.id() + "/download")
                    .withInnerText("Download")))
            .withChild(metaLine("Type", artifact.artifactType()))
            .withChild(new HtmlTag("pre").withInnerText(content == null ? "" : content));
    }

    static Component statusFragment(String message, boolean error) {
        return new Div().withClass(error ? "orch-status orch-status-error" : "orch-status")
            .withInnerText(message);
    }

    private static List<Component> widgetGridChildren(AvatarDashboardData data) {
        List<AvatarDashboardRow> rows = data.rows() == null ? List.of() : data.rows();
        if (rows.isEmpty()) {
            return normalizedLayout(data.layout()).stream()
                .map(widget -> widget(data, widget))
                .toList();
        }
        return rows.stream()
            .map(row -> {
                Row layoutRow = new Row().withId("avatar-dashboard-row-" + row.id());
                layoutRow.withAttribute("class", "row avatar-dashboard-row");
                for (AvatarDashboardRowWidget rowWidget : row.widgets()) {
                    layoutRow.addColumn(Column.create()
                        .withWidth(rowWidget.columnWidth())
                        .withChild(widget(data, displayWidget(rowWidget))));
                }
                return (Component) layoutRow;
            })
            .toList();
    }

    private static Component rowShell(
        AvatarDashboardData data,
        AvatarDashboardRow dashboardRow,
        int index,
        int rowCount,
        boolean editMode
    ) {
        Div shell = new Div()
            .withClass(editMode
                ? "avatar-dashboard-row-shell editable-row-wrapper avatar-dashboard-row-shell-editing"
                : "avatar-dashboard-row-shell")
            .withAttribute("data-avatar-row-id", dashboardRow.id());
        if (editMode && dashboardRow.widgets().isEmpty()) {
            shell.withClass("avatar-empty-row-shell");
            shell.withChild(rowDecoration(dashboardRow, index, rowCount));
            shell.withChild(emptyRowInsert(dashboardRow));
            return shell;
        }
        Row row = new Row().withId("avatar-dashboard-row-" + dashboardRow.id());
        row.withAttribute("class", "row avatar-dashboard-row");
        for (AvatarDashboardRowWidget rowWidget : dashboardRow.widgets()) {
            row.addColumn(Column.create()
                .withWidth(rowWidget.columnWidth())
                .withChild(widget(data, displayWidget(rowWidget), rowWidget, editMode)));
        }
        if (editMode) {
            shell.withChild(rowDecoration(dashboardRow, index, rowCount));
        }
        shell.withChild(row);
        if (editMode) {
            shell.withChild(addModuleSection(dashboardRow, index, rowCount));
        }
        return shell;
    }

    private static Component shellTabs(String activeTab, boolean editMode) {
        return new Div().withId("avatar-shell-tabs-wrap").withClass("avatar-shell-tabs-wrap")
            .withChild(new Div().withClass("avatar-shell-strip")
                .withChild(new Div()
                    .withChild(Header.H2("Assistant"))
                    .withChild(small("Dashboard with a persistent assistant rail.")))
                .withChild(dashboardActions(editMode)));
    }

    private static Component dashboardActions(boolean editMode) {
        Div actions = new Div().withClass("avatar-shell-actions");
        if (editMode) {
            actions.withChild(new HtmlTag("span").withClass("avatar-shell-note")
                .withInnerText("Dashboard edit mode"));
        }
        return actions.withChild(iconLink(
            editMode ? "close" : "settings",
            editMode ? "Exit dashboard layout edit" : "Edit dashboard layout",
            editMode ? "/dashboards/assistant" : "/dashboards/assistant?edit=true"
        ));
    }

    static Component tabPanel(AvatarDashboardData data, String activeTab, boolean editMode) {
        Div panel = new Div()
            .withId("avatar-tab-panel")
            .withClass("avatar-tab-panel avatar-tab-panel-dashboard")
            .withAttribute("data-avatar-tab-panel", "dashboard");
        return panel.withChild(new Div().withClass("avatar-dashboard-panel")
            .withChild(widgetGrid(data, editMode)));
    }

    static Component tabPanelResponse(AvatarDashboardData data, String activeTab, boolean editMode) {
        Div tabs = (Div) shellTabs(activeTab, editMode);
        tabs.withAttribute("hx-swap-oob", "true");
        return new Div()
            .withChild(tabs)
            .withChild(tabPanel(data, activeTab, editMode));
    }

    private static Div tabSection(String title, String subtitle) {
        return new Div().withClass("avatar-tab-section")
            .withChild(new Div().withClass("avatar-tab-section-header")
                .withChild(new Div()
                    .withChild(Header.H2(title))
                    .withChild(small(subtitle))));
    }

    private static Component queuePanel(List<WorkAssignment> assignments, List<AgentProfile> agents) {
        Div panel = new Div().withClass("avatar-tab-card");
        List<WorkAssignment> safeAssignments = assignments == null ? List.of() : assignments;
        if (safeAssignments.isEmpty()) {
            return panel.withChild(empty("No active assignments are visible to Assistant."));
        }
        Map<String, String> agentNames = new LinkedHashMap<>();
        if (agents != null) {
            for (AgentProfile agent : agents) {
                agentNames.put(agent.id(), agent.name() == null ? agent.id() : agent.name());
            }
        }
        Div list = new Div().withClass("avatar-list");
        for (WorkAssignment assignment : safeAssignments.stream().limit(12).toList()) {
            String agentName = assignment.agentId() == null ? "agent" : agentNames.getOrDefault(assignment.agentId(), assignment.agentId());
            String status = assignment.status() == null ? "unknown" : assignment.status().name().toLowerCase(Locale.ROOT);
            String type = assignment.assignmentType() == null ? "assignment" : assignment.assignmentType().name().toLowerCase(Locale.ROOT);
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(agentName))
                    .withChild(small(type + " / " + status))));
        }
        return panel.withChild(list);
    }

    private static Component historyPanel(List<JobDefinition> jobs, List<WorkAssignment> assignments, List<RunOutputArtifact> outputs) {
        Div wrapper = new Div().withClass("avatar-tab-stack");
        wrapper.withChild(new Div().withClass("avatar-tab-card")
            .withChild(new HtmlTag("strong").withInnerText("Recent work"))
            .withChild(small("History expands from existing runtime data first; deeper chat/session history wiring follows this baseline."))
            .withChild(recentWork(jobs, assignments, outputs)));
        return wrapper;
    }

    private static Component profilePanel(AvatarProfile profile, List<AgentProfile> agents, String defaultModel) {
        Div panel = new Div().withClass("avatar-tab-card avatar-profile-card");
        AgentProfile avatarAgent = agents == null ? null : agents.stream()
            .filter(agent -> "avatar".equals(agent.id()))
            .findFirst()
            .orElse(null);
        Div grid = new Div().withClass("avatar-profile-grid");
        grid.withChild(profileField("Display", profile == null ? "Assistant" : profile.displayName()));
        grid.withChild(profileField("Timezone", profile == null ? null : profile.timezone()));
        grid.withChild(profileField("Locale", profile == null ? null : profile.locale()));
        grid.withChild(profileField("Summary", profile == null ? null : profile.summary()));
        grid.withChild(profileField("Default Model", defaultModel));
        grid.withChild(profileField("Backing Agent", avatarAgent == null ? "avatar (not loaded)" : avatarAgent.name()));
        grid.withChild(profileField("Agent Status", avatarAgent == null || avatarAgent.status() == null
            ? "unknown"
            : avatarAgent.status().name().toLowerCase(Locale.ROOT)));
        return panel.withChild(grid);
    }

    private static Component profileField(String label, String value) {
        return new Div().withClass("avatar-profile-field")
            .withChild(new HtmlTag("span").withClass("avatar-profile-label").withInnerText(label))
            .withChild(new HtmlTag("strong").withInnerText(value == null || value.isBlank() ? "unset" : value));
    }

    private static Component outputsPanel(List<RunOutputArtifact> outputs) {
        return new Div().withClass("avatar-tab-card")
            .withChild(outputs(outputs));
    }

    private static Component workAreasPanel(List<WorkArea> workAreas) {
        return new Div().withClass("avatar-tab-card")
            .withChild(files(workAreas));
    }

    private static String shellNote(String activeTab) {
        return switch (normalizeTab(activeTab)) {
            case "queue" -> "Assistant-supervised queue view";
            case "history" -> "Recent execution and output history";
            case "profile" -> "Assistant identity and defaults";
            case "outputs" -> "Generated artifacts and previews";
            case "work-areas" -> "Confined workspace browser";
            default -> "Dashboard";
        };
    }

    private static Component compactChat(String defaultModel) {
        return new HtmlTag("aside")
            .withId("avatar-chat")
            .withClass("avatar-chat")
            .withAttribute("data-avatar-chat", "true")
            .withAttribute("data-chat-surface", "avatar")
            .withAttribute("data-default-model", defaultModel == null ? "" : defaultModel)
            .withAttribute("data-avatar-chat-rail", "true")
            .withChild(new Div().withClass("avatar-chat-header")
                .withChild(new Div()
                    .withChild(Header.H2("Assistant Chat"))
                    .withChild(small("Dashboard assistant channel")))
                .withChild(new Div().withClass("avatar-chat-chips")
                    .withChild(new HtmlTag("span").withClass("avatar-chip").withInnerText("surface home"))
                    .withChild(new HtmlTag("span").withId("avatar-chat-session").withClass("avatar-chip").withInnerText("new chat"))))
            .withChild(new Div().withClass("avatar-chat-status")
                .withChild(new HtmlTag("span").withId("avatar-chat-status").withInnerText("Ready"))
                .withChild(new HtmlTag("span").withInnerText(defaultModel == null || defaultModel.isBlank()
                    ? "model unset"
                    : "model " + defaultModel)))
            .withChild(new Div().withId("avatar-chat-messages")
                .withClass("avatar-chat-messages")
                .withAttribute("aria-live", "polite")
                .withChild(new Div().withClass("avatar-chat-empty").withInnerText("Ask the assistant for a quick update.")))
            .withChild(Form.create().withId("avatar-chat-form").withClass("avatar-chat-form")
                .withChild(TextArea.create("message").withId("avatar-chat-input").withRows(4)
                    .withPlaceholder("Ask the assistant"))
                .withChild(Button.submit("Send")))
            .withChild(new HtmlTag("button")
                .withClass("avatar-chat-corner-resizer")
                .withAttribute("type", "button")
                .withAttribute("data-avatar-chat-corner-resizer", "true")
                .withAttribute("aria-label", "Resize assistant chat")
                .withAttribute("title", "Resize assistant chat")
                .withInnerText(""));
    }

    private static Component widgetBody(AvatarDashboardData data, AvatarDashboardWidget widget) {
        String targetId = rootId(widget.widgetId());
        return switch (widgetType(widget)) {
            case "today-planner" -> todayPlanner(data.todayPlanner(), targetId);
            case "tasks-routines" -> tasksRoutines(data.tasksRoutines(), targetId);
            case "calendar-schedule" -> calendarSchedule(data.calendarSchedule(), targetId);
            case "daily-tasks" -> dailyTasks(data.dailyTasks(), targetId);
            case "todos" -> todos(data.todos(), targetId);
            case "calendar" -> calendar(data.calendarItems(), targetId);
            case "notes" -> notes(noteView(data, widget), targetId, notesPostUrl(data, widget), data, widget);
            case "projects" -> projects(projectView(data, widget), targetId, false);
            case "contacts-materials" -> projects(projectView(data, widget), targetId, true);
            case "files" -> files(data.workAreas());
            case "outputs" -> outputs(data.outputs());
            case "system" -> system(data.agents(), data.jobs(), data.assignments());
            case "alerts" -> alerts(data.events(), data.userInbox(), targetId);
            case "recent-work" -> recentWork(data.jobs(), data.assignments(), data.outputs());
            default -> empty("Unknown widget.");
        };
    }

    private static String notesPostUrl(AvatarDashboardData data, AvatarDashboardWidget widget) {
        String widgetId = widget.widgetId();
        String type = widgetType(widget);
        if (data != null && data.dashboard() != null && widgetId != null && !widgetId.equals(type)) {
            return "/dashboards/" + url(data.dashboard().id()) + "/widgets/" + url(widgetId) + "/_notes";
        }
        return "/_dashboards/_notes";
    }

    private static DashboardNotesView noteView(AvatarDashboardData data, AvatarDashboardWidget widget) {
        DashboardNotesView view = data == null || data.noteViews() == null ? null : data.noteViews().get(widget.widgetId());
        return view == null
            ? new DashboardNotesView("personal", "Personal notes", null, "", "", "", data == null ? List.of() : data.notes(), List.of())
            : view;
    }

    private static DashboardProjectContextView projectView(AvatarDashboardData data, AvatarDashboardWidget widget) {
        DashboardProjectContextView view = data == null || data.projectViews() == null ? null : data.projectViews().get(widget.widgetId());
        return view == null
            ? new DashboardProjectContextView(null, false, null, "Choose a project in widget settings.", List.of(), List.of(), List.of())
            : view;
    }

    private static Component todayPlanner(TodayPlannerView view, String targetId) {
        Div body = new Div().withClass("avatar-widget-body avatar-planner-summary");
        TodayPlannerView safe = view == null
            ? new TodayPlannerView(LocalDate.now(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
            : view;
        body.withChild(new Div().withClass("avatar-planner-metrics")
            .withChild(metric("Top", Integer.toString(safe.topPriorities().size())))
            .withChild(metric("Overdue", Integer.toString(safe.overdue().size())))
            .withChild(metric("Blocks", Integer.toString(safe.timeBlocks().size()))));
        Form capture = Form.create().withClass("avatar-inline-form");
        capture.withAttribute("hx-post", "/_dashboards/_today/quick-capture");
        capture.withAttribute("hx-target", "#" + targetId);
        capture.withAttribute("hx-swap", "outerHTML");
        capture.withChild(TextInput.create("title").withPlaceholder("Quick capture"));
        capture.withChild(Button.submit("Capture"));
        body.withChild(capture);
        body.withChild(phaseList("Top priorities", safe.topPriorities(), 3));
        body.withChild(phaseList("Now", safe.now(), 1));
        body.withChild(phaseList("Next", safe.next(), 1));
        body.withChild(phaseList("Later", safe.later(), 4));
        body.withChild(phaseList("Overdue", safe.overdue(), 4));
        body.withChild(phaseList("Unscheduled", safe.unscheduled(), 4));
        if (!safe.timeBlocks().isEmpty()) {
            Div blocks = new Div().withClass("avatar-timeblock-strip");
            for (PlannerTimeBlock block : safe.timeBlocks().stream().limit(4).toList()) {
                blocks.withChild(new Div().withClass("avatar-timeblock-chip")
                    .withChild(new HtmlTag("strong").withInnerText(timeLabel(block.startsAt()) + " " + block.title()))
                    .withChild(small(block.status())));
            }
            body.withChild(blocks);
        }
        Form review = Form.create().withClass("avatar-stack-form avatar-review-form");
        review.withAttribute("hx-post", "/_dashboards/_today/review");
        review.withAttribute("hx-target", "#" + targetId);
        review.withAttribute("hx-swap", "outerHTML");
        review.withChild(TextArea.create("reviewNotes")
            .withRows(2)
            .withPlaceholder("Review notes")
            .withInnerText(safe.dayMap() == null || safe.dayMap().reviewNotes() == null ? "" : safe.dayMap().reviewNotes()));
        review.withChild(new Div().withClass("avatar-row-actions")
            .withChild(Button.create("Restart")
                .withAttribute("type", "button")
                .withAttribute("hx-post", "/_dashboards/_today/restart")
                .withAttribute("hx-target", "#" + targetId)
                .withAttribute("hx-swap", "outerHTML"))
            .withChild(Button.submit("Review")));
        body.withChild(review);
        return body;
    }

    private static Component tasksRoutines(TasksRoutinesView view, String targetId) {
        Div body = new Div().withClass("avatar-widget-body avatar-tasks-routines");
        TasksRoutinesView safe = view == null
            ? new TasksRoutinesView(List.of(), Map.of(), List.of(), List.of())
            : view;
        body.withChild(new Div().withClass("avatar-planner-metrics")
            .withChild(metric("Tasks", Integer.toString(safe.tasks().size())))
            .withChild(metric("Recurring", Long.toString(safe.tasks().stream().filter(AvatarDashboardComponents::recurring).count())))
            .withChild(metric("Reminders", Integer.toString(safe.reminders().size()))));
        body.withChild(tasksRoutineFilters(safe));
        Form create = Form.create().withClass("avatar-stack-form");
        create.withAttribute("hx-post", "/_dashboards/_planner-tasks");
        create.withAttribute("hx-target", "#avatar-edit-container");
        create.withAttribute("hx-swap", "innerHTML");
        create.withChild(TextInput.create("title").withPlaceholder("Task or routine"));
        create.withChild(new Div().withClass("avatar-form-grid")
            .withChild(prioritySelect())
            .withChild(Select.create("recurrenceMode")
                .addOption("NONE", "No repeat", true)
                .addOption("DAILY", "Daily", false)
                .addOption("WEEKLY", "Weekly", false)
                .addOption("MONTHLY", "Monthly", false)));
        create.withChild(Button.submit("Add Task"));
        body.withChild(create);
        Div list = new Div().withClass("avatar-list avatar-list-constrained");
        if (safe.tasks().isEmpty()) {
            list.withChild(empty("No planner tasks yet."));
        }
        for (PlannerTask task : safe.tasks().stream().limit(8).toList()) {
            Div row = new Div().withClass("avatar-list-row avatar-planner-task");
            row.withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText(task.title()))
                .withChild(small(taskMeta(task) + projectLinkText(task))));
            List<PlannerSubtodo> taskSubtodos = safe.subtodos().getOrDefault(task.id(), List.of());
            if (!taskSubtodos.isEmpty()) {
                Div subtodos = new Div().withClass("avatar-subtodo-list");
                for (PlannerSubtodo subtodo : taskSubtodos.stream().limit(3).toList()) {
                    subtodos.withChild(small(subtodo.title() + " / " + subtodo.status()));
                }
                row.withChild(subtodos);
            }
            safe.occurrences().stream()
                .filter(occurrence -> task.id().equals(occurrence.taskId()))
                .findFirst()
                .ifPresent(occurrence -> row.withChild(occurrenceActions(task, occurrence, targetId)));
            list.withChild(row);
        }
        return body.withChild(list);
    }

    private static Component tasksRoutineFilters(TasksRoutinesView view) {
        Form filters = Form.create().withClass("avatar-inline-form avatar-planner-filters");
        filters.withAttribute("hx-get", "/_dashboards/_widgets/tasks-routines/detail");
        filters.withAttribute("hx-target", "#avatar-edit-container");
        filters.withAttribute("hx-swap", "innerHTML");
        filters.withChild(filterSelect("status", safeFilter(view.statusFilter(), "ALL"), List.of(
            Map.entry("ALL", "All statuses"),
            Map.entry("PLANNED", "Planned"),
            Map.entry("ACTIVE", "Active"),
            Map.entry("WAITING", "Waiting"),
            Map.entry("DONE", "Done"),
            Map.entry("CANCELLED", "Cancelled")
        )));
        filters.withChild(filterSelect("range", safeFilter(view.rangeFilter(), "ALL"), List.of(
            Map.entry("ALL", "All ranges"),
            Map.entry("TODAY", "Today"),
            Map.entry("WEEK", "Next 7 days"),
            Map.entry("MONTH", "Next 30 days"),
            Map.entry("OVERDUE", "Overdue")
        )));
        filters.withChild(filterSelect("recurrence", safeFilter(view.recurrenceFilter(), "ALL"), List.of(
            Map.entry("ALL", "All repeats"),
            Map.entry("RECURRING", "Recurring"),
            Map.entry("ONE_OFF", "One-off")
        )));
        filters.withChild(Button.submit("Apply"));
        return filters;
    }

    private static Component filterSelect(String name, String selected, List<Map.Entry<String, String>> options) {
        Select select = Select.create(name);
        for (Map.Entry<String, String> option : options) {
            select.addOption(option.getKey(), option.getValue(), option.getKey().equals(selected));
        }
        return select;
    }

    private static String safeFilter(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Component calendarSchedule(CalendarScheduleView view, String targetId) {
        CalendarScheduleView safe = view == null
            ? new CalendarScheduleView(LocalDate.now(), LocalDate.now().plusDays(30), List.of())
            : view;
        Div body = new Div().withClass("avatar-widget-body avatar-calendar-schedule");
        body.withChild(calendarMonthGrid(safe));
        body.withChild(new Div().withClass("avatar-agenda")
            .withChild(new HtmlTag("strong").withInnerText("Agenda")));
        Div agenda = new Div().withClass("avatar-list avatar-list-constrained");
        if (safe.entries().isEmpty()) {
            agenda.withChild(empty("No scheduled items in range."));
        }
        for (CalendarScheduleView.Entry entry : safe.entries().stream().limit(8).toList()) {
            agenda.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(entry.title()))
                    .withChild(small(entry.kind() + " / " + timeLabel(entry.startsAt()) + " / " + entry.status()))));
        }
        body.withChild(agenda);
        Form block = Form.create().withClass("avatar-stack-form");
        block.withAttribute("hx-post", "/_dashboards/_time-blocks");
        block.withAttribute("hx-target", "#" + targetId);
        block.withAttribute("hx-swap", "outerHTML");
        block.withChild(TextInput.create("title").withPlaceholder("Time block"));
        block.withChild(TextInput.create("startsAt").withAttribute("type", "datetime-local"));
        block.withChild(Button.submit("Block"));
        body.withChild(block);
        Form reminder = Form.create().withClass("avatar-stack-form");
        reminder.withAttribute("hx-post", "/_dashboards/_reminders");
        reminder.withAttribute("hx-target", "#" + targetId);
        reminder.withAttribute("hx-swap", "outerHTML");
        reminder.withChild(TextInput.create("title").withPlaceholder("Reminder"));
        reminder.withChild(TextInput.create("remindAt").withAttribute("type", "datetime-local"));
        reminder.withChild(Button.submit("Add Reminder"));
        return body.withChild(reminder);
    }

    private static Component phaseList(String title, List<PlannerTask> tasks, int limit) {
        Div section = new Div().withClass("avatar-phase-list");
        section.withChild(new HtmlTag("strong").withInnerText(title));
        if (tasks == null || tasks.isEmpty()) {
            return section.withChild(small("none"));
        }
        for (PlannerTask task : tasks.stream().limit(limit).toList()) {
            section.withChild(new Div().withClass("avatar-phase-row")
                .withChild(new HtmlTag("span").withInnerText(task.title()))
                .withChild(small(taskMeta(task))));
        }
        return section;
    }

    private static Component occurrenceActions(PlannerTask task, PlannerOccurrence occurrence, String targetId) {
        Div actions = new Div().withClass("avatar-row-actions avatar-occurrence-actions");
        for (String action : List.of("SKIPPED", "SNOOZED", "RESTARTED")) {
            Form form = Form.create().withClass("avatar-inline-action-form");
            form.withAttribute("hx-post", "/_dashboards/_planner-tasks/" + task.id() + "/occurrences");
            form.withAttribute("hx-target", "#" + targetId);
            form.withAttribute("hx-swap", "outerHTML");
            form.withChild(hiddenInput("occurrenceStart", string(occurrence.occurrenceStart())));
            form.withChild(hiddenInput("action", action));
            form.withChild(Button.submit(actionLabel(action)));
            actions.withChild(form);
        }
        return actions;
    }

    private static Component calendarMonthGrid(CalendarScheduleView view) {
        LocalDate first = view.startDate().withDayOfMonth(1);
        LocalDate cursor = first.minusDays(first.getDayOfWeek().getValue() % 7);
        Div grid = new Div().withClass("avatar-calendar-grid")
            .withAttribute("data-calendar-structure", "month");
        for (String day : List.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")) {
            grid.withChild(new Div().withClass("avatar-calendar-heading").withInnerText(day));
        }
        for (int i = 0; i < 42; i++) {
            LocalDate cellDate = cursor.plusDays(i);
            Div cell = new Div().withClass(cellDate.getMonth() == first.getMonth()
                ? "avatar-calendar-cell"
                : "avatar-calendar-cell avatar-calendar-cell-muted");
            cell.withChild(new HtmlTag("span").withClass("avatar-calendar-day").withInnerText(Integer.toString(cellDate.getDayOfMonth())));
            List<CalendarScheduleView.Entry> cellEntries = view.entries().stream()
                .filter(entry -> entry.startsAt() != null
                    && entry.startsAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(cellDate))
                .limit(3)
                .toList();
            for (CalendarScheduleView.Entry entry : cellEntries) {
                cell.withChild(new Div().withClass("avatar-calendar-pill avatar-calendar-pill-" + entry.kind())
                    .withInnerText(entry.title()));
            }
            grid.withChild(cell);
        }
        return grid;
    }

    private static Component dailyTasks(List<AvatarDailyTask> tasks, String targetId) {
        Div body = new Div().withClass("avatar-widget-body");
        body.withChild(Form.create().withClass("avatar-inline-form")
            .withAttribute("hx-post", "/_dashboards/_daily-tasks")
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML")
            .withChild(TextInput.create("title").withPlaceholder("Add daily task"))
            .withChild(Button.submit("Add Daily")));
        if (tasks == null || tasks.isEmpty()) {
            return body.withChild(empty("No daily tasks for today."));
        }
        List<AvatarDailyTask> visible = tasks.stream().limit(6).toList();
        Div list = new Div().withClass("avatar-list avatar-list-constrained");
        for (AvatarDailyTask task : visible) {
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(task.title()))
                    .withChild(small(task.status() == null ? "planned" : task.status().name().toLowerCase(Locale.ROOT))))
                .withChild(iconPostAction(
                    "check",
                    "Mark daily task done",
                    "Mark daily task " + task.title() + " done",
                    "/_dashboards/_daily-tasks/" + task.id() + "/complete",
                    targetId
                )));
        }
        body.withChild(list);
        if (tasks.size() > visible.size()) {
            body.withChild(small("Showing " + visible.size() + " of " + tasks.size() + " daily tasks."));
        }
        return body;
    }

    private static Component todos(List<AvatarTodo> todos, String targetId) {
        Div body = new Div().withClass("avatar-widget-body");
        Select priority = Select.create("priority")
            .addOption("NORMAL", "Normal", true)
            .addOption("HIGH", "High", false)
            .addOption("URGENT", "Urgent", false)
            .addOption("LOW", "Low", false);
        body.withChild(Form.create().withClass("avatar-inline-form")
            .withAttribute("hx-post", "/_dashboards/_todos")
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML")
            .withChild(TextInput.create("title").withPlaceholder("Add todo"))
            .withChild(priority)
            .withChild(Button.submit("Add Todo")));
        if (todos == null || todos.isEmpty()) {
            return body.withChild(empty("No todos."));
        }
        List<AvatarTodo> visible = newestTodos(todos);
        Div list = new Div().withClass("avatar-list avatar-list-constrained");
        for (AvatarTodo todo : visible) {
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(todo.title()))
                    .withChild(small(todo.priority() == null ? "normal" : todo.priority().name().toLowerCase(Locale.ROOT))))
                .withChild(new Div().withClass("avatar-row-actions")
                    .withChild(iconPostAction(
                        "check",
                        "Mark todo done",
                        "Mark todo " + todo.title() + " done",
                        "/_dashboards/_todos/" + todo.id() + "/complete",
                        targetId
                    ))
                    .withChild(iconDeleteAction(
                        "Delete todo",
                        "Delete todo " + todo.title(),
                        "/_dashboards/_todos/" + todo.id(),
                        targetId
                    ))));
        }
        body.withChild(list);
        long openCount = todos.stream()
            .filter(todo -> todo.status() == null || "OPEN".equals(todo.status().name()))
            .count();
        if (openCount > visible.size()) {
            body.withChild(small("Showing " + visible.size() + " of " + openCount + " open todos."));
        }
        return body;
    }

    private static List<AvatarTodo> newestTodos(List<AvatarTodo> todos) {
        return todos.stream()
            .filter(todo -> todo.status() == null || "OPEN".equals(todo.status().name()))
            .sorted(Comparator.comparing(
                AvatarTodo::createdAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            ).reversed())
            .limit(8)
            .toList();
    }

    private static Component calendar(List<AvatarCalendarItem> items, String targetId) {
        Div body = new Div().withClass("avatar-widget-body");
        body.withChild(Form.create().withClass("avatar-stack-form")
            .withAttribute("hx-post", "/_dashboards/_calendar")
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML")
            .withChild(TextInput.create("title").withPlaceholder("Event title"))
            .withChild(TextInput.create("startsAt").withAttribute("type", "datetime-local"))
            .withChild(Button.submit("Add Event")));
        if (items == null || items.isEmpty()) {
            return body.withChild(empty("No calendar items."));
        }
        Div list = new Div().withClass("avatar-list");
        for (AvatarCalendarItem item : items.stream().limit(5).toList()) {
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(item.title()))
                    .withChild(small(formatInstant(item.startsAt()))))
                .withChild(iconDeleteAction(
                    "Delete calendar item",
                    "Delete calendar item " + item.title(),
                    "/_dashboards/_calendar/" + item.id(),
                    targetId
                )));
        }
        return body.withChild(list);
    }

    private static Component notes(DashboardNotesView view, String targetId, String postUrl, AvatarDashboardData data, AvatarDashboardWidget widget) {
        Div body = new Div().withClass("avatar-widget-body");
        DashboardNotesView safe = view == null
            ? new DashboardNotesView("personal", "Personal notes", null, "", "", "", List.of(), List.of())
            : view;
        body.withChild(new Div().withClass("avatar-source-strip")
            .withChild(new HtmlTag("span").withClass("avatar-chip").withInnerText(sourceModeLabel(safe.sourceMode())))
            .withChild(new HtmlTag("span").withClass("avatar-chip avatar-chip-muted").withInnerText(safe.sourceLabel())));
        if (safe.missingBinding()) {
            body.withChild(empty(safe.missingBindingMessage()));
        }
        if (data != null && data.dashboard() != null && widget != null) {
            Form search = Form.create().withClass("avatar-inline-form");
            search.withAttribute("hx-put", "/dashboards/" + url(data.dashboard().id()) + "/widgets/" + url(widget.widgetId()) + "/settings");
            search.withAttribute("hx-target", "#avatar-edit-container");
            search.withAttribute("hx-swap", "innerHTML");
            search.withChild(hiddenInput("noteSourceMode", safe.sourceMode()));
            search.withChild(hiddenInput("agentId", value(widget.settings(), "agentId")));
            search.withChild(hiddenInput("projectId", value(widget.settings(), "projectId")));
            search.withChild(hiddenInput("workAreaId", value(widget.settings(), "workAreaId")));
            search.withChild(hiddenInput("density", value(widget.settings(), "density")));
            search.withChild(TextInput.create("noteQuery").withValue(safe.query()).withPlaceholder("Search notes or tags"));
            search.withChild(Button.submit("Search"));
            body.withChild(search);
        }
        body.withChild(Form.create().withClass("avatar-stack-form")
            .withAttribute("hx-post", postUrl)
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML")
            .withChild(TextInput.create("title").withPlaceholder("Note title"))
            .withChild(TextInput.create("tags").withPlaceholder("Tags, comma separated"))
            .withChild(TextArea.create("body").withRows(3).withPlaceholder("Capture a note"))
            .withChild(Button.submit("Save Note")));
        if (safe.personalNotes().isEmpty() && safe.fileNotes().isEmpty()) {
            return body.withChild(empty("No notes yet."));
        }
        Div list = new Div().withClass("avatar-list");
        for (AvatarNote note : safe.personalNotes().stream().limit(4).toList()) {
            Div row = new Div().withClass("avatar-note")
                .withChild(new Div().withClass("avatar-list-row-main")
                    .withChild(new HtmlTag("strong").withInnerText(note.title()))
                    .withChild(new Paragraph(snippet(note.body(), 160)))
                    .withChild(tagStrip(note.tags())));
            if (data != null && data.dashboard() != null && widget != null) {
                row.withChild(Button.create("Open")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/dashboards/" + url(data.dashboard().id()) + "/widgets/" + url(widget.widgetId())
                        + "/_notes/" + url(note.id()))
                    .withAttribute("hx-target", "#avatar-edit-container")
                    .withAttribute("hx-swap", "innerHTML"));
            }
            list.withChild(row);
        }
        for (DashboardFileNote note : safe.fileNotes().stream().limit(4).toList()) {
            if (data != null && widget != null) {
                list.withChild(fileNoteRow(data, widget, note));
            }
        }
        return body.withChild(list);
    }

    private static Component fileNoteRow(AvatarDashboardData data, AvatarDashboardWidget widget, DashboardFileNote note) {
        return new Div().withClass("avatar-note avatar-file-note")
            .withChild(new Div().withClass("avatar-list-row-main")
                .withChild(new HtmlTag("strong").withInnerText(note.title()))
                .withChild(small(note.sourceLabel() + " / " + note.path()))
                .withChild(tagStrip(note.tags())))
            .withChild(Button.create(note.markdown() ? "View/Edit" : "Open")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/dashboards/" + url(data.dashboard().id()) + "/widgets/" + url(widget.widgetId())
                    + "/_file-note?source=" + url(note.sourceMode()) + "&path=" + url(note.path()))
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
    }

    private static Component projects(DashboardProjectContextView view, String targetId, boolean contactsMaterialsOnly) {
        Div body = new Div().withClass("avatar-widget-body avatar-project-widget");
        if (view == null || view.missingBinding()) {
            return body.withChild(new Div().withClass("avatar-source-strip")
                    .withChild(new HtmlTag("span").withClass("avatar-chip").withInnerText("project")))
                .withChild(empty(view == null ? "Choose a project in widget settings." : view.missingBindingMessage()));
        }
        body.withChild(new Div().withClass("avatar-source-strip")
            .withChild(new HtmlTag("span").withClass("avatar-chip").withInnerText(view.codeProject() ? "code project" : "household project"))
            .withChild(new HtmlTag("span").withClass("avatar-chip avatar-chip-muted").withInnerText(view.project().name())));
        List<DashboardProjectArtifact> artifacts = contactsMaterialsOnly
            ? view.artifacts().stream()
                .filter(artifact -> "contacts".equals(artifact.type()) || "materials".equals(artifact.type()))
                .toList()
            : view.artifacts();
        Div metrics = new Div().withClass("avatar-planner-metrics");
        metrics.withChild(metric("Artifacts", Integer.toString(artifacts.size())))
            .withChild(metric("Outputs", Integer.toString(view.outputs().size())))
            .withChild(metric("Notes", Integer.toString(view.notes().size())));
        body.withChild(metrics);
        Div list = new Div().withClass("avatar-list avatar-list-constrained");
        for (DashboardProjectArtifact artifact : artifacts) {
            list.withChild(projectArtifactRow(artifact));
        }
        if (artifacts.isEmpty()) {
            list.withChild(empty("No project artifacts yet."));
        }
        body.withChild(list);
        if (!contactsMaterialsOnly && !view.outputs().isEmpty()) {
            body.withChild(new Div().withClass("avatar-section-heading").withInnerText("Recent outputs"));
            Div outputs = new Div().withClass("avatar-list");
            for (RunOutputArtifact output : view.outputs().stream().limit(3).toList()) {
                outputs.withChild(new Div().withClass("avatar-list-row")
                    .withChild(new Div()
                        .withChild(new HtmlTag("strong").withInnerText(output.outputName() == null ? "output" : output.outputName()))
                        .withChild(small(output.artifactType()))));
            }
            body.withChild(outputs);
        }
        return body;
    }

    private static Component projectArtifactRow(DashboardProjectArtifact artifact) {
        Div row = new Div().withClass("avatar-list-row avatar-project-artifact")
            .withAttribute("data-project-artifact", artifact.type());
        Div main = new Div()
            .withChild(new HtmlTag("strong").withInnerText(artifact.title()))
            .withChild(small(artifact.error() == null ? artifact.path() : artifact.error()));
        if (artifact.items().isEmpty()) {
            main.withChild(small("empty"));
        } else {
            for (String item : artifact.items().stream().limit(3).toList()) {
                main.withChild(small(item));
            }
        }
        row.withChild(main);
        row.withChild(new HtmlTag("span").withClass("avatar-chip avatar-chip-muted").withInnerText(artifact.status()));
        return row;
    }

    static Component personalNoteDetailModal(AvatarDashboardData data, AvatarDashboardRowWidget widget, AvatarNote note) {
        Div body = new Div().withClass("avatar-stack-form");
        body.withChild(new Div().withClass("avatar-source-strip")
            .withChild(new HtmlTag("span").withClass("avatar-chip").withInnerText("personal"))
            .withChild(new HtmlTag("span").withClass("avatar-chip avatar-chip-muted").withInnerText("avatar_notes")));
        body.withChild(tagStrip(note.tags()));
        body.withChild(new Markdown(note.body() == null ? "" : note.body()));
        return detailModal("Personal Note", body);
    }

    static Component fileNoteDetailModal(
        AvatarDashboardData data,
        AvatarDashboardRowWidget widget,
        String source,
        WorkAreaExplorerService.FilePreview preview,
        String message
    ) {
        Div body = new Div().withClass("avatar-stack-form avatar-file-note-detail");
        if (message != null && !message.isBlank()) {
            body.withChild(new Div().withClass("orch-status").withInnerText(message));
        }
        body.withChild(new Div().withClass("avatar-source-strip")
            .withChild(new HtmlTag("span").withClass("avatar-chip").withInnerText(sourceModeLabel(source)))
            .withChild(new HtmlTag("span").withClass("avatar-chip avatar-chip-muted").withInnerText(preview.path())));
        if (!preview.text()) {
            body.withChild(empty("Preview unavailable for this file note."));
            return detailModal("File Note", body);
        }
        if ("markdown".equals(preview.kind())) {
            body.withChild(new Markdown(preview.content() == null ? "" : preview.content()));
        }
        Form form = Form.create().withClass("avatar-stack-form");
        form.withAttribute("hx-put", "/dashboards/" + url(data.dashboard().id()) + "/widgets/" + url(widget.id()) + "/_file-note");
        form.withAttribute("hx-target", "#avatar-edit-container");
        form.withAttribute("hx-swap", "innerHTML");
        form.withChild(hiddenInput("source", source));
        form.withChild(hiddenInput("path", preview.path()));
        form.withChild(TextArea.create("content").withRows(14).withInnerText(preview.content() == null ? "" : preview.content()));
        form.withChild(Button.submit("Save File Note"));
        body.withChild(form);
        return detailModal("File Note", body);
    }

    private static Component detailModal(String title, Component body) {
        return new Div().withId("avatar-widget-detail-modal").withClass("avatar-modal")
            .withChild(new Div().withClass("avatar-edit-panel avatar-widget-detail-panel")
                .withChild(new Div().withClass("avatar-edit-header")
                    .withChild(Header.H2(title))
                    .withChild(Button.create("Close")
                        .withAttribute("type", "button")
                        .withAttribute("hx-get", "/dashboards/_modal/clear")
                        .withAttribute("hx-target", "#avatar-edit-container")
                        .withAttribute("hx-swap", "innerHTML")))
                .withChild(body));
    }

    private static Component tagStrip(List<String> tags) {
        Div strip = new Div().withClass("file-entry-tags");
        if (tags == null || tags.isEmpty()) {
            return strip.withChild(new HtmlTag("span").withClass("tag tag-muted").withInnerText("untagged"));
        }
        for (String tag : tags.stream().limit(4).toList()) {
            strip.withChild(new HtmlTag("span").withClass("tag").withInnerText(tag));
        }
        return strip;
    }

    private static String sourceModeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "personal";
        }
        return value.replace('_', ' ');
    }

    static Component workAreaPreview(String workAreaId, WorkAreaExplorerService.FilePreview preview) {
        Div panel = new Div().withId("avatar-workarea-preview").withClass("avatar-workarea-preview");
        panel.withChild(new Div().withClass("avatar-output-preview-header")
            .withChild(Header.H2(fileName(preview.path())))
            .withChild(new Div().withClass("avatar-row-actions")
                .withChild(new HtmlTag("a")
                    .withClass("orch-primary")
                    .withAttribute("href", "/api/work-areas/" + workAreaId + "/files/download?path=" + url(preview.path()))
                    .withInnerText("Download"))
                .withChild(Button.create("Edit")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/avatar/_work-areas/" + workAreaId + "/edit?path=" + url(preview.path()))
                    .withAttribute("hx-target", "#avatar-workarea-preview")
                    .withAttribute("hx-swap", "outerHTML"))));
        panel.withChild(small(preview.size() + " bytes"));
        if ("image".equals(preview.kind())) {
            return panel.withChild(new HtmlTag("img")
                .withClass("avatar-workarea-image")
                .withAttribute("src", "/api/work-areas/" + workAreaId + "/files/view?path=" + url(preview.path()))
                .withAttribute("alt", fileName(preview.path())));
        }
        if (!preview.text()) {
            return panel.withChild(empty("Preview unavailable for this file type or size."));
        }
        if ("markdown".equals(preview.kind())) {
            panel.withChild(new Div().withClass("avatar-workarea-tabs")
                .withChild(new HtmlTag("span").withClass("avatar-tab-active").withInnerText("Rendered"))
                .withChild(Button.create("Edit")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/avatar/_work-areas/" + workAreaId + "/edit?path=" + url(preview.path()))
                    .withAttribute("hx-target", "#avatar-workarea-preview")
                    .withAttribute("hx-swap", "outerHTML")));
            return panel.withChild(new Markdown(preview.content() == null ? "" : preview.content()));
        }
        return panel.withChild(new HtmlTag("pre").withClass("avatar-workarea-text-preview")
            .withInnerText(preview.content() == null ? "" : preview.content()));
    }

    static Component workAreaTextEditor(String workAreaId, WorkAreaExplorerService.FilePreview preview, boolean markdownEdit) {
        if (!preview.text()) {
            return workAreaPreview(workAreaId, preview);
        }
        Form form = Form.create().withClass("avatar-stack-form avatar-workarea-preview")
            .withId("avatar-workarea-preview");
        form.withAttribute("hx-put", "/avatar/_work-areas/" + workAreaId + "/text?path=" + url(preview.path()));
        form.withAttribute("hx-target", "#avatar-edit-container");
        form.withAttribute("hx-swap", "innerHTML");
        form.withChild(new Div().withClass("avatar-output-preview-header")
            .withChild(Header.H2(fileName(preview.path())))
            .withChild(new Div().withClass("avatar-row-actions")
                .withChild("markdown".equals(preview.kind())
                    ? Button.create("Rendered")
                        .withAttribute("type", "button")
                        .withAttribute("hx-get", "/avatar/_work-areas/" + workAreaId + "/preview?path=" + url(preview.path()))
                        .withAttribute("hx-target", "#avatar-workarea-preview")
                        .withAttribute("hx-swap", "outerHTML")
                    : new HtmlTag("span"))
                .withChild(Button.submit("Save File"))));
        form.withChild(TextArea.create("content").withRows(14)
            .withInnerText(preview.content() == null ? "" : preview.content()));
        return form;
    }

    private static Component files(List<WorkArea> workAreas) {
        Div body = new Div().withClass("avatar-widget-body");
        if (workAreas == null || workAreas.isEmpty()) {
            return body.withChild(empty("No agent Work Areas are available."));
        }
        body.withClass("avatar-workarea-browser avatar-workarea-browser-widget");
        Div layout = new Div().withClass("avatar-workarea-browser-grid");
        Div list = new Div().withClass("avatar-list");
        for (WorkArea workArea : workAreas.stream().limit(8).toList()) {
            list.withChild(new Div().withClass("avatar-list-row avatar-workarea-entry")
                .withAttribute("data-workarea-id", workArea.id())
                .withAttribute("role", "button")
                .withAttribute("tabindex", "0")
                .withAttribute("hx-get", "/avatar/_work-areas/" + workArea.id() + "/explorer")
                .withAttribute("hx-trigger", "click, keyup[key=='Enter']")
                .withAttribute("hx-target", "#avatar-workarea-surface")
                .withAttribute("hx-swap", "innerHTML")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(workArea.displayName()))
                    .withChild(small(workAreaOwnerLabel(workArea)))));
        }
        return body.withChild(layout
            .withChild(list)
            .withChild(new Div()
                .withId("avatar-workarea-surface")
                .withClass("avatar-workarea-surface")
                .withChild(workAreaSurfacePlaceholder())));
    }

    private static String workAreaOwnerLabel(WorkArea workArea) {
        if (workArea == null) {
            return "workspace";
        }
        String owner = workArea.ownerId() == null || workArea.ownerId().isBlank()
            ? "unknown"
            : workArea.ownerId();
        String kind = workArea.ownerType() == null ? "owner" : workArea.ownerType().name().toLowerCase(Locale.ROOT);
        return kind + " " + owner;
    }

    static Component workAreaSurfacePlaceholder() {
        return new Div().withClass("avatar-workarea-surface-empty")
            .withChild(Header.H3("Select a Work Area"))
            .withChild(small("Click a Work Area card to open the confined explorer."));
    }

    static Component workAreaInspector(
        String workAreaId,
        String path,
        List<io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileLabelAssignment> labels
    ) {
        boolean note = labels != null && labels.stream().anyMatch(label -> "note".equals(label.label().slug()));
        Div panel = new Div().withId("avatar-workarea-inspector").withClass("file-explorer-inspector-pane")
            .withChild(Header.H4("Labels"))
            .withChild(new Paragraph(path == null ? "." : path))
            .withChild(new Div().withClass("file-entry-tags")
                .withChild(new Div().withClass(note ? "tag" : "tag tag-muted").withInnerText(note ? "note" : "not noted")));
        panel.withChild(Button.create(note ? "Remove Note" : "Mark Note").small()
            .withAttribute(note ? "hx-delete" : "hx-post", "/avatar/_work-areas/" + workAreaId + "/labels/note?path=" + url(path))
            .withAttribute("hx-target", "#avatar-workarea-inspector")
            .withAttribute("hx-swap", "outerHTML"));
        return panel;
    }

    static Component workAreaActionModal(
        String workAreaId,
        String action,
        String path,
        WorkAreaExplorerService.DeletePreflight deletePreflight
    ) {
        Div body = new Div().withClass("avatar-stack-form");
        String title = switch (action) {
            case "create-folder" -> "Create Folder";
            case "create-text" -> "Create Text File";
            case "create-markdown" -> "Create Markdown File";
            case "rename" -> "Rename";
            case "copy" -> "Copy";
            case "move" -> "Move";
            case "delete", "delete-recursive" -> "Delete";
            default -> "File Action";
        };
        if ("delete-recursive".equals(action)) {
            body.withChild(new Paragraph("Recursive delete will remove the directory and its contents."));
            body.withChild(Button.create("Confirm Recursive Delete")
                .withAttribute("hx-post", "/avatar/_work-areas/" + workAreaId + "/files/delete?path=" + url(path)
                    + "&step=DIRECTORY_RECURSIVE_CONFIRM")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
        } else if ("delete".equals(action)) {
            body.withChild(new Paragraph("Delete " + fileName(path) + "?"));
            if (deletePreflight != null && deletePreflight.directory() && deletePreflight.requiredStep() == WorkAreaExplorerService.DeleteStep.DIRECTORY_RECURSIVE_CONFIRM) {
                body.withChild(new Paragraph("This is a directory. First confirm that you want to delete it, then confirm recursive deletion to remove its contents."));
                body.withChild(Button.create("Confirm Delete")
                    .withAttribute("hx-get", "/avatar/_work-areas/" + workAreaId + "/modal/delete-recursive?path=" + url(path))
                    .withAttribute("hx-target", "#avatar-workarea-modal")
                    .withAttribute("hx-swap", "innerHTML"));
            } else {
                body.withChild(Button.create("Confirm Delete")
                    .withAttribute("hx-post", "/avatar/_work-areas/" + workAreaId + "/files/delete?path=" + url(path) + "&step=INTENT")
                    .withAttribute("hx-target", "#avatar-edit-container")
                    .withAttribute("hx-swap", "innerHTML"));
            }
        } else if (action.startsWith("create-")) {
            String kind = "create-markdown".equals(action) ? "markdown" : "text";
            Form form = Form.create().withClass("avatar-stack-form");
            form.withAttribute("hx-post", "create-folder".equals(action)
                ? "/avatar/_work-areas/" + workAreaId + "/directories"
                : "/avatar/_work-areas/" + workAreaId + "/text?kind=" + kind);
            form.withAttribute("hx-target", "#avatar-edit-container");
            form.withAttribute("hx-swap", "innerHTML");
            form.withChild(hidden("path", path));
            form.withChild(TextInput.create("name").withPlaceholder("Name"));
            form.withChild(Button.submit("Create"));
            body.withChild(form);
        } else if ("rename".equals(action)) {
            body.withChild(simpleFileActionForm(workAreaId, "/files/rename", path, "New name", "Rename"));
        } else if ("copy".equals(action) || "move".equals(action)) {
            Form form = Form.create().withClass("avatar-stack-form");
            form.withAttribute("hx-post", "/avatar/_work-areas/" + workAreaId + "/files/action/" + action);
            form.withAttribute("hx-target", "#avatar-edit-container");
            form.withAttribute("hx-swap", "innerHTML");
            form.withChild(hidden("path", path));
            form.withChild(TextInput.create("destination").withPlaceholder("Destination directory"));
            form.withChild(TextInput.create("name").withPlaceholder("Optional new name"));
            form.withChild(Button.submit("copy".equals(action) ? "Copy" : "Move"));
            body.withChild(form);
        }
        return Modal.create()
            .withModalId("avatar_workarea_action_modal")
            .withTitle(title)
            .withBody(body);
    }

    static Component workAreaUnavailableModal(String action, String path, String message) {
        return Modal.create()
            .withModalId("avatar_workarea_action_modal")
            .withTitle("Action unavailable")
            .withBody(new Div().withClass("avatar-stack-form")
                .withChild(new Paragraph(fileName(path)))
                .withChild(new Paragraph(message == null ? "This action is not available for this path." : message)));
    }

    private static Component simpleFileActionForm(String workAreaId, String route, String path, String placeholder, String label) {
        return Form.create().withClass("avatar-stack-form")
            .withAttribute("hx-post", "/avatar/_work-areas/" + workAreaId + route)
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML")
            .withChild(hidden("path", path))
            .withChild(TextInput.create("name").withPlaceholder(placeholder))
            .withChild(Button.submit(label));
    }

    private static Component hidden(String name, String value) {
        return new HtmlTag("input", true)
            .withAttribute("type", "hidden")
            .withAttribute("name", name)
            .withAttribute("value", value == null ? "." : value);
    }

    private static Component workAreaCreateDirectoryForm(WorkAreaExplorerService.DirectoryListing listing) {
        Form form = Form.create().withClass("avatar-inline-form");
        form.withAttribute("hx-post", "/avatar/_work-areas/" + listing.workArea().id() + "/directories");
        form.withAttribute("hx-target", "#avatar-edit-container");
        form.withAttribute("hx-swap", "innerHTML");
        form.withChild(new HtmlTag("input", true)
            .withAttribute("type", "hidden")
            .withAttribute("name", "path")
            .withAttribute("value", listing.path().isBlank() ? "." : listing.path()));
        form.withChild(TextInput.create("name").withPlaceholder("New directory"));
        form.withChild(Button.submit("Create"));
        return form;
    }

    private static Component workAreaCreateTextFileForm(WorkAreaExplorerService.DirectoryListing listing) {
        Form form = Form.create().withClass("avatar-inline-form");
        form.withAttribute("hx-post", "/avatar/_work-areas/" + listing.workArea().id() + "/text");
        form.withAttribute("hx-target", "#avatar-workarea-preview");
        form.withAttribute("hx-swap", "outerHTML");
        form.withChild(new HtmlTag("input", true)
            .withAttribute("type", "hidden")
            .withAttribute("name", "path")
            .withAttribute("value", listing.path().isBlank() ? "." : listing.path()));
        form.withChild(TextInput.create("name").withPlaceholder("New text file"));
        form.withChild(Button.submit("Create File"));
        return form;
    }

    private static Component workAreaEntry(String workAreaId, WorkAreaExplorerService.Entry entry) {
        Div actions = new Div().withClass("avatar-row-actions");
        if (entry.directory()) {
            actions.withChild(Button.create("Open")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_work-areas/" + workAreaId + "/explorer?path=" + url(entry.path()))
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
            actions.withChild(Button.create("Mark")
                .withAttribute("type", "button")
                .withAttribute("hx-post", "/avatar/_work-areas/" + workAreaId + "/mark?path=" + url(entry.path()))
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
        } else if (entry.regularFile()) {
            actions.withChild(Button.create("Preview")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_work-areas/" + workAreaId + "/preview?path=" + url(entry.path()))
                .withAttribute("hx-target", "#avatar-workarea-preview")
                .withAttribute("hx-swap", "outerHTML"));
        }
        actions.withChild(Button.create("Delete")
            .withAttribute("type", "button")
            .withAttribute("hx-delete", "/avatar/_work-areas/" + workAreaId + "/files?path=" + url(entry.path())
                + "&confirm=" + url(entry.name()))
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML")
            .withAttribute("hx-confirm", "Delete " + entry.name() + "?"));
        return new Div().withClass("avatar-list-row")
            .withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText(entry.name()))
                .withChild(small(entry.directory() ? "directory" : entry.size() + " bytes")))
            .withChild(actions);
    }

    private static Component outputs(List<RunOutputArtifact> outputs) {
        Div body = new Div().withClass("avatar-widget-body");
        if (outputs == null || outputs.isEmpty()) {
            return body.withChild(empty("No recent outputs."));
        }
        Div list = new Div().withClass("avatar-list");
        for (RunOutputArtifact output : outputs.stream().limit(8).toList()) {
            boolean viewable = "text".equals(output.artifactType())
                || "json".equals(output.artifactType())
                || "user_message".equals(output.artifactType());
            Component action = viewable
                ? new HtmlTag("button")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/_dashboards/_outputs/" + output.id())
                    .withAttribute("hx-target", "#avatar-output-preview")
                    .withAttribute("hx-swap", "outerHTML")
                    .withInnerText("Preview")
                : new HtmlTag("a")
                    .withClass("orch-primary")
                    .withAttribute("href", "/api/outputs/" + output.id() + "/download")
                    .withInnerText("Download");
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(output.outputName() == null ? "output" : output.outputName()))
                    .withChild(small(output.artifactType())))
                .withChild(action));
        }
        return body.withChild(list);
    }

    private static Component system(List<AgentProfile> agents, List<JobDefinition> jobs, List<WorkAssignment> assignments) {
        long activeAgents = agents == null ? 0 : agents.stream()
            .filter(agent -> agent.status() != null && "ACTIVE".equals(agent.status().name()))
            .count();
        long activeJobs = jobs == null ? 0 : jobs.stream()
            .filter(job -> job.status() != null && !"COMPLETED".equals(job.status()) && !"CANCELLED".equals(job.status()))
            .count();
        long activeAssignments = assignments == null ? 0 : assignments.stream()
            .filter(assignment -> assignment.status() == null || !assignment.status().isTerminal())
            .count();
        return new Div().withClass("avatar-widget-body avatar-metrics")
            .withChild(metric("Active agents", Long.toString(activeAgents)))
            .withChild(metric("Open jobs", Long.toString(activeJobs)))
            .withChild(metric("Queued work", Long.toString(activeAssignments)));
    }

    private static Component alerts(List<AvatarEvent> events, List<InboxMessage> inbox, String targetId) {
        Div body = new Div().withClass("avatar-widget-body");
        Div list = new Div().withClass("avatar-list");
        int count = 0;
        if (inbox != null) {
            for (InboxMessage message : inbox.stream().filter(m -> m.respondedAt() == null).limit(4).toList()) {
                count++;
                list.withChild(new Div().withClass("avatar-alert")
                    .withChild(new HtmlTag("strong").withInnerText(message.messageType().name()))
                    .withChild(new Paragraph(snippet(message.body(), 180))));
            }
        }
        if (events != null) {
            java.util.Set<String> dismissed = events.stream()
                .filter(event -> "alert.dismissed".equals(event.eventType()))
                .map(event -> event.payload() == null ? null : event.payload().get("eventId"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(java.util.stream.Collectors.toSet());
            for (AvatarEvent event : events.stream()
                .filter(AvatarDashboardComponents::alertEvent)
                .filter(event -> !dismissed.contains(event.id()))
                .limit(4)
                .toList()) {
                count++;
                list.withChild(new Div().withClass("avatar-alert")
                    .withChild(new HtmlTag("strong").withInnerText(event.eventType()))
                    .withChild(small(formatInstant(event.occurredAt())))
                    .withChild(action("Dismiss", "/_dashboards/_alerts/" + event.id() + "/dismiss", targetId)));
            }
        }
        if (count == 0) {
            return body.withChild(empty("No alerts."));
        }
        return body.withChild(list);
    }

    private static Component recentWork(List<JobDefinition> jobs, List<WorkAssignment> assignments, List<RunOutputArtifact> outputs) {
        Div body = new Div().withClass("avatar-widget-body");
        Div list = new Div().withClass("avatar-list");
        int count = 0;
        if (assignments != null) {
            for (WorkAssignment assignment : assignments.stream().limit(4).toList()) {
                count++;
                OrchestrationStatus status = assignment.status();
                list.withChild(new Div().withClass("avatar-list-row")
                    .withChild(new Div()
                        .withChild(new HtmlTag("strong").withInnerText(assignment.assignmentType() == null ? "Assignment" : assignment.assignmentType().name()))
                        .withChild(small(status == null ? "unknown" : status.name()))));
            }
        }
        if (jobs != null) {
            for (JobDefinition job : jobs.stream().limit(3).toList()) {
                count++;
                list.withChild(new Div().withClass("avatar-list-row")
                    .withChild(new Div()
                        .withChild(new HtmlTag("strong").withInnerText(job.title() == null ? "Job" : job.title()))
                        .withChild(small(job.status()))));
            }
        }
        if (outputs != null) {
            for (RunOutputArtifact output : outputs.stream().limit(3).toList()) {
                count++;
                list.withChild(new Div().withClass("avatar-list-row")
                    .withChild(new Div()
                        .withChild(new HtmlTag("strong").withInnerText(output.outputName() == null ? "Output" : output.outputName()))
                        .withChild(small(output.createdAt() == null ? output.artifactType() : formatInstant(output.createdAt())))));
            }
        }
        if (count == 0) {
            return body.withChild(empty("No recent work."));
        }
        return body.withChild(list);
    }

    private static Component editRow(AvatarDashboardRow row, int index, int rowCount) {
        Div frame = new Div()
            .withClass("avatar-edit-row")
            .withAttribute("data-avatar-row-id", row.id());
        frame.withChild(new Div().withClass("avatar-edit-row-header")
            .withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText("Row " + (index + 1)))
                .withChild(small(row.widgets().stream().mapToInt(AvatarDashboardRowWidget::columnWidth).sum() + "/12 columns used")))
            .withChild(new Div().withClass("avatar-row-actions")
                .withChild(rowMoveButton(row.id(), "up", index == 0))
                .withChild(rowMoveButton(row.id(), "down", index >= rowCount - 1))
                .withChild(Button.create("Add Widget")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/_dashboards/_layout/rows/" + row.id() + "/catalog")
                    .withAttribute("hx-target", "#avatar-edit-container")
                    .withAttribute("hx-swap", "innerHTML"))));
        Div widgets = new Div().withClass("avatar-edit-widgets");
        if (row.widgets().isEmpty()) {
            widgets.withChild(empty("No widgets in this row."));
        }
        for (AvatarDashboardRowWidget widget : row.widgets()) {
            widgets.withChild(editWidget(widget));
        }
        return frame.withChild(widgets);
    }

    private static Component editWidget(AvatarDashboardRowWidget widget) {
        DashboardWidgetDefinition definition = definition(widget.widgetKey());
        HtmlTag widthForm = Form.create().withClass("avatar-widget-width-form")
            .withAttribute("hx-put", "/_dashboards/_layout/widgets/" + widget.id() + "/width")
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML");
        widthForm.withChild(widthSelect("columnWidth", widget.columnWidth(), true));
        widthForm.withChild(Button.submit("Set"));
        return new Div().withClass("avatar-edit-widget")
            .withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText(definition.title()))
                .withChild(small(widget.columnWidth() + "/12")))
            .withChild(widthForm)
            .withChild(new Div().withClass("avatar-row-actions")
                .withChild(widgetMoveButton(widget.id(), "left"))
                .withChild(widgetMoveButton(widget.id(), "right"))
                .withChild(widgetMoveButton(widget.id(), "up"))
                .withChild(widgetMoveButton(widget.id(), "down"))
                .withChild(Button.create("Remove")
                    .withAttribute("type", "button")
                    .withAttribute("hx-delete", "/_dashboards/_layout/widgets/" + widget.id())
                    .withAttribute("hx-target", "#avatar-edit-container")
                    .withAttribute("hx-swap", "innerHTML")
                    .withAttribute("hx-confirm", "Remove this widget?")));
    }

    private static Component addModuleSection(AvatarDashboardRow row, int index, int rowCount) {
        return new Div().withClass("add-module-section avatar-add-module-section")
            .withChild(Button.create("+ Add Widget")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/_dashboards/_layout/rows/" + row.id() + "/catalog")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
    }

    private static Component emptyRowInsert(AvatarDashboardRow row) {
        return new Div().withClass("avatar-empty-row-insert")
            .withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText("Empty row"))
                .withChild(small("Add a widget or remove this row.")))
            .withChild(Button.create("+ Add Widget")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/_dashboards/_layout/rows/" + row.id() + "/catalog")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
    }

    private static Component rowDecoration(AvatarDashboardRow row, int index, int rowCount) {
        String usage = row.widgets().isEmpty()
            ? "Empty row"
            : "Row " + (index + 1) + " · " + row.widgets().stream().mapToInt(AvatarDashboardRowWidget::columnWidth).sum() + "/12";
        return new Div().withClass("avatar-row-decoration")
            .withChild(new HtmlTag("span").withClass("avatar-row-decoration-label").withInnerText(usage))
            .withChild(new Div().withClass("avatar-row-decoration-actions")
            .withChild(rowMoveButton(row.id(), "up", index == 0))
            .withChild(rowMoveButton(row.id(), "down", index >= rowCount - 1))
            .withChild(rowDeleteButton(row.id(), row.widgets().isEmpty())));
    }

    private static Component insertRowSection(AvatarDashboardRow row) {
        return new Div().withClass("insert-row-section avatar-insert-row-section")
            .withChild(Button.create("+ Insert Row Below")
                .withAttribute("type", "button")
                .withAttribute("hx-post", "/_dashboards/_layout/rows/" + row.id() + "/insert-after")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
    }

    private static Component widgetCornerControls(
        String dashboardId,
        String widgetInstanceId,
        String widgetType,
        AvatarDashboardRowWidget layoutWidget,
        boolean editMode
    ) {
        Div controls = new Div().withClass(editMode
            ? "avatar-widget-corner-controls avatar-widget-corner-controls-editing"
            : "avatar-widget-corner-controls");
        controls.withChild(detailButton(dashboardId, widgetInstanceId, widgetType, layoutWidget));
        if (layoutWidget != null) {
            controls.withChild(settingsButton(dashboardId, layoutWidget.id(), widgetType));
        }
        if (!editMode || layoutWidget == null) {
            return controls;
        }
        controls.withChild(widgetMoveButton(layoutWidget.id(), "left"));
        controls.withChild(widgetMoveButton(layoutWidget.id(), "right"));
        controls.withChild(widgetMoveButton(layoutWidget.id(), "up"));
        controls.withChild(widgetMoveButton(layoutWidget.id(), "down"));

        controls.withChild(iconButton("width", "Choose widget width", "Choose " + definition(widgetType).title() + " width")
            .withAttribute("data-avatar-width-picker-trigger", "true")
            .withAttribute("data-avatar-widget-id", layoutWidget.id())
            .withAttribute("hx-get", "/_dashboards/_layout/widgets/" + layoutWidget.id() + "/width-picker")
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML"));
        controls.withChild(iconButton("trash", "Remove widget", "Remove " + definition(widgetType).title())
            .withAttribute("hx-delete", "/_dashboards/_layout/widgets/" + layoutWidget.id())
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML")
            .withAttribute("hx-confirm", "Remove this widget?"));
        return controls;
    }

    private static Component rowMoveButton(String rowId, String direction, boolean disabled) {
        HtmlTag button = iconButton(
            direction.equals("up") ? "up" : "down",
            "Move row " + direction,
            "Move row " + direction
        );
        button.withAttribute("hx-post", "/_dashboards/_layout/rows/" + rowId + "/move?direction=" + direction);
        button.withAttribute("hx-target", "#avatar-edit-container");
        button.withAttribute("hx-swap", "innerHTML");
        if (disabled) {
            button.withAttribute("disabled", "disabled");
        }
        return button;
    }

    private static Component rowDeleteButton(String rowId, boolean enabled) {
        HtmlTag button = iconButton("trash", "Delete empty row", "Delete empty row");
        button.withAttribute("hx-delete", "/_dashboards/_layout/rows/" + rowId);
        button.withAttribute("hx-target", "#avatar-edit-container");
        button.withAttribute("hx-swap", "innerHTML");
        button.withAttribute("hx-confirm", "Delete this empty row?");
        if (!enabled) {
            button.withAttribute("disabled", "disabled");
            button.withAttribute("title", "Remove widgets before deleting this row");
        }
        return button;
    }

    private static Component widgetMoveButton(String widgetId, String direction) {
        return iconButton(direction, "Move widget " + direction, "Move widget " + direction)
            .withAttribute("hx-post", "/_dashboards/_layout/widgets/" + widgetId + "/move?direction=" + direction)
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML");
    }

    private static Component widthSelect(String name, int selected, boolean compact) {
        Select select = Select.create(name)
            .addOption("3", compact ? "3" : "Quarter (3/12)", selected == 3)
            .addOption("4", compact ? "4" : "Third (4/12)", selected == 4)
            .addOption("6", compact ? "6" : "Half (6/12)", selected == 6)
            .addOption("8", compact ? "8" : "Two Thirds (8/12)", selected == 8)
            .addOption("12", compact ? "12" : "Full (12/12)", selected == 12);
        return select;
    }

    private static Component detailButton(String dashboardId, String instanceId, String type, AvatarDashboardRowWidget layoutWidget) {
        HtmlTag button = iconButton("open", "Open widget detail", "Open " + definition(type).title() + " detail")
            .withAttribute("data-avatar-detail-trigger", instanceId);
        if (layoutWidget == null) {
            button.withAttribute("hx-get", "/_dashboards/_widgets/" + type + "/detail");
        } else {
            button.withAttribute("hx-get", "/dashboards/" + url(dashboardId) + "/widgets/" + url(layoutWidget.id()) + "/detail");
        }
        return button
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML");
    }

    private static Component settingsButton(String dashboardId, String instanceId, String type) {
        return iconButton("settings", "Open widget settings", "Open " + definition(type).title() + " settings")
            .withAttribute("data-avatar-settings-trigger", instanceId)
            .withAttribute("hx-get", "/dashboards/" + url(dashboardId) + "/widgets/" + url(instanceId) + "/settings")
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML");
    }

    private static HtmlTag iconPostAction(String icon, String title, String ariaLabel, String path, String targetId) {
        return iconButton(icon, title, ariaLabel)
            .withAttribute("hx-post", path)
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML");
    }

    private static HtmlTag iconDeleteAction(String title, String ariaLabel, String path, String targetId) {
        return iconButton("trash", title, ariaLabel)
            .withAttribute("hx-delete", path)
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML");
    }

    private static HtmlTag iconButton(String icon, String title, String ariaLabel) {
        return new HtmlTag("button")
            .withClass("avatar-icon-button avatar-control-button")
            .withAttribute("type", "button")
            .withAttribute("title", title)
            .withAttribute("aria-label", ariaLabel)
            .withUnsafeHtml(iconSvg(icon));
    }

    private static HtmlTag iconLink(String icon, String ariaLabel, String href) {
        return new HtmlTag("a")
            .withClass("avatar-icon-link avatar-control-button")
            .withAttribute("href", href)
            .withAttribute("title", ariaLabel)
            .withAttribute("aria-label", ariaLabel)
            .withUnsafeHtml(iconSvg(icon));
    }

    private static String iconSvg(String icon) {
        return switch (icon) {
            case "check" -> strokeIcon("""
                <path d="M5 12.5l4.2 4.2L19 7.5"/>
                """);
            case "trash" -> strokeIcon("""
                <path d="M4 7h16"/>
                <path d="M9 7V5.5a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 5.5V7"/>
                <path d="M7.5 7l.7 11.2A1.8 1.8 0 0 0 10 20h4a1.8 1.8 0 0 0 1.8-1.8L16.5 7"/>
                <path d="M10 10.5v6"/>
                <path d="M14 10.5v6"/>
                """);
            case "refresh" -> strokeIcon("""
                <path d="M20 11a8 8 0 0 0-14.5-3.8"/>
                <path d="M4 4v4h4"/>
                <path d="M4 13a8 8 0 0 0 14.5 3.8"/>
                <path d="M20 20v-4h-4"/>
                """);
            case "left" -> strokeIcon("""
                <path d="M19 12H5"/>
                <path d="M11 6l-6 6 6 6"/>
                """);
            case "right" -> strokeIcon("""
                <path d="M5 12h14"/>
                <path d="M13 6l6 6-6 6"/>
                """);
            case "up" -> strokeIcon("""
                <path d="M12 19V5"/>
                <path d="M6 11l6-6 6 6"/>
                """);
            case "down" -> strokeIcon("""
                <path d="M12 5v14"/>
                <path d="M18 13l-6 6-6-6"/>
                """);
            case "width" -> strokeIcon("""
                <path d="M4 6v12"/>
                <path d="M20 6v12"/>
                <path d="M9 12H5"/>
                <path d="M7 10l-2 2 2 2"/>
                <path d="M15 12h4"/>
                <path d="M17 10l2 2-2 2"/>
                <path d="M10 8h4"/>
                <path d="M10 16h4"/>
                """);
            case "settings" -> strokeIcon("""
                <path d="M4 7h8"/>
                <path d="M16 7h4"/>
                <circle cx="14" cy="7" r="2"/>
                <path d="M4 17h4"/>
                <path d="M12 17h8"/>
                <circle cx="10" cy="17" r="2"/>
                """);
            case "close" -> strokeIcon("""
                <path d="M6 6l12 12"/>
                <path d="M18 6L6 18"/>
                """);
            default -> strokeIcon("""
                <circle cx="12" cy="12" r="9"/>
                <path d="M12 8v8"/>
                <path d="M8 12h8"/>
                """);
        };
    }

    private static Component hiddenInput(String name, String value) {
        return new HtmlTag("input", true)
            .withAttribute("type", "hidden")
            .withAttribute("name", name)
            .withAttribute("value", value);
    }

    private static void allowHtmxErrorSwap(HtmlTag tag) {
        tag.withAttribute(
            "hx-on::before-swap",
            "if (event.detail.xhr.status === 400) { event.detail.shouldSwap = true; event.detail.isError = false; }"
        );
    }

    private static Div editContainer() {
        Div container = new Div().withId("avatar-edit-container");
        allowHtmxErrorSwap(container);
        return container;
    }

    private static int maxWidthForWidget(List<AvatarDashboardRow> rows, AvatarDashboardRowWidget widget) {
        if (rows == null) {
            return 12;
        }
        return rows.stream()
            .filter(row -> row.id().equals(widget.rowId()))
            .findFirst()
            .map(row -> {
                int usedWithoutWidget = row.widgets().stream()
                    .filter(item -> !item.id().equals(widget.id()))
                    .mapToInt(AvatarDashboardRowWidget::columnWidth)
                    .sum();
                return Math.max(1, 12 - usedWithoutWidget);
            })
            .orElse(12);
    }

    private static String strokeIcon(String paths) {
        return """
            <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none"
                 xmlns="http://www.w3.org/2000/svg" stroke="currentColor"
                 stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
            """ + paths + "</svg>";
    }

    private static Component action(String label, String path, String targetId) {
        return new HtmlTag("button")
            .withAttribute("type", "button")
            .withAttribute("hx-post", path)
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML")
            .withInnerText(label);
    }

    private static Component deleteAction(String label, String path, String targetId) {
        return new HtmlTag("button")
            .withAttribute("type", "button")
            .withAttribute("hx-delete", path)
            .withAttribute("hx-target", "#" + targetId)
            .withAttribute("hx-swap", "outerHTML")
            .withInnerText(label);
    }

    private static Component metric(String label, String value) {
        return new Div().withClass("avatar-metric")
            .withChild(new HtmlTag("span").withClass("avatar-metric-value").withInnerText(value))
            .withChild(new HtmlTag("span").withClass("avatar-metric-label").withInnerText(label));
    }

    private static Component metaLine(String label, String value) {
        return new Div().withClass("avatar-meta-line")
            .withChild(new HtmlTag("span").withInnerText(label))
            .withChild(new HtmlTag("strong").withInnerText(value == null || value.isBlank() ? "none" : value));
    }

    private static Component empty(String text) {
        return new Div().withClass("avatar-empty").withInnerText(text);
    }

    private static Component small(String text) {
        return new HtmlTag("small").withInnerText(text == null || text.isBlank() ? "none" : text);
    }

    private static Component moduleScript(String src) {
        return new HtmlTag("script").withAttribute("type", "module").withAttribute("src", src);
    }

    private static boolean alertEvent(AvatarEvent event) {
        return event.eventType() != null
            && !"alert.dismissed".equals(event.eventType())
            && event.eventType().contains("alert");
    }

    private static String snippet(String value, int max) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip();
        return normalized.length() <= max ? normalized : normalized.substring(0, max - 1) + "...";
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) {
            return "unscheduled";
        }
        return DATE.format(instant.atZone(ZoneId.systemDefault()));
    }

    private static String string(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "file";
        }
        String normalized = path.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
    }

    private static String parentPath(String path) {
        if (path == null || path.isBlank() || ".".equals(path)) {
            return ".";
        }
        String normalized = path.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash <= 0 ? "." : normalized.substring(0, lastSlash);
    }

    private static String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String size(AvatarDashboardWidget widget) {
        if (widget == null || widget.size() == null || widget.size().isBlank()) {
            return "standard";
        }
        return widget.size();
    }

    private static String widgetType(AvatarDashboardWidget widget) {
        if (widget != null && widget.settings() != null) {
            Object type = widget.settings().get("widgetType");
            if (type instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return widget == null ? "" : widget.widgetId();
    }

    static String rootId(String widgetKey) {
        return "avatar-widget-" + widgetKey;
    }

    static boolean isKnownWidget(String widgetKey) {
        return WIDGET_REGISTRY.contains(widgetKey);
    }

    static DashboardWidgetDefinition definition(String widgetKey) {
        return WIDGET_REGISTRY.find(widgetKey)
            .orElseGet(() -> new DashboardWidgetDefinition(
                widgetKey,
                widgetKey,
                "Dashboard widget.",
                "compatibility",
                "unknown",
                4,
                List.of(3, 4, 6, 8, 12),
                WidgetInstancePolicy.MULTI_INSTANCE,
                io.mindspice.magenta2.avatar.dashboard.WidgetBindingMode.NONE,
                io.mindspice.magenta2.avatar.dashboard.WidgetSettingsSchema.basic("dashboard"),
                widgetKey,
                widgetKey,
                "generic",
                io.mindspice.magenta2.avatar.dashboard.WidgetRefreshPolicy.MANUAL,
                io.mindspice.magenta2.avatar.dashboard.WidgetEmptyStatePolicy.NO_DATA,
                io.mindspice.magenta2.avatar.dashboard.WidgetToolDescriptor.none()
            ));
    }

    static List<AvatarDashboardWidget> normalizedLayout(List<AvatarDashboardWidget> saved) {
        Map<String, AvatarDashboardWidget> byKey = layoutByKey(saved);
        int position = 0;
        java.util.ArrayList<AvatarDashboardWidget> result = new java.util.ArrayList<>();
        for (DashboardWidgetDefinition definition : WIDGETS) {
            AvatarDashboardWidget widget = byKey.get(definition.type());
            result.add(widget == null ? defaultWidget(definition, position) : widget);
            position++;
        }
        result.sort(java.util.Comparator.comparingInt(AvatarDashboardWidget::position));
        return result;
    }

    static AvatarDashboardWidget defaultWidget(DashboardWidgetDefinition definition, int position) {
        return new AvatarDashboardWidget(
            definition.type(),
            position,
            sizeFromWidth(definition.defaultWidth()),
            true,
            false,
            Map.of("widgetType", definition.type()),
            null
        );
    }

    static AvatarDashboardWidget displayWidget(AvatarDashboardRowWidget widget) {
        Map<String, Object> settings = new LinkedHashMap<>(widget.settings() == null ? Map.of() : widget.settings());
        settings.put("widgetType", widget.widgetKey());
        return new AvatarDashboardWidget(
            widget.id(),
            widget.columnPosition(),
            sizeFromWidth(widget.columnWidth()),
            widget.enabled(),
            widget.collapsed(),
            settings,
            widget.updatedAt()
        );
    }

    private static int defaultWidth(DashboardWidgetDefinition definition) {
        return definition.defaultWidth();
    }

    private static Map<String, Object> mergedSettings(DashboardWidgetDefinition definition, Map<String, Object> saved) {
        Map<String, Object> merged = new LinkedHashMap<>(definition.settingsSchema().defaults());
        if (saved != null) {
            merged.putAll(saved);
        }
        return merged;
    }

    private static String value(Map<String, Object> settings, String name) {
        Object value = settings == null ? null : settings.get(name);
        return value == null ? "" : value.toString();
    }

    private static String policyLabel(WidgetInstancePolicy policy) {
        return switch (policy) {
            case SINGLE_PER_DASHBOARD -> "single per dashboard";
            case MULTI_INSTANCE -> "multi-instance";
            case SINGLE_SYSTEM -> "single system";
        };
    }

    private static String optionLabel(String option) {
        return option == null ? "" : option.replace('_', ' ');
    }

    private static String sizeFromWidth(int width) {
        if (width >= 6) {
            return "wide";
        }
        if (width <= 3) {
            return "compact";
        }
        return "standard";
    }

    private static Map<String, AvatarDashboardWidget> layoutByKey(List<AvatarDashboardWidget> saved) {
        Map<String, AvatarDashboardWidget> byKey = new LinkedHashMap<>();
        if (saved != null) {
            for (AvatarDashboardWidget widget : saved) {
                String type = widgetType(widget);
                if (widget != null && isKnownWidget(type)) {
                    byKey.put(type, widget);
                }
            }
        }
        return byKey;
    }

    private static String normalizeTab(String tab) {
        return "dashboard";
    }

    record AvatarDashboardData(
        UserDashboard dashboard,
        List<UserDashboard> dashboards,
        AvatarProfile profile,
        List<AvatarDashboardWidget> layout,
        List<AvatarDashboardRow> rows,
        List<AvatarDailyTask> dailyTasks,
        List<AvatarTodo> todos,
        List<AvatarCalendarItem> calendarItems,
        TodayPlannerView todayPlanner,
        TasksRoutinesView tasksRoutines,
        CalendarScheduleView calendarSchedule,
        List<AvatarNote> notes,
        Map<String, DashboardNotesView> noteViews,
        Map<String, DashboardProjectContextView> projectViews,
        List<AvatarEvent> events,
        List<RunOutputArtifact> outputs,
        List<AgentProfile> agents,
        List<WorkArea> workAreas,
        List<JobDefinition> jobs,
        List<WorkAssignment> assignments,
        List<InboxMessage> userInbox,
        String defaultModel
    ) {
    }
}
