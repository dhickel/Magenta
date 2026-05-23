package io.mindspice.magenta2.avatar;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AvatarService {
    private final AvatarRepository repository;

    public AvatarService(AvatarRepository repository) {
        this.repository = repository;
    }

    public AvatarProfile profile() {
        return repository.findProfile().orElseGet(() -> repository.saveProfile(repository.defaultProfile()));
    }

    public AvatarProfile saveProfile(AvatarProfile profile) {
        return repository.saveProfile(profile);
    }

    public AvatarPreference upsertPreference(AvatarPreference preference) {
        return repository.upsertPreference(preference);
    }

    public List<AvatarPreference> preferences() {
        return repository.findPreferences();
    }

    public AvatarDashboardWidget saveDashboardWidget(AvatarDashboardWidget widget) {
        return repository.saveDashboardWidget(widget);
    }

    public List<AvatarDashboardWidget> dashboardLayout() {
        return repository.findDashboardLayout();
    }

    public List<AvatarDashboardRow> dashboardRows() {
        return repository.findDashboardRows();
    }

    public AvatarDashboardRow addDashboardRow() {
        return repository.addDashboardRow();
    }

    public AvatarDashboardRow moveDashboardRow(String rowId, int direction) {
        return repository.moveDashboardRow(rowId, direction);
    }

    public AvatarDashboardRowWidget addDashboardWidget(String rowId, String widgetKey, int columnWidth) {
        return repository.addDashboardWidget(rowId, widgetKey, columnWidth);
    }

    public AvatarDashboardRowWidget resizeDashboardWidget(String widgetId, int columnWidth) {
        return repository.resizeDashboardWidget(widgetId, columnWidth);
    }

    public AvatarDashboardRowWidget moveDashboardWidget(String widgetId, String direction) {
        return repository.moveDashboardWidget(widgetId, direction);
    }

    public void removeDashboardWidget(String widgetId) {
        repository.removeDashboardWidget(widgetId);
    }

    public AvatarTodo saveTodo(AvatarTodo todo) {
        return repository.saveTodo(todo);
    }

    public AvatarTodo todo(String id) {
        requireText(id, "todo id");
        return repository.findTodo(id).orElseThrow(() -> new IllegalArgumentException("todo not found: " + id));
    }

    public List<AvatarTodo> todos() {
        return repository.findTodos();
    }

    public AvatarTodo completeTodo(String id) {
        AvatarTodo current = todo(id);
        return repository.saveTodo(new AvatarTodo(
            current.id(),
            current.title(),
            current.notes(),
            AvatarTodoStatus.DONE,
            current.priority(),
            current.dueAt(),
            current.linkedProjectId(),
            current.linkedTaskId(),
            current.linkedOutputId(),
            current.createdAt(),
            current.updatedAt(),
            Instant.now()
        ));
    }

    public void deleteTodo(String id) {
        repository.deleteTodo(id);
    }

    public AvatarDailyTask saveDailyTask(AvatarDailyTask task) {
        return repository.saveDailyTask(task);
    }

    public AvatarDailyTask dailyTask(String id) {
        requireText(id, "daily task id");
        return repository.findDailyTask(id)
            .orElseThrow(() -> new IllegalArgumentException("daily task not found: " + id));
    }

    public List<AvatarDailyTask> dailyTasks(LocalDate date) {
        return repository.findDailyTasks(date);
    }

    public AvatarDailyTask completeDailyTask(String id) {
        AvatarDailyTask current = dailyTask(id);
        return repository.saveDailyTask(new AvatarDailyTask(
            current.id(),
            current.taskDate(),
            current.title(),
            current.notes(),
            AvatarTaskStatus.DONE,
            current.position(),
            current.createdAt(),
            current.updatedAt()
        ));
    }

    public AvatarCalendarItem saveCalendarItem(AvatarCalendarItem item) {
        return repository.saveCalendarItem(item);
    }

    public AvatarCalendarItem calendarItem(String id) {
        requireText(id, "calendar item id");
        return repository.findCalendarItem(id)
            .orElseThrow(() -> new IllegalArgumentException("calendar item not found: " + id));
    }

    public List<AvatarCalendarItem> calendarItems() {
        return repository.findCalendarItems();
    }

    public void deleteCalendarItem(String id) {
        calendarItem(id);
        repository.deleteCalendarItem(id);
    }

    public AvatarNote saveNote(AvatarNote note) {
        return repository.saveNote(note);
    }

    public AvatarNote note(String id) {
        requireText(id, "note id");
        return repository.findNote(id).orElseThrow(() -> new IllegalArgumentException("note not found: " + id));
    }

    public List<AvatarNote> notes(boolean includeArchived) {
        return repository.findNotes(includeArchived);
    }

    public AvatarNote appendNote(String id, String title, String body, List<String> tags) {
        requireText(body, "note body");
        if (StringUtils.hasText(id)) {
            AvatarNote current = note(id);
            String nextBody = current.body() == null || current.body().isBlank()
                ? body
                : current.body() + System.lineSeparator() + body;
            List<String> nextTags = tags == null || tags.isEmpty() ? current.tags() : tags;
            return repository.saveNote(new AvatarNote(
                current.id(),
                StringUtils.hasText(title) ? title.trim() : current.title(),
                nextBody,
                nextTags,
                current.sourceRef(),
                current.archived(),
                current.createdAt(),
                current.updatedAt()
            ));
        }
        return repository.saveNote(new AvatarNote(
            null,
            StringUtils.hasText(title) ? title.trim() : "Untitled note",
            body,
            tags == null ? List.of() : tags,
            java.util.Map.of("source", "avatar_tool"),
            false,
            null,
            null
        ));
    }

    public List<AvatarNote> searchNotes(String query, boolean includeArchived, int limit) {
        String normalized = StringUtils.hasText(query) ? query.trim().toLowerCase(Locale.ROOT) : "";
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        return notes(includeArchived).stream()
            .filter(note -> normalized.isEmpty() || matches(note, normalized))
            .limit(boundedLimit)
            .toList();
    }

    public AvatarFact upsertFact(AvatarFact fact) {
        return repository.upsertFact(fact);
    }

    public List<AvatarFact> facts() {
        return repository.findFacts();
    }

    public AvatarEvent appendEvent(AvatarEvent event) {
        return repository.appendEvent(event);
    }

    public List<AvatarEvent> events() {
        return repository.findEvents();
    }

    public AvatarSnapshot snapshot() {
        return new AvatarSnapshot(
            profile(),
            preferences(),
            dashboardLayout(),
            todos(),
            dailyTasks(null),
            calendarItems(),
            notes(false),
            facts(),
            events()
        );
    }

    private boolean matches(AvatarNote note, String query) {
        return contains(note.title(), query)
            || contains(note.body(), query)
            || (note.tags() != null && note.tags().stream().anyMatch(tag -> contains(tag, query)));
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
