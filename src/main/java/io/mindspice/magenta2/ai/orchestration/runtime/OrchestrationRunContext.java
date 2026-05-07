package io.mindspice.magenta2.ai.orchestration.runtime;

import org.springframework.util.StringUtils;

public record OrchestrationRunContext(
    String agentId,
    String jobId,
    String workspaceId,
    String modelOverride,
    Integer priority
) {
    public OrchestrationRunContext {
        agentId = normalize(agentId);
        jobId = normalize(jobId);
        workspaceId = normalize(workspaceId);
        modelOverride = normalize(modelOverride);
    }

    public boolean hasContext() {
        return StringUtils.hasText(agentId) || StringUtils.hasText(jobId) || StringUtils.hasText(workspaceId)
            || StringUtils.hasText(modelOverride) || priority != null;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
