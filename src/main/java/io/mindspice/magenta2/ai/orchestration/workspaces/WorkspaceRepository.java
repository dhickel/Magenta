package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
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

    public List<Workspace> findAll(WorkspaceOwnerType ownerType, String ownerId, int limit) {
        StringBuilder sql = new StringBuilder("select * from workspaces where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (ownerType != null) {
            sql.append(" and owner_type = ?");
            args.add(ownerType.name());
        }
        if (StringUtils.hasText(ownerId)) {
            sql.append(" and owner_id = ?");
            args.add(ownerId.trim());
        }
        sql.append(" order by updated_at desc limit ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toWorkspace(rs), args.toArray());
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

    public void deleteByOwner(WorkspaceOwnerType ownerType, String ownerId) {
        if (ownerType == null || !StringUtils.hasText(ownerId)) {
            return;
        }
        List<String> workspaceIds = jdbcTemplate.query(
            "select id from workspaces where owner_type = ? and owner_id = ?",
            (rs, rowNum) -> rs.getString("id"),
            ownerType.name(),
            ownerId
        );
        if (workspaceIds.isEmpty()) {
            return;
        }
        for (String workspaceId : workspaceIds) {
            jdbcTemplate.update("delete from workspace_links where workspace_id = ?", workspaceId);
        }
        jdbcTemplate.update(
            "delete from run_output_artifacts where workspace_id in (select id from workspaces where owner_type = ? and owner_id = ?)",
            ownerType.name(),
            ownerId
        );
        jdbcTemplate.update("delete from workspaces where owner_type = ? and owner_id = ?", ownerType.name(), ownerId);
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

    // ── WorkspaceRoot — deprecated, delegates to workspaces ──

    /**
     * @deprecated Use {@link #findById(String)} instead.
     */
    @Deprecated
    public Optional<WorkspaceRoot> findRootById(String id) {
        return findById(id).map(this::toRootFromWorkspace);
    }

    /**
     * @deprecated Use {@link #findByOwner(WorkspaceOwnerType, String)} instead.
     */
    @Deprecated
    public Optional<WorkspaceRoot> findRootByOwner(WorkspaceOwnerType ownerType, String ownerId) {
        return findByOwner(ownerType, ownerId).map(this::toRootFromWorkspace);
    }

    /**
     * @deprecated Use {@link #save(Workspace)} instead.
     */
    @Deprecated
    public WorkspaceRoot saveRoot(WorkspaceRoot root) {
        Workspace saved = save(new Workspace(
            root.id(), root.ownerType(), root.ownerId(),
            root.rootRelativePath(), root.displayName(),
            root.metadataJson(), root.createdAt(), root.updatedAt()
        ));
        return toRootFromWorkspace(saved);
    }

    private WorkspaceRoot toRootFromWorkspace(Workspace workspace) {
        return new WorkspaceRoot(
            workspace.id(), workspace.ownerType(), workspace.ownerId(),
            workspace.rootRelativePath(), workspace.displayName(),
            workspace.metadataJson(), workspace.createdAt(), workspace.updatedAt()
        );
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

    public List<WorkspaceLease> findActiveLeases(String workspaceId) {
        return jdbcTemplate.query(
            """
                select * from workspace_leases
                where workspace_id = ? and released_at is null
                order by created_at desc
                """,
            (rs, rowNum) -> toLease(rs),
            workspaceId
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
                    expires_at, release_requested, released_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    workspace_id = excluded.workspace_id,
                    holder_type = excluded.holder_type,
                    holder_id = excluded.holder_id,
                    mode = excluded.mode,
                    expires_at = excluded.expires_at,
                    release_requested = excluded.release_requested,
                    released_at = excluded.released_at,
                    updated_at = excluded.updated_at
                """,
            lease.id(),
            lease.workspaceId(),
            lease.holderType(),
            lease.holderId(),
            lease.mode().name(),
            lease.expiresAt() != null ? lease.expiresAt().toString() : null,
            lease.releaseRequested() ? 1 : 0,
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
                    expires_at, release_requested, released_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(workspace_id) where mode = 'WRITE' and released_at is null
                do nothing
                """,
            lease.id(),
            lease.workspaceId(),
            lease.holderType(),
            lease.holderId(),
            lease.mode().name(),
            lease.expiresAt() != null ? lease.expiresAt().toString() : null,
            lease.releaseRequested() ? 1 : 0,
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

    public void releaseLeasesByWorkspaceId(String workspaceId) {
        if (!StringUtils.hasText(workspaceId)) {
            return;
        }
        Instant now = Instant.now();
        jdbcTemplate.update(
            "update workspace_leases set released_at = ?, updated_at = ? where workspace_id = ? and released_at is null",
            now.toString(),
            now.toString(),
            workspaceId
        );
    }

    public int releaseExpiredLeases(Instant now) {
        return jdbcTemplate.update(
            """
                update workspace_leases
                set released_at = ?, updated_at = ?
                where released_at is null and expires_at is not null and expires_at <= ?
                """,
            now.toString(), now.toString(), now.toString()
        );
    }

    // ── RunOutputArtifact ──

    public RunOutputArtifact saveArtifact(RunOutputArtifact artifact) {
        Instant now = Instant.now();
        Instant createdAt = artifact.createdAt() == null ? now : artifact.createdAt();
        jdbcTemplate.update(
            """
                insert into run_output_artifacts (
                    id, run_id, plan_id, agent_id, job_id, job_assignment_id, job_run_id,
                    project_id, workspace_id, run_type,
                    output_name, artifact_type,
                    file_name, file_path, content_json, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    agent_id = excluded.agent_id,
                    job_id = excluded.job_id,
                    job_assignment_id = excluded.job_assignment_id,
                    job_run_id = excluded.job_run_id,
                    project_id = excluded.project_id,
                    workspace_id = excluded.workspace_id,
                    run_type = excluded.run_type,
                    output_name = excluded.output_name,
                    artifact_type = excluded.artifact_type,
                    file_name = excluded.file_name,
                    file_path = excluded.file_path,
                    content_json = excluded.content_json
                """,
            artifact.id(),
            artifact.runId(),
            artifact.planId(),
            artifact.agentId(),
            artifact.jobId(),
            artifact.jobAssignmentId(),
            artifact.jobRunId(),
            artifact.projectId(),
            artifact.workspaceId(),
            artifact.runType(),
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

    public List<RunOutputArtifact> findArtifactsByRunIds(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String inSql = String.join(",", java.util.Collections.nCopies(runIds.size(), "?"));
        return jdbcTemplate.query(
            "select * from run_output_artifacts where run_id in (" + inSql + ") order by output_name",
            (rs, rowNum) -> toArtifact(rs),
            runIds.toArray()
        );
    }

    public Optional<RunOutputArtifact> findArtifactById(String artifactId) {
        if (!StringUtils.hasText(artifactId)) return Optional.empty();
        return jdbcTemplate.query(
            "select * from run_output_artifacts where id = ?",
            rs -> rs.next() ? Optional.of(toArtifact(rs)) : Optional.empty(),
            artifactId
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
        return findArtifacts(OutputArtifactQuery.of(
            null, null, null, null, runId, planId, artifactType, limit
        ));
    }

    public List<RunOutputArtifact> findArtifacts(OutputArtifactQuery query) {
        OutputArtifactQuery effectiveQuery = query == null
            ? OutputArtifactQuery.of(null, null, null, null, null, null, null, 50)
            : query;
        StringBuilder sql = new StringBuilder("select * from run_output_artifacts where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(effectiveQuery.agentId())) {
            sql.append(" and agent_id = ?");
            args.add(effectiveQuery.agentId());
        }
        if (StringUtils.hasText(effectiveQuery.jobId())) {
            sql.append(" and job_id = ?");
            args.add(effectiveQuery.jobId());
        }
        if (StringUtils.hasText(effectiveQuery.jobAssignmentId())) {
            sql.append(" and job_assignment_id = ?");
            args.add(effectiveQuery.jobAssignmentId());
        }
        if (StringUtils.hasText(effectiveQuery.jobRunId())) {
            sql.append(" and job_run_id = ?");
            args.add(effectiveQuery.jobRunId());
        }
        if (StringUtils.hasText(effectiveQuery.projectId())) {
            sql.append(" and project_id = ?");
            args.add(effectiveQuery.projectId());
        }
        if (StringUtils.hasText(effectiveQuery.workspaceId())) {
            sql.append(" and workspace_id = ?");
            args.add(effectiveQuery.workspaceId());
        }
        if (StringUtils.hasText(effectiveQuery.runId())) {
            sql.append(" and run_id = ?");
            args.add(effectiveQuery.runId());
        }
        if (StringUtils.hasText(effectiveQuery.planId())) {
            sql.append(" and plan_id = ?");
            args.add(effectiveQuery.planId());
        }
        if (StringUtils.hasText(effectiveQuery.runType())) {
            sql.append(" and run_type = ?");
            args.add(effectiveQuery.runType());
        }
        if (StringUtils.hasText(effectiveQuery.artifactType())) {
            sql.append(" and artifact_type = ?");
            args.add(effectiveQuery.artifactType());
        }
        sql.append(" order by created_at desc limit ?");
        args.add(effectiveQuery.limit());
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toArtifact(rs), args.toArray());
    }

    public int backfillArtifactAttribution(String runId, OutputArtifactContext context) {
        if (!StringUtils.hasText(runId) || context == null || context.isEmpty()) {
            return 0;
        }
        return jdbcTemplate.update(
            """
                update run_output_artifacts
                set
                    agent_id = coalesce(agent_id, ?),
                    job_id = coalesce(job_id, ?),
                    job_assignment_id = coalesce(job_assignment_id, ?),
                    job_run_id = coalesce(job_run_id, ?),
                    project_id = coalesce(project_id, ?),
                    workspace_id = coalesce(workspace_id, ?),
                    run_type = coalesce(run_type, ?)
                where run_id = ?
                """,
            context.agentId(),
            context.jobId(),
            context.jobAssignmentId(),
            context.jobRunId(),
            context.projectId(),
            context.workspaceId(),
            context.runType(),
            runId
        );
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
            rs.getInt("release_requested") != 0,
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
            rs.getString("agent_id"),
            rs.getString("job_id"),
            getNullable(rs, "job_assignment_id"),
            getNullable(rs, "job_run_id"),
            rs.getString("project_id"),
            rs.getString("workspace_id"),
            rs.getString("run_type"),
            rs.getString("output_name"),
            rs.getString("artifact_type"),
            rs.getString("file_name"),
            rs.getString("file_path"),
            rs.getString("content_json"),
            instant(rs.getString("created_at"))
        );
    }

    private String getNullable(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
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

        // Migrate: workspace_roots was a duplicate of workspaces.
        // Copy any data from workspace_roots into workspaces, then drop workspace_roots.
        migrateWorkspaceRootsToWorkspaces();

        jdbcTemplate.execute("""
            create table if not exists workspace_leases (
                id text primary key,
                workspace_id text not null,
                holder_type text not null,
                holder_id text not null,
                mode text not null,
                expires_at text,
                release_requested integer not null default 0,
                released_at text,
                created_at text not null,
                updated_at text not null,
                foreign key(workspace_id) references workspaces(id)
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
                agent_id text,
                job_id text,
                job_assignment_id text,
                job_run_id text,
                project_id text,
                workspace_id text,
                run_type text,
                output_name text not null,
                artifact_type text not null,
                file_name text not null,
                file_path text not null,
                content_json text,
                created_at text not null
            )
            """);
        addColumnIfMissing("run_output_artifacts", "agent_id", "alter table run_output_artifacts add column agent_id text");
        addColumnIfMissing("run_output_artifacts", "job_id", "alter table run_output_artifacts add column job_id text");
        addColumnIfMissing("run_output_artifacts", "job_assignment_id", "alter table run_output_artifacts add column job_assignment_id text");
        addColumnIfMissing("run_output_artifacts", "job_run_id", "alter table run_output_artifacts add column job_run_id text");
        addColumnIfMissing("run_output_artifacts", "project_id", "alter table run_output_artifacts add column project_id text");
        addColumnIfMissing("workspace_leases", "release_requested",
            "alter table workspace_leases add column release_requested integer not null default 0");
        addColumnIfMissing("run_output_artifacts", "workspace_id", "alter table run_output_artifacts add column workspace_id text");
        addColumnIfMissing("run_output_artifacts", "run_type", "alter table run_output_artifacts add column run_type text");
        if (runOutputArtifactsReferencePlanRuns()) {
            recreateRunOutputArtifactsWithoutPlanRunFk();
        }
        jdbcTemplate.execute("""
            create index if not exists idx_run_output_artifacts_run
                on run_output_artifacts(run_id)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_run_output_artifacts_agent
                on run_output_artifacts(agent_id)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_run_output_artifacts_job
                on run_output_artifacts(job_id)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_run_output_artifacts_project
                on run_output_artifacts(project_id)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_run_output_artifacts_workspace
                on run_output_artifacts(workspace_id)
            """);
    }

    private void migrateWorkspaceRootsToWorkspaces() {
        if (!tableExists("workspace_roots")) {
            return;
        }

        jdbcTemplate.update("""
            insert or ignore into workspaces (id, owner_type, owner_id, root_relative_path,
                display_name, metadata_json, created_at, updated_at)
            select id, owner_type, owner_id, root_relative_path,
                display_name, metadata_json, created_at, updated_at
            from workspace_roots
            """);

        if (workspaceLeasesReferenceWorkspaceRoots()) {
            recreateWorkspaceLeasesForWorkspaces();
        }
        jdbcTemplate.execute("drop table if exists workspace_roots");
    }

    private void recreateWorkspaceLeasesForWorkspaces() {
        String releaseRequested = columnExists("workspace_leases", "release_requested")
            ? "release_requested"
            : "0";
        jdbcTemplate.execute("drop table if exists workspace_leases_migrated");
        jdbcTemplate.execute("""
            create table workspace_leases_migrated (
                id text primary key,
                workspace_id text not null,
                holder_type text not null,
                holder_id text not null,
                mode text not null,
                expires_at text,
                release_requested integer not null default 0,
                released_at text,
                created_at text not null,
                updated_at text not null,
                foreign key(workspace_id) references workspaces(id)
            )
            """);
        jdbcTemplate.execute("""
            insert into workspace_leases_migrated (
                id, workspace_id, holder_type, holder_id, mode,
                expires_at, release_requested, released_at, created_at, updated_at
            )
            select id, workspace_id, holder_type, holder_id, mode,
                expires_at, %s, released_at, created_at, updated_at
            from workspace_leases
            """.formatted(releaseRequested));
        jdbcTemplate.execute("drop table workspace_leases");
        jdbcTemplate.execute("alter table workspace_leases_migrated rename to workspace_leases");
    }

    private void recreateRunOutputArtifactsWithoutPlanRunFk() {
        jdbcTemplate.execute("drop table if exists run_output_artifacts_migrated");
        jdbcTemplate.execute("""
            create table run_output_artifacts_migrated (
                id text primary key,
                run_id text not null,
                plan_id text not null,
                agent_id text,
                job_id text,
                job_assignment_id text,
                job_run_id text,
                project_id text,
                workspace_id text,
                run_type text,
                output_name text not null,
                artifact_type text not null,
                file_name text not null,
                file_path text not null,
                content_json text,
                created_at text not null
            )
            """);
        jdbcTemplate.execute("""
            insert into run_output_artifacts_migrated (
                id, run_id, plan_id, agent_id, job_id, job_assignment_id, job_run_id,
                project_id, workspace_id, run_type, output_name, artifact_type, file_name, file_path,
                content_json, created_at
            )
            select id, run_id, plan_id, agent_id, job_id, null, null, project_id, workspace_id,
                run_type, output_name, artifact_type, file_name, file_path,
                content_json, created_at
            from run_output_artifacts
            """);
        jdbcTemplate.execute("drop table run_output_artifacts");
        jdbcTemplate.execute("alter table run_output_artifacts_migrated rename to run_output_artifacts");
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        if (!table.matches("[a-zA-Z0-9_]+") || !column.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Unsupported table/column identifier");
        }
        if (!columnExists(table, column)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private boolean tableExists(String table) {
        if (!table.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Unsupported table identifier");
        }
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from sqlite_master where type = 'table' and name = ?",
            Integer.class,
            table
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        if (!table.matches("[a-zA-Z0-9_]+") || !column.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Unsupported table/column identifier");
        }
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from pragma_table_info(?) where name = ?",
            Integer.class,
            table,
            column
        );
        return count != null && count > 0;
    }

    private boolean workspaceLeasesReferenceWorkspaceRoots() {
        if (!tableExists("workspace_leases")) {
            return false;
        }
        return jdbcTemplate.query(
            "select \"table\" from pragma_foreign_key_list('workspace_leases') where \"from\" = 'workspace_id'",
            (rs, rowNum) -> rs.getString(1)
        ).stream().anyMatch("workspace_roots"::equals);
    }

    private boolean runOutputArtifactsReferencePlanRuns() {
        if (!tableExists("run_output_artifacts")) {
            return false;
        }
        return jdbcTemplate.query(
            "select \"table\" from pragma_foreign_key_list('run_output_artifacts') where \"from\" = 'run_id'",
            (rs, rowNum) -> rs.getString(1)
        ).stream().anyMatch("plan_runs"::equals);
    }
}
