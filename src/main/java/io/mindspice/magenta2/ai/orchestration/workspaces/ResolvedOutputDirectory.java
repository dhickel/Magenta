package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.nio.file.Path;

public record ResolvedOutputDirectory(
    WorkspaceOwnerType workspaceOwnerType,
    String workspaceOwnerId,
    String agentId,
    String projectId,
    String workspaceId,
    Path workspaceRoot,
    Path outputDirectory,
    OutputArtifactContext artifactContext
) {
}
