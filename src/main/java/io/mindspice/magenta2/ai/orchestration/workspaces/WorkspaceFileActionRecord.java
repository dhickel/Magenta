package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

public record WorkspaceFileActionRecord(
    String id,
    String workspaceId,
    WorkspaceOwnerType ownerType,
    String ownerId,
    String workAreaId,
    String actorType,
    String actorId,
    WorkspaceFileActionType actionType,
    String sourceRelativePath,
    String targetRelativePath,
    String result,
    String payloadJson,
    Instant createdAt
) {
}
