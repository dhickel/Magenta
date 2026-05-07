package io.mindspice.magenta2.ai.orchestration.settings;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RuntimeSettingsRepository {
    private static final String SETTINGS_ID = "runtime";

    private final JdbcTemplate jdbcTemplate;

    public RuntimeSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public Optional<RuntimeSettings> find() {
        return jdbcTemplate.query(
            """
                select default_agent_id, default_agent_name, default_model,
                       planning_model, summary_model, compaction_model, context_buffer_percent
                from runtime_settings
                where id = ?
                """,
            rs -> rs.next()
                ? Optional.of(new RuntimeSettings(
                    rs.getString("default_agent_id"),
                    rs.getString("default_agent_name"),
                    rs.getString("default_model"),
                    rs.getString("planning_model"),
                    rs.getString("summary_model"),
                    rs.getString("compaction_model"),
                    (Integer) rs.getObject("context_buffer_percent")
                ))
                : Optional.empty(),
            SETTINGS_ID
        );
    }

    public RuntimeSettings save(RuntimeSettings settings) {
        jdbcTemplate.update(
            """
                insert into runtime_settings (
                    id, default_agent_id, default_agent_name, default_model,
                    planning_model, summary_model, compaction_model, context_buffer_percent
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(id) do update set
                    default_agent_id = excluded.default_agent_id,
                    default_agent_name = excluded.default_agent_name,
                    default_model = excluded.default_model,
                    planning_model = excluded.planning_model,
                    summary_model = excluded.summary_model,
                    compaction_model = excluded.compaction_model,
                    context_buffer_percent = excluded.context_buffer_percent
                """,
            SETTINGS_ID,
            settings.defaultAgentId(),
            settings.defaultAgentName(),
            settings.defaultModel(),
            settings.planningModel(),
            settings.summaryModel(),
            settings.compactionModel(),
            settings.contextBufferPercent()
        );
        return find().orElseThrow();
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists runtime_settings (
                id text primary key,
                default_agent_id text,
                default_agent_name text,
                default_model text,
                planning_model text,
                summary_model text,
                compaction_model text,
                context_buffer_percent integer
            )
            """);
    }
}
