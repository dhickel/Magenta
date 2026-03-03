package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SessionRouter {

    private static final Consumer<InputRouteReport> NOOP_INPUT_REPORTER = report -> {};
    private static final Consumer<SessionOutputEvent> NOOP_OUTPUT_LISTENER = event -> {};
    private static final Consumer<String> NOOP_DIAGNOSTICS = message -> {};

    private final Function<UUID, SessionHandle> handleResolver;
    private final BiConsumer<UUID, SessionInput> inputSubmitter;
    private final Consumer<String> diagnosticsSink;
    private final ConcurrentMap<UUID, InputRoute> inputRoutesBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, OutputRoute>> outputRoutesBySession = new ConcurrentHashMap<>();

    public SessionRouter(Function<UUID, SessionHandle> handleResolver, BiConsumer<UUID, SessionInput> inputSubmitter) {
        this(handleResolver, inputSubmitter, NOOP_DIAGNOSTICS);
    }

    public SessionRouter(
            Function<UUID, SessionHandle> handleResolver,
            BiConsumer<UUID, SessionInput> inputSubmitter,
            Consumer<String> diagnosticsSink
    ) {
        this.handleResolver = Objects.requireNonNull(handleResolver, "handleResolver");
        this.inputSubmitter = Objects.requireNonNull(inputSubmitter, "inputSubmitter");
        this.diagnosticsSink = Objects.requireNonNullElse(diagnosticsSink, NOOP_DIAGNOSTICS);
    }

    public void registerInputRoute(
            SessionHandle handle,
            InputRoutePolicy policy,
            InputRouteReportLevel reportLevel,
            Consumer<InputRouteReport> reportCallback
    ) {
        SessionHandle activeHandle = requireActiveHandle(handle);
        inputRoutesBySession.put(
                activeHandle.sessionId(),
                new InputRoute(
                        activeHandle,
                        Objects.requireNonNullElse(policy, InputRoutePolicy.defaults()),
                        Objects.requireNonNullElse(reportLevel, InputRouteReportLevel.ERROR),
                        Objects.requireNonNullElse(reportCallback, NOOP_INPUT_REPORTER)
                )
        );
    }

    public void updateInputRoute(
            SessionHandle handle,
            InputRoutePolicy policy,
            InputRouteReportLevel reportLevel,
            Consumer<InputRouteReport> reportCallback
    ) {
        registerInputRoute(handle, policy, reportLevel, reportCallback);
    }

    public void unregisterInputRoute(SessionHandle handle) {
        SessionHandle validated = requireKnownHandle(handle);
        inputRoutesBySession.remove(validated.sessionId());
    }

    public Consumer<SessionInput.MessageInput> getMessageInputConsumer(SessionHandle handle) {
        SessionHandle validated = requireKnownHandle(handle);
        return input -> routeInput(validated.sessionId(), input);
    }

    public Consumer<SessionInput.EventInput> getEventInputConsumer(SessionHandle handle) {
        SessionHandle validated = requireKnownHandle(handle);
        return input -> routeInput(validated.sessionId(), input);
    }

    public UUID registerOutputRoute(
            SessionHandle handle,
            OutputRoutePolicy outputPolicy,
            Consumer<SessionOutputEvent> outputListener
    ) {
        SessionHandle activeHandle = requireActiveHandle(handle);
        OutputRoutePolicy effectivePolicy = Objects.requireNonNullElse(outputPolicy, OutputRoutePolicy.defaults());
        if (!activeHandle.configView().streamingEnabled() && effectivePolicy.requestsPartialTokens()) {
            throw new IllegalArgumentException("Partial output routes require streamingEnabled=true for session " + activeHandle.sessionId());
        }

        UUID routeId = UUID.randomUUID();
        outputRoutesBySession
                .computeIfAbsent(activeHandle.sessionId(), ignored -> new ConcurrentHashMap<>())
                .put(routeId, new OutputRoute(effectivePolicy, Objects.requireNonNullElse(outputListener, NOOP_OUTPUT_LISTENER)));
        return routeId;
    }

    public void unregisterOutputRoute(SessionHandle handle, UUID routeId) {
        SessionHandle validated = requireKnownHandle(handle);
        if (routeId == null) {
            return;
        }
        Map<UUID, OutputRoute> routes = outputRoutesBySession.get(validated.sessionId());
        if (routes != null) {
            routes.remove(routeId);
            if (routes.isEmpty()) {
                outputRoutesBySession.remove(validated.sessionId(), routes);
            }
        }
    }

    public boolean hasPartialTokenListeners(SessionHandle handle) {
        SessionHandle validated = requireKnownHandle(handle);
        if (!validated.configView().streamingEnabled()) {
            return false;
        }

        Map<UUID, OutputRoute> routes = outputRoutesBySession.get(validated.sessionId());
        if (routes == null || routes.isEmpty()) {
            return false;
        }

        for (OutputRoute route : routes.values()) {
            if (route.policy().requestsPartialTokens()) {
                return true;
            }
        }
        return false;
    }

    public void emit(SessionHandle handle, SessionOutputEvent event) {
        SessionHandle validated = requireKnownHandle(handle);
        if (event == null) {
            return;
        }

        Map<UUID, OutputRoute> routes = outputRoutesBySession.get(validated.sessionId());
        if (routes == null || routes.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, OutputRoute> entry : routes.entrySet()) {
            OutputRoute route = entry.getValue();
            if (!route.policy().allows(event)) {
                continue;
            }
            try {
                route.listener().accept(event);
            } catch (Throwable throwable) {
                diagnosticsSink.accept("output_route_listener_failure sessionId="
                        + validated.sessionId() + " routeId=" + entry.getKey() + " error=" + throwable.getClass().getSimpleName());
            }
        }
    }

    public void pruneSession(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        inputRoutesBySession.remove(sessionId);
        outputRoutesBySession.remove(sessionId);
    }

    private void routeInput(UUID sessionId, SessionInput input) {
        if (input == null) {
            return;
        }

        InputRoute route = inputRoutesBySession.get(sessionId);
        if (route == null) {
            throw new IllegalStateException("Input route not registered for session: " + sessionId);
        }

        SessionHandle handle = route.handle();
        if (!handle.isActive()) {
            emitInputReport(route, input, InputRouteOutcome.SESSION_INACTIVE, "Session is inactive");
            return;
        }
        if (!route.policy().allows(input)) {
            emitInputReport(route, input, InputRouteOutcome.DENIED_POLICY, "Input denied by route policy");
            return;
        }

        emitInputReport(route, input, InputRouteOutcome.APPROVED, "Input approved by route policy");
        inputSubmitter.accept(sessionId, input);
    }

    private void emitInputReport(InputRoute route, SessionInput input, InputRouteOutcome outcome, String reason) {
        if (!shouldEmit(route.reportLevel(), outcome)) {
            return;
        }

        try {
            route.reportCallback().accept(new InputRouteReport(route.handle().sessionId(), input, outcome, reason));
        } catch (Throwable ignored) {
            // Route reporting is observability-only.
        }
    }

    private boolean shouldEmit(InputRouteReportLevel level, InputRouteOutcome outcome) {
        return switch (level) {
            case ALL -> true;
            case FAILURE -> outcome == InputRouteOutcome.DENIED_POLICY || outcome == InputRouteOutcome.SESSION_INACTIVE;
            case ERROR -> outcome == InputRouteOutcome.SESSION_INACTIVE;
        };
    }

    private SessionHandle requireActiveHandle(SessionHandle handle) {
        SessionHandle resolved = requireKnownHandle(handle);
        if (!resolved.isActive()) {
            throw new IllegalStateException("Session handle is inactive: " + resolved.sessionId());
        }
        return resolved;
    }

    private SessionHandle requireKnownHandle(SessionHandle handle) {
        if (handle == null || handle.sessionId() == null) {
            throw new IllegalArgumentException("Session handle is required");
        }

        SessionHandle resolved;
        try {
            resolved = handleResolver.apply(handle.sessionId());
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Unknown session handle: " + handle.sessionId());
        }
        if (resolved == null) {
            throw new IllegalStateException("Unknown session handle: " + handle.sessionId());
        }
        return resolved;
    }

    private record InputRoute(
            SessionHandle handle,
            InputRoutePolicy policy,
            InputRouteReportLevel reportLevel,
            Consumer<InputRouteReport> reportCallback
    ) {}

    private record OutputRoute(OutputRoutePolicy policy, Consumer<SessionOutputEvent> listener) {}
}
