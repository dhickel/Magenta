package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;

import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.core.util.PlainPathSegmentValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Manages filesystem paths for all workspace types. Every path is resolved
 * relative to the configured {@code dataRoot} and confined so it cannot
 * escape via {@code ..} traversal.
 *
 * <h3>Layout</h3>
 * <ul>
 *   <li>Agent workspace: {@code data/agents/{agentId}/workspace}</li>
 *   <li>Durable workspace layout: {@code work/}, {@code outputs/}, {@code runs/}, {@code scratch/}</li>
 *   <li>Agent outputs: {@code data/agents/{agentId}/workspace/outputs/{slug}-{runId}/}</li>
 *   <li>Agent project links: {@code data/agents/{agentId}/workspace/projects/{projectId}}</li>
 *   <li>Agent scratch: {@code data/agents/{agentId}/workspace/scratch}</li>
 *   <li>Task temp: {@code data/runtime/task-runs/{runId}}</li>
 *   <li>Chat files: {@code data/chats/{conversationId}/files}</li>
 *   <li>Workflow temp: {@code data/runtime/workflow-runs/{runId}}</li>
 *   <li>Job workspace: {@code data/jobs/{jobId}/workspace}</li>
 *   <li>Job outputs: {@code data/jobs/{jobId}/outputs/{slug}-{runId}/}</li>
 *   <li>Project workspace: {@code data/projects/{projectId}/workspace}</li>
 * </ul>
 */
@Service
public class WorkspaceDirectoryService {
    private final Path dataRoot;

    public WorkspaceDirectoryService(AiConfig aiConfig) throws IOException {
        if (aiConfig == null || aiConfig.dataRoot() == null) {
            throw new IllegalArgumentException("AI config dataRoot is required");
        }
        this.dataRoot = Files.createDirectories(aiConfig.dataRoot()).toRealPath();
    }

    // ── Agent ──

    /**
     * Agent execution root: {@code agents/<id>/workspace}.
     * Does not auto-migrate legacy directories; call
     * {@link #migrateLegacyAgentDirs(String)} explicitly when a warm
     * data root still contains pre-workspace directories.
     */
    public Path agentWorkspace(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/workspace"));
    }

    /**
     * @deprecated Replaced by {@link #agentWorkspace(String)}. Retained only
     * for warm data roots that still reference the pre-workspace layout.
     */
    @Deprecated
    public Path agentHome(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/home"));
    }

    public Path agentWorkspaceRoot(String agentId) {
        return agentWorkspace(agentId);
    }

    public Path agentWorkspaceOutputs(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/workspace/outputs"));
    }

    public Path agentProjectLinks(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/workspace/projects"));
    }

    public Path agentScratch(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/workspace/scratch"));
    }

    public Path workDir(Path workspaceRoot) {
        return ensureDir(confinedWorkspaceChild(workspaceRoot, "work"));
    }

    public Path outputsDir(Path workspaceRoot) {
        return ensureDir(confinedWorkspaceChild(workspaceRoot, "outputs"));
    }

    public Path runsDir(Path workspaceRoot) {
        return ensureDir(confinedWorkspaceChild(workspaceRoot, "runs"));
    }

    public Path scratchDir(Path workspaceRoot) {
        return ensureDir(confinedWorkspaceChild(workspaceRoot, "scratch"));
    }

    public Path taskOutput(Path workspaceRoot, String taskId, String runId) {
        requireId(taskId, "taskId");
        requireId(runId, "runId");
        return ensureDir(confinedWorkspaceChild(outputsDir(workspaceRoot), "tasks", taskId, runId));
    }

    public Path workflowOutput(Path workspaceRoot, String workflowId, String runId) {
        requireId(workflowId, "workflowId");
        requireId(runId, "runId");
        return ensureDir(confinedWorkspaceChild(outputsDir(workspaceRoot), "workflows", workflowId, runId));
    }

    public Path jobAssignmentOutput(Path workspaceRoot, String jobAssignmentId, String runId) {
        requireId(jobAssignmentId, "jobAssignmentId");
        requireId(runId, "runId");
        return ensureDir(confinedWorkspaceChild(outputsDir(workspaceRoot), "jobs", jobAssignmentId, runId));
    }

    /**
     * @deprecated Replaced by {@link #agentWorkspaceOutputs(String)}.
     * Retained only for warm data roots that still reference the legacy output layout.
     */
    @Deprecated
    public Path agentOutputRoot(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/outputs"));
    }

    /**
     * Output directory for an agent's run. Never deleted after terminal state.
     * Layout: data/agents/{agentId}/workspace/outputs/{slug}-{runId}/
     */
    public Path agentOutput(String agentId, String planSlug, String runId) {
        requireId(agentId, "agentId");
        requireId(runId, "runId");
        String slug = StringUtils.hasText(planSlug) ? sanitize(planSlug) : "run";
        return ensureDir(confined("agents/" + agentId + "/workspace/outputs/" + slug + "-" + runId));
    }

    /**
     * Migrates legacy {@code agents/<id>/home} and {@code agents/<id>/outputs}
     * into the workspace tree. Safe to call repeatedly — only performs work
     * when legacy directories exist and their workspace counterparts do not.
     */
    public void migrateLegacyAgentDirs(String agentId) {
        requireId(agentId, "agentId");
        Path workspaceDir = confined("agents/" + agentId + "/workspace");
        if (Files.exists(workspaceDir)) {
            return; // already migrated
        }
        Path legacyHome = confined("agents/" + agentId + "/home");
        Path legacyOutputs = confined("agents/" + agentId + "/outputs");
        try {
            if (Files.isDirectory(legacyHome)) {
                Files.createDirectories(workspaceDir.getParent());
                Files.move(legacyHome, workspaceDir);
            }
            if (Files.isDirectory(legacyOutputs)) {
                Path wsOutputs = workspaceDir.resolve("outputs");
                if (!Files.exists(wsOutputs)) {
                    Files.createDirectories(wsOutputs.getParent());
                    Files.move(legacyOutputs, wsOutputs);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to migrate legacy agent directories for " + agentId, e);
        }
    }

    // ── Task / Workflow temp ──

    /**
     * Temporary workspace for a single task run. Deleted after terminal state.
     * Layout: data/runtime/task-runs/{runId}
     */
    public Path taskTemp(String runId) {
        requireId(runId, "runId");
        return ensureDir(confined("runtime/task-runs/" + runId));
    }

    /**
     * Returns the path for a task temp directory without creating it.
     * Use for cleanup or path computation when the directory may not exist.
     */
    public String taskTempPath(String runId) {
        requireId(runId, "runId");
        return confined("runtime/task-runs/" + runId).toString();
    }

    /**
     * Temporary workspace for a workflow run. Persists between workflow steps,
     * deleted after terminal workflow completion.
     * Layout: data/runtime/workflow-runs/{runId}
     */
    public Path workflowTemp(String runId) {
        requireId(runId, "runId");
        return ensureDir(confined("runtime/workflow-runs/" + runId));
    }

    /**
     * Persistent file directory for an ordinary chat conversation. This is not
     * task temp and is never auto-deleted by run cleanup.
     */
    public Path chatFiles(String conversationId) {
        requireId(conversationId, "conversationId");
        return ensureDir(confined("chats/" + conversationId + "/files"));
    }

    // ── Job ──

    public Path jobWorkspace(String jobId) {
        requireId(jobId, "jobId");
        return ensureDir(confined("jobs/" + jobId + "/workspace"));
    }

    public Path jobOutput(String jobId, String planSlug, String runId) {
        requireId(jobId, "jobId");
        requireId(runId, "runId");
        String slug = StringUtils.hasText(planSlug) ? sanitize(planSlug) : "run";
        return ensureDir(confined("jobs/" + jobId + "/outputs/" + slug + "-" + runId));
    }

    // ── Project ──

    public Path projectWorkspace(String projectId) {
        requireId(projectId, "projectId");
        return ensureDir(confined("projects/" + projectId + "/workspace"));
    }

    public Path projectWorkspaceRoot(String projectId) {
        return projectWorkspace(projectId);
    }

    /**
     * Materializes the current project workspace under an assignment temp
     * workspace as {@code projects/<projectId>}. The returned path is the
     * visible assignment-relative link; callers should remove it before
     * releasing the corresponding project workspace lease.
     */
    public Path materializeAssignmentProjectLink(String assignmentWorkspacePath, String projectId) {
        requireId(projectId, "projectId");
        try {
            Path assignmentWorkspace = existingConfinedDirectory(assignmentWorkspacePath, "assignment workspace");
            Path projectsDir = ensureDir(confinedChild(assignmentWorkspace, "projects"));
            Path link = confinedChild(projectsDir, projectId);
            Path target = projectWorkspace(projectId).toRealPath();
            removeExistingProjectLink(link, target);
            try {
                Files.createSymbolicLink(link, target);
            } catch (UnsupportedOperationException | FileSystemException e) {
                throw new IllegalStateException(
                    "Project workspace links require filesystem symlink support: " + link, e);
            }
            return link;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to materialize project workspace link for " + projectId, e);
        }
    }

    public Path requireAssignmentProjectLinkTarget(String assignmentWorkspacePath, String projectId) {
        requireId(projectId, "projectId");
        try {
            Path assignmentWorkspace = existingConfinedDirectory(assignmentWorkspacePath, "assignment workspace");
            Path projectsDir = confinedChild(assignmentWorkspace, "projects");
            Path link = confinedChild(projectsDir, projectId);
            if (!Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Project workspace is not materialized for this assignment: " + projectId);
            }
            Path expectedTarget = projectWorkspace(projectId).toRealPath();
            Path actualTarget = link.toRealPath();
            if (!actualTarget.equals(expectedTarget)) {
                throw new IllegalArgumentException("Project workspace link target does not match leased project: " + projectId);
            }
            return actualTarget;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve project workspace link for " + projectId, e);
        }
    }

    public void removeAssignmentProjectLink(String assignmentWorkspacePath, String projectId) {
        requireId(projectId, "projectId");
        if (!StringUtils.hasText(assignmentWorkspacePath)) {
            return;
        }
        Path assignmentWorkspace = Path.of(assignmentWorkspacePath).toAbsolutePath().normalize();
        if (!assignmentWorkspace.startsWith(dataRoot) || !Files.exists(assignmentWorkspace, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Path projectsDir = confinedChild(assignmentWorkspace, "projects");
            Path link = confinedChild(projectsDir, projectId);
            if (Files.isSymbolicLink(link)) {
                Files.deleteIfExists(link);
            } else if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Refusing to remove non-link project path: " + link);
            }
            try {
                Files.deleteIfExists(projectsDir);
            } catch (IOException ignored) {
                // Directory is not empty; leave other assignment-local project links alone.
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to remove project workspace link for " + projectId, e);
        }
    }

    // ── Input file resolution ──

    /**
     * Resolves a relative or workspace-absolute input path to a real file system
     * path. The input path must be confined under dataRoot. If the path is
     * absolute, it is normalized and checked. If relative, it is resolved
     * against the given base workspace directory.
     */
    public Path resolveInputPath(String baseWorkspaceDir, String inputPath) {
        if (!StringUtils.hasText(inputPath)) {
            throw new IllegalArgumentException("inputPath is required");
        }
        Path path = Path.of(inputPath);
        Path resolved;
        if (path.isAbsolute()) {
            resolved = path.normalize();
        } else {
            resolved = confined(baseWorkspaceDir).resolve(path).normalize();
        }
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("Input path escapes data root: " + inputPath);
        }
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("Input file does not exist: " + resolved);
        }
        return resolved;
    }

    // ── Cleanup ──

    /**
     * Deletes a temp directory recursively. Never deletes output directories.
     * Safe — rejects paths that are not under {@code data/runtime/}.
     */
    public void deleteTempDir(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        Path normalized = dir.toRealPath();
        Path runtimeDir = dataRoot.resolve("runtime").toRealPath();
        if (!normalized.startsWith(runtimeDir)) {
            throw new IllegalArgumentException(
                "Refusing to delete non-temp directory: " + normalized);
        }
        try (var stream = Files.walk(normalized)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
        }
    }

    // ── Helpers ──

    public Path dataRoot() {
        return dataRoot;
    }

    private Path confined(String relativePath) {
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Workspace path must be relative to data root");
        }
        Path resolved = dataRoot.resolve(relative).normalize();
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("Workspace path escapes data root: " + relativePath);
        }
        return resolved;
    }

    private Path existingConfinedDirectory(String path, String label) throws IOException {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException(label + " is required");
        }
        Path raw = Path.of(path);
        Path normalized = raw.isAbsolute() ? raw.normalize() : dataRoot.resolve(raw).normalize();
        if (!normalized.startsWith(dataRoot)) {
            throw new IllegalArgumentException(label + " escapes data root");
        }
        Path real = normalized.toRealPath();
        if (!real.startsWith(dataRoot)) {
            throw new IllegalArgumentException(label + " escapes data root");
        }
        if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " is not a directory");
        }
        return real;
    }

    private Path confinedChild(Path parent, String child) {
        Path resolved = parent.resolve(child).normalize();
        if (!resolved.startsWith(parent)) {
            throw new IllegalArgumentException("Workspace child path escapes parent: " + child);
        }
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("Workspace child path escapes data root: " + child);
        }
        return resolved;
    }

    private Path confinedWorkspaceChild(Path workspaceRoot, String first, String... more) {
        requireId(first, "workspaceChild");
        Path root = confinedWorkspaceRoot(workspaceRoot);
        Path resolved = root.resolve(first);
        for (String segment : more) {
            requireId(segment, "workspaceChild");
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Workspace child path escapes workspace root");
        }
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("Workspace child path escapes data root");
        }
        return resolved;
    }

    private Path confinedWorkspaceRoot(Path workspaceRoot) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot is required");
        }
        Path normalized = workspaceRoot.isAbsolute()
            ? workspaceRoot.normalize()
            : dataRoot.resolve(workspaceRoot).normalize();
        if (!normalized.startsWith(dataRoot)) {
            throw new IllegalArgumentException("workspaceRoot escapes data root");
        }
        try {
            Path realRoot = Files.createDirectories(normalized).toRealPath();
            if (!realRoot.startsWith(dataRoot)) {
                throw new IllegalArgumentException("workspaceRoot escapes data root");
            }
            return realRoot;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory: " + normalized, e);
        }
    }

    private void removeExistingProjectLink(Path link, Path expectedTarget) throws IOException {
        if (!Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isSymbolicLink(link)) {
            throw new IllegalStateException("Refusing to replace non-link project path: " + link);
        }
        Path actualTarget = link.toRealPath();
        if (!actualTarget.equals(expectedTarget)) {
            throw new IllegalStateException("Refusing to replace project link with unexpected target: " + link);
        }
        Files.delete(link);
    }

    private Path ensureDir(Path path) {
        try {
            return Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory: " + path, e);
        }
    }

    private void requireId(String value, String label) {
        PlainPathSegmentValidator.requirePlainSegment(value, label);
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_.-]", "_").replaceAll("_+", "_");
    }
}
