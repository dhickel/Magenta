package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ToolPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ToolPayloads() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static ToolResult success(ToolRequest request, String message, ObjectNode data) {
        return ToolResult.handled(
                request.toolCall().id(),
                request.toolCall().name(),
                payload("ok", "ok", message, data)
        );
    }

    public static ToolResult failure(
            ToolRequest request,
            String code,
            String message,
            ObjectNode data,
            boolean handled
    ) {
        return new ToolResult(
                request.toolCall().id(),
                request.toolCall().name(),
                payload("failed", code, message, data),
                handled
        );
    }

    public static String payload(String status, String code, String message, ObjectNode data) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("status", status == null ? "failed" : status);
        root.put("code", code == null ? "unknown" : code);
        root.put("message", message == null ? "" : message);
        if (data != null) {
            root.set("data", data);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return "{\"status\":\"failed\",\"code\":\"serialization_error\",\"message\":\"Failed to serialize tool payload\"}";
        }
    }

    public static String normalizePayload(String content) {
        if (content == null || content.isBlank()) {
            return payload("ok", "ok", "", null);
        }

        try {
            JsonNode jsonNode = MAPPER.readTree(content);
            if (jsonNode.isObject() && jsonNode.has("status") && jsonNode.has("code")) {
                return content;
            }
            ObjectNode data = MAPPER.createObjectNode();
            data.set("content", jsonNode);
            return payload("ok", "raw_passthrough", "Handler returned unstructured JSON payload", data);
        } catch (Exception ignored) {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("content", content);
            return payload("ok", "raw_passthrough", "Handler returned non-JSON payload", data);
        }
    }
}
