package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.runtime.context.ContextElement;

import java.util.Map;

public record ToolResult(String toolCallId, String toolName, String content, boolean handled) {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static ToolResult handled(String toolCallId, String toolName, String content) {
        return new ToolResult(toolCallId, toolName, content == null ? "" : content, true);
    }

    public static ToolResult notHandled(ContextElement.ToolCall toolCall) {
        String toolCallId = toolCall == null || toolCall.id() == null ? "" : toolCall.id();
        String toolName = toolCall == null || toolCall.name() == null ? "" : toolCall.name();
        String payload = json(Map.of(
                "status", "failed",
                "code", "not_handled",
                "message", "Tool not handled: " + toolName
        ));
        return new ToolResult(toolCallId, toolName, payload, false);
    }

    private static String json(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"status\":\"failed\",\"code\":\"serialization_error\",\"message\":\"Failed to serialize tool payload\"}";
        }
    }
}
