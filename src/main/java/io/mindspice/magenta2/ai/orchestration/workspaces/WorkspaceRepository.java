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
    }
}
