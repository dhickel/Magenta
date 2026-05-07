package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

public record WorkspaceLink(
    String id,
    String workspaceId,
    String label,
    WorkspaceLinkType linkType,
    String target,
    boolean readable,
    boolean writable,
    Instant createdAt,
    Instant updatedAt
) {
}
