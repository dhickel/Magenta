package io.mindspice.magenta2.ai.chat.repository;

import java.time.Instant;
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
                insert into ai_chat_session_metadata (conversation_id, model, updated_at)
                values (?, ?, ?)
                on conflict(conversation_id) do update set
                    model = excluded.model,
                    updated_at = excluded.updated_at
                """,
            conversationId,
            model,
            now()
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

    public void updateTitle(String conversationId, String title) {
        saveTitle(conversationId, title);
    }

    public void saveTitleIfAbsent(String conversationId, String title) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(title)) {
            return;
        }
        jdbcTemplate.update(
            """
                insert into ai_chat_session_metadata (conversation_id, title)
                values (?, ?)
                on conflict(conversation_id) do update set title = excluded.title
                where ai_chat_session_metadata.title is null or trim(ai_chat_session_metadata.title) = ''
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

    public boolean isFavorite(String conversationId) {
        return booleanValue(conversationId, "favorite");
    }

    public boolean isArchived(String conversationId) {
        return booleanValue(conversationId, "archived");
    }

    public Optional<String> findUpdatedAt(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select updated_at
                from ai_chat_session_metadata
                where conversation_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return Optional.<String>empty();
                }
                String updatedAt = rs.getString("updated_at");
                return StringUtils.hasText(updatedAt) ? Optional.of(updatedAt) : Optional.empty();
            },
            conversationId
        );
    }

    public void setFavorite(String conversationId, boolean favorite) {
        updateFlag(conversationId, "favorite", favorite);
    }

    public void setArchived(String conversationId, boolean archived) {
        updateFlag(conversationId, "archived", archived);
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
                title text,
                favorite integer not null default 0,
                archived integer not null default 0,
                updated_at text
            )
            """);
        java.util.List<String> columns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('ai_chat_session_metadata')",
            String.class
        );
        if (!columns.contains("title")) {
            jdbcTemplate.execute("alter table ai_chat_session_metadata add column title text");
        }
        if (!columns.contains("favorite")) {
            jdbcTemplate.execute("alter table ai_chat_session_metadata add column favorite integer not null default 0");
        }
        if (!columns.contains("archived")) {
            jdbcTemplate.execute("alter table ai_chat_session_metadata add column archived integer not null default 0");
        }
        if (!columns.contains("updated_at")) {
            jdbcTemplate.execute("alter table ai_chat_session_metadata add column updated_at text");
        }
    }

    private boolean booleanValue(String conversationId, String column) {
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        return Boolean.TRUE.equals(jdbcTemplate.query(
            "select " + column + " from ai_chat_session_metadata where conversation_id = ?",
            rs -> rs.next() && rs.getInt(column) != 0,
            conversationId
        ));
    }

    private void updateFlag(String conversationId, String column, boolean value) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        jdbcTemplate.update(
            "insert into ai_chat_session_metadata (conversation_id, " + column + ") values (?, ?) "
                + "on conflict(conversation_id) do update set " + column + " = excluded." + column,
            conversationId,
            value ? 1 : 0
        );
    }

    private String now() {
        return Instant.now().toString();
    }
}
