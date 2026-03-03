package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.routing.FilterTag;

public sealed interface SessionInput permits SessionInput.MessageInput, SessionInput.EventInput {
    String text();
    String sourceId();
    boolean addToContext();

    static UserMsg userMessage(String text) {
        return new UserMsg(text, "user", true);
    }

    sealed interface MessageInput extends SessionInput permits UserMsg, AgentMsg {
         FilterTag<SessionInput> FILTER_FOR = value -> value instanceof MessageInput;

    }

    sealed interface EventInput extends SessionInput permits SysEvent, WakeEvent {
        FilterTag<SessionInput> FILTER_FOR = value -> value instanceof EventInput;

    }

    record UserMsg(
            String text,
            String sourceId,
            boolean addToContext
    ) implements MessageInput {
        public static final FilterTag<SessionInput> FILTER_FOR = value -> value instanceof UserMsg;

        public UserMsg {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "user" : sourceId;
        }
    }

    record AgentMsg(
            String text,
            String sourceId,
            boolean addToContext
    ) implements MessageInput {
        public static final FilterTag<SessionInput> FILTER_FOR = value -> value instanceof AgentMsg;

        public AgentMsg {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "" : sourceId;
        }
    }

    record SysEvent(
            String text,
            String sourceId,
            boolean addToContext
    ) implements EventInput {
        public static final FilterTag<SessionInput> FILTER_FOR = value -> value instanceof SysEvent;

        public SysEvent {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "system" : sourceId;
        }
    }

    record WakeEvent(
            String text,
            String sourceId,
            boolean addToContext
    ) implements EventInput {
        public static final FilterTag<SessionInput> FILTER_FOR = value -> value instanceof WakeEvent;

        public WakeEvent {
            text = text == null ? "" : text;
            sourceId = sourceId == null ? "scheduler" : sourceId;
        }
    }
}
