package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.session.SessionOutput;

import java.util.Objects;

public final class AssistantOutputWriter {

    private final AssistantOutputTarget target;
    private boolean streamInProgress = false;

    public AssistantOutputWriter(AssistantOutputTarget target) {
        this(target, false);
    }

    public AssistantOutputWriter(AssistantOutputTarget target, boolean emitFallbackNotice) {
        this.target = Objects.requireNonNull(target, "target");
    }

    public void onOutput(SessionOutput output) {
        switch (output) {
            case SessionOutput.StreamedOutput stream -> {
                if (!streamInProgress) {
                    target.printAssistantToken("assistant> ");
                }
                streamInProgress = true;
                target.printAssistantToken(stream.text());
            }
            case SessionOutput.FinalOutput finalOutput -> {
                boolean hasFinalText = finalOutput.text() != null && !finalOutput.text().isBlank();
                if (streamInProgress) {
                    target.finishAssistantStreamLine();
                } else {
                    if (hasFinalText) {
                        target.printAssistantFinal("assistant> " + finalOutput.text());
                    }
                }
                streamInProgress = false;
            }
            case SessionOutput.ToolCallOutput toolCallOutput ->
                    target.printToolCall(toolCallOutput.toolCall().name(), toolCallOutput.toolCall().argumentsJson());
            case SessionOutput.ToolMessageOutput toolMessageOutput -> target.printToolResult(
                    toolMessageOutput.message().toolName(),
                    toolMessageOutput.text(),
                    isFailurePayload(toolMessageOutput.text())
            );
        }
    }

    private boolean isFailurePayload(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.toLowerCase();
        return normalized.contains("\"status\":\"failed\"") || normalized.contains("\"status\": \"failed\"");
    }
}
