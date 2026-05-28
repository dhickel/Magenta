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

class AvatarRepositoryTest {
    private AvatarRepository repository;

    @BeforeEach
    void setUp() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        new AvatarSchemaInitializer(dataSource).initialize();
        repository = new AvatarRepository(new JdbcTemplate(dataSource), new ObjectMapper());
    }

    @Test
    void savesSingletonProfile() {
        AvatarProfile saved = repository.saveProfile(new AvatarProfile(
            AvatarRepository.PROFILE_ID,
            "Avatar",
            "America/New_York",
            "en-US",
            "Personal dashboard",
            null,
            null
        ));

        assertThat(saved.id()).isEqualTo(AvatarRepository.PROFILE_ID);
        assertThat(saved.displayName()).isEqualTo("Avatar");
        assertThat(saved.createdAt()).isNotNull();
        assertThat(repository.findProfile()).contains(saved);
    }

    @Test
    void roundTripsPreferencesLayoutAndFactsAsJson() {
        repository.upsertPreference(new AvatarPreference(
            "dashboard",
            "density",
            Map.of("mode", "compact", "columns", 3),
            null
        ));
        repository.saveDashboardWidget(new AvatarDashboardWidget(
            "today",
            1,
            "wide",
            true,
            false,
            Map.of("accent", "green"),
            null
        ));
        repository.upsertFact(new AvatarFact(
            "user",
            "timezone",
            Map.of("value", "America/New_York"),
            AvatarFactStatus.ACTIVE,
            null
        ));
        repository.upsertFact(new AvatarFact(
            "user",
            "timezone",
            Map.of("value", "UTC"),
            AvatarFactStatus.ARCHIVED,
            null
        ));

        assertThat(repository.findPreferences()).singleElement()
            .satisfies(preference -> assertThat(preference.value()).containsEntry("mode", "compact"));
        assertThat(repository.findDashboardLayout()).singleElement()
            .satisfies(widget -> assertThat(widget.settings()).containsEntry("accent", "green"));
        assertThat(repository.assistantDashboard().name()).isEqualTo("Assistant");
        assertThat(repository.findDashboardRows()).hasSize(3);
        assertThat(repository.findFacts()).singleElement()
            .satisfies(fact -> {
                assertThat(fact.value()).containsEntry("value", "UTC");
                assertThat(fact.status()).isEqualTo(AvatarFactStatus.ARCHIVED);
            });
    }

    @Test
    void savesOrganizerRecordsWithStatusParsing() {
        Instant due = Instant.parse("2026-05-23T12:00:00Z");
        AvatarTodo todo = repository.saveTodo(new AvatarTodo(
            null,
            "Pay bills",
            "Use checking",
            AvatarTodoStatus.IN_PROGRESS,
            AvatarPriority.HIGH,
            due,
            "project-1",
            "task-1",
            "output-1",
            null,
            null,
            null
        ));
        AvatarDailyTask daily = repository.saveDailyTask(new AvatarDailyTask(
            null,
            LocalDate.of(2026, 5, 22),
            "Review day",
            null,
            AvatarTaskStatus.ACTIVE,
            2,
            null,
            null
        ));
        AvatarCalendarItem calendar = repository.saveCalendarItem(new AvatarCalendarItem(
            null,
            "Dentist",
            null,
            Instant.parse("2026-05-24T15:00:00Z"),
            Instant.parse("2026-05-24T16:00:00Z"),
            "America/New_York",
            "Clinic",
            AvatarCalendarStatus.SCHEDULED,
            null,
            null
        ));
        AvatarNote note = repository.saveNote(new AvatarNote(
            null,
            "Garden",
            "Water seedlings",
            List.of("home", "plants"),
            Map.of("kind", "manual"),
            false,
            null,
            null
        ));

        assertThat(repository.findTodo(todo.id()).orElseThrow().status()).isEqualTo(AvatarTodoStatus.IN_PROGRESS);
        assertThat(repository.findDailyTasks(LocalDate.of(2026, 5, 22))).containsExactly(daily);
        assertThat(repository.findCalendarItem(calendar.id()).orElseThrow().timezone()).isEqualTo("America/New_York");
        assertThat(repository.findNote(note.id()).orElseThrow().tags()).containsExactly("home", "plants");
        assertThat(repository.findNotes(false)).containsExactly(note);
    }

    @Test
    void savesPlannerTasksSubtodosLinksAndCalendarProjection() {
        AvatarNote note = repository.saveNote(new AvatarNote(
            null,
            "Research",
            "Linkable context",
            List.of("planner"),
            Map.of(),
            false,
            null,
            null
        ));
        PlannerTask task = repository.savePlannerTask(new PlannerTask(
            null,
            "Plan garden week",
            "Use recurring reminder",
            PlannerTaskStatus.ACTIVE,
            AvatarPriority.HIGH,
            Instant.parse("2026-05-23T14:00:00Z"),
            Instant.parse("2026-05-23T15:00:00Z"),
            "UTC",
            new PlannerRecurrence(
                PlannerRecurrenceMode.WEEKLY,
                2,
                LocalDate.of(2026, 5, 23),
                LocalDate.of(2026, 6, 30),
                LocalTime.of(14, 0),
                null,
                null,
                null
            ),
            new PlannerTaskLink("project-1", "assignment-1", "job-1", "output-1"),
            null,
            null,
            null
        ));

        PlannerSubtodo subtodo = repository.savePlannerSubtodo(new PlannerSubtodo(
            null,
            task.id(),
            "Check seedlings",
            AvatarTodoStatus.OPEN,
            0,
            null,
            null
        ));
        repository.linkPlannerTaskNote(task.id(), note.id());
        repository.replacePlannerCalendarProjection(task.id(), List.of(new PlannerCalendarProjection(
            null,
            task.id(),
            Instant.parse("2026-05-23T14:00:00Z"),
            Instant.parse("2026-05-23T15:00:00Z"),
            PlannerTaskStatus.ACTIVE,
            null,
            null
        )));

        assertThat(repository.findPlannerTask(task.id())).hasValueSatisfying(saved -> {
            assertThat(saved.link().assignmentId()).isEqualTo("assignment-1");
            assertThat(saved.recurrence().mode()).isEqualTo(PlannerRecurrenceMode.WEEKLY);
            assertThat(saved.recurrence().interval()).isEqualTo(2);
        });
        assertThat(repository.findPlannerTasks()).containsExactly(task);
        assertThat(repository.findPlannerSubtodos(task.id())).containsExactly(subtodo);
        assertThat(repository.findPlannerCalendarProjection(null, null)).singleElement()
            .satisfies(projection -> assertThat(projection.taskId()).isEqualTo(task.id()));
    }

    @Test
    void appendsEventsInOccurredOrder() {
        repository.appendEvent(new AvatarEvent(
            "later",
            "todo.updated",
            Map.of("todoId", "2"),
            Instant.parse("2026-05-22T11:00:00Z")
        ));
        repository.appendEvent(new AvatarEvent(
            "earlier",
            "todo.created",
            Map.of("todoId", "1"),
            Instant.parse("2026-05-22T10:00:00Z")
        ));

        assertThat(repository.findEvents()).extracting(AvatarEvent::id).containsExactly("earlier", "later");
    }

    @Test
    void createsEmptyCustomDashboardsForRowLayoutEditing() {
        UserDashboard dashboard = repository.createDashboard("Layout");

        assertThat(repository.findDashboards()).extracting(UserDashboard::name)
            .containsExactly("Assistant", "Layout");
        assertThat(repository.findDashboardRows(dashboard.id())).isEmpty();

        AvatarDashboardRow rowOne = repository.addDashboardRow(dashboard.id());
        AvatarDashboardRow rowTwo = repository.addDashboardRow(dashboard.id());
        repository.addDashboardWidget(dashboard.id(), rowOne.id(), "one", 6);
        repository.addDashboardWidget(dashboard.id(), rowOne.id(), "two", 6);
        repository.addDashboardWidget(dashboard.id(), rowTwo.id(), "three", 4);

        List<AvatarDashboardRow> rows = repository.findDashboardRows(dashboard.id());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).position()).isZero();
        assertThat(rows.get(0).widgets()).extracting(AvatarDashboardRowWidget::widgetKey)
            .containsExactly("one", "two");
        assertThat(rows.get(0).widgets()).extracting(AvatarDashboardRowWidget::columnWidth)
            .containsExactly(6, 6);
        assertThat(rows.get(1).position()).isEqualTo(1);
        assertThat(rows.get(1).widgets()).extracting(AvatarDashboardRowWidget::widgetKey)
            .containsExactly("three");
    }

    @Test
    void addsWidgetsWithSingleInstanceAndRowWidthBounds() {
        UserDashboard dashboard = repository.createDashboard("Organizer");
        AvatarDashboardRow row = repository.addDashboardRow(dashboard.id());
        AvatarDashboardRowWidget first = repository.addDashboardWidget(dashboard.id(), row.id(), "todos", 8);
        AvatarDashboardRowWidget second = repository.addDashboardWidget(dashboard.id(), row.id(), "notes", 4);

        assertThat(repository.findDashboardRows(dashboard.id())).singleElement()
            .satisfies(saved -> assertThat(saved.widgets()).extracting(AvatarDashboardRowWidget::widgetKey)
                .containsExactly("todos", "notes"));
        repository.resizeDashboardWidget(first.id(), 5);
        assertThat(repository.findDashboardRows(dashboard.id())).singleElement()
            .satisfies(saved -> assertThat(saved.widgets().getFirst().columnWidth()).isEqualTo(5));
        assertThatThrownBy(() -> repository.addDashboardWidget(dashboard.id(), row.id(), "todos", 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        assertThatThrownBy(() -> repository.resizeDashboardWidget(first.id(), 12))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot exceed 12");
        assertThatThrownBy(() -> repository.addDashboardWidget(dashboard.id(), row.id(), "calendar", 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot exceed 12");

        repository.removeDashboardWidget(second.id());
        assertThat(repository.findDashboardRows(dashboard.id())).singleElement()
            .satisfies(saved -> assertThat(saved.widgets()).extracting(AvatarDashboardRowWidget::widgetKey)
                .containsExactly("todos"));

        assertThatThrownBy(() -> repository.removeDashboardRow(row.id()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be empty");

        repository.removeDashboardWidget(first.id());
        repository.removeDashboardRow(row.id());
        assertThat(repository.findDashboardRows(dashboard.id())).isEmpty();
    }

    @Test
    void movesRowsAndWidgetsWithinBounds() {
        UserDashboard dashboard = repository.createDashboard("Movement");
        AvatarDashboardRow rowOne = repository.addDashboardRow(dashboard.id());
        AvatarDashboardRow rowTwo = repository.addDashboardRow(dashboard.id());
        AvatarDashboardRowWidget todos = repository.addDashboardWidget(dashboard.id(), rowOne.id(), "todos", 4);
        AvatarDashboardRowWidget notes = repository.addDashboardWidget(dashboard.id(), rowOne.id(), "notes", 4);
        repository.addDashboardWidget(dashboard.id(), rowTwo.id(), "calendar", 4);

        assertThatThrownBy(() -> repository.moveDashboardRow(rowOne.id(), -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outside layout bounds");

        repository.moveDashboardRow(rowTwo.id(), -1);
        assertThat(repository.findDashboardRows(dashboard.id())).extracting(AvatarDashboardRow::id)
            .containsExactly(rowTwo.id(), rowOne.id());

        repository.moveDashboardWidget(notes.id(), "left");
        AvatarDashboardRow savedRowOne = repository.findDashboardRows(dashboard.id()).stream()
            .filter(row -> row.id().equals(rowOne.id()))
            .findFirst()
            .orElseThrow();
        assertThat(savedRowOne.widgets()).extracting(AvatarDashboardRowWidget::widgetKey)
            .containsExactly("notes", "todos");

        repository.moveDashboardWidget(todos.id(), "up");
        AvatarDashboardRow savedRowTwo = repository.findDashboardRows(dashboard.id()).stream()
            .filter(row -> row.id().equals(rowTwo.id()))
            .findFirst()
            .orElseThrow();
        assertThat(savedRowTwo.widgets()).extracting(AvatarDashboardRowWidget::widgetKey)
            .containsExactly("calendar", "todos");
        assertThatThrownBy(() -> repository.moveDashboardWidget(notes.id(), "sideways"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown widget direction");
    }

    private AvatarDashboardWidget widget(String id, int position, String size) {
        return new AvatarDashboardWidget(id, position, size, true, false, Map.of(), null);
    }
}
