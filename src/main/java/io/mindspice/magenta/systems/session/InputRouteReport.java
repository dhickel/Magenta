package io.mindspice.magenta.systems.session;

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
