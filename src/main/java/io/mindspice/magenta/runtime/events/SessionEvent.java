package io.mindspice.magenta.runtime.events;

import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionException;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

public sealed interface SessionEvent permits SessionEvent.MessageIn,
        SessionEvent.MessageOut,
        SessionEvent.Action,
        SessionEvent.RoutingDecision,
        SessionEvent.SecurityDecision,
        SessionEvent.ErrorEvent {

    @NonNull SessionHandle sessionHandle();

    @NonNull String agentId();

    record MessageIn(
            @NonNull SessionHandle sessionHandle,
            @NonNull String agentId,
            @NonNull SessionInput input
    ) implements SessionEvent {
        public MessageIn {
            Objects.requireNonNull(sessionHandle, "sessionHandle");
            agentId = agentId == null ? "" : agentId;
            Objects.requireNonNull(input, "input");
        }
    }

    record MessageOut(
            @NonNull SessionHandle sessionHandle,
            @NonNull String agentId,
            @NonNull SessionOutput output
    ) implements SessionEvent {
        public MessageOut {
            Objects.requireNonNull(sessionHandle, "sessionHandle");
            agentId = agentId == null ? "" : agentId;
            Objects.requireNonNull(output, "output");
        }
    }

    sealed interface Action extends SessionEvent permits Action.ToolCall,
            Action.ToolResult,
            Action.SessionStarted,
            Action.SessionClosed,
            Action.InputRouteAdded,
            Action.OutputRouteAdded,
            Action.RouteRemoved {

        @NonNull String actionType();

        record ToolCall(
                @NonNull SessionHandle sessionHandle,
                @NonNull String agentId,
                @NonNull String toolName,
                @NonNull String toolCallId,
                @NonNull String argumentsJson
        ) implements Action {
            public ToolCall {
                Objects.requireNonNull(sessionHandle, "sessionHandle");
                agentId = agentId == null ? "" : agentId;
                toolName = toolName == null ? "" : toolName;
                toolCallId = toolCallId == null ? "" : toolCallId;
                argumentsJson = argumentsJson == null ? "" : argumentsJson;
            }

            @Override
            public String actionType() {
                return "tool_call";
            }
        }

        record ToolResult(
                @NonNull SessionHandle sessionHandle,
                @NonNull String agentId,
                @NonNull String toolName,
                @NonNull String toolCallId,
                @NonNull String content
        ) implements Action {
            public ToolResult {
                Objects.requireNonNull(sessionHandle, "sessionHandle");
                agentId = agentId == null ? "" : agentId;
                toolName = toolName == null ? "" : toolName;
                toolCallId = toolCallId == null ? "" : toolCallId;
                content = content == null ? "" : content;
            }

            @Override
            public String actionType() {
                return "tool_result";
            }
        }

        record SessionStarted(
                @NonNull SessionHandle sessionHandle,
                @NonNull String agentId,
                @NonNull String alias
        ) implements Action {
            public SessionStarted {
                Objects.requireNonNull(sessionHandle, "sessionHandle");
                agentId = agentId == null ? "" : agentId;
                alias = alias == null ? "" : alias;
            }

            @Override
            public String actionType() {
                return "session_started";
            }
        }

        record SessionClosed(
                @NonNull SessionHandle sessionHandle,
                @NonNull String agentId
        ) implements Action {
            public SessionClosed {
                Objects.requireNonNull(sessionHandle, "sessionHandle");
                agentId = agentId == null ? "" : agentId;
            }

            @Override
            public String actionType() {
                return "session_closed";
            }
        }

        record InputRouteAdded(
                @NonNull SessionHandle sessionHandle,
                @NonNull String agentId,
                @NonNull UUIDLike routeId
        ) implements Action {
            public InputRouteAdded {
                Objects.requireNonNull(sessionHandle, "sessionHandle");
                agentId = agentId == null ? "" : agentId;
                Objects.requireNonNull(routeId, "routeId");
            }

            @Override
            public String actionType() {
                return "input_route_added";
            }
        }

        record OutputRouteAdded(
                @NonNull SessionHandle sessionHandle,
                @NonNull String agentId,
                @NonNull UUIDLike routeId
        ) implements Action {
            public OutputRouteAdded {
                Objects.requireNonNull(sessionHandle, "sessionHandle");
                agentId = agentId == null ? "" : agentId;
                Objects.requireNonNull(routeId, "routeId");
            }

            @Override
            public String actionType() {
                return "output_route_added";
            }
        }

        record RouteRemoved(
                @NonNull SessionHandle sessionHandle,
                @NonNull String agentId,
                @NonNull UUIDLike routeId
        ) implements Action {
            public RouteRemoved {
                Objects.requireNonNull(sessionHandle, "sessionHandle");
                agentId = agentId == null ? "" : agentId;
                Objects.requireNonNull(routeId, "routeId");
            }

            @Override
            public String actionType() {
                return "route_removed";
            }
        }
    }

    record RoutingDecision(
            @NonNull SessionHandle sessionHandle,
            @NonNull String agentId,
            @NonNull RoutingEvent routingEvent
    ) implements SessionEvent {
        public RoutingDecision {
            Objects.requireNonNull(sessionHandle, "sessionHandle");
            agentId = agentId == null ? "" : agentId;
            Objects.requireNonNull(routingEvent, "routingEvent");
        }
    }

    record SecurityDecision(
            @NonNull SessionHandle sessionHandle,
            @NonNull String agentId,
            SecurityManager.SecurityEvent securityEvent
    ) implements SessionEvent {
        public SecurityDecision {
            Objects.requireNonNull(sessionHandle, "sessionHandle");
            agentId = agentId == null ? "" : agentId;
            Objects.requireNonNull(securityEvent, "securityEvent");
        }
    }

    record ErrorEvent(
            @NonNull SessionHandle sessionHandle,
            @NonNull String agentId,
            @NonNull SessionException error
    ) implements SessionEvent {
        public ErrorEvent {
            Objects.requireNonNull(sessionHandle, "sessionHandle");
            agentId = agentId == null ? "" : agentId;
            Objects.requireNonNull(error, "error");
        }
    }

    record UUIDLike(String value) {
        public UUIDLike {
            value = value == null ? "" : value;
        }

        public static UUIDLike from(RouteHandle routeHandle) {
            Objects.requireNonNull(routeHandle, "routeHandle");
            return new UUIDLike(routeHandle.routeId().toString());
        }

        public static UUIDLike from(Optional<RouteHandle> routeHandle) {
            return new UUIDLike(routeHandle.map(handle -> handle.routeId().toString()).orElse(""));
        }
    }
}
