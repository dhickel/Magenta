package io.mindspice.magenta.runtime.session;

public sealed interface SessionInput permits SessionInput.MessageInput, SessionInput.EventInput {
    String text();
    String sourceId();
    boolean persist();

    static UserMsg userMessage(String text) {
        return new UserMsg(text, "user", true);
    }

    sealed interface MessageInput extends SessionInput permits UserMsg, AgentMsg {
        MessageInputKind kind();
    }

    sealed interface EventInput extends SessionInput permits SysEvent, WakeEvent {
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

    record UserMsg(
            String text,
            String sourceId,
            boolean persist
    ) implements MessageInput {
        public UserMsg {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "user" : sourceId;
        }

        @Override
        public MessageInputKind kind() {
            return MessageInputKind.USER_MESSAGE;
        }
    }

    record AgentMsg(
            String text,
            String sourceId,
            boolean persist
    ) implements MessageInput {
        public AgentMsg {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "" : sourceId;
        }

        @Override
        public MessageInputKind kind() {
            return MessageInputKind.BUS_MESSAGE;
        }
    }

    record SysEvent(
            String text,
            String sourceId,
            boolean persist
    ) implements EventInput {
        public SysEvent {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "system" : sourceId;
        }

        @Override
        public EventInputKind kind() {
            return EventInputKind.SYSTEM_EVENT;
        }
    }

    record WakeEvent(
            String text,
            String sourceId,
            boolean persist
    ) implements EventInput {
        public WakeEvent {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "scheduler" : sourceId;
        }

        @Override
        public EventInputKind kind() {
            return EventInputKind.TIMER_WAKE;
        }
    }
}
