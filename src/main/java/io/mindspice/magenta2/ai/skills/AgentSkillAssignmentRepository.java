package io.mindspice.magenta2.ai.skills;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class AgentSkillAssignmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public AgentSkillAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public AgentSkillAssignment save(
        Long skillId,
        String skillName,
        AgentSkillTargetType targetType,
        String targetId,
        boolean enabled
    ) {
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                insert into agent_skill_assignments (
                    skill_id, skill_name, target_type, target_id, enabled, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(skill_name, target_type, target_id) do update set
                    skill_id = excluded.skill_id,
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at
                """,
            skillId,
            skillName,
            targetType.name(),
            targetId,
            enabled ? 1 : 0,
            now.toString(),
            now.toString()
        );
        return find(skillName, targetType, targetId).orElseThrow();
    }

    public Optional<AgentSkillAssignment> find(String skillName, AgentSkillTargetType targetType, String targetId) {
        if (!StringUtils.hasText(skillName) || targetType == null || !StringUtils.hasText(targetId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select *
                from agent_skill_assignments
                where skill_name = ? and target_type = ? and target_id = ?
                """,
            rs -> rs.next() ? Optional.of(toAssignment(rs)) : Optional.empty(),
            skillName,
            targetType.name(),
            targetId
        );
    }

    public List<AgentSkillAssignment> findByTarget(AgentSkillTargetType targetType, String targetId) {
        if (targetType == null || !StringUtils.hasText(targetId)) {
            return List.of();
        }
        return jdbcTemplate.query(
            """
                select *
                from agent_skill_assignments
                where target_type = ? and target_id = ?
                order by skill_name
                """,
            (rs, rowNum) -> toAssignment(rs),
            targetType.name(),
            targetId
        );
    }

    public List<AgentSkillAssignment> findBySkillName(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return List.of();
        }
        return jdbcTemplate.query(
            """
                select *
                from agent_skill_assignments
                where skill_name = ?
                order by target_type, target_id
                """,
            (rs, rowNum) -> toAssignment(rs),
            skillName
        );
    }

    public List<String> findEnabledSkillNames(AgentSkillTargetType targetType, String targetId) {
        if (targetType == null || !StringUtils.hasText(targetId)) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
            """
                select skill_name
                from agent_skill_assignments
                where target_type = ? and target_id = ? and enabled = 1
                order by skill_name
                """,
            String.class,
            targetType.name(),
            targetId
        );
    }

    public void delete(String skillName, AgentSkillTargetType targetType, String targetId) {
        if (!StringUtils.hasText(skillName) || targetType == null || !StringUtils.hasText(targetId)) {
            return;
        }
        jdbcTemplate.update(
            "delete from agent_skill_assignments where skill_name = ? and target_type = ? and target_id = ?",
            skillName,
            targetType.name(),
            targetId
        );
    }

    private AgentSkillAssignment toAssignment(ResultSet rs) throws SQLException {
        Number id = (Number) rs.getObject("id");
        Number skillId = (Number) rs.getObject("skill_id");
        return new AgentSkillAssignment(
            id == null ? null : id.longValue(),
            skillId == null ? null : skillId.longValue(),
            rs.getString("skill_name"),
            AgentSkillTargetType.valueOf(rs.getString("target_type")),
            rs.getString("target_id"),
            rs.getInt("enabled") == 1,
            instant(rs.getString("created_at")),
            instant(rs.getString("updated_at"))
        );
    }

    private Instant instant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private void ensureSchema() {
        jdbcTemplate.execute(
            """
                create table if not exists agent_skill_assignments (
                    id integer primary key autoincrement,
                    skill_id integer,
                    skill_name text not null,
                    target_type text not null,
                    target_id text not null,
                    enabled integer not null,
                    created_at text not null,
                    updated_at text not null,
                    unique(skill_name, target_type, target_id)
                )
                """
        );
        jdbcTemplate.execute(
            "create index if not exists idx_agent_skill_assignments_target on agent_skill_assignments(target_type, target_id, enabled)"
        );
        jdbcTemplate.execute(
            "create index if not exists idx_agent_skill_assignments_skill_id on agent_skill_assignments(skill_id)"
        );
    }
}
