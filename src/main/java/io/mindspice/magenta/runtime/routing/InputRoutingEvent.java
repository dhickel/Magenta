package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionInput;

import java.util.Objects;
import java.util.UUID;

public record InputRoutingEvent(
        UUID sessionId,
        SessionInput input,
        OutCome outcome,
        String reason
) {
    public InputRoutingEvent {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(outcome, "outcome");
        reason = reason == null ? "" : reason;
    }

    public enum Level {
        ALL,
        FAILURE,
        ERROR
    }

    public enum OutCome {
        APPROVED,
        DENIED_POLICY,
        SESSION_INACTIVE
    }
}
