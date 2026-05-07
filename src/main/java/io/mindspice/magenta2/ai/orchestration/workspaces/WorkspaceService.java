package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkspaceService {
    private final WorkspaceRepository repository;
    private final Path dataRoot;

    public WorkspaceService(WorkspaceRepository repository, AiConfig aiConfig) throws IOException {
        this.repository = repository;
        if (aiConfig == null || aiConfig.dataRoot() == null) {
            throw new IllegalArgumentException("AI config dataRoot is required for workspaces");
        }
        this.dataRoot = Files.createDirectories(aiConfig.dataRoot()).toRealPath();
    }

    public Workspace get(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("Workspace not found: " + id));
    }

    public Workspace agentWorkspace(String agentId, String displayName) {
        return repository.findByOwner(WorkspaceOwnerType.AGENT, agentId)
            .orElseGet(() -> createWorkspace(WorkspaceOwnerType.AGENT, agentId, "agents/" + agentId, displayName));
    }

    public Workspace jobWorkspace(String jobId, String displayName) {
        return repository.findByOwner(WorkspaceOwnerType.JOB, jobId)
            .orElseGet(() -> createWorkspace(WorkspaceOwnerType.JOB, jobId, "jobs/" + jobId, displayName));
    }

    public Path assignmentPath(String agentId, String assignmentId) {
        return confined("agents/" + agentId + "/work/" + assignmentId);
    }

    public List<WorkspaceLink> links(String workspaceId) {
        get(workspaceId);
        return repository.links(workspaceId);
    }

    public WorkspaceLink addLink(String workspaceId, WorkspaceLink link) {
        Workspace workspace = get(workspaceId);
        if (!StringUtils.hasText(link.label())) {
            throw new IllegalArgumentException("workspace link label is required");
        }
        WorkspaceLinkType type = link.linkType() == null ? WorkspaceLinkType.PATH : link.linkType();
        validateLinkTarget(workspace, type, link.target());
        return repository.saveLink(new WorkspaceLink(
            StringUtils.hasText(link.id()) ? link.id() : UUID.randomUUID().toString(),
            workspaceId,
            link.label(),
            type,
            link.target(),
            link.readable(),
            link.writable(),
            null,
            null
        ));
    }

    public void deleteLink(String workspaceId, String linkId) {
        get(workspaceId);
        repository.deleteLink(workspaceId, linkId);
    }

    private Workspace createWorkspace(
        WorkspaceOwnerType ownerType,
        String ownerId,
        String rootRelativePath,
        String displayName
    ) {
        if (!StringUtils.hasText(ownerId)) {
            throw new IllegalArgumentException("workspace owner id is required");
        }
        Path root = confined(rootRelativePath);
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create workspace root: " + root, exception);
        }
        return repository.save(new Workspace(
            UUID.randomUUID().toString(),
            ownerType,
            ownerId,
            rootRelativePath,
            StringUtils.hasText(displayName) ? displayName : ownerType.name().toLowerCase() + " " + ownerId,
            "{}",
            null,
            null
        ));
    }

    private void validateLinkTarget(Workspace workspace, WorkspaceLinkType type, String target) {
        if (!StringUtils.hasText(target)) {
            throw new IllegalArgumentException("workspace link target is required");
        }
        if (type == WorkspaceLinkType.PATH) {
            Path path = Path.of(target);
            Path root = confined(workspace.rootRelativePath());
            Path resolved = path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
            if (!resolved.startsWith(dataRoot)) {
                throw new IllegalArgumentException("workspace link target escapes data root");
            }
        }
    }

    private Path confined(String relativePath) {
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("workspace path must be relative to data root");
        }
        Path resolved = dataRoot.resolve(relative).normalize();
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("workspace path escapes data root");
        }
        return resolved;
    }
}
