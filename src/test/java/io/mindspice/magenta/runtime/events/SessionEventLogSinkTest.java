package io.mindspice.magenta.runtime.events;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.session.SessionHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEventLogSinkTest {

    @TempDir
    Path tempDir;

    @Test
    void infoLevelRedactsToolResultContentToPreview() throws Exception {
        SessionEventLogSink sink = new SessionEventLogSink(
                tempDir,
                new RuntimeConfig.ObservabilityConfig(RuntimeConfig.LogLevel.INFO, false)
        );

        String content = "{\"status\":\"ok\",\"message\":\"" + "x".repeat(2048) + "\"}";
        sink.append(toolResultEvent(content));

        Path jsonl = tempDir.resolve("logs/session-events.jsonl");
        String line = Files.readString(jsonl);
        assertThat(line).contains("\\\"previewTruncated\\\":true");
        assertThat(line).contains("fullSizeChars");
        assertThat(line).doesNotContain("x".repeat(1400));
    }

    @Test
    void debugLevelKeepsFullToolResultContent() throws Exception {
        SessionEventLogSink sink = new SessionEventLogSink(
                tempDir,
                new RuntimeConfig.ObservabilityConfig(RuntimeConfig.LogLevel.DEBUG, false)
        );

        String content = "{\"status\":\"ok\",\"message\":\"" + "y".repeat(1400) + "\"}";
        sink.append(toolResultEvent(content));

        Path jsonl = tempDir.resolve("logs/session-events.jsonl");
        String line = Files.readString(jsonl);
        assertThat(line).contains("y".repeat(1200));
    }

    @Test
    void errorLevelLogsFailedToolResultsOnly() throws Exception {
        SessionEventLogSink sink = new SessionEventLogSink(
                tempDir,
                new RuntimeConfig.ObservabilityConfig(RuntimeConfig.LogLevel.ERROR, false)
        );

        sink.append(toolResultEvent("{\"status\":\"ok\",\"message\":\"fine\"}"));
        sink.append(toolResultEvent("{\"status\":\"failed\",\"code\":\"boom\"}"));

        Path jsonl = tempDir.resolve("logs/session-events.jsonl");
        String line = Files.readString(jsonl);
        assertThat(line).contains("failed");
        assertThat(line).doesNotContain("fine");
    }

    @Test
    void prettyLogFileNotWrittenWhenToggleDisabled() throws Exception {
        SessionEventLogSink sink = new SessionEventLogSink(
                tempDir,
                new RuntimeConfig.ObservabilityConfig(RuntimeConfig.LogLevel.INFO, false)
        );

        sink.append(toolResultEvent("{\"status\":\"ok\"}"));

        assertThat(Files.exists(tempDir.resolve("logs/session-events.pretty.json"))).isFalse();
    }

    private SessionEvent.Action.ToolResult toolResultEvent(String content) {
        SessionHandle handle = new SessionHandle(UUID.randomUUID(), () -> true);
        return new SessionEvent.Action.ToolResult(handle, "agent-default", "read_file", "call-1", content);
    }
}
