package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionInput;

import java.util.Objects;
import java.util.UUID;

public record InputRouteReport(
        UUID sessionId,
        SessionInput input,
        InputRouteOutcome outcome,
        String reason
) {
    public InputRouteReport {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(outcome, "outcome");
        reason = reason == null ? "" : reason;
    }
}
