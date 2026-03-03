package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionMessage;

import java.util.Objects;
import java.util.Set;

public sealed interface SessionOutputEvent permits
        SessionOutputEvent.PartialToken,
        SessionOutputEvent.AssistantFinal,
        SessionOutputEvent.MessageAppended,
        SessionOutputEvent.ToolMessageAppended {

    Kind kind();
    String source();
    Set<String> tags();

    enum Kind {
        PARTIAL,
        FINAL,
        MESSAGE_APPENDED,
        TOOL_MESSAGE_APPENDED
    }

    record PartialToken(String token, String source, Set<String> tags) implements SessionOutputEvent {
        public PartialToken {
            token = token == null ? "" : token;
            source = source == null ? "model" : source;
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }

        @Override
        public Kind kind() {
            return Kind.PARTIAL;
        }
    }

    record AssistantFinal(String text, String source, Set<String> tags) implements SessionOutputEvent {
        public AssistantFinal {
            text = text == null ? "" : text;
            source = source == null ? "model" : source;
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }

        @Override
        public Kind kind() {
            return Kind.FINAL;
        }
    }

    record MessageAppended(SessionMessage message, String source, Set<String> tags) implements SessionOutputEvent {
        public MessageAppended {
            Objects.requireNonNull(message, "message");
            source = source == null ? "session-context" : source;
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }

        @Override
        public Kind kind() {
            return Kind.MESSAGE_APPENDED;
        }
    }

    record ToolMessageAppended(SessionMessage.ToolMsg message, String source, Set<String> tags) implements SessionOutputEvent {
        public ToolMessageAppended {
            Objects.requireNonNull(message, "message");
            source = source == null ? "tool-bridge" : source;
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }

        @Override
        public Kind kind() {
            return Kind.TOOL_MESSAGE_APPENDED;
        }
    }
}
