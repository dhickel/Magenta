package io.mindspice.magenta.runtime.session;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record SessionHandle(
        UUID sessionId,
        BooleanSupplier isActiveSupplier,
        SessionConfigView configView
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
