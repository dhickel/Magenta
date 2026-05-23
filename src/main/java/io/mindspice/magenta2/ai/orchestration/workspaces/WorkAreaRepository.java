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
public class WorkAreaRepository {
    private final JdbcTemplate jdbcTemplate;

    public WorkAreaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public Optional<WorkArea> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "select * from work_areas where id = ?",
            rs -> rs.next() ? Optional.of(toWorkArea(rs)) : Optional.empty(),
            id
        );
    }

    public Optional<WorkArea> findByOwnerAndPath(WorkspaceOwnerType ownerType, String ownerId, String areaRelativePath) {
        return jdbcTemplate.query(
            """
                select * from work_areas
                where owner_type = ? and owner_id = ? and area_relative_path = ?
                """,
            rs -> rs.next() ? Optional.of(toWorkArea(rs)) : Optional.empty(),
            ownerType.name(),
            ownerId,
            areaRelativePath
        );
    }

    public Optional<WorkArea> findHome(WorkspaceOwnerType ownerType, String ownerId) {
        return jdbcTemplate.query(
            """
                select * from work_areas
                where owner_type = ? and owner_id = ? and home_flag = 1 and active_flag = 1
                order by created_at limit 1
                """,
            rs -> rs.next() ? Optional.of(toWorkArea(rs)) : Optional.empty(),
            ownerType.name(),
            ownerId
        );
    }

    public List<WorkArea> findByOwner(WorkspaceOwnerType ownerType, String ownerId, boolean includeInactive) {
        if (includeInactive) {
            return jdbcTemplate.query(
                """
                    select * from work_areas
                    where owner_type = ? and owner_id = ?
                    order by home_flag desc, active_flag desc, display_name
                    """,
                (rs, rowNum) -> toWorkArea(rs),
                ownerType.name(),
                ownerId
            );
        }
        return jdbcTemplate.query(
            """
                select * from work_areas
                where owner_type = ? and owner_id = ? and active_flag = 1
                order by home_flag desc, display_name
                """,
            (rs, rowNum) -> toWorkArea(rs),
            ownerType.name(),
            ownerId
        );
    }

    public WorkArea save(WorkArea workArea) {
        Instant now = Instant.now();
        Instant createdAt = workArea.createdAt() == null ? now : workArea.createdAt();
        Instant updatedAt = now;
        jdbcTemplate.update(
            """
                insert into work_areas (
                    id, owner_type, owner_id, workspace_id, root_relative_path, area_relative_path,
                    display_name, system_flag, home_flag, active_flag, metadata_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    owner_type = excluded.owner_type,
                    owner_id = excluded.owner_id,
                    workspace_id = excluded.workspace_id,
                    root_relative_path = excluded.root_relative_path,
                    area_relative_path = excluded.area_relative_path,
                    display_name = excluded.display_name,
                    system_flag = excluded.system_flag,
                    home_flag = excluded.home_flag,
                    active_flag = excluded.active_flag,
                    metadata_json = excluded.metadata_json,
                    updated_at = excluded.updated_at
                """,
            workArea.id(),
            workArea.ownerType().name(),
            workArea.ownerId(),
            workArea.workspaceId(),
            workArea.rootRelativePath(),
            workArea.areaRelativePath(),
            workArea.displayName(),
            workArea.system() ? 1 : 0,
            workArea.home() ? 1 : 0,
            workArea.active() ? 1 : 0,
            StringUtils.hasText(workArea.metadataJson()) ? workArea.metadataJson() : "{}",
            createdAt.toString(),
            updatedAt.toString()
        );
        return findById(workArea.id()).orElseThrow();
    }

    public WorkArea deactivate(String id) {
        WorkArea current = findById(id).orElseThrow(() -> new IllegalArgumentException("work area not found: " + id));
        jdbcTemplate.update(
            "update work_areas set active_flag = 0, updated_at = ? where id = ?",
            Instant.now().toString(),
            id
        );
        return findById(current.id()).orElseThrow();
    }

    public boolean hasActiveAssignment(String workAreaId) {
        if (!tableExists("work_assignments") || !columnExists("work_assignments", "selected_work_area_id")) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*) from work_assignments
                where selected_work_area_id = ?
                  and status in ('QUEUED', 'RUNNING', 'WAITING', 'PAUSED', 'CANCEL_REQUESTED')
                """,
            Integer.class,
            workAreaId
        );
        return count != null && count > 0;
    }

    public boolean hasActiveOutputTarget(String workAreaId) {
        if (!tableExists("work_assignments") || !columnExists("work_assignments", "output_work_area_id")) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*) from work_assignments
                where output_work_area_id = ?
                  and status in ('QUEUED', 'RUNNING', 'WAITING', 'PAUSED', 'CANCEL_REQUESTED')
                """,
            Integer.class,
            workAreaId
        );
        return count != null && count > 0;
    }

    private WorkArea toWorkArea(ResultSet rs) throws SQLException {
        return new WorkArea(
            rs.getString("id"),
            WorkspaceOwnerType.valueOf(rs.getString("owner_type")),
            rs.getString("owner_id"),
            rs.getString("workspace_id"),
            rs.getString("root_relative_path"),
            rs.getString("area_relative_path"),
            rs.getString("display_name"),
            rs.getInt("system_flag") != 0,
            rs.getInt("home_flag") != 0,
            rs.getInt("active_flag") != 0,
            rs.getString("metadata_json"),
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private Instant instant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists work_areas (
                id text primary key,
                owner_type text not null,
                owner_id text not null,
                workspace_id text,
                root_relative_path text not null,
                area_relative_path text not null,
                display_name text not null,
                system_flag integer not null default 0,
                home_flag integer not null default 0,
                active_flag integer not null default 1,
                metadata_json text not null default '{}',
                created_at text not null,
                updated_at text not null,
                unique(owner_type, owner_id, area_relative_path),
                foreign key(workspace_id) references workspaces(id)
            )
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_work_areas_owner
                on work_areas(owner_type, owner_id, active_flag)
            """);
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from sqlite_master where type = 'table' and name = ?",
            Integer.class,
            table
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        return !jdbcTemplate.queryForList(
            "select name from pragma_table_info('" + table + "') where name = ?",
            String.class,
            column
        ).isEmpty();
    }
}
