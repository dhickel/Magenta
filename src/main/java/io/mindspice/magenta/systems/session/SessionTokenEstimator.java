package io.mindspice.magenta.systems.session;

import java.util.List;

public final class SessionTokenEstimator {
    private SessionTokenEstimator() {}

    public static int estimate(List<SessionMessage> messages) {
        int total = 0;
        for (SessionMessage message : messages) {
            total += estimateText(message.content());
            if (message instanceof SessionMessage.AssistantMsg assistant) {
                for (SessionMessage.ToolCall call : assistant.toolCalls()) {
                    total += estimateText(call.name() + call.argumentsJson());
                }
            }
        }
        return total;
    }

    public static int estimateText(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        return Math.max(1, text.length() / 4);
    }
}
