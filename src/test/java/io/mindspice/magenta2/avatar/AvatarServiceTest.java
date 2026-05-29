package io.mindspice.magenta2.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AvatarServiceTest {
    private AvatarService service;

    @BeforeEach
    void setUp() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        new AvatarSchemaInitializer(dataSource).initialize();
        service = new AvatarService(new AvatarRepository(new JdbcTemplate(dataSource), new ObjectMapper()));
    }

    @Test
    void createsDefaultProfileForEmptyStore() {
        AvatarProfile profile = service.profile();

        assertThat(profile.id()).isEqualTo(AvatarRepository.PROFILE_ID);
        assertThat(profile.displayName()).isEqualTo("Avatar");
        assertThat(profile.timezone()).isNotBlank();
    }

    @Test
    void snapshotComposesEmptyAndSavedState() {
        service.upsertPreference(new AvatarPreference("assistant", "tone", Map.of("value", "brief"), null));
        service.appendEvent(new AvatarEvent("event-1", "profile.created", Map.of("source", "test"), null));

        AvatarSnapshot snapshot = service.snapshot();

        assertThat(snapshot.profile().displayName()).isEqualTo("Avatar");
        assertThat(snapshot.preferences()).hasSize(1);
        assertThat(snapshot.dashboardLayout()).isEmpty();
        assertThat(snapshot.plannerTasks()).isEmpty();
        assertThat(snapshot.todos()).isEmpty();
        assertThat(snapshot.events()).hasSize(1);
    }

    @Test
    void plannerTasksProjectFriendlyRecurrence() {
        PlannerTask task = service.savePlannerTask(new PlannerTask(
            null,
            "Water plants",
            null,
            PlannerTaskStatus.PLANNED,
            AvatarPriority.NORMAL,
            Instant.now().plusSeconds(3600),
            null,
            "UTC",
            new PlannerRecurrence(
                PlannerRecurrenceMode.DAILY,
                1,
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                LocalTime.of(9, 0),
                null,
                null,
                null
            ),
            new PlannerTaskLink(null, null, null, null),
            null,
            null,
            null
        ));

        PlannerSubtodo subtodo = service.savePlannerSubtodo(new PlannerSubtodo(
            null,
            task.id(),
            "Fill watering can",
            AvatarTodoStatus.OPEN,
            0,
            null,
            null
        ));

        assertThat(service.plannerTasks()).singleElement()
            .satisfies(saved -> assertThat(saved.title()).isEqualTo("Water plants"));
        assertThat(service.plannerSubtodos(task.id())).containsExactly(subtodo);
        assertThat(service.plannerCalendarProjection(null, null)).hasSizeGreaterThanOrEqualTo(1)
            .allSatisfy(projection -> assertThat(projection.taskId()).isEqualTo(task.id()));
    }

    @Test
    void organizerHelpersCompleteDeleteAppendAndSearch() {
        AvatarTodo todo = service.saveTodo(new AvatarTodo(
            null,
            "Pay bills",
            null,
            AvatarTodoStatus.OPEN,
            AvatarPriority.NORMAL,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));
        AvatarDailyTask dailyTask = service.saveDailyTask(new AvatarDailyTask(
            null,
            LocalDate.of(2026, 5, 22),
            "Review day",
            null,
            AvatarTaskStatus.PLANNED,
            0,
            null,
            null
        ));
        AvatarCalendarItem calendarItem = service.saveCalendarItem(new AvatarCalendarItem(
            null,
            "Dentist",
            null,
            Instant.parse("2026-05-24T15:00:00Z"),
            null,
            "UTC",
            null,
            AvatarCalendarStatus.SCHEDULED,
            null,
            null
        ));
        AvatarNote note = service.appendNote(null, "Garden", "Water seedlings", List.of("plants"));

        service.appendNote(note.id(), null, "Move tray outside", List.of());
        service.deleteCalendarItem(calendarItem.id());

        assertThat(service.completeTodo(todo.id()).status()).isEqualTo(AvatarTodoStatus.DONE);
        assertThat(service.completeDailyTask(dailyTask.id()).status()).isEqualTo(AvatarTaskStatus.DONE);
        assertThat(service.calendarItems()).isEmpty();
        assertThat(service.searchNotes("seedlings", false, 10)).singleElement()
            .satisfies(saved -> assertThat(saved.body()).contains("Move tray outside"));
    }

    @Test
    void plannerReadModelsKeepDueBlocksRemindersAndRecurrenceSeparate() {
        PlannerTask task = service.savePlannerTask(new PlannerTask(
            null,
            "Water plants",
            null,
            PlannerTaskStatus.ACTIVE,
            AvatarPriority.HIGH,
            Instant.parse("2026-05-29T13:00:00Z"),
            Instant.parse("2026-05-29T14:00:00Z"),
            "UTC",
            new PlannerRecurrence(PlannerRecurrenceMode.DAILY, 1, LocalDate.of(2026, 5, 29), null,
                LocalTime.of(13, 0), null, null, null),
            new PlannerTaskLink("project-1", null, null, null),
            null,
            null,
            null
        ));
        service.saveTimeBlock(new PlannerTimeBlock(
            null,
            LocalDate.of(2026, 5, 29),
            "Garden block",
            Instant.parse("2026-05-29T15:00:00Z"),
            Instant.parse("2026-05-29T16:00:00Z"),
            "task",
            task.id(),
            "PLANNED",
            null,
            null
        ));
        service.saveReminder(new PlannerReminder(
            null,
            "Check plants",
            null,
            Instant.parse("2026-05-29T14:30:00Z"),
            "OPEN",
            "task",
            task.id(),
            null,
            null,
            null
        ));

        assertThat(service.todayPlanner(LocalDate.of(2026, 5, 29)).timeBlocks()).singleElement()
            .satisfies(block -> assertThat(block.title()).isEqualTo("Garden block"));
        assertThat(service.calendarSchedule(LocalDate.of(2026, 5, 29), LocalDate.of(2026, 5, 30)).entries())
            .extracting(CalendarScheduleView.Entry::kind)
            .contains("time_block", "reminder", "recurrence");
        assertThat(service.updateOccurrence(task.id(), Instant.parse("2026-05-29T13:00:00Z"), "SKIPPED", null).status())
            .isEqualTo("SKIPPED");
    }

    @Test
    void calendarScheduleOverlaysOccurrenceStatusWithoutChangingParentTask() {
        PlannerTask task = service.savePlannerTask(new PlannerTask(
            null,
            "Water plants",
            null,
            PlannerTaskStatus.ACTIVE,
            AvatarPriority.HIGH,
            Instant.parse("2026-05-29T13:00:00Z"),
            null,
            "UTC",
            new PlannerRecurrence(PlannerRecurrenceMode.DAILY, 1, LocalDate.of(2026, 5, 29), null,
                LocalTime.of(13, 0), null, null, null),
            new PlannerTaskLink(null, null, null, null),
            null,
            null,
            null
        ));

        service.updateOccurrence(task.id(), Instant.parse("2026-05-29T13:00:00Z"), "SNOOZED",
            Instant.parse("2026-05-29T18:00:00Z"));

        assertThat(service.plannerTask(task.id()).status()).isEqualTo(PlannerTaskStatus.ACTIVE);
        assertThat(service.calendarSchedule(LocalDate.of(2026, 5, 29), LocalDate.of(2026, 5, 29)).entries())
            .filteredOn(entry -> "recurrence".equals(entry.kind()) && task.id().equals(entry.sourceId()))
            .singleElement()
            .satisfies(entry -> {
                assertThat(entry.status()).isEqualTo("SNOOZED");
                assertThat(entry.meta()).contains("snoozed until 2026-05-29T18:00:00Z");
            });
    }

    @Test
    void tasksRoutinesAppliesStatusRangeAndRecurrenceFilters() {
        PlannerTask activeRecurring = service.savePlannerTask(new PlannerTask(
            null,
            "Water plants",
            null,
            PlannerTaskStatus.ACTIVE,
            AvatarPriority.HIGH,
            Instant.now().plusSeconds(3600),
            null,
            "UTC",
            new PlannerRecurrence(PlannerRecurrenceMode.DAILY, 1, LocalDate.now(), null,
                LocalTime.of(13, 0), null, null, null),
            new PlannerTaskLink(null, null, null, null),
            null,
            null,
            null
        ));
        service.savePlannerTask(new PlannerTask(
            null,
            "Done one-off",
            null,
            PlannerTaskStatus.DONE,
            AvatarPriority.NORMAL,
            Instant.now().plusSeconds(7200),
            null,
            "UTC",
            new PlannerRecurrence(PlannerRecurrenceMode.NONE, 1, null, null, null, null, null, null),
            new PlannerTaskLink(null, null, null, null),
            null,
            null,
            null
        ));

        TasksRoutinesView filtered = service.tasksRoutines("ACTIVE", "WEEK", "RECURRING");

        assertThat(filtered.statusFilter()).isEqualTo("ACTIVE");
        assertThat(filtered.rangeFilter()).isEqualTo("WEEK");
        assertThat(filtered.recurrenceFilter()).isEqualTo("RECURRING");
        assertThat(filtered.tasks()).extracting(PlannerTask::id).containsExactly(activeRecurring.id());
    }

    @Test
    void dashboardRowHelpersExposeLayoutOperations() {
        UserDashboard dashboard = service.createDashboard("Layout");
        AvatarDashboardRow row = service.addDashboardRow(dashboard.id());
        AvatarDashboardRowWidget todos = service.addDashboardWidget(dashboard.id(), row.id(), "todos", 4);
        AvatarDashboardRowWidget notes = service.addDashboardWidget(dashboard.id(), row.id(), "notes", 4);

        service.moveDashboardWidget(notes.id(), "left");
        service.resizeDashboardWidget(todos.id(), 5);

        assertThat(service.dashboardRows(dashboard.id())).singleElement()
            .satisfies(saved -> {
                assertThat(saved.widgets()).extracting(AvatarDashboardRowWidget::widgetKey)
                    .containsExactly("notes", "todos");
                assertThat(saved.widgets()).extracting(AvatarDashboardRowWidget::columnWidth)
                    .containsExactly(4, 5);
            });
        assertThatThrownBy(() -> service.addDashboardWidget(dashboard.id(), row.id(), "todos", 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");

        assertThatThrownBy(() -> service.removeDashboardRow(row.id()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be empty");

        service.removeDashboardWidget(notes.id());
        service.removeDashboardWidget(todos.id());
        service.removeDashboardRow(row.id());
        assertThat(service.dashboardRows(dashboard.id())).isEmpty();
    }
}
