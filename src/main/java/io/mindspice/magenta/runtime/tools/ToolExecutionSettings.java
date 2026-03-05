package io.mindspice.magenta.runtime.tools;

import java.nio.file.Path;

public record ToolExecutionSettings(
        Path workspaceRoot,
        int maxToolOutputBytes,
        int maxFileReadLines,
        int maxSqlRows
) {
    public ToolExecutionSettings {
        workspaceRoot = workspaceRoot == null
                ? Path.of("").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
        if (maxToolOutputBytes <= 0) {
            throw new IllegalArgumentException("maxToolOutputBytes must be > 0");
        }
        if (maxFileReadLines <= 0) {
            throw new IllegalArgumentException("maxFileReadLines must be > 0");
        }
        if (maxSqlRows <= 0) {
            throw new IllegalArgumentException("maxSqlRows must be > 0");
        }
    }
}
