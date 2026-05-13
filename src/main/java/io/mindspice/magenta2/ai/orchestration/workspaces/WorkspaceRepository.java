package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class WorkspaceRepository {
    private final JdbcTemplate jdbcTemplate;

    public WorkspaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public Optional<Workspace> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select * from workspaces where id = ?",
            rs -> rs.next() ? Optional.of(toWorkspace(rs)) : Optional.empty(),
            id
        );
    }

    public Optional<Workspace> findByOwner(WorkspaceOwnerType ownerType, String ownerId) {
        return jdbcTemplate.query(
            "select * from workspaces where owner_type = ? and owner_id = ? order by created_at limit 1",
            rs -> rs.next() ? Optional.of(toWorkspace(rs)) : Optional.empty(),
            ownerType.name(),
            ownerId
        );
    }

    public Workspace save(Workspace workspace) {
        Instant now = Instant.now();
        Instant createdAt = workspace.createdAt() == null ? now : workspace.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into workspaces (
                    id, owner_type, owner_id, root_relative_path, display_name,
                    metadata_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    owner_type = excluded.owner_type,
                    owner_id = excluded.owner_id,
                    root_relative_path = excluded.root_relative_path,
                    display_name = excluded.display_name,
                    metadata_json = excluded.metadata_json,
                    updated_at = excluded.updated_at
                """,
            workspace.id(),
            workspace.ownerType().name(),
            workspace.ownerId(),
            workspace.rootRelativePath(),
            workspace.displayName(),
            workspace.metadataJson(),
            createdAt.toString(),
            updatedAt.toString()
        );
        return findById(workspace.id()).orElseThrow();
    }

    public List<WorkspaceLink> links(String workspaceId) {
        return jdbcTemplate.query(
            "select * from workspace_links where workspace_id = ? order by label",
            (rs, rowNum) -> toLink(rs),
            workspaceId
        );
    }

    public WorkspaceLink saveLink(WorkspaceLink link) {
        Instant now = Instant.now();
        Instant createdAt = link.createdAt() == null ? now : link.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into workspace_links (
                    id, workspace_id, label, link_type, target, readable, writable, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    label = excluded.label,
                    link_type = excluded.link_type,
                    target = excluded.target,
                    readable = excluded.readable,
                    writable = excluded.writable,
                    updated_at = excluded.updated_at
                """,
            link.id(),
            link.workspaceId(),
            link.label(),
            link.linkType().name(),
            link.target(),
            link.readable() ? 1 : 0,
            link.writable() ? 1 : 0,
            createdAt.toString(),
            updatedAt.toString()
        );
        return findLink(link.id()).orElseThrow();
    }

    public Optional<WorkspaceLink> findLink(String id) {
        return jdbcTemplate.query(
            "select * from workspace_links where id = ?",
            rs -> rs.next() ? Optional.of(toLink(rs)) : Optional.empty(),
            id
        );
    }

    public void deleteLink(String workspaceId, String linkId) {
        jdbcTemplate.update("delete from workspace_links where workspace_id = ? and id = ?", workspaceId, linkId);
    }

    // ── WorkspaceRoot ──

    public Optional<WorkspaceRoot> findRootById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select * from workspace_roots where id = ?",
            rs -> rs.next() ? Optional.of(toRoot(rs)) : Optional.empty(),
            id
        );
    }

    public Optional<WorkspaceRoot> findRootByOwner(WorkspaceOwnerType ownerType, String ownerId) {
        return jdbcTemplate.query(
            "select * from workspace_roots where owner_type = ? and owner_id = ? order by created_at limit 1",
            rs -> rs.next() ? Optional.of(toRoot(rs)) : Optional.empty(),
            ownerType.name(),
            ownerId
        );
    }

    public WorkspaceRoot saveRoot(WorkspaceRoot root) {
        Instant now = Instant.now();
        Instant createdAt = root.createdAt() == null ? now : root.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into workspace_roots (
                    id, owner_type, owner_id, root_relative_path, display_name,
                    metadata_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    owner_type = excluded.owner_type,
                    owner_id = excluded.owner_id,
                    root_relative_path = excluded.root_relative_path,
                    display_name = excluded.display_name,
                    metadata_json = excluded.metadata_json,
                    updated_at = excluded.updated_at
                """,
            root.id(),
            root.ownerType().name(),
            root.ownerId(),
            root.rootRelativePath(),
            root.displayName(),
            root.metadataJson(),
            createdAt.toString(),
            updatedAt.toString()
        );
        return findRootById(root.id()).orElseThrow();
    }

    // ── WorkspaceLease ──

    public Optional<WorkspaceLease> findLeaseById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select * from workspace_leases where id = ?",
            rs -> rs.next() ? Optional.of(toLease(rs)) : Optional.empty(),
            id
        );
    }

    public Optional<WorkspaceLease> findActiveWritableLease(String workspaceId) {
        return jdbcTemplate.query(
            """
                select * from workspace_leases
                where workspace_id = ? and mode = 'WRITE' and released_at is null
                order by created_at desc limit 1
                """,
            rs -> rs.next() ? Optional.of(toLease(rs)) : Optional.empty(),
            workspaceId
        );
    }

    public List<WorkspaceLease> findActiveLeases(String holderType, String holderId) {
        return jdbcTemplate.query(
            """
                select * from workspace_leases
                where holder_type = ? and holder_id = ? and released_at is null
                order by created_at desc
                """,
            (rs, rowNum) -> toLease(rs),
            holderType,
            holderId
        );
    }

    public WorkspaceLease saveLease(WorkspaceLease lease) {
        Instant now = Instant.now();
        Instant createdAt = lease.createdAt() == null ? now : lease.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into workspace_leases (
                    id, workspace_id, holder_type, holder_id, mode,
                    expires_at, released_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    workspace_id = excluded.workspace_id,
                    holder_type = excluded.holder_type,
                    holder_id = excluded.holder_id,
                    mode = excluded.mode,
                    expires_at = excluded.expires_at,
                    released_at = excluded.released_at,
                    updated_at = excluded.updated_at
                """,
            lease.id(),
            lease.workspaceId(),
            lease.holderType(),
            lease.holderId(),
            lease.mode().name(),
            lease.expiresAt() != null ? lease.expiresAt().toString() : null,
            lease.releasedAt() != null ? lease.releasedAt().toString() : null,
            createdAt.toString(),
            updatedAt.toString()
        );
        return findLeaseById(lease.id()).orElseThrow();
    }

    /**
     * Atomically insert a writable lease. Returns the saved lease if the
     * insert succeeded, or {@link Optional#empty()} if an active writable
     * lease already exists for the workspace.
     *
     * <p>Relies on the unique partial index
     * {@code idx_workspace_leases_active_write} to enforce at most one
     * active WRITE lease per workspace at the database level.
     */
    public Optional<WorkspaceLease> insertWritableLease(WorkspaceLease lease) {
        Instant now = Instant.now();
        Instant createdAt = lease.createdAt() == null ? now : lease.createdAt();
        Instant updatedAt = now;
        int rows = jdbcTemplate.update(
            """
                insert into workspace_leases (
                    id, workspace_id, holder_type, holder_id, mode,
                    expires_at, released_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(workspace_id) where mode = 'WRITE' and released_at is null
                do nothing
                """,
            lease.id(),
            lease.workspaceId(),
            lease.holderType(),
            lease.holderId(),
            lease.mode().name(),
            lease.expiresAt() != null ? lease.expiresAt().toString() : null,
            lease.releasedAt() != null ? lease.releasedAt().toString() : null,
            createdAt.toString(),
            updatedAt.toString()
        );
        if (rows == 0) {
            return Optional.empty();
        }
        return findLeaseById(lease.id());
    }

    public void releaseLease(String leaseId) {
        jdbcTemplate.update(
            "update workspace_leases set released_at = ?, updated_at = ? where id = ?",
            Instant.now().toString(),
            Instant.now().toString(),
            leaseId
        );
    }

    // ── RunOutputArtifact ──

    public RunOutputArtifact saveArtifact(RunOutputArtifact artifact) {
        Instant now = Instant.now();
        Instant createdAt = artifact.createdAt() == null ? now : artifact.createdAt();
        jdbcTemplate.update(
            """
                insert into run_output_artifacts (
                    id, run_id, plan_id, output_name, artifact_type,
                    file_name, file_path, content_json, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    output_name = excluded.output_name,
                    artifact_type = excluded.artifact_type,
                    file_name = excluded.file_name,
                    file_path = excluded.file_path,
                    content_json = excluded.content_json
                """,
            artifact.id(),
            artifact.runId(),
            artifact.planId(),
            artifact.outputName(),
            artifact.artifactType(),
            artifact.fileName(),
            artifact.filePath(),
            artifact.contentJson(),
            createdAt.toString()
        );
        return artifact;
    }

    public List<RunOutputArtifact> findArtifactsByRunId(String runId) {
        return jdbcTemplate.query(
            "select * from run_output_artifacts where run_id = ? order by output_name",
            (rs, rowNum) -> toArtifact(rs),
            runId
        );
    }

    public List<RunOutputArtifact> findArtifactsByPlanId(String planId) {
        return jdbcTemplate.query(
            "select * from run_output_artifacts where plan_id = ? order by created_at desc",
            (rs, rowNum) -> toArtifact(rs),
            planId
        );
    }

    public List<RunOutputArtifact> findArtifacts(String runId, String planId, String artifactType, int limit) {
        StringBuilder sql = new StringBuilder("select * from run_output_artifacts where 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(runId)) {
            sql.append(" and run_id = ?");
            args.add(runId);
        }
        if (StringUtils.hasText(planId)) {
            sql.append(" and plan_id = ?");
            args.add(planId);
        }
        if (StringUtils.hasText(artifactType)) {
            sql.append(" and artifact_type = ?");
            args.add(artifactType);
        }
        sql.append(" order by created_at desc limit ?");
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toArtifact(rs), args.toArray());
    }

    private Workspace toWorkspace(ResultSet rs) throws SQLException {
        return new Workspace(
            rs.getString("id"),
            WorkspaceOwnerType.valueOf(rs.getString("owner_type")),
            rs.getString("owner_id"),
            rs.getString("root_relative_path"),
            rs.getString("display_name"),
            rs.getString("metadata_json"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private WorkspaceLink toLink(ResultSet rs) throws SQLException {
        return new WorkspaceLink(
            rs.getString("id"),
            rs.getString("workspace_id"),
            rs.getString("label"),
            WorkspaceLinkType.valueOf(rs.getString("link_type")),
            rs.getString("target"),
            rs.getInt("readable") == 1,
            rs.getInt("writable") == 1,
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private Instant instant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private WorkspaceRoot toRoot(ResultSet rs) throws SQLException {
        return new WorkspaceRoot(
            rs.getString("id"),
            WorkspaceOwnerType.valueOf(rs.getString("owner_type")),
            rs.getString("owner_id"),
            rs.getString("root_relative_path"),
            rs.getString("display_name"),
            rs.getString("metadata_json"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private WorkspaceLease toLease(ResultSet rs) throws SQLException {
        return new WorkspaceLease(
            rs.getString("id"),
            rs.getString("workspace_id"),
            rs.getString("holder_type"),
            rs.getString("holder_id"),
            LeaseMode.valueOf(rs.getString("mode")),
            instant(rs.getString("expires_at")),
            instant(rs.getString("released_at")),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private RunOutputArtifact toArtifact(ResultSet rs) throws SQLException {
        return new RunOutputArtifact(
            rs.getString("id"),
            rs.getString("run_id"),
            rs.getString("plan_id"),
            rs.getString("output_name"),
            rs.getString("artifact_type"),
            rs.getString("file_name"),
            rs.getString("file_path"),
            rs.getString("content_json"),
            instant(rs.getString("created_at"))
        );
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists workspaces (
                id text primary key,
                owner_type text not null,
                owner_id text not null,
                root_relative_path text not null,
                display_name text not null,
                metadata_json text,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create unique index if not exists idx_workspaces_owner
                on workspaces(owner_type, owner_id)
            """);
        jdbcTemplate.execute("""
            create table if not exists workspace_links (
                id text primary key,
                workspace_id text not null,
                label text not null,
                link_type text not null,
                target text not null,
                readable integer not null,
                writable integer not null,
                created_at text not null,
                updated_at text not null,
                foreign key(workspace_id) references workspaces(id)
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists workspace_roots (
                id text primary key,
                owner_type text not null,
                owner_id text not null,
                root_relative_path text not null,
                display_name text not null,
                metadata_json text,
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create unique index if not exists idx_workspace_roots_owner
                on workspace_roots(owner_type, owner_id)
            """);
        jdbcTemplate.execute("""
            create table if not exists workspace_leases (
                id text primary key,
                workspace_id text not null,
                holder_type text not null,
                holder_id text not null,
                mode text not null,
                expires_at text,
                released_at text,
                created_at text not null,
                updated_at text not null,
                foreign key(workspace_id) references workspace_roots(id)
            )
            """);
        jdbcTemplate.execute("""
            create unique index if not exists idx_workspace_leases_active_write
                on workspace_leases(workspace_id)
                where mode = 'WRITE' and released_at is null
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_leases_active_holder
                on workspace_leases(holder_type, holder_id)
                where released_at is null
            """);
        jdbcTemplate.execute("""
            create table if not exists run_output_artifacts (
                id text primary key,
                run_id text not null,
                plan_id text not null,
                output_name text not null,
                artifact_type text not null,
                file_name text not null,
                file_path text not null,
                content_json text,
                created_at text not null,
                foreign key(run_id) references plan_runs(id)
            )
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_run_output_artifacts_run
                on run_output_artifacts(run_id)
            """);
    }
}
