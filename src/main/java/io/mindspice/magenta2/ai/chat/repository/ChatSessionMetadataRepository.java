package io.mindspice.magenta2.ai.chat.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ChatSessionMetadataRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatSessionMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public void saveModel(String conversationId, String model) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(model)) {
            return;
        }
        jdbcTemplate.update(
            """
                insert into ai_chat_session_metadata (conversation_id, model)
                values (?, ?)
                on conflict(conversation_id) do update set model = excluded.model
                """,
            conversationId,
            model
        );
    }

    public void saveTitle(String conversationId, String title) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(title)) {
            return;
        }
        jdbcTemplate.update(
            """
                insert into ai_chat_session_metadata (conversation_id, title)
                values (?, ?)
                on conflict(conversation_id) do update set title = excluded.title
                """,
            conversationId,
            title
        );
    }

    public Optional<String> findModel(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select model
                from ai_chat_session_metadata
                where conversation_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return Optional.<String>empty();
                }
                String model = rs.getString("model");
                return StringUtils.hasText(model) ? Optional.of(model) : Optional.empty();
            },
            conversationId
        );
    }

    public Optional<String> findTitle(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select title
                from ai_chat_session_metadata
                where conversation_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return Optional.<String>empty();
                }
                String title = rs.getString("title");
                return StringUtils.hasText(title) ? Optional.of(title) : Optional.empty();
            },
            conversationId
        );
    }

    public void deleteByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        jdbcTemplate.update(
            "delete from ai_chat_session_metadata where conversation_id = ?",
            conversationId
        );
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists ai_chat_session_metadata (
                conversation_id text primary key,
                model text,
                title text
            )
            """);
        java.util.List<String> columns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('ai_chat_session_metadata')",
            String.class
        );
        if (!columns.contains("title")) {
            jdbcTemplate.execute("alter table ai_chat_session_metadata add column title text");
        }
    }
}
