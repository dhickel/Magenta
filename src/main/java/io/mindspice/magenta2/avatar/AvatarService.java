package io.mindspice.magenta2.avatar;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.mindspice.magenta2.avatar.dashboard.DashboardWidgetDefinition;
import io.mindspice.magenta2.avatar.dashboard.DashboardWidgetRegistry;
import io.mindspice.magenta2.avatar.dashboard.WidgetSettingsValidation;
import io.mindspice.magenta2.avatar.dashboard.WidgetSettingsValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AvatarService {
    private final AvatarRepository repository;
    private final DashboardWidgetRegistry widgetRegistry;

    public AvatarService(AvatarRepository repository) {
        this.repository = repository;
        this.widgetRegistry = DashboardWidgetRegistry.defaultRegistry();
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

    public List<UserDashboard> dashboards() {
        return repository.findDashboards();
    }

    public UserDashboard assistantDashboard() {
        return repository.assistantDashboard();
    }

    public UserDashboard dashboard(String dashboardId) {
        requireText(dashboardId, "dashboard id");
        return repository.findDashboard(dashboardId)
            .orElseThrow(() -> new IllegalArgumentException("dashboard not found: " + dashboardId));
    }

    public UserDashboard createDashboard(String name) {
        return repository.createDashboard(name);
    }

    public String dashboardIdForRow(String rowId) {
        return repository.dashboardIdForDashboardRow(rowId);
    }

    public String dashboardIdForWidget(String widgetId) {
        String dashboardId = repository.dashboardIdForDashboardWidget(widgetId);
        if (!StringUtils.hasText(dashboardId)) {
            throw new IllegalArgumentException("dashboard widget not found: " + widgetId);
        }
        return dashboardId;
    }

    public List<AvatarDashboardRow> dashboardRows() {
        return repository.findDashboardRows();
    }

    public List<AvatarDashboardRow> dashboardRows(String dashboardId) {
        return repository.findDashboardRows(dashboardId);
    }

    public AvatarDashboardRow addDashboardRow() {
        return repository.addDashboardRow();
    }

    public AvatarDashboardRow addDashboardRow(String dashboardId) {
        return repository.addDashboardRow(dashboardId);
    }

    public AvatarDashboardRow insertDashboardRowAfter(String rowId) {
        return repository.insertDashboardRowAfter(rowId);
    }

    public AvatarDashboardRow insertDashboardRowAfter(String dashboardId, String rowId) {
        return repository.insertDashboardRowAfter(dashboardId, rowId);
    }

    public AvatarDashboardRow moveDashboardRow(String rowId, int direction) {
        return repository.moveDashboardRow(rowId, direction);
    }

    public AvatarDashboardRow moveDashboardRow(String dashboardId, String rowId, int direction) {
        return repository.moveDashboardRow(dashboardId, rowId, direction);
    }

    public void removeDashboardRow(String rowId) {
        repository.removeDashboardRow(rowId);
    }

    public AvatarDashboardRowWidget addDashboardWidget(String rowId, String widgetKey, int columnWidth) {
        String dashboardId = repository.dashboardIdForDashboardRow(rowId);
        return addDashboardWidget(dashboardId, rowId, widgetKey, columnWidth);
    }

    public AvatarDashboardRowWidget addDashboardWidget(String dashboardId, String rowId, String widgetKey, int columnWidth) {
        DashboardWidgetDefinition definition = widgetRegistry.require(widgetKey);
        if (!definition.supportsWidth(columnWidth)) {
            throw new IllegalArgumentException("unsupported width for " + definition.title() + ": " + columnWidth);
        }
        if (definition.singleInstance() && dashboardHasWidgetType(dashboardId, widgetKey)) {
            throw new IllegalArgumentException("dashboard widget already exists: " + widgetKey);
        }
        return repository.addDashboardWidget(
            dashboardId,
            rowId,
            definition.type(),
            columnWidth,
            definition.settingsSchema().defaults()
        );
    }

    public AvatarDashboardRowWidget dashboardWidget(String widgetId) {
        requireText(widgetId, "widget id");
        return repository.findDashboardRowWidget(widgetId)
            .orElseThrow(() -> new IllegalArgumentException("dashboard widget not found: " + widgetId));
    }

    public AvatarDashboardRowWidget updateDashboardWidgetSettings(
        String dashboardId,
        String widgetId,
        Map<String, ?> submittedSettings
    ) {
        AvatarDashboardRowWidget widget = dashboardWidget(widgetId);
        String actualDashboardId = dashboardIdForWidget(widgetId);
        if (!actualDashboardId.equals(dashboardId)) {
            throw new IllegalArgumentException("dashboard widget not found on dashboard: " + widgetId);
        }
        DashboardWidgetDefinition definition = widgetRegistry.require(widget.widgetKey());
        WidgetSettingsValidation validation = validateDashboardWidgetSettings(definition.type(), submittedSettings);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join(" ", validation.errors()));
        }
        return repository.updateDashboardWidgetSettings(widgetId, validation.settings());
    }

    public WidgetSettingsValidation validateDashboardWidgetSettings(String widgetType, Map<String, ?> submittedSettings) {
        DashboardWidgetDefinition definition = widgetRegistry.require(widgetType);
        return WidgetSettingsValidator.validate(definition, submittedSettings);
    }

    public AvatarDashboardRowWidget resizeDashboardWidget(String widgetId, int columnWidth) {
        return repository.resizeDashboardWidget(widgetId, columnWidth);
    }

    public AvatarDashboardRowWidget cycleDashboardWidgetWidth(String widgetId) {
        return repository.cycleDashboardWidgetWidth(widgetId);
    }

    public AvatarDashboardRowWidget moveDashboardWidget(String widgetId, String direction) {
        return repository.moveDashboardWidget(widgetId, direction);
    }

    public void removeDashboardWidget(String widgetId) {
        repository.removeDashboardWidget(widgetId);
    }

    private boolean dashboardHasWidgetType(String dashboardId, String widgetType) {
        return dashboardRows(dashboardId).stream()
            .flatMap(row -> row.widgets().stream())
            .anyMatch(widget -> widgetType.equals(widget.widgetKey()));
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

    public PlannerTask savePlannerTask(PlannerTask task) {
        PlannerTask saved = repository.savePlannerTask(task);
        repository.replacePlannerCalendarProjection(saved.id(), project(saved, 60));
        return saved;
    }

    public PlannerTask plannerTask(String id) {
        requireText(id, "planner task id");
        return repository.findPlannerTask(id)
            .orElseThrow(() -> new IllegalArgumentException("planner task not found: " + id));
    }

    public List<PlannerTask> plannerTasks() {
        return repository.findPlannerTasks();
    }

    public PlannerSubtodo savePlannerSubtodo(PlannerSubtodo subtodo) {
        return repository.savePlannerSubtodo(subtodo);
    }

    public List<PlannerSubtodo> plannerSubtodos(String taskId) {
        requireText(taskId, "planner task id");
        return repository.findPlannerSubtodos(taskId);
    }

    public void linkPlannerTaskNote(String taskId, String noteId) {
        repository.linkPlannerTaskNote(taskId, noteId);
    }

    public List<PlannerCalendarProjection> plannerCalendarProjection(Instant from, Instant to) {
        return repository.findPlannerCalendarProjection(from, to);
    }

    public PlannerDayMap dayMap(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return repository.findPlannerDayMap(day).orElseGet(() -> repository.savePlannerDayMap(new PlannerDayMap(
            null,
            day,
            List.of(),
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null
        )));
    }

    public PlannerDayMap restartDay(LocalDate date) {
        PlannerDayMap current = dayMap(date);
        return repository.savePlannerDayMap(new PlannerDayMap(
            current.id(),
            current.mapDate(),
            current.topPriorityIds(),
            current.nowItemId(),
            current.nextItemId(),
            current.laterItemIds(),
            current.reviewNotes(),
            Instant.now(),
            current.reviewedAt(),
            current.createdAt(),
            current.updatedAt()
        ));
    }

    public PlannerDayMap reviewDay(LocalDate date, String notes) {
        PlannerDayMap current = dayMap(date);
        return repository.savePlannerDayMap(new PlannerDayMap(
            current.id(),
            current.mapDate(),
            current.topPriorityIds(),
            current.nowItemId(),
            current.nextItemId(),
            current.laterItemIds(),
            notes,
            current.restartedAt(),
            Instant.now(),
            current.createdAt(),
            current.updatedAt()
        ));
    }

    public PlannerTimeBlock saveTimeBlock(PlannerTimeBlock block) {
        return repository.savePlannerTimeBlock(block);
    }

    public List<PlannerTimeBlock> timeBlocks(LocalDate from, LocalDate to) {
        return repository.findPlannerTimeBlocks(from, to);
    }

    public PlannerReminder saveReminder(PlannerReminder reminder) {
        return repository.savePlannerReminder(reminder);
    }

    public List<PlannerReminder> reminders(Instant from, Instant to, boolean includeClosed) {
        return repository.findPlannerReminders(from, to, includeClosed);
    }

    public PlannerOccurrence updateOccurrence(String taskId, Instant occurrenceStart, String action, Instant snoozedUntil) {
        PlannerTask task = plannerTask(taskId);
        PlannerOccurrence current = repository.findPlannerOccurrence(taskId, occurrenceStart)
            .orElseGet(() -> repository.ensurePlannerOccurrence(new PlannerOccurrence(
                null,
                task.id(),
                occurrenceStart,
                task.dueAt(),
                "PROJECTED",
                null,
                null,
                null,
                null,
                null
            )));
        String normalized = requireAction(action);
        Instant now = Instant.now();
        return repository.savePlannerOccurrence(new PlannerOccurrence(
            current.id(),
            current.taskId(),
            current.occurrenceStart(),
            current.occurrenceEnd(),
            normalized,
            "SKIPPED".equals(normalized) ? now : current.skippedAt(),
            "SNOOZED".equals(normalized) ? (snoozedUntil == null ? now.plusSeconds(3600) : snoozedUntil) : current.snoozedUntil(),
            "RESTARTED".equals(normalized) ? now : current.restartedAt(),
            current.createdAt(),
            current.updatedAt()
        ));
    }

    public List<PlannerOccurrence> plannerOccurrences(Instant from, Instant to) {
        return repository.findPlannerOccurrences(from, to);
    }

    public PlannerTask quickCapture(String title, String notes) {
        requireText(title, "planner task title");
        return savePlannerTask(new PlannerTask(
            null,
            title.strip(),
            notes,
            PlannerTaskStatus.PLANNED,
            AvatarPriority.NORMAL,
            null,
            null,
            ZoneId.systemDefault().getId(),
            new PlannerRecurrence(PlannerRecurrenceMode.NONE, 1, null, null, null, null, null, null),
            new PlannerTaskLink(null, null, null, null),
            null,
            null,
            null
        ));
    }

    public TodayPlannerView todayPlanner(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        PlannerDayMap map = dayMap(day);
        List<PlannerTask> openTasks = plannerTasks().stream()
            .filter(task -> task.status() != PlannerTaskStatus.DONE && task.status() != PlannerTaskStatus.CANCELLED)
            .toList();
        Map<String, PlannerTask> byId = openTasks.stream().collect(Collectors.toMap(PlannerTask::id, task -> task, (a, b) -> a, LinkedHashMap::new));
        List<PlannerTask> top = orderedTasks(map.topPriorityIds(), byId);
        if (top.isEmpty()) {
            top = openTasks.stream()
                .filter(task -> task.priority() == AvatarPriority.URGENT || task.priority() == AvatarPriority.HIGH)
                .sorted(taskComparator())
                .limit(3)
                .toList();
        }
        List<PlannerTask> overdue = openTasks.stream()
            .filter(task -> task.dueAt() != null && task.dueAt().isBefore(day.atStartOfDay(ZoneId.systemDefault()).toInstant()))
            .sorted(taskComparator())
            .limit(6)
            .toList();
        List<PlannerTask> today = openTasks.stream()
            .filter(task -> occursOn(task, day))
            .sorted(taskComparator())
            .toList();
        List<PlannerTask> unscheduled = openTasks.stream()
            .filter(task -> task.startsAt() == null && task.dueAt() == null && noRecurrence(task))
            .sorted(taskComparator())
            .limit(8)
            .toList();
        return new TodayPlannerView(
            day,
            map,
            top,
            splitPhase(today, map.nowItemId(), 0),
            splitPhase(today, map.nextItemId(), 1),
            splitLater(today, map.laterItemIds()),
            overdue,
            unscheduled,
            timeBlocks(day, day),
            reminders(day.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(), false)
        );
    }

    public TasksRoutinesView tasksRoutines() {
        return tasksRoutines("ALL", "ALL", "ALL");
    }

    public TasksRoutinesView tasksRoutines(String status, String range, String recurrence) {
        String statusFilter = normalizePlannerStatusFilter(status);
        String rangeFilter = normalizePlannerRangeFilter(range);
        String recurrenceFilter = normalizePlannerRecurrenceFilter(recurrence);
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        List<PlannerTask> tasks = plannerTasks().stream()
            .filter(task -> matchesPlannerStatus(task, statusFilter))
            .filter(task -> matchesPlannerRange(task, rangeFilter, today, zone))
            .filter(task -> matchesPlannerRecurrence(task, recurrenceFilter))
            .toList();
        Map<String, List<PlannerSubtodo>> subtodos = tasks.stream()
            .collect(Collectors.toMap(PlannerTask::id, task -> plannerSubtodos(task.id()), (a, b) -> a, LinkedHashMap::new));
        List<String> taskIds = tasks.stream().map(PlannerTask::id).toList();
        return new TasksRoutinesView(
            tasks,
            subtodos,
            plannerOccurrences(null, null).stream()
                .filter(occurrence -> taskIds.contains(occurrence.taskId()))
                .toList(),
            reminders(null, null, false),
            statusFilter,
            rangeFilter,
            recurrenceFilter
        );
    }

    public CalendarScheduleView calendarSchedule(LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now() : start;
        LocalDate toDate = end == null ? from.plusDays(30) : end;
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(zone).toInstant();
        List<CalendarScheduleView.Entry> entries = new ArrayList<>();
        calendarItems().stream()
            .filter(item -> !item.startsAt().isBefore(fromInstant) && item.startsAt().isBefore(toInstant))
            .forEach(item -> entries.add(new CalendarScheduleView.Entry(
                "event", item.id(), item.title(), item.startsAt(), item.endsAt(), item.status().name(), item.location()
            )));
        timeBlocks(from, toDate).forEach(block -> entries.add(new CalendarScheduleView.Entry(
            "time_block", block.id(), block.title(), block.startsAt(), block.endsAt(), block.status(), block.sourceType()
        )));
        Map<String, PlannerOccurrence> occurrences = plannerOccurrences(fromInstant, toInstant).stream()
            .collect(Collectors.toMap(
                occurrence -> occurrence.taskId() + "\n" + occurrence.occurrenceStart(),
                occurrence -> occurrence,
                (left, right) -> right,
                LinkedHashMap::new
            ));
        plannerCalendarProjection(fromInstant, toInstant).forEach(projection -> {
            PlannerTask task = plannerTask(projection.taskId());
            PlannerOccurrence occurrence = occurrences.get(projection.taskId() + "\n" + projection.occurrenceStart());
            entries.add(new CalendarScheduleView.Entry(
                "recurrence",
                projection.taskId(),
                task.title(),
                projection.occurrenceStart(),
                occurrence == null || occurrence.occurrenceEnd() == null ? projection.occurrenceEnd() : occurrence.occurrenceEnd(),
                occurrence == null ? projection.status().name() : occurrence.status(),
                occurrence == null ? "task projection" : occurrenceCalendarMeta(occurrence)
            ));
        });
        reminders(fromInstant, toInstant, false).forEach(reminder -> entries.add(new CalendarScheduleView.Entry(
            "reminder", reminder.id(), reminder.title(), reminder.remindAt(), reminder.snoozedUntil(), reminder.status(), reminder.sourceType()
        )));
        entries.sort(Comparator.comparing(CalendarScheduleView.Entry::startsAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return new CalendarScheduleView(from, toDate, entries);
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

    public List<PlannerCalendarProjection> project(PlannerTask task, int days) {
        if (task == null) {
            return List.of();
        }
        PlannerRecurrence recurrence = task.recurrence() == null
            ? new PlannerRecurrence(PlannerRecurrenceMode.NONE, 1, null, null, null, null, null, null)
            : task.recurrence().normalized();
        Instant seed = task.startsAt() != null ? task.startsAt() : task.dueAt();
        if (seed == null) {
            return List.of();
        }
        ZoneId zone = StringUtils.hasText(task.timezone()) ? ZoneId.of(task.timezone()) : ZoneId.systemDefault();
        ZonedDateTime cursor = seed.atZone(zone);
        if (recurrence.startDate() != null) {
            LocalTime time = recurrence.time() == null ? cursor.toLocalTime() : recurrence.time();
            cursor = LocalDateTime.of(recurrence.startDate(), time).atZone(zone);
        }
        cursor = alignRecurrenceCursor(cursor, recurrence);
        Instant until = Instant.now().plusSeconds(Math.max(days, 1L) * 24L * 60L * 60L);
        List<PlannerCalendarProjection> result = new ArrayList<>();
        if (recurrence.mode() == PlannerRecurrenceMode.NONE || recurrence.mode() == PlannerRecurrenceMode.CRON) {
            result.add(projection(task, cursor.toInstant()));
            return result;
        }
        int interval = Math.max(recurrence.interval(), 1);
        while (!cursor.toInstant().isAfter(until) && result.size() < 64) {
            if (recurrence.endDate() != null && cursor.toLocalDate().isAfter(recurrence.endDate())) {
                break;
            }
            if (!cursor.toInstant().isBefore(Instant.now().minusSeconds(24 * 60 * 60))) {
                result.add(projection(task, cursor.toInstant()));
            }
            cursor = switch (recurrence.mode()) {
                case DAILY -> cursor.plusDays(interval);
                case WEEKLY -> cursor.plusWeeks(interval);
                case MONTHLY -> cursor.plusMonths(interval);
                default -> cursor.plusDays(interval);
            };
        }
        return result;
    }

    public AvatarSnapshot snapshot() {
        return new AvatarSnapshot(
            profile(),
            preferences(),
            dashboardLayout(),
            plannerTasks(),
            todos(),
            dailyTasks(null),
            calendarItems(),
            notes(false),
            facts(),
            events()
        );
    }

    private ZonedDateTime alignRecurrenceCursor(ZonedDateTime cursor, PlannerRecurrence recurrence) {
        if (recurrence.mode() == PlannerRecurrenceMode.WEEKLY && recurrence.weekday() != null) {
            while (cursor.getDayOfWeek() != recurrence.weekday()) {
                cursor = cursor.plusDays(1);
            }
        }
        if (recurrence.mode() == PlannerRecurrenceMode.MONTHLY && recurrence.monthDay() != null) {
            int day = Math.max(1, Math.min(recurrence.monthDay(), cursor.toLocalDate().lengthOfMonth()));
            cursor = cursor.withDayOfMonth(day);
        }
        return cursor;
    }

    private PlannerCalendarProjection projection(PlannerTask task, Instant start) {
        return new PlannerCalendarProjection(null, task.id(), start, task.dueAt(), task.status(), null, null);
    }

    private String requireAction(String action) {
        if (!StringUtils.hasText(action)) {
            throw new IllegalArgumentException("occurrence action is required");
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SKIPPED", "SNOOZED", "RESTARTED").contains(normalized)) {
            throw new IllegalArgumentException("unsupported occurrence action: " + action);
        }
        return normalized;
    }

    private Comparator<PlannerTask> taskComparator() {
        return Comparator
            .comparing((PlannerTask task) -> task.dueAt() == null ? task.startsAt() : task.dueAt(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PlannerTask::title, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private boolean occursOn(PlannerTask task, LocalDate day) {
        ZoneId zone = StringUtils.hasText(task.timezone()) ? ZoneId.of(task.timezone()) : ZoneId.systemDefault();
        if (task.startsAt() != null && task.startsAt().atZone(zone).toLocalDate().equals(day)) {
            return true;
        }
        if (task.dueAt() != null && task.dueAt().atZone(zone).toLocalDate().equals(day)) {
            return true;
        }
        return plannerCalendarProjection(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()).stream()
            .anyMatch(projection -> projection.taskId().equals(task.id()));
    }

    private String normalizePlannerStatusFilter(String status) {
        if (!StringUtils.hasText(status) || "ALL".equalsIgnoreCase(status)) {
            return "ALL";
        }
        return PlannerTaskStatus.valueOf(status.strip().toUpperCase(Locale.ROOT)).name();
    }

    private String normalizePlannerRangeFilter(String range) {
        if (!StringUtils.hasText(range)) {
            return "ALL";
        }
        return switch (range.strip().toUpperCase(Locale.ROOT)) {
            case "TODAY", "WEEK", "MONTH", "OVERDUE" -> range.strip().toUpperCase(Locale.ROOT);
            default -> "ALL";
        };
    }

    private String normalizePlannerRecurrenceFilter(String recurrence) {
        if (!StringUtils.hasText(recurrence)) {
            return "ALL";
        }
        return switch (recurrence.strip().toUpperCase(Locale.ROOT)) {
            case "RECURRING", "ONE_OFF" -> recurrence.strip().toUpperCase(Locale.ROOT);
            default -> "ALL";
        };
    }

    private boolean matchesPlannerStatus(PlannerTask task, String statusFilter) {
        return "ALL".equals(statusFilter) || task.status().name().equals(statusFilter);
    }

    private boolean matchesPlannerRange(PlannerTask task, String rangeFilter, LocalDate today, ZoneId zone) {
        if ("ALL".equals(rangeFilter)) {
            return true;
        }
        Instant reference = task.startsAt() == null ? task.dueAt() : task.startsAt();
        if (reference == null) {
            return false;
        }
        LocalDate taskDate = reference.atZone(zone).toLocalDate();
        return switch (rangeFilter) {
            case "TODAY" -> taskDate.equals(today);
            case "WEEK" -> !taskDate.isBefore(today) && !taskDate.isAfter(today.plusDays(7));
            case "MONTH" -> !taskDate.isBefore(today) && !taskDate.isAfter(today.plusDays(30));
            case "OVERDUE" -> reference.isBefore(today.atStartOfDay(zone).toInstant());
            default -> true;
        };
    }

    private boolean matchesPlannerRecurrence(PlannerTask task, String recurrenceFilter) {
        if ("ALL".equals(recurrenceFilter)) {
            return true;
        }
        boolean recurring = !noRecurrence(task);
        return "RECURRING".equals(recurrenceFilter) ? recurring : !recurring;
    }

    private String occurrenceCalendarMeta(PlannerOccurrence occurrence) {
        if (occurrence.snoozedUntil() != null) {
            return "task occurrence / snoozed until " + occurrence.snoozedUntil();
        }
        if (occurrence.skippedAt() != null) {
            return "task occurrence / skipped " + occurrence.skippedAt();
        }
        if (occurrence.restartedAt() != null) {
            return "task occurrence / restarted " + occurrence.restartedAt();
        }
        return "task occurrence";
    }

    private boolean noRecurrence(PlannerTask task) {
        return task.recurrence() == null || task.recurrence().mode() == null || task.recurrence().mode() == PlannerRecurrenceMode.NONE;
    }

    private List<PlannerTask> orderedTasks(List<String> ids, Map<String, PlannerTask> byId) {
        return ids == null ? List.of() : ids.stream()
            .map(byId::get)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private List<PlannerTask> splitPhase(List<PlannerTask> tasks, String preferredId, int fallbackIndex) {
        if (StringUtils.hasText(preferredId)) {
            PlannerTask preferred = tasks.stream().filter(task -> preferredId.equals(task.id())).findFirst().orElse(null);
            if (preferred != null) {
                return List.of(preferred);
            }
        }
        return tasks.size() > fallbackIndex ? List.of(tasks.get(fallbackIndex)) : List.of();
    }

    private List<PlannerTask> splitLater(List<PlannerTask> tasks, List<String> preferredIds) {
        Map<String, PlannerTask> byId = tasks.stream()
            .collect(Collectors.toMap(PlannerTask::id, task -> task, (a, b) -> a, LinkedHashMap::new));
        List<PlannerTask> preferred = orderedTasks(preferredIds, byId);
        if (!preferred.isEmpty()) {
            return preferred;
        }
        return tasks.stream().skip(2).limit(6).toList();
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
