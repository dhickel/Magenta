package io.mindspice.magenta2.ai.chat.repository;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SQLiteChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public SQLiteChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
            "select distinct conversation_id from ai_chat_memory order by conversation_id",
            String.class
        );
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbcTemplate.query(
            """
                select message_type, message_text
                from ai_chat_memory
                where conversation_id = ?
                order by message_order asc
                """,
            (rs, rowNum) -> toMessage(rs.getString("message_type"), rs.getString("message_text")),
            conversationId
        );
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        jdbcTemplate.update("delete from ai_chat_memory where conversation_id = ?", conversationId);
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            jdbcTemplate.update(
                """
                    insert into ai_chat_memory (conversation_id, message_order, message_type, message_text)
                    values (?, ?, ?, ?)
                    """,
                conversationId,
                i,
                message.getMessageType().getValue(),
                message.getText()
            );
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("delete from ai_chat_memory where conversation_id = ?", conversationId);
    }

    private Message toMessage(String messageTypeValue, String messageText) {
        MessageType messageType = MessageType.fromValue(messageTypeValue);
        return switch (messageType) {
            case USER -> new UserMessage(messageText);
            case SYSTEM -> new SystemMessage(messageText);
            default -> new AssistantMessage(messageText);
        };
    }
}
