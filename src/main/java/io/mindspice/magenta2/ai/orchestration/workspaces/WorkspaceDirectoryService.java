package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Manages filesystem paths for all workspace types. Every path is resolved
 * relative to the configured {@code dataRoot} and confined so it cannot
 * escape via {@code ..} traversal.
 *
 * <h3>Layout</h3>
 * <ul>
 *   <li>Agent home: {@code data/agents/{agentId}/home}</li>
 *   <li>Agent outputs: {@code data/agents/{agentId}/outputs/{slug}-{runId}/}</li>
 *   <li>Task temp: {@code data/runtime/task-runs/{runId}}</li>
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

    public Path agentHome(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/home"));
    }

    public Path agentWorkspaceRoot(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId));
    }

    public Path agentOutputRoot(String agentId) {
        requireId(agentId, "agentId");
        return ensureDir(confined("agents/" + agentId + "/outputs"));
    }

    /**
     * Output directory for an agent's run. Never deleted after terminal state.
     * Layout: data/agents/{agentId}/outputs/{slug}-{runId}/
     */
    public Path agentOutput(String agentId, String planSlug, String runId) {
        requireId(agentId, "agentId");
        requireId(runId, "runId");
        String slug = StringUtils.hasText(planSlug) ? sanitize(planSlug) : "run";
        return ensureDir(confined("agents/" + agentId + "/outputs/" + slug + "-" + runId));
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

    private Path ensureDir(Path path) {
        try {
            return Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory: " + path, e);
        }
    }

    private void requireId(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_.-]", "_").replaceAll("_+", "_");
    }
}
