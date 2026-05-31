package io.mindspice.magenta2.ai.chat.repository;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.mindspice.magenta2.ai.chat.model.ChatSessionSurface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatPendingMessageRepositoryTest {

    @Test
    void enqueueListsVisibleMessagesInFifoOrder() {
        ChatPendingMessageRepository repository = repository();

        repository.enqueue("conversation-1", "first", "main", "planner", ChatSessionSurface.BROWSER);
        repository.enqueue("conversation-1", "second", null, null, null);

        var messages = repository.findVisibleByConversationId("conversation-1");

        assertThat(messages).extracting(message -> message.messageText())
            .containsExactly("first", "second");
        assertThat(messages).extracting(message -> message.position())
            .containsExactly(1, 2);
        assertThat(messages).extracting(message -> message.total())
            .containsExactly(2, 2);
        assertThat(messages.getFirst().model()).isEqualTo("main");
        assertThat(messages.getFirst().planningModel()).isEqualTo("planner");
        assertThat(messages.getFirst().surface()).isEqualTo(ChatSessionSurface.BROWSER);
    }

    @Test
    void claimAckReleaseAndStaleRecoveryPreserveMessagesSafely() {
        ChatPendingMessageRepository repository = repository();
        repository.enqueue("conversation-1", "first", null, null, null);
        repository.enqueue("conversation-1", "second", null, null, null);

        var firstClaim = repository.claimOldest("conversation-1").orElseThrow();
        assertThat(firstClaim.message().messageText()).isEqualTo("first");
        assertThat(repository.findVisibleByConversationId("conversation-1").getFirst().status()).isEqualTo("CLAIMED");

        assertThat(repository.release("conversation-1", firstClaim.message().id(), "wrong-token")).isFalse();
        assertThat(repository.release("conversation-1", firstClaim.message().id(), firstClaim.claimToken())).isTrue();

        var releasedClaim = repository.claimOldest("conversation-1").orElseThrow();
        assertThat(releasedClaim.message().messageText()).isEqualTo("first");
        assertThat(repository.ack("conversation-1", releasedClaim.message().id(), releasedClaim.claimToken())).isTrue();
        assertThat(repository.findVisibleByConversationId("conversation-1"))
            .extracting(message -> message.messageText())
            .containsExactly("second");

        var staleClaim = repository.claimOldest("conversation-1").orElseThrow();
        repository.markClaimStaleForTest(staleClaim.message().id(), Instant.now().minusSeconds(900));
        List<String> recovered = repository.findVisibleByConversationId("conversation-1").stream()
            .map(message -> message.status())
            .toList();
        assertThat(recovered).containsExactly("PENDING");
        assertThat(repository.claimOldest("conversation-1").orElseThrow().message().messageText()).isEqualTo("second");
    }

    @Test
    void deleteByConversationRemovesOnlyThatQueue() {
        ChatPendingMessageRepository repository = repository();
        repository.enqueue("conversation-1", "delete me", null, null, null);
        repository.enqueue("conversation-2", "keep me", null, null, null);

        repository.deleteByConversationId("conversation-1");

        assertThat(repository.findVisibleByConversationId("conversation-1")).isEmpty();
        assertThat(repository.findVisibleByConversationId("conversation-2"))
            .extracting(message -> message.messageText())
            .containsExactly("keep me");
    }

    @Test
    void concurrentSameConversationEnqueuePersistsUniqueDeterministicOrder(@TempDir Path tempDir) throws Exception {
        DriverManagerDataSource dataSource = fileDataSource(tempDir.resolve("pending-chat.sqlite"));
        new ChatPendingMessageRepository(new JdbcTemplate(dataSource));
        int messageCount = 24;
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < messageCount; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    ChatPendingMessageRepository repository = new ChatPendingMessageRepository(new JdbcTemplate(dataSource));
                    repository.enqueue("conversation-1", "message-" + index, null, null, null);
                    return null;
                }));
            }

            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer totalRows = jdbcTemplate.queryForObject(
            "select count(*) from ai_chat_pending_messages where conversation_id = ?",
            Integer.class,
            "conversation-1"
        );
        Integer uniqueOrders = jdbcTemplate.queryForObject(
            "select count(distinct message_order) from ai_chat_pending_messages where conversation_id = ?",
            Integer.class,
            "conversation-1"
        );
        assertThat(totalRows).isEqualTo(messageCount);
        assertThat(uniqueOrders).isEqualTo(messageCount);

        ChatPendingMessageRepository repository = new ChatPendingMessageRepository(new JdbcTemplate(dataSource));
        assertThat(repository.findVisibleByConversationId("conversation-1"))
            .extracting(message -> message.position())
            .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, messageCount).boxed().toList());
        assertThat(repository.claimOldest("conversation-1").orElseThrow().message().position()).isEqualTo(1);
    }

    @Test
    void schemaEnforcesUniqueMessageOrderPerConversation() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        ChatPendingMessageRepository repository = new ChatPendingMessageRepository(jdbcTemplate);
        repository.enqueue("conversation-1", "first", null, null, null);

        assertThatThrownBy(() -> rawInsert(
            jdbcTemplate,
            "duplicate-order",
            "conversation-1",
            1,
            "duplicate"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void startupNormalizesLegacyDuplicateOrderKeysBeforeAddingUniqueIndex() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createLegacyPendingTable(jdbcTemplate);
        rawInsert(jdbcTemplate, "b", "conversation-1", 1, "second-id");
        rawInsert(jdbcTemplate, "a", "conversation-1", 1, "first-id");
        rawInsert(jdbcTemplate, "c", "conversation-1", 2, "third");

        ChatPendingMessageRepository repository = new ChatPendingMessageRepository(jdbcTemplate);

        assertThat(repository.findVisibleByConversationId("conversation-1"))
            .extracting(message -> message.messageText())
            .containsExactly("first-id", "second-id", "third");
        assertThat(jdbcTemplate.queryForList(
            "select message_order from ai_chat_pending_messages where conversation_id = ? order by message_order",
            Integer.class,
            "conversation-1"
        )).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> rawInsert(jdbcTemplate, "d", "conversation-1", 1, "duplicate"))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void schemaSqlNormalizesLegacyDuplicateOrderKeysBeforeRepositoryStartup() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createLegacyPendingTable(jdbcTemplate);
        rawInsert(jdbcTemplate, "b", "conversation-1", 1, "second-id");
        rawInsert(jdbcTemplate, "a", "conversation-1", 1, "first-id");
        rawInsert(jdbcTemplate, "c", "conversation-1", 2, "third");

        applySchema(jdbcTemplate);
        ChatPendingMessageRepository repository = new ChatPendingMessageRepository(jdbcTemplate);

        assertThat(repository.findVisibleByConversationId("conversation-1"))
            .extracting(message -> message.messageText())
            .containsExactly("first-id", "second-id", "third");
        assertThat(jdbcTemplate.queryForList(
            "select message_order from ai_chat_pending_messages where conversation_id = ? order by message_order",
            Integer.class,
            "conversation-1"
        )).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> rawInsert(jdbcTemplate, "d", "conversation-1", 1, "duplicate"))
            .isInstanceOf(DataAccessException.class);
    }

    private ChatPendingMessageRepository repository() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        return new ChatPendingMessageRepository(new JdbcTemplate(dataSource));
    }

    private DriverManagerDataSource fileDataSource(Path dbPath) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:sqlite:" + dbPath + "?foreign_keys=true&busy_timeout=5000"
        );
        dataSource.setDriverClassName("org.sqlite.JDBC");
        return dataSource;
    }

    private void createLegacyPendingTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
            create table ai_chat_pending_messages (
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
    }

    private void applySchema(JdbcTemplate jdbcTemplate) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
    }

    private void rawInsert(
        JdbcTemplate jdbcTemplate,
        String id,
        String conversationId,
        int messageOrder,
        String messageText
    ) {
        jdbcTemplate.update(
            """
                insert into ai_chat_pending_messages (
                    id, conversation_id, message_order, message_text, model, planning_model,
                    surface, status, claim_token, claimed_at, created_at, updated_at
                )
                values (?, ?, ?, ?, null, null, null, 'PENDING', null, null, ?, ?)
                """,
            id,
            conversationId,
            messageOrder,
            messageText,
            "2026-05-31T00:00:00Z",
            "2026-05-31T00:00:00Z"
        );
    }
}
