package io.mindspice.magenta2.api.web;

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
import io.mindspice.magenta2.avatar.AvatarCalendarItem;
import io.mindspice.magenta2.avatar.AvatarDailyTask;
import io.mindspice.magenta2.avatar.AvatarDashboardRow;
import io.mindspice.magenta2.avatar.AvatarDashboardRowWidget;
import io.mindspice.magenta2.avatar.AvatarDashboardWidget;
import io.mindspice.magenta2.avatar.AvatarEvent;
import io.mindspice.magenta2.avatar.AvatarNote;
import io.mindspice.magenta2.avatar.AvatarProfile;
import io.mindspice.magenta2.avatar.AvatarTodo;
import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.components.Header;
import io.mindspice.simplypages.components.Paragraph;
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
        new WidgetDefinition("files", "Files", "standard"),
        new WidgetDefinition("outputs", "Outputs", "wide"),
        new WidgetDefinition("system", "System", "standard"),
        new WidgetDefinition("alerts", "Alerts", "standard"),
        new WidgetDefinition("recent-work", "Recent Work", "wide")
    );

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private AvatarDashboardComponents() {
    }

    static Component page(AvatarDashboardData data) {
        return new Div()
            .withId("avatar-page")
            .withClass("avatar-page")
            .withAttribute("data-avatar-page", "true")
            .withChild(pageHeader(data.profile()))
            .withChild(new Div().withClass("avatar-layout")
                .withChild(new Div().withClass("avatar-main")
                    .withChild(toolbar())
                    .withChild(widgetGrid(data)))
                .withChild(compactChat(data.defaultModel())))
            .withChild(new Div().withId("avatar-edit-container"))
            .withChild(new Div().withId("avatar-output-preview").withClass("avatar-output-preview"))
            .withChild(moduleScript("/js/avatar-chat.js?v=3"));
    }

    static Component widgetGrid(AvatarDashboardData data) {
        List<AvatarDashboardRow> rows = data.rows() == null ? List.of() : data.rows();
        Div grid = new Div()
            .withId("avatar-widget-grid")
            .withClass(rows.isEmpty() ? "avatar-widget-grid" : "avatar-widget-grid avatar-row-widget-grid")
            .withAttribute("data-avatar-widget-grid", "true");
        if (rows.isEmpty()) {
            for (AvatarDashboardWidget widget : normalizedLayout(data.layout())) {
                grid.withChild(widget(data, widget));
            }
            return grid;
        }
        for (AvatarDashboardRow dashboardRow : rows) {
            Row row = new Row().withId("avatar-dashboard-row-" + dashboardRow.id());
            row.withAttribute("class", "row avatar-dashboard-row");
            for (AvatarDashboardRowWidget rowWidget : dashboardRow.widgets()) {
                row.addColumn(Column.create()
                    .withWidth(rowWidget.columnWidth())
                    .withChild(widget(data, displayWidget(rowWidget))));
            }
            grid.withChild(row);
        }
        return grid;
    }

    static Component widget(AvatarDashboardData data, AvatarDashboardWidget widget) {
        WidgetDefinition definition = definition(widget.widgetId());
        Div frame = new Div()
            .withId(rootId(widget.widgetId()))
            .withClass("avatar-widget")
            .withClass("avatar-widget-" + size(widget))
            .withAttribute("data-avatar-widget", widget.widgetId())
            .withAttribute("data-avatar-widget-enabled", Boolean.toString(widget.enabled()));
        if (!widget.enabled()) {
            frame.withClass("avatar-widget-disabled");
        }
        frame.withChild(new Div().withClass("avatar-widget-header")
            .withChild(Header.H2(definition.title()))
            .withChild(new Div().withClass("avatar-widget-actions")
                .withChild(refreshButton(widget.widgetId()))));
        if (!widget.enabled()) {
            return frame.withChild(empty("Disabled in layout."));
        }
        return switch (widget.widgetId()) {
            case "daily-tasks" -> frame.withChild(dailyTasks(data.dailyTasks()));
            case "todos" -> frame.withChild(todos(data.todos()));
            case "calendar" -> frame.withChild(calendar(data.calendarItems()));
            case "notes" -> frame.withChild(notes(data.notes()));
            case "files" -> frame.withChild(files(data.outputs()));
            case "outputs" -> frame.withChild(outputs(data.outputs()));
            case "system" -> frame.withChild(system(data.agents(), data.jobs(), data.assignments()));
            case "alerts" -> frame.withChild(alerts(data.events(), data.userInbox()));
            case "recent-work" -> frame.withChild(recentWork(data.jobs(), data.assignments(), data.outputs()));
            default -> frame.withChild(empty("Unknown widget."));
        };
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
        return layoutEditResponse(data);
    }

    static Component layoutEditResponse(AvatarDashboardData data) {
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
        Div panel = new Div().withClass("avatar-edit-panel avatar-widget-catalog");
        panel.withChild(new Div().withClass("avatar-edit-header")
            .withChild(Header.H2("Add Widget"))
            .withChild(Button.create("Back")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_edit")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")));
        Div catalog = new Div().withClass("avatar-catalog-grid");
        for (WidgetDefinition definition : WIDGETS) {
            boolean disabled = used.contains(definition.key());
            Form form = Form.create().withClass("avatar-catalog-item");
            form.withAttribute("hx-post", "/avatar/_layout/rows/" + rowId + "/widgets");
            form.withAttribute("hx-target", "#avatar-edit-container");
            form.withAttribute("hx-swap", "innerHTML");
            form.withChild(new HtmlTag("input", true)
                .withAttribute("type", "hidden")
                .withAttribute("name", "widgetKey")
                .withAttribute("value", definition.key()));
            form.withChild(new HtmlTag("strong").withInnerText(definition.title()));
            form.withChild(widthSelect("columnWidth", defaultWidth(definition), false));
            Button button = Button.submit(disabled ? "Added" : "Add");
            if (disabled) {
                button.withAttribute("disabled", "disabled");
            }
            form.withChild(button);
            catalog.withChild(form);
        }
        panel.withChild(catalog);
        return new Div().withId("avatar-edit-modal").withClass("avatar-modal").withChild(panel);
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

    private static Component pageHeader(AvatarProfile profile) {
        String displayName = profile == null || profile.displayName() == null ? "Avatar" : profile.displayName();
        return new Div().withClass("avatar-page-header")
            .withChild(new Div()
                .withChild(Header.H1(displayName))
                .withChild(new Paragraph("Personal command surface for chat, organizer work, alerts, and recent outputs.")))
            .withChild(new HtmlTag("a")
                .withClass("avatar-dashboard-link")
                .withAttribute("href", "/dashboard")
                .withInnerText("Operations Dashboard"));
    }

    private static Component toolbar() {
        return new Div().withClass("avatar-toolbar")
            .withChild(new HtmlTag("button")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_edit")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML")
                .withInnerText("Edit Layout"))
            .withChild(new HtmlTag("button")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/avatar/_widgets")
                .withAttribute("hx-target", "#avatar-widget-grid")
                .withAttribute("hx-swap", "outerHTML")
                .withInnerText("Refresh Widgets"));
    }

    private static Component compactChat(String defaultModel) {
        return new HtmlTag("aside")
            .withId("avatar-chat")
            .withClass("avatar-chat")
            .withAttribute("data-avatar-chat", "true")
            .withAttribute("data-chat-surface", "avatar")
            .withAttribute("data-default-model", defaultModel == null ? "" : defaultModel)
            .withChild(new Div().withClass("avatar-chat-header")
                .withChild(Header.H2("Avatar Chat"))
                .withChild(new HtmlTag("span").withId("avatar-chat-session").withInnerText("New chat")))
            .withChild(new Div().withId("avatar-chat-messages")
                .withClass("avatar-chat-messages")
                .withAttribute("aria-live", "polite")
                .withChild(new Div().withClass("avatar-chat-empty").withInnerText("Ask Avatar for a quick update.")))
            .withChild(Form.create().withId("avatar-chat-form").withClass("avatar-chat-form")
                .withChild(TextArea.create("message").withId("avatar-chat-input").withRows(4)
                    .withPlaceholder("Ask Avatar"))
                .withChild(Button.submit("Send")));
    }

    private static Component dailyTasks(List<AvatarDailyTask> tasks) {
        Div body = new Div().withClass("avatar-widget-body");
        body.withChild(Form.create().withClass("avatar-inline-form")
            .withAttribute("hx-post", "/avatar/_daily-tasks")
            .withAttribute("hx-target", "#" + rootId("daily-tasks"))
            .withAttribute("hx-swap", "outerHTML")
            .withChild(TextInput.create("title").withPlaceholder("Add daily task"))
            .withChild(Button.submit("Add")));
        if (tasks == null || tasks.isEmpty()) {
            return body.withChild(empty("No daily tasks for today."));
        }
        Div list = new Div().withClass("avatar-list");
        for (AvatarDailyTask task : tasks) {
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(task.title()))
                    .withChild(small(task.status() == null ? "planned" : task.status().name().toLowerCase(Locale.ROOT))))
                .withChild(action("Done", "/avatar/_daily-tasks/" + task.id() + "/complete", rootId("daily-tasks"))));
        }
        return body.withChild(list);
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
            .withChild(Button.submit("Add")));
        if (todos == null || todos.isEmpty()) {
            return body.withChild(empty("No todos."));
        }
        Div list = new Div().withClass("avatar-list");
        for (AvatarTodo todo : newestTodos(todos)) {
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(todo.title()))
                    .withChild(small(todo.priority() == null ? "normal" : todo.priority().name().toLowerCase(Locale.ROOT))))
                .withChild(new Div().withClass("avatar-row-actions")
                    .withChild(action("Done", "/avatar/_todos/" + todo.id() + "/complete", rootId("todos")))
                    .withChild(deleteAction("Delete", "/avatar/_todos/" + todo.id(), rootId("todos")))));
        }
        return body.withChild(list);
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
                .withChild(deleteAction("Remove", "/avatar/_calendar/" + item.id(), rootId("calendar"))));
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

    private static Component files(List<RunOutputArtifact> outputs) {
        Div body = new Div().withClass("avatar-widget-body");
        List<RunOutputArtifact> withFiles = outputs == null ? List.of() : outputs.stream()
            .filter(output -> output.fileName() != null && !output.fileName().isBlank())
            .limit(6)
            .toList();
        if (withFiles.isEmpty()) {
            return body.withChild(empty("No recent output files."));
        }
        Div list = new Div().withClass("avatar-list");
        for (RunOutputArtifact output : withFiles) {
            list.withChild(new Div().withClass("avatar-list-row")
                .withChild(new Div()
                    .withChild(new HtmlTag("strong").withInnerText(output.fileName()))
                    .withChild(small(output.outputName())))
                .withChild(new HtmlTag("a")
                    .withClass("orch-primary")
                    .withAttribute("href", "/api/outputs/" + output.id() + "/download")
                    .withInnerText("Download")));
        }
        return body.withChild(list);
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

    private static Component rowMoveButton(String rowId, String direction, boolean disabled) {
        Button button = Button.create(direction.equals("up") ? "Up" : "Down");
        button.withAttribute("type", "button");
        button.withAttribute("hx-post", "/avatar/_layout/rows/" + rowId + "/move?direction=" + direction);
        button.withAttribute("hx-target", "#avatar-edit-container");
        button.withAttribute("hx-swap", "innerHTML");
        if (disabled) {
            button.withAttribute("disabled", "disabled");
        }
        return button;
    }

    private static Component widgetMoveButton(String widgetId, String direction) {
        return Button.create(switch (direction) {
                case "left" -> "Left";
                case "right" -> "Right";
                case "up" -> "Up";
                case "down" -> "Down";
                default -> direction;
            })
            .withAttribute("type", "button")
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

    private static Component refreshButton(String key) {
        return new HtmlTag("button")
            .withAttribute("type", "button")
            .withAttribute("hx-get", "/avatar/_widgets/" + key)
            .withAttribute("hx-target", "#" + rootId(key))
            .withAttribute("hx-swap", "outerHTML")
            .withInnerText("Refresh");
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
        List<JobDefinition> jobs,
        List<WorkAssignment> assignments,
        List<InboxMessage> userInbox,
        String defaultModel
    ) {
    }
}
