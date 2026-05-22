package io.mindspice.magenta2.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AvatarAgentProfileBootstrapTest {
    private JdbcTemplate jdbcTemplate;
    private AgentProfileRepository profileRepository;
    private RuntimeSettingsRepository settingsRepository;
    private AvatarAgentProfileBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true));
        profileRepository = new AgentProfileRepository(jdbcTemplate, new ObjectMapper());
        settingsRepository = new RuntimeSettingsRepository(jdbcTemplate);
        bootstrap = new AvatarAgentProfileBootstrap(profileRepository);
    }

    @Test
    void reservesAvatarProfileWithoutChangingRuntimeDefaults() {
        settingsRepository.save(settings("magenta-id", "magenta"));

        bootstrap.reserveAvatarProfile();
        bootstrap.reserveAvatarProfile();

        AgentProfile avatar = profileRepository.findById("avatar").orElseThrow();
        RuntimeSettings settings = settingsRepository.find().orElseThrow();
        Integer avatarRows = jdbcTemplate.queryForObject(
            "select count(*) from agent_profiles where id = 'avatar'",
            Integer.class
        );

        assertThat(avatarRows).isEqualTo(1);
        assertThat(avatar.name()).isEqualTo("Avatar");
        assertThat(avatar.status()).isEqualTo(AgentProfileStatus.DISABLED);
        assertThat(avatar.directLineEnabled()).isFalse();
        assertThat(avatar.approvedTools()).isEmpty();
        assertThat(settings.defaultAgentId()).isEqualTo("magenta-id");
        assertThat(settings.defaultAgentName()).isEqualTo("magenta");
    }

    @Test
    void preservesExistingAvatarProfile() {
        profileRepository.save(new AgentProfile(
            "avatar",
            "Avatar",
            AgentProfileStatus.ACTIVE,
            null,
            "Custom",
            List.of("tool"),
            List.of(),
            true,
            null,
            null
        ));

        bootstrap.reserveAvatarProfile();

        AgentProfile avatar = profileRepository.findById("avatar").orElseThrow();
        assertThat(avatar.status()).isEqualTo(AgentProfileStatus.ACTIVE);
        assertThat(avatar.systemPrompt()).isEqualTo("Custom");
        assertThat(avatar.directLineEnabled()).isTrue();
    }

    @Test
    void failsOnAvatarIdConflict() {
        profileRepository.save(new AgentProfile(
            "avatar",
            "Other",
            AgentProfileStatus.ACTIVE,
            null,
            "Prompt",
            List.of(),
            List.of(),
            false,
            null,
            null
        ));

        assertThatThrownBy(() -> bootstrap.reserveAvatarProfile())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("avatar");
    }

    @Test
    void failsOnAvatarNameConflict() {
        profileRepository.save(new AgentProfile(
            "other",
            "Avatar",
            AgentProfileStatus.ACTIVE,
            null,
            "Prompt",
            List.of(),
            List.of(),
            false,
            null,
            null
        ));

        assertThatThrownBy(() -> bootstrap.reserveAvatarProfile())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Avatar");
    }

    private RuntimeSettings settings(String defaultAgentId, String defaultAgentName) {
        return new RuntimeSettings(
            defaultAgentId,
            defaultAgentName,
            "main",
            "main",
            "main",
            "main",
            10,
            "main",
            null,
            null,
            10,
            true,
            -1,
            false
        );
    }
}
