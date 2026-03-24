package io.mindspice.magenta.runtime;

import io.mindspice.magenta.runtime.config.RuntimeConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class TestOpenAiRuntimeConfigs {

    private TestOpenAiRuntimeConfigs() {
    }

    static RuntimeConfig runtimeConfig(String endpoint) {
        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "model-default",
                "openai",
                "test-model",
                endpoint,
                4096,
                4096,
                500,
                0.0,
                "rolling_window",
                "cl100k_base",
                false,
                false,
                true
        );

        RuntimeConfig.AgentConfig baseAgent = new RuntimeConfig.AgentConfig(
                "agent-default",
                "model-default",
                List.of("base.system", "agents.default"),
                List.of("default-task"),
                List.of("default-workflow"),
                List.of("read_file"),
                true
        );

        RuntimeConfig.AgentConfig compactionAgent = new RuntimeConfig.AgentConfig(
                "agent-compaction",
                "model-default",
                List.of("base.system"),
                List.of(),
                List.of(),
                List.of(),
                true
        );

        RuntimeConfig.TaskConfig defaultTask = new RuntimeConfig.TaskConfig(
                "default-task",
                List.of("tasks.default"),
                List.of("read_file"),
                true
        );
        RuntimeConfig.WorkflowConfig defaultWorkflow = new RuntimeConfig.WorkflowConfig(
                "default-workflow",
                List.of("default-task"),
                List.of(),
                true
        );

        return new RuntimeConfig(
                Path.of("configs"),
                Path.of(".").toAbsolutePath().normalize(),
                "agent-default",
                "agent-compaction",
                8,
                64,
                32_768,
                200,
                500,
                Map.of(modelConfig.id(), modelConfig),
                Map.of(baseAgent.id(), baseAgent, compactionAgent.id(), compactionAgent),
                Map.of(
                        "base.system", "Base prompt",
                        "agents.default", "Agent prompt",
                        "tasks.default", "Configured task"
                ),
                Map.of(defaultTask.id(), defaultTask),
                Map.of(defaultWorkflow.id(), defaultWorkflow),
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                RuntimeConfig.TerminalConfig.defaults()
        );
    }
}
