package io.mindspice.magenta.support;

import io.mindspice.magenta.runtime.config.RuntimeConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TestRuntimeConfigs {

    private TestRuntimeConfigs() {
    }

    public static RuntimeConfig basicRuntimeConfig() {
        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "model-default",
                "test-provider",
                "test-model",
                "http://localhost:11434",
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
                List.of(),
                List.of(),
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

        return new RuntimeConfig(
                Path.of("configs"),
                "agent-default",
                "agent-compaction",
                8,
                Map.of(modelConfig.id(), modelConfig),
                Map.of(baseAgent.id(), baseAgent, compactionAgent.id(), compactionAgent),
                Map.of(
                        "base.system", "Base prompt",
                        "agents.default", "Agent prompt"
                )
        );
    }
}
