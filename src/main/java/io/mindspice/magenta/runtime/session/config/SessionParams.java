package io.mindspice.magenta.runtime.session.config;

public record SessionParams(
        boolean blockingOnly,
        boolean toolsEnabled,
        boolean streamingEnabled
) {
    public static SessionParams ofBlocking(boolean toolsEnabled) {
        return new SessionParams(true, toolsEnabled, false);
    }

    public static SessionParams ofStreaming(boolean toolsEnabled) {
        return new SessionParams(false, toolsEnabled, true);
    }
}
