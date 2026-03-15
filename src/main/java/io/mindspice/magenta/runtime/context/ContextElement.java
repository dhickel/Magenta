package io.mindspice.magenta.runtime.context;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface ContextElement permits ContextElement.SystemElement, ContextElement.UserMsg,
        ContextElement.AssistantMsg, ContextElement.ToolMsg, ContextElement.SummaryMsg, ContextElement.InboundMsg {

    String content();

    sealed interface SystemElement extends ContextElement permits ContextElement.PromptSystemElement,
            ContextElement.SystemStateMsg {
    }

    sealed interface PromptSystemElement extends SystemElement permits ContextElement.SystemCoreMsg,
            ContextElement.SystemAgentMsg, ContextElement.SystemTaskMsg {
    }

    static boolean isSystemElement(ContextElement element) {
        return element instanceof SystemElement;
    }

    static boolean isPromptSystemElement(ContextElement element) {
        return element instanceof PromptSystemElement;
    }

    static boolean isStateSystemElement(ContextElement element) {
        return element instanceof SystemStateMsg;
    }

    record SystemCoreMsg(String content) implements PromptSystemElement {
        public SystemCoreMsg {
            content = content == null ? "" : content;
        }
    }

    record SystemAgentMsg(String content) implements PromptSystemElement {
        public SystemAgentMsg {
            content = content == null ? "" : content;
        }
    }

    record SystemTaskMsg(String content) implements PromptSystemElement {
        public SystemTaskMsg {
            content = content == null ? "" : content;
        }
    }

    record SystemStateMsg(String content) implements SystemElement {
        public SystemStateMsg {
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

    record ToolMsg(
            String toolCallId,
            String toolName,
            String content,
            String rawContent,
            boolean contentTruncated
    ) implements ContextElement {
        public ToolMsg(String toolCallId, String toolName, String content) {
            this(toolCallId, toolName, content, content, false);
        }

        public ToolMsg {
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolName = toolName == null ? "" : toolName;
            content = content == null ? "" : content;
            rawContent = rawContent == null || rawContent.isBlank() ? content : rawContent;
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
