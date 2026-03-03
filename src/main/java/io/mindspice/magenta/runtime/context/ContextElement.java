package io.mindspice.magenta.runtime.context;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface ContextElement permits ContextElement.SystemMsg, ContextElement.UserMsg,
        ContextElement.AssistantMsg, ContextElement.ToolMsg, ContextElement.SummaryMsg, ContextElement.InboundMsg {

    String content();

    record SystemMsg(String content) implements ContextElement {
        public SystemMsg {
            content = content == null ? "" : content;
        }
    }

    record UserMsg(String content) implements ContextElement {
        public UserMsg {
            content = content == null ? "" : content;
        }
    }

    record AssistantMsg(String content, List<ToolCall> toolCalls) implements ContextElement {
        public AssistantMsg {
            content = content == null ? "" : content;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    record ToolMsg(String toolCallId, String toolName, String content) implements ContextElement {
        public ToolMsg {
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolName = toolName == null ? "" : toolName;
            content = content == null ? "" : content;
        }
    }

    record SummaryMsg(String content, String sourceTag) implements ContextElement {
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
    ) implements ContextElement {
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
