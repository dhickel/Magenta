package io.mindspice.magenta2.ai.orchestration.settings;

import java.util.List;
import java.util.Map;

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
    }

    @Test
    void compactionModelFallsBackToSummaryModelWhenBlank() {
        RuntimeSettingsRepository repository = repository();
        repository.save(settings("summary", null));
        RuntimeSettingsService service = new RuntimeSettingsService(repository, aiConfig(), null);

        assertThat(service.compactionModel()).isEqualTo("summary-remote");
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
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        return new RuntimeSettingsRepository(new JdbcTemplate(dataSource));
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
