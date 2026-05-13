package io.mindspice.magenta2.ai.orchestration.docker;

import java.time.Instant;

/**
 * Status response for the Docker runtime health check endpoint.
 */
public record DockerStatusResponse(
    boolean enabled,
    boolean available,
    String dockerHost,
    String agentImage,
    String message,
    Instant checkedAt
) {}
