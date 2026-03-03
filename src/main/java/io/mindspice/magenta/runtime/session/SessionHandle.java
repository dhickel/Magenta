package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.session.config.SessionParams;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record SessionHandle(
        UUID sessionId,
        BooleanSupplier isActiveSupplier,
        SessionParams configView
) {
    public SessionHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(configView, "configView");
        isActiveSupplier = isActiveSupplier == null ? () -> false : isActiveSupplier;
    }

    public boolean isActive() {
        return isActiveSupplier.getAsBoolean();
    }
}
