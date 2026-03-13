package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;

import java.util.Optional;
import java.util.Objects;

public record InputRoutingEvent(
        SessionHandle sessionHandle,
        Optional<RouteHandle> routeHandle,
        OutCome outcome,
        Phase phase,
        String reason,
        String inputType,
        String inputSourceId
) {
    public InputRoutingEvent {
        Objects.requireNonNull(sessionHandle, "sessionHandle");
        routeHandle = routeHandle == null ? Optional.empty() : routeHandle;
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(phase, "phase");
        reason = reason == null ? "" : reason;
        inputType = inputType == null ? "" : inputType;
        inputSourceId = inputSourceId == null ? "" : inputSourceId;
    }

    public enum OutCome {
        APPROVED,
        DENIED_POLICY,
        SESSION_INACTIVE,
        QUEUE_FULL
    }

    public enum Phase {
        ATTEMPT,
        FINAL
    }
}
