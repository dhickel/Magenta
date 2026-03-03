package io.mindspice.magenta.runtime.tools;

import io.mindspice.magenta.runtime.context.ContextElement;

public record ToolRequest(String sessionId, String agentId, ContextElement.ToolCall toolCall) {
}
