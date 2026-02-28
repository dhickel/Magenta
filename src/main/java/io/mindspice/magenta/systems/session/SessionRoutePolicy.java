package io.mindspice.magenta.systems.session;

import java.util.Set;

public record SessionRoutePolicy(
        Set<SessionInput.MessageInputKind> allowedMessageKinds,
        Set<SessionInput.EventInputKind> allowedEventKinds,
        Set<String> allowedSourceIds
) {
    public SessionRoutePolicy {
        allowedMessageKinds = allowedMessageKinds == null ? Set.of() : Set.copyOf(allowedMessageKinds);
        allowedEventKinds = allowedEventKinds == null ? Set.of() : Set.copyOf(allowedEventKinds);
        allowedSourceIds = allowedSourceIds == null ? Set.of() : Set.copyOf(allowedSourceIds);
    }

    public static SessionRoutePolicy defaults() {
        return new SessionRoutePolicy(Set.of(), Set.of(), Set.of());
    }

    public boolean allows(SessionInput input) {
        if (input == null) {
            return false;
        }

        if (!allowedSourceIds.isEmpty() && !allowedSourceIds.contains(input.sourceId())) {
            return false;
        }

        return switch (input) {
            case SessionInput.MessageInput messageInput -> allowedMessageKinds.isEmpty() || allowedMessageKinds.contains(messageInput.kind());
            case SessionInput.EventInput eventInput -> allowedEventKinds.isEmpty() || allowedEventKinds.contains(eventInput.kind());
        };
    }
}
