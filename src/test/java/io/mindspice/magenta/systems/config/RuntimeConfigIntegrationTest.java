package io.mindspice.magenta.systems.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadParsesStrictConfigurationWithResolvableReferences() throws IOException {
        Path configRoot = tempDir.resolve("cfg");
        Files.createDirectories(configRoot.resolve("models"));
        Files.createDirectories(configRoot.resolve("agents"));
        Files.createDirectories(configRoot.resolve("prompts/base"));

        Files.writeString(configRoot.resolve("magenta.yaml"), """
                instance:
                  baseAgentId: "agent-default"
                  compactionAgentId: "agent-compaction"
                  maxTurns: 3
                models:
                  include:
                    - "models/*.yaml"
                prompts:
                  include:
                    - "prompts/**/*.md"
                agents:
                  include:
                    - "agents/*.yaml"
                """);

        Files.writeString(configRoot.resolve("models/default.yaml"), """
                id: "model-default"
                provider: "langchain4j"
                model: "dev"
                endpoint: "http://localhost:11434"
                maxTokens: 8000
                maxContext: 7000
                compactThreshold: 6000
                temperature: 0.1
                compactionStrategy: "rolling_window"
                supportsToolCalling: true
                supportsStreaming: false
                enabled: true
                """);

        Files.writeString(configRoot.resolve("agents/default.yaml"), """
                id: "agent-default"
                modelId: "model-default"
                promptIds: ["base.system"]
                toolIds: []
                enabled: true
                """);

        Files.writeString(configRoot.resolve("agents/compaction.yaml"), """
                id: "agent-compaction"
                modelId: "model-default"
                promptIds: ["base.system"]
                toolIds: []
                enabled: true
                """);

        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        RuntimeConfig loaded = RuntimeConfig.load(configRoot.resolve("magenta.yaml"));
        assertThat(loaded.baseAgentId()).isEqualTo("agent-default");
        assertThat(loaded.compactionAgentId()).isEqualTo("agent-compaction");
        assertThat(loaded.maxTurns()).isEqualTo(3);
        assertThat(loaded.modelsById()).containsKey("model-default");
        assertThat(loaded.agentsById()).containsKeys("agent-default", "agent-compaction");
        assertThat(loaded.promptsById()).containsKey("base.system");
    }

    @Test
    void loadDefaultRejectsCurrentRepositoryConfigShape() {
        assertThatThrownBy(RuntimeConfig::loadDefault)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workspaceRoot");
    }

    @Test
    void duplicateModelIdsAreSilentlyOverwritten() throws IOException {
        Path configRoot = tempDir.resolve("cfg-duplicate-model");
        Files.createDirectories(configRoot.resolve("models"));
        Files.createDirectories(configRoot.resolve("agents"));
        Files.createDirectories(configRoot.resolve("prompts/base"));

        Files.writeString(configRoot.resolve("magenta.yaml"), """
                instance:
                  baseAgentId: "agent-default"
                models:
                  include:
                    - "models/*.yaml"
                prompts:
                  include:
                    - "prompts/**/*.md"
                agents:
                  include:
                    - "agents/*.yaml"
                """);

        Files.writeString(configRoot.resolve("models/a.yaml"), """
                id: "model-default"
                provider: "langchain4j"
                model: "first"
                endpoint: "http://localhost:11434"
                maxTokens: 8000
                maxContext: 7000
                compactThreshold: 6000
                temperature: 0.1
                compactionStrategy: "rolling_window"
                supportsToolCalling: true
                supportsStreaming: false
                enabled: true
                """);

        Files.writeString(configRoot.resolve("models/b.yaml"), """
                id: "model-default"
                provider: "langchain4j"
                model: "second"
                endpoint: "http://localhost:11434"
                maxTokens: 8000
                maxContext: 7000
                compactThreshold: 6000
                temperature: 0.1
                compactionStrategy: "rolling_window"
                supportsToolCalling: true
                supportsStreaming: false
                enabled: true
                """);

        Files.writeString(configRoot.resolve("agents/default.yaml"), """
                id: "agent-default"
                modelId: "model-default"
                promptIds: ["base.system"]
                toolIds: []
                enabled: true
                """);

        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        RuntimeConfig loaded = RuntimeConfig.load(configRoot.resolve("magenta.yaml"));
        assertThat(loaded.modelsById()).hasSize(1);
        assertThat(loaded.modelsById().get("model-default").model()).isEqualTo("second");
    }
}
