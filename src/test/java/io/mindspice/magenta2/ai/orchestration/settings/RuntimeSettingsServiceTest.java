package io.mindspice.magenta2.ai.orchestration.settings;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeSettingsServiceTest {

    @Test
    void compactionModelUsesConfiguredCompactionModelWhenPresent() {
        RuntimeSettingsRepository repository = repository();
        repository.save(settings("summary", "compact"));
        RuntimeSettingsService service = new RuntimeSettingsService(repository, aiConfig(), null);

        assertThat(service.compactionModel()).isEqualTo("compact-remote");
        assertThat(service.compactionModelKey()).isEqualTo("compact");
    }

    @Test
    void compactionModelFallsBackToSummaryModelWhenBlank() {
        RuntimeSettingsRepository repository = repository();
        repository.save(settings("summary", null));
        RuntimeSettingsService service = new RuntimeSettingsService(repository, aiConfig(), null);

        assertThat(service.compactionModel()).isEqualTo("summary-remote");
        assertThat(service.compactionModelKey()).isEqualTo("summary");
    }

    @Test
    void anonymousDefaultModelUsesRuntimeSettingBeforeDefaultAgentProfileModel() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AiConfig aiConfig = aiConfig();
        AgentProfileRepository agentRepository = new AgentProfileRepository(jdbcTemplate, new ObjectMapper());
        AgentProfileService agentService = new AgentProfileService(agentRepository, aiConfig, null);
        agentService.create(new AgentProfile(
            "agent-1",
            "magenta",
            AgentProfileStatus.ACTIVE,
            "main",
            "Prompt",
            List.of(),
            List.of(),
            true,
            null,
            null
        ));
        RuntimeSettingsRepository repository = new RuntimeSettingsRepository(jdbcTemplate);
        repository.save(new RuntimeSettings(
            "agent-1",
            "magenta",
            "summary",
            "main",
            "summary",
            "compact",
            10
        ));
        RuntimeSettingsService service = new RuntimeSettingsService(repository, aiConfig, agentService);

        assertThat(service.defaultModel()).isEqualTo("summary-remote");
        assertThat(service.defaultModelKey()).isEqualTo("summary");
        assertThat(service.resolveModel(null, "main")).isEqualTo("main-remote");
    }

    @Test
    void saveNormalizesRemoteModelNamesToAliasKeys() {
        RuntimeSettingsRepository repository = repository();
        RuntimeSettingsService service = new RuntimeSettingsService(repository, aiConfig(), null);

        RuntimeSettings saved = service.save(new RuntimeSettings(
            null,
            "magenta",
            "summary-remote",
            "main-remote",
            "summary-remote",
            "compact-remote",
            10,
            "main-remote",
            null,
            null,
            10,
            true,
            -1,
            false
        ));

        assertThat(saved.defaultModel()).isEqualTo("summary");
        assertThat(saved.planningModel()).isEqualTo("main");
        assertThat(saved.summaryModel()).isEqualTo("summary");
        assertThat(saved.compactionModel()).isEqualTo("compact");
        assertThat(saved.systemChatModel()).isEqualTo("main");
        assertThat(service.defaultModelKey()).isEqualTo("summary");
        assertThat(service.defaultModel()).isEqualTo("summary-remote");
    }

    @Test
    void keyAccessorsMapLegacyPersistedRemoteModelNamesWithoutMutatingSettings() {
        RuntimeSettingsRepository repository = repository();
        repository.save(new RuntimeSettings(
            null,
            "magenta",
            "main",
            "main-remote",
            "summary-remote",
            "compact-remote",
            10,
            "main-remote",
            null,
            null,
            10,
            true,
            -1,
            false
        ));
        RuntimeSettingsService service = new RuntimeSettingsService(repository, aiConfig(), null);

        assertThat(service.defaultModelKey()).isEqualTo("main");
        assertThat(service.planningModelKey()).isEqualTo("main");
        assertThat(service.summaryModelKey()).isEqualTo("summary");
        assertThat(service.compactionModelKey()).isEqualTo("compact");
        assertThat(service.systemChatModelKey()).isEqualTo("main");
        assertThat(repository.find().orElseThrow().summaryModel()).isEqualTo("summary-remote");
    }

    private RuntimeSettings settings(String summaryModel, String compactionModel) {
        return new RuntimeSettings(
            null,
            "magenta",
            "main",
            "main",
            summaryModel,
            compactionModel,
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

    private RuntimeSettingsRepository repository() {
        return new RuntimeSettingsRepository(jdbcTemplate());
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        return new JdbcTemplate(dataSource);
    }

    private AiConfig aiConfig() {
        return new AiConfig(
            "magenta",
            "main",
            "summary",
            "main",
            "compact",
            10,
            null,
            null,
            Map.of(
                "main", new ModelConfig("main-remote", "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null),
                "summary", new ModelConfig("summary-remote", "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null),
                "compact", new ModelConfig("compact-remote", "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null)
            ),
            Map.of("magenta", new AgentConfig("main", "Prompt", List.of(), List.of()))
        );
    }
}
