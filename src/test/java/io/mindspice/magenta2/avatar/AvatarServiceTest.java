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
        assertThat(snapshot.todos()).isEmpty();
        assertThat(snapshot.events()).hasSize(1);
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
}
