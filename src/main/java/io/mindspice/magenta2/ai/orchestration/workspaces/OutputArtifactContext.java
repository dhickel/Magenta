package io.mindspice.magenta2.ai.orchestration.workspaces;

import org.springframework.util.StringUtils;

public record OutputArtifactContext(
    String agentId,
    String jobId,
    String projectId,
    String workspaceId,
    String runType
) {
    public static final OutputArtifactContext EMPTY = new OutputArtifactContext(null, null, null, null, null);

    public OutputArtifactContext {
        agentId = normalize(agentId);
        jobId = normalize(jobId);
        projectId = normalize(projectId);
        workspaceId = normalize(workspaceId);
        runType = normalize(runType);
    }

    public boolean isEmpty() {
        return !StringUtils.hasText(agentId)
            && !StringUtils.hasText(jobId)
            && !StringUtils.hasText(projectId)
            && !StringUtils.hasText(workspaceId)
            && !StringUtils.hasText(runType);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
