package io.mindspice.magenta.runtime.persistence;

import io.mindspice.magenta.runtime.context.ContextElement;

import java.util.List;

public sealed interface SessionContextResult permits CommonCommandResults.Success,
        CommonCommandResults.Failure,
        SessionContextResult.ActiveContextLoaded,
        SessionContextResult.ContextMessageLoaded {

    record ActiveContextLoaded(
            List<ContextElement> messages,
            int sysPromptAmount,
            int nextMessageId,
            List<Integer> droppedMessageIds
    ) implements SessionContextResult {
        public ActiveContextLoaded {
            messages = messages == null ? List.of() : List.copyOf(messages);
            sysPromptAmount = Math.max(sysPromptAmount, 0);
            nextMessageId = Math.max(nextMessageId, 0);
            droppedMessageIds = droppedMessageIds == null ? List.of() : List.copyOf(droppedMessageIds);
        }
    }

    record ContextMessageLoaded(int messageId, ContextElement message, boolean dropped) implements SessionContextResult {
    }
}
