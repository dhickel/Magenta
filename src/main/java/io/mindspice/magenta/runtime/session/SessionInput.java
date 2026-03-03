package io.mindspice.magenta.runtime.session;

import java.util.Map;

public sealed interface SessionInput permits SessionInput.MessageInput, SessionInput.EventInput {
    String text();
    String sourceId();
    String correlationId();
    Map<String, String> metadata();
    boolean persist();

    static SessionInput.UserMessageInput userMessage(String text) {
        return new SessionInput.UserMessageInput(text, "user", "", Map.of(), true);
    }

    sealed interface MessageInput extends SessionInput permits SessionInput.UserMessageInput, SessionInput.BusMessageInput {
        MessageInputKind kind();
    }

    sealed interface EventInput extends SessionInput permits SessionInput.SystemEventInput, SessionInput.TimerWakeEventInput {
        EventInputKind kind();
    }

    enum MessageInputKind {
        USER_MESSAGE,
        BUS_MESSAGE
    }

    enum EventInputKind {
        SYSTEM_EVENT,
        TIMER_WAKE
    }

    record UserMessageInput(
            String text,
            String sourceId,
            String correlationId,
            Map<String, String> metadata,
            boolean persist
    ) implements MessageInput {
        public UserMessageInput {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "user" : sourceId;
            correlationId = correlationId == null ? "" : correlationId;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        @Override
        public MessageInputKind kind() {
            return MessageInputKind.USER_MESSAGE;
        }
    }

    record BusMessageInput(
            String text,
            String sourceId,
            String correlationId,
            Map<String, String> metadata,
            boolean persist
    ) implements MessageInput {
        public BusMessageInput {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "" : sourceId;
            correlationId = correlationId == null ? "" : correlationId;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        @Override
        public MessageInputKind kind() {
            return MessageInputKind.BUS_MESSAGE;
        }
    }

    record SystemEventInput(
            String text,
            String sourceId,
            String correlationId,
            Map<String, String> metadata,
            boolean persist
    ) implements EventInput {
        public SystemEventInput {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "system" : sourceId;
            correlationId = correlationId == null ? "" : correlationId;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        @Override
        public EventInputKind kind() {
            return EventInputKind.SYSTEM_EVENT;
        }
    }

    record TimerWakeEventInput(
            String text,
            String sourceId,
            String correlationId,
            Map<String, String> metadata,
            boolean persist
    ) implements EventInput {
        public TimerWakeEventInput {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "scheduler" : sourceId;
            correlationId = correlationId == null ? "" : correlationId;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        @Override
        public EventInputKind kind() {
            return EventInputKind.TIMER_WAKE;
        }
    }
}
