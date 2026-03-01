package io.mindspice.magenta.systems.session;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record SessionHandle(UUID sessionId, BooleanSupplier isActiveSupplier) {
    public SessionHandle {
        Objects.requireNonNull(sessionId, "sessionId");
        isActiveSupplier = isActiveSupplier == null ? () -> false : isActiveSupplier;
    }

    public boolean isActive() {
        return isActiveSupplier.getAsBoolean();
    }
}
