package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.SessionQueueFullException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Object routesLock = new Object();
    private final Map<SessionHandle, LinkedHashSet<RouteBinding>> routesBySession = new HashMap<>();
    private final ExecutorService outputDispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<UUID, SerialDispatchQueue> outputDispatchBySession = new ConcurrentHashMap<>();

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
        synchronized (routesLock) {
            LinkedHashSet<RouteBinding> routes = routesBySession.computeIfAbsent(handle, ignored -> new LinkedHashSet<>());
            UUID routeId = UUID.randomUUID();
            RouteHandle routeHandle = new RouteHandle(routeId, () -> isRouteActive(routeId, handle));
            routes.add(new InputRouteBinding(new Route.InputRoute(routeHandle, handle, policy)));
            return routeHandle;
        }
    }

    public RouteHandle addOutputRoute(SessionHandle handle, OutputRoutePolicy policy, Consumer<OutputRoutingEvent> outputListener) {
        if (!handle.isActive()) {
            throw new IllegalStateException("Session handle is inactive: " + handle.sessionId());
        }
        synchronized (routesLock) {
            LinkedHashSet<RouteBinding> routes = routesBySession.computeIfAbsent(handle, ignored -> new LinkedHashSet<>());
            UUID routeId = UUID.randomUUID();
            RouteHandle routeHandle = new RouteHandle(routeId, () -> isRouteActive(routeId, handle));
            routes.add(new OutputRouteBinding(new Route.OutputRoute(routeHandle, handle, policy), outputListener));
            return routeHandle;
        }
    }

    public void removeRoute(RouteHandle routeHandle) {
        synchronized (routesLock) {
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
    }

    public Route route(RouteHandle routeHandle) {
        synchronized (routesLock) {
            for (LinkedHashSet<RouteBinding> routes : routesBySession.values()) {
                for (RouteBinding binding : routes) {
                    if (binding.route().handle().equals(routeHandle)) {
                        return binding.route();
                    }
                }
            }
        }
        throw new IllegalStateException("Route not registered: " + routeHandle.routeId());
    }

    public Set<Route> routes(SessionHandle handle) {
        synchronized (routesLock) {
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
    }

    public Consumer<SessionInput.MessageInput> messageInputConsumer(SessionHandle handle) {
        return input -> routeInput(handle, input);
    }

    public Consumer<SessionInput.EventInput> eventInputConsumer(SessionHandle handle) {
        return input -> routeInput(handle, input);
    }

    public boolean hasStreamedOutputListeners(SessionHandle handle) {
        synchronized (routesLock) {
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
    }

    public void emit(SessionHandle handle, OutputRoutingEvent event) {
        List<OutputRouteBinding> outputRoutes = snapshotOutputRoutes(handle);
        if (outputRoutes.isEmpty()) {
            return;
        }
        dispatchQueueFor(handle).execute(() -> dispatchOutput(handle, event, outputRoutes));
    }

    public void emit(SessionHandle handle, SessionOutput output) {
        emit(handle, new OutputRoutingEvent(handle, output));
    }

    public void pruneSession(SessionHandle handle) {
        synchronized (routesLock) {
            routesBySession.remove(handle);
        }
        SerialDispatchQueue queue = outputDispatchBySession.remove(handle.sessionId());
        if (queue != null) {
            queue.close();
        }
    }

    private boolean isRouteActive(UUID routeId, SessionHandle sessionHandle) {
        if (!sessionHandle.isActive()) {
            return false;
        }
        synchronized (routesLock) {
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
    }

    private void routeInput(SessionHandle handle, SessionInput input) {
        List<InputRouteBinding> inputRoutes = snapshotInputRoutes(handle);
        if (inputRoutes.isEmpty()) {
            throw new IllegalStateException("Input route not registered for session: " + handle.sessionId());
        }

        for (InputRouteBinding inputBinding : inputRoutes) {
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
            try {
                inputSubmitter.accept(handle, input);
            } catch (SessionQueueFullException queueFullException) {
                emitRoutingEvent(new RoutingEvent.InputResult(
                        handle,
                        Optional.of(routeHandle),
                        InputRoutingEvent.OutCome.QUEUE_FULL,
                        InputRoutingEvent.Phase.FINAL,
                        queueFullException.getMessage(),
                        inputType,
                        sourceId
                ));
            }
            return;
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

    private List<InputRouteBinding> snapshotInputRoutes(SessionHandle handle) {
        synchronized (routesLock) {
            LinkedHashSet<RouteBinding> routes = routesBySession.get(handle);
            if (routes == null || routes.isEmpty()) {
                return List.of();
            }
            List<InputRouteBinding> snapshot = new ArrayList<>();
            for (RouteBinding binding : routes) {
                if (binding instanceof InputRouteBinding inputRouteBinding) {
                    snapshot.add(inputRouteBinding);
                }
            }
            return List.copyOf(snapshot);
        }
    }

    private List<OutputRouteBinding> snapshotOutputRoutes(SessionHandle handle) {
        synchronized (routesLock) {
            LinkedHashSet<RouteBinding> routes = routesBySession.get(handle);
            if (routes == null || routes.isEmpty()) {
                return List.of();
            }
            List<OutputRouteBinding> snapshot = new ArrayList<>();
            for (RouteBinding binding : routes) {
                if (binding instanceof OutputRouteBinding outputRouteBinding) {
                    snapshot.add(outputRouteBinding);
                }
            }
            return List.copyOf(snapshot);
        }
    }

    private SerialDispatchQueue dispatchQueueFor(SessionHandle handle) {
        return outputDispatchBySession.computeIfAbsent(
                handle.sessionId(),
                ignored -> new SerialDispatchQueue(outputDispatchExecutor)
        );
    }

    private void dispatchOutput(SessionHandle handle, OutputRoutingEvent event, List<OutputRouteBinding> outputRoutes) {
        Set<RouteHandle> matchedRoutes = new LinkedHashSet<>();
        Set<RouteHandle> deliveredRoutes = new LinkedHashSet<>();
        Set<RouteHandle> failedRoutes = new LinkedHashSet<>();
        for (OutputRouteBinding outputBinding : outputRoutes) {
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

    private static final class SerialDispatchQueue {
        private final ExecutorService executorService;
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private final AtomicBoolean running = new AtomicBoolean(false);
        private volatile boolean closed;

        private SerialDispatchQueue(ExecutorService executorService) {
            this.executorService = executorService;
        }

        private void execute(Runnable runnable) {
            Objects.requireNonNull(runnable, "runnable");
            synchronized (queue) {
                if (closed) {
                    return;
                }
                queue.addLast(runnable);
            }
            schedule();
        }

        private void close() {
            synchronized (queue) {
                closed = true;
                queue.clear();
            }
        }

        private void schedule() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            executorService.submit(this::runLoop);
        }

        private void runLoop() {
            try {
                while (true) {
                    Runnable next;
                    synchronized (queue) {
                        next = queue.pollFirst();
                    }
                    if (next == null) {
                        return;
                    }
                    next.run();
                }
            } finally {
                running.set(false);
                synchronized (queue) {
                    if (!queue.isEmpty() && !closed) {
                        schedule();
                    }
                }
            }
        }
    }
}
