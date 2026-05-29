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
import io.mindspice.magenta2.avatar.dashboard.DashboardWidgetRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class AvatarRepository {
    public static final String PROFILE_ID = "default";
    public static final String ASSISTANT_DASHBOARD_ID = "assistant";

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AvatarRepository(@Qualifier("avatarJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        migrateUserDashboardWidgetsIfNeeded();
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

    public List<UserDashboard> findDashboards() {
        ensureAssistantDashboard();
        return jdbcTemplate.query(
            "select * from user_dashboards order by dashboard_position, dashboard_name",
            (rs, rowNum) -> toUserDashboard(rs)
        );
    }

    public Optional<UserDashboard> findDashboard(String dashboardId) {
        ensureAssistantDashboard();
        return jdbcTemplate.query(
            "select * from user_dashboards where id = ?",
            rs -> rs.next() ? Optional.of(toUserDashboard(rs)) : Optional.empty(),
            requireText(dashboardId, "dashboard id")
        );
    }

    public UserDashboard assistantDashboard() {
        return findDashboard(ASSISTANT_DASHBOARD_ID).orElseThrow();
    }

    public UserDashboard createDashboard(String name) {
        String normalized = requireText(name, "dashboard name").strip();
        if (normalized.length() > 80) {
            throw new IllegalArgumentException("dashboard name must be 80 characters or less");
        }
        ensureAssistantDashboard();
        Integer duplicate = jdbcTemplate.queryForObject(
            "select count(*) from user_dashboards where lower(dashboard_name) = lower(?)",
            Integer.class,
            normalized
        );
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("dashboard already exists: " + normalized);
        }
        Instant now = Instant.now();
        String id = "dashboard-" + UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into user_dashboards (
                    id, dashboard_name, dashboard_position, default_dashboard, settings_json, created_at, updated_at
                )
                values (?, ?, ?, 0, '{}', ?, ?)
                """,
            id,
            normalized,
            nextUserDashboardPosition(),
            now.toString(),
            now.toString()
        );
        return findDashboard(id).orElseThrow();
    }

    public String dashboardIdForDashboardRow(String rowId) {
        String dashboardId = dashboardIdForRow(rowId);
        if (!StringUtils.hasText(dashboardId)) {
            throw new IllegalArgumentException("dashboard row not found: " + rowId);
        }
        return dashboardId;
    }

    public String dashboardIdForDashboardWidget(String widgetId) {
        return jdbcTemplate.query(
            "select dashboard_id from user_dashboard_widgets where id = ?",
            rs -> rs.next() ? rs.getString("dashboard_id") : null,
            requireText(widgetId, "widget id")
        );
    }

    public Optional<AvatarDashboardRowWidget> findDashboardRowWidget(String widgetId) {
        return findDashboardWidget(widgetId);
    }

    public List<AvatarDashboardRow> findDashboardRows() {
        return findDashboardRows(ASSISTANT_DASHBOARD_ID);
    }

    public List<AvatarDashboardRow> findDashboardRows(String dashboardId) {
        requireDashboard(dashboardId);
        List<DashboardRowRecord> rows = jdbcTemplate.query(
            "select * from user_dashboard_rows where dashboard_id = ? order by row_position, id",
            (rs, rowNum) -> toDashboardRowRecord(rs),
            dashboardId
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
        return addDashboardRow(ASSISTANT_DASHBOARD_ID);
    }

    public AvatarDashboardRow addDashboardRow(String dashboardId) {
        requireDashboard(dashboardId);
        int nextPosition = nextDashboardRowPosition(dashboardId);
        String id = "row-" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                insert into user_dashboard_rows (id, dashboard_id, row_position, collapsed, settings_json, updated_at)
                values (?, ?, ?, 0, '{}', ?)
                """,
            id,
            dashboardId,
            nextPosition,
            now.toString()
        );
        return findDashboardRow(id).orElseThrow();
    }

    public AvatarDashboardRow insertDashboardRowAfter(String rowId) {
        return insertDashboardRowAfter(dashboardIdForRow(rowId), rowId);
    }

    public AvatarDashboardRow insertDashboardRowAfter(String dashboardId, String rowId) {
        requireText(rowId, "row id");
        requireDashboard(dashboardId);
        List<DashboardRowRecord> rows = dashboardRowRecords(dashboardId);
        int index = indexOfRow(rows, rowId);
        if (index < 0) {
            throw new IllegalArgumentException("dashboard row not found: " + rowId);
        }
        int position = rows.get(index).position() + 1;
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                update user_dashboard_rows set row_position = row_position + 1, updated_at = ?
                where dashboard_id = ? and row_position >= ?
                """,
            now.toString(),
            dashboardId,
            position
        );
        String id = "row-" + UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into user_dashboard_rows (id, dashboard_id, row_position, collapsed, settings_json, updated_at)
                values (?, ?, ?, 0, '{}', ?)
                """,
            id,
            dashboardId,
            position,
            now.toString()
        );
        normalizeDashboardRows(dashboardId);
        return findDashboardRow(id).orElseThrow();
    }

    public AvatarDashboardRow moveDashboardRow(String rowId, int direction) {
        return moveDashboardRow(dashboardIdForRow(rowId), rowId, direction);
    }

    public AvatarDashboardRow moveDashboardRow(String dashboardId, String rowId, int direction) {
        requireText(rowId, "row id");
        requireDashboard(dashboardId);
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("row direction must be -1 or 1");
        }
        List<DashboardRowRecord> rows = dashboardRowRecords(dashboardId);
        int index = indexOfRow(rows, rowId);
        int target = index + direction;
        if (target < 0 || target >= rows.size()) {
            throw new IllegalArgumentException("row cannot move outside layout bounds");
        }
        DashboardRowRecord current = rows.get(index);
        DashboardRowRecord swap = rows.get(target);
        Instant now = Instant.now();
        jdbcTemplate.update("update user_dashboard_rows set row_position = ?, updated_at = ? where id = ?",
            swap.position(), now.toString(), current.id());
        jdbcTemplate.update("update user_dashboard_rows set row_position = ?, updated_at = ? where id = ?",
            current.position(), now.toString(), swap.id());
        normalizeDashboardRows(dashboardId);
        return findDashboardRow(rowId).orElseThrow();
    }

    public AvatarDashboardRowWidget addDashboardWidget(String rowId, String widgetKey, int columnWidth) {
        return addDashboardWidget(dashboardIdForRow(rowId), rowId, widgetKey, columnWidth);
    }

    public AvatarDashboardRowWidget addDashboardWidget(String dashboardId, String rowId, String widgetKey, int columnWidth) {
        return addDashboardWidget(
            dashboardId,
            rowId,
            widgetKey,
            columnWidth,
            DashboardWidgetRegistry.defaultRegistry().require(widgetKey).settingsSchema().defaults()
        );
    }

    public AvatarDashboardRowWidget addDashboardWidget(
        String dashboardId,
        String rowId,
        String widgetType,
        int columnWidth,
        Map<String, Object> settings
    ) {
        requireText(rowId, "row id");
        requireDashboard(dashboardId);
        requireText(widgetType, "widget type");
        int width = requireColumnWidth(columnWidth);
        findDashboardRow(rowId).orElseThrow(() -> new IllegalArgumentException("dashboard row not found: " + rowId));
        int usedWidth = dashboardRowWidth(rowId);
        if (usedWidth + width > 12) {
            throw new IllegalArgumentException("dashboard row width cannot exceed 12 columns");
        }
        String id = "widget-" + UUID.randomUUID();
        Instant now = Instant.now();
        String singleInstanceKey = DashboardWidgetRegistry.defaultRegistry().singleInstanceKey(widgetType);
        jdbcTemplate.update(
            """
                insert into user_dashboard_widgets (
                    id, dashboard_id, row_id, widget_key, widget_type, column_position, column_width, enabled,
                    collapsed, settings_json, single_instance_key, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, ?, ?)
                """,
            id,
            dashboardId,
            rowId,
            widgetType,
            widgetType,
            nextDashboardWidgetPosition(rowId),
            width,
            jsonMap(settings),
            singleInstanceKey,
            now.toString(),
            now.toString()
        );
        return findDashboardWidget(id).orElseThrow();
    }

    public AvatarDashboardRowWidget updateDashboardWidgetSettings(String widgetId, Map<String, Object> settings) {
        requireText(widgetId, "widget id");
        findDashboardWidget(widgetId).orElseThrow(() -> new IllegalArgumentException("dashboard widget not found: " + widgetId));
        jdbcTemplate.update(
            "update user_dashboard_widgets set settings_json = ?, updated_at = ? where id = ?",
            jsonMap(settings),
            Instant.now().toString(),
            widgetId
        );
        return findDashboardWidget(widgetId).orElseThrow();
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
            "update user_dashboard_widgets set column_width = ?, updated_at = ? where id = ?",
            width,
            Instant.now().toString(),
            widgetId
        );
        return findDashboardWidget(widgetId).orElseThrow();
    }

    public AvatarDashboardRowWidget cycleDashboardWidgetWidth(String widgetId) {
        AvatarDashboardRowWidget widget = findDashboardWidget(widgetId)
            .orElseThrow(() -> new IllegalArgumentException("dashboard widget not found: " + widgetId));
        int[] widths = {3, 4, 6, 8, 12};
        int next = widths[0];
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] == widget.columnWidth()) {
                next = widths[(i + 1) % widths.length];
                break;
            }
        }
        return resizeDashboardWidget(widgetId, next);
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
        String dashboardId = dashboardIdForRow(rowId);
        jdbcTemplate.update("delete from user_dashboard_rows where id = ?", rowId);
        normalizeDashboardRows(dashboardId);
    }

    public void removeDashboardWidget(String widgetId) {
        requireText(widgetId, "widget id");
        String rowId = findDashboardWidget(widgetId)
            .map(AvatarDashboardRowWidget::rowId)
            .orElseThrow(() -> new IllegalArgumentException("dashboard widget not found: " + widgetId));
        if (jdbcTemplate.update("delete from user_dashboard_widgets where id = ?", widgetId) == 0) {
            throw new IllegalArgumentException("dashboard widget not found: " + widgetId);
        }
        normalizeDashboardWidgets(rowId);
    }

    private Optional<AvatarDashboardRow> findDashboardRow(String rowId) {
        return jdbcTemplate.query(
            "select * from user_dashboard_rows where id = ?",
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
            "select * from user_dashboard_widgets where id = ?",
            rs -> rs.next() ? Optional.of(toDashboardRowWidget(rs)) : Optional.empty(),
            widgetId
        );
    }

    private Optional<AvatarDashboardRowWidget> findDashboardWidgetByKey(String dashboardId, String widgetKey) {
        return jdbcTemplate.query(
            "select * from user_dashboard_widgets where dashboard_id = ? and widget_key = ?",
            rs -> rs.next() ? Optional.of(toDashboardRowWidget(rs)) : Optional.empty(),
            dashboardId,
            widgetKey
        );
    }

    private List<AvatarDashboardRowWidget> findDashboardRowWidgets(String rowId) {
        return jdbcTemplate.query(
            """
                select * from user_dashboard_widgets
                where row_id = ?
                order by column_position, widget_key
                """,
            (rs, rowNum) -> toDashboardRowWidget(rs),
            rowId
        );
    }

    private List<DashboardRowRecord> dashboardRowRecords(String dashboardId) {
        requireDashboard(dashboardId);
        return jdbcTemplate.query(
            "select * from user_dashboard_rows where dashboard_id = ? order by row_position, id",
            (rs, rowNum) -> toDashboardRowRecord(rs),
            dashboardId
        );
    }

    private int nextDashboardRowPosition(String dashboardId) {
        Integer position = jdbcTemplate.queryForObject(
            "select coalesce(max(row_position), -1) + 1 from user_dashboard_rows where dashboard_id = ?",
            Integer.class,
            dashboardId
        );
        return position == null ? 0 : position;
    }

    private int nextDashboardWidgetPosition(String rowId) {
        Integer position = jdbcTemplate.queryForObject(
            "select coalesce(max(column_position), -1) + 1 from user_dashboard_widgets where row_id = ?",
            Integer.class,
            rowId
        );
        return position == null ? 0 : position;
    }

    private int dashboardRowWidth(String rowId) {
        Integer width = jdbcTemplate.queryForObject(
            "select coalesce(sum(column_width), 0) from user_dashboard_widgets where row_id = ?",
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
        jdbcTemplate.update("update user_dashboard_widgets set column_position = ?, updated_at = ? where id = ?",
            swap.columnPosition(), now.toString(), widget.id());
        jdbcTemplate.update("update user_dashboard_widgets set column_position = ?, updated_at = ? where id = ?",
            widget.columnPosition(), now.toString(), swap.id());
        normalizeDashboardWidgets(widget.rowId());
        return findDashboardWidget(widget.id()).orElseThrow();
    }

    private AvatarDashboardRowWidget moveDashboardWidgetToAdjacentRow(AvatarDashboardRowWidget widget, int direction) {
        List<DashboardRowRecord> rows = dashboardRowRecords(dashboardIdForRow(widget.rowId()));
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
            "update user_dashboard_widgets set row_id = ?, column_position = ?, updated_at = ? where id = ?",
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

    private void normalizeDashboardRows(String dashboardId) {
        List<DashboardRowRecord> rows = jdbcTemplate.query(
            "select * from user_dashboard_rows where dashboard_id = ? order by row_position, id",
            (rs, rowNum) -> toDashboardRowRecord(rs),
            dashboardId
        );
        Instant now = Instant.now();
        for (int i = 0; i < rows.size(); i++) {
            jdbcTemplate.update(
                "update user_dashboard_rows set row_position = ?, updated_at = ? where id = ?",
                i,
                now.toString(),
                rows.get(i).id()
            );
        }
    }

    private void normalizeDashboardWidgets(String rowId) {
        List<AvatarDashboardRowWidget> widgets = findDashboardRowWidgets(rowId);
        Instant now = Instant.now();
        for (int i = 0; i < widgets.size(); i++) {
            jdbcTemplate.update(
                "update user_dashboard_widgets set column_position = ?, updated_at = ? where id = ?",
                i,
                now.toString(),
                widgets.get(i).id()
            );
        }
    }

    private void seedDashboardRowsFromLegacyLayoutIfNeeded() {
        ensureAssistantDashboard();
    }

    private void syncDashboardRowWidgetFromLegacy(AvatarDashboardWidget widget) {
        ensureAssistantDashboard();
    }

    private void migrateUserDashboardWidgetsIfNeeded() {
        if (!tableExists("user_dashboard_widgets") || hasColumn("user_dashboard_widgets", "widget_type")) {
            return;
        }
        jdbcTemplate.execute("pragma foreign_keys = off");
        jdbcTemplate.execute(
            """
                create table if not exists user_dashboard_widgets_new (
                    id text primary key,
                    dashboard_id text not null,
                    row_id text not null,
                    widget_key text not null,
                    widget_type text not null,
                    instance_label text,
                    column_position integer not null,
                    column_width integer not null,
                    enabled integer not null default 1,
                    collapsed integer not null default 0,
                    settings_json text not null default '{}',
                    single_instance_key text,
                    created_at text not null,
                    updated_at text not null,
                    unique(dashboard_id, single_instance_key),
                    foreign key(dashboard_id) references user_dashboards(id) on delete cascade,
                    foreign key(row_id) references user_dashboard_rows(id) on delete cascade
                )
                """
        );
        jdbcTemplate.update(
            """
                insert into user_dashboard_widgets_new (
                    id, dashboard_id, row_id, widget_key, widget_type, instance_label, column_position,
                    column_width, enabled, collapsed, settings_json, single_instance_key, created_at, updated_at
                )
                select
                    id,
                    dashboard_id,
                    row_id,
                    widget_key,
                    widget_key,
                    null,
                    column_position,
                    column_width,
                    enabled,
                    collapsed,
                    settings_json,
                    null,
                    updated_at,
                    updated_at
                from user_dashboard_widgets
                """
        );
        for (String type : DashboardWidgetRegistry.defaultRegistry().definitions().stream()
            .filter(definition -> definition.singleInstance())
            .map(definition -> definition.type())
            .toList()) {
            jdbcTemplate.update(
                "update user_dashboard_widgets_new set single_instance_key = widget_type where widget_type = ?",
                type
            );
        }
        jdbcTemplate.execute("drop table user_dashboard_widgets");
        jdbcTemplate.execute("alter table user_dashboard_widgets_new rename to user_dashboard_widgets");
        jdbcTemplate.execute(
            """
                create index if not exists idx_user_dashboard_widgets_row
                    on user_dashboard_widgets(row_id, column_position)
                """
        );
        jdbcTemplate.execute(
            """
                create index if not exists idx_user_dashboard_widgets_dashboard_type
                    on user_dashboard_widgets(dashboard_id, widget_type)
                """
        );
        jdbcTemplate.execute(
            """
                create index if not exists idx_user_dashboard_widgets_dashboard_row_position
                    on user_dashboard_widgets(dashboard_id, row_id, column_position)
                """
        );
        jdbcTemplate.execute("pragma foreign_keys = on");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from sqlite_master where type = 'table' and name = ?",
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private boolean hasColumn(String tableName, String columnName) {
        return jdbcTemplate.queryForList("pragma table_info(" + tableName + ")").stream()
            .anyMatch(row -> columnName.equals(row.get("name")));
    }

    private void ensureAssistantDashboard() {
        Integer dashboardCount = jdbcTemplate.queryForObject(
            "select count(*) from user_dashboards where id = ?",
            Integer.class,
            ASSISTANT_DASHBOARD_ID
        );
        if (dashboardCount == null || dashboardCount == 0) {
            Instant now = Instant.now();
            jdbcTemplate.update(
                """
                    insert into user_dashboards (
                        id, dashboard_name, dashboard_position, default_dashboard, settings_json, created_at, updated_at
                    )
                    values (?, 'Assistant', 0, 1, '{}', ?, ?)
                    """,
                ASSISTANT_DASHBOARD_ID,
                now.toString(),
                now.toString()
            );
        }
        Integer rowCount = jdbcTemplate.queryForObject(
            "select count(*) from user_dashboard_rows where dashboard_id = ?",
            Integer.class,
            ASSISTANT_DASHBOARD_ID
        );
        if (rowCount != null && rowCount > 0) {
            return;
        }
        String rowOne = insertUserDashboardRow(ASSISTANT_DASHBOARD_ID, 0);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowOne, "today-planner", 6, 0);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowOne, "calendar-schedule", 6, 1);
        String rowTwo = insertUserDashboardRow(ASSISTANT_DASHBOARD_ID, 1);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowTwo, "tasks-routines", 6, 0);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowTwo, "notes", 6, 1);
        String rowThree = insertUserDashboardRow(ASSISTANT_DASHBOARD_ID, 2);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowThree, "habits-trackers", 6, 0);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowThree, "reminders-alerts", 6, 1);
        String rowFour = insertUserDashboardRow(ASSISTANT_DASHBOARD_ID, 3);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowFour, "system", 4, 0);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowFour, "dashboard-context", 4, 1);
        insertUserDashboardWidget(ASSISTANT_DASHBOARD_ID, rowFour, "recent-work", 4, 2);
    }

    private String insertUserDashboardRow(String dashboardId, int position) {
        String rowId = "row-" + UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into user_dashboard_rows (id, dashboard_id, row_position, collapsed, settings_json, updated_at)
                values (?, ?, ?, 0, '{}', ?)
                """,
            rowId,
            dashboardId,
            position,
            Instant.now().toString()
        );
        return rowId;
    }

    private void insertUserDashboardWidget(String dashboardId, String rowId, String widgetKey, int width, int position) {
        Instant now = Instant.now();
        String singleInstanceKey = DashboardWidgetRegistry.defaultRegistry().singleInstanceKey(widgetKey);
        jdbcTemplate.update(
            """
                insert into user_dashboard_widgets (
                    id, dashboard_id, row_id, widget_key, widget_type, column_position, column_width, enabled,
                    collapsed, settings_json, single_instance_key, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, ?, ?)
                """,
            "widget-" + UUID.randomUUID(),
            dashboardId,
            rowId,
            requireText(widgetKey, "widget id"),
            widgetKey,
            position,
            width,
            jsonMap(DashboardWidgetRegistry.defaultRegistry().require(widgetKey).settingsSchema().defaults()),
            singleInstanceKey,
            now.toString(),
            now.toString()
        );
    }

    private int requireColumnWidth(int width) {
        if (width < 1 || width > 12) {
            throw new IllegalArgumentException("column width must be between 1 and 12");
        }
        return width;
    }

    private void requireDashboard(String dashboardId) {
        findDashboard(requireText(dashboardId, "dashboard id"))
            .orElseThrow(() -> new IllegalArgumentException("dashboard not found: " + dashboardId));
    }

    private String dashboardIdForRow(String rowId) {
        requireText(rowId, "row id");
        return jdbcTemplate.query(
            "select dashboard_id from user_dashboard_rows where id = ?",
            rs -> rs.next() ? rs.getString("dashboard_id") : null,
            rowId
        );
    }

    private int nextUserDashboardPosition() {
        Integer position = jdbcTemplate.queryForObject(
            "select coalesce(max(dashboard_position), -1) + 1 from user_dashboards",
            Integer.class
        );
        return position == null ? 0 : position;
    }

    private UserDashboard toUserDashboard(ResultSet rs) throws SQLException {
        return new UserDashboard(
            rs.getString("id"),
            rs.getString("dashboard_name"),
            rs.getInt("dashboard_position"),
            rs.getInt("default_dashboard") == 1,
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
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
            rs.getString("widget_type"),
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

    public AvatarHabit saveHabit(AvatarHabit habit) {
        String id = id(habit.id());
        Instant now = Instant.now();
        Instant createdAt = habit.createdAt() == null ? now : habit.createdAt();
        boolean archived = habit.archived();
        jdbcTemplate.update(
            """
                insert into avatar_habits (
                    id, title, notes, habit_type, period, target_quantity, target_unit, display_days_json,
                    start_time, end_time, streak_enabled, archived, created_at, updated_at, archived_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    notes = excluded.notes,
                    habit_type = excluded.habit_type,
                    period = excluded.period,
                    target_quantity = excluded.target_quantity,
                    target_unit = excluded.target_unit,
                    display_days_json = excluded.display_days_json,
                    start_time = excluded.start_time,
                    end_time = excluded.end_time,
                    streak_enabled = excluded.streak_enabled,
                    archived = excluded.archived,
                    updated_at = excluded.updated_at,
                    archived_at = excluded.archived_at
                """,
            id,
            requireText(habit.title(), "habit title"),
            habit.notes(),
            normalizeChoice(habit.habitType(), "BUILD", List.of("BUILD", "QUIT")),
            normalizeChoice(habit.period(), "DAILY", List.of("DAILY", "WEEKLY", "MONTHLY")),
            habit.targetQuantity() <= 0 ? 1.0 : habit.targetQuantity(),
            StringUtils.hasText(habit.targetUnit()) ? habit.targetUnit().strip() : "times",
            jsonList(habit.displayDays()),
            string(habit.startTime()),
            string(habit.endTime()),
            habit.streakEnabled() ? 1 : 0,
            archived ? 1 : 0,
            createdAt.toString(),
            now.toString(),
            archived ? string(habit.archivedAt() == null ? now : habit.archivedAt()) : null
        );
        return findHabit(id).orElseThrow();
    }

    public Optional<AvatarHabit> findHabit(String id) {
        return jdbcTemplate.query(
            "select * from avatar_habits where id = ?",
            rs -> rs.next() ? Optional.of(toHabit(rs)) : Optional.empty(),
            requireText(id, "habit id")
        );
    }

    public List<AvatarHabit> findHabits(boolean includeArchived) {
        if (includeArchived) {
            return jdbcTemplate.query(
                "select * from avatar_habits order by archived, title",
                (rs, rowNum) -> toHabit(rs)
            );
        }
        return jdbcTemplate.query(
            "select * from avatar_habits where archived = 0 order by title",
            (rs, rowNum) -> toHabit(rs)
        );
    }

    public AvatarHabitLog saveHabitLog(AvatarHabitLog log) {
        findHabit(requireText(log.habitId(), "habit id"))
            .orElseThrow(() -> new IllegalArgumentException("habit not found: " + log.habitId()));
        String id = id(log.id());
        Instant now = Instant.now();
        Instant createdAt = log.createdAt() == null ? now : log.createdAt();
        String status = normalizeChoice(log.status(), "LOGGED", List.of("LOGGED", "SKIPPED", "RESTARTED"));
        jdbcTemplate.update(
            """
                insert into avatar_habit_logs (
                    id, habit_id, log_date, quantity, status, notes, skipped_at, restarted_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(habit_id, log_date) do update set
                    quantity = excluded.quantity,
                    status = excluded.status,
                    notes = excluded.notes,
                    skipped_at = excluded.skipped_at,
                    restarted_at = excluded.restarted_at,
                    updated_at = excluded.updated_at
                """,
            id,
            log.habitId(),
            requireDate(log.logDate()).toString(),
            Math.max(log.quantity(), 0.0),
            status,
            log.notes(),
            string(log.skippedAt()),
            string(log.restartedAt()),
            createdAt.toString(),
            now.toString()
        );
        return findHabitLog(log.habitId(), log.logDate()).orElseThrow();
    }

    public Optional<AvatarHabitLog> findHabitLog(String habitId, LocalDate logDate) {
        return jdbcTemplate.query(
            "select * from avatar_habit_logs where habit_id = ? and log_date = ?",
            rs -> rs.next() ? Optional.of(toHabitLog(rs)) : Optional.empty(),
            requireText(habitId, "habit id"),
            requireDate(logDate).toString()
        );
    }

    public List<AvatarHabitLog> findHabitLogs(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return jdbcTemplate.query(
                "select * from avatar_habit_logs order by log_date desc, habit_id",
                (rs, rowNum) -> toHabitLog(rs)
            );
        }
        return jdbcTemplate.query(
            """
                select * from avatar_habit_logs
                where log_date >= ? and log_date <= ?
                order by log_date desc, habit_id
                """,
            (rs, rowNum) -> toHabitLog(rs),
            from.toString(),
            to.toString()
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
            ensurePlannerOccurrence(new PlannerOccurrence(
                null,
                taskId,
                projection.occurrenceStart(),
                projection.occurrenceEnd(),
                "PROJECTED",
                null,
                null,
                null,
                null,
                null
            ));
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

    public PlannerDayMap savePlannerDayMap(PlannerDayMap dayMap) {
        String id = id(dayMap.id());
        Instant now = Instant.now();
        Instant createdAt = dayMap.createdAt() == null ? now : dayMap.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_planner_day_maps (
                    id, map_date, top_priority_ids_json, now_item_id, next_item_id, later_item_ids_json,
                    review_notes, restarted_at, reviewed_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(map_date) do update set
                    top_priority_ids_json = excluded.top_priority_ids_json,
                    now_item_id = excluded.now_item_id,
                    next_item_id = excluded.next_item_id,
                    later_item_ids_json = excluded.later_item_ids_json,
                    review_notes = excluded.review_notes,
                    restarted_at = excluded.restarted_at,
                    reviewed_at = excluded.reviewed_at,
                    updated_at = excluded.updated_at
                """,
            id,
            requireDate(dayMap.mapDate()).toString(),
            jsonList(dayMap.topPriorityIds()),
            dayMap.nowItemId(),
            dayMap.nextItemId(),
            jsonList(dayMap.laterItemIds()),
            dayMap.reviewNotes(),
            string(dayMap.restartedAt()),
            string(dayMap.reviewedAt()),
            createdAt.toString(),
            now.toString()
        );
        return findPlannerDayMap(dayMap.mapDate()).orElseThrow();
    }

    public Optional<PlannerDayMap> findPlannerDayMap(LocalDate date) {
        return jdbcTemplate.query(
            "select * from avatar_planner_day_maps where map_date = ?",
            rs -> rs.next() ? Optional.of(toPlannerDayMap(rs)) : Optional.empty(),
            requireDate(date).toString()
        );
    }

    public PlannerTimeBlock savePlannerTimeBlock(PlannerTimeBlock block) {
        String id = id(block.id());
        Instant now = Instant.now();
        Instant createdAt = block.createdAt() == null ? now : block.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_planner_time_blocks (
                    id, block_date, title, starts_at, ends_at, source_type, source_id, status, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    block_date = excluded.block_date,
                    title = excluded.title,
                    starts_at = excluded.starts_at,
                    ends_at = excluded.ends_at,
                    source_type = excluded.source_type,
                    source_id = excluded.source_id,
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """,
            id,
            requireDate(block.blockDate()).toString(),
            requireText(block.title(), "time block title"),
            requireInstant(block.startsAt(), "time block start").toString(),
            string(block.endsAt()),
            block.sourceType(),
            block.sourceId(),
            StringUtils.hasText(block.status()) ? block.status() : "PLANNED",
            createdAt.toString(),
            now.toString()
        );
        return findPlannerTimeBlock(id).orElseThrow();
    }

    public Optional<PlannerTimeBlock> findPlannerTimeBlock(String id) {
        return jdbcTemplate.query(
            "select * from avatar_planner_time_blocks where id = ?",
            rs -> rs.next() ? Optional.of(toPlannerTimeBlock(rs)) : Optional.empty(),
            requireText(id, "time block id")
        );
    }

    public List<PlannerTimeBlock> findPlannerTimeBlocks(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return jdbcTemplate.query(
                "select * from avatar_planner_time_blocks order by block_date, starts_at, title",
                (rs, rowNum) -> toPlannerTimeBlock(rs)
            );
        }
        return jdbcTemplate.query(
            """
                select * from avatar_planner_time_blocks
                where block_date >= ? and block_date <= ?
                order by block_date, starts_at, title
                """,
            (rs, rowNum) -> toPlannerTimeBlock(rs),
            from.toString(),
            to.toString()
        );
    }

    public PlannerReminder savePlannerReminder(PlannerReminder reminder) {
        String id = id(reminder.id());
        Instant now = Instant.now();
        Instant createdAt = reminder.createdAt() == null ? now : reminder.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_planner_reminders (
                    id, title, notes, remind_at, status, source_type, source_id, snoozed_until, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    title = excluded.title,
                    notes = excluded.notes,
                    remind_at = excluded.remind_at,
                    status = excluded.status,
                    source_type = excluded.source_type,
                    source_id = excluded.source_id,
                    snoozed_until = excluded.snoozed_until,
                    updated_at = excluded.updated_at
                """,
            id,
            requireText(reminder.title(), "reminder title"),
            reminder.notes(),
            requireInstant(reminder.remindAt(), "reminder time").toString(),
            StringUtils.hasText(reminder.status()) ? reminder.status() : "OPEN",
            reminder.sourceType(),
            reminder.sourceId(),
            string(reminder.snoozedUntil()),
            createdAt.toString(),
            now.toString()
        );
        return findPlannerReminder(id).orElseThrow();
    }

    public Optional<PlannerReminder> findPlannerReminder(String id) {
        return jdbcTemplate.query(
            "select * from avatar_planner_reminders where id = ?",
            rs -> rs.next() ? Optional.of(toPlannerReminder(rs)) : Optional.empty(),
            requireText(id, "reminder id")
        );
    }

    public List<PlannerReminder> findPlannerReminders(Instant from, Instant to, boolean includeClosed) {
        String statusFilter = includeClosed ? "" : " and status in ('OPEN', 'SNOOZED')";
        if (from == null || to == null) {
            return jdbcTemplate.query(
                "select * from avatar_planner_reminders where 1 = 1" + statusFilter + " order by remind_at, title",
                (rs, rowNum) -> toPlannerReminder(rs)
            );
        }
        return jdbcTemplate.query(
            "select * from avatar_planner_reminders where remind_at >= ? and remind_at <= ?" + statusFilter
                + " order by remind_at, title",
            (rs, rowNum) -> toPlannerReminder(rs),
            from.toString(),
            to.toString()
        );
    }

    public PlannerOccurrence ensurePlannerOccurrence(PlannerOccurrence occurrence) {
        Optional<PlannerOccurrence> existing = findPlannerOccurrence(occurrence.taskId(), occurrence.occurrenceStart());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        return savePlannerOccurrence(occurrence);
    }

    public PlannerOccurrence savePlannerOccurrence(PlannerOccurrence occurrence) {
        findPlannerTask(requireText(occurrence.taskId(), "planner task id"))
            .orElseThrow(() -> new IllegalArgumentException("planner task not found: " + occurrence.taskId()));
        String id = id(occurrence.id());
        Instant now = Instant.now();
        Instant createdAt = occurrence.createdAt() == null ? now : occurrence.createdAt();
        jdbcTemplate.update(
            """
                insert into avatar_planner_occurrences (
                    id, task_id, occurrence_start, occurrence_end, status, skipped_at, snoozed_until,
                    restarted_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(task_id, occurrence_start) do update set
                    occurrence_end = excluded.occurrence_end,
                    status = excluded.status,
                    skipped_at = excluded.skipped_at,
                    snoozed_until = excluded.snoozed_until,
                    restarted_at = excluded.restarted_at,
                    updated_at = excluded.updated_at
                """,
            id,
            occurrence.taskId(),
            requireInstant(occurrence.occurrenceStart(), "occurrence start").toString(),
            string(occurrence.occurrenceEnd()),
            StringUtils.hasText(occurrence.status()) ? occurrence.status() : "PROJECTED",
            string(occurrence.skippedAt()),
            string(occurrence.snoozedUntil()),
            string(occurrence.restartedAt()),
            createdAt.toString(),
            now.toString()
        );
        return findPlannerOccurrence(occurrence.taskId(), occurrence.occurrenceStart()).orElseThrow();
    }

    public Optional<PlannerOccurrence> findPlannerOccurrence(String taskId, Instant occurrenceStart) {
        return jdbcTemplate.query(
            "select * from avatar_planner_occurrences where task_id = ? and occurrence_start = ?",
            rs -> rs.next() ? Optional.of(toPlannerOccurrence(rs)) : Optional.empty(),
            requireText(taskId, "planner task id"),
            requireInstant(occurrenceStart, "occurrence start").toString()
        );
    }

    public List<PlannerOccurrence> findPlannerOccurrences(Instant from, Instant to) {
        if (from == null || to == null) {
            return jdbcTemplate.query(
                "select * from avatar_planner_occurrences order by occurrence_start, task_id",
                (rs, rowNum) -> toPlannerOccurrence(rs)
            );
        }
        return jdbcTemplate.query(
            """
                select * from avatar_planner_occurrences
                where occurrence_start >= ? and occurrence_start <= ?
                order by occurrence_start, task_id
                """,
            (rs, rowNum) -> toPlannerOccurrence(rs),
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

    private AvatarHabit toHabit(ResultSet rs) throws SQLException {
        return new AvatarHabit(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("notes"),
            rs.getString("habit_type"),
            rs.getString("period"),
            rs.getDouble("target_quantity"),
            rs.getString("target_unit"),
            list(rs.getString("display_days_json")),
            time(rs.getString("start_time")),
            time(rs.getString("end_time")),
            rs.getInt("streak_enabled") == 1,
            rs.getInt("archived") == 1,
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at")),
            instant(rs.getString("archived_at"))
        );
    }

    private AvatarHabitLog toHabitLog(ResultSet rs) throws SQLException {
        return new AvatarHabitLog(
            rs.getString("id"),
            rs.getString("habit_id"),
            LocalDate.parse(rs.getString("log_date")),
            rs.getDouble("quantity"),
            rs.getString("status"),
            rs.getString("notes"),
            instant(rs.getString("skipped_at")),
            instant(rs.getString("restarted_at")),
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

    private PlannerDayMap toPlannerDayMap(ResultSet rs) throws SQLException {
        return new PlannerDayMap(
            rs.getString("id"),
            LocalDate.parse(rs.getString("map_date")),
            list(rs.getString("top_priority_ids_json")),
            rs.getString("now_item_id"),
            rs.getString("next_item_id"),
            list(rs.getString("later_item_ids_json")),
            rs.getString("review_notes"),
            instant(rs.getString("restarted_at")),
            instant(rs.getString("reviewed_at")),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private PlannerTimeBlock toPlannerTimeBlock(ResultSet rs) throws SQLException {
        return new PlannerTimeBlock(
            rs.getString("id"),
            LocalDate.parse(rs.getString("block_date")),
            rs.getString("title"),
            instant(rs.getString("starts_at")),
            instant(rs.getString("ends_at")),
            rs.getString("source_type"),
            rs.getString("source_id"),
            rs.getString("status"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private PlannerReminder toPlannerReminder(ResultSet rs) throws SQLException {
        return new PlannerReminder(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("notes"),
            instant(rs.getString("remind_at")),
            rs.getString("status"),
            rs.getString("source_type"),
            rs.getString("source_id"),
            instant(rs.getString("snoozed_until")),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private PlannerOccurrence toPlannerOccurrence(ResultSet rs) throws SQLException {
        return new PlannerOccurrence(
            rs.getString("id"),
            rs.getString("task_id"),
            instant(rs.getString("occurrence_start")),
            instant(rs.getString("occurrence_end")),
            rs.getString("status"),
            instant(rs.getString("skipped_at")),
            instant(rs.getString("snoozed_until")),
            instant(rs.getString("restarted_at")),
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

    private String normalizeChoice(String value, String fallback, List<String> allowed) {
        String normalized = StringUtils.hasText(value) ? value.strip().toUpperCase(Locale.ROOT) : fallback;
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("unsupported value: " + value);
        }
        return normalized;
    }

    private String string(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String string(LocalTime time) {
        return time == null ? null : time.toString();
    }

    private Instant instant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private LocalTime time(String value) {
        return StringUtils.hasText(value) ? LocalTime.parse(value) : null;
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
