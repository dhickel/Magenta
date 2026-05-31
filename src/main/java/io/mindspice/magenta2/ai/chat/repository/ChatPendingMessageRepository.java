package io.mindspice.magenta2.ai.chat.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.mindspice.magenta2.ai.chat.model.ChatSessionSurface;
import io.mindspice.magenta2.ai.chat.model.ClaimedPendingChatMessage;
import io.mindspice.magenta2.ai.chat.model.PendingChatMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ChatPendingMessageRepository {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CLAIMED = "CLAIMED";
    private static final Duration STALE_CLAIM_AGE = Duration.ofMinutes(10);
    private static final int MAX_ENQUEUE_ATTEMPTS = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentMap<String, Object> conversationLocks = new ConcurrentHashMap<>();

    public ChatPendingMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public PendingChatMessage enqueue(
        String conversationId,
        String messageText,
        String model,
        String planningModel,
        ChatSessionSurface surface
    ) {
        Object lock = conversationLocks.computeIfAbsent(conversationId, ignored -> new Object());
        synchronized (lock) {
            for (int attempt = 1; attempt <= MAX_ENQUEUE_ATTEMPTS; attempt++) {
                String id = UUID.randomUUID().toString();
                try {
                    insertNext(conversationId, messageText, model, planningModel, surface, id);
                    return findVisibleByConversationId(conversationId).stream()
                        .filter(message -> message.id().equals(id))
                        .findFirst()
                        .orElseThrow();
                } catch (DataAccessException exception) {
                    if (attempt == MAX_ENQUEUE_ATTEMPTS || !isMessageOrderConflict(exception)) {
                        throw exception;
                    }
                }
            }
        }
        throw new IllegalStateException("Unable to enqueue pending chat message");
    }

    public List<PendingChatMessage> findVisibleByConversationId(String conversationId) {
        recoverStaleClaims(conversationId);
        List<PendingChatMessageRow> rows = jdbcTemplate.query(
            """
                select id, conversation_id, message_text, model, planning_model, surface,
                       status, created_at, updated_at
                from ai_chat_pending_messages
                where conversation_id = ?
                  and status in ('PENDING', 'CLAIMED')
                order by message_order asc, created_at asc, id asc
                """,
            (rs, rowNum) -> new PendingChatMessageRow(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("message_text"),
                rs.getString("model"),
                rs.getString("planning_model"),
                surface(rs.getString("surface")),
                rs.getString("status"),
                rs.getString("created_at"),
                rs.getString("updated_at")
            ),
            conversationId
        );
        int total = rows.size();
        List<PendingChatMessage> messages = new ArrayList<>(total);
        for (int i = 0; i < rows.size(); i++) {
            messages.add(rows.get(i).toMessage(i + 1, total));
        }
        return messages;
    }

    @Transactional
    public Optional<ClaimedPendingChatMessage> claimOldest(String conversationId) {
        recoverStaleClaims(conversationId);
        Optional<String> messageId = findOldestPendingId(conversationId);
        if (messageId.isEmpty()) {
            return Optional.empty();
        }
        String claimToken = UUID.randomUUID().toString();
        String now = now();
        int updated = jdbcTemplate.update(
            """
                update ai_chat_pending_messages
                set status = ?, claim_token = ?, claimed_at = ?, updated_at = ?
                where id = ?
                  and conversation_id = ?
                  and status = ?
                """,
            STATUS_CLAIMED,
            claimToken,
            now,
            now,
            messageId.get(),
            conversationId,
            STATUS_PENDING
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findVisibleByConversationId(conversationId).stream()
            .filter(message -> message.id().equals(messageId.get()))
            .findFirst()
            .map(message -> new ClaimedPendingChatMessage(message, claimToken));
    }

    @Transactional
    public boolean ack(String conversationId, String messageId, String claimToken) {
        int updated = jdbcTemplate.update(
            """
                delete from ai_chat_pending_messages
                where id = ?
                  and conversation_id = ?
                  and status = ?
                  and claim_token = ?
                """,
            messageId,
            conversationId,
            STATUS_CLAIMED,
            claimToken
        );
        return updated > 0;
    }

    @Transactional
    public boolean release(String conversationId, String messageId, String claimToken) {
        int updated = jdbcTemplate.update(
            """
                update ai_chat_pending_messages
                set status = ?, claim_token = null, claimed_at = null, updated_at = ?
                where id = ?
                  and conversation_id = ?
                  and status = ?
                  and claim_token = ?
                """,
            STATUS_PENDING,
            now(),
            messageId,
            conversationId,
            STATUS_CLAIMED,
            claimToken
        );
        return updated > 0;
    }

    public int recoverStaleClaims(String conversationId) {
        return jdbcTemplate.update(
            """
                update ai_chat_pending_messages
                set status = ?, claim_token = null, claimed_at = null, updated_at = ?
                where conversation_id = ?
                  and status = ?
                  and claimed_at < ?
                """,
            STATUS_PENDING,
            now(),
            conversationId,
            STATUS_CLAIMED,
            Instant.now().minus(STALE_CLAIM_AGE).toString()
        );
    }

    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update(
            "delete from ai_chat_pending_messages where conversation_id = ?",
            conversationId
        );
    }

    void markClaimStaleForTest(String messageId, Instant claimedAt) {
        jdbcTemplate.update(
            """
                update ai_chat_pending_messages
                set claimed_at = ?, updated_at = ?
                where id = ?
                """,
            claimedAt.toString(),
            claimedAt.toString(),
            messageId
        );
    }

    private Optional<String> findOldestPendingId(String conversationId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id
                    from ai_chat_pending_messages
                    where conversation_id = ?
                      and status = ?
                    order by message_order asc, created_at asc, id asc
                    limit 1
                    """,
                String.class,
                conversationId,
                STATUS_PENDING
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists ai_chat_pending_messages (
                id text primary key,
                conversation_id text not null,
                message_order integer not null,
                message_text text not null,
                model text,
                planning_model text,
                surface text,
                status text not null,
                claim_token text,
                claimed_at text,
                created_at text not null,
                updated_at text not null
            )
            """);
        normalizeMessageOrderDuplicates();
        jdbcTemplate.execute("""
            create index if not exists idx_ai_chat_pending_messages_status_order
                on ai_chat_pending_messages (conversation_id, status, message_order, created_at, id)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_ai_chat_pending_messages_order
                on ai_chat_pending_messages (conversation_id, message_order)
            """);
        jdbcTemplate.execute("""
            create unique index if not exists ux_ai_chat_pending_messages_conversation_order
                on ai_chat_pending_messages (conversation_id, message_order)
            """);
    }

    private void insertNext(
        String conversationId,
        String messageText,
        String model,
        String planningModel,
        ChatSessionSurface surface,
        String id
    ) {
        recoverStaleClaims(conversationId);
        String now = now();
        Integer nextOrder = jdbcTemplate.queryForObject(
            """
                select coalesce(max(message_order), 0) + 1
                from ai_chat_pending_messages
                where conversation_id = ?
                """,
            Integer.class,
            conversationId
        );
        jdbcTemplate.update(
            """
                insert into ai_chat_pending_messages (
                    id, conversation_id, message_order, message_text, model, planning_model,
                    surface, status, claim_token, claimed_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, null, null, ?, ?)
                """,
            id,
            conversationId,
            nextOrder == null ? 1 : nextOrder,
            messageText,
            model,
            planningModel,
            surface == null ? null : surface.name(),
            STATUS_PENDING,
            now,
            now
        );
    }

    private void normalizeMessageOrderDuplicates() {
        Integer duplicateGroups = jdbcTemplate.queryForObject(
            """
                select count(*)
                from (
                    select 1
                    from ai_chat_pending_messages
                    group by conversation_id, message_order
                    having count(*) > 1
                )
                """,
            Integer.class
        );
        if (duplicateGroups == null || duplicateGroups == 0) {
            return;
        }

        List<PendingOrderRow> rows = jdbcTemplate.query(
            """
                select id, conversation_id
                from ai_chat_pending_messages
                order by conversation_id asc, message_order asc, created_at asc, id asc
                """,
            (rs, rowNum) -> new PendingOrderRow(
                rs.getString("id"),
                rs.getString("conversation_id")
            )
        );
        String currentConversationId = null;
        int nextOrder = 0;
        for (PendingOrderRow row : rows) {
            if (!row.conversationId().equals(currentConversationId)) {
                currentConversationId = row.conversationId();
                nextOrder = 1;
            } else {
                nextOrder++;
            }
            jdbcTemplate.update(
                "update ai_chat_pending_messages set message_order = ? where id = ?",
                nextOrder,
                row.id()
            );
        }
    }

    private boolean isMessageOrderConflict(DataAccessException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                && message.contains("ai_chat_pending_messages.conversation_id")
                && message.contains("ai_chat_pending_messages.message_order")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ChatSessionSurface surface(String value) {
        return value == null || value.isBlank() ? null : ChatSessionSurface.valueOf(value);
    }

    private String now() {
        return Instant.now().toString();
    }

    private record PendingChatMessageRow(
        String id,
        String conversationId,
        String messageText,
        String model,
        String planningModel,
        ChatSessionSurface surface,
        String status,
        String createdAt,
        String updatedAt
    ) {
        PendingChatMessage toMessage(int position, int total) {
            return new PendingChatMessage(
                id,
                conversationId,
                messageText,
                model,
                planningModel,
                surface,
                status,
                position,
                total,
                createdAt,
                updatedAt
            );
        }
    }

    private record PendingOrderRow(String id, String conversationId) {
    }
}
