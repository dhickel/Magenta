package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceLeaseServiceTest {
    private JdbcTemplate jdbcTemplate;
    private WorkspaceRepository repository;
    private WorkspaceLeaseService leaseService;
    private String workspaceId;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        repository = new WorkspaceRepository(jdbcTemplate);
        leaseService = new WorkspaceLeaseService(repository);
        workspaceId = UUID.randomUUID().toString();
        repository.saveRoot(new WorkspaceRoot(
            workspaceId, WorkspaceOwnerType.AGENT, "agent-1",
            "agents/agent-1", "Agent 1", "{}", Instant.now(), Instant.now()
        ));
    }

    @Test
    void acquireWritable_singleSucceeds() {
        WorkspaceLease lease = leaseService.acquireWritable(
            workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));

        assertThat(lease).isNotNull();
        assertThat(lease.workspaceId()).isEqualTo(workspaceId);
        assertThat(lease.mode()).isEqualTo(LeaseMode.WRITE);
        assertThat(lease.holderId()).isEqualTo("run-1");
        assertThat(lease.isActive()).isTrue();
        assertThat(lease.releasedAt()).isNull();
    }

    @Test
    void acquireWritable_secondConflicts() {
        leaseService.acquireWritable(workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));

        assertThatThrownBy(() ->
            leaseService.acquireWritable(workspaceId, "JOB_RUN", "run-2", Duration.ofMinutes(5))
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already leased for write access");
    }

    @Test
    void acquireWritable_concurrentSameWorkspace() throws Exception {
        AtomicReference<WorkspaceLease> winner = new AtomicReference<>();
        AtomicReference<Exception> loserError = new AtomicReference<>();

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            try {
                WorkspaceLease lease = leaseService.acquireWritable(
                    workspaceId, "TASK_RUN", "run-A", Duration.ofMinutes(5));
                if (winner.compareAndSet(null, lease)) {
                    // we won
                }
            } catch (Exception e) {
                loserError.compareAndSet(null, e);
            }
        });

        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            try {
                WorkspaceLease lease = leaseService.acquireWritable(
                    workspaceId, "JOB_RUN", "run-B", Duration.ofMinutes(5));
                if (winner.compareAndSet(null, lease)) {
                    // we won
                }
            } catch (Exception e) {
                loserError.compareAndSet(null, e);
            }
        });

        // Ensure both complete (they should be fast since we're using in-memory SQLite)
        t1.join();
        t2.join();

        // Exactly one must have won
        assertThat(winner.get()).isNotNull();
        assertThat(loserError.get()).isNotNull();

        // The winner's lease must be the only active writable lease
        WorkspaceLease active = repository.findActiveWritableLease(workspaceId).orElseThrow();
        assertThat(active.id()).isEqualTo(winner.get().id());

        // The loser must have an IllegalStateException
        assertThat(loserError.get())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already leased for write access");
    }

    @Test
    void acquireWritable_concurrentDifferentWorkspaces() {
        String workspace2 = UUID.randomUUID().toString();
        repository.saveRoot(new WorkspaceRoot(
            workspace2, WorkspaceOwnerType.JOB, "job-1",
            "jobs/job-1", "Job 1", "{}", Instant.now(), Instant.now()
        ));

        CompletableFuture<WorkspaceLease> f1 = CompletableFuture.supplyAsync(() ->
            leaseService.acquireWritable(workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5)));

        CompletableFuture<WorkspaceLease> f2 = CompletableFuture.supplyAsync(() ->
            leaseService.acquireWritable(workspace2, "JOB_RUN", "run-2", Duration.ofMinutes(5)));

        // Both should succeed — different workspaces
        CompletableFuture.allOf(f1, f2).join();

        WorkspaceLease lease1 = f1.join();
        WorkspaceLease lease2 = f2.join();

        assertThat(lease1.workspaceId()).isEqualTo(workspaceId);
        assertThat(lease2.workspaceId()).isEqualTo(workspace2);
        assertThat(lease1.isActive()).isTrue();
        assertThat(lease2.isActive()).isTrue();
    }

    @Test
    void releaseThenReacquire() {
        WorkspaceLease first = leaseService.acquireWritable(
            workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));

        // Release
        leaseService.release(first.id(), "run-1");
        WorkspaceLease released = repository.findLeaseById(first.id()).orElseThrow();
        assertThat(released.releasedAt()).isNotNull();
        assertThat(released.isActive()).isFalse();

        // Re-acquire should succeed
        WorkspaceLease second = leaseService.acquireWritable(
            workspaceId, "JOB_RUN", "run-2", Duration.ofMinutes(5));
        assertThat(second.workspaceId()).isEqualTo(workspaceId);
        assertThat(second.holderId()).isEqualTo("run-2");
        assertThat(second.isActive()).isTrue();
    }

    @Test
    void readLeasesCoexist() {
        // Multiple read leases on the same workspace must succeed
        WorkspaceLease read1 = leaseService.acquireRead(
            workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));
        WorkspaceLease read2 = leaseService.acquireRead(
            workspaceId, "TASK_RUN", "run-2", Duration.ofMinutes(5));
        WorkspaceLease read3 = leaseService.acquireRead(
            workspaceId, "JOB_RUN", "run-3", Duration.ofMinutes(5));

        assertThat(read1.mode()).isEqualTo(LeaseMode.READ);
        assertThat(read2.mode()).isEqualTo(LeaseMode.READ);
        assertThat(read3.mode()).isEqualTo(LeaseMode.READ);
        assertThat(read1.isActive()).isTrue();
        assertThat(read2.isActive()).isTrue();
        assertThat(read3.isActive()).isTrue();
    }

    @Test
    void writeAndReadCoexist() {
        // A write lease does not prevent a concurrent read lease
        leaseService.acquireWritable(workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));
        WorkspaceLease read = leaseService.acquireRead(
            workspaceId, "JOB_RUN", "run-2", Duration.ofMinutes(5));

        assertThat(read.mode()).isEqualTo(LeaseMode.READ);
        assertThat(read.isActive()).isTrue();
    }

    @Test
    void extendByWrongHolderFails() {
        WorkspaceLease lease = leaseService.acquireWritable(
            workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));

        assertThatThrownBy(() ->
            leaseService.extendLease(lease.id(), "intruder", Duration.ofMinutes(10))
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only the current holder");
    }

    @Test
    void extendExtendsExpiry() {
        WorkspaceLease lease = leaseService.acquireWritable(
            workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(1));

        Instant originalExpiry = lease.expiresAt();
        WorkspaceLease extended = leaseService.extendLease(lease.id(), "run-1", Duration.ofMinutes(60));

        assertThat(extended.expiresAt()).isAfter(originalExpiry);
        assertThat(extended.isActive()).isTrue();
    }

    @Test
    void releaseByWrongHolderFails() {
        WorkspaceLease lease = leaseService.acquireWritable(
            workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));

        assertThatThrownBy(() ->
            leaseService.release(lease.id(), "intruder")
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only the current holder");
    }

    @Test
    void releaseAllFor_releasesAll() {
        String workspace2 = UUID.randomUUID().toString();
        repository.saveRoot(new WorkspaceRoot(
            workspace2, WorkspaceOwnerType.JOB, "job-1",
            "jobs/job-1", "Job 1", "{}", Instant.now(), Instant.now()
        ));

        leaseService.acquireWritable(workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));
        leaseService.acquireRead(workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));
        leaseService.acquireWritable(workspace2, "TASK_RUN", "run-1", Duration.ofMinutes(5));

        leaseService.releaseAllFor("TASK_RUN", "run-1");

        assertThat(repository.findActiveLeases("TASK_RUN", "run-1")).isEmpty();
    }

    @Test
    void hasWritableLease_returnsTrueOnlyForCorrectHolder() {
        leaseService.acquireWritable(workspaceId, "TASK_RUN", "run-1", Duration.ofMinutes(5));

        assertThat(leaseService.hasWritableLease(workspaceId, "run-1")).isTrue();
        assertThat(leaseService.hasWritableLease(workspaceId, "run-2")).isFalse();
    }

    @Test
    void acquireWritable_workspaceNotFoundThrows() {
        assertThatThrownBy(() ->
            leaseService.acquireWritable("nonexistent", "TASK_RUN", "run-1", Duration.ofMinutes(5))
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Workspace not found");
    }

    @Test
    void expiredWritableLeaseIsReconciledBeforeReacquire() {
        repository.saveLease(new WorkspaceLease(
            "expired",
            workspaceId,
            "ASSIGNMENT",
            "old-run",
            LeaseMode.WRITE,
            Instant.now().minusSeconds(1),
            false,
            null,
            Instant.now().minusSeconds(60),
            Instant.now().minusSeconds(60)
        ));

        WorkspaceLease fresh = leaseService.acquireWritable(
            workspaceId, "ASSIGNMENT", "new-run", Duration.ofMinutes(5));

        assertThat(fresh.holderId()).isEqualTo("new-run");
        assertThat(repository.findLeaseById("expired").orElseThrow().releasedAt()).isNotNull();
    }

    @Test
    void gracefulReleaseMarksActiveLeaseWithoutDroppingIt() {
        WorkspaceLease lease = leaseService.acquireWritable(
            workspaceId, "ASSIGNMENT", "run-1", Duration.ofMinutes(5));

        WorkspaceLease requested = leaseService.requestGracefulRelease(workspaceId);

        assertThat(requested.id()).isEqualTo(lease.id());
        assertThat(requested.releaseRequested()).isTrue();
        assertThat(requested.releasedAt()).isNull();
    }
}
