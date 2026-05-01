package io.mindspice.magenta2.ai.config.user;

import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiConfig(
    String defaultAgent,
    @JsonProperty("summeryModel") String summeryModel,
    Integer contextBufferPercent,
    Path dataRoot,
    WebSearchConfig webSearch,
    Map<String, ModelConfig> models,
    Map<String, AgentConfig> agents
) {

    public int resolvedContextBufferPercent() {
        return contextBufferPercent == null ? 10 : contextBufferPercent;
    }

    public String resolvedSummeryModelKey() {
        return summeryModel;
    }

    public AiConfig(
        String defaultAgent,
        String summeryModel,
        Integer contextBufferPercent,
        Path dataRoot,
        Map<String, ModelConfig> models,
        Map<String, AgentConfig> agents
    ) {
        this(defaultAgent, summeryModel, contextBufferPercent, dataRoot, null, models, agents);
    }

}
