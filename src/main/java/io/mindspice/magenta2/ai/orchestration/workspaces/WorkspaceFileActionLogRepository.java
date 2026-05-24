package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class WorkspaceFileActionLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public WorkspaceFileActionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public WorkspaceFileActionRecord record(
        WorkArea workArea,
        WorkspaceFileActionType actionType,
        String sourceRelativePath,
        String targetRelativePath,
        String result,
        String payloadJson
    ) {
        return record(
            workArea,
            "system",
            null,
            actionType,
            sourceRelativePath,
            targetRelativePath,
            result,
            payloadJson
        );
    }

    public WorkspaceFileActionRecord record(
        WorkArea workArea,
        String actorType,
        String actorId,
        WorkspaceFileActionType actionType,
        String sourceRelativePath,
        String targetRelativePath,
        String result,
        String payloadJson
    ) {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                insert into workspace_file_actions (
                    id, workspace_id, owner_type, owner_id, work_area_id, actor_type, actor_id,
                    action_type, source_relative_path, target_relative_path, result, payload_json, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            id,
            workArea.workspaceId(),
            workArea.ownerType().name(),
            workArea.ownerId(),
            workArea.id(),
            StringUtils.hasText(actorType) ? actorType.trim() : "system",
            StringUtils.hasText(actorId) ? actorId.trim() : null,
            actionType.name(),
            sourceRelativePath,
            targetRelativePath,
            StringUtils.hasText(result) ? result.trim() : "SUCCEEDED",
            StringUtils.hasText(payloadJson) ? payloadJson : "{}",
            now.toString()
        );
        return findById(id);
    }

    public WorkspaceFileActionRecord findById(String id) {
        return jdbcTemplate.query(
            "select * from workspace_file_actions where id = ?",
            rs -> {
                if (!rs.next()) {
                    throw new IllegalArgumentException("workspace file action not found: " + id);
                }
                return toRecord(rs);
            },
            id
        );
    }

    public List<WorkspaceFileActionRecord> recentForWorkspace(String workspaceId, int limit) {
        return jdbcTemplate.query(
            """
                select * from workspace_file_actions
                where workspace_id = ?
                order by created_at desc
                limit ?
                """,
            (rs, rowNum) -> toRecord(rs),
            workspaceId,
            Math.max(1, limit)
        );
    }

    private WorkspaceFileActionRecord toRecord(ResultSet rs) throws SQLException {
        return new WorkspaceFileActionRecord(
            rs.getString("id"),
            rs.getString("workspace_id"),
            WorkspaceOwnerType.valueOf(rs.getString("owner_type")),
            rs.getString("owner_id"),
            rs.getString("work_area_id"),
            rs.getString("actor_type"),
            rs.getString("actor_id"),
            WorkspaceFileActionType.valueOf(rs.getString("action_type")),
            rs.getString("source_relative_path"),
            rs.getString("target_relative_path"),
            rs.getString("result"),
            rs.getString("payload_json"),
            Instant.parse(rs.getString("created_at"))
        );
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists workspace_file_actions (
                id text primary key,
                workspace_id text not null,
                owner_type text not null,
                owner_id text not null,
                work_area_id text,
                actor_type text,
                actor_id text,
                action_type text not null,
                source_relative_path text,
                target_relative_path text,
                result text not null,
                payload_json text not null default '{}',
                created_at text not null,
                foreign key(workspace_id) references workspaces(id)
            )
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_file_actions_workspace
                on workspace_file_actions(workspace_id, created_at desc)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_file_actions_owner
                on workspace_file_actions(owner_type, owner_id, created_at desc)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_file_actions_work_area
                on workspace_file_actions(work_area_id, created_at desc)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_file_actions_type
                on workspace_file_actions(action_type, created_at desc)
            """);
    }
}
