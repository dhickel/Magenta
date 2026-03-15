package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ToolPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final int DEFAULT_PREVIEW_MAX_CHARS = 2_000;

    private ToolPayloads() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static ToolResult success(ToolRequest request, String message, ObjectNode data) {
        return success(request, "ok", message, data);
    }

    public static ToolResult success(ToolRequest request, String code, String message, ObjectNode data) {
        return ToolResult.handled(
                request.toolCall().id(),
                request.toolCall().name(),
                payload("ok", code, message, data)
        );
    }

    public static ToolResult failure(
            ToolRequest request,
            String code,
            String message,
            ObjectNode data,
            boolean handled
    ) {
        return new ToolResult(
                request.toolCall().id(),
                request.toolCall().name(),
                payload("failed", code, message, data),
                handled
        );
    }

    public static String payload(String status, String code, String message, ObjectNode data) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("status", status == null ? "failed" : status);
        root.put("code", code == null ? "unknown" : code);
        root.put("message", message == null ? "" : message);
        if (data != null) {
            root.set("data", data);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return "{\"status\":\"failed\",\"code\":\"serialization_error\",\"message\":\"Failed to serialize tool payload\"}";
        }
    }

    public static String normalizePayload(String content) {
        if (content == null || content.isBlank()) {
            return payload("ok", "ok", "", null);
        }

        try {
            JsonNode jsonNode = MAPPER.readTree(content);
            if (jsonNode.isObject() && jsonNode.has("status") && jsonNode.has("code")) {
                return content;
            }
            ObjectNode data = MAPPER.createObjectNode();
            data.set("content", jsonNode);
            return payload("ok", "raw_passthrough", "Handler returned unstructured JSON payload", data);
        } catch (Exception ignored) {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("content", content);
            return payload("ok", "raw_passthrough", "Handler returned non-JSON payload", data);
        }
    }

    public static String buildToolPreview(String toolName, String rawPayload) {
        return buildToolPreview(toolName, rawPayload, DEFAULT_PREVIEW_MAX_CHARS);
    }

    public static String buildToolPreview(String toolName, String rawPayload, int maxChars) {
        String safeTool = toolName == null ? "" : toolName.trim();
        String safeRaw = rawPayload == null ? "" : rawPayload;
        int boundedMax = Math.max(128, maxChars);
        if (safeRaw.length() <= boundedMax) {
            return safeRaw;
        }
        JsonNode root = parseJsonOrNull(safeRaw);
        JsonNode data = root == null ? null : root.path("data");
        return switch (safeTool) {
            case "sqlite_exec" -> compactSqliteExecPreview(root, data, boundedMax);
            case "sqlite_query" -> compactSqliteQueryPreview(root, data, boundedMax);
            case "todo_create", "todo_list", "todo_update", "todo_delete" -> compactTodoPreview(root, data, boundedMax);
            default -> compactGenericPreview(root, safeRaw, boundedMax);
        };
    }

    private static String compactSqliteExecPreview(JsonNode root, JsonNode data, int maxChars) {
        ObjectNode compact = MAPPER.createObjectNode();
        compact.put("status", text(root, "status", "ok"));
        compact.put("code", text(root, "code", "sqlite_exec_receipt"));
        compact.put("message", text(root, "message", ""));
        ObjectNode compactData = compact.putObject("data");
        compactData.put("kind", "sqlite_exec_receipt");
        ObjectNode database = compactData.putObject("database");
        database.put("dbPath", text(data == null ? null : data.path("database"), "dbPath", text(data, "dbPath", "")));
        JsonNode receipt = data == null ? null : data.path("receipt");
        ObjectNode compactReceipt = compactData.putObject("receipt");
        compactReceipt.put("statementCount", intValue(receipt, "statementCount", intValue(data, "statementCount", 0)));
        compactReceipt.put("rowsAffected", intValue(receipt, "rowsAffected", intValue(data, "rowsAffected", 0)));
        compactReceipt.put("transactional", boolValue(receipt, "transactional", boolValue(data, "transactional", true)));
        long lastInsertRowId = longValue(receipt, "lastInsertRowId", longValue(data, "lastInsertRowId", 0L));
        if (lastInsertRowId > 0) {
            compactReceipt.put("lastInsertRowId", lastInsertRowId);
        }
        return writeCompacted(compact, maxChars);
    }

    private static String compactSqliteQueryPreview(JsonNode root, JsonNode data, int maxChars) {
        ObjectNode compact = MAPPER.createObjectNode();
        compact.put("status", text(root, "status", "ok"));
        compact.put("code", text(root, "code", "sqlite_query_result"));
        compact.put("message", text(root, "message", ""));
        ObjectNode compactData = compact.putObject("data");
        compactData.put("kind", "sqlite_query_result");
        ObjectNode database = compactData.putObject("database");
        database.put("dbPath", text(data == null ? null : data.path("database"), "dbPath", text(data, "dbPath", "")));
        JsonNode result = data == null ? null : data.path("result");
        ObjectNode compactResult = compactData.putObject("result");
        ArrayNode columns = compactResult.putArray("columns");
        JsonNode sourceColumns = result == null || result.isMissingNode() ? data == null ? null : data.path("columns") : result.path("columns");
        if (sourceColumns != null && sourceColumns.isArray()) {
            int count = 0;
            for (JsonNode column : sourceColumns) {
                if (count++ >= 12) {
                    break;
                }
                columns.add(column.asText(""));
            }
        }
        compactResult.put("rowCount", intValue(result, "rowCount", intValue(data, "rowCount", 0)));
        compactResult.put("truncated", boolValue(result, "truncated", boolValue(data, "truncated", false)));
        ArrayNode sampleRows = compactResult.putArray("rows");
        JsonNode rows = result == null || result.isMissingNode() ? data == null ? null : data.path("rows") : result.path("rows");
        if (rows != null && rows.isArray()) {
            int rowCount = 0;
            for (JsonNode row : rows) {
                if (rowCount++ >= 3) {
                    break;
                }
                sampleRows.add(row);
            }
        }
        return writeCompacted(compact, maxChars);
    }

    private static String compactTodoPreview(JsonNode root, JsonNode data, int maxChars) {
        ObjectNode compact = MAPPER.createObjectNode();
        compact.put("status", text(root, "status", "ok"));
        compact.put("code", text(root, "code", "ok"));
        compact.put("message", text(root, "message", ""));
        ObjectNode compactData = compact.putObject("data");
        compactData.put("kind", text(data, "kind", "todo_focus"));
        compactData.put("activeTodoId", text(data, "activeTodoId", ""));
        compactData.put("openCount", intValue(data, "openCount", 0));
        compactData.put("doneCount", intValue(data, "doneCount", 0));
        JsonNode focus = data == null ? null : data.path("focus");
        if (focus != null && focus.isObject()) {
            ObjectNode compactFocus = compactData.putObject("focus");
            compactFocus.put("todoId", text(focus, "todoId", ""));
            compactFocus.put("title", text(focus, "title", ""));
            compactFocus.put("status", text(focus, "status", ""));
            compactFocus.put("updatedAtMs", longValue(focus, "updatedAtMs", 0L));
        }
        return writeCompacted(compact, maxChars);
    }

    private static String compactGenericPreview(JsonNode root, String rawPayload, int maxChars) {
        if (root == null || !root.isObject()) {
            return ellipsis(rawPayload, maxChars);
        }
        ObjectNode compact = MAPPER.createObjectNode();
        compact.put("status", text(root, "status", "ok"));
        compact.put("code", text(root, "code", "ok"));
        compact.put("message", text(root, "message", ""));
        JsonNode data = root.path("data");
        if (data != null && data.isObject()) {
            ObjectNode compactData = compact.putObject("data");
            compactData.put("kind", text(data, "kind", ""));
        }
        return writeCompacted(compact, maxChars);
    }

    private static JsonNode parseJsonOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String writeCompacted(ObjectNode compactNode, int maxChars) {
        try {
            String json = MAPPER.writeValueAsString(compactNode);
            if (json.length() <= maxChars) {
                return json;
            }
            return ellipsis(json, maxChars);
        } catch (Exception ignored) {
            return ellipsis(compactNode.toString(), maxChars);
        }
    }

    private static String ellipsis(String value, int maxChars) {
        String safe = value == null ? "" : value;
        if (safe.length() <= maxChars) {
            return safe;
        }
        int end = Math.max(0, maxChars - 3);
        return safe.substring(0, end) + "...";
    }

    private static String text(JsonNode node, String key, String fallback) {
        if (node == null || !node.isObject() || !node.hasNonNull(key)) {
            return fallback;
        }
        return node.path(key).asText(fallback);
    }

    private static int intValue(JsonNode node, String key, int fallback) {
        if (node == null || !node.isObject() || !node.has(key)) {
            return fallback;
        }
        return node.path(key).asInt(fallback);
    }

    private static long longValue(JsonNode node, String key, long fallback) {
        if (node == null || !node.isObject() || !node.has(key)) {
            return fallback;
        }
        return node.path(key).asLong(fallback);
    }

    private static boolean boolValue(JsonNode node, String key, boolean fallback) {
        if (node == null || !node.isObject() || !node.has(key)) {
            return fallback;
        }
        return node.path(key).asBoolean(fallback);
    }
}
