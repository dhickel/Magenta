package io.mindspice.magenta.runtime.routing;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record RouteHandle(
        @NonNull UUID routeId,
        @NonNull BooleanSupplier isActiveSupplier
) {
    public boolean isActive() {
        return isActiveSupplier.getAsBoolean();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof RouteHandle that)) { return false; }
        return Objects.equals(routeId, that.routeId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(routeId);
    }
}
