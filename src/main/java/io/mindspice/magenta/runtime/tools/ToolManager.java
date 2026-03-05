package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.tools.builtin.FileTools;
import io.mindspice.magenta.runtime.tools.builtin.ShellTools;
import io.mindspice.magenta.runtime.tools.builtin.SqliteTools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class ToolManager {

    private static final int DEFAULT_MAX_TOOL_OUTPUT_BYTES = 32_768;
    private static final int DEFAULT_MAX_FILE_READ_LINES = 200;
    private static final int DEFAULT_MAX_SQL_ROWS = 500;

    private final Map<String, Function<ToolRequest, ToolResult>> handlersByTool;
    private final ToolExecutionSettings settings;

    public ToolManager(Map<String, Function<ToolRequest, ToolResult>> handlersByTool) {
        this(
                new ToolExecutionSettings(
                        Path.of("").toAbsolutePath().normalize(),
                        DEFAULT_MAX_TOOL_OUTPUT_BYTES,
                        DEFAULT_MAX_FILE_READ_LINES,
                        DEFAULT_MAX_SQL_ROWS
                ),
                handlersByTool
        );
    }

    private ToolManager(ToolExecutionSettings settings, Map<String, Function<ToolRequest, ToolResult>> handlersByTool) {
        this.settings = settings;
        this.handlersByTool = handlersByTool == null ? Map.of() : Map.copyOf(handlersByTool);
    }

    public static ToolManager empty() {
        return new ToolManager(Map.of());
    }

    public static ToolManager withBuiltIns(RuntimeConfig runtimeConfig) {
        Objects.requireNonNull(runtimeConfig, "runtimeConfig");

        ToolExecutionSettings settings = new ToolExecutionSettings(
                runtimeConfig.workspaceRoot(),
                runtimeConfig.maxToolOutputBytes(),
                runtimeConfig.maxFileReadLines(),
                runtimeConfig.maxSqlRows()
        );

        FileTools fileTools = new FileTools(settings);
        ShellTools shellTools = new ShellTools(settings);
        SqliteTools sqliteTools = new SqliteTools(settings);

        return new ToolManager(settings, Map.of(
                "read_file", fileTools::readFile,
                "grep_files", fileTools::grepFiles,
                "search_replace", fileTools::searchReplace,
                "write_file", fileTools::writeFile,
                "shell_command", shellTools::shellCommand,
                "sqlite_query", sqliteTools::sqliteQuery,
                "sqlite_exec", sqliteTools::sqliteExec
        ));
    }

    public ToolResult execute(ToolRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.toolCall() == null) {
            return new ToolResult(
                    "",
                    "",
                    ToolPayloads.payload("failed", "validation_error", "Missing tool call", null),
                    true
            );
        }

        String toolName = request.toolCall().name() == null ? "" : request.toolCall().name().trim();
        if (toolName.isEmpty()) {
            return normalizeResult(request, ToolPayloads.failure(request, "validation_error", "Tool name is blank", null, true));
        }

        Function<ToolRequest, ToolResult> handler = handlersByTool.get(toolName);
        if (handler == null) {
            return normalizeResult(request, ToolResult.notHandled(request.toolCall()));
        }

        ToolResult result;
        try {
            result = handler.apply(request);
        } catch (Throwable t) {
            ObjectNode data = ToolPayloads.mapper().createObjectNode();
            data.put("errorType", t.getClass().getSimpleName());
            result = ToolPayloads.failure(request, "handler_exception", "Tool execution failed", data, true);
        }

        if (result == null) {
            result = ToolResult.notHandled(request.toolCall());
        }
        return normalizeResult(request, result);
    }

    private ToolResult normalizeResult(ToolRequest request, ToolResult raw) {
        String toolCallId = isBlank(raw.toolCallId()) ? request.toolCall().id() : raw.toolCallId();
        String toolName = isBlank(raw.toolName()) ? request.toolCall().name() : raw.toolName();

        String content = raw.content() == null ? "" : raw.content();
        String normalizedPayload = ToolPayloads.normalizePayload(content);

        int byteCount = normalizedPayload.getBytes(StandardCharsets.UTF_8).length;
        if (byteCount > settings.maxToolOutputBytes()) {
            ObjectNode data = ToolPayloads.mapper().createObjectNode();
            data.put("maxBytes", settings.maxToolOutputBytes());
            data.put("actualBytes", byteCount);
            return new ToolResult(
                    toolCallId,
                    toolName,
                    ToolPayloads.payload("failed", "output_too_large", "Tool output exceeded maxToolOutputBytes", data),
                    true
            );
        }

        return new ToolResult(toolCallId, toolName, normalizedPayload, raw.handled());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
