package io.mindspice.magenta.systems.session;

public record ToolRequest(String sessionId, String agentId, SessionMessage.ToolCall toolCall) {
}
