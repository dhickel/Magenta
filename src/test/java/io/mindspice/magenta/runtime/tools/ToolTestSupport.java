package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

final class ToolTestSupport {

    static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ToolTestSupport() {
    }

    static RuntimeConfig runtimeConfig(Path workspaceRoot) {
        return runtimeConfig(workspaceRoot, 32_768, 200, 500);
    }

    static RuntimeConfig runtimeConfig(Path workspaceRoot, int maxToolOutputBytes, int maxFileReadLines, int maxSqlRows) {
        return new RuntimeConfig(
                workspaceRoot,
                workspaceRoot,
                "agent-default",
                "agent-compaction",
                8,
                maxToolOutputBytes,
                maxFileReadLines,
                maxSqlRows,
                Map.of(),
                Map.of(),
                Map.of(),
                RuntimeConfig.SecurityPolicyConfig.defaults()
        );
    }

    static ToolRequest request(String toolName, String argsJson) {
        return request(UUID.randomUUID(), toolName, argsJson);
    }

    static ToolRequest request(UUID sessionId, String toolName, String argsJson) {
        return new ToolRequest(
                sessionId.toString(),
                "agent-default",
                new ContextElement.ToolCall("call-1", toolName, argsJson)
        );
    }

    static JsonNode payload(ToolResult result) throws Exception {
        return MAPPER.readTree(result.content());
    }
}
