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
        Path configRoot = createConfigRoot("cfg");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "model-default", "dev");
        writeAgent(configRoot.resolve("agents/default.yaml"), "agent-default", "model-default", "base.system");
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "agent-compaction", "model-default", "base.system");
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        RuntimeConfig loaded = RuntimeConfig.load(configRoot.resolve("magenta.yaml"));
        assertThat(loaded.baseAgentId()).isEqualTo("agent-default");
        assertThat(loaded.compactionAgentId()).isEqualTo("agent-compaction");
        assertThat(loaded.maxTurns()).isEqualTo(3);
        assertThat(loaded.modelsById()).containsKey("model-default");
        assertThat(loaded.modelsById().get("model-default").tokenizerEncodingOrDefault()).isEqualTo("cl100k_base");
        assertThat(loaded.agentsById()).containsKeys("agent-default", "agent-compaction");
        assertThat(loaded.promptsById()).containsKey("base.system");
    }

    @Test
    void loadDefaultParsesCurrentRepositoryConfigShape() {
        RuntimeConfig loaded = RuntimeConfig.loadDefault();
        assertThat(loaded).isNotNull();
        assertThat(loaded.maxTurns()).isEqualTo(8);
        assertThat(loaded.modelsById()).isNotEmpty();
        assertThat(loaded.agentsById()).isNotEmpty();
        assertThat(loaded.promptsById()).isNotEmpty();
    }

    @Test
    void duplicateModelIdsAreRejected() throws IOException {
        Path configRoot = createConfigRoot("cfg-duplicate-model");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/a.yaml"), "model-default", "first");
        writeModel(configRoot.resolve("models/b.yaml"), "model-default", "second");
        writeAgent(configRoot.resolve("agents/default.yaml"), "agent-default", "model-default", "base.system");
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "agent-compaction", "model-default", "base.system");
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate model id 'model-default'")
                .hasMessageContaining("a.yaml")
                .hasMessageContaining("b.yaml");
    }

    @Test
    void duplicateAgentIdsAreRejected() throws IOException {
        Path configRoot = createConfigRoot("cfg-duplicate-agent");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "model-default", "dev");
        writeAgent(configRoot.resolve("agents/a.yaml"), "agent-default", "model-default", "base.system");
        writeAgent(configRoot.resolve("agents/b.yaml"), "agent-default", "model-default", "base.system");
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate agent id 'agent-default'")
                .hasMessageContaining("a.yaml")
                .hasMessageContaining("b.yaml");
    }

    @Test
    void duplicatePromptIdsAreRejected() throws IOException {
        Path configRoot = createConfigRoot("cfg-duplicate-prompt");
        Files.createDirectories(configRoot.resolve("base"));
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
                    - "base/*.md"
                agents:
                  include:
                    - "agents/*.yaml"
                """);
        writeModel(configRoot.resolve("models/default.yaml"), "model-default", "dev");
        writeAgent(configRoot.resolve("agents/default.yaml"), "agent-default", "model-default", "base.system");
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "agent-compaction", "model-default", "base.system");
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt A");
        Files.writeString(configRoot.resolve("base/system.md"), "system prompt B");

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate prompt id 'base.system'")
                .hasMessageContaining("prompts/base/system.md")
                .hasMessageContaining("base/system.md");
    }

    @Test
    void unknownConfigKeyStillFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-unknown-key");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "model-default", "dev");
        writeAgent(configRoot.resolve("agents/default.yaml"), "agent-default", "model-default", "base.system");
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "agent-compaction", "model-default", "base.system");
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", "maxTurns: 3\n  unknownSetting: true"));

        assertThatThrownBy(() -> RuntimeConfig.load(magentaYaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknownSetting");
    }

    @Test
    void invalidTokenizerEncodingFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-invalid-tokenizer");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "model-default", "dev");
        writeAgent(configRoot.resolve("agents/default.yaml"), "agent-default", "model-default", "base.system");
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "agent-compaction", "model-default", "base.system");
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path modelYaml = configRoot.resolve("models/default.yaml");
        Files.writeString(modelYaml, Files.readString(modelYaml).replace("tokenizerEncoding: \"cl100k_base\"", "tokenizerEncoding: \"not_real\""));

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported tokenizerEncoding")
                .hasMessageContaining("model-default")
                .hasMessageContaining("not_real");
    }

    private Path createConfigRoot(String name) throws IOException {
        Path configRoot = tempDir.resolve(name);
        Files.createDirectories(configRoot.resolve("models"));
        Files.createDirectories(configRoot.resolve("agents"));
        Files.createDirectories(configRoot.resolve("prompts/base"));
        return configRoot;
    }

    private void writeDefaultMagentaYaml(Path configRoot) throws IOException {
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
    }

    private void writeModel(Path path, String id, String modelName) throws IOException {
        Files.writeString(path, """
                id: "%s"
                provider: "langchain4j"
                model: "%s"
                endpoint: "http://localhost:11434"
                maxTokens: 8000
                maxContext: 7000
                compactThreshold: 6000
                temperature: 0.1
                compactionStrategy: "rolling_window"
                tokenizerEncoding: "cl100k_base"
                supportsToolCalling: true
                supportsStreaming: false
                enabled: true
                """.formatted(id, modelName));
    }

    private void writeAgent(Path path, String id, String modelId, String promptId) throws IOException {
        Files.writeString(path, """
                id: "%s"
                modelId: "%s"
                promptIds: ["%s"]
                toolIds: []
                enabled: true
                """.formatted(id, modelId, promptId));
    }
}
