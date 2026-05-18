package io.mindspice.magenta2.ai.config.user;

import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

public record AiConfig(
    String defaultAgent,
    String defaultModel,
    @JsonProperty("summeryModel") String summeryModel,
    String planningModel,
    @JsonProperty("compactionModel") String compactionModel,
    Integer contextBufferPercent,
    Path dataRoot,
    WebSearchConfig webSearch,
    Map<String, ModelConfig> models,
    Map<String, AgentConfig> agents,
    Boolean unsafeAllowWildcardShellCommands
) {

    public int resolvedContextBufferPercent() {
        return contextBufferPercent == null ? 10 : contextBufferPercent;
    }

    public String resolvedDefaultModelKey() {
        return StringUtils.hasText(defaultModel) ? defaultModel : null;
    }

    public String resolvedSummeryModelKey() {
        return summeryModel;
    }

    public String resolvedPlanningModelKey() {
        return planningModel == null || planningModel.isBlank() ? "local-gemma-26b" : planningModel;
    }

    public String resolvedCompactionModelKey() {
        return StringUtils.hasText(compactionModel) ? compactionModel : summeryModel;
    }

    public boolean unsafeAllowWildcardShellCommandsEnabled() {
        return Boolean.TRUE.equals(unsafeAllowWildcardShellCommands);
    }

    public AiConfig(
        String defaultAgent,
        String defaultModel,
        String summeryModel,
        String planningModel,
        String compactionModel,
        Integer contextBufferPercent,
        Path dataRoot,
        WebSearchConfig webSearch,
        Map<String, ModelConfig> models,
        Map<String, AgentConfig> agents
    ) {
        this(defaultAgent, defaultModel, summeryModel, planningModel, compactionModel,
            contextBufferPercent, dataRoot, webSearch, models, agents, false);
    }

    public AiConfig(
        String defaultAgent,
        String summeryModel,
        String planningModel,
        Integer contextBufferPercent,
        Path dataRoot,
        Map<String, ModelConfig> models,
        Map<String, AgentConfig> agents
    ) {
        this(defaultAgent, null, summeryModel, planningModel, null, contextBufferPercent, dataRoot, null, models, agents, false);
    }

    public AiConfig(
        String defaultAgent,
        String summeryModel,
        Integer contextBufferPercent,
        Path dataRoot,
        WebSearchConfig webSearch,
        Map<String, ModelConfig> models,
        Map<String, AgentConfig> agents
    ) {
        this(defaultAgent, null, summeryModel, null, null, contextBufferPercent, dataRoot, webSearch, models, agents, false);
    }

    public AiConfig(
        String defaultAgent,
        String summeryModel,
        Integer contextBufferPercent,
        Path dataRoot,
        Map<String, ModelConfig> models,
        Map<String, AgentConfig> agents
    ) {
        this(defaultAgent, null, summeryModel, null, null, contextBufferPercent, dataRoot, null, models, agents, false);
    }

}
