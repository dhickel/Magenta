package io.mindspice.magenta2.ai.orchestration.runtime;

import org.springframework.util.StringUtils;

/**
 * Task execution context set by the orchestration runner and visible to
 * tool implementations (shell, file) during model-backed task execution.
 *
 * <p>Contains agent, job, project, and workspace identifiers plus the
 * host filesystem paths for the run workspace and output staging directories.
 * Tool implementations use this context to resolve working directories.
 * The legacy {@code hostWorkspacePath} field is retained as the active
 * run/assignment workspace path; {@code hostDurableWorkspacePath} is the
 * effective durable workspace root exposed as {@code workspace/}.
 * {@code hostJobWorkspacePath} is legacy compatibility only and should remain
 * unset in active runtime paths.
 */
public record OrchestrationTaskContext(
    String agentId,
    String agentName,
    String jobId,
    String jobAssignmentId,
    String jobRunId,
    String projectId,
    String workspaceId,
    String runType,
    String runDisplayName,
    String hostWorkspacePath,
    String hostOutputPath,
    String hostDurableWorkspacePath,
    String hostRunPath,
    String hostJobWorkspacePath,
    String hostRootPath,
    String selectedWorkAreaId,
    String outputRouteType,
    String outputWorkAreaId,
    String outputDirectRelativePath
) {
    public static final OrchestrationTaskContext EMPTY = new OrchestrationTaskContext(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    public OrchestrationTaskContext(
        String agentId,
        String agentName,
        String jobId,
        String projectId,
        String workspaceId,
        String runType,
        String hostWorkspacePath,
        String hostOutputPath
    ) {
        this(agentId, agentName, jobId, null, null, projectId, workspaceId, runType, null,
            hostWorkspacePath, hostOutputPath, null, hostWorkspacePath, null, null, null, null, null, null);
    }

    public OrchestrationTaskContext(
        String agentId,
        String agentName,
        String jobId,
        String projectId,
        String workspaceId,
        String runType,
        String hostWorkspacePath,
        String hostOutputPath,
        String hostDurableWorkspacePath,
        String hostRunPath
    ) {
        this(agentId, agentName, jobId, null, null, projectId, workspaceId, runType, null,
            hostWorkspacePath, hostOutputPath, hostDurableWorkspacePath, hostRunPath, null,
            hostDurableWorkspacePath, null, null, null, null);
    }

    public OrchestrationTaskContext(
        String agentId,
        String agentName,
        String jobId,
        String projectId,
        String workspaceId,
        String runType,
        String hostWorkspacePath,
        String hostOutputPath,
        String hostDurableWorkspacePath,
        String hostRunPath,
        String hostJobWorkspacePath
    ) {
        this(agentId, agentName, jobId, null, null, projectId, workspaceId, runType, null,
            hostWorkspacePath, hostOutputPath, hostDurableWorkspacePath, hostRunPath, hostJobWorkspacePath,
            hostDurableWorkspacePath, null, null, null, null);
    }

    public OrchestrationTaskContext(
        String agentId,
        String agentName,
        String jobId,
        String projectId,
        String workspaceId,
        String runType,
        String hostWorkspacePath,
        String hostOutputPath,
        String hostDurableWorkspacePath,
        String hostRunPath,
        String hostJobWorkspacePath,
        String hostRootPath,
        String selectedWorkAreaId,
        String outputRouteType,
        String outputWorkAreaId,
        String outputDirectRelativePath
    ) {
        this(agentId, agentName, jobId, projectId, workspaceId, runType, null,
            hostWorkspacePath, hostOutputPath, hostDurableWorkspacePath, hostRunPath, hostJobWorkspacePath,
            hostRootPath, selectedWorkAreaId, outputRouteType, outputWorkAreaId, outputDirectRelativePath);
    }

    public OrchestrationTaskContext(
        String agentId,
        String agentName,
        String jobId,
        String projectId,
        String workspaceId,
        String runType,
        String runDisplayName,
        String hostWorkspacePath,
        String hostOutputPath,
        String hostDurableWorkspacePath,
        String hostRunPath,
        String hostJobWorkspacePath,
        String hostRootPath,
        String selectedWorkAreaId,
        String outputRouteType,
        String outputWorkAreaId,
        String outputDirectRelativePath
    ) {
        this(agentId, agentName, jobId, null, null, projectId, workspaceId, runType, runDisplayName,
            hostWorkspacePath, hostOutputPath, hostDurableWorkspacePath, hostRunPath, hostJobWorkspacePath,
            hostRootPath, selectedWorkAreaId, outputRouteType, outputWorkAreaId, outputDirectRelativePath);
    }

    public OrchestrationTaskContext {
        agentId = normalize(agentId);
        agentName = normalize(agentName);
        jobId = normalize(jobId);
        jobAssignmentId = normalize(jobAssignmentId);
        jobRunId = normalize(jobRunId);
        projectId = normalize(projectId);
        workspaceId = normalize(workspaceId);
        runType = normalize(runType);
        runDisplayName = normalize(runDisplayName);
        hostWorkspacePath = normalize(hostWorkspacePath);
        hostOutputPath = normalize(hostOutputPath);
        hostDurableWorkspacePath = normalize(hostDurableWorkspacePath);
        hostRunPath = normalize(hostRunPath);
        hostJobWorkspacePath = normalize(hostJobWorkspacePath);
        hostRootPath = normalize(hostRootPath);
        selectedWorkAreaId = normalize(selectedWorkAreaId);
        outputRouteType = normalize(outputRouteType);
        outputWorkAreaId = normalize(outputWorkAreaId);
        outputDirectRelativePath = normalize(outputDirectRelativePath);
    }

    public boolean hasAgentContext() {
        return StringUtils.hasText(agentId);
    }

    public boolean hasContext() {
        return StringUtils.hasText(agentId) || StringUtils.hasText(jobId)
            || StringUtils.hasText(jobAssignmentId)
            || StringUtils.hasText(jobRunId)
            || StringUtils.hasText(projectId)
            || StringUtils.hasText(workspaceId);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public OrchestrationTaskContext withPaths(String hostWorkspacePath, String hostOutputPath) {
        return new OrchestrationTaskContext(
            agentId, agentName, jobId, jobAssignmentId, jobRunId, projectId, workspaceId, runType,
            runDisplayName, hostWorkspacePath, hostOutputPath, hostDurableWorkspacePath, hostWorkspacePath,
            hostJobWorkspacePath, hostRootPath, selectedWorkAreaId, outputRouteType, outputWorkAreaId,
            outputDirectRelativePath
        );
    }

    public OrchestrationTaskContext withExecutionPaths(
        String hostDurableWorkspacePath,
        String hostOutputPath,
        String hostRunPath
    ) {
        return new OrchestrationTaskContext(
            agentId, agentName, jobId, jobAssignmentId, jobRunId, projectId, workspaceId, runType,
            runDisplayName, hostRunPath, hostOutputPath, hostDurableWorkspacePath, hostRunPath,
            hostJobWorkspacePath, hostDurableWorkspacePath, selectedWorkAreaId, outputRouteType,
            outputWorkAreaId, outputDirectRelativePath
        );
    }

    public OrchestrationTaskContext withExecutionPaths(
        String hostDurableWorkspacePath,
        String hostOutputPath,
        String hostRunPath,
        String hostRootPath
    ) {
        return new OrchestrationTaskContext(
            agentId, agentName, jobId, jobAssignmentId, jobRunId, projectId, workspaceId, runType,
            runDisplayName, hostRunPath, hostOutputPath, hostDurableWorkspacePath, hostRunPath,
            hostJobWorkspacePath, hostRootPath, selectedWorkAreaId, outputRouteType,
            outputWorkAreaId, outputDirectRelativePath
        );
    }

    @Deprecated
    public OrchestrationTaskContext withJobWorkspacePath(String hostJobWorkspacePath) {
        return new OrchestrationTaskContext(
            agentId, agentName, jobId, jobAssignmentId, jobRunId, projectId, workspaceId, runType,
            runDisplayName, hostWorkspacePath, hostOutputPath, hostDurableWorkspacePath, hostRunPath,
            hostJobWorkspacePath, hostRootPath, selectedWorkAreaId, outputRouteType, outputWorkAreaId,
            outputDirectRelativePath
        );
    }

    public OrchestrationTaskContext withJobRun(String jobAssignmentId, String jobRunId) {
        return new OrchestrationTaskContext(
            agentId, agentName, jobId, jobAssignmentId, jobRunId, projectId, workspaceId, runType,
            runDisplayName, hostWorkspacePath, hostOutputPath, hostDurableWorkspacePath, hostRunPath,
            hostJobWorkspacePath, hostRootPath, selectedWorkAreaId, outputRouteType, outputWorkAreaId,
            outputDirectRelativePath
        );
    }
}
