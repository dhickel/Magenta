package io.mindspice.magenta.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.ui.prompt.PromptService;
import io.mindspice.magenta.ui.prompt.UiPromptRequest;
import io.mindspice.magenta.ui.prompt.UiPromptResponse;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ToolApprovalPromptAdapter implements SecurityManager.ApprovalCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final int DEFAULT_ARGS_PREVIEW_MAX = 240;
    private final AtomicReference<PromptService> promptServiceRef = new AtomicReference<>();

    public void setPromptService(PromptService promptService) {
        promptServiceRef.set(Objects.requireNonNull(promptService, "promptService"));
    }

    @Override
    public SecurityManager.ApprovalResponse approve(SecurityManager.ApprovalRequest request) {
        PromptService promptService = promptServiceRef.get();
        if (promptService == null) {
            return SecurityManager.ApprovalResponse.DENY;
        }

        String rawArgs = request.argumentsJson() == null ? "" : request.argumentsJson();
        String argsPreview = truncate(rawArgs, DEFAULT_ARGS_PREVIEW_MAX);

        PreviewField previewField = buildPreviewField(request.toolName(), rawArgs);
        String message = buildPromptMessage(request, previewField, argsPreview);

        UiPromptResponse response = promptService.prompt(new UiPromptRequest.ConfirmPrompt(
                "Tool Approval",
                message,
                false
        ));

        return switch (response) {
            case UiPromptResponse.ConfirmResponse confirm -> confirm.approved()
                    ? SecurityManager.ApprovalResponse.APPROVE
                    : SecurityManager.ApprovalResponse.DENY;
            default -> SecurityManager.ApprovalResponse.DENY;
        };
    }

    private String buildPromptMessage(SecurityManager.ApprovalRequest request, PreviewField previewField, String argsPreview) {
        String cleanedReason = normalizeReason(request.reason());
        String toolLabel = label(request.toolName());
        StringBuilder builder = new StringBuilder("Approve tool execution?");
        builder.append("\n  Tool: ").append(toolLabel);
        if (!previewField.value().isBlank()) {
            builder.append("\n  ").append(previewField.label()).append(": ").append(previewField.value());
        } else if (!argsPreview.isBlank()) {
            builder.append("\n  Args: ").append(argsPreview);
        }
        if (!cleanedReason.isBlank()) {
            builder.append("\n  Reason: ").append(cleanedReason);
        }
        return builder.toString();
    }

    private PreviewField buildPreviewField(String toolName, String argsPreview) {
        JsonNode args = parseJson(argsPreview);
        String normalized = normalizeToolName(toolName);
        return switch (normalized) {
            case "shell_command" -> new PreviewField("Command", firstString(args, List.of("cmd", "command")));
            case "sqlite_query", "sqlite_exec" -> new PreviewField("Database", firstString(args, List.of("dbPath", "path")));
            case "grep_files" -> new PreviewField("Root", firstString(args, List.of("rootPath", "path")));
            default -> new PreviewField("Path", firstString(args, List.of("path", "filePath", "targetPath")));
        };
    }

    private String label(String toolName) {
        return switch (normalizeToolName(toolName)) {
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
            default -> toolName == null || toolName.isBlank() ? "Tool" : toolName;
        };
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        String cleaned = reason
                .replace("Approval required:", "")
                .replace("Approval required by", "")
                .trim();
        return cleaned.isBlank() ? reason.trim() : cleaned;
    }

    private String normalizeToolName(String toolName) {
        return toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
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

    private String firstString(JsonNode node, List<String> keys) {
        if (node == null || !node.isObject()) {
            return "";
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return "";
    }

    private record PreviewField(String label, String value) {}

    private String truncate(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int max = Math.max(32, maxChars);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 3) + "...";
    }
}
