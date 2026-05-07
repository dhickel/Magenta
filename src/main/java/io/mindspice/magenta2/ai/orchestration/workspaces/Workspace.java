package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

public record Workspace(
    String id,
    WorkspaceOwnerType ownerType,
    String ownerId,
    String rootRelativePath,
    String displayName,
    String metadataJson,
    Instant createdAt,
    Instant updatedAt
) {
}
