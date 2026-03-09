package io.mindspice.magenta.runtime.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.magenta.runtime.persistence.CommonCommandResults;
import io.mindspice.magenta.runtime.persistence.ToolCommand;
import io.mindspice.magenta.runtime.persistence.ToolCommandResult;
import io.mindspice.magenta.runtime.tools.ToolExecutionSettings;
import io.mindspice.magenta.runtime.tools.ToolPathSupport;
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class TodoTools {

    private static final ObjectMapper MAPPER = ToolPayloads.mapper();
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 200;
    private static final String STATUS_OPEN = "open";
    private static final String STATUS_DONE = "done";

    private final ToolExecutionSettings settings;
    private final Function<ToolCommand, ToolCommandResult> commandBridge;

    public TodoTools(ToolExecutionSettings settings, Function<ToolCommand, ToolCommandResult> commandBridge) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.commandBridge = Objects.requireNonNull(commandBridge, "commandBridge");
    }

    public ToolResult todoCreate(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String sessionId = normalizedSessionId(request.sessionId());
        if (sessionId == null) {
            return ToolPayloads.failure(request, "validation_error", "Invalid session id", null, true);
        }

        String title = readFirstString(args, List.of("title"));
        if (isBlank(title)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: title", null, true);
        }

        String details = readFirstString(args, List.of("details"));
        details = details == null ? "" : details;

        ToolCommandResult result = commandBridge.apply(new ToolCommand.TodoCreate(sessionId, title.trim(), details));
        return switch (result) {
            case CommonCommandResults.Failure failure -> ToolPayloads.failure(request, failure.code(), failure.message(), null, true);
            case ToolCommandResult.TodoCreated created -> {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), created.dbPath()));
                data.set("todo", toTodoNode(created.todo()));
                yield ToolPayloads.success(request, "Todo created", data);
            }
            default -> ToolPayloads.failure(request, "handler_exception", "Unexpected todo create response", null, true);
        };
    }

    public ToolResult todoList(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String sessionId = normalizedSessionId(request.sessionId());
        if (sessionId == null) {
            return ToolPayloads.failure(request, "validation_error", "Invalid session id", null, true);
        }

        String requestedStatus = readFirstString(args, List.of("status"));
        String normalizedStatus = normalizeStatus(requestedStatus);
        if (requestedStatus != null && normalizedStatus == null) {
            return ToolPayloads.failure(request, "validation_error", "status must be one of: open, done", null, true);
        }

        int limit = intValue(args.get("limit"), DEFAULT_LIST_LIMIT);
        if (limit <= 0) {
            return ToolPayloads.failure(request, "validation_error", "limit must be > 0", null, true);
        }
        limit = Math.min(limit, MAX_LIST_LIMIT);

        ToolCommandResult result = commandBridge.apply(new ToolCommand.TodoList(sessionId, normalizedStatus, limit));
        return switch (result) {
            case CommonCommandResults.Failure failure -> ToolPayloads.failure(request, failure.code(), failure.message(), null, true);
            case ToolCommandResult.TodoListed listed -> {
                ArrayNode todos = MAPPER.createArrayNode();
                for (ToolCommandResult.TodoItem item : listed.todos()) {
                    todos.add(toTodoNode(item));
                }

                ObjectNode data = MAPPER.createObjectNode();
                data.put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), listed.dbPath()));
                data.put("count", todos.size());
                data.put("limit", listed.limit());
                data.put("truncated", listed.truncated());
                if (listed.statusOrNull() != null && !listed.statusOrNull().isBlank()) {
                    data.put("status", listed.statusOrNull());
                }
                data.set("todos", todos);
                yield ToolPayloads.success(request, "Todos listed", data);
            }
            default -> ToolPayloads.failure(request, "handler_exception", "Unexpected todo list response", null, true);
        };
    }

    public ToolResult todoUpdate(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String sessionId = normalizedSessionId(request.sessionId());
        if (sessionId == null) {
            return ToolPayloads.failure(request, "validation_error", "Invalid session id", null, true);
        }

        String todoId = readFirstString(args, List.of("todoId", "id"));
        if (isBlank(todoId)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: todoId", null, true);
        }

        boolean hasTitle = args.has("title");
        boolean hasDetails = args.has("details");
        boolean hasStatus = args.has("status");
        if (!hasTitle && !hasDetails && !hasStatus) {
            return ToolPayloads.failure(
                    request,
                    "validation_error",
                    "At least one update field is required: title, details, or status",
                    null,
                    true
            );
        }

        String title = readFirstString(args, List.of("title"));
        if (hasTitle && isBlank(title)) {
            return ToolPayloads.failure(request, "validation_error", "title cannot be blank", null, true);
        }

        String details = readFirstString(args, List.of("details"));

        String requestedStatus = readFirstString(args, List.of("status"));
        String normalizedStatus = normalizeStatus(requestedStatus);
        if (hasStatus && normalizedStatus == null) {
            return ToolPayloads.failure(request, "validation_error", "status must be one of: open, done", null, true);
        }

        ToolCommandResult result = commandBridge.apply(new ToolCommand.TodoUpdate(
                sessionId,
                todoId,
                hasTitle,
                hasTitle ? title.trim() : "",
                hasDetails,
                hasDetails ? (details == null ? "" : details) : "",
                hasStatus,
                hasStatus ? normalizedStatus : ""
        ));

        return switch (result) {
            case CommonCommandResults.Failure failure -> ToolPayloads.failure(request, failure.code(), failure.message(), null, true);
            case ToolCommandResult.TodoUpdated updated -> {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), updated.dbPath()));
                data.set("todo", toTodoNode(updated.todo()));
                yield ToolPayloads.success(request, "Todo updated", data);
            }
            default -> ToolPayloads.failure(request, "handler_exception", "Unexpected todo update response", null, true);
        };
    }

    public ToolResult todoDelete(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String sessionId = normalizedSessionId(request.sessionId());
        if (sessionId == null) {
            return ToolPayloads.failure(request, "validation_error", "Invalid session id", null, true);
        }

        String todoId = readFirstString(args, List.of("todoId", "id"));
        if (isBlank(todoId)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: todoId", null, true);
        }

        ToolCommandResult result = commandBridge.apply(new ToolCommand.TodoDelete(sessionId, todoId));
        return switch (result) {
            case CommonCommandResults.Failure failure -> ToolPayloads.failure(request, failure.code(), failure.message(), null, true);
            case ToolCommandResult.TodoDeleted deleted -> {
                ObjectNode data = MAPPER.createObjectNode();
                data.put("dbPath", ToolPathSupport.displayPath(settings.workspaceRoot(), deleted.dbPath()));
                data.put("todoId", deleted.todoId());
                data.put("deleted", true);
                yield ToolPayloads.success(request, "Todo deleted", data);
            }
            default -> ToolPayloads.failure(request, "handler_exception", "Unexpected todo delete response", null, true);
        };
    }

    private ObjectNode toTodoNode(ToolCommandResult.TodoItem row) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("todoId", row.todoId());
        node.put("sessionId", row.sessionId());
        node.put("title", row.title());
        node.put("details", row.details());
        node.put("status", row.status());
        node.put("createdAtMs", row.createdAtMs());
        node.put("updatedAtMs", row.updatedAtMs());
        return node;
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

    private String normalizedSessionId(String sessionId) {
        return isBlank(sessionId) ? null : sessionId.trim();
    }

    private String normalizeStatus(String statusRaw) {
        if (statusRaw == null) {
            return null;
        }
        String normalized = statusRaw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case STATUS_OPEN, STATUS_DONE -> normalized;
            default -> null;
        };
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
