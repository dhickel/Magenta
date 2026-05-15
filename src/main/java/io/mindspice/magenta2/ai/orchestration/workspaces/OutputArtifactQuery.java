package io.mindspice.magenta2.ai.orchestration.workspaces;

import org.springframework.util.StringUtils;

public record OutputArtifactQuery(
    String agentId,
    String jobId,
    String projectId,
    String workspaceId,
    String runId,
    String planId,
    String artifactType,
    int limit
) {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    public OutputArtifactQuery {
        agentId = normalize(agentId);
        jobId = normalize(jobId);
        projectId = normalize(projectId);
        workspaceId = normalize(workspaceId);
        runId = normalize(runId);
        planId = normalize(planId);
        artifactType = normalize(artifactType);
        limit = normalizeLimit(limit);
    }

    public static OutputArtifactQuery of(String agentId,
                                         String jobId,
                                         String projectId,
                                         String workspaceId,
                                         String runId,
                                         String planId,
                                         String artifactType,
                                         Integer limit) {
        return new OutputArtifactQuery(agentId, jobId, projectId, workspaceId, runId, planId, artifactType,
            limit == null ? DEFAULT_LIMIT : limit);
    }

    private static int normalizeLimit(int value) {
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
