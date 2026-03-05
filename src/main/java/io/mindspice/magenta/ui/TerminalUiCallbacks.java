package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionException;

import java.util.Objects;
import java.util.function.Consumer;

public record TerminalUiCallbacks(
        Consumer<RoutingEvent> onRouting,
        Consumer<SecurityManager.SecurityEvent> onSecurity,
        Consumer<SessionException> onError
) {
    public TerminalUiCallbacks {
        onRouting = onRouting == null ? ignored -> {} : onRouting;
        onSecurity = onSecurity == null ? ignored -> {} : onSecurity;
        onError = onError == null ? ignored -> {} : onError;
    }

    public static TerminalUiCallbacks defaults() {
        return new TerminalUiCallbacks(ignored -> {}, ignored -> {}, ignored -> {});
    }
}
