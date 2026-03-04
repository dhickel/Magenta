package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;
import org.jspecify.annotations.NonNull;

public sealed interface Route permits Route.InputRoute, Route.OutputRoute {
    @NonNull RouteHandle handle();
    @NonNull SessionHandle sessionHandle();

    record InputRoute(
            @NonNull RouteHandle handle,
            @NonNull SessionHandle sessionHandle,
            @NonNull InputRoutePolicy policy
    ) implements Route {}

    record OutputRoute(
            @NonNull RouteHandle handle,
            @NonNull SessionHandle sessionHandle,
            @NonNull OutputRoutePolicy policy
    ) implements Route {}
}
