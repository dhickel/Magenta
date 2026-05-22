package io.mindspice.magenta2.ai.orchestration.workspaces;

import org.springframework.util.StringUtils;

public record OutputPublicationTarget(
    OutputDirectoryKind kind,
    String workUnitId,
    String runId,
    String agentId,
    String projectId,
    String jobId,
    String jobAssignmentId,
    String jobRunId,
    String workspaceId
) {
    public OutputPublicationTarget {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        workUnitId = normalize(workUnitId);
        runId = normalize(runId);
        agentId = normalize(agentId);
        projectId = normalize(projectId);
        jobId = normalize(jobId);
        jobAssignmentId = normalize(jobAssignmentId);
        jobRunId = normalize(jobRunId);
        workspaceId = normalize(workspaceId);
    }

    public static OutputPublicationTarget task(
        String taskId,
        String runId,
        String agentId,
        String projectId,
        String workspaceId
    ) {
        return new OutputPublicationTarget(
            OutputDirectoryKind.TASK, taskId, runId, agentId, projectId, null, null, null, workspaceId);
    }

    public static OutputPublicationTarget workflow(
        String workflowId,
        String runId,
        String agentId,
        String projectId,
        String workspaceId
    ) {
        return new OutputPublicationTarget(
            OutputDirectoryKind.WORKFLOW, workflowId, runId, agentId, projectId, null, null, null, workspaceId);
    }

    public static OutputPublicationTarget job(
        String jobId,
        String jobAssignmentId,
        String jobRunId,
        String agentId,
        String projectId,
        String workspaceId
    ) {
        return new OutputPublicationTarget(
            OutputDirectoryKind.JOB, null, null, agentId, projectId, jobId, jobAssignmentId, jobRunId, workspaceId);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
