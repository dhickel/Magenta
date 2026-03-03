package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.routing.FilterTag;

import java.util.Objects;

public sealed interface SessionOutput permits SessionOutput.StreamOutput, SessionOutput.MessageOutput {

    String text();

    sealed interface StreamOutput extends SessionOutput permits StreamedOutput, FinalOutput {}

    sealed interface MessageOutput extends SessionOutput permits ContextMessageOutput, ToolMessageOutput {}

    record StreamedOutput(String text) implements StreamOutput {
        public static final FilterTag<SessionOutput> FILTER_TAG = value -> value instanceof StreamedOutput;

        public StreamedOutput {
            Objects.requireNonNull(text, "text");
        }
    }

    record FinalOutput(String text) implements StreamOutput {
        public static final FilterTag<SessionOutput> FILTER_TAG = value -> value instanceof FinalOutput;

        public FinalOutput {
            Objects.requireNonNull(text, "text");
        }
    }

    record ContextMessageOutput(ContextElement message) implements MessageOutput {
        public static final FilterTag<SessionOutput> FILTER_TAG = value -> value instanceof ContextMessageOutput;

        public ContextMessageOutput {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public String text() {
            return message.content();
        }
    }

    record ToolMessageOutput(ContextElement.ToolMsg message) implements MessageOutput {
        public static final FilterTag<SessionOutput> FILTER_TAG = value -> value instanceof ToolMessageOutput;

        public ToolMessageOutput {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public String text() {
            return message.content();
        }
    }
}
