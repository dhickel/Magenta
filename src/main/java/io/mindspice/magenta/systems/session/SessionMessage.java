package io.mindspice.magenta.systems.session;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface SessionMessage permits SessionMessage.SystemMsg, SessionMessage.UserMsg,
        SessionMessage.AssistantMsg, SessionMessage.ToolMsg, SessionMessage.SummaryMsg, SessionMessage.InboundMsg {

    String content();

    record SystemMsg(String content) implements SessionMessage {
        public SystemMsg {
            content = content == null ? "" : content;
        }
    }

    record UserMsg(String content) implements SessionMessage {
        public UserMsg {
            content = content == null ? "" : content;
        }
    }

    record AssistantMsg(String content, List<ToolCall> toolCalls) implements SessionMessage {
        public AssistantMsg {
            content = content == null ? "" : content;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    record ToolMsg(String toolCallId, String toolName, String content) implements SessionMessage {
        public ToolMsg {
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolName = toolName == null ? "" : toolName;
            content = content == null ? "" : content;
        }
    }

    record SummaryMsg(String content, String sourceTag) implements SessionMessage {
        public SummaryMsg {
            content = content == null ? "" : content;
            sourceTag = sourceTag == null ? "" : sourceTag;
        }
    }

    record InboundMsg(
            String inputDomain,
            String inputKind,
            String sourceId,
            String content,
            String correlationId,
            Map<String, String> metadata
    ) implements SessionMessage {
        public InboundMsg {
            inputDomain = inputDomain == null ? "" : inputDomain;
            inputKind = inputKind == null ? "" : inputKind;
            sourceId = sourceId == null ? "" : sourceId;
            content = content == null ? "" : content;
            correlationId = correlationId == null ? "" : correlationId;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record ToolCall(String id, String name, String argumentsJson) {
        public ToolCall {
            id = id == null ? UUID.randomUUID().toString() : id;
            name = name == null ? "" : name;
            argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
        }
    }
}
