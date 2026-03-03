package io.mindspice.magenta.runtime.session;

public record SessionConfigView(
        boolean blockingOnly,
        boolean toolsEnabled,
        boolean bypassSecurity,
        boolean streamingEnabled
) {}
