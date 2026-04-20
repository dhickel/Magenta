package io.mindspice.magenta2.config.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentConfig(
    @JsonProperty("model") String model,
    @JsonProperty("systemPrompt") String systemPrompt,
    @JsonProperty("approvedTools") List<String> approvedTools
) {
}
