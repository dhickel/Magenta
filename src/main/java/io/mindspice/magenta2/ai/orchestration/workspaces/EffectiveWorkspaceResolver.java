package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.nio.file.Path;

import io.mindspice.magenta2.core.util.PlainPathSegmentValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Centralizes durable workspace selection for executable work.
 *
 * <p>A project context owns the effective durable workspace. Without a project,
 * the executing agent owns the effective durable workspace. Existing
 * compatibility {@code workspaceId} values are intentionally not interpreted as
 * project ids.
 */
@Service
public class EffectiveWorkspaceResolver {
    private final WorkspaceDirectoryService directoryService;
    private final WorkspaceService workspaceService;

    public EffectiveWorkspaceResolver(
        WorkspaceDirectoryService directoryService,
        WorkspaceService workspaceService
    ) {
        this.directoryService = directoryService;
        this.workspaceService = workspaceService;
    }

    public EffectiveWorkspace resolve(String agentId, String projectId) {
        String cleanAgentId = normalize(agentId);
        String cleanProjectId = normalize(projectId);
        if (StringUtils.hasText(cleanProjectId)) {
            PlainPathSegmentValidator.requirePlainSegment(cleanProjectId, "projectId");
            if (StringUtils.hasText(cleanAgentId)) {
                PlainPathSegmentValidator.requirePlainSegment(cleanAgentId, "agentId");
            }
            Workspace workspace = workspaceService.projectWorkspace(cleanProjectId, "Project " + cleanProjectId);
            Path root = directoryService.projectWorkspaceRoot(cleanProjectId);
            return resolved(
                WorkspaceOwnerType.PROJECT,
                cleanProjectId,
                cleanAgentId,
                cleanProjectId,
                workspace.id(),
                root
            );
        }

        if (!StringUtils.hasText(cleanAgentId)) {
            throw new IllegalArgumentException("agentId is required when projectId is absent");
        }
        PlainPathSegmentValidator.requirePlainSegment(cleanAgentId, "agentId");
        Workspace workspace = workspaceService.agentWorkspace(cleanAgentId, "Agent " + cleanAgentId);
        Path root = directoryService.agentWorkspaceRoot(cleanAgentId);
        return resolved(
            WorkspaceOwnerType.AGENT,
            cleanAgentId,
            cleanAgentId,
            null,
            workspace.id(),
            root
        );
    }

    private EffectiveWorkspace resolved(
        WorkspaceOwnerType ownerType,
        String ownerId,
        String agentId,
        String projectId,
        String workspaceId,
        Path root
    ) {
        return new EffectiveWorkspace(
            ownerType,
            ownerId,
            agentId,
            projectId,
            workspaceId,
            root,
            directoryService.workDir(root),
            directoryService.outputsDir(root),
            directoryService.runsDir(root),
            directoryService.scratchDir(root)
        );
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
