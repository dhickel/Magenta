package io.mindspice.magenta.runtime.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadParsesStrictConfigurationWithFilenameDerivedIds() throws IOException {
        Path configRoot = createConfigRoot("cfg");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of("default-task"), List.of());
        writeAgent(
                configRoot.resolve("agents/main.yaml"),
                "default",
                List.of("base/system"),
                List.of("default-task"),
                List.of("default-workflow")
        );
        writeAgent(
                configRoot.resolve("agents/compaction.yaml"),
                "default",
                List.of("base/system"),
                List.of(),
                List.of()
        );
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        RuntimeConfig loaded = RuntimeConfig.load(configRoot.resolve("magenta.yaml"));
        assertThat(loaded.baseAgentId()).isEqualTo("main");
        assertThat(loaded.compactionAgentId()).isEqualTo("compaction");
        assertThat(loaded.maxTurns()).isEqualTo(3);
        assertThat(loaded.workspaceRoot()).isEqualTo(configRoot.toAbsolutePath().normalize());
        assertThat(loaded.maxToolOutputBytes()).isEqualTo(32_768);
        assertThat(loaded.maxFileReadLines()).isEqualTo(200);
        assertThat(loaded.maxSqlRows()).isEqualTo(500);
        assertThat(loaded.modelRequestTimeoutMs()).isEqualTo(600_000);
        assertThat(loaded.toolLoopGuard().enabled()).isTrue();
        assertThat(loaded.toolLoopGuard().repeatThreshold()).isEqualTo(5);
        assertThat(loaded.toolLoopGuard().windowSize()).isEqualTo(8);
        assertThat(loaded.modelsById()).containsKey("default");
        assertThat(loaded.modelsById().get("default").tokenizerEncodingOrDefault()).isEqualTo("cl100k_base");
        assertThat(loaded.agentsById()).containsKeys("main", "compaction");
        assertThat(loaded.agentsById().get("main").tasks()).containsExactly("default-task");
        assertThat(loaded.promptsById()).containsKey("base/system");
        assertThat(loaded.security().mode()).isEqualTo(RuntimeConfig.SecurityMode.BLACKLIST);
        assertThat(loaded.terminal().security().eventVisibility()).isEqualTo(RuntimeConfig.TerminalSecurityVisibility.DENIALS_ONLY);
        assertThat(loaded.observability().logLevel()).isEqualTo(RuntimeConfig.LogLevel.INFO);
        assertThat(loaded.observability().prettyLogsEnabled()).isFalse();
    }

    @Test
    void wildcardTaskReferencesExpandToAllTasks() throws IOException {
        Path configRoot = createConfigRoot("cfg-task-star");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/a.yaml"), List.of("base/system"), List.of("read_file"));
        writeTask(configRoot.resolve("tasks/b.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of("*"), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        RuntimeConfig loaded = RuntimeConfig.load(configRoot.resolve("magenta.yaml"));
        assertThat(loaded.agentsById().get("main").tasks()).containsExactly("a", "b");
    }

    @Test
    void legacyModelIdFieldIsRejected() throws IOException {
        Path configRoot = createConfigRoot("cfg-legacy-model-id");
        writeDefaultMagentaYaml(configRoot);
        Files.writeString(configRoot.resolve("models/default.yaml"), """
                id: "legacy"
                provider: "langchain4j"
                model: "dev"
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
                """);
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unrecognized field \"id\"");
    }

    @Test
    void legacyAgentTaskFieldIsRejected() throws IOException {
        Path configRoot = createConfigRoot("cfg-legacy-agent-task");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        Files.writeString(configRoot.resolve("agents/main.yaml"), """
                modelId: "default"
                promptIds: ["base/system"]
                task: "legacy"
                toolIds: []
                enabled: true
                """);
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unrecognized field \"task\"");
    }

    @Test
    void duplicatePromptIdsAreRejected() throws IOException {
        Path configRoot = createConfigRoot("cfg-duplicate-prompt");
        Files.createDirectories(configRoot.resolve("base"));
        Files.writeString(configRoot.resolve("magenta.yaml"), """
                instance:
                  baseAgentId: "main"
                  compactionAgentId: "compaction"
                  maxTurns: 3
                models:
                  include:
                    - "models/*.yaml"
                prompts:
                  include:
                    - "prompts/**/*.md"
                    - "base/*.md"
                tasks:
                  include:
                    - "tasks/*.yaml"
                workflows:
                  include:
                    - "workflows/*.yaml"
                agents:
                  include:
                    - "agents/*.yaml"
                """);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt A");
        Files.writeString(configRoot.resolve("base/system.md"), "system prompt B");

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate prompt id 'base/system'")
                .hasMessageContaining("prompts/base/system.md")
                .hasMessageContaining("base/system.md");
    }

    @Test
    void unknownConfigKeyStillFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-unknown-key");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", "maxTurns: 3\n  unknownSetting: true"));

        assertThatThrownBy(() -> RuntimeConfig.load(magentaYaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknownSetting");
    }

    @Test
    void toolLoopGuardParsesFromInstanceBlock() throws IOException {
        Path configRoot = createConfigRoot("cfg-tool-loop-guard");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", """
                maxTurns: 3
                  toolLoopGuard:
                    enabled: false
                    repeatThreshold: 9999
                    windowSize: 9999
                """));

        RuntimeConfig loaded = RuntimeConfig.load(magentaYaml);
        assertThat(loaded.toolLoopGuard().enabled()).isFalse();
        assertThat(loaded.toolLoopGuard().repeatThreshold()).isEqualTo(9999);
        assertThat(loaded.toolLoopGuard().windowSize()).isEqualTo(9999);
    }

    @Test
    void modelRequestTimeoutParsesFromInstanceBlock() throws IOException {
        Path configRoot = createConfigRoot("cfg-model-request-timeout");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", """
                maxTurns: 3
                  modelRequestTimeoutMs: 700000
                """));

        RuntimeConfig loaded = RuntimeConfig.load(magentaYaml);
        assertThat(loaded.modelRequestTimeoutMs()).isEqualTo(700_000);
    }

    @Test
    void invalidModelRequestTimeoutFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-model-request-timeout-invalid");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", """
                maxTurns: 3
                  modelRequestTimeoutMs: 0
                """));

        assertThatThrownBy(() -> RuntimeConfig.load(magentaYaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("instance.modelRequestTimeoutMs must be > 0");
    }

    @Test
    void invalidToolLoopGuardWindowFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-tool-loop-guard-invalid");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", """
                maxTurns: 3
                  toolLoopGuard:
                    repeatThreshold: 6
                    windowSize: 5
                """));

        assertThatThrownBy(() -> RuntimeConfig.load(magentaYaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("instance.toolLoopGuard.windowSize must be >= repeatThreshold");
    }

    @Test
    void invalidTokenizerEncodingFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-invalid-tokenizer");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path modelYaml = configRoot.resolve("models/default.yaml");
        Files.writeString(modelYaml, Files.readString(modelYaml).replace("tokenizerEncoding: \"cl100k_base\"", "tokenizerEncoding: \"not_real\""));

        assertThatThrownBy(() -> RuntimeConfig.load(configRoot.resolve("magenta.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported tokenizerEncoding")
                .hasMessageContaining("default")
                .hasMessageContaining("not_real");
    }

    @Test
    void invalidSecurityModeFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-invalid-security-mode");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml) + """
                security:
                  mode: "nope"
                """);

        assertThatThrownBy(() -> RuntimeConfig.load(magentaYaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported security mode");
    }

    @Test
    void invalidTerminalColorFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-invalid-terminal-color");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml) + """
                terminal:
                  rendering:
                    colors:
                      info: "ultraviolet"
                """);

        assertThatThrownBy(() -> RuntimeConfig.load(magentaYaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported terminal color token");
    }

    @Test
    void observabilityLogLevelParsesFromInstanceBlock() throws IOException {
        Path configRoot = createConfigRoot("cfg-observability-log-level");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", """
                maxTurns: 3
                  observability:
                    log_level: "debug"
                    pretty_logs_enabled: true
                """));

        RuntimeConfig loaded = RuntimeConfig.load(magentaYaml);
        assertThat(loaded.observability().logLevel()).isEqualTo(RuntimeConfig.LogLevel.DEBUG);
        assertThat(loaded.observability().prettyLogsEnabled()).isTrue();
    }

    @Test
    void invalidObservabilityLogLevelFailsFast() throws IOException {
        Path configRoot = createConfigRoot("cfg-observability-log-level-invalid");
        writeDefaultMagentaYaml(configRoot);
        writeModel(configRoot.resolve("models/default.yaml"), "dev");
        writeTask(configRoot.resolve("tasks/default-task.yaml"), List.of("base/system"), List.of("read_file"));
        writeWorkflow(configRoot.resolve("workflows/default-workflow.yaml"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/main.yaml"), "default", List.of("base/system"), List.of(), List.of());
        writeAgent(configRoot.resolve("agents/compaction.yaml"), "default", List.of("base/system"), List.of(), List.of());
        Files.writeString(configRoot.resolve("prompts/base/system.md"), "system prompt");

        Path magentaYaml = configRoot.resolve("magenta.yaml");
        Files.writeString(magentaYaml, Files.readString(magentaYaml).replace("maxTurns: 3", """
                maxTurns: 3
                  observability:
                    log_level: "chatter"
                """));

        assertThatThrownBy(() -> RuntimeConfig.load(magentaYaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported log level");
    }

    private Path createConfigRoot(String name) throws IOException {
        Path configRoot = tempDir.resolve(name);
        Files.createDirectories(configRoot.resolve("models"));
        Files.createDirectories(configRoot.resolve("agents"));
        Files.createDirectories(configRoot.resolve("prompts/base"));
        Files.createDirectories(configRoot.resolve("tasks"));
        Files.createDirectories(configRoot.resolve("workflows"));
        return configRoot;
    }

    private void writeDefaultMagentaYaml(Path configRoot) throws IOException {
        Files.writeString(configRoot.resolve("magenta.yaml"), """
                instance:
                  baseAgentId: "main"
                  compactionAgentId: "compaction"
                  maxTurns: 3
                models:
                  include:
                    - "models/*.yaml"
                prompts:
                  include:
                    - "prompts/**/*.md"
                tasks:
                  include:
                    - "tasks/*.yaml"
                workflows:
                  include:
                    - "workflows/*.yaml"
                agents:
                  include:
                    - "agents/*.yaml"
                """);
    }

    private void writeModel(Path path, String modelName) throws IOException {
        Files.writeString(path, """
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
                """.formatted(modelName));
    }

    private void writeAgent(
            Path path,
            String modelId,
            List<String> promptIds,
            List<String> tasks,
            List<String> workflows
    ) throws IOException {
        Files.writeString(path, """
                modelId: "%s"
                promptIds: %s
                tasks: %s
                workflows: %s
                toolIds: []
                enabled: true
                """.formatted(modelId, toYamlInline(promptIds), toYamlInline(tasks), toYamlInline(workflows)));
    }

    private void writeTask(Path path, List<String> promptIds, List<String> toolIds) throws IOException {
        Files.writeString(path, """
                promptIds: %s
                toolIds: %s
                enabled: true
                """.formatted(toYamlInline(promptIds), toYamlInline(toolIds)));
    }

    private void writeWorkflow(Path path, List<String> taskIds, List<String> dependsOnWorkflows) throws IOException {
        Files.writeString(path, """
                taskIds: %s
                dependsOnWorkflows: %s
                enabled: true
                """.formatted(toYamlInline(taskIds), toYamlInline(dependsOnWorkflows)));
    }

    private String toYamlInline(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream().map(v -> "\"" + v + "\"").collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }
}
