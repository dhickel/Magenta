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

    public void deleteByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        jdbcTemplate.update(
            "delete from ai_chat_session_metadata where conversation_id = ?",
            conversationId
        );
    }
}
