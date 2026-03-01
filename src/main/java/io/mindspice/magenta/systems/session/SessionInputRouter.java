package io.mindspice.magenta.systems.session;

import java.util.Objects;
import java.util.function.Consumer;

public final class SessionInputRouter implements Consumer<SessionInput> {

    private final SessionHandle sessionHandle;
    private final SessionRoutePolicy policy;
    private final Consumer<SessionInput> sessionConsumer;
    private final Consumer<InputRouteReport> reportCallback;
    private final InputRouteReportLevel reportLevel;

    public SessionInputRouter(
            SessionHandle sessionHandle,
            SessionRoutePolicy policy,
            Consumer<SessionInput> sessionConsumer,
            Consumer<InputRouteReport> reportCallback,
            InputRouteReportLevel reportLevel
    ) {
        this.sessionHandle = Objects.requireNonNull(sessionHandle, "sessionHandle");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sessionConsumer = Objects.requireNonNull(sessionConsumer, "sessionConsumer");
        this.reportCallback = Objects.requireNonNull(reportCallback, "reportCallback");
        this.reportLevel = Objects.requireNonNull(reportLevel, "reportLevel");
    }

    @Override
    public void accept(SessionInput input) {
        route(input);
    }

    public boolean route(SessionInput input) {
        if (input == null) {
            return false;
        }

        if (!sessionHandle.isActive()) {
            emit(InputRouteOutcome.SESSION_INACTIVE, input, "Session is inactive");
            return false;
        }

        if (!policy.allows(input)) {
            emit(InputRouteOutcome.DENIED_POLICY, input, "Input denied by route policy");
            return false;
        }

        emit(InputRouteOutcome.APPROVED, input, "Input approved by route policy");
        try {
            sessionConsumer.accept(input);
            return true;
        } catch (Throwable throwable) {
            if (throwable instanceof IllegalStateException e && isSessionNotFound(e)) {
                emit(InputRouteOutcome.SESSION_INACTIVE, input, "Session became inactive before submit");
            }
            return false;
        }
    }

    public SessionHandle sessionHandle() {
        return sessionHandle;
    }

    public SessionRoutePolicy policy() {
        return policy;
    }

    private void emit(InputRouteOutcome outcome, SessionInput input, String reason) {
        if (!shouldEmit(outcome)) {
            return;
        }

        try {
            reportCallback.accept(new InputRouteReport(sessionHandle.sessionId(), input, outcome, reason));
        } catch (Throwable ignored) {
            // Route callback is observability-only and must not break input ingress.
        }
    }

    private boolean shouldEmit(InputRouteOutcome outcome) {
        return switch (reportLevel) {
            case ALL -> true;
            case FAILURE -> outcome == InputRouteOutcome.DENIED_POLICY || outcome == InputRouteOutcome.SESSION_INACTIVE;
            case ERROR -> outcome == InputRouteOutcome.SESSION_INACTIVE;
        };
    }

    private boolean isSessionNotFound(IllegalStateException e) {
        String message = e.getMessage();
        return message != null && message.contains("Session not found");
    }
}
