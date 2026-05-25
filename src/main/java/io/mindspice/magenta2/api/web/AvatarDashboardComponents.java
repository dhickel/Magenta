package io.mindspice.magenta2.api.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import io.mindspice.magenta2.avatar.PlannerCalendarProjection;
import io.mindspice.magenta2.avatar.PlannerSubtodo;
import io.mindspice.magenta2.avatar.PlannerTask;
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
    static final List<WidgetDefinition> WIDGETS = List.of(
        new WidgetDefinition("daily-tasks", "Daily Tasks", "wide"),
        new WidgetDefinition("todos", "Todos", "standard"),
        new WidgetDefinition("calendar", "Calendar", "standard"),
        new WidgetDefinition("notes", "Notes", "wide"),
        new WidgetDefinition("files", "Work Areas", "standard"),
        new WidgetDefinition("outputs", "Outputs", "wide"),
        new WidgetDefinition("system", "System", "standard"),
        new WidgetDefinition("alerts", "Alerts", "standard"),
        new WidgetDefinition("recent-work", "Recent Work", "wide")
    );

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private AvatarDashboardComponents() {
    }

    static Component page(AvatarDashboardData data, boolean editMode) {
        return page(data, "dashboard", editMode);
    }

    static Component page(AvatarDashboardData data, String activeTab, boolean editMode) {
        String normalizedTab = normalizeTab(activeTab);
        boolean dashboardEditMode = editMode && "dashboard".equals(normalizedTab);
        return new Div()
            .withId("avatar-page")
            .withClass(dashboardEditMode ? "avatar-page avatar-page-editing" : "avatar-page")
            .withAttribute("data-avatar-page", "true")
            .withAttribute("data-avatar-shell", "true")
            .withAttribute("data-avatar-active-tab", normalizedTab)
            .withChild(pageHeader(data.profile()))
            .withChild(new Div().withClass("avatar-shell")
                .withChild(new Div().withClass("avatar-shell-grid")
                    .withChild(new Div().withClass("avatar-shell-rail")
                        .withChild(compactChat(data.defaultModel())))
                    .withChild(new Div().withClass("avatar-shell-main")
                        .withChild(shellTabs(normalizedTab, dashboardEditMode))
                        .withChild(tabPanel(data, normalizedTab, dashboardEditMode)))))
            .withChild(new Div().withId("avatar-edit-container"))
            .withChild(new Div().withId("avatar-output-preview").withClass("avatar-output-preview"))
            .withChild(moduleScript("/js/avatar-chat.js?v=3"))
            .withChild(moduleScript("/js/avatar-layout-edit.js?v=1"))
            .withChild(moduleScript("/js/avatar-shell.js?v=4"));
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
            for (AvatarDashboardWidget widget : normalizedLayout(data.layout())) {
                grid.withChild(widget(data, widget));
            }
            return grid;
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

    static Component widget(AvatarDashboardData data, AvatarDashboardWidget widget) {
        return widget(data, widget, null, false);
    }

    private static Component widget(
        AvatarDashboardData data,
        AvatarDashboardWidget widget,
        AvatarDashboardRowWidget layoutWidget,
        boolean editMode
    ) {
        WidgetDefinition definition = definition(widget.widgetId());
        Div frame = new Div()
            .withId(rootId(widget.widgetId()))
            .withClass("avatar-widget")
            .withClass("avatar-widget-" + size(widget))
            .withAttribute("data-avatar-widget", widget.widgetId())
            .withAttribute("data-avatar-widget-enabled", Boolean.toString(widget.enabled()));
        if (editMode && layoutWidget != null) {
            frame.withClass("avatar-widget-editing");
        }
        if (!widget.enabled()) {
            frame.withClass("avatar-widget-disabled");
        }
        frame.withChild(widgetCornerControls(widget.widgetId(), layoutWidget, editMode));
        frame.withChild(new Div().withClass("avatar-widget-header")
            .withChild(Header.H2(definition.title())));
        if (!widget.enabled()) {
            return frame.withChild(empty("Disabled in layout."));
        }
        return frame.withChild(widgetBody(data, widget.widgetId()));
    }

    static Component editModal(List<AvatarDashboardRow> rows) {
        Div panel = new Div()
            .withId("avatar-layout-editor")
            .withClass("avatar-edit-panel")
            .withAttribute("data-avatar-layout-editor", "true");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(new Div()
                .withChild(Header.H2("Edit Avatar Dashboard"))
                .withChild(small("Rows use 12-column widths. Each first-party widget can appear once.")))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_edit?close=true")
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
                .withAttribute("hx-post", "/avatar/_layout/rows")
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
            .withChild(new Div().withId("avatar-edit-container").withAttribute("hx-swap-oob", "true"))
            .withChild(grid);
    }

    static Component layoutEditResponseWithCatalog(AvatarDashboardData data, String rowId) {
        Div grid = (Div) widgetGrid(data, true);
        grid.withAttribute("hx-swap-oob", "true");
        return new Div()
            .withChild(widgetCatalogModal(data.rows(), rowId))
            .withChild(grid);
    }

    static Component widgetDetailModal(AvatarDashboardData data, String widgetKey) {
        Div panel = new Div().withClass("avatar-edit-panel avatar-widget-detail-panel");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(Header.H2(definition(widgetKey).title()))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_edit?close=true")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        panel.withChild(new Div().withClass("avatar-widget avatar-widget-detail")
            .withChild(widgetBody(data, widgetKey)));
        return new Div().withId("avatar-widget-detail-modal").withClass("avatar-modal").withChild(panel);
    }

    static Component widgetWidthPicker(List<AvatarDashboardRow> rows, AvatarDashboardRowWidget widget) {
        WidgetDefinition definition = definition(widget.widgetKey());
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
            preset.withAttribute("hx-put", "/avatar/_layout/widgets/" + widget.id() + "/width");
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
        custom.withAttribute("hx-put", "/avatar/_layout/widgets/" + widget.id() + "/width");
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
        java.util.Set<String> used = rows == null ? java.util.Set.of() : rows.stream()
            .flatMap(row -> row.widgets().stream())
            .map(AvatarDashboardRowWidget::widgetKey)
            .collect(java.util.stream.Collectors.toSet());
        Div panel = new Div().withClass("avatar-edit-panel avatar-widget-catalog avatar-widget-picker-modal");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(new Div()
                .withChild(Header.H2("Add Widget"))
                .withChild(small("Pick one module for this row. Widths follow the 12-column dashboard grid.")))
            .withChild(Button.create("Close")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_edit?close=true")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        Div catalog = new Div().withClass("avatar-catalog-grid");
        for (WidgetDefinition definition : WIDGETS) {
            boolean disabled = used.contains(definition.key());
            Form form = Form.create().withClass("avatar-catalog-item");
            form.withAttribute("hx-post", "/avatar/_layout/rows/" + rowId + "/widgets");
            form.withAttribute("hx-target", "#avatar-edit-container");
            form.withAttribute("hx-swap", "innerHTML");
            if (disabled) {
                form.withClass("avatar-catalog-item-disabled");
            }
            form.withChild(new HtmlTag("input", true)
                .withAttribute("type", "hidden")
                .withAttribute("name", "widgetKey")
                .withAttribute("value", definition.key()));
            form.withChild(new Div()
                .withChild(new HtmlTag("strong").withInnerText(definition.title()))
                .withChild(small(widgetDescription(definition.key()))));
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
                .withAttribute("hx-get", "/avatar/_edit?close=true")
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
            .withAttribute("hx-get", "/avatar/_organizer?tab=" + tab)
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
        form.withAttribute("hx-post", "/avatar/_planner-tasks");
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
            subtodoForm.withAttribute("hx-post", "/avatar/_planner-tasks/" + task.id() + "/subtodos");
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
        return body.withChild(todos(todos));
    }

    private static Component organizerCalendarTab(
        List<AvatarCalendarItem> items,
        List<PlannerCalendarProjection> projections
    ) {
        Div body = new Div().withClass("avatar-organizer-body");
        body.withChild(calendar(items));
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
        return body.withChild(notes(notes));
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

    private static Component pageHeader(AvatarProfile profile) {
        String displayName = profile == null || profile.displayName() == null ? "Avatar" : profile.displayName();
        return new Div().withClass("avatar-page-header")
            .withChild(new Div()
                .withChild(Header.H1(displayName))
                .withChild(new Paragraph("Personal command surface for dashboard work, queue flow, chat, and outputs.")))
            .withChild(new HtmlTag("a")
                .withClass("avatar-dashboard-link")
                .withAttribute("href", "/dashboard")
                .withInnerText("Operations Dashboard"));
    }

    private static Component shellTabs(String activeTab, boolean editMode) {
        return new Div().withId("avatar-shell-tabs-wrap").withClass("avatar-shell-tabs-wrap")
            .withChild(new Div().withClass("avatar-shell-strip")
                .withChild(new Div()
                    .withChild(Header.H2("Avatar"))
                    .withChild(small("Operational shell with a persistent assistant rail.")))
                .withChild("dashboard".equals(activeTab)
                    ? dashboardActions(editMode)
                    : new Div().withClass("avatar-shell-actions")
                        .withChild(new HtmlTag("span").withClass("avatar-shell-note")
                            .withInnerText(shellNote(activeTab)))))
            .withChild(new HtmlTag("nav")
                .withClass("orch-tabs avatar-shell-tabs")
                .withAttribute("aria-label", "Avatar views")
                .withChild(shellTab("dashboard", "Dashboard", activeTab, editMode))
                .withChild(shellTab("queue", "Queue", activeTab, false))
                .withChild(shellTab("history", "History", activeTab, false))
                .withChild(shellTab("profile", "Profile", activeTab, false))
                .withChild(shellTab("outputs", "Outputs", activeTab, false))
                .withChild(shellTab("work-areas", "Work Areas", activeTab, false)));
    }

    private static Component shellTab(String tab, String label, String activeTab, boolean editMode) {
        Button button = Button.create(label);
        if (tab.equals(activeTab)) {
            button.withClass("active");
        }
        String href = tab.equals("dashboard") && editMode ? "/avatar?tab=dashboard&edit=true" : "/avatar?tab=" + tab;
        return button
            .withAttribute("data-avatar-tab", tab)
            .withAttribute("aria-current", tab.equals(activeTab) ? "page" : "false")
            .withAttribute("hx-get", "/avatar/_tab-panel/" + tab + (tab.equals("dashboard") && editMode ? "?edit=true" : ""))
            .withAttribute("hx-target", "#avatar-tab-panel")
            .withAttribute("hx-swap", "outerHTML")
            .withAttribute("hx-push-url", href);
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
            editMode ? "/avatar?tab=dashboard" : "/avatar?tab=dashboard&edit=true"
        ));
    }

    static Component tabPanel(AvatarDashboardData data, String activeTab, boolean editMode) {
        String normalizedTab = normalizeTab(activeTab);
        Div panel = new Div()
            .withId("avatar-tab-panel")
            .withClass("avatar-tab-panel avatar-tab-panel-" + normalizedTab)
            .withAttribute("data-avatar-tab-panel", normalizedTab);
        return switch (normalizedTab) {
            case "queue" -> panel.withChild(tabSection("Queue", "Live assignment queue for Avatar-supervised work.")
                .withChild(queuePanel(data.assignments(), data.agents())));
            case "history" -> panel.withChild(tabSection("History", "Recent work and published results across the Avatar surface.")
                .withChild(historyPanel(data.jobs(), data.assignments(), data.outputs())));
            case "profile" -> panel.withChild(tabSection("Profile", "Avatar identity and assistant defaults.")
                .withChild(profilePanel(data.profile(), data.agents(), data.defaultModel())));
            case "outputs" -> panel.withChild(tabSection("Outputs", "Recent generated artifacts and previews.")
                .withChild(outputsPanel(data.outputs())));
            case "work-areas" -> panel.withChild(tabSection("Work Areas", "Confined workspaces and files available to Avatar.")
                .withChild(workAreasPanel(data.workAreas())));
            default -> panel.withChild(new Div().withClass("avatar-dashboard-panel")
                .withChild(widgetGrid(data, editMode)));
        };
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
            return panel.withChild(empty("No active assignments are visible to Avatar."));
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
        grid.withChild(profileField("Display", profile == null ? "Avatar" : profile.displayName()));
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
            case "queue" -> "Avatar-supervised queue view";
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
                    .withChild(Header.H2("Avatar Chat"))
                    .withChild(small("Personal assistant channel")))
                .withChild(new Div().withClass("avatar-chat-chips")
                    .withChild(new HtmlTag("span").withClass("avatar-chip").withInnerText("surface avatar"))
                    .withChild(new HtmlTag("span").withId("avatar-chat-session").withClass("avatar-chip").withInnerText("new chat"))))
            .withChild(new Div().withClass("avatar-chat-status")
                .withChild(new HtmlTag("span").withId("avatar-chat-status").withInnerText("Ready"))
                .withChild(new HtmlTag("span").withInnerText(defaultModel == null || defaultModel.isBlank()
                    ? "model unset"
                    : "model " + defaultModel)))
            .withChild(new Div().withId("avatar-chat-messages")
                .withClass("avatar-chat-messages")
                .withAttribute("aria-live", "polite")
                .withChild(new Div().withClass("avatar-chat-empty").withInnerText("Ask Avatar for a quick update.")))
            .withChild(Form.create().withId("avatar-chat-form").withClass("avatar-chat-form")
                .withChild(TextArea.create("message").withId("avatar-chat-input").withRows(4)
                    .withPlaceholder("Ask Avatar"))
                .withChild(Button.submit("Send")))
            .withChild(new HtmlTag("button")
                .withClass("avatar-chat-corner-resizer")
                .withAttribute("type", "button")
                .withAttribute("data-avatar-chat-corner-resizer", "true")
                .withAttribute("aria-label", "Resize Avatar chat")
                .withAttribute("title", "Resize Avatar chat"));
    }

    private static Component widgetBody(AvatarDashboardData data, String widgetId) {
        return switch (widgetId) {
            case "daily-tasks" -> dailyTasks(data.dailyTasks());
            case "todos" -> todos(data.todos());
            case "calendar" -> calendar(data.calendarItems());
            case "notes" -> notes(data.notes());
            case "files" -> files(data.workAreas());
            case "outputs" -> outputs(data.outputs());
            case "system" -> system(data.agents(), data.jobs(), data.assignments());
            case "alerts" -> alerts(data.events(), data.userInbox());
            case "recent-work" -> recentWork(data.jobs(), data.assignments(), data.outputs());
            default -> empty("Unknown widget.");
        };
    }

    private static Component dailyTasks(List<AvatarDailyTask> tasks) {
        Div body = new Div().withClass("avatar-widget-body");
        body.withChild(Form.create().withClass("avatar-inline-form")
            .withAttribute("hx-post", "/avatar/_daily-tasks")
            .withAttribute("hx-target", "#" + rootId("daily-tasks"))
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
                    "/avatar/_daily-tasks/" + task.id() + "/complete",
                    rootId("daily-tasks")
                )));
        }
        body.withChild(list);
        if (tasks.size() > visible.size()) {
            body.withChild(small("Showing " + visible.size() + " of " + tasks.size() + " daily tasks."));
        }
        return body;
    }

    private static Component todos(List<AvatarTodo> todos) {
        Div body = new Div().withClass("avatar-widget-body");
        Select priority = Select.create("priority")
            .addOption("NORMAL", "Normal", true)
            .addOption("HIGH", "High", false)
            .addOption("URGENT", "Urgent", false)
            .addOption("LOW", "Low", false);
        body.withChild(Form.create().withClass("avatar-inline-form")
            .withAttribute("hx-post", "/avatar/_todos")
            .withAttribute("hx-target", "#" + rootId("todos"))
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
                        "/avatar/_todos/" + todo.id() + "/complete",
                        rootId("todos")
                    ))
                    .withChild(iconDeleteAction(
                        "Delete todo",
                        "Delete todo " + todo.title(),
                        "/avatar/_todos/" + todo.id(),
                        rootId("todos")
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

    private static Component calendar(List<AvatarCalendarItem> items) {
        Div body = new Div().withClass("avatar-widget-body");
        body.withChild(Form.create().withClass("avatar-stack-form")
            .withAttribute("hx-post", "/avatar/_calendar")
            .withAttribute("hx-target", "#" + rootId("calendar"))
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
                    "/avatar/_calendar/" + item.id(),
                    rootId("calendar")
                )));
        }
        return body.withChild(list);
    }

    private static Component notes(List<AvatarNote> notes) {
        Div body = new Div().withClass("avatar-widget-body");
        body.withChild(Form.create().withClass("avatar-stack-form")
            .withAttribute("hx-post", "/avatar/_notes")
            .withAttribute("hx-target", "#" + rootId("notes"))
            .withAttribute("hx-swap", "outerHTML")
            .withChild(TextInput.create("title").withPlaceholder("Note title"))
            .withChild(TextArea.create("body").withRows(3).withPlaceholder("Capture a note"))
            .withChild(Button.submit("Save Note")));
        if (notes == null || notes.isEmpty()) {
            return body.withChild(empty("No notes yet."));
        }
        Div list = new Div().withClass("avatar-list");
        for (AvatarNote note : notes.stream().limit(4).toList()) {
            list.withChild(new Div().withClass("avatar-note")
                .withChild(new HtmlTag("strong").withInnerText(note.title()))
                .withChild(new Paragraph(snippet(note.body(), 160))));
        }
        return body.withChild(list);
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
        body.withClass("avatar-workarea-browser");
        Div layout = new Div().withClass("avatar-workarea-browser-grid");
        Div list = new Div().withClass("avatar-list");
        for (WorkArea workArea : workAreas.stream().limit(8).toList()) {
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(workArea.displayName()))
                    .withChild(small(workArea.ownerId() + " / " + workArea.areaRelativePath())))
                .withChild(Button.create("Browse")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/avatar/_work-areas/" + workArea.id() + "/explorer")
                    .withAttribute("hx-target", "#avatar-workarea-surface")
                    .withAttribute("hx-swap", "innerHTML")));
        }
        return body.withChild(layout
            .withChild(list)
            .withChild(new Div()
                .withId("avatar-workarea-surface")
                .withClass("avatar-workarea-surface")
                .withChild(workAreaSurfacePlaceholder())));
    }

    static Component workAreaSurfacePlaceholder() {
        return new Div().withClass("avatar-workarea-surface-empty")
            .withChild(Header.H3("Select a Work Area"))
            .withChild(small("Choose Browse to open the confined file explorer here."));
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
                    .withAttribute("hx-get", "/avatar/_outputs/" + output.id())
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

    private static Component alerts(List<AvatarEvent> events, List<InboxMessage> inbox) {
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
                    .withChild(action("Dismiss", "/avatar/_alerts/" + event.id() + "/dismiss", rootId("alerts"))));
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
                    .withAttribute("hx-get", "/avatar/_layout/rows/" + row.id() + "/catalog")
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
        WidgetDefinition definition = definition(widget.widgetKey());
        HtmlTag widthForm = Form.create().withClass("avatar-widget-width-form")
            .withAttribute("hx-put", "/avatar/_layout/widgets/" + widget.id() + "/width")
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
                    .withAttribute("hx-delete", "/avatar/_layout/widgets/" + widget.id())
                    .withAttribute("hx-target", "#avatar-edit-container")
                    .withAttribute("hx-swap", "innerHTML")
                    .withAttribute("hx-confirm", "Remove this widget?")));
    }

    private static Component addModuleSection(AvatarDashboardRow row, int index, int rowCount) {
        return new Div().withClass("add-module-section avatar-add-module-section")
            .withChild(Button.create("+ Add Widget")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_layout/rows/" + row.id() + "/catalog")
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
                .withAttribute("hx-get", "/avatar/_layout/rows/" + row.id() + "/catalog")
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
                .withAttribute("hx-post", "/avatar/_layout/rows/" + row.id() + "/insert-after")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"));
    }

    private static Component widgetCornerControls(
        String widgetKey,
        AvatarDashboardRowWidget layoutWidget,
        boolean editMode
    ) {
        Div controls = new Div().withClass(editMode
            ? "avatar-widget-corner-controls avatar-widget-corner-controls-editing"
            : "avatar-widget-corner-controls");
        controls.withChild(detailButton(widgetKey));
        if (!editMode || layoutWidget == null) {
            return controls;
        }
        controls.withChild(widgetMoveButton(layoutWidget.id(), "left"));
        controls.withChild(widgetMoveButton(layoutWidget.id(), "right"));
        controls.withChild(widgetMoveButton(layoutWidget.id(), "up"));
        controls.withChild(widgetMoveButton(layoutWidget.id(), "down"));

        controls.withChild(iconButton("width", "Choose widget width", "Choose " + definition(widgetKey).title() + " width")
            .withAttribute("data-avatar-width-picker-trigger", "true")
            .withAttribute("data-avatar-widget-id", layoutWidget.id())
            .withAttribute("hx-get", "/avatar/_layout/widgets/" + layoutWidget.id() + "/width-picker")
            .withAttribute("hx-target", "#avatar-edit-container")
            .withAttribute("hx-swap", "innerHTML"));
        controls.withChild(iconButton("trash", "Remove widget", "Remove " + definition(widgetKey).title())
            .withAttribute("hx-delete", "/avatar/_layout/widgets/" + layoutWidget.id())
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
        button.withAttribute("hx-post", "/avatar/_layout/rows/" + rowId + "/move?direction=" + direction);
        button.withAttribute("hx-target", "#avatar-edit-container");
        button.withAttribute("hx-swap", "innerHTML");
        if (disabled) {
            button.withAttribute("disabled", "disabled");
        }
        return button;
    }

    private static Component rowDeleteButton(String rowId, boolean enabled) {
        HtmlTag button = iconButton("trash", "Delete empty row", "Delete empty row");
        button.withAttribute("hx-delete", "/avatar/_layout/rows/" + rowId);
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
            .withAttribute("hx-post", "/avatar/_layout/widgets/" + widgetId + "/move?direction=" + direction)
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

    private static Component detailButton(String key) {
        return iconButton("settings", "Open widget settings", "Open " + definition(key).title() + " settings")
            .withAttribute("data-avatar-detail-trigger", key)
            .withAttribute("hx-get", "/avatar/_widgets/" + key + "/detail")
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

    static String rootId(String widgetKey) {
        return "avatar-widget-" + widgetKey;
    }

    static boolean isKnownWidget(String widgetKey) {
        return WIDGETS.stream().anyMatch(widget -> widget.key().equals(widgetKey));
    }

    static WidgetDefinition definition(String widgetKey) {
        return WIDGETS.stream()
            .filter(widget -> widget.key().equals(widgetKey))
            .findFirst()
            .orElse(new WidgetDefinition(widgetKey, widgetKey, "standard"));
    }

    static List<AvatarDashboardWidget> normalizedLayout(List<AvatarDashboardWidget> saved) {
        Map<String, AvatarDashboardWidget> byKey = layoutByKey(saved);
        int position = 0;
        java.util.ArrayList<AvatarDashboardWidget> result = new java.util.ArrayList<>();
        for (WidgetDefinition definition : WIDGETS) {
            AvatarDashboardWidget widget = byKey.get(definition.key());
            result.add(widget == null ? defaultWidget(definition, position) : widget);
            position++;
        }
        result.sort(java.util.Comparator.comparingInt(AvatarDashboardWidget::position));
        return result;
    }

    static AvatarDashboardWidget defaultWidget(WidgetDefinition definition, int position) {
        return new AvatarDashboardWidget(definition.key(), position, definition.defaultSize(), true, false, Map.of(), null);
    }

    static AvatarDashboardWidget displayWidget(AvatarDashboardRowWidget widget) {
        return new AvatarDashboardWidget(
            widget.widgetKey(),
            widget.columnPosition(),
            sizeFromWidth(widget.columnWidth()),
            widget.enabled(),
            widget.collapsed(),
            widget.settings(),
            widget.updatedAt()
        );
    }

    private static int defaultWidth(WidgetDefinition definition) {
        return switch (definition.defaultSize()) {
            case "wide" -> 6;
            case "compact" -> 3;
            default -> 4;
        };
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

    private static String widgetDescription(String key) {
        return switch (key) {
            case "daily-tasks" -> "Today-focused task capture.";
            case "todos" -> "Priority queue and quick completion.";
            case "calendar" -> "Upcoming dated work.";
            case "notes" -> "Short personal notes.";
            case "files" -> "Agent work-area browser.";
            case "outputs" -> "Recent generated artifacts.";
            case "system" -> "Agent and queue counters.";
            case "alerts" -> "Inbox and system alerts.";
            case "recent-work" -> "Recent jobs, assignments, and outputs.";
            default -> "Avatar dashboard widget.";
        };
    }

    private static Map<String, AvatarDashboardWidget> layoutByKey(List<AvatarDashboardWidget> saved) {
        Map<String, AvatarDashboardWidget> byKey = new LinkedHashMap<>();
        if (saved != null) {
            for (AvatarDashboardWidget widget : saved) {
                if (widget != null && isKnownWidget(widget.widgetId())) {
                    byKey.put(widget.widgetId(), widget);
                }
            }
        }
        return byKey;
    }

    record WidgetDefinition(String key, String title, String defaultSize) {
    }

    private static String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return "dashboard";
        }
        return switch (tab) {
            case "dashboard", "queue", "history", "profile", "outputs", "work-areas" -> tab;
            default -> "dashboard";
        };
    }

    record AvatarDashboardData(
        AvatarProfile profile,
        List<AvatarDashboardWidget> layout,
        List<AvatarDashboardRow> rows,
        List<AvatarDailyTask> dailyTasks,
        List<AvatarTodo> todos,
        List<AvatarCalendarItem> calendarItems,
        List<AvatarNote> notes,
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
