package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.Set;

public sealed interface RoutingEvent permits RoutingEvent.InputResult, RoutingEvent.OutputResult {
    @NonNull SessionHandle sessionHandle();

    record InputResult(
            @NonNull SessionHandle sessionHandle,
            @NonNull Optional<RouteHandle> routeHandle,
            InputRoutingEvent.OutCome outcome,
            InputRoutingEvent.Phase phase,
            @NonNull String reason,
            @NonNull String inputType,
            @NonNull String inputSourceId
    ) implements RoutingEvent {
        public InputResult {
            routeHandle = routeHandle == null ? Optional.empty() : routeHandle;
            reason = reason == null ? "" : reason;
            inputType = inputType == null ? "" : inputType;
            inputSourceId = inputSourceId == null ? "" : inputSourceId;
        }
    }

    record OutputResult(
            @NonNull SessionHandle sessionHandle,
            @NonNull String outputType,
            @NonNull Set<RouteHandle> matchedRoutes,
            @NonNull Set<RouteHandle> deliveredRoutes,
            @NonNull Set<RouteHandle> failedRoutes
    ) implements RoutingEvent {
        public OutputResult {
            outputType = outputType == null ? "" : outputType;
            matchedRoutes = matchedRoutes == null ? Set.of() : Set.copyOf(matchedRoutes);
            deliveredRoutes = deliveredRoutes == null ? Set.of() : Set.copyOf(deliveredRoutes);
            failedRoutes = failedRoutes == null ? Set.of() : Set.copyOf(failedRoutes);
        }
    }
}
