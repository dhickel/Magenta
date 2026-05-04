package io.mindspice.magenta2.ai.chat.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.mindspice.magenta2.ai.chat.model.ContextUsage;
import io.mindspice.magenta2.ai.chat.tool.ToolTranscriptService.ToolTranscriptEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
    private static final Logger log = LoggerFactory.getLogger(AuditRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists audit_event (
                id integer primary key autoincrement,
                conversation_id text not null,
                sequence integer not null,
                event_type text not null,
                message_text text,
                message_metadata_json text,
                model text,
                tool_call_id text,
                tool_name text,
                arguments_json text,
                arguments_summary text,
                call_preview text,
                result_text text,
                result_summary text,
                result_preview text,
                tool_status text,
                result_truncated integer default 0,
                result_large integer default 0,
                compaction_method text,
                compaction_summary text,
                used_tokens integer,
                max_tokens integer,
                trigger_tokens integer,
                percent_used real,
                stored_message_count integer,
                recorded_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_audit_event_conversation
                on audit_event (conversation_id, sequence)
            """);

        // Migrate columns added after initial creation
        List<String> columns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('audit_event')", String.class
        );
        List<String> required = List.of(
            "message_text", "message_metadata_json", "model",
            "tool_call_id", "tool_name", "arguments_json", "arguments_summary",
            "call_preview", "result_text", "result_summary", "result_preview",
            "tool_status", "result_truncated", "result_large",
            "compaction_method", "compaction_summary",
            "used_tokens", "max_tokens", "trigger_tokens", "percent_used", "stored_message_count"
        );
        for (String col : required) {
            if (!columns.contains(col)) {
                String type = col.endsWith("_truncated") || col.endsWith("_large") ? "integer default 0"
                    : col.endsWith("_tokens") || col.endsWith("_count") ? "integer"
                    : col.equals("percent_used") ? "real"
                    : "text";
                jdbcTemplate.execute("alter table audit_event add column " + col + " " + type);
            }
        }
    }

    private int nextSequence(String conversationId) {
        Integer max = jdbcTemplate.queryForObject(
            "select coalesce(max(sequence), -1) from audit_event where conversation_id = ?",
            Integer.class,
            conversationId
        );
        return (max == null ? -1 : max) + 1;
    }

    public void recordUserMessage(String conversationId, String messageText, String model) {
        try {
            int seq = nextSequence(conversationId);
            jdbcTemplate.update(
                """
                    insert into audit_event (conversation_id, sequence, event_type, message_text, model, recorded_at)
                    values (?, ?, 'user_msg', ?, ?, ?)
                    """,
                conversationId, seq, messageText, model, Instant.now().toString()
            );
        } catch (Exception e) {
            log.debug("Audit: failed to record user_msg for {}: {}", conversationId, e.getMessage());
        }
    }

    public void recordAssistantMessage(String conversationId, String messageText,
                                       String metadataJson, String model) {
        try {
            int seq = nextSequence(conversationId);
            jdbcTemplate.update(
                """
                    insert into audit_event (conversation_id, sequence, event_type, message_text, message_metadata_json, model, recorded_at)
                    values (?, ?, 'assistant_msg', ?, ?, ?, ?)
                    """,
                conversationId, seq, messageText, metadataJson, model, Instant.now().toString()
            );
        } catch (Exception e) {
            log.debug("Audit: failed to record assistant_msg for {}: {}", conversationId, e.getMessage());
        }
    }

    public void recordToolExec(ToolTranscriptEntry entry, String conversationId, String model) {
        try {
            int seq = nextSequence(conversationId);
            jdbcTemplate.update(
                """
                    insert into audit_event (conversation_id, sequence, event_type,
                        tool_call_id, tool_name, arguments_json, arguments_summary, call_preview,
                        result_text, result_summary, result_preview, tool_status,
                        result_truncated, result_large, model, recorded_at)
                    values (?, ?, 'tool_exec', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                conversationId, seq,
                entry.toolCallId(), entry.toolName(),
                entry.argumentsText(), entry.argumentsSummary(), entry.callPreview(),
                entry.resultText(), entry.resultSummary(), entry.resultPreview(),
                entry.status(),
                entry.truncated() ? 1 : 0, entry.largeResult() ? 1 : 0,
                model, Instant.now().toString()
            );
        } catch (Exception e) {
            log.debug("Audit: failed to record tool_exec for {}: {}", conversationId, e.getMessage());
        }
    }

    public void recordCompaction(String conversationId, ContextUsage usage, int storedMessageCount,
                                 String method, String summary, String model) {
        try {
            int seq = nextSequence(conversationId);
            jdbcTemplate.update(
                """
                    insert into audit_event (conversation_id, sequence, event_type,
                        compaction_method, compaction_summary,
                        used_tokens, max_tokens, trigger_tokens, percent_used, stored_message_count,
                        model, recorded_at)
                    values (?, ?, 'compaction', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                conversationId, seq,
                method, summary,
                usage.usedTokens(), usage.maxTokens(), usage.triggerTokens(),
                usage.percentUsed(), storedMessageCount,
                model, Instant.now().toString()
            );
        } catch (Exception e) {
            log.debug("Audit: failed to record compaction for {}: {}", conversationId, e.getMessage());
        }
    }

    public void recordContext(String conversationId, ContextUsage usage,
                              int storedMessageCount, String model) {
        try {
            int seq = nextSequence(conversationId);
            jdbcTemplate.update(
                """
                    insert into audit_event (conversation_id, sequence, event_type,
                        used_tokens, max_tokens, trigger_tokens, percent_used, stored_message_count,
                        model, recorded_at)
                    values (?, ?, 'context', ?, ?, ?, ?, ?, ?, ?)
                    """,
                conversationId, seq,
                usage.usedTokens(), usage.maxTokens(), usage.triggerTokens(),
                usage.percentUsed(), storedMessageCount,
                model, Instant.now().toString()
            );
        } catch (Exception e) {
            log.debug("Audit: failed to record context for {}: {}", conversationId, e.getMessage());
        }
    }

    public List<AuditEvent> findByConversationId(String conversationId) {
        return jdbcTemplate.query(
            """
                select sequence, event_type, message_text, message_metadata_json, model,
                       tool_call_id, tool_name, arguments_json, arguments_summary, call_preview,
                       result_text, result_summary, result_preview, tool_status,
                       result_truncated, result_large,
                       compaction_method, compaction_summary,
                       used_tokens, max_tokens, trigger_tokens, percent_used, stored_message_count,
                       recorded_at
                from audit_event
                where conversation_id = ?
                order by sequence asc
                """,
            (rs, rowNum) -> mapEvent(rs),
            conversationId
        );
    }

    private AuditEvent mapEvent(ResultSet rs) throws SQLException {
        return new AuditEvent(
            rs.getInt("sequence"),
            rs.getString("event_type"),
            rs.getString("message_text"),
            rs.getString("message_metadata_json"),
            rs.getString("model"),
            rs.getString("tool_call_id"),
            rs.getString("tool_name"),
            rs.getString("arguments_json"),
            rs.getString("arguments_summary"),
            rs.getString("call_preview"),
            rs.getString("result_text"),
            rs.getString("result_summary"),
            rs.getString("result_preview"),
            rs.getString("tool_status"),
            rs.getInt("result_truncated") != 0,
            rs.getInt("result_large") != 0,
            rs.getString("compaction_method"),
            rs.getString("compaction_summary"),
            rs.getInt("used_tokens"),
            rs.getInt("max_tokens"),
            rs.getInt("trigger_tokens"),
            rs.getDouble("percent_used"),
            rs.getInt("stored_message_count"),
            rs.getString("recorded_at")
        );
    }

    public record AuditEvent(
        int sequence,
        String eventType,
        String messageText,
        String messageMetadataJson,
        String model,
        String toolCallId,
        String toolName,
        String argumentsJson,
        String argumentsSummary,
        String callPreview,
        String resultText,
        String resultSummary,
        String resultPreview,
        String toolStatus,
        boolean resultTruncated,
        boolean resultLarge,
        String compactionMethod,
        String compactionSummary,
        int usedTokens,
        int maxTokens,
        int triggerTokens,
        double percentUsed,
        int storedMessageCount,
        String recordedAt
    ) {}
}
