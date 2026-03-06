package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class RoutingEventFormatter {

    public List<String> format(RoutingEvent event) {
        List<String> lines = new ArrayList<>();
        lines.add("sessionId=" + event.sessionHandle().sessionId() + " active=" + event.sessionHandle().isActive());

        switch (event) {
            case RoutingEvent.InputResult input -> {
                lines.add("type=input");
                lines.add("inputType=" + input.inputType() + " sourceId=" + input.inputSourceId());
                lines.add("outcome=" + input.outcome() + " phase=" + input.phase());
                lines.add("route=" + input.routeHandle().map(handle -> handle.routeId().toString()).orElse("none"));
                lines.add("reason=" + input.reason());
            }
            case RoutingEvent.OutputResult output -> {
                lines.add("type=output");
                lines.add("outputType=" + output.outputType());
                lines.add("matchedRoutes=" + formatRoutes(output.matchedRoutes()));
                lines.add("deliveredRoutes=" + formatRoutes(output.deliveredRoutes()));
                lines.add("failedRoutes=" + formatRoutes(output.failedRoutes()));
            }
        }

        return lines;
    }

    private String formatRoutes(Set<RouteHandle> routes) {
        if (routes == null || routes.isEmpty()) {
            return "[]";
        }
        return routes.stream()
                .map(route -> route.routeId().toString())
                .sorted(Comparator.naturalOrder())
                .toList()
                .toString();
    }
}
