package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShellToolsFunctionalTest {

    @TempDir
    Path tempDir;

    @Test
    void shellCommandReturnsFailureForNonZeroExit() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "shell_command",
                "{\"cmd\":\"exit 7\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("command_failed");
        assertThat(payload.path("data").path("exitCode").asInt()).isEqualTo(7);
    }

    @Test
    void shellCommandReturnsTimeoutCode() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "shell_command",
                "{\"cmd\":\"sleep 1\",\"timeoutMs\":10}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("command_timeout");
        assertThat(payload.path("data").path("timedOut").asBoolean()).isTrue();
    }

    @Test
    void shellCommandRejectsNonPositiveTimeout() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "shell_command",
                "{\"cmd\":\"echo ok\",\"timeoutMs\":0}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void shellCommandTimeoutTerminatesDescendantProcesses() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "shell_command",
                "{\"cmd\":\"sleep 5 & child=$!; echo $child > child.pid; wait\",\"timeoutMs\":200}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("command_timeout");
        assertThat(payload.path("data").path("descendantProcesses").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(payload.path("data").path("allProcessesTerminated").asBoolean()).isTrue();

        Path pidFile = tempDir.resolve("child.pid");
        if (java.nio.file.Files.exists(pidFile)) {
            long pid = Long.parseLong(java.nio.file.Files.readString(pidFile).trim());
            boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            assertThat(alive).isFalse();
        }
    }
}
