package io.mindspice.magenta.runtime.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.magenta.runtime.tools.ToolExecutionSettings;
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ShellTools {

    private static final ObjectMapper MAPPER = ToolPayloads.mapper();
    private static final int DEFAULT_SHELL_TIMEOUT_MS = 10_000;
    private static final int MAX_SHELL_TIMEOUT_MS = 120_000;

    private final ToolExecutionSettings settings;

    public ShellTools(ToolExecutionSettings settings) {
        this.settings = settings;
    }

    public ToolResult shellCommand(ToolRequest request) {
        JsonNode args = readArgsOrNull(request);
        if (args == null || !args.isObject()) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        String command = readFirstString(args, List.of("cmd", "command"));
        if (isBlank(command)) {
            return ToolPayloads.failure(request, "validation_error", "Missing required argument: cmd", null, true);
        }

        int timeoutMs = intValue(args.get("timeoutMs"), DEFAULT_SHELL_TIMEOUT_MS);
        if (timeoutMs <= 0) {
            return ToolPayloads.failure(request, "validation_error", "timeoutMs must be > 0", null, true);
        }
        timeoutMs = Math.min(timeoutMs, MAX_SHELL_TIMEOUT_MS);

        Instant startedAt = Instant.now();
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-lc", command);
            processBuilder.directory(settings.workspaceRoot().toFile());
            process = processBuilder.start();
            final Process runningProcess = process;

            StreamCapture stdoutCapture;
            StreamCapture stderrCapture;

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<StreamCapture> stdoutFuture = executor.submit(
                        () -> readStreamLimited(runningProcess.getInputStream(), settings.maxToolOutputBytes())
                );
                Future<StreamCapture> stderrFuture = executor.submit(
                        () -> readStreamLimited(runningProcess.getErrorStream(), settings.maxToolOutputBytes())
                );

                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }

                stdoutCapture = stdoutFuture.get();
                stderrCapture = stderrFuture.get();

                long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
                boolean timedOut = !finished;
                int exitCode = timedOut ? -1 : process.exitValue();
                boolean succeeded = !timedOut && exitCode == 0;

                ObjectNode data = MAPPER.createObjectNode();
                data.put("command", command);
                data.put("exitCode", exitCode);
                data.put("timedOut", timedOut);
                data.put("durationMs", durationMs);
                data.put("stdout", stdoutCapture.text());
                data.put("stderr", stderrCapture.text());
                data.put("stdoutTruncated", stdoutCapture.truncated());
                data.put("stderrTruncated", stderrCapture.truncated());
                data.put("stdoutBytes", stdoutCapture.bytesSeen());
                data.put("stderrBytes", stderrCapture.bytesSeen());

                if (succeeded) {
                    return ToolPayloads.success(request, "Command completed", data);
                }
                String code = timedOut ? "command_timeout" : "command_failed";
                String message = timedOut ? "Command timed out" : "Command failed";
                return ToolPayloads.failure(request, code, message, data, true);
            }
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return ToolPayloads.failure(request, "command_error", "Failed to execute command: " + e.getMessage(), null, true);
        }
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

    private StreamCapture readStreamLimited(InputStream inputStream, int limitBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(4096, limitBytes));
        byte[] buffer = new byte[4096];
        int bytesSeen = 0;
        boolean truncated = false;

        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            bytesSeen += read;
            int canWrite = Math.max(0, limitBytes - out.size());
            if (canWrite > 0) {
                out.write(buffer, 0, Math.min(canWrite, read));
            }
            if (read > canWrite) {
                truncated = true;
            }
        }

        return new StreamCapture(out.toString(StandardCharsets.UTF_8), bytesSeen, truncated);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private record StreamCapture(String text, int bytesSeen, boolean truncated) {
    }
}
