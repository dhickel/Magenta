package io.mindspice.magenta.ui;

final class StreamingAssistantBuffer {
    private final String prefix;
    private final StringBuilder content = new StringBuilder();
    private boolean started = false;

    StreamingAssistantBuffer(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    boolean appendToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (!started) {
            content.append(prefix);
            started = true;
        }
        content.append(token);
        return true;
    }

    boolean started() {
        return started;
    }

    String content() {
        return content.toString();
    }

    void reset() {
        content.setLength(0);
        started = false;
    }
}
