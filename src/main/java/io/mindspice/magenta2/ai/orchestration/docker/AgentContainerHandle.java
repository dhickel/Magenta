package io.mindspice.magenta2.ai.orchestration.docker;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentContainerHandle(
    String agentId,
    String containerId,
    String containerName,
    AgentContainerStatus status,
    String dockerHost,
    String image,
    Instant startedAt,
    Instant lastUsedAt,
    List<String> mounts,
    Map<String, String> labels,
    String message
) {
}
