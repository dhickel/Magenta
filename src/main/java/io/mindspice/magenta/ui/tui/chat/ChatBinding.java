package io.mindspice.magenta.ui.tui.chat;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record ChatBinding(
        SessionHandle handle,
        Consumer<SessionInput> messageIn,
        Supplier<Magenta.SessionContextUsage> contextUsage
) {
    public ChatBinding {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(messageIn, "messageIn");
        Objects.requireNonNull(contextUsage, "contextUsage");
    }
}
