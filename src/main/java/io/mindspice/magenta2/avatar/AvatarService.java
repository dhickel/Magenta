package io.mindspice.magenta2.avatar;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

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

    public AvatarTodo saveTodo(AvatarTodo todo) {
        return repository.saveTodo(todo);
    }

    public List<AvatarTodo> todos() {
        return repository.findTodos();
    }

    public void deleteTodo(String id) {
        repository.deleteTodo(id);
    }

    public AvatarDailyTask saveDailyTask(AvatarDailyTask task) {
        return repository.saveDailyTask(task);
    }

    public List<AvatarDailyTask> dailyTasks(LocalDate date) {
        return repository.findDailyTasks(date);
    }

    public AvatarCalendarItem saveCalendarItem(AvatarCalendarItem item) {
        return repository.saveCalendarItem(item);
    }

    public List<AvatarCalendarItem> calendarItems() {
        return repository.findCalendarItems();
    }

    public AvatarNote saveNote(AvatarNote note) {
        return repository.saveNote(note);
    }

    public List<AvatarNote> notes(boolean includeArchived) {
        return repository.findNotes(includeArchived);
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
}
