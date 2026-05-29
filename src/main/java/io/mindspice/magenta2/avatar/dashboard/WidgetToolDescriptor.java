package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

public record WidgetToolDescriptor(
    List<String> readTools,
    List<String> mutationTools,
    String authorization,
    boolean destructiveConfirmationRequired,
    int responseLimit
) {
    public WidgetToolDescriptor {
        readTools = readTools == null ? List.of() : List.copyOf(readTools);
        mutationTools = mutationTools == null ? List.of() : List.copyOf(mutationTools);
        authorization = authorization == null ? "READ_ONLY_DASHBOARD_CONTEXT" : authorization;
    }

    public static WidgetToolDescriptor none() {
        return new WidgetToolDescriptor(List.of(), List.of(), "READ_ONLY_DASHBOARD_CONTEXT", false, 0);
    }
}
