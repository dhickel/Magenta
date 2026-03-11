package io.mindspice.magenta.runtime.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

public final class SessionEventLogSink {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final int NON_DEBUG_PREVIEW_CHARS = 1024;

    private final Path jsonlPath;
    private final Path prettyPath;
    private final RuntimeConfig.LogLevel logLevel;
    private final boolean prettyLogsEnabled;

    public SessionEventLogSink(Path workspaceRoot, RuntimeConfig.ObservabilityConfig observabilityConfig) {
        Path logsRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").resolve("logs");
        this.jsonlPath = logsRoot.resolve("session-events.jsonl");
        this.prettyPath = logsRoot.resolve("session-events.pretty.json");
        RuntimeConfig.ObservabilityConfig effective = observabilityConfig == null
                ? RuntimeConfig.ObservabilityConfig.defaults()
                : observabilityConfig;
        this.logLevel = effective.logLevel();
        this.prettyLogsEnabled = effective.prettyLogsEnabled();
        try {
            Files.createDirectories(logsRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize session event logs at: " + logsRoot.toAbsolutePath(), e);
        }
    }

    public synchronized void append(SessionEvent event) {
        if (event == null || !shouldLog(event)) {
            return;
        }

        SessionEvent normalized = sanitizeForLevel(event);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("timestamp", Instant.now().toString());
        root.put("sessionId", normalized.sessionHandle().sessionId().toString());
        root.put("agentId", normalized.agentId());
        root.put("eventType", normalized.getClass().getSimpleName());
        root.put("correlationId", "");
        root.set("payload", MAPPER.valueToTree(normalized));

        try {
            String compact = MAPPER.writeValueAsString(root);
            Files.writeString(
                    jsonlPath,
                    compact + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
            );

            if (prettyLogsEnabled) {
                String pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                Files.writeString(
                        prettyPath,
                        pretty + System.lineSeparator() + System.lineSeparator(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE
                );
            }
        } catch (IOException ignored) {
            // Logging must not break runtime execution.
        }
    }

    private boolean shouldLog(SessionEvent event) {
        return switch (logLevel) {
            case OFF -> false;
            case ERROR -> isErrorLevelEvent(event);
            case INFO -> isInfoLevelEvent(event);
            case DEBUG -> isDebugLevelEvent(event);
            case TRACE -> true;
        };
    }

    private boolean isErrorLevelEvent(SessionEvent event) {
        if (event instanceof SessionEvent.ErrorEvent) {
            return true;
        }
        if (event instanceof SessionEvent.SecurityDecision securityDecision) {
            SecurityManager.DecisionCode code = securityDecision.securityEvent().decisionCode();
            return code == SecurityManager.DecisionCode.DENIED
                   || code == SecurityManager.DecisionCode.VALIDATION_ERROR;
        }
        if (event instanceof SessionEvent.Action.ToolResult toolResult) {
            return isToolFailure(toolResult.content());
        }
        return false;
    }

    private boolean isInfoLevelEvent(SessionEvent event) {
        if (isErrorLevelEvent(event)) {
            return true;
        }
        if (event instanceof SessionEvent.Action) {
            return true;
        }
        if (event instanceof SessionEvent.SecurityDecision) {
            return true;
        }
        if (event instanceof SessionEvent.MessageOut messageOut) {
            return !(messageOut.output() instanceof SessionOutput.StreamedOutput);
        }
        if (event instanceof SessionEvent.RoutingDecision routingDecision) {
            return isRoutingFinal(routingDecision.routingEvent());
        }
        return false;
    }

    private boolean isDebugLevelEvent(SessionEvent event) {
        if (event instanceof SessionEvent.RoutingDecision routingDecision) {
            return isRoutingFinal(routingDecision.routingEvent());
        }
        if (event instanceof SessionEvent.MessageOut messageOut) {
            return !(messageOut.output() instanceof SessionOutput.StreamedOutput);
        }
        return true;
    }

    private boolean isRoutingFinal(RoutingEvent routingEvent) {
        if (routingEvent instanceof RoutingEvent.OutputResult) {
            return true;
        }
        if (routingEvent instanceof RoutingEvent.InputResult inputResult) {
            return inputResult.phase() == InputRoutingEvent.Phase.FINAL;
        }
        return false;
    }

    private SessionEvent sanitizeForLevel(SessionEvent event) {
        if (logLevel == RuntimeConfig.LogLevel.DEBUG || logLevel == RuntimeConfig.LogLevel.TRACE) {
            return event;
        }

        if (event instanceof SessionEvent.Action.ToolResult toolResult) {
            return new SessionEvent.Action.ToolResult(
                    toolResult.sessionHandle(),
                    toolResult.agentId(),
                    toolResult.toolName(),
                    toolResult.toolCallId(),
                    sanitizeToolPayload(toolResult.content())
            );
        }

        if (event instanceof SessionEvent.Action.ToolCall toolCall) {
            return new SessionEvent.Action.ToolCall(
                    toolCall.sessionHandle(),
                    toolCall.agentId(),
                    toolCall.toolName(),
                    toolCall.toolCallId(),
                    sanitizePreview(toolCall.argumentsJson())
            );
        }

        if (event instanceof SessionEvent.MessageOut messageOut) {
            SessionOutput output = messageOut.output();
            if (output instanceof SessionOutput.ToolMessageOutput toolMessageOutput) {
                ContextElement.ToolMsg redacted = new ContextElement.ToolMsg(
                        toolMessageOutput.message().toolCallId(),
                        toolMessageOutput.message().toolName(),
                        sanitizeToolPayload(toolMessageOutput.message().content())
                );
                return new SessionEvent.MessageOut(
                        messageOut.sessionHandle(),
                        messageOut.agentId(),
                        new SessionOutput.ToolMessageOutput(redacted)
                );
            }
            if (output instanceof SessionOutput.ToolCallOutput toolCallOutput) {
                ContextElement.ToolCall redacted = new ContextElement.ToolCall(
                        toolCallOutput.toolCall().id(),
                        toolCallOutput.toolCall().name(),
                        sanitizePreview(toolCallOutput.toolCall().argumentsJson())
                );
                return new SessionEvent.MessageOut(
                        messageOut.sessionHandle(),
                        messageOut.agentId(),
                        new SessionOutput.ToolCallOutput(redacted)
                );
            }
        }

        return event;
    }

    private boolean isToolFailure(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        try {
            JsonNode json = MAPPER.readTree(content);
            if (json != null && json.isObject()) {
                JsonNode status = json.get("status");
                if (status != null && status.isTextual()) {
                    return "failed".equalsIgnoreCase(status.asText());
                }
            }
        } catch (Exception ignored) {
            // best effort fallback below
        }
        return content.contains("\"status\":\"failed\"") || content.contains("\"status\": \"failed\"");
    }

    private String sanitizeToolPayload(String content) {
        String safe = content == null ? "" : content;
        String preview = sanitizePreview(safe);
        ObjectNode summary = MAPPER.createObjectNode();

        try {
            JsonNode json = MAPPER.readTree(safe);
            if (json != null && json.isObject()) {
                copyTextIfPresent(json, summary, "status");
                copyTextIfPresent(json, summary, "code");
                copyTextIfPresent(json, summary, "message");
            }
        } catch (Exception ignored) {
            // fallback to raw preview only
        }

        summary.put("preview", preview);
        summary.put("fullSizeChars", safe.length());
        summary.put("previewTruncated", safe.length() > NON_DEBUG_PREVIEW_CHARS);

        try {
            return MAPPER.writeValueAsString(summary);
        } catch (Exception ignored) {
            return preview;
        }
    }

    private void copyTextIfPresent(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isTextual()) {
            target.put(field, sanitizePreview(value.asText()));
        }
    }

    private String sanitizePreview(String value) {
        String safe = value == null ? "" : value;
        if (safe.length() <= NON_DEBUG_PREVIEW_CHARS) {
            return safe;
        }
        return safe.substring(0, NON_DEBUG_PREVIEW_CHARS);
    }
}
