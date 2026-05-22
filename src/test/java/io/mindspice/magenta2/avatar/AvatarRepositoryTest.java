package io.mindspice.magenta2.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
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
}
