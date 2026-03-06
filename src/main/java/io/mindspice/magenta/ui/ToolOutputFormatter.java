package io.mindspice.magenta.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.ui.render.UiStyle;

import java.util.List;

final class ToolOutputFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    FormattedToolCall formatCall(String toolName, String argumentsJson) {
        String label = label(toolName);
        JsonNode args = parseJson(argumentsJson);
        String target = target(toolName, args);
        String summary = switch (toolName) {
            case "read_file", "write_file", "delete_file", "search_replace", "grep_files" -> "path=" + target;
            case "shell_command" -> "cmd=" + target;
            case "sqlite_query", "sqlite_exec" -> "db=" + target;
            default -> "target=" + target;
        };
        return new FormattedToolCall("tool-call> " + label, List.of(summary), UiStyle.INFO);
    }

    FormattedToolResult formatResult(String toolName, String content) {
        String label = label(toolName);
        JsonNode root = parseJson(content);
        String status = text(root, "status", "ok");
        boolean failed = "failed".equalsIgnoreCase(status);
        String code = text(root, "code", "ok");
        String message = text(root, "message", "");
        JsonNode data = root == null ? null : root.get("data");

        String summary = failed ? failureSummary(code, message) : successSummary(toolName, data);
        UiStyle style = failed ? UiStyle.ERROR : UiStyle.INFO;
        String title = "tool-result> " + label + (failed ? " FAILED" : " OK");
        return new FormattedToolResult(title, List.of(summary), style, failed);
    }

    private String successSummary(String toolName, JsonNode data) {
        if (data == null || !data.isObject()) {
            return "completed";
        }
        return switch (toolName) {
            case "read_file" -> "bytes=" + intValue(data, "bytesRead")
                                + " lines=" + intValue(data, "returnedLines")
                                + "/" + intValue(data, "totalLines")
                                + " path=" + text(data, "path", "n/a");
            case "write_file" -> "bytes=" + intValue(data, "bytesWritten")
                                 + " overwrite=" + boolValue(data, "overwrote")
                                 + " path=" + text(data, "path", "n/a");
            case "delete_file" -> "bytes=" + intValue(data, "bytesDeleted")
                                  + " path=" + text(data, "path", "n/a");
            case "sqlite_query" -> "rows=" + intValue(data, "rowCount")
                                   + " truncated=" + boolValue(data, "truncated")
                                   + " db=" + text(data, "dbPath", "n/a");
            case "sqlite_exec" -> "rowsAffected=" + intValue(data, "rowsAffected")
                                  + " statements=" + intValue(data, "statementCount")
                                  + " db=" + text(data, "dbPath", "n/a");
            case "shell_command" -> "exit=" + intValue(data, "exitCode")
                                    + " durationMs=" + intValue(data, "durationMs")
                                    + " timedOut=" + boolValue(data, "timedOut");
            default -> "completed";
        };
    }

    private String failureSummary(String code, String message) {
        if (message.isBlank()) {
            return "code=" + code;
        }
        return "code=" + code + " message=" + compact(message);
    }

    private String label(String toolName) {
        return switch (toolName) {
            case "read_file" -> "READ FILE";
            case "write_file" -> "WRITE FILE";
            case "delete_file" -> "DELETE FILE";
            case "search_replace" -> "SEARCH REPLACE";
            case "grep_files" -> "GREP FILES";
            case "shell_command" -> "SHELL";
            case "sqlite_query" -> "SQL QUERY";
            case "sqlite_exec" -> "SQL EXEC";
            default -> toolName == null || toolName.isBlank() ? "TOOL" : toolName.toUpperCase();
        };
    }

    private String target(String toolName, JsonNode args) {
        if (args == null || !args.isObject()) {
            return "n/a";
        }
        String key = switch (toolName) {
            case "read_file", "write_file", "delete_file", "search_replace", "grep_files" -> "path";
            case "shell_command" -> "command";
            case "sqlite_query", "sqlite_exec" -> "dbPath";
            default -> "target";
        };
        if (!key.isBlank() && args.hasNonNull(key)) {
            return compact(args.get(key).asText(""));
        }
        if (args.hasNonNull("target")) {
            return compact(args.get("target").asText(""));
        }
        return "n/a";
    }

    private JsonNode parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String key, String fallback) {
        if (node == null || !node.isObject() || !node.hasNonNull(key)) {
            return fallback;
        }
        return node.get(key).asText(fallback);
    }

    private int intValue(JsonNode node, String key) {
        if (node == null || !node.isObject() || !node.has(key) || !node.get(key).isNumber()) {
            return 0;
        }
        return node.get(key).asInt();
    }

    private boolean boolValue(JsonNode node, String key) {
        return node != null && node.isObject() && node.has(key) && node.get(key).asBoolean(false);
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace('\n', ' ').trim();
        if (singleLine.length() <= 220) {
            return singleLine;
        }
        return singleLine.substring(0, 217) + "...";
    }

    record FormattedToolCall(String title, List<String> lines, UiStyle style) {}

    record FormattedToolResult(String title, List<String> lines, UiStyle style, boolean failed) {}
}
