package io.mindspice.magenta2.ai.orchestration.workspaces;

import org.springframework.util.StringUtils;

public record OutputArtifactContext(
    String agentId,
    String jobId,
    String jobAssignmentId,
    String jobRunId,
    String projectId,
    String workspaceId,
    String runType
) {
    public static final OutputArtifactContext EMPTY = new OutputArtifactContext(null, null, null, null, null, null, null);

    public OutputArtifactContext(
        String agentId,
        String jobId,
        String projectId,
        String workspaceId,
        String runType
    ) {
        this(agentId, jobId, null, null, projectId, workspaceId, runType);
    }

    public OutputArtifactContext {
        agentId = normalize(agentId);
        jobId = normalize(jobId);
        jobAssignmentId = normalize(jobAssignmentId);
        jobRunId = normalize(jobRunId);
        projectId = normalize(projectId);
        workspaceId = normalize(workspaceId);
        runType = normalize(runType);
    }

    public boolean isEmpty() {
        return !StringUtils.hasText(agentId)
            && !StringUtils.hasText(jobId)
            && !StringUtils.hasText(jobAssignmentId)
            && !StringUtils.hasText(jobRunId)
            && !StringUtils.hasText(projectId)
            && !StringUtils.hasText(workspaceId)
            && !StringUtils.hasText(runType);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
