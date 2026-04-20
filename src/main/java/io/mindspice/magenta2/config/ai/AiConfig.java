package io.mindspice.magenta2.config.ai;

import java.util.Map;

public record AiConfig(
    String defaultAgent,
    Map<String, ModelConfig> models,
    Map<String, AgentConfig> agents
) {
}
