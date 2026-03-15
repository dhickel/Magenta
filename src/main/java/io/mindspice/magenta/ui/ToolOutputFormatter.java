package io.mindspice.magenta.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.ui.render.UiStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ToolOutputFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final int MAX_COMPACT_LEN = 220;
    private static final int MAX_SQL_PREVIEW_LINES = 3;
    private static final int MAX_TODO_TITLE_LEN = 96;
    private static final int MAX_TODO_DETAILS_LEN = 72;

    public FormattedToolCall formatCall(String toolName, String argumentsJson) {
        String normalizedToolName = normalizeToolName(toolName);
        String label = label(normalizedToolName);
        JsonNode args = parseJson(argumentsJson);
        return new FormattedToolCall(
                "[Tool] " + label,
                List.of(callSummary(normalizedToolName, args)),
                UiStyle.INFO
        );
    }

    public FormattedToolResult formatResult(String toolName, String content) {
        String normalizedToolName = normalizeToolName(toolName);
        String label = label(normalizedToolName);
        JsonNode root = parseJson(content);
        String status = text(root, "status", "ok");
        boolean failed = "failed".equalsIgnoreCase(status);
        String code = text(root, "code", "ok");
        String message = text(root, "message", "");
        JsonNode data = root == null ? null : root.get("data");

        List<String> summaries = failed
                ? failureSummaries(normalizedToolName, code, message, data)
                : successSummaries(normalizedToolName, data);
        UiStyle style = failed ? UiStyle.ERROR : UiStyle.INFO;
        String title = "[Tool] " + label + (failed ? " FAILED" : " OK");
        return new FormattedToolResult(title, summaries, style, failed);
    }

    private List<String> successSummaries(String toolName, JsonNode data) {
        if (data == null || !data.isObject()) {
            return List.of("Completed");
        }
        return switch (toolName) {
            case "read_file" -> List.of(
                    "Path: " + text(data, "path", "n/a"),
                    "Lines: " + readLineSummary(data)
                            + (boolValue(data, "truncated") ? " (truncated)" : "")
            );
            case "write_file" -> List.of(
                    "Path: " + text(data, "path", "n/a"),
                    "Bytes: " + intValue(data, "bytesWritten")
                            + (boolValue(data, "overwrote") ? " (overwrote existing file)" : "")
            );
            case "delete_file" -> List.of(
                    "Path: " + text(data, "path", "n/a"),
                    "Bytes Deleted: " + intValue(data, "bytesDeleted")
            );
            case "list_directory" -> List.of(
                    "Path: " + text(data, "path", "n/a"),
                    "Entries: " + intValue(data, "entryCount")
                            + (boolValue(data, "truncated") ? " (truncated)" : "")
            );
            case "file_metadata" -> List.of(
                    "Path: " + text(data, "path", "n/a"),
                    "Type: " + metadataType(data)
            );
            case "grep_files" -> List.of(
                    "Root: " + text(data, "rootPath", "n/a"),
                    "Scanned: " + intValue(data, "scannedFiles"),
                    "Matches: " + intValue(data, "matchCount")
                            + (boolValue(data, "truncated") ? " (truncated)" : "")
            );
            case "search_replace" -> List.of(
                    "Path: " + text(data, "path", "n/a"),
                    "Applied Edits: " + intValue(data, "appliedEdits")
            );
            case "sqlite_query" -> sqliteQuerySummaries(data);
            case "sqlite_exec" -> sqliteExecSummaries(data);
            case "todo_create", "todo_update" -> List.of(todoFocusSummary(toolName, data));
            case "todo_list" -> List.of(
                    "Active Todo: " + text(data, "activeTodoId", "none"),
                    "Open: " + intValue(data, "openCount") + ", Done: " + intValue(data, "doneCount")
            );
            case "todo_delete" -> List.of(
                    "Deleted: " + text(data, "deletedTodoId", "n/a"),
                    "Next Focus: " + text(data, "activeTodoId", "none")
            );
            case "shell_command" -> List.of(
                    "Command: " + compact(text(data, "command", "n/a")),
                    "Exit: " + intValue(data, "exitCode")
                            + (boolValue(data, "timedOut") ? " (timed out)" : "")
            );
            default -> List.of("Completed");
        };
    }

    private List<String> failureSummaries(String toolName, String code, String message, JsonNode data) {
        List<String> lines = new ArrayList<>();
        lines.add("Code: " + code);

        String location = switch (toolName) {
            case "read_file", "write_file", "delete_file", "search_replace", "file_metadata", "list_directory" ->
                    text(data, "path", "");
            case "grep_files" -> text(data, "rootPath", "");
            case "sqlite_query", "sqlite_exec" -> firstNonBlank(
                    text(data, "dbPath", ""),
                    text(data == null ? null : data.path("database"), "dbPath", "")
            );
            case "shell_command" -> text(data, "command", "");
            default -> "";
        };
        if (!location.isBlank()) {
            String locationLabel = switch (toolName) {
                case "shell_command" -> "Command";
                case "sqlite_query", "sqlite_exec" -> "Database";
                case "grep_files" -> "Root";
                default -> "Path";
            };
            lines.add(locationLabel + ": " + compact(location));
        }

        if ("search_replace".equals(toolName)) {
            String conflictReason = searchReplaceConflictReason(data);
            if (!conflictReason.isBlank()) {
                lines.add("Conflict: " + conflictReason);
            }
        }

        if (!message.isBlank()) {
            lines.add("Message: " + compact(message));
        }
        String recoveryHint = text(data, "recoveryHint", "");
        if (!recoveryHint.isBlank()) {
            lines.add("Hint: " + compact(recoveryHint));
        }
        return lines;
    }

    private String label(String toolName) {
        return switch (toolName) {
            case "read_file" -> "Read File";
            case "write_file" -> "Write File";
            case "delete_file" -> "Delete File";
            case "list_directory" -> "List Directory";
            case "file_metadata" -> "File Metadata";
            case "search_replace" -> "Search Replace";
            case "grep_files" -> "Grep Files";
            case "shell_command" -> "Shell";
            case "sqlite_query" -> "SQL Query";
            case "sqlite_exec" -> "SQL Exec";
            default -> fallbackLabel(toolName);
        };
    }

    private String callSummary(String toolName, JsonNode args) {
        return switch (toolName) {
            case "shell_command" -> "Command: " + firstString(args, List.of("cmd", "command"), "n/a");
            case "sqlite_query", "sqlite_exec" -> "Database: " + firstString(args, List.of("dbPath", "path"), "n/a");
            case "grep_files" -> "Root: " + firstString(args, List.of("rootPath", "path", "targetPath"), ".");
            default -> "Path: " + firstString(args, List.of("path", "filePath", "targetPath"), "n/a");
        };
    }

    private String firstString(JsonNode args, List<String> keys, String fallback) {
        if (args == null || !args.isObject()) {
            return fallback;
        }
        for (String key : keys) {
            if (args.hasNonNull(key)) {
                return compact(args.get(key).asText(""));
            }
        }
        return fallback;
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

    private String metadataType(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "unknown";
        }
        if (boolValue(node, "directory")) {
            return "directory";
        }
        if (boolValue(node, "regularFile")) {
            return "file";
        }
        if (boolValue(node, "symbolicLink")) {
            return "symlink";
        }
        return "unknown";
    }

    private String searchReplaceConflictReason(JsonNode data) {
        if (data == null || !data.isObject()) {
            return "";
        }
        JsonNode conflicts = data.get("conflicts");
        if (conflicts == null || !conflicts.isArray() || conflicts.isEmpty()) {
            return "";
        }
        JsonNode first = conflicts.get(0);
        if (first == null || !first.isObject()) {
            return "";
        }
        return compact(text(first, "reason", ""));
    }

    private int intValue(JsonNode node, String key) {
        if (node == null || !node.isObject() || !node.has(key) || !node.get(key).isNumber()) {
            return 0;
        }
        return node.get(key).asInt();
    }

    private String readLineSummary(JsonNode data) {
        int total = intValue(data, "totalLines");
        int returned = intValue(data, "returnedLines");
        int start = intValue(data, "returnedStartLine");
        int end = intValue(data, "returnedEndLine");

        if (returned <= 0) {
            return "none/" + total;
        }
        if (start > 0 && end >= start) {
            return start + "-" + end + "/" + total;
        }
        return returned + "/" + total;
    }

    private boolean boolValue(JsonNode node, String key) {
        return node != null && node.isObject() && node.has(key) && node.get(key).asBoolean(false);
    }

    private List<String> sqliteQuerySummaries(JsonNode data) {
        List<String> lines = new ArrayList<>();
        lines.add("Database: " + text(data == null ? null : data.path("database"), "dbPath", "n/a"));
        appendSqlPreview(lines, text(data, "sqlPreview", ""));
        return lines;
    }

    private List<String> sqliteExecSummaries(JsonNode data) {
        List<String> lines = new ArrayList<>();
        lines.add("Database: " + text(data == null ? null : data.path("database"), "dbPath", "n/a"));
        appendSqlPreview(lines, text(data, "sqlPreview", ""));
        return lines;
    }

    private void appendSqlPreview(List<String> lines, String sqlPreview) {
        if (sqlPreview == null || sqlPreview.isBlank()) {
            return;
        }
        String[] physicalLines = sqlPreview.split("\\R");
        int visibleLines = Math.min(physicalLines.length, MAX_SQL_PREVIEW_LINES);
        for (int i = 0; i < visibleLines; i++) {
            String compactLine = compact(physicalLines[i]);
            if (i == 0) {
                lines.add("SQL: " + compactLine);
            } else {
                lines.add("     " + compactLine);
            }
        }
        if (physicalLines.length > MAX_SQL_PREVIEW_LINES) {
            int lastIdx = lines.size() - 1;
            String last = lines.get(lastIdx);
            lines.set(lastIdx, last.endsWith("...") ? last : last + " ...");
        }
    }

    private String todoFocusSummary(String toolName, JsonNode data) {
        JsonNode focus = data == null ? null : data.path("focus");
        String title = firstNonBlank(
                text(focus, "title", ""),
                text(data, "title", ""),
                text(data, "activeTodoId", "n/a")
        );
        String verb = "todo_create".equals(toolName) ? "Created" : "Updated";
        return "Todo " + verb + ": " + compact(title, MAX_TODO_TITLE_LEN);
    }

    private String compact(String value) {
        return compact(value, MAX_COMPACT_LEN);
    }

    private String compact(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace('\n', ' ').trim();
        int boundedMax = Math.max(24, maxLen);
        if (singleLine.length() <= boundedMax) {
            return singleLine;
        }
        return singleLine.substring(0, boundedMax - 3) + "...";
    }

    private String fallbackLabel(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "Tool";
        }
        String[] parts = toolName.trim().replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? "Tool" : out.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String normalizeToolName(String toolName) {
        return toolName == null ? "" : toolName.trim();
    }

    public record FormattedToolCall(String title, List<String> lines, UiStyle style) {}

    public record FormattedToolResult(String title, List<String> lines, UiStyle style, boolean failed) {}
}
