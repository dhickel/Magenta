package io.mindspice.magenta.runtime.session;

import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.function.BooleanSupplier;

public record SessionHandle(
        @NonNull UUID sessionId,
        @NonNull BooleanSupplier isActiveSupplier,
        @NonNull SessionSettingsView settingsView
) {

    public boolean isActive() {
        return isActiveSupplier.getAsBoolean();
    }
}
