package io.mindspice.magenta2.avatar;

import static org.assertj.core.api.Assertions.assertThat;

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
}
