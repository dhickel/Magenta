package io.mindspice.magenta.runtime.tools;

import io.mindspice.magenta.runtime.session.SessionMessage;

public record ToolResult(String toolCallId, String toolName, String content, boolean handled) {

    public static ToolResult handled(String toolCallId, String toolName, String content) {
        return new ToolResult(toolCallId, toolName, content == null ? "" : content, true);
    }

    public static ToolResult notHandled(SessionMessage.ToolCall toolCall) {
        return new ToolResult(toolCall.id(), toolCall.name(), "Tool not handled: " + toolCall.name(), false);
    }
}
