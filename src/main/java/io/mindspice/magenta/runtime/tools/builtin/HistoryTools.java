package io.mindspice.magenta.runtime.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.magenta.runtime.persistence.CommonCommandResults;
import io.mindspice.magenta.runtime.persistence.ToolCommand;
import io.mindspice.magenta.runtime.persistence.ToolCommandResult;
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class HistoryTools {

    private static final ObjectMapper MAPPER = ToolPayloads.mapper();
    private static final int DEFAULT_META_LIMIT = 20;
    private static final int DEFAULT_RAW_MAX_CHARS = 6_000;

    private final Function<ToolCommand, ToolCommandResult> commandBridge;

    public HistoryTools(Function<ToolCommand, ToolCommandResult> commandBridge) {
        this.commandBridge = Objects.requireNonNull(commandBridge, "commandBridge");
    }

    public ToolResult historyMetaLookup(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String sessionId = normalizedSessionId(request.sessionId());
        if (sessionId == null) {
            return ToolPayloads.failure(request, "validation_error", "Invalid session id", null, true);
        }

        int limit = intValue(args.get("limit"), DEFAULT_META_LIMIT);
        if (limit <= 0) {
            return ToolPayloads.failure(request, "validation_error", "limit must be > 0", null, true);
        }
        Integer beforeMessageId = nullableInt(args.get("beforeMessageId"));
        if (beforeMessageId != null && beforeMessageId <= 0) {
            return ToolPayloads.failure(request, "validation_error", "beforeMessageId must be > 0", null, true);
        }

        String elementTypeFilter = normalizeText(readFirstString(args, List.of("elementTypeFilter", "elementType")));
        String toolNameFilter = normalizeText(readFirstString(args, List.of("toolNameFilter", "toolName")));
        boolean includeDropped = boolValue(args.get("includeDropped"), true);

        ToolCommandResult result = commandBridge.apply(new ToolCommand.HistoryMetaLookup(
                sessionId,
                limit,
                beforeMessageId,
                elementTypeFilter,
                toolNameFilter,
                includeDropped
        ));

        return switch (result) {
            case CommonCommandResults.Failure failure -> ToolPayloads.failure(request, failure.code(), failure.message(), null, true);
            case ToolCommandResult.HistoryMetaListed listed -> {
                ArrayNode rows = MAPPER.createArrayNode();
                for (ToolCommandResult.HistoryMetaItem item : listed.items()) {
                    ObjectNode row = MAPPER.createObjectNode();
                    row.put("messageId", item.messageId());
                    row.put("elementType", item.elementType());
                    row.put("toolCallId", item.toolCallId());
                    row.put("toolName", item.toolName());
                    row.put("status", item.status());
                    row.put("code", item.code());
                    row.put("preview", item.preview());
                    row.put("createdAtMs", item.createdAtMs());
                    row.put("dropped", item.dropped());
                    rows.add(row);
                }

                ObjectNode data = MAPPER.createObjectNode();
                data.put("kind", "history_meta");
                data.put("limit", listed.limit());
                data.put("truncated", listed.truncated());
                data.put("nextBeforeMessageId", listed.nextBeforeMessageId());
                data.put("includeDropped", listed.includeDropped());
                if (!listed.elementTypeFilter().isBlank()) {
                    data.put("elementTypeFilter", listed.elementTypeFilter());
                }
                if (!listed.toolNameFilter().isBlank()) {
                    data.put("toolNameFilter", listed.toolNameFilter());
                }
                data.set("rows", rows);
                yield ToolPayloads.success(request, "history_meta_result", "History metadata loaded", data);
            }
            default -> ToolPayloads.failure(request, "handler_exception", "Unexpected history_meta_lookup response", null, true);
        };
    }

    public ToolResult historyRawLookup(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String sessionId = normalizedSessionId(request.sessionId());
        if (sessionId == null) {
            return ToolPayloads.failure(request, "validation_error", "Invalid session id", null, true);
        }

        Integer messageId = nullableInt(args.get("messageId"));
        if (messageId == null || messageId <= 0) {
            return ToolPayloads.failure(request, "validation_error", "messageId must be > 0", null, true);
        }

        int startChar = intValue(args.get("startChar"), 0);
        if (startChar < 0) {
            return ToolPayloads.failure(request, "validation_error", "startChar must be >= 0", null, true);
        }
        int maxChars = intValue(args.get("maxChars"), DEFAULT_RAW_MAX_CHARS);
        if (maxChars <= 0) {
            return ToolPayloads.failure(request, "validation_error", "maxChars must be > 0", null, true);
        }

        ToolCommandResult result = commandBridge.apply(new ToolCommand.HistoryRawLookup(
                sessionId,
                messageId,
                startChar,
                maxChars
        ));

        return switch (result) {
            case CommonCommandResults.Failure failure -> ToolPayloads.failure(request, failure.code(), failure.message(), null, true);
            case ToolCommandResult.HistoryRawLoaded loaded -> {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("kind", "history_raw");
                data.put("messageId", loaded.messageId());
                data.put("elementType", loaded.elementType());
                data.put("toolCallId", loaded.toolCallId());
                data.put("toolName", loaded.toolName());
                data.put("startChar", loaded.startChar());
                data.put("returnedChars", loaded.returnedChars());
                data.put("totalChars", loaded.totalChars());
                data.put("hasMore", loaded.hasMore());
                data.put("dropped", loaded.dropped());
                data.put("createdAtMs", loaded.createdAtMs());
                data.put("rawContent", loaded.rawContentSlice());
                yield ToolPayloads.success(request, "history_raw_result", "History raw content loaded", data);
            }
            default -> ToolPayloads.failure(request, "handler_exception", "Unexpected history_raw_lookup response", null, true);
        };
    }

    private JsonNode readArgsOrNull(ToolRequest request) {
        String argsJson = request.toolCall().argumentsJson();
        if (isBlank(argsJson)) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(argsJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readFirstString(JsonNode args, List<String> keys) {
        if (args == null || !args.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode node = args.get(key);
            if (node != null && node.isTextual()) {
                return node.asText();
            }
        }
        return null;
    }

    private int intValue(JsonNode node, int defaultValue) {
        return node == null || !node.canConvertToInt() ? defaultValue : node.asInt();
    }

    private Integer nullableInt(JsonNode node) {
        if (node == null || !node.canConvertToInt()) {
            return null;
        }
        return node.asInt();
    }

    private boolean boolValue(JsonNode node, boolean defaultValue) {
        return node == null || !node.isBoolean() ? defaultValue : node.asBoolean();
    }

    private String normalizedSessionId(String sessionId) {
        return isBlank(sessionId) ? null : sessionId.trim();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
