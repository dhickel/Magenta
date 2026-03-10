package io.mindspice.magenta.runtime.tools;

import java.nio.file.Path;

public final class ToolPathSupport {

    private ToolPathSupport() {
    }

    public static Path resolveWorkspacePath(Path workspaceRoot, String pathText) {
        return resolveWorkspacePath(workspaceRoot, true, pathText);
    }

    public static Path resolveWorkspacePath(Path workspaceRoot, boolean enforceWorkspaceRoot, String pathText) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }

        Path path = Path.of(pathText.trim());
        if (!path.isAbsolute()) {
            path = workspaceRoot.resolve(path);
        }

        Path normalized = path.toAbsolutePath().normalize();
        if (enforceWorkspaceRoot && !normalized.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path is outside workspace root");
        }
        return normalized;
    }

    public static String displayPath(Path workspaceRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(workspaceRoot)) {
            String relative = workspaceRoot.relativize(normalized).toString().replace('\\', '/');
            return relative.isBlank() ? "." : relative;
        }
        return normalized.toString().replace('\\', '/');
    }
}
