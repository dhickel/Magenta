package io.mindspice.magenta2.ai.chat.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolTranscriptService {
    public static final String FULL_PREFIX = "[[MAGENTA_TOOL_RESULT_FULL]]\n";
    public static final String SUMMARY_PREFIX = "[[MAGENTA_TOOL_RESULT_SUMMARY]]\n";

    private static final int RAW_OUTPUT_CHARACTER_LIMIT = 4_000;
    private static final int STORED_RAW_OUTPUT_CHARACTER_LIMIT = 40_000;
    private static final int RETAIN_FULL_OUTPUT_USER_TURNS = 4;
    private static final int ARGUMENT_SUMMARY_CHARACTER_LIMIT = 500;

    private final ObjectMapper objectMapper;

    public ToolTranscriptService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SystemMessage fullResult(String toolCallId, String toolName, String argumentsJson, String resultText) {
        ToolTranscriptEntry entry = new ToolTranscriptEntry(
            UUID.randomUUID().toString(),
            valueOrGenerated(toolCallId),
            toolName,
            summarizeArguments(argumentsJson),
            summarizeResult(resultText),
            storedResultText(resultText),
            "completed",
            Instant.now().toString(),
            resultText != null && resultText.length() > STORED_RAW_OUTPUT_CHARACTER_LIMIT,
            resultText != null && resultText.length() > RAW_OUTPUT_CHARACTER_LIMIT
        );
        return new SystemMessage(FULL_PREFIX + serialize(entry));
    }

    public List<Message> truncateExpiredLargeResults(List<Message> messages) {
        List<Message> rewritten = new ArrayList<>(messages.size());
        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (isFullToolTranscript(message)) {
                ToolTranscriptEntry entry = parse(message);
                if (entry != null && entry.largeResult() && userTurnsAfter(messages, i) >= RETAIN_FULL_OUTPUT_USER_TURNS) {
                    rewritten.add(new SystemMessage(SUMMARY_PREFIX + serialize(entry.withoutRawOutput())));
                    changed = true;
                    continue;
                }
            }
            rewritten.add(message);
        }
        return changed ? rewritten : messages;
    }

    public boolean isToolTranscript(Message message) {
        return isFullToolTranscript(message) || isSummaryToolTranscript(message);
    }

    public boolean isFullToolTranscript(Message message) {
        return message instanceof SystemMessage
            && message.getText() != null
            && message.getText().startsWith(FULL_PREFIX);
    }

    public boolean isSummaryToolTranscript(Message message) {
        return message instanceof SystemMessage
            && message.getText() != null
            && message.getText().startsWith(SUMMARY_PREFIX);
    }

    public String renderForModel(Message message) {
        ToolTranscriptEntry entry = parse(message);
        if (entry == null) {
            return message.getText() == null ? "" : message.getText();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Tool activity available as context.\n")
            .append("Tool: ").append(entry.toolName()).append("\n")
            .append("Status: ").append(entry.status()).append("\n")
            .append("Time: ").append(entry.createdAt()).append("\n")
            .append("Call id: ").append(entry.toolCallId()).append("\n")
            .append("Arguments summary: ").append(entry.argumentsSummary()).append("\n")
            .append("Result summary: ").append(entry.resultSummary()).append("\n");
        if (entry.truncated()) {
            builder.append("Raw output: truncated; exact prior output is no longer in context.");
        } else {
            builder.append("Raw output:\n").append(entry.resultText());
        }
        return builder.toString();
    }

    public String renderForHistory(Message message) {
        ToolTranscriptEntry entry = parse(message);
        if (entry == null) {
            return message.getText() == null ? "" : message.getText();
        }
        return "Tool " + entry.toolName()
            + " " + entry.status()
            + " at " + entry.createdAt()
            + ". " + entry.resultSummary();
    }

    private int userTurnsAfter(List<Message> messages, int index) {
        int count = 0;
        for (int i = index + 1; i < messages.size(); i++) {
            if (messages.get(i) instanceof UserMessage) {
                count++;
            }
        }
        return count;
    }

    private ToolTranscriptEntry parse(Message message) {
        String text = message.getText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String json;
        if (text.startsWith(FULL_PREFIX)) {
            json = text.substring(FULL_PREFIX.length());
        } else if (text.startsWith(SUMMARY_PREFIX)) {
            json = text.substring(SUMMARY_PREFIX.length());
        } else {
            return null;
        }
        try {
            return objectMapper.readValue(json, ToolTranscriptEntry.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String serialize(ToolTranscriptEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize tool transcript entry", exception);
        }
    }

    private String summarizeArguments(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return "No arguments.";
        }
        return truncate(argumentsJson.replaceAll("\\s+", " ").trim(), ARGUMENT_SUMMARY_CHARACTER_LIMIT);
    }

    private String summarizeResult(String resultText) {
        if (!StringUtils.hasText(resultText)) {
            return "Tool returned no text.";
        }
        return "Returned " + resultText.length() + " characters.";
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 15)).trim() + " ... [truncated]";
    }

    private String storedResultText(String resultText) {
        if (resultText == null) {
            return "";
        }
        return truncate(resultText, STORED_RAW_OUTPUT_CHARACTER_LIMIT);
    }

    private String valueOrGenerated(String value) {
        return StringUtils.hasText(value) ? value : UUID.randomUUID().toString();
    }

    public record ToolTranscriptEntry(
        String id,
        String toolCallId,
        String toolName,
        String argumentsSummary,
        String resultSummary,
        String resultText,
        String status,
        String createdAt,
        boolean truncated,
        boolean largeResult
    ) {
        public ToolTranscriptEntry withoutRawOutput() {
            return new ToolTranscriptEntry(
                id,
                toolCallId,
                toolName,
                argumentsSummary,
                resultSummary,
                "",
                status,
                createdAt,
                true,
                largeResult
            );
        }
    }
}
