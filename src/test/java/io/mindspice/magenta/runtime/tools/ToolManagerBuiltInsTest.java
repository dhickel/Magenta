package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolManagerBuiltInsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void readFileReturnsAnchorsAndSnapshot() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult result = manager.execute(request("read_file", "{\"path\":\"sample.txt\"}"));

        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("snapshotId").asText()).isNotBlank();
        assertThat(payload.path("data").path("lines")).hasSize(2);
        assertThat(payload.path("data").path("lines").get(0).path("anchor").asText()).startsWith("1:");
    }

    @Test
    void searchReplaceFailsOnSnapshotMismatch() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        String args = """
                {
                  "path": "sample.txt",
                  "snapshotId": "bad",
                  "edits": [
                    {"startAnchor":"1:00","endAnchor":"1:00","replacement":"alpha"}
                  ]
                }
                """;

        ToolResult result = manager.execute(request("search_replace", args));
        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("snapshot_mismatch");
    }

    @Test
    void searchReplaceAppliesAnchorBasedEdit() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult read = manager.execute(request("read_file", "{\"path\":\"sample.txt\"}"));
        JsonNode readPayload = MAPPER.readTree(read.content());

        String snapshotId = readPayload.path("data").path("snapshotId").asText();
        String anchor = readPayload.path("data").path("lines").get(0).path("anchor").asText();

        String args = MAPPER.writeValueAsString(Map.of(
                "path", "sample.txt",
                "snapshotId", snapshotId,
                "edits", List.of(Map.of(
                        "startAnchor", anchor,
                        "endAnchor", anchor,
                        "replacement", "omega"
                ))
        ));

        ToolResult replace = manager.execute(request("search_replace", args));
        JsonNode replacePayload = MAPPER.readTree(replace.content());

        assertThat(replacePayload.path("status").asText()).isEqualTo("ok");
        assertThat(Files.readString(tempDir.resolve("sample.txt"))).isEqualTo("omega");
    }

    @Test
    void sqliteExecAndQueryRoundTrip() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));

        String execArgs = MAPPER.writeValueAsString(Map.of(
                "dbPath", "test.db",
                "sql", "CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT); INSERT INTO t(name) VALUES ('a'); INSERT INTO t(name) VALUES ('b');",
                "transactional", true
        ));
        ToolResult exec = manager.execute(request("sqlite_exec", execArgs));
        JsonNode execPayload = MAPPER.readTree(exec.content());
        assertThat(execPayload.path("status").asText())
                .withFailMessage(exec.content())
                .isEqualTo("ok");

        String queryArgs = MAPPER.writeValueAsString(Map.of(
                "dbPath", "test.db",
                "sql", "SELECT id, name FROM t ORDER BY id"
        ));
        ToolResult query = manager.execute(request("sqlite_query", queryArgs));
        JsonNode queryPayload = MAPPER.readTree(query.content());

        assertThat(queryPayload.path("status").asText())
                .withFailMessage(query.content())
                .isEqualTo("ok");
        assertThat(queryPayload.path("data").path("rows")).hasSize(2);
        assertThat(queryPayload.path("data").path("rows").get(0).path("name").asText()).isEqualTo("a");
    }

    @Test
    void shellCommandCapturesOutput() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));

        ToolResult result = manager.execute(request("shell_command", "{\"cmd\":\"echo hello\"}"));
        JsonNode payload = MAPPER.readTree(result.content());

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("stdout").asText()).contains("hello");
    }

    @Test
    void outputIsBoundedToConfiguredMax() throws Exception {
        ToolManager manager = new ToolManager(Map.of(
                "oversized", request -> ToolResult.handled(
                        request.toolCall().id(),
                        request.toolCall().name(),
                        "x".repeat(100_000)
                )
        ));

        ToolResult result = manager.execute(request("oversized", "{}"));
        JsonNode payload = MAPPER.readTree(result.content());

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("output_too_large");
    }

    private RuntimeConfig runtimeConfig(Path workspaceRoot) {
        return new RuntimeConfig(
                workspaceRoot,
                workspaceRoot,
                "agent-default",
                "agent-compaction",
                8,
                32_768,
                200,
                500,
                Map.of(),
                Map.of(),
                Map.of(),
                RuntimeConfig.SecurityPolicyConfig.defaults()
        );
    }

    private ToolRequest request(String toolName, String argsJson) {
        return new ToolRequest(
                UUID.randomUUID().toString(),
                "agent-default",
                new ContextElement.ToolCall("call-1", toolName, argsJson)
        );
    }
}
