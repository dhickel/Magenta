package io.mindspice.magenta2.config.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelConfig(
    @JsonProperty("remoteModelName") String remoteModelName,
    @JsonProperty("remoteEndpoint") String remoteEndpoint,
    @JsonProperty("endpointType") EndpointType endpointType,
    @JsonProperty("contextLength") Integer contextLength
) {
}
