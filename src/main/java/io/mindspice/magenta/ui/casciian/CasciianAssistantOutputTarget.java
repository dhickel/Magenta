package io.mindspice.magenta.ui.casciian;

import io.mindspice.magenta.ui.AssistantOutputTarget;

import java.util.Objects;
import java.util.regex.Pattern;

public final class CasciianAssistantOutputTarget implements AssistantOutputTarget {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[^>\\r\\n]{1,64}>\\s*");

    private final CasciianTerminalUiRuntime runtime;

    public CasciianAssistantOutputTarget(CasciianTerminalUiRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public synchronized void printAssistantToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        String sanitized = stripLeadingLabelPrefix(token);
        if (sanitized.isEmpty()) {
            return;
        }
        runtime.appendAssistantToken(sanitized);
    }

    @Override
    public synchronized void finishAssistantStreamLine() {
        runtime.finishAssistantStream();
    }

    @Override
    public synchronized void printAssistantFinal(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        runtime.appendAssistant(stripLeadingLabelPrefix(text));
    }

    @Override
    public void printToolCall(String toolName, String argumentsJson) {
        // Internal detail; only rendered tool results are operator-relevant.
    }

    @Override
    public void printToolResult(String toolName, String content, boolean failed) {
        String status = failed ? "FAILED" : "OK";
        String line = "[Tool] " + label(toolName) + " " + status;
        if (content != null && !content.isBlank()) {
            line += " | " + compact(content);
        }
        runtime.appendToolLine(line);
    }

    @Override
    public void printStreamFallbackNotice(String reason) {
        // Kept intentionally quiet to reduce transcript noise.
    }

    private String stripLeadingLabelPrefix(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return PREFIX_PATTERN.matcher(value).replaceFirst("");
    }

    private String label(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "Tool";
        }
        return switch (toolName.trim().toLowerCase(java.util.Locale.ROOT)) {
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
            default -> toolName;
        };
    }

    private String compact(String text) {
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 220) {
            return normalized;
        }
        return normalized.substring(0, 217) + "...";
    }
}
