package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

/**
 * A timed lease on a workspace root. Only one active writable lease per
 * workspace at a time. Extension must verify holder ownership.
 * {@code releasedAt} is set when the lease is released; null means active.
 */
public record WorkspaceLease(
    String id,
    String workspaceId,
    String holderType,
    String holderId,
    LeaseMode mode,
    Instant expiresAt,
    boolean releaseRequested,
    Instant releasedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean isActive() {
        return releasedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    public boolean isExpired() {
        return releasedAt == null && expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    public WorkspaceLease withReleased() {
        return new WorkspaceLease(id, workspaceId, holderType, holderId, mode,
            expiresAt, releaseRequested, Instant.now(), createdAt, Instant.now());
    }

    public WorkspaceLease withExtended(Instant newExpiresAt) {
        return new WorkspaceLease(id, workspaceId, holderType, holderId, mode,
            newExpiresAt, releaseRequested, releasedAt, createdAt, Instant.now());
    }

    public WorkspaceLease withReleaseRequested() {
        return new WorkspaceLease(id, workspaceId, holderType, holderId, mode,
            expiresAt, true, releasedAt, createdAt, Instant.now());
    }
}
