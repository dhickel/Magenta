package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionOutput;

import java.util.Objects;

public record OutputRoutingEvent(
        SessionHandle sessionHandle,
        SessionOutput output
) {
    public OutputRoutingEvent {
        Objects.requireNonNull(sessionHandle, "sessionHandle");
        Objects.requireNonNull(output, "output");
    }
}
