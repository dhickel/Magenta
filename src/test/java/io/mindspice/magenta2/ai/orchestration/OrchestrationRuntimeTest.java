package io.mindspice.magenta2.ai.orchestration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileSeeder;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsRepository;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workspaces.Workspace;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLink;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLinkType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrchestrationRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimeSettingsSaveLoadAndModelResolutionPriority() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AiConfig aiConfig = aiConfig();
        AgentProfileRepository agentRepository = new AgentProfileRepository(jdbcTemplate, new ObjectMapper());
        AgentProfileService agentService = new AgentProfileService(agentRepository, aiConfig, null);
        AgentProfile agent = agentService.create(profile("magenta", "main"));
        RuntimeSettingsRepository settingsRepository = new RuntimeSettingsRepository(jdbcTemplate);
        RuntimeSettingsService settingsService = new RuntimeSettingsService(settingsRepository, aiConfig, agentService);

        RuntimeSettings saved = settingsService.save(new RuntimeSettings(
            agent.id(), agent.name(), "summary", "planning", "summary", "main", 20
        ));

        assertThat(settingsRepository.find()).contains(saved);
        assertThat(settingsService.resolveModel("planning", "main")).isEqualTo("planning-remote");
        assertThat(settingsService.resolveModel(null, "main")).isEqualTo("main-remote");
        assertThat(settingsService.defaultModel()).isEqualTo("main-remote");
        assertThat(settingsService.contextBufferPercent()).isEqualTo(20);
    }

    @Test
    void agentProfileCrudCloneDisableAndJsonLists() {
        AgentProfileService service = agentService(jdbcTemplate(), aiConfig());
        AgentProfile created = service.create(profile("magenta", "main"));

        AgentProfile updated = service.update(created.id(), new AgentProfile(
            created.id(), "magenta", AgentProfileStatus.ACTIVE, "planning", "Prompt 2",
            List.of("file_read"), List.of("printf"), true, null, null
        ));
        AgentProfile clone = service.clone(created.id());
        service.deleteOrDisable(created.id());

        assertThat(updated.approvedTools()).containsExactly("file_read");
        assertThat(updated.allowedShellCommands()).containsExactly("printf");
        assertThat(clone.id()).isNotEqualTo(created.id());
        assertThat(clone.name()).isEqualTo("magenta copy");
        assertThat(service.get(created.id()).status()).isEqualTo(AgentProfileStatus.DISABLED);
    }

    @Test
    void workspaceCreatesRootsAndRejectsEscapes() throws Exception {
        WorkspaceService service = new WorkspaceService(new WorkspaceRepository(jdbcTemplate()), aiConfig());

        Workspace workspace = service.agentWorkspace("agent-1", "Agent 1");
        WorkspaceLink link = service.addLink(workspace.id(), new WorkspaceLink(
            null, workspace.id(), "notes", WorkspaceLinkType.PATH, "notes", true, false, null, null
        ));

        assertThat(Files.isDirectory(tempDir.resolve("agents/agent-1"))).isTrue();
        assertThat(link.label()).isEqualTo("notes");
        assertThatThrownBy(() -> service.addLink(workspace.id(), new WorkspaceLink(
            null, workspace.id(), "bad", WorkspaceLinkType.PATH, "../../../escape", true, false, null, null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("escapes data root");
    }

    @Test
    void legacySeederCreatesOneDefaultAgentOnlyWhenEmpty() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AiConfig aiConfig = aiConfig();
        AgentProfileRepository repository = new AgentProfileRepository(jdbcTemplate, new ObjectMapper());
        AgentProfileService service = new AgentProfileService(repository, aiConfig, null);
        RuntimeSettingsRepository settingsRepository = new RuntimeSettingsRepository(jdbcTemplate);
        AgentProfileSeeder seeder = new AgentProfileSeeder(repository, service, settingsRepository, aiConfig);

        seeder.run(null);
        seeder.run(null);

        assertThat(repository.findAll()).hasSize(1);
        AgentProfile agent = repository.findAll().getFirst();
        assertThat(agent.name()).isEqualTo("magenta");
        assertThat(agent.systemPrompt()).isEqualTo("Legacy prompt");
        assertThat(settingsRepository.find().orElseThrow().defaultAgentId()).isEqualTo(agent.id());
    }

    private AgentProfileService agentService(JdbcTemplate jdbcTemplate, AiConfig aiConfig) {
        return new AgentProfileService(new AgentProfileRepository(jdbcTemplate, new ObjectMapper()), aiConfig, null);
    }

    private AgentProfile profile(String name, String model) {
        return new AgentProfile(
            null, name, AgentProfileStatus.ACTIVE, model, "Prompt", List.of(), List.of("printf"),
            true, null, null
        );
    }

    private AiConfig aiConfig() {
        Map<String, ModelConfig> models = Map.of(
            "main", model("main-remote"),
            "planning", model("planning-remote"),
            "summary", model("summary-remote")
        );
        return new AiConfig(
            "legacy",
            "main",
            "summary",
            "planning",
            "main",
            10,
            tempDir,
            null,
            models,
            Map.of("legacy", new AgentConfig("main", "Legacy prompt", List.of(), List.of("*")))
        );
    }

    private ModelConfig model(String remoteName) {
        return new ModelConfig(remoteName, "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null);
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
