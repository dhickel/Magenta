package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

/**
 * A managed workspace root directory owned by an agent, job, or project.
 * The {@code rootRelativePath} is resolved against the configured
 * {@code dataRoot} at runtime.
 */
public record WorkspaceRoot(
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
