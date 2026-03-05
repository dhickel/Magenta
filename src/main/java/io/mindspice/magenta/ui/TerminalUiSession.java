package io.mindspice.magenta.ui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record TerminalUiSession(
        SessionHandle handle,
        RouteHandle outputRoute,
        Consumer<SessionInput.MessageInput> messageIn,
        Consumer<SessionInput.EventInput> eventIn,
        Supplier<Magenta.SessionContextUsage> contextUsageSupplier
) {
    public TerminalUiSession {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(outputRoute, "outputRoute");
        Objects.requireNonNull(messageIn, "messageIn");
        Objects.requireNonNull(eventIn, "eventIn");
        Objects.requireNonNull(contextUsageSupplier, "contextUsageSupplier");
    }
}
