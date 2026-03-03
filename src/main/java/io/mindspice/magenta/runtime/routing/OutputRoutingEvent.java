package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionOutput;

import java.util.Objects;
import java.util.UUID;

public record OutputRoutingEvent(
        UUID sessionId,
        SessionOutput output
) {
    public OutputRoutingEvent {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(output, "output");
    }
}
