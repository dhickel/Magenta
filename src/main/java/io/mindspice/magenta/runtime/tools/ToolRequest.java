package io.mindspice.magenta.runtime.tools;

import io.mindspice.magenta.runtime.session.SessionMessage;

public record ToolRequest(String sessionId, String agentId, SessionMessage.ToolCall toolCall) {
}
