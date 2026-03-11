package io.mindspice.magenta.runtime.events;

import java.util.Objects;
import java.util.UUID;

public record SessionEventListenerHandle(
        UUID sessionId,
        UUID listenerId
) {
    public SessionEventListenerHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(listenerId, "listenerId");
    }
}
