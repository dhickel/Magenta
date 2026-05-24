package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

public record WorkspaceFileLabelAssignment(
    String id,
    String workspaceId,
    WorkspaceOwnerType ownerType,
    String ownerId,
    String rootRelativePath,
    String fileRelativePath,
    WorkspaceFileLabel label,
    String metadataJson,
    Instant createdAt,
    Instant updatedAt
) {
}
