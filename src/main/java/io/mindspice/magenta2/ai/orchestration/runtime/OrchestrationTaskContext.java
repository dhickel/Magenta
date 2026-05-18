package io.mindspice.magenta2.ai.orchestration.runtime;

import org.springframework.util.StringUtils;

/**
 * Task execution context set by the orchestration runner and visible to
 * tool implementations (shell, file) during model-backed task execution.
 *
 * <p>Contains agent, job, project, and workspace identifiers plus the
 * host filesystem paths for the task workspace and output directories.
 * Tool implementations use this context to resolve working directories
 * within the agent workspace.
 */
public record OrchestrationTaskContext(
    String agentId,
    String agentName,
    String jobId,
    String projectId,
    String workspaceId,
    String runType,
    String hostWorkspacePath,
    String hostOutputPath
) {
    public static final OrchestrationTaskContext EMPTY = new OrchestrationTaskContext(
        null, null, null, null, null, null, null, null);

    public OrchestrationTaskContext {
        agentId = normalize(agentId);
        agentName = normalize(agentName);
        jobId = normalize(jobId);
        projectId = normalize(projectId);
        workspaceId = normalize(workspaceId);
        runType = normalize(runType);
        hostWorkspacePath = normalize(hostWorkspacePath);
        hostOutputPath = normalize(hostOutputPath);
    }

    public boolean hasAgentContext() {
        return StringUtils.hasText(agentId);
    }

    public boolean hasContext() {
        return StringUtils.hasText(agentId) || StringUtils.hasText(jobId)
            || StringUtils.hasText(projectId)
            || StringUtils.hasText(workspaceId);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public OrchestrationTaskContext withPaths(String hostWorkspacePath, String hostOutputPath) {
        return new OrchestrationTaskContext(
            agentId, agentName, jobId, projectId, workspaceId, runType,
            hostWorkspacePath, hostOutputPath
        );
    }
}
