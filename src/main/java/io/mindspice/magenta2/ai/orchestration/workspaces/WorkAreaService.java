package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkAreaService {
    private static final String HOME_AREA = "home";

    private final WorkAreaRepository repository;
    private final WorkspaceService workspaceService;
    private final WorkspaceDirectoryService directoryService;

    public WorkAreaService(
        WorkAreaRepository repository,
        WorkspaceService workspaceService,
        WorkspaceDirectoryService directoryService
    ) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.directoryService = directoryService;
    }

    public WorkArea get(String workAreaId) {
        return repository.findById(workAreaId)
            .orElseThrow(() -> new IllegalArgumentException("work area not found: " + workAreaId));
    }

    public List<WorkArea> list(WorkspaceOwnerType ownerType, String ownerId, boolean includeInactive) {
        requireSupportedOwner(ownerType);
        requireOwnerId(ownerId);
        ensureHome(ownerType, ownerId, null);
        return repository.findByOwner(ownerType, ownerId, includeInactive);
    }

    public WorkArea ensureHome(WorkspaceOwnerType ownerType, String ownerId, String displayName) {
        requireSupportedOwner(ownerType);
        requireOwnerId(ownerId);
        return repository.findHome(ownerType, ownerId)
            .orElseGet(() -> createHome(ownerType, ownerId, displayName));
    }

    public WorkArea markDirectory(
        WorkspaceOwnerType ownerType,
        String ownerId,
        String areaRelativePath,
        String displayName
    ) {
        requireSupportedOwner(ownerType);
        requireOwnerId(ownerId);
        Workspace workspace = workspace(ownerType, ownerId, null);
        String normalizedArea = normalizeAreaRelativePath(areaRelativePath);
        Path areaPath = requireExistingDirectory(workspace, normalizedArea);
        String resolvedDisplay = StringUtils.hasText(displayName)
            ? displayName.trim()
            : areaPath.getFileName().toString();

        return repository.findByOwnerAndPath(ownerType, ownerId, normalizedArea)
            .map(existing -> repository.save(new WorkArea(
                existing.id(),
                existing.ownerType(),
                existing.ownerId(),
                workspace.id(),
                workspace.rootRelativePath(),
                existing.areaRelativePath(),
                resolvedDisplay,
                existing.system(),
                existing.home(),
                true,
                existing.metadataJson(),
                existing.createdAt(),
                existing.updatedAt()
            )))
            .orElseGet(() -> repository.save(new WorkArea(
                UUID.randomUUID().toString(),
                ownerType,
                ownerId,
                workspace.id(),
                workspace.rootRelativePath(),
                normalizedArea,
                resolvedDisplay,
                false,
                HOME_AREA.equals(normalizedArea),
                true,
                "{}",
                null,
                null
            )));
    }

    public WorkArea unmark(String workAreaId) {
        WorkArea workArea = get(workAreaId);
        if (workArea.system() || workArea.home()) {
            throw new IllegalArgumentException("system and Home Work Areas cannot be unmarked");
        }
        if (repository.hasActiveAssignment(workAreaId) || repository.hasActiveOutputTarget(workAreaId)) {
            throw new IllegalArgumentException("Work Area is active in queued or running work: " + workAreaId);
        }
        return repository.deactivate(workAreaId);
    }

    public Path resolve(WorkArea workArea) {
        if (workArea == null) {
            throw new IllegalArgumentException("work area is required");
        }
        Path root = rootPath(workArea.rootRelativePath());
        return requireExistingDirectory(root, workArea.areaRelativePath(), "work area");
    }

    private WorkArea createHome(WorkspaceOwnerType ownerType, String ownerId, String displayName) {
        Workspace workspace = workspace(ownerType, ownerId, displayName);
        Path root = rootPath(workspace.rootRelativePath());
        Path home = root.resolve(HOME_AREA).normalize();
        if (!home.startsWith(root)) {
            throw new IllegalArgumentException("Home Work Area escapes workspace root");
        }
        try {
            Files.createDirectories(home);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create Home Work Area: " + home, exception);
        }
        return repository.save(new WorkArea(
            UUID.randomUUID().toString(),
            ownerType,
            ownerId,
            workspace.id(),
            workspace.rootRelativePath(),
            HOME_AREA,
            StringUtils.hasText(displayName) ? displayName.trim() : "Home",
            true,
            true,
            true,
            "{}",
            null,
            null
        ));
    }

    private Workspace workspace(WorkspaceOwnerType ownerType, String ownerId, String displayName) {
        return switch (ownerType) {
            case AGENT -> workspaceService.agentWorkspace(ownerId, displayName);
            case PROJECT -> workspaceService.projectWorkspace(ownerId, displayName);
            case JOB -> throw new IllegalArgumentException("Work Areas are supported for agent and project roots only");
        };
    }

    private Path requireExistingDirectory(Workspace workspace, String areaRelativePath) {
        Path root = rootPath(workspace.rootRelativePath());
        return requireExistingDirectory(root, areaRelativePath, "work area directory");
    }

    private Path requireExistingDirectory(Path root, String areaRelativePath, String label) {
        try {
            Path resolved = root.resolve(areaRelativePath).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException(label + " escapes workspace root");
            }
            Path realRoot = root.toRealPath();
            Path real = resolved.toRealPath();
            if (!real.startsWith(realRoot)) {
                throw new IllegalArgumentException(label + " escapes workspace root");
            }
            if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(label + " is not a directory");
            }
            return real;
        } catch (IOException exception) {
            throw new IllegalArgumentException(label + " does not exist: " + areaRelativePath, exception);
        }
    }

    private Path rootPath(String rootRelativePath) {
        Path root = directoryService.dataRoot().resolve(rootRelativePath).normalize();
        if (!root.startsWith(directoryService.dataRoot())) {
            throw new IllegalArgumentException("workspace root escapes data root");
        }
        try {
            Path realRoot = Files.createDirectories(root).toRealPath();
            if (!realRoot.startsWith(directoryService.dataRoot())) {
                throw new IllegalArgumentException("workspace root escapes data root");
            }
            return realRoot;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to resolve workspace root: " + rootRelativePath, exception);
        }
    }

    private String normalizeAreaRelativePath(String areaRelativePath) {
        if (!StringUtils.hasText(areaRelativePath)) {
            throw new IllegalArgumentException("work area path is required");
        }
        String normalizedText = areaRelativePath.replace('\\', '/').trim();
        Path path = Path.of(normalizedText).normalize();
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("work area path must be relative to the workspace root");
        }
        if (path.toString().isBlank() || ".".equals(path.toString())) {
            throw new IllegalArgumentException("workspace root cannot be marked as a Work Area");
        }
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("work area path escapes workspace root");
            }
        }
        return path.toString().replace('\\', '/');
    }

    private void requireSupportedOwner(WorkspaceOwnerType ownerType) {
        if (ownerType != WorkspaceOwnerType.AGENT && ownerType != WorkspaceOwnerType.PROJECT) {
            throw new IllegalArgumentException("Work Areas are supported for agent and project roots only");
        }
    }

    private void requireOwnerId(String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            throw new IllegalArgumentException("work area owner id is required");
        }
    }
}
