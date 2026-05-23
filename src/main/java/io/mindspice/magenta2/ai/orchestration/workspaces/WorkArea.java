package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

public record WorkArea(
    String id,
    WorkspaceOwnerType ownerType,
    String ownerId,
    String workspaceId,
    String rootRelativePath,
    String areaRelativePath,
    String displayName,
    boolean system,
    boolean home,
    boolean active,
    String metadataJson,
    Instant createdAt,
    Instant updatedAt
) {
}
