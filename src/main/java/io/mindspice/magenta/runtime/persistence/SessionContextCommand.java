package io.mindspice.magenta.runtime.persistence;

import io.mindspice.magenta.runtime.context.ContextElement;

import java.util.List;
import java.util.Objects;

public sealed interface SessionContextCommand permits SessionContextCommand.InitializeSession,
        SessionContextCommand.AppendMessage,
        SessionContextCommand.AppendMessages,
        SessionContextCommand.ReplaceActiveContext,
        SessionContextCommand.UpsertStateSystemMessage,
        SessionContextCommand.LoadActiveContext,
        SessionContextCommand.GetMessageById,
        SessionContextCommand.LoadCompactionState {

    record InitializeSession(
            String sessionId,
            String agentId,
            String alias,
            int sysPromptAmount,
            List<ContextElement> initialContext
    ) implements SessionContextCommand {
        public InitializeSession {
            sessionId = sessionId == null ? "" : sessionId;
            agentId = agentId == null ? "" : agentId;
            alias = alias == null ? "" : alias;
            sysPromptAmount = Math.max(sysPromptAmount, 0);
            initialContext = initialContext == null ? List.of() : List.copyOf(initialContext);
        }
    }

    record AppendMessage(String sessionId, ContextElement message) implements SessionContextCommand {
        public AppendMessage {
            sessionId = sessionId == null ? "" : sessionId;
            Objects.requireNonNull(message, "message");
        }
    }

    record AppendMessages(String sessionId, List<ContextElement> messages) implements SessionContextCommand {
        public AppendMessages {
            sessionId = sessionId == null ? "" : sessionId;
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    record ReplaceActiveContext(
            String sessionId,
            List<ContextElement> replacement,
            int sysPromptAmount
    ) implements SessionContextCommand {
        public ReplaceActiveContext {
            sessionId = sessionId == null ? "" : sessionId;
            replacement = replacement == null ? List.of() : List.copyOf(replacement);
            sysPromptAmount = Math.max(sysPromptAmount, 0);
        }
    }

    record UpsertStateSystemMessage(String sessionId, String stateJson) implements SessionContextCommand {
        public UpsertStateSystemMessage {
            sessionId = sessionId == null ? "" : sessionId;
            stateJson = stateJson == null ? "" : stateJson;
        }
    }

    record LoadActiveContext(String sessionId) implements SessionContextCommand {
        public LoadActiveContext {
            sessionId = sessionId == null ? "" : sessionId;
        }
    }

    record GetMessageById(String sessionId, int messageId) implements SessionContextCommand {
        public GetMessageById {
            sessionId = sessionId == null ? "" : sessionId;
        }
    }

    record LoadCompactionState(
            String sessionId,
            int toolScanLimit,
            int todoLimit
    ) implements SessionContextCommand {
        public LoadCompactionState {
            sessionId = sessionId == null ? "" : sessionId;
            toolScanLimit = Math.max(toolScanLimit, 1);
            todoLimit = Math.max(todoLimit, 1);
        }
    }
}
