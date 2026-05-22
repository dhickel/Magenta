package io.mindspice.magenta2.avatar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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
