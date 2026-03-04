package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class SessionRouter {

    private sealed interface RouteBinding permits InputRouteBinding, OutputRouteBinding {
        Route route();
    }

    private record InputRouteBinding(Route.InputRoute route) implements RouteBinding {}

    private record OutputRouteBinding(Route.OutputRoute route, Consumer<OutputRoutingEvent> listener) implements RouteBinding {}

    private final BiConsumer<SessionHandle, SessionInput> inputSubmitter;
    private final Consumer<RoutingEvent> routingObserver;
    private final Consumer<String> diagnosticsSink;
    private final Map<SessionHandle, LinkedHashSet<RouteBinding>> routesBySession = new HashMap<>();

    public SessionRouter(
            BiConsumer<SessionHandle, SessionInput> inputSubmitter,
            Consumer<RoutingEvent> routingObserver,
            Consumer<String> diagnosticsSink
    ) {
        this.inputSubmitter = inputSubmitter;
        this.routingObserver = routingObserver;
        this.diagnosticsSink = diagnosticsSink;
    }

    public RouteHandle addInputRoute(SessionHandle handle, InputRoutePolicy policy) {
        if (!handle.isActive()) {
            throw new IllegalStateException("Session handle is inactive: " + handle.sessionId());
        }
        LinkedHashSet<RouteBinding> routes = routesBySession.computeIfAbsent(handle, ignored -> new LinkedHashSet<>());
        UUID routeId = UUID.randomUUID();
        RouteHandle routeHandle = new RouteHandle(routeId, () -> isRouteActive(routeId, handle));
        routes.add(new InputRouteBinding(new Route.InputRoute(routeHandle, handle, policy)));
        return routeHandle;
    }

    public RouteHandle addOutputRoute(SessionHandle handle, OutputRoutePolicy policy, Consumer<OutputRoutingEvent> outputListener) {
        if (!handle.isActive()) {
            throw new IllegalStateException("Session handle is inactive: " + handle.sessionId());
        }
        LinkedHashSet<RouteBinding> routes = routesBySession.computeIfAbsent(handle, ignored -> new LinkedHashSet<>());
        UUID routeId = UUID.randomUUID();
        RouteHandle routeHandle = new RouteHandle(routeId, () -> isRouteActive(routeId, handle));
        routes.add(new OutputRouteBinding(new Route.OutputRoute(routeHandle, handle, policy), outputListener));
        return routeHandle;
    }

    public void removeRoute(RouteHandle routeHandle) {
        SessionHandle emptySetOwner = null;

        for (Map.Entry<SessionHandle, LinkedHashSet<RouteBinding>> entry : routesBySession.entrySet()) {
            LinkedHashSet<RouteBinding> routes = entry.getValue();
            boolean removed = routes.removeIf(binding -> binding.route().handle().equals(routeHandle));
            if (removed && routes.isEmpty()) {
                emptySetOwner = entry.getKey();
            }
            if (removed) {
                break;
            }
        }

        if (emptySetOwner != null) {
            routesBySession.remove(emptySetOwner);
        }
    }

    public Route route(RouteHandle routeHandle) {
        for (LinkedHashSet<RouteBinding> routes : routesBySession.values()) {
            for (RouteBinding binding : routes) {
                if (binding.route().handle().equals(routeHandle)) {
                    return binding.route();
                }
            }
        }
        throw new IllegalStateException("Route not registered: " + routeHandle.routeId());
    }

    public Set<Route> routes(SessionHandle handle) {
        LinkedHashSet<RouteBinding> routes = routesBySession.get(handle);
        if (routes == null || routes.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Route> snapshot = new LinkedHashSet<>();
        for (RouteBinding binding : routes) {
            snapshot.add(binding.route());
        }
        return Set.copyOf(snapshot);
    }

    public Consumer<SessionInput.MessageInput> messageInputConsumer(SessionHandle handle) {
        return input -> routeInput(handle, input);
    }

    public Consumer<SessionInput.EventInput> eventInputConsumer(SessionHandle handle) {
        return input -> routeInput(handle, input);
    }

    public boolean hasStreamedOutputListeners(SessionHandle handle) {
        LinkedHashSet<RouteBinding> routes = routesBySession.get(handle);
        if (routes == null || routes.isEmpty()) {
            return false;
        }

        for (RouteBinding binding : routes) {
            if (binding instanceof OutputRouteBinding outputBinding
                && outputBinding.route().policy().requestsStreamedOutput()) {
                return true;
            }
        }
        return false;
    }

    public void emit(SessionHandle handle, OutputRoutingEvent event) {
        LinkedHashSet<RouteBinding> routes = routesBySession.get(handle);
        if (routes == null || routes.isEmpty()) {
            return;
        }

        Set<RouteHandle> matchedRoutes = new LinkedHashSet<>();
        Set<RouteHandle> deliveredRoutes = new LinkedHashSet<>();
        Set<RouteHandle> failedRoutes = new LinkedHashSet<>();
        for (RouteBinding binding : Set.copyOf(routes)) {
            if (!(binding instanceof OutputRouteBinding outputBinding)) {
                continue;
            }
            if (!outputBinding.route().policy().allows(event)) {
                continue;
            }
            matchedRoutes.add(outputBinding.route().handle());

            try {
                outputBinding.listener().accept(event);
                deliveredRoutes.add(outputBinding.route().handle());
            } catch (Throwable throwable) {
                failedRoutes.add(outputBinding.route().handle());
                diagnosticsSink.accept("output_route_listener_failure sessionId="
                        + handle.sessionId() + " routeId=" + outputBinding.route().handle().routeId()
                        + " error=" + throwable.getClass().getSimpleName());
            }
        }
        emitRoutingEvent(new RoutingEvent.OutputResult(
                handle,
                event.output().getClass().getSimpleName(),
                matchedRoutes,
                deliveredRoutes,
                failedRoutes
        ));
    }

    public void emit(SessionHandle handle, SessionOutput output) {
        emit(handle, new OutputRoutingEvent(handle, output));
    }

    public void pruneSession(SessionHandle handle) {
        routesBySession.remove(handle);
    }

    private boolean isRouteActive(UUID routeId, SessionHandle sessionHandle) {
        if (!sessionHandle.isActive()) {
            return false;
        }
        LinkedHashSet<RouteBinding> routes = routesBySession.get(sessionHandle);
        if (routes == null) {
            return false;
        }
        for (RouteBinding binding : routes) {
            if (binding.route().handle().routeId().equals(routeId)) {
                return true;
            }
        }
        return false;
    }

    private void routeInput(SessionHandle handle, SessionInput input) {
        LinkedHashSet<RouteBinding> routes = routesBySession.get(handle);
        if (routes == null || routes.isEmpty()) {
            throw new IllegalStateException("Input route not registered for session: " + handle.sessionId());
        }

        boolean sawInputRoute = false;
        for (RouteBinding binding : routes) {
            if (!(binding instanceof InputRouteBinding inputBinding)) {
                continue;
            }
            sawInputRoute = true;
            RouteHandle routeHandle = inputBinding.route().handle();
            String inputType = input.getClass().getSimpleName();
            String sourceId = input.sourceId();

            if (!handle.isActive()) {
                emitRoutingEvent(new RoutingEvent.InputResult(
                        handle,
                        Optional.of(routeHandle),
                        InputRoutingEvent.OutCome.SESSION_INACTIVE,
                        InputRoutingEvent.Phase.FINAL,
                        "Session is inactive",
                        inputType,
                        sourceId
                ));
                return;
            }

            if (!inputBinding.route().policy().allows(input)) {
                emitRoutingEvent(new RoutingEvent.InputResult(
                        handle,
                        Optional.of(routeHandle),
                        InputRoutingEvent.OutCome.DENIED_POLICY,
                        InputRoutingEvent.Phase.ATTEMPT,
                        "Input denied by route policy",
                        inputType,
                        sourceId
                ));
                continue;
            }

            emitRoutingEvent(new RoutingEvent.InputResult(
                    handle,
                    Optional.of(routeHandle),
                    InputRoutingEvent.OutCome.APPROVED,
                    InputRoutingEvent.Phase.FINAL,
                    "Input approved by route policy",
                    inputType,
                    sourceId
            ));
            inputSubmitter.accept(handle, input);
            return;
        }

        if (!sawInputRoute) {
            throw new IllegalStateException("Input route not registered for session: " + handle.sessionId());
        }

        emitRoutingEvent(new RoutingEvent.InputResult(
                handle,
                Optional.empty(),
                InputRoutingEvent.OutCome.DENIED_POLICY,
                InputRoutingEvent.Phase.FINAL,
                "Input denied after all input routes were evaluated",
                input.getClass().getSimpleName(),
                input.sourceId()
        ));
    }

    private void emitRoutingEvent(RoutingEvent event) {
        try {
            routingObserver.accept(event);
        } catch (Throwable ignored) {
            // Routing callbacks are observability-only.
        }
    }
}
