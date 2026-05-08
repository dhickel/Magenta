package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkspaceLink(
    String id,
    String workspaceId,
    @NotBlank String label,
    @NotNull WorkspaceLinkType linkType,
    @NotBlank String target,
    boolean readable,
    boolean writable,
    Instant createdAt,
    Instant updatedAt
) {
}
