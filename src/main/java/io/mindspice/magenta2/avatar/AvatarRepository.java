package io.mindspice.magenta2.avatar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class AvatarRepository {
    public static final String PROFILE_ID = "default";

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AvatarRepository(@Qualifier("avatarJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<AvatarProfile> findProfile() {
        return jdbcTemplate.query(
            "select * from avatar_profile where id = ?",
            rs -> rs.next() ? Optional.of(toProfile(rs)) : Optional.empty(),
            PROFILE_ID
        );
    }

    public AvatarProfile saveProfile(AvatarProfile profile) {
        Instant now = Instant.now();
        Instant createdAt = profile.createdAt() == null ? now : profile.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into avatar_profile (id, display_name, timezone, locale, summary, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    display_name = excluded.display_name,
                    timezone = excluded.timezone,
                    locale = excluded.locale,
                    summary = excluded.summary,
                    updated_at = excluded.updated_at
                """,
            PROFILE_ID,
            requireText(profile.displayName(), "display name"),
            profile.timezone(),
            profile.locale(),
            profile.summary(),
            createdAt.toString(),
            updatedAt.toString()
        );
        return findProfile().orElseThrow();
    }

    public AvatarProfile defaultProfile() {
        return new AvatarProfile(
            PROFILE_ID,
            "Avatar",
            ZoneId.systemDefault().getId(),
            Locale.getDefault().toLanguageTag(),
            null,
            null,
            null
        );
    }

    public AvatarPreference upsertPreference(AvatarPreference preference) {
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into avatar_preferences (namespace, preference_key, value_json, updated_at)
                values (?, ?, ?, ?)
                on conflict(namespace, preference_key) do update set
                    value_json = excluded.value_json,
                    updated_at = excluded.updated_at
                """,
            requireText(preference.namespace(), "preference namespace"),
            requireText(preference.key(), "preference key"),
            jsonMap(preference.value()),
            updatedAt.toString()
        );
        return findPreference(preference.namespace(), preference.key()).orElseThrow();
    }

    public Optional<AvatarPreference> findPreference(String namespace, String key) {
        return jdbcTemplate.query(
            "select * from avatar_preferences where namespace = ? and preference_key = ?",
            rs -> rs.next() ? Optional.of(toPreference(rs)) : Optional.empty(),
            namespace,
            key
        );
    }

    public List<AvatarPreference> findPreferences() {
        return jdbcTemplate.query(
            "select * from avatar_preferences order by namespace, preference_key",
            (rs, rowNum) -> toPreference(rs)
        );
    }

    public AvatarDashboardWidget saveDashboardWidget(AvatarDashboardWidget widget) {
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into avatar_dashboard_layout (
                    widget_id, widget_position, widget_size, enabled, collapsed, settings_json, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(widget_id) do update set
                    widget_position = excluded.widget_position,
                    widget_size = excluded.widget_size,
                    enabled = excluded.enabled,
                    collapsed = excluded.collapsed,
                    settings_json = excluded.settings_json,
                    updated_at = excluded.updated_at
                """,
            requireText(widget.widgetId(), "widget id"),
            widget.position(),
            requireText(widget.size(), "widget size"),
            widget.enabled() ? 1 : 0,
            widget.collapsed() ? 1 : 0,
            jsonMap(widget.settings()),
            updatedAt.toString()
        );
        syncDashboardRowWidgetFromLegacy(widget);
        return findDashboardLayout().stream()
            .filter(saved -> saved.widgetId().equals(widget.widgetId()))
            .findFirst()
            .orElseThrow();
    }

    public List<AvatarDashboardWidget> findDashboardLayout() {
        return jdbcTemplate.query(
            "select * from avatar_dashboard_layout order by widget_position, widget_id",
            (rs, rowNum) -> toDashboardWidget(rs)
        );
    }

    public List<AvatarDashboardRow> findDashboardRows() {
        seedDashboardRowsFromLegacyLayoutIfNeeded();
        List<DashboardRowRecord> rows = jdbcTemplate.query(
            "select * from avatar_dashboard_rows order by row_position, id",
            (rs, rowNum) -> toDashboardRowRecord(rs)
        );
        return rows.stream()
            .map(row -> new AvatarDashboardRow(
                row.id(),
                row.position(),
                row.collapsed(),
                row.settings(),
                row.updatedAt(),
                findDashboardRowWidgets(row.id())
            ))
            .toList();
    }

    public AvatarDashboardRow addDashboardRow() {
        int nextPosition = nextDashboardRowPosition();
        String id = "row-" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                insert into avatar_dashboard_rows (id, row_position, collapsed, settings_json, updated_at)
                values (?, ?, 0, '{}', ?)
                """,
            id,
            nextPosition,
            now.toString()
        );
        return findDashboardRow(id).orElseThrow();
    }

    public AvatarDashboardRow moveDashboardRow(String rowId, int direction) {
        requireText(rowId, "row id");
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("row direction must be -1 or 1");
        }
        List<DashboardRowRecord> rows = dashboardRowRecords();
        int index = indexOfRow(rows, rowId);
        int target = index + direction;
        if (target < 0 || target >= rows.size()) {
            throw new IllegalArgumentException("row cannot move outside layout bounds");
        }
        DashboardRowRecord current = rows.get(index);
        DashboardRowRecord swap = rows.get(target);
        Instant now = Instant.now();
        jdbcTemplate.update("update avatar_dashboard_rows set row_position = ?, updated_at = ? where id = ?",
            swap.position(), now.toString(), current.id());
        jdbcTemplate.update("update avatar_dashboard_rows set row_position = ?, updated_at = ? where id = ?",
            current.position(), now.toString(), swap.id());
        normalizeDashboardRows();
        return findDashboardRow(rowId).orElseThrow();
    }

    public AvatarDashboardRowWidget addDashboardWidget(String rowId, String widgetKey, int columnWidth) {
        requireText(rowId, "row id");
        requireText(widgetKey, "widget key");
        int width = requireColumnWidth(columnWidth);
        if (findDashboardWidgetByKey(widgetKey).isPresent()) {
            throw new IllegalArgumentException("dashboard widget already exists: " + widgetKey);
        }
        findDashboardRow(rowId).orElseThrow(() -> new IllegalArgumentException("dashboard row not found: " + rowId));
        int usedWidth = dashboardRowWidth(rowId);
        if (usedWidth + width > 12) {
            throw new IllegalArgumentException("dashboard row width cannot exceed 12 columns");
        }
        String id = "widget-" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                insert into avatar_dashboard_widgets (
                    id, row_id, widget_key, column_position, column_width, enabled, collapsed, settings_json, updated_at
                )
                values (?, ?, ?, ?, ?, 1, 0, '{}', ?)
                """,
            id,
            rowId,
            widgetKey,
            nextDashboardWidgetPosition(rowId),
            width,
            now.toString()
        );
        return findDashboardWidget(id).orElseThrow();
    }

    public AvatarDashboardRowWidget resizeDashboardWidget(String widgetId, int columnWidth) {
        AvatarDashboardRowWidget widget = findDashboardWidget(widgetId)
            .orElseThrow(() -> new IllegalArgumentException("dashboard widget not found: " + widgetId));
        int width = requireColumnWidth(columnWidth);
        int usedWidthWithoutWidget = dashboardRowWidth(widget.rowId()) - widget.columnWidth();
        if (usedWidthWithoutWidget + width > 12) {
            throw new IllegalArgumentException("dashboard row width cannot exceed 12 columns");
        }
        jdbcTemplate.update(
            "update avatar_dashboard_widgets set column_width = ?, updated_at = ? where id = ?",
            width,
            Instant.now().toString(),
            widgetId
        );
        return findDashboardWidget(widgetId).orElseThrow();
    }

    public AvatarDashboardRowWidget moveDashboardWidget(String widgetId, String direction) {
        AvatarDashboardRowWidget widget = findDashboardWidget(widgetId)
            .orElseThrow(() -> new IllegalArgumentException("dashboard widget not found: " + widgetId));
        String normalized = requireText(direction, "widget direction").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "left" -> moveDashboardWidgetWithinRow(widget, -1);
            case "right" -> moveDashboardWidgetWithinRow(widget, 1);
            case "up" -> moveDashboardWidgetToAdjacentRow(widget, -1);
            case "down" -> moveDashboardWidgetToAdjacentRow(widget, 1);
            default -> throw new IllegalArgumentException("unknown widget direction: " + direction);
        };
    }

    public void removeDashboardRow(String rowId) {
        requireText(rowId, "row id");
        findDashboardRow(rowId).orElseThrow(() -> new IllegalArgumentException("dashboard row not found: " + rowId));
        if (dashboardRowWidth(rowId) > 0) {
            throw new IllegalArgumentException("dashboard row must be empty before it can be removed");
        }
        jdbcTemplate.update("delete from avatar_dashboard_rows where id = ?", rowId);
        normalizeDashboardRows();
    }

    public void removeDashboardWidget(String widgetId) {
        requireText(widgetId, "widget id");
        if (jdbcTemplate.update("delete from avatar_dashboard_widgets where id = ?", widgetId) == 0) {
            throw new IllegalArgumentException("dashboard widget not found: " + widgetId);
        }
        normalizeDashboardWidgets();
    }

    private Optional<AvatarDashboardRow> findDashboardRow(String rowId) {
        return jdbcTemplate.query(
            "select * from avatar_dashboard_rows where id = ?",
            rs -> rs.next()
                ? Optional.of(toDashboardRowRecord(rs))
                : Optional.<DashboardRowRecord>empty(),
            rowId
        ).map(row -> new AvatarDashboardRow(
            row.id(),
            row.position(),
            row.collapsed(),
            row.settings(),
            row.updatedAt(),
            findDashboardRowWidgets(row.id())
        ));
    }

    private Optional<AvatarDashboardRowWidget> findDashboardWidget(String widgetId) {
        return jdbcTemplate.query(
            "select * from avatar_dashboard_widgets where id = ?",
            rs -> rs.next() ? Optional.of(toDashboardRowWidget(rs)) : Optional.empty(),
            widgetId
        );
    }

    private Optional<AvatarDashboardRowWidget> findDashboardWidgetByKey(String widgetKey) {
        return jdbcTemplate.query(
            "select * from avatar_dashboard_widgets where widget_key = ?",
            rs -> rs.next() ? Optional.of(toDashboardRowWidget(rs)) : Optional.empty(),
            widgetKey
        );
    }

    private List<AvatarDashboardRowWidget> findDashboardRowWidgets(String rowId) {
        return jdbcTemplate.query(
            """
                select * from avatar_dashboard_widgets
                where row_id = ?
                order by column_position, widget_key
                """,
            (rs, rowNum) -> toDashboardRowWidget(rs),
            rowId
        );
    }

    private List<DashboardRowRecord> dashboardRowRecords() {
        seedDashboardRowsFromLegacyLayoutIfNeeded();
        return jdbcTemplate.query(
            "select * from avatar_dashboard_rows order by row_position, id",
            (rs, rowNum) -> toDashboardRowRecord(rs)
        );
    }

    private int nextDashboardRowPosition() {
        Integer position = jdbcTemplate.queryForObject(
            "select coalesce(max(row_position), -1) + 1 from avatar_dashboard_rows",
            Integer.class
        );
        return position == null ? 0 : position;
    }

    private int nextDashboardWidgetPosition(String rowId) {
        Integer position = jdbcTemplate.queryForObject(
            "select coalesce(max(column_position), -1) + 1 from avatar_dashboard_widgets where row_id = ?",
            Integer.class,
            rowId
        );
        return position == null ? 0 : position;
    }

    private int dashboardRowWidth(String rowId) {
        Integer width = jdbcTemplate.queryForObject(
            "select coalesce(sum(column_width), 0) from avatar_dashboard_widgets where row_id = ?",
            Integer.class,
            rowId
        );
        return width == null ? 0 : width;
    }

    private int indexOfRow(List<DashboardRowRecord> rows, String rowId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(rowId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("dashboard row not found: " + rowId);
    }

    private AvatarDashboardRowWidget moveDashboardWidgetWithinRow(AvatarDashboardRowWidget widget, int direction) {
        List<AvatarDashboardRowWidget> widgets = findDashboardRowWidgets(widget.rowId());
        int index = indexOfWidget(widgets, widget.id());
        int target = index + direction;
        if (target < 0 || target >= widgets.size()) {
            throw new IllegalArgumentException("widget cannot move outside row bounds");
        }
        AvatarDashboardRowWidget swap = widgets.get(target);
        Instant now = Instant.now();
        jdbcTemplate.update("update avatar_dashboard_widgets set column_position = ?, updated_at = ? where id = ?",
            swap.columnPosition(), now.toString(), widget.id());
        jdbcTemplate.update("update avatar_dashboard_widgets set column_position = ?, updated_at = ? where id = ?",
            widget.columnPosition(), now.toString(), swap.id());
        normalizeDashboardWidgets(widget.rowId());
        return findDashboardWidget(widget.id()).orElseThrow();
    }

    private AvatarDashboardRowWidget moveDashboardWidgetToAdjacentRow(AvatarDashboardRowWidget widget, int direction) {
        List<DashboardRowRecord> rows = dashboardRowRecords();
        int currentRowIndex = indexOfRow(rows, widget.rowId());
        int targetRowIndex = currentRowIndex + direction;
        if (targetRowIndex < 0 || targetRowIndex >= rows.size()) {
            throw new IllegalArgumentException("widget cannot move outside layout bounds");
        }
        String targetRowId = rows.get(targetRowIndex).id();
        if (dashboardRowWidth(targetRowId) + widget.columnWidth() > 12) {
            throw new IllegalArgumentException("target row does not have enough available width");
        }
        jdbcTemplate.update(
            "update avatar_dashboard_widgets set row_id = ?, column_position = ?, updated_at = ? where id = ?",
            targetRowId,
            nextDashboardWidgetPosition(targetRowId),
            Instant.now().toString(),
            widget.id()
        );
        normalizeDashboardWidgets(widget.rowId());
        normalizeDashboardWidgets(targetRowId);
        return findDashboardWidget(widget.id()).orElseThrow();
    }

    private int indexOfWidget(List<AvatarDashboardRowWidget> widgets, String widgetId) {
        for (int i = 0; i < widgets.size(); i++) {
            if (widgets.get(i).id().equals(widgetId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("dashboard widget not found: " + widgetId);
    }

    private void normalizeDashboardRows() {
        List<DashboardRowRecord> rows = jdbcTemplate.query(
            "select * from avatar_dashboard_rows order by row_position, id",
            (rs, rowNum) -> toDashboardRowRecord(rs)
        );
        Instant now = Instant.now();
        for (int i = 0; i < rows.size(); i++) {
            jdbcTemplate.update(
                "update avatar_dashboard_rows set row_position = ?, updated_at = ? where id = ?",
                i,
                now.toString(),
                rows.get(i).id()
            );
        }
    }

    private void normalizeDashboardWidgets() {
        for (DashboardRowRecord row : dashboardRowRecords()) {
            normalizeDashboardWidgets(row.id());
        }
    }

    private void normalizeDashboardWidgets(String rowId) {
        List<AvatarDashboardRowWidget> widgets = findDashboardRowWidgets(rowId);
        Instant now = Instant.now();
        for (int i = 0; i < widgets.size(); i++) {
            jdbcTemplate.update(
                "update avatar_dashboard_widgets set column_position = ?, updated_at = ? where id = ?",
                i,
                now.toString(),
                widgets.get(i).id()
            );
        }
    }

    private void seedDashboardRowsFromLegacyLayoutIfNeeded() {
        Integer rowCount = jdbcTemplate.queryForObject("select count(*) from avatar_dashboard_rows", Integer.class);
        if (rowCount != null && rowCount > 0) {
            return;
        }
        List<AvatarDashboardWidget> legacy = findDashboardLayout();
        if (legacy.isEmpty()) {
            return;
        }
        String rowId = null;
        int rowPosition = -1;
        int rowWidth = 0;
        for (AvatarDashboardWidget widget : legacy) {
            int width = widthForLegacySize(widget.size());
            if (rowId == null || rowWidth + width > 12) {
                rowId = insertDashboardRow(++rowPosition);
                rowWidth = 0;
            }
            insertDashboardRowWidget(rowId, widget, width, nextDashboardWidgetPosition(rowId));
            rowWidth += width;
        }
    }

    private void syncDashboardRowWidgetFromLegacy(AvatarDashboardWidget widget) {
        seedDashboardRowsFromLegacyLayoutIfNeeded();
        Optional<AvatarDashboardRowWidget> existing = findDashboardWidgetByKey(widget.widgetId());
        int width = widthForLegacySize(widget.size());
        if (existing.isPresent()) {
            jdbcTemplate.update(
                """
                    update avatar_dashboard_widgets
                    set column_width = ?, enabled = ?, collapsed = ?, settings_json = ?, updated_at = ?
                    where id = ?
                    """,
                width,
                widget.enabled() ? 1 : 0,
                widget.collapsed() ? 1 : 0,
                jsonMap(widget.settings()),
                Instant.now().toString(),
                existing.get().id()
            );
            return;
        }
        List<DashboardRowRecord> rows = dashboardRowRecords();
        String rowId = rows.isEmpty() ? insertDashboardRow(0) : rows.get(rows.size() - 1).id();
        if (dashboardRowWidth(rowId) + width > 12) {
            rowId = insertDashboardRow(nextDashboardRowPosition());
        }
        insertDashboardRowWidget(rowId, widget, width, nextDashboardWidgetPosition(rowId));
    }

    private String insertDashboardRow(int position) {
        String rowId = "row-" + UUID.randomUUID();
        jdbcTemplate.update(
            "insert into avatar_dashboard_rows (id, row_position, collapsed, settings_json, updated_at) values (?, ?, 0, '{}', ?)",
            rowId,
            position,
            Instant.now().toString()
        );
        return rowId;
    }

    private void insertDashboardRowWidget(String rowId, AvatarDashboardWidget widget, int width, int position) {
        jdbcTemplate.update(
            """
                insert into avatar_dashboard_widgets (
                    id, row_id, widget_key, column_position, column_width, enabled, collapsed, settings_json, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            "widget-" + UUID.randomUUID(),
            rowId,
            requireText(widget.widgetId(), "widget id"),
            position,
            width,
            widget.enabled() ? 1 : 0,
            widget.collapsed() ? 1 : 0,
            jsonMap(widget.settings()),
            Instant.now().toString()
        );
    }

    private int widthForLegacySize(String size) {
        if ("wide".equalsIgnoreCase(size)) {
            return 6;
        }
        if ("compact".equalsIgnoreCase(size)) {
            return 3;
        }
        return 4;
    }

    private int requireColumnWidth(int width) {
        if (width != 3 && width != 4 && width != 6 && width != 8 && width != 12) {
            throw new IllegalArgumentException("column width must be one of 3, 4, 6, 8, or 12");
        }
        return width;
    }

    private DashboardRowRecord toDashboardRowRecord(ResultSet rs) throws SQLException {
        return new DashboardRowRecord(
            rs.getString("id"),
            rs.getInt("row_position"),
            rs.getInt("collapsed") == 1,
            map(rs.getString("settings_json")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarDashboardRowWidget toDashboardRowWidget(ResultSet rs) throws SQLException {
        return new AvatarDashboardRowWidget(
            rs.getString("id"),
            rs.getString("row_id"),
            rs.getString("widget_key"),
            rs.getInt("column_position"),
            rs.getInt("column_width"),
            rs.getInt("enabled") == 1,
            rs.getInt("collapsed") == 1,
            map(rs.getString("settings_json")),
            instant(rs.getString("updated_at"))
        );
    }

    private record DashboardRowRecord(
        String id,
        int position,
        boolean collapsed,
        Map<String, Object> settings,
        Instant updatedAt
    ) {
    }

    public AvatarTodo saveTodo(AvatarTodo todo) {
        String id = id(todo.id());
        Instant now = Instant.now();
        Instant createdAt = todo.createdAt() == null ? now : todo.createdAt();
        Instant completedAt = todo.completedAt();
        jdbcTemplate.update(
            """
                insert into avatar_todos (
                    id, title, notes, status, priority, due_at, linked_project_id, linked_task_id, linked_output_id,
                    created_at, updated_at, completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    notes = excluded.notes,
                    status = excluded.status,
                    priority = excluded.priority,
                    due_at = excluded.due_at,
                    linked_project_id = excluded.linked_project_id,
                    linked_task_id = excluded.linked_task_id,
                    linked_output_id = excluded.linked_output_id,
                    updated_at = excluded.updated_at,
                    completed_at = excluded.completed_at
                """,
            id,
            requireText(todo.title(), "todo title"),
            todo.notes(),
            status(todo.status(), AvatarTodoStatus.OPEN).name(),
            priority(todo.priority()).name(),
            string(todo.dueAt()),
            todo.linkedProjectId(),
            todo.linkedTaskId(),
            todo.linkedOutputId(),
            createdAt.toString(),
            now.toString(),
            string(completedAt)
        );
        return findTodo(id).orElseThrow();
    }

    public Optional<AvatarTodo> findTodo(String id) {
        return jdbcTemplate.query(
            "select * from avatar_todos where id = ?",
            rs -> rs.next() ? Optional.of(toTodo(rs)) : Optional.empty(),
            id
        );
    }

    public List<AvatarTodo> findTodos() {
        return jdbcTemplate.query(
            "select * from avatar_todos order by coalesce(due_at, '9999-12-31T23:59:59Z'), created_at, title",
            (rs, rowNum) -> toTodo(rs)
        );
    }

    public void deleteTodo(String id) {
        jdbcTemplate.update("delete from avatar_todos where id = ?", id);
    }

    public AvatarDailyTask saveDailyTask(AvatarDailyTask task) {
        String id = id(task.id());
        Instant now = Instant.now();
        Instant createdAt = task.createdAt() == null ? now : task.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_daily_tasks (
                    id, task_date, title, notes, status, task_position, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    task_date = excluded.task_date,
                    title = excluded.title,
                    notes = excluded.notes,
                    status = excluded.status,
                    task_position = excluded.task_position,
                    updated_at = excluded.updated_at
                """,
            id,
            requireDate(task.taskDate()).toString(),
            requireText(task.title(), "daily task title"),
            task.notes(),
            status(task.status(), AvatarTaskStatus.PLANNED).name(),
            task.position(),
            createdAt.toString(),
            now.toString()
        );
        return findDailyTask(id).orElseThrow();
    }

    public Optional<AvatarDailyTask> findDailyTask(String id) {
        return jdbcTemplate.query(
            "select * from avatar_daily_tasks where id = ?",
            rs -> rs.next() ? Optional.of(toDailyTask(rs)) : Optional.empty(),
            id
        );
    }

    public List<AvatarDailyTask> findDailyTasks(LocalDate date) {
        if (date == null) {
            return jdbcTemplate.query(
                "select * from avatar_daily_tasks order by task_date, task_position, title",
                (rs, rowNum) -> toDailyTask(rs)
            );
        }
        return jdbcTemplate.query(
            "select * from avatar_daily_tasks where task_date = ? order by task_position, title",
            (rs, rowNum) -> toDailyTask(rs),
            date.toString()
        );
    }

    public AvatarCalendarItem saveCalendarItem(AvatarCalendarItem item) {
        String id = id(item.id());
        Instant now = Instant.now();
        Instant createdAt = item.createdAt() == null ? now : item.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_calendar_items (
                    id, title, notes, starts_at, ends_at, timezone, location, status, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    notes = excluded.notes,
                    starts_at = excluded.starts_at,
                    ends_at = excluded.ends_at,
                    timezone = excluded.timezone,
                    location = excluded.location,
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """,
            id,
            requireText(item.title(), "calendar title"),
            item.notes(),
            requireInstant(item.startsAt(), "calendar start").toString(),
            string(item.endsAt()),
            item.timezone(),
            item.location(),
            status(item.status(), AvatarCalendarStatus.SCHEDULED).name(),
            createdAt.toString(),
            now.toString()
        );
        return findCalendarItem(id).orElseThrow();
    }

    public Optional<AvatarCalendarItem> findCalendarItem(String id) {
        return jdbcTemplate.query(
            "select * from avatar_calendar_items where id = ?",
            rs -> rs.next() ? Optional.of(toCalendarItem(rs)) : Optional.empty(),
            id
        );
    }

    public List<AvatarCalendarItem> findCalendarItems() {
        return jdbcTemplate.query(
            "select * from avatar_calendar_items order by starts_at, title",
            (rs, rowNum) -> toCalendarItem(rs)
        );
    }

    public void deleteCalendarItem(String id) {
        jdbcTemplate.update("delete from avatar_calendar_items where id = ?", id);
    }

    public AvatarNote saveNote(AvatarNote note) {
        String id = id(note.id());
        Instant now = Instant.now();
        Instant createdAt = note.createdAt() == null ? now : note.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_notes (
                    id, title, body, tags_json, source_ref_json, archived, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    body = excluded.body,
                    tags_json = excluded.tags_json,
                    source_ref_json = excluded.source_ref_json,
                    archived = excluded.archived,
                    updated_at = excluded.updated_at
                """,
            id,
            requireText(note.title(), "note title"),
            note.body() == null ? "" : note.body(),
            jsonList(note.tags()),
            jsonMap(note.sourceRef()),
            note.archived() ? 1 : 0,
            createdAt.toString(),
            now.toString()
        );
        return findNote(id).orElseThrow();
    }

    public Optional<AvatarNote> findNote(String id) {
        return jdbcTemplate.query(
            "select * from avatar_notes where id = ?",
            rs -> rs.next() ? Optional.of(toNote(rs)) : Optional.empty(),
            id
        );
    }

    public List<AvatarNote> findNotes(boolean includeArchived) {
        if (includeArchived) {
            return jdbcTemplate.query(
                "select * from avatar_notes order by updated_at desc, title",
                (rs, rowNum) -> toNote(rs)
            );
        }
        return jdbcTemplate.query(
            "select * from avatar_notes where archived = 0 order by updated_at desc, title",
            (rs, rowNum) -> toNote(rs)
        );
    }

    public PlannerTask savePlannerTask(PlannerTask task) {
        String id = id(task.id());
        Instant now = Instant.now();
        Instant createdAt = task.createdAt() == null ? now : task.createdAt();
        PlannerTaskLink link = task.link() == null ? new PlannerTaskLink(null, null, null, null) : task.link();
        jdbcTemplate.update(
            """
                insert into avatar_planner_tasks (
                    id, title, notes, status, priority, starts_at, due_at, timezone, recurrence_json,
                    linked_project_id, linked_assignment_id, linked_job_id, linked_output_id,
                    created_at, updated_at, completed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    notes = excluded.notes,
                    status = excluded.status,
                    priority = excluded.priority,
                    starts_at = excluded.starts_at,
                    due_at = excluded.due_at,
                    timezone = excluded.timezone,
                    recurrence_json = excluded.recurrence_json,
                    linked_project_id = excluded.linked_project_id,
                    linked_assignment_id = excluded.linked_assignment_id,
                    linked_job_id = excluded.linked_job_id,
                    linked_output_id = excluded.linked_output_id,
                    updated_at = excluded.updated_at,
                    completed_at = excluded.completed_at
                """,
            id,
            requireText(task.title(), "planner task title"),
            task.notes(),
            status(task.status(), PlannerTaskStatus.PLANNED).name(),
            priority(task.priority()).name(),
            string(task.startsAt()),
            string(task.dueAt()),
            StringUtils.hasText(task.timezone()) ? task.timezone() : ZoneId.systemDefault().getId(),
            jsonRecurrence(task.recurrence()),
            link.projectId(),
            link.assignmentId(),
            link.jobId(),
            link.outputId(),
            createdAt.toString(),
            now.toString(),
            string(task.completedAt())
        );
        return findPlannerTask(id).orElseThrow();
    }

    public Optional<PlannerTask> findPlannerTask(String id) {
        return jdbcTemplate.query(
            "select * from avatar_planner_tasks where id = ?",
            rs -> rs.next() ? Optional.of(toPlannerTask(rs)) : Optional.empty(),
            id
        );
    }

    public List<PlannerTask> findPlannerTasks() {
        return jdbcTemplate.query(
            """
                select * from avatar_planner_tasks
                order by coalesce(due_at, starts_at, '9999-12-31T23:59:59Z'), created_at, title
                """,
            (rs, rowNum) -> toPlannerTask(rs)
        );
    }

    public PlannerSubtodo savePlannerSubtodo(PlannerSubtodo subtodo) {
        findPlannerTask(requireText(subtodo.taskId(), "planner task id"))
            .orElseThrow(() -> new IllegalArgumentException("planner task not found: " + subtodo.taskId()));
        String id = id(subtodo.id());
        Instant now = Instant.now();
        Instant createdAt = subtodo.createdAt() == null ? now : subtodo.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_planner_subtodos (
                    id, task_id, title, status, subtodo_position, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    status = excluded.status,
                    subtodo_position = excluded.subtodo_position,
                    updated_at = excluded.updated_at
                """,
            id,
            subtodo.taskId(),
            requireText(subtodo.title(), "planner subtodo title"),
            status(subtodo.status(), AvatarTodoStatus.OPEN).name(),
            subtodo.position(),
            createdAt.toString(),
            now.toString()
        );
        return findPlannerSubtodos(subtodo.taskId()).stream()
            .filter(saved -> saved.id().equals(id))
            .findFirst()
            .orElseThrow();
    }

    public List<PlannerSubtodo> findPlannerSubtodos(String taskId) {
        return jdbcTemplate.query(
            "select * from avatar_planner_subtodos where task_id = ? order by subtodo_position, created_at, title",
            (rs, rowNum) -> toPlannerSubtodo(rs),
            taskId
        );
    }

    public void linkPlannerTaskNote(String taskId, String noteId) {
        findPlannerTask(requireText(taskId, "planner task id"))
            .orElseThrow(() -> new IllegalArgumentException("planner task not found: " + taskId));
        findNote(requireText(noteId, "note id"))
            .orElseThrow(() -> new IllegalArgumentException("note not found: " + noteId));
        jdbcTemplate.update(
            """
                insert into avatar_planner_task_notes (task_id, note_id, created_at)
                values (?, ?, ?)
                on conflict(task_id, note_id) do nothing
                """,
            taskId,
            noteId,
            Instant.now().toString()
        );
    }

    public void replacePlannerCalendarProjection(String taskId, List<PlannerCalendarProjection> projections) {
        jdbcTemplate.update("delete from avatar_planner_calendar_projection where task_id = ?", taskId);
        for (PlannerCalendarProjection projection : projections == null ? List.<PlannerCalendarProjection>of() : projections) {
            String id = id(projection.id());
            Instant now = Instant.now();
            Instant createdAt = projection.createdAt() == null ? now : projection.createdAt();
            jdbcTemplate.update(
                """
                    insert into avatar_planner_calendar_projection (
                        id, task_id, occurrence_start, occurrence_end, status, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                id,
                taskId,
                requireInstant(projection.occurrenceStart(), "planner occurrence start").toString(),
                string(projection.occurrenceEnd()),
                status(projection.status(), PlannerTaskStatus.PLANNED).name(),
                createdAt.toString(),
                now.toString()
            );
        }
    }

    public List<PlannerCalendarProjection> findPlannerCalendarProjection(Instant from, Instant to) {
        if (from == null || to == null) {
            return jdbcTemplate.query(
                "select * from avatar_planner_calendar_projection order by occurrence_start, task_id",
                (rs, rowNum) -> toPlannerProjection(rs)
            );
        }
        return jdbcTemplate.query(
            """
                select * from avatar_planner_calendar_projection
                where occurrence_start >= ? and occurrence_start <= ?
                order by occurrence_start, task_id
                """,
            (rs, rowNum) -> toPlannerProjection(rs),
            from.toString(),
            to.toString()
        );
    }

    public AvatarFact upsertFact(AvatarFact fact) {
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into avatar_facts (namespace, fact_key, value_json, status, updated_at)
                values (?, ?, ?, ?, ?)
                on conflict(namespace, fact_key) do update set
                    value_json = excluded.value_json,
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """,
            requireText(fact.namespace(), "fact namespace"),
            requireText(fact.key(), "fact key"),
            jsonMap(fact.value()),
            status(fact.status(), AvatarFactStatus.ACTIVE).name(),
            updatedAt.toString()
        );
        return findFact(fact.namespace(), fact.key()).orElseThrow();
    }

    public Optional<AvatarFact> findFact(String namespace, String key) {
        return jdbcTemplate.query(
            "select * from avatar_facts where namespace = ? and fact_key = ?",
            rs -> rs.next() ? Optional.of(toFact(rs)) : Optional.empty(),
            namespace,
            key
        );
    }

    public List<AvatarFact> findFacts() {
        return jdbcTemplate.query(
            "select * from avatar_facts order by namespace, fact_key",
            (rs, rowNum) -> toFact(rs)
        );
    }

    public AvatarEvent appendEvent(AvatarEvent event) {
        String id = id(event.id());
        Instant occurredAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        jdbcTemplate.update(
            "insert into avatar_events (id, event_type, payload_json, occurred_at) values (?, ?, ?, ?)",
            id,
            requireText(event.eventType(), "event type"),
            jsonMap(event.payload()),
            occurredAt.toString()
        );
        return findEvent(id).orElseThrow();
    }

    public Optional<AvatarEvent> findEvent(String id) {
        return jdbcTemplate.query(
            "select * from avatar_events where id = ?",
            rs -> rs.next() ? Optional.of(toEvent(rs)) : Optional.empty(),
            id
        );
    }

    public List<AvatarEvent> findEvents() {
        return jdbcTemplate.query(
            "select * from avatar_events order by occurred_at, id",
            (rs, rowNum) -> toEvent(rs)
        );
    }

    private AvatarProfile toProfile(ResultSet rs) throws SQLException {
        return new AvatarProfile(
            rs.getString("id"),
            rs.getString("display_name"),
            rs.getString("timezone"),
            rs.getString("locale"),
            rs.getString("summary"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarPreference toPreference(ResultSet rs) throws SQLException {
        return new AvatarPreference(
            rs.getString("namespace"),
            rs.getString("preference_key"),
            map(rs.getString("value_json")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarDashboardWidget toDashboardWidget(ResultSet rs) throws SQLException {
        return new AvatarDashboardWidget(
            rs.getString("widget_id"),
            rs.getInt("widget_position"),
            rs.getString("widget_size"),
            rs.getInt("enabled") == 1,
            rs.getInt("collapsed") == 1,
            map(rs.getString("settings_json")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarTodo toTodo(ResultSet rs) throws SQLException {
        return new AvatarTodo(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("notes"),
            AvatarTodoStatus.valueOf(rs.getString("status")),
            AvatarPriority.valueOf(rs.getString("priority")),
            instant(rs.getString("due_at")),
            rs.getString("linked_project_id"),
            rs.getString("linked_task_id"),
            rs.getString("linked_output_id"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at")),
            instant(rs.getString("completed_at"))
        );
    }

    private AvatarDailyTask toDailyTask(ResultSet rs) throws SQLException {
        return new AvatarDailyTask(
            rs.getString("id"),
            LocalDate.parse(rs.getString("task_date")),
            rs.getString("title"),
            rs.getString("notes"),
            AvatarTaskStatus.valueOf(rs.getString("status")),
            rs.getInt("task_position"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarCalendarItem toCalendarItem(ResultSet rs) throws SQLException {
        return new AvatarCalendarItem(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("notes"),
            instant(rs.getString("starts_at")),
            instant(rs.getString("ends_at")),
            rs.getString("timezone"),
            rs.getString("location"),
            AvatarCalendarStatus.valueOf(rs.getString("status")),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarNote toNote(ResultSet rs) throws SQLException {
        return new AvatarNote(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("body"),
            list(rs.getString("tags_json")),
            map(rs.getString("source_ref_json")),
            rs.getInt("archived") == 1,
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private PlannerTask toPlannerTask(ResultSet rs) throws SQLException {
        return new PlannerTask(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("notes"),
            PlannerTaskStatus.valueOf(rs.getString("status")),
            AvatarPriority.valueOf(rs.getString("priority")),
            instant(rs.getString("starts_at")),
            instant(rs.getString("due_at")),
            rs.getString("timezone"),
            recurrence(rs.getString("recurrence_json")),
            new PlannerTaskLink(
                rs.getString("linked_project_id"),
                rs.getString("linked_assignment_id"),
                rs.getString("linked_job_id"),
                rs.getString("linked_output_id")
            ),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at")),
            instant(rs.getString("completed_at"))
        );
    }

    private PlannerSubtodo toPlannerSubtodo(ResultSet rs) throws SQLException {
        return new PlannerSubtodo(
            rs.getString("id"),
            rs.getString("task_id"),
            rs.getString("title"),
            AvatarTodoStatus.valueOf(rs.getString("status")),
            rs.getInt("subtodo_position"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private PlannerCalendarProjection toPlannerProjection(ResultSet rs) throws SQLException {
        return new PlannerCalendarProjection(
            rs.getString("id"),
            rs.getString("task_id"),
            instant(rs.getString("occurrence_start")),
            instant(rs.getString("occurrence_end")),
            PlannerTaskStatus.valueOf(rs.getString("status")),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarFact toFact(ResultSet rs) throws SQLException {
        return new AvatarFact(
            rs.getString("namespace"),
            rs.getString("fact_key"),
            map(rs.getString("value_json")),
            AvatarFactStatus.valueOf(rs.getString("status")),
            instant(rs.getString("updated_at"))
        );
    }

    private AvatarEvent toEvent(ResultSet rs) throws SQLException {
        return new AvatarEvent(
            rs.getString("id"),
            rs.getString("event_type"),
            map(rs.getString("payload_json")),
            instant(rs.getString("occurred_at"))
        );
    }

    private String id(String id) {
        return StringUtils.hasText(id) ? id : UUID.randomUUID().toString();
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private LocalDate requireDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("daily task date is required");
        }
        return value;
    }

    private Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private AvatarPriority priority(AvatarPriority value) {
        return value == null ? AvatarPriority.NORMAL : value;
    }

    private <T extends Enum<T>> T status(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private String string(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Instant instant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private String jsonMap(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Avatar JSON object", exception);
        }
    }

    private String jsonList(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Avatar JSON list", exception);
        }
    }

    private String jsonRecurrence(PlannerRecurrence value) {
        try {
            PlannerRecurrence recurrence = value == null
                ? new PlannerRecurrence(PlannerRecurrenceMode.NONE, 1, null, null, null, null, null, null)
                : value.normalized();
            return objectMapper.writeValueAsString(Map.of(
                "mode", recurrence.mode().name(),
                "interval", recurrence.interval(),
                "startDate", recurrence.startDate() == null ? "" : recurrence.startDate().toString(),
                "endDate", recurrence.endDate() == null ? "" : recurrence.endDate().toString(),
                "time", recurrence.time() == null ? "" : recurrence.time().toString(),
                "weekday", recurrence.weekday() == null ? "" : recurrence.weekday().name(),
                "monthDay", recurrence.monthDay() == null ? "" : recurrence.monthDay().toString(),
                "cron", recurrence.cron() == null ? "" : recurrence.cron()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize planner recurrence", exception);
        }
    }

    private PlannerRecurrence recurrence(String json) {
        if (!StringUtils.hasText(json) || "{}".equals(json.trim())) {
            return new PlannerRecurrence(PlannerRecurrenceMode.NONE, 1, null, null, null, null, null, null);
        }
        try {
            Map<String, Object> values = objectMapper.readValue(json, MAP);
            return new PlannerRecurrence(
                recurrenceMode(values.get("mode")),
                intValue(values.get("interval"), 1),
                localDate(values.get("startDate")),
                localDate(values.get("endDate")),
                localTime(values.get("time")),
                weekday(values.get("weekday")),
                intObject(values.get("monthDay")),
                stringValue(values.get("cron"))
            ).normalized();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse planner recurrence", exception);
        }
    }

    private PlannerRecurrenceMode recurrenceMode(Object value) {
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return PlannerRecurrenceMode.NONE;
        }
        return PlannerRecurrenceMode.valueOf(text);
    }

    private LocalDate localDate(Object value) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? LocalDate.parse(text) : null;
    }

    private LocalTime localTime(Object value) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? LocalTime.parse(text) : null;
    }

    private DayOfWeek weekday(Object value) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? DayOfWeek.valueOf(text) : null;
    }

    private Integer intObject(Object value) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? Integer.parseInt(text) : null;
    }

    private int intValue(Object value, int fallback) {
        Integer parsed = intObject(value);
        return parsed == null ? fallback : parsed;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> map(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return Map.copyOf(objectMapper.readValue(json, MAP));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse Avatar JSON object", exception);
        }
    }

    private List<String> list(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(json, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse Avatar JSON list", exception);
        }
    }
}
