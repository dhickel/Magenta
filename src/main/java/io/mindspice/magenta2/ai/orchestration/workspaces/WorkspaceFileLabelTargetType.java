package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.util.Locale;

public enum WorkspaceFileLabelTargetType {
    FILE("file"),
    DIRECTORY("directory");

    private final String wireName;

    WorkspaceFileLabelTargetType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static WorkspaceFileLabelTargetType forDirectory(boolean directory) {
        return directory ? DIRECTORY : FILE;
    }

    public static WorkspaceFileLabelTargetType fromWireName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("tag target type is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "file" -> FILE;
            case "directory", "dir", "folder" -> DIRECTORY;
            default -> throw new IllegalArgumentException("unknown tag target type: " + value);
        };
    }
}
