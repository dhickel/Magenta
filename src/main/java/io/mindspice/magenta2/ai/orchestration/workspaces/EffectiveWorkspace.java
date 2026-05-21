package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.nio.file.Path;

public record EffectiveWorkspace(
    WorkspaceOwnerType ownerType,
    String ownerId,
    String agentId,
    String projectId,
    String workspaceId,
    Path root,
    Path workDir,
    Path outputsDir,
    Path runsDir,
    Path scratchDir
) {
}
