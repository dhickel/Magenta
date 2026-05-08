package io.mindspice.magenta2.ai.orchestration.runtime;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
    @NotBlank String agentId,
    String jobId,
    String jobItemId,
    @NotNull AssignmentType assignmentType,
    Integer priority,
    String modelOverride,
    String workspaceId,
    Map<String, Object> input
) {
}
