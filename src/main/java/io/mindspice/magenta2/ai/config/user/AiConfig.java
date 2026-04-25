package io.mindspice.magenta2.ai.config.user;

import java.nio.file.Path;
import java.util.Map;

public record AiConfig(
    String defaultAgent,
    String summarizationAgent,
    Integer contextBufferPercent,
    Path dataRoot,
    Map<String, ModelConfig> models,
    Map<String, AgentConfig> agents
) {

    public int resolvedContextBufferPercent() {
        return contextBufferPercent == null ? 10 : contextBufferPercent;
    }

}
