package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Manages exclusive writable leases on workspace roots. Only one active
 * writable lease per workspace at a time. Extension must verify holder
 * ownership.
 */
@Service
public class WorkspaceLeaseService {
    private final WorkspaceRepository repository;

    public WorkspaceLeaseService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    /**
     * Acquire a writable lease on a workspace. Fails if an active writable
     * lease already exists for this workspace.
     *
     * <p>Acquisition is atomic at the database level — the unique partial
     * index on workspace_leases guarantees at most one active WRITE lease
     * per workspace, even under concurrent callers.
     *
     * @param workspaceId workspace root id
     * @param holderType  e.g. "TASK_RUN", "WORKFLOW_RUN", "JOB_RUN"
     * @param holderId    the run or job id holding the lease
     * @param duration    how long the lease is valid
     * @return the new active lease
     * @throws IllegalStateException if an active writable lease conflicts
     */
    public WorkspaceLease acquireWritable(String workspaceId, String holderType,
                                          String holderId, Duration duration) {
        requireId(workspaceId, "workspaceId");
        requireId(holderType, "holderType");
        requireId(holderId, "holderId");
        Duration effectiveDuration = duration == null ? Duration.ofHours(1) : duration;

        // Verify workspace root exists
        repository.findRootById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace root not found: " + workspaceId));

        Instant now = Instant.now();
        WorkspaceLease lease = new WorkspaceLease(
            UUID.randomUUID().toString(),
            workspaceId,
            holderType,
            holderId,
            LeaseMode.WRITE,
            now.plus(effectiveDuration),
            null,
            now,
            now
        );

        // Atomic insert — the unique partial index rejects a second active
        // WRITE lease for the same workspace at the database level.
        return repository.insertWritableLease(lease)
            .orElseGet(() -> {
                // Conflict: another active writable lease already exists.
                // Fetch it to include holder details in the error message.
                WorkspaceLease existing = repository.findActiveWritableLease(workspaceId)
                    .orElseThrow(() -> new IllegalStateException(
                        "Workspace is already leased for write access but existing lease not found"));
                throw new IllegalStateException(
                    "Workspace is already leased for write access by " + existing.holderType()
                    + ":" + existing.holderId());
            });
    }

    /**
     * Acquire a read lease on a workspace. Non-exclusive — multiple read
     * leases can coexist.
     */
    public WorkspaceLease acquireRead(String workspaceId, String holderType,
                                      String holderId, Duration duration) {
        requireId(workspaceId, "workspaceId");
        requireId(holderType, "holderType");
        requireId(holderId, "holderId");
        Duration effectiveDuration = duration == null ? Duration.ofHours(1) : duration;

        repository.findRootById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace root not found: " + workspaceId));

        Instant now = Instant.now();
        return repository.saveLease(new WorkspaceLease(
            UUID.randomUUID().toString(),
            workspaceId,
            holderType,
            holderId,
            LeaseMode.READ,
            now.plus(effectiveDuration),
            null,
            now,
            now
        ));
    }

    /**
     * Extend a lease. Only the original holder can extend.
     *
     * @param leaseId       the lease to extend
     * @param holderId      must match the current holder
     * @param newDuration   how long from now the lease should last
     * @return the updated lease
     * @throws IllegalStateException if the lease is not active or holder mismatch
     */
    public WorkspaceLease extendLease(String leaseId, String holderId, Duration newDuration) {
        WorkspaceLease existing = repository.findLeaseById(leaseId)
            .orElseThrow(() -> new IllegalStateException("Lease not found: " + leaseId));
        if (!existing.isActive()) {
            throw new IllegalStateException("Lease is not active: " + leaseId);
        }
        if (!existing.holderId().equals(holderId)) {
            throw new IllegalStateException(
                "Only the current holder can extend the lease. Current: " + existing.holderId()
                + ", requested: " + holderId);
        }
        Duration effectiveDuration = newDuration == null ? Duration.ofHours(1) : newDuration;
        return repository.saveLease(existing.withExtended(Instant.now().plus(effectiveDuration)));
    }

    /**
     * Release a lease. Only the original holder or a parent run can release.
     */
    public void release(String leaseId, String holderId) {
        WorkspaceLease existing = repository.findLeaseById(leaseId)
            .orElseThrow(() -> new IllegalStateException("Lease not found: " + leaseId));
        if (existing.releasedAt() != null) {
            return; // already released
        }
        if (!existing.holderId().equals(holderId)) {
            throw new IllegalStateException(
                "Only the current holder can release the lease. Current: " + existing.holderId()
                + ", requested: " + holderId);
        }
        repository.releaseLease(leaseId);
    }

    /**
     * Release all active leases held by a given holder.
     */
    public void releaseAllFor(String holderType, String holderId) {
        List<WorkspaceLease> active = repository.findActiveLeases(holderType, holderId);
        for (WorkspaceLease lease : active) {
            repository.releaseLease(lease.id());
        }
    }

    /**
     * Check if a holder has an active writable lease on a workspace.
     */
    public boolean hasWritableLease(String workspaceId, String holderId) {
        return repository.findActiveWritableLease(workspaceId)
            .map(lease -> lease.isActive() && lease.holderId().equals(holderId))
            .orElse(false);
    }

    public WorkspaceLease getLease(String leaseId) {
        return repository.findLeaseById(leaseId)
            .orElseThrow(() -> new IllegalStateException("Lease not found: " + leaseId));
    }

    private void requireId(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
